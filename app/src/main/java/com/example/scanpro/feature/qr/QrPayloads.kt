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
}
