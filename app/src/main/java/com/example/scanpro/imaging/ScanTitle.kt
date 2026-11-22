package com.example.scanpro.imaging

import com.example.scanpro.core.text.takeWhole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The name a new scan gets (R34, after Adobe Scan's and Genius Scan's name templates). With no pattern the title is
 * what it always was: the smart title from the page text, else the mode and date. A pattern is text with tokens:
 * `{text}` the smart title (nothing when the page gave none), `{mode}` the scan mode, `{date}` yyyy-MM-dd, `{time}` HH-mm.
 * Tokens match in any case (`{Date}` works), and an unknown one stays as typed.
 */
object ScanTitle {
    const val MAX_LENGTH = 120
    val TOKENS = listOf("{text}", "{mode}", "{date}", "{time}")

    /** Ready-made patterns offered in Settings, each shown there as the name it would give. */
    val PRESETS = listOf("{mode} {date}", "{text} – {date}", "{date} {time} {mode}")

    private val TOKEN = Regex("\\{([A-Za-z]+)\\}")
    private val WHITESPACE = Regex("\\s+")
    private const val SEP = "[-–—_·|:,]"
    private val DOUBLED_SEP = Regex("(\\s*$SEP\\s*){2,}")
    private val LEADING_SEP = Regex("^\\s*$SEP+\\s*")
    private val TRAILING_SEP = Regex("\\s*$SEP+\\s*$")

    fun build(pattern: String, smartTitle: String?, mode: String, created: Long, fallback: String, zone: TimeZone = TimeZone.getDefault()): String {
        val text = smartTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (pattern.isBlank()) return text ?: fallback
        val date = Date(created)
        fun stamp(format: String) = SimpleDateFormat(format, Locale.getDefault()).apply { timeZone = zone }.format(date)
        // One pass over the pattern: text that came from the page is inserted as it is, so a page that happens
        // to read "{date}" cannot be expanded a second time.
        val expanded = TOKEN.replace(pattern) { match ->
            when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "text" -> text.orEmpty()
                "mode" -> mode
                "date" -> stamp("yyyy-MM-dd")
                "time" -> stamp("HH-mm")
                else -> match.value
            }
        }
        return tidy(expanded).ifBlank { fallback }.takeWhole(MAX_LENGTH)
    }

    /** The `{…}` parts of [pattern] that are not tokens, so Settings can say they will stay as typed. */
    fun unknownTokens(pattern: String): List<String> =
        TOKEN.findAll(pattern).map { it.value }.filter { it.lowercase(Locale.ROOT) !in TOKENS }.distinct().toList()

    /**
     * [text] with [token] put in place of the selection [start]..[end], with a space on either side where it would
     * otherwise run into a word. Returns the new text and where the cursor goes (just after what was inserted).
     */
    fun insertToken(text: String, start: Int, end: Int, token: String): Pair<String, Int> {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val before = text.substring(0, from)
        val after = text.substring(to)
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trail = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        val inserted = lead + token + trail
        return (before + inserted + after) to (before.length + inserted.length)
    }

    /** Collapses the gaps a missing token leaves: repeated spaces, and separators with nothing on one side. */
    internal fun tidy(raw: String): String {
        var s = raw.replace(WHITESPACE, " ").trim()
        // A separator left dangling at either end, or doubled where a token was empty.
        s = s.replace(DOUBLED_SEP, " – ")
        s = s.replace(LEADING_SEP, "").replace(TRAILING_SEP, "")
        return s.trim()
    }
}
