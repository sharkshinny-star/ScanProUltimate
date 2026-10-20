package com.example.scanpro.intelligence

import java.util.Locale
import kotlin.math.min

enum class DocumentKind(val label: String) {
    INVOICE("Invoice"),
    RECEIPT("Receipt"),
    CONTRACT("Contract"),
    IDENTITY("Identity document"),
    BUSINESS_CARD("Business card"),
    LETTER("Letter"),
    NOTES("Notes"),
    GENERAL("Document")
}

data class DocumentInsights(
    val kind: DocumentKind,
    val summary: String,
    val amounts: List<String>,
    val dates: List<String>,
    val emails: List<String>,
    val phoneNumbers: List<String>,
    val references: List<String>,
    val actionItems: List<String>,
    val suggestedTags: List<String>
)

/**
 * Fully offline, deterministic document intelligence. It never invents data: all returned
 * facts are exact OCR substrings. Cloud LLM integrations should sit behind a separate backend
 * and must not replace this verifiable local layer.
 */
object DocumentIntelligenceEngine {
    // Letter currency codes need word boundaries ("3 years 2024" must not read as "rs 2024"), and the
    // gap between symbol and number is spaces only, so an amount never spans two lines.
    private val amountRegex = Regex("(?i)(?:₹|\\$|€|£|\\b(?:Rs\\.?|INR|USD|EUR|GBP|AED|SAR)(?![a-z]))[ \\t]?\\d[\\d,]*(?:\\.\\d{1,2})?|\\b\\d[\\d,]*(?:\\.\\d{1,2})?[ \\t]?(?:INR|USD|EUR|GBP|AED|SAR)\\b")
    private const val MONTH = "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?"
    // Numeric (12/03/2024, 12-03-24, 08.10.2026, ISO 2026-10-08) and written (Mar 12, 2024 / 12 Mar 2024).
    private val dateRegex = Regex(
        "\\b(?:\\d{1,2}[/.-]\\d{1,2}[/.-](?:\\d{4}|\\d{2})|\\d{4}-\\d{1,2}-\\d{1,2}|$MONTH\\s+\\d{1,2},?\\s+\\d{4}|\\d{1,2}\\s+$MONTH,?\\s+\\d{4})\\b",
        RegexOption.IGNORE_CASE
    )
    private val emailRegex = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    // No dots: "10.00 20.00 30.00" is a column of prices, not a phone number.
    private val phoneRegex = Regex("(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)")
    private val referenceRegex = Regex("""(?i)\b(?:invoice|receipt|order|ref(?:erence)?|gstin|tax id|account|contract)[ \t]*(?:(?:no\.?|number|#)[ \t]*)?[:#-]?[ \t]*([A-Z0-9](?=[A-Z0-9/_-]*\d)[A-Z0-9/_-]{3,})""")

