package com.example.scanpro.imaging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The name a new scan gets (R34, after Adobe Scan's and Genius Scan's name templates). With no pattern the title is
 * what it always was: the smart title from the page text, else the mode and date. A pattern is text with tokens:
 * `{text}` the smart title (nothing when the page gave none), `{mode}` the scan mode, `{date}` yyyy-MM-dd, `{time}` HH-mm.
 */
object ScanTitle {
    const val MAX_LENGTH = 120
    val TOKENS = listOf("{text}", "{mode}", "{date}", "{time}")

    fun build(pattern: String, smartTitle: String?, mode: String, created: Long, fallback: String, zone: TimeZone = TimeZone.getDefault()): String {
        val text = smartTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (pattern.isBlank()) return text ?: fallback
        fun stamp(format: String) = SimpleDateFormat(format, Locale.getDefault()).apply { timeZone = zone }.format(Date(created))
        val expanded = pattern
            .replace("{text}", text.orEmpty())
            .replace("{mode}", mode)
            .replace("{date}", stamp("yyyy-MM-dd"))
            .replace("{time}", stamp("HH-mm"))
        return tidy(expanded).ifBlank { fallback }.take(MAX_LENGTH)
    }

    /** Collapses the gaps a missing token leaves: repeated spaces, and separators with nothing on one side. */
    internal fun tidy(raw: String): String {
        var s = raw.replace(Regex("\\s+"), " ").trim()
        // A separator left dangling at either end, or doubled where a token was empty.
        val sep = "[-–—_·|:,]"
        s = s.replace(Regex("(\\s*$sep\\s*){2,}"), " – ")
        s = s.replace(Regex("^\\s*$sep+\\s*"), "").replace(Regex("\\s*$sep+\\s*$"), "")
        return s.trim()
    }
}
