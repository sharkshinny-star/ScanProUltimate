package com.example.scanpro.scanner

import java.net.URI

/** Something about a scanned web address worth a second look before opening it. */
enum class LinkWarning {
    /** http://, not https://: the page and anything typed into it travel unencrypted. */
    NOT_ENCRYPTED,

    /** The host is a bare IP address instead of a name. */
    IP_ADDRESS,

    /** The host uses look-alike letters (punycode xn-- or non-Latin characters), a common phishing trick. */
    LOOKALIKE_LETTERS,

    /** Text before an @ ("bank.com@evil.example") makes the address look like a different site. */
    MISLEADING_AT,

    /** A link shortener: the real destination is hidden until it is opened. */
    SHORTENED
}

/** What the "Open link?" preview shows: the [host] in bold, the [url] in full, and any [warnings]. */
data class LinkReport(val url: String, val host: String, val warnings: Set<LinkWarning>)

/**
 * Looks at a scanned web address without touching the network, so the person sees where it really goes before
 * anything is opened. Pure Kotlin, unit tested.
 */
object LinkSafety {
    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "cutt.ly", "rb.gy", "buff.ly", "shorturl.at",
        "tiny.cc", "s.id", "rebrand.ly", "t.ly", "lnkd.in", "qrco.de", "short.io"
    )

    /** The report for [url]; null when it is not an http(s) address with a host. */
    fun check(url: String): LinkReport? {
        val text = url.trim()
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: text.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        // java.net.URI refuses non-ASCII hosts, so the authority is also read by hand.
        val authority = uri?.rawAuthority ?: text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        val hostPart = authority.substringAfterLast('@').let { host ->
            if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBeforeLast(':').takeIf { host.contains(':') } ?: host
        }
        val host = hostPart.lowercase().trimEnd('.')
        if (host.isEmpty()) return null
        val warnings = LinkedHashSet<LinkWarning>()
        if (scheme == "http") warnings.add(LinkWarning.NOT_ENCRYPTED)
        if (isIpAddress(host)) warnings.add(LinkWarning.IP_ADDRESS)
        if (host.split('.').any { it.startsWith("xn--") } || host.any { it.code > 127 }) warnings.add(LinkWarning.LOOKALIKE_LETTERS)
        if (authority.contains('@')) warnings.add(LinkWarning.MISLEADING_AT)
        if (host.removePrefix("www.") in SHORTENERS) warnings.add(LinkWarning.SHORTENED)
        return LinkReport(text, host, warnings)
    }

    private fun isIpAddress(host: String): Boolean {
        if (host.startsWith("[")) return true // IPv6 literal
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toInt() <= 255 }
    }
}
