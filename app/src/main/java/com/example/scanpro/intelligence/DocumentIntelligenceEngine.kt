package com.example.scanpro.intelligence

import com.example.scanpro.core.text.AppStrings
import androidx.annotation.StringRes
import com.example.scanpro.R
import java.util.Locale
import kotlin.math.min

/**
 * [label] is the English keyword the engine's own logic works with (suggested tags, issuer matching, summary
 * text, smart titles); [labelRes] is what the screens show for the kind.
 */
// [label] stays English on purpose (owner decision): it feeds smart titles and tags, which are stored and searched.
enum class DocumentKind(val label: String, @StringRes val labelRes: Int) {
    INVOICE("Invoice", R.string.data_kind_invoice),
    RECEIPT("Receipt", R.string.data_kind_receipt),
    CONTRACT("Contract", R.string.data_kind_contract),
    IDENTITY("Identity document", R.string.data_kind_identity),
    BUSINESS_CARD("Business card", R.string.data_kind_business_card),
    LETTER("Letter", R.string.data_kind_letter),
    NOTES("Notes", R.string.data_kind_notes),
    GENERAL("Document", R.string.data_kind_general)
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
        val summary = structuredSummary(text, kind, dates) ?: extractiveSummary(text, kind, amounts, dates)
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
        if (qTokens.isEmpty() || text.isBlank()) return AppStrings.get(R.string.assistant_ask_specific)

        // Intent shortcuts for natural on-device assistant queries
        if (qTokens.any { it in setOf("summary", "summarise", "overview") }) {
            val insights = analyze(raw)
            if (insights.summary.isNotBlank()) return insights.summary
        }
        if (qTokens.any { it in setOf("total", "amount", "cost", "price", "due", "balance") }) {
            total(text)?.let { return "Total: $it" }
        }
        if (qTokens.any { it in setOf("action", "actions", "todo", "task", "tasks") }) {
            val actions = extractActionItems(text)
            if (actions.isNotEmpty()) return actions.joinToString("\n• ", prefix = "• ")
        }

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

        if (ranked.isEmpty()) return AppStrings.get(R.string.assistant_no_match)
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
        // A business card is short and carries a way to reach someone. One word such as "email" or "manager" on a
        // laptop screen or a menu is not enough, and the label offers "Add to contacts", so it must not misfire.
        val looksLikeCard = lower.length <= CARD_MAX_CHARS && (EMAIL.containsMatchIn(lower) || PHONE.containsMatchIn(lower))
        val scores = mapOf(
            DocumentKind.INVOICE to score("invoice", "gstin", "subtotal", "tax invoice", "amount due"),
            DocumentKind.RECEIPT to score("receipt", "cash", "change", "total", "thank you"),
            DocumentKind.CONTRACT to score("agreement", "contract", "party", "whereas", "terms and conditions", "signature"),
            DocumentKind.IDENTITY to score("passport", "date of birth", "nationality", "identity", "aadhaar", "driving licence", "driver license"),
            DocumentKind.BUSINESS_CARD to if (looksLikeCard) score("www.", "mobile", "email", "director", "manager", "linkedin") else 0,
            DocumentKind.LETTER to score("dear ", "sincerely", "regards", "subject:"),
            DocumentKind.NOTES to score("notes", "todo", "to do", "agenda", "minutes")
        )
        return scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: DocumentKind.GENERAL
    }

    /**
     * The business or person a document comes from: the first line near the top that reads like a name (mostly
     * letters), skipping headings such as "Tax Invoice", the kind word itself, and contact details.
     */
    fun issuer(text: String, kindLabel: String? = null): String? = text.lineSequence()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length in 3..80 }
        .take(10)
        .firstOrNull { line ->
            val l = line.lowercase(Locale.ROOT)
            val letters = line.count { it.isLetter() }
            val digits = line.count { it.isDigit() }
            letters >= 3 && digits * 3 <= letters &&
                '@' !in line && "www." !in l && "http" !in l &&
                (kindLabel == null || kindLabel.lowercase(Locale.ROOT) !in l) &&
                GENERIC_HEADINGS.none { l == it || l.startsWith("$it ") || l.startsWith("$it:") }
        }
        ?.trim()?.trim(':', '-', '–', '|', '.', ',')?.trim()

    /**
     * The amount on the "Total" / "Amount due" / "Balance due" line (the last one, so a grand total wins), never a
     * subtotal or a single item; an exact OCR substring, or null when there is no such line.
     */
    fun total(text: String): String? = text.lineSequence()
        .filter { Regex("(?i)\\b(?:grand\\s+)?total\\b|\\bamount\\s+due\\b|\\bbalance\\s+due\\b").containsMatchIn(it) }
        .filterNot { Regex("(?i)\\bsub\\s*-?\\s*total\\b").containsMatchIn(it) }
        .mapNotNull { line -> amountRegex.find(line)?.value?.trim() ?: Regex("\\d[\\d,]*\\.\\d{2}\\b").find(line)?.value }
        .lastOrNull()

    /** "Receipt from Blue Bottle Coffee · Total $8.37 · 03/10/2026" for receipts and invoices; null otherwise. */
    private fun structuredSummary(text: String, kind: DocumentKind, dates: List<String>): String? {
        if (kind != DocumentKind.RECEIPT && kind != DocumentKind.INVOICE) return null
        val from = issuer(text, kind.label)
        val total = total(text)
        if (from == null && total == null) return null
        return listOfNotNull(
            if (from != null) "${kind.label} from $from" else kind.label,
            total?.let { "Total $it" },
            dates.firstOrNull()
        ).joinToString(" · ")
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

    /** Longest text still read as a business card. */
    private const val CARD_MAX_CHARS = 700
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")

    /** A phone-like number: groups of at least two digits, so a keyboard's row of single digits does not count. */
    private val PHONE = Regex("(?<!\\d)(?:\\+\\d{1,3}[\\s-]?)?\\(?\\d{2,5}\\)?[\\s-]?\\d{3,5}[\\s-]?\\d{3,5}(?!\\d)")

    private val GENERIC_HEADINGS = listOf(
        "tax invoice", "invoice", "receipt", "cash receipt", "bill", "bill to", "ship to", "sold to", "original",
        "duplicate", "customer copy", "merchant copy", "welcome", "thank you", "date", "time", "total", "subtotal",
        "dear", "to", "from", "agreement", "contract", "notes", "agenda", "minutes", "statement", "page"
    )

    private val STOPWORDS = setOf(
        "the", "and", "for", "are", "was", "were", "what", "when", "where", "which", "who", "whom", "why",
        "how", "this", "that", "these", "those", "with", "from", "does", "did", "has", "have", "had", "can",
        "could", "should", "would", "will", "shall", "about", "into", "onto", "any", "all", "you", "your",
        "there", "their", "them", "they", "its", "our", "out", "please", "tell", "show", "give", "find", "much", "many"
    )
}
