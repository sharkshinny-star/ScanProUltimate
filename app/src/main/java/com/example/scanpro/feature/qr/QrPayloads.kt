package com.example.scanpro.feature.qr

import java.net.URLEncoder

/** The Wi-Fi security choices of the generator. */
enum class WifiSecurity(val code: String) { WPA("WPA"), WEP("WEP"), NONE("nopass") }

/**
 * The text each kind of QR code carries, as pure functions so they can be unit tested. Each returns null while the
 * required fields are still empty or invalid, so the screen shows no code until there is something worth scanning.
 */
object QrPayloads {
    fun text(value: String): String? = value.takeIf { it.isNotBlank() }

    /** A web address; one typed without a scheme ("example.com") gets https:// in front. */
    fun link(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    }

    /** Backslash, semicolon, comma, colon and double quote are escaped with a backslash, as the Wi-Fi code format asks. */
    fun escapeWifi(value: String): String {
        val out = StringBuilder(value.length + 4)
        for (ch in value) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }

    fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean): String? {
        if (ssid.isEmpty() || (security != WifiSecurity.NONE && password.isEmpty())) return null
        val builder = StringBuilder("WIFI:T:").append(security.code).append(";S:").append(escapeWifi(ssid)).append(';')
        if (security != WifiSecurity.NONE) builder.append("P:").append(escapeWifi(password)).append(';')
        if (hidden) builder.append("H:true;")
        return builder.append(';').toString()
    }

    /** A phone link: spaces, dashes and brackets are dropped, a leading plus and digits (and * #) are kept. */
    fun phone(value: String): String? {
        val number = value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return if (number.any { it.isDigit() }) "tel:$number" else null
    }

    /** Percent-encoding with %20 for spaces, as links need it (URLEncoder would write a plus). */
    fun percentEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun email(address: String, subject: String, body: String): String? {
        val to = address.trim()
        if (to.isEmpty() || to.any { it.isWhitespace() }) return null
        val parts = ArrayList<String>()
        if (subject.isNotBlank()) parts.add("subject=" + percentEncode(subject))
        if (body.isNotBlank()) parts.add("body=" + percentEncode(body))
        return "mailto:$to" + if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    /** Backslash, comma, semicolon and line breaks are escaped, as vCard text values need. */
    fun escapeVCard(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    /** A vCard 3.0 contact. The name is required; phone and email are added when given. */
    fun contact(name: String, phone: String, email: String): String? {
        val full = name.trim()
        if (full.isEmpty()) return null
        val split = full.lastIndexOf(' ')
        val given = if (split > 0) full.substring(0, split).trim() else ""
        val family = if (split > 0) full.substring(split + 1) else full
        val lines = ArrayList<String>()
        lines.add("BEGIN:VCARD")
        lines.add("VERSION:3.0")
        lines.add("N:" + escapeVCard(family) + ";" + escapeVCard(given) + ";;;")
        lines.add("FN:" + escapeVCard(full))
        if (phone.isNotBlank()) lines.add("TEL:" + escapeVCard(phone.trim()))
        if (email.isNotBlank()) lines.add("EMAIL:" + escapeVCard(email.trim()))
        lines.add("END:VCARD")
        return lines.joinToString("\r\n")
    }

    /** True for an empty amount or a positive number with at most two decimals, like 250 or 99.50. */
    fun isValidUpiAmount(value: String): Boolean {
        val amount = value.trim()
        if (amount.isEmpty()) return true
        if (!Regex("^\\d{1,9}(\\.\\d{1,2})?$").matches(amount)) return false
        return amount.toDouble() > 0.0
    }

    /** A UPI payment link: the payee address is required, the amount must be valid when given; the currency is INR. */
    fun upi(payeeAddress: String, payeeName: String, amount: String, note: String): String? {
        val address = payeeAddress.trim()
        if (address.isEmpty() || address.any { it.isWhitespace() } || !isValidUpiAmount(amount)) return null
        val query = ArrayList<String>()
        // The address keeps its @ unescaped, as payment apps expect.
        query.add("pa=" + percentEncode(address).replace("%40", "@"))
        if (payeeName.isNotBlank()) query.add("pn=" + percentEncode(payeeName.trim()))
        if (amount.isNotBlank()) query.add("am=" + amount.trim())
        query.add("cu=INR")
        if (note.isNotBlank()) query.add("tn=" + percentEncode(note.trim()))
        return "upi://pay?" + query.joinToString("&")
    }
    /** A text message: SMSTO:number:message, the form phone scanners read. The number is cleaned as in [phone]. */
    fun sms(number: String, message: String): String? {
        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.none { it.isDigit() }) return null
        return "SMSTO:$digits:" + message.trim()
    }

    /** A coordinate typed by hand: a decimal number, a comma also accepted as the decimal mark; null when not a number. */
    fun parseCoordinate(value: String): Double? {
        val text = value.trim().replace(',', '.')
        if (!Regex("^[+-]?\\d{1,3}(\\.\\d+)?$").matches(text)) return null
        return text.toDoubleOrNull()
    }

    fun isValidLatitude(value: String): Boolean = parseCoordinate(value)?.let { it in -90.0..90.0 } == true
    fun isValidLongitude(value: String): Boolean = parseCoordinate(value)?.let { it in -180.0..180.0 } == true

    /** A map location: geo:latitude,longitude, with a place name as the query when one is given. */
    fun geo(latitude: String, longitude: String, label: String = ""): String? {
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) return null
        val lat = parseCoordinate(latitude)!!.toPlainCoordinate()
        val lng = parseCoordinate(longitude)!!.toPlainCoordinate()
        val name = label.trim()
        return "geo:$lat,$lng" + if (name.isEmpty()) "" else "?q=$lat,$lng(" + percentEncode(name) + ")"
    }

    private fun Double.toPlainCoordinate(): String = java.math.BigDecimal(this.toString()).stripTrailingZeros().toPlainString()

    /** A date typed as YYYY-MM-DD, as the eight digits iCalendar wants; null when it is not a real date. */
    fun icalDate(value: String): String? {
        val match = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(value.trim()) ?: return null
        val (y, m, d) = match.destructured
        val year = y.toInt()
        val month = m.toInt()
        val day = d.toInt()
        if (month !in 1..12 || day < 1 || day > daysIn(year, month)) return null
        return "%04d%02d%02d".format(java.util.Locale.ROOT, year, month, day)
    }

    /** A time typed as HH:MM (24-hour), as HHMMSS; null when it is not a real time. */
    fun icalTime(value: String): String? {
        val match = Regex("^(\\d{1,2})[:.](\\d{2})$").matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null
        return "%02d%02d00".format(java.util.Locale.ROOT, hour, minute)
    }

    private fun daysIn(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Backslash, semicolon, comma and line breaks are escaped, as iCalendar text values need. */
    fun escapeIcal(value: String): String = escapeVCard(value)

    /**
     * A calendar event (an iCalendar VEVENT). The title and start date are required. With no start time it is an
     * all-day event; times are the phone's local time. The end is optional but must not be before the start.
     */
    fun event(
        title: String,
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String,
        location: String,
        description: String
    ): String? {
        val summary = title.trim()
        if (summary.isEmpty()) return null
        val start = eventStamp(startDate, startTime) ?: return null
        val end = if (endDate.isBlank() && endTime.isBlank()) {
            null
        } else {
            // An end time alone means the same day as the start.
            eventStamp(endDate.ifBlank { startDate }, endTime) ?: return null
        }
        if (end != null && (end.length != start.length || end < start)) return null
        val lines = ArrayList<String>()
        lines.add("BEGIN:VEVENT")
        lines.add("SUMMARY:" + escapeIcal(summary))
        lines.add(if (start.length == 8) "DTSTART;VALUE=DATE:$start" else "DTSTART:$start")
        if (end != null) lines.add(if (end.length == 8) "DTEND;VALUE=DATE:$end" else "DTEND:$end")
        if (location.isNotBlank()) lines.add("LOCATION:" + escapeIcal(location.trim()))
        if (description.isNotBlank()) lines.add("DESCRIPTION:" + escapeIcal(description.trim()))
        lines.add("END:VEVENT")
        return lines.joinToString("\r\n")
    }

    /** True when the end typed for an event is before its start (both valid); for the form's error. */
    fun eventEndsBeforeStart(startDate: String, startTime: String, endDate: String, endTime: String): Boolean {
        if (endDate.isBlank() && endTime.isBlank()) return false
        val start = eventStamp(startDate, startTime) ?: return false
        val end = eventStamp(endDate.ifBlank { startDate }, endTime) ?: return false
        return end.length == start.length && end < start
    }

    private fun eventStamp(date: String, time: String): String? {
        val day = icalDate(date) ?: return null
        if (time.isBlank()) return day
        return day + "T" + (icalTime(time) ?: return null)
    }

    /** A rough check that [value] looks like an email address (one @, a dot after it, no spaces); for the form's hint. */
    fun looksLikeEmail(value: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(value.trim())
}