    fun analyze(raw: String): DocumentInsights {
        val text = normalize(raw)
        if (text.isBlank()) return DocumentInsights(DocumentKind.GENERAL, "No OCR text is available yet.", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val lower = text.lowercase(Locale.ROOT)
        val kind = classify(lower)
        val amounts = amountRegex.findAll(text).map { it.value.trim() }.distinct().take(8).toList()
        val dates = dateRegex.findAll(text).map { it.value.trim() }.distinct().take(8).toList()
        val emails = emailRegex.findAll(text).map { it.value.trim() }.distinct().take(8).toList()
        val phones = phoneRegex.findAll(text).map { it.value.trim() }.filter { it.count(Char::isDigit) in 8..15 }.distinct().take(8).toList()
        val refs = referenceRegex.findAll(text).mapNotNull { it.groups[1]?.value?.trim() }.distinct().take(8).toList()
        val actionItems = extractActionItems(text)
        val summary = extractiveSummary(text, kind, amounts, dates)
        val tags = buildList {
            add(kind.label.lowercase(Locale.ROOT))
            if (amounts.isNotEmpty()) add("financial")
            if (dates.isNotEmpty()) add("dated")
            if (emails.isNotEmpty() || phones.isNotEmpty()) add("contact")
        }.distinct()

        return DocumentInsights(kind, summary, amounts, dates, emails, phones, refs, actionItems, tags)
    }

    fun answer(question: String, raw: String): String {
        val text = normalize(raw)
        // Drop question words and filler so "What is the address?" matches on "address", not "the".
        val qTokens = tokens(question).filter { it.length >= 3 && it !in STOPWORDS }.toSet()
        if (qTokens.isEmpty() || text.isBlank()) return "Ask a specific question about the recognized text."

        val chunks = text.split(Regex("\\n{2,}|(?<=[.!?])\\s+"))
            .map(String::trim)
            .filter { it.length >= 12 }
            .take(300)

        val ranked = chunks.map { chunk ->
            val chunkTokens = tokens(chunk).toSet()
            // Whole-word overlap only; substring matching let "the" hit "there" and "other".
            chunk to qTokens.count { it in chunkTokens }
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)

        if (ranked.isEmpty()) return "I couldn't find a matching passage in the OCR text."
        return ranked.joinToString("\n\n") { it.first }.take(1200)
    }

    private fun classify(lower: String): DocumentKind {
        // Match whole words so "mobile" does not hit "automobile" or "change" hit "exchange".
        // Boundaries are only enforced on sides where the term starts/ends with a letter or digit.
        fun hasTerm(term: String): Boolean {
            val t = term.trim()
            val head = if (t.first().isLetterOrDigit()) "(?<![\\p{L}\\p{N}])" else ""
            val tail = if (t.last().isLetterOrDigit()) "(?![\\p{L}\\p{N}])" else ""
            return Regex(head + Regex.escape(t) + tail).containsMatchIn(lower)
        }
        fun score(vararg terms: String) = terms.count { hasTerm(it) }
        val scores = mapOf(
            DocumentKind.INVOICE to score("invoice", "gstin", "subtotal", "tax invoice", "amount due"),
            DocumentKind.RECEIPT to score("receipt", "cash", "change", "total", "thank you"),
            DocumentKind.CONTRACT to score("agreement", "contract", "party", "whereas", "terms and conditions", "signature"),
            DocumentKind.IDENTITY to score("passport", "date of birth", "nationality", "identity", "aadhaar", "driving licence", "driver license"),
            DocumentKind.BUSINESS_CARD to score("www.", "mobile", "email", "director", "manager", "linkedin"),
            DocumentKind.LETTER to score("dear ", "sincerely", "regards", "subject:"),
            DocumentKind.NOTES to score("notes", "todo", "to do", "agenda", "minutes")
        )
        return scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: DocumentKind.GENERAL
    }

    private fun extractActionItems(text: String): List<String> {
        val markers = listOf(
            "please ", "must ", "need to ", "needs to ", "required to ", "action:",
            "action item", "follow up", "follow-up", "deadline", "due by", "submit ",
            "send ", "call ", "review ", "approve ", "pay ", "sign "
        )
        return text.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 8..220 }
            .filter { line -> markers.any { marker -> line.lowercase(Locale.ROOT).contains(marker) } }
            .distinct()
            .take(6)
            .toList()
    }

    private fun extractiveSummary(text: String, kind: DocumentKind, amounts: List<String>, dates: List<String>): String {
        val lines = text.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 15..220 }
            .distinct()
            .take(40)
            .toList()

        val best = lines.sortedByDescending { line ->
            val lower = line.lowercase(Locale.ROOT)
            var score = min(line.length, 120) / 30
            if (kind != DocumentKind.GENERAL && lower.contains(kind.label.substringBefore(' ').lowercase(Locale.ROOT))) score += 4
            if (amounts.any { line.contains(it) }) score += 3
            if (dates.any { line.contains(it) }) score += 2
            score
        }.take(3)

        return if (best.isEmpty()) text.take(360) else best.joinToString(" ").take(520)
    }

    private fun normalize(value: String) = value.replace('\u0000', ' ').replace(Regex("[ \\t]+"), " ").trim()

    private fun tokens(value: String) = value.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }

    private val STOPWORDS = setOf(
        "the", "and", "for", "are", "was", "were", "what", "when", "where", "which", "who", "whom", "why",
        "how", "this", "that", "these", "those", "with", "from", "does", "did", "has", "have", "had", "can",
        "could", "should", "would", "will", "shall", "about", "into", "onto", "any", "all", "you", "your",
        "there", "their", "them", "they", "its", "our", "out", "please", "tell", "show", "give", "find", "much", "many"
    )
}
