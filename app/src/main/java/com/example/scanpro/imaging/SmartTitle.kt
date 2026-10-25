package com.example.scanpro.imaging

import com.example.scanpro.intelligence.DocumentIntelligenceEngine
import com.example.scanpro.intelligence.DocumentKind

/**
 * Names a new scan from what it says, the way "Receipt – Starbucks – 12 Mar 2026" reads in a file list:
 * the kind of document, who it is from (or its subject), and its date. Every part is taken word for word
 * from the recognised text; when nothing useful is found, [fromOcr] returns the fallback.
 */
object SmartTitle {
    private const val SEPARATOR = " – "
    private const val MAX_LENGTH = 72

    fun fromOcr(text: String, fallback: String): String {
        val lines = text.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 3..80 }
            .filterNot { it.matches(Regex("(?i)^page\\s+\\d+.*")) }
            .toList()
        if (lines.isEmpty()) return fallback

        val insights = DocumentIntelligenceEngine.analyze(text)
        val lower = text.lowercase()
        // ID documents are named by type only: a name, number or birth date in a file list would expose them.
        if (insights.kind == DocumentKind.IDENTITY) {
            return when {
                "passport" in lower -> "ID – Passport"
                "driving licence" in lower || "driver license" in lower || "driving license" in lower -> "ID – Driving licence"
                "aadhaar" in lower -> "ID – Aadhaar"
                else -> "ID document"
            }
        }
        val kind = when {
            "resume" in lower || "curriculum vitae" in lower -> "Resume"
            insights.kind == DocumentKind.GENERAL && "statement" in lower -> "Statement"
            insights.kind == DocumentKind.GENERAL -> null
            else -> insights.kind.label
        }

        val subject = when (insights.kind) {
            DocumentKind.LETTER -> lines.firstNotNullOfOrNull { line ->
                Regex("(?i)^(?:subject|re)\\s*[:-]\\s*(.+)").find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 }
            } ?: nameLine(lines, kind)
            else -> nameLine(lines, kind)
        }
        val date = insights.dates.firstOrNull()?.replace('/', '-')

        val parts = listOfNotNull(kind, subject?.let(::tidy), date).distinct()
        // A kind alone ("Invoice") says too little; fall back to the first line, as before.
        val title = when {
            parts.size >= 2 -> parts.joinToString(SEPARATOR)
            kind != null -> "$kind$SEPARATOR${tidy(lines.first())}"
            else -> tidy(lines.first())
        }
        return shorten(title).ifBlank { fallback }
    }

    /**
     * The first line that reads like a name (a shop, company or person): mostly letters, and not a heading
     * such as "Tax Invoice", a date, an amount, an address line or contact details.
     */
    private fun nameLine(lines: List<String>, kind: String?): String? = lines.take(10).firstOrNull { line ->
        val l = line.lowercase()
        val letters = line.count { it.isLetter() }
        val digits = line.count { it.isDigit() }
        letters >= 3 && digits * 3 <= letters &&
            '@' !in line && "www." !in l && "http" !in l &&
            (kind == null || kind.lowercase() !in l) &&
            GENERIC_HEADINGS.none { l == it || l.startsWith("$it ") || l.startsWith("$it:") }
    }

    private fun tidy(value: String) = value.trim().trim(':', '-', '–', '|', '.', ',').trim()

    private fun shorten(value: String): String {
        if (value.length <= MAX_LENGTH) return value.trim()
        val cut = value.take(MAX_LENGTH)
        val space = cut.lastIndexOf(' ')
        return (if (space > MAX_LENGTH / 2) cut.take(space) else cut).trim().trimEnd('–', '-').trim()
    }

    private val GENERIC_HEADINGS = listOf(
        "tax invoice", "invoice", "receipt", "cash receipt", "bill", "bill to", "ship to", "sold to", "original",
        "duplicate", "customer copy", "merchant copy", "welcome", "thank you", "date", "time", "total", "subtotal",
        "dear", "to", "from", "agreement", "contract", "notes", "agenda", "minutes", "statement", "page"
    )
}
