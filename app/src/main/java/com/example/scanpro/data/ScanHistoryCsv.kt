package com.example.scanpro.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The scan history as CSV (RFC 4180), for the user's own "Export" through the share sheet. Pure Kotlin, unit tested.
 *
 * Two protections: a Wi-Fi code's password is left out (the result sheet hides and never shares it either), and a
 * cell starting with = + - @ or a tab gets a leading apostrophe so a spreadsheet shows it as text instead of running
 * it as a formula (CSV injection from a hostile code).
 */
object ScanHistoryCsv {
    const val HEADER = "saved_at,source,format,favourite,content"

    fun export(list: List<ScanHistoryEntry>): String {
        val out = StringBuilder(HEADER).append("\r\n")
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (entry in list) {
            out.append(cell(time.format(Date(entry.savedAt)))).append(',')
                .append(if (entry.created) "created" else "scanned").append(',')
                .append(formatName(entry.format)).append(',')
                .append(if (entry.favourite) "yes" else "no").append(',')
                .append(cell(withoutWifiPassword(entry.raw)))
                .append("\r\n")
        }
        return out.toString()
    }

    private val WIFI_PASSWORD = Regex("(?i)(?<=^WIFI:|;)P:(?:\\\\.|[^;\\\\])*;")

    /** A Wi-Fi code with its `P:` field removed; any other text unchanged. */
    fun withoutWifiPassword(raw: String): String =
        if (raw.startsWith("WIFI:", ignoreCase = true)) WIFI_PASSWORD.replace(raw, "") else raw

    /** One CSV cell: quoted when it holds a comma, quote or line break; formula-looking text neutralised. */
    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** ML Kit's Barcode.FORMAT_* number as a short name. */
    fun formatName(format: Int): String = when (format) {
        1 -> "CODE_128"
        2 -> "CODE_39"
        4 -> "CODE_93"
        8 -> "CODABAR"
        16 -> "DATA_MATRIX"
        32 -> "EAN_13"
        64 -> "EAN_8"
        128 -> "ITF"
        256 -> "QR_CODE"
        512 -> "UPC_A"
        1024 -> "UPC_E"
        2048 -> "PDF417"
        4096 -> "AZTEC"
        else -> "UNKNOWN"
    }
}
