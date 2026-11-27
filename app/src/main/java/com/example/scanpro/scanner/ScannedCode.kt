package com.example.scanpro.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import java.net.URLDecoder
import java.util.Calendar
import java.util.TimeZone

/** A Wi-Fi code. [encryption] uses ML Kit's values (Barcode.WiFi.TYPE_OPEN, TYPE_WPA, TYPE_WEP). */
data class WifiCode(val ssid: String, val password: String, val encryption: Int, val sae: Boolean, val hidden: Boolean)

data class ContactCode(
    val name: String?,
    val organization: String?,
    val title: String?,
    val phones: List<String>,
    val emails: List<String>,
    val website: String?,
    val address: String?
)

data class SmsCode(val number: String, val message: String?)

data class EmailCode(val address: String, val subject: String?, val body: String?)

data class GeoCode(val lat: Double, val lng: Double)

data class EventCode(
    val summary: String?,
    val location: String?,
    val description: String?,
    val startMillis: Long?,
    val endMillis: Long?,
    val allDay: Boolean
)

/**
 * What a scanned code holds, in a form the result sheet can show without ML Kit's Barcode. A Barcode can't be
 * rebuilt from stored text, so live scans and scan history both go through this: [valueType] uses ML Kit's
 * Barcode.TYPE_* numbers and [format] its Barcode.FORMAT_* numbers.
 */
data class ScannedCode(
    val raw: String,
    val format: Int,
    val valueType: Int,
    val url: String? = null,
    val wifi: WifiCode? = null,
    val contact: ContactCode? = null,
    val phone: String? = null,
    val sms: SmsCode? = null,
    val email: EmailCode? = null,
    val geo: GeoCode? = null,
    val event: EventCode? = null
) {
    /** A readable summary for contacts and events; null for everything else, which shows the raw text. */
    val displayValue: String?
        get() = when (valueType) {
            Barcode.TYPE_CONTACT_INFO -> contact?.let { c ->
                listOfNotNull(c.name, c.organization, c.title).plus(c.phones).plus(c.emails)
                    .plus(listOfNotNull(c.website, c.address))
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .ifBlank { null }
            }
            Barcode.TYPE_CALENDAR_EVENT -> event?.let { e ->
                listOfNotNull(e.summary, e.location, e.description).filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
            }
            else -> null
        }
}

/** A short one-line description of [code] for lists and screen readers; a Wi-Fi code gives its network name, never its password. */
fun codeSummary(code: ScannedCode, limit: Int = 60): String {
    val text = if (code.valueType == Barcode.TYPE_WIFI) code.wifi?.ssid.orEmpty() else code.displayValue ?: code.url ?: code.raw
    val oneLine = text.replace(Regex("\\s+"), " ").trim()
    return if (oneLine.length > limit) oneLine.take(limit - 1) + "…" else oneLine
}

/** Builds a [ScannedCode] from ML Kit's result: the text is parsed here, contacts and events keep ML Kit's richer reading. */
fun scannedCodeFrom(barcode: Barcode): ScannedCode {
    val raw = barcode.rawValue.orEmpty()
    var code = parseScannedCode(raw, barcode.format, barcode.valueType)
    if (code.valueType == Barcode.TYPE_TEXT && barcode.valueType == Barcode.TYPE_URL) {
        code = code.copy(valueType = Barcode.TYPE_URL, url = barcode.url?.url ?: raw)
    }
    val info = barcode.contactInfo
    if (barcode.valueType == Barcode.TYPE_CONTACT_INFO && info != null) {
        code = code.copy(
            valueType = Barcode.TYPE_CONTACT_INFO,
            contact = ContactCode(
                name = info.name?.formattedName,
                organization = info.organization,
                title = info.title,
                phones = info.phones.mapNotNull { it.number },
                emails = info.emails.mapNotNull { it.address },
                website = info.urls.firstOrNull(),
                address = info.addresses.firstOrNull()?.addressLines?.joinToString(", ")
            )
        )
    }
    val event = barcode.calendarEvent
    // ML Kit reads times in UTC or the phone's zone only; a code that names its own zone keeps the text parser's reading.
    val ownEventKept = code.event != null && raw.contains("TZID=", ignoreCase = true)
    if (barcode.valueType == Barcode.TYPE_CALENDAR_EVENT && event != null && !ownEventKept) {
        code = code.copy(
            valueType = Barcode.TYPE_CALENDAR_EVENT,
            event = EventCode(
                summary = event.summary,
                location = event.location,
                description = event.description,
                startMillis = calendarMillis(event.start),
                endMillis = calendarMillis(event.end),
                allDay = event.start?.rawValue?.length?.let { it <= 8 } == true
            )
        )
    }
    return code
}

/**
 * Reads the standard text formats QR codes carry: Wi-Fi, vCard, MECARD, geo, mailto, tel, SMS, calendar events and web
 * links. [hintType] is ML Kit's own type, kept only for product and ISBN barcodes, whose text has no other clue.
 * Anything else is plain text. Pure Kotlin, so it runs in unit tests.
 */
fun parseScannedCode(raw: String, format: Int, hintType: Int = Barcode.TYPE_UNKNOWN): ScannedCode {
    val text = raw.trim()
    val lower = text.lowercase()
    val plain = ScannedCode(raw, format, Barcode.TYPE_TEXT)
    return when {
        lower.startsWith("wifi:") -> parseWifi(raw, format) ?: plain
        lower.startsWith("begin:vcard") -> parseVCard(raw, format) ?: plain
        lower.startsWith("mecard:") -> parseMeCard(raw, format) ?: plain
        lower.startsWith("begin:vevent") || (lower.startsWith("begin:vcalendar") && lower.contains("begin:vevent")) ->
            parseEvent(raw, format) ?: plain
        lower.startsWith("geo:") -> parseGeo(raw, format) ?: plain
        lower.startsWith("mailto:") -> parseMailto(raw, format) ?: plain
        lower.startsWith("tel:") -> {
            val number = text.substring(4).trim()
            if (number.isEmpty()) plain else plain.copy(valueType = Barcode.TYPE_PHONE, phone = number)
        }
        lower.startsWith("smsto:") || lower.startsWith("sms:") || lower.startsWith("mms:") || lower.startsWith("mmsto:") ->
            parseSms(raw, format) ?: plain
        lower.startsWith("http://") || lower.startsWith("https://") ->
            plain.copy(valueType = Barcode.TYPE_URL, url = text)
        hintType == Barcode.TYPE_PRODUCT || hintType == Barcode.TYPE_ISBN -> plain.copy(valueType = hintType)
        else -> plain
    }
}

/** Splits on each `;` that is not escaped; a backslash keeps the next character with its own field, even another backslash. */
private fun splitFields(text: String): List<String> {
    val parts = ArrayList<String>()
    val current = StringBuilder()
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        if (ch == '\\' && index + 1 < text.length) {
            current.append(ch).append(text[index + 1])
            index += 2
            continue
        }
        if (ch == ';') {
            parts.add(current.toString())
            current.setLength(0)
        } else {
            current.append(ch)
        }
        index++
    }
    parts.add(current.toString())
    return parts
}

private fun unescape(value: String): String = value.replace(Regex("\\\\(.)"), "$1")

/** The `KEY:value` fields as written: values are still escaped, so a quote that is part of a name can be told from one around it. */
private fun rawFieldMap(parts: List<String>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (part in parts) {
        val colon = part.indexOf(':')
        if (colon <= 0) continue
        val key = part.substring(0, colon).trim().uppercase()
        if (key !in map) map[key] = part.substring(colon + 1)
    }
    return map
}

/**
 * A Wi-Fi field's text. The Wi-Fi code format writes a name or password that looks like hexadecimal inside double quotes
 * (`S:"1234"`) to say it is the text itself, not hex digits; the quotes are not part of the value. A quote that belongs
 * to the value is escaped (`\"`), so it never starts the field and is kept.
 */
private fun wifiValue(raw: String?): String? {
    if (raw == null) return null
    val quoted = raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') && !raw.endsWith("\\\"")
    return unescape(if (quoted) raw.substring(1, raw.length - 1) else raw)
}

private fun parseWifi(raw: String, format: Int): ScannedCode? {
    val body = raw.trim().substring(5)
    val fields = rawFieldMap(splitFields(body))
    val ssid = wifiValue(fields["S"]) ?: return null
    val type = fields["T"].orEmpty().trim().uppercase()
    val sae = type == "SAE" || type == "WPA3"
    val encryption = when {
        type == "WEP" -> Barcode.WiFi.TYPE_WEP
        type.startsWith("WPA") || sae -> Barcode.WiFi.TYPE_WPA
        else -> Barcode.WiFi.TYPE_OPEN
    }
    val hidden = fields["H"].orEmpty().trim().equals("true", ignoreCase = true)
    return ScannedCode(
        raw, format, Barcode.TYPE_WIFI,
        wifi = WifiCode(ssid, wifiValue(fields["P"]).orEmpty(), encryption, sae, hidden)
    )
}

private fun parseVCard(raw: String, format: Int): ScannedCode? {
    // Folded lines continue on the next line after a space or tab.
    val lines = raw.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')
    var formatted: String? = null
    var structured: String? = null
    var organization: String? = null
    var title: String? = null
    var website: String? = null
    var address: String? = null
    val phones = ArrayList<String>()
    val emails = ArrayList<String>()
    for (line in lines) {
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        // iPhone and Google contacts write grouped lines ("item1.TEL;type=pref:..."): the group is not part of the name.
        val name = line.substring(0, colon).substringBefore(';').trim().uppercase().substringAfterLast('.')
        val value = vcardUnescape(line.substring(colon + 1)).trim()
        if (value.isEmpty()) continue
        when (name) {
            "FN" -> if (formatted == null) formatted = value
            "N" -> if (structured == null) {
                val parts = line.substring(colon + 1).split(';').map { vcardUnescape(it).trim() }
                structured = listOf(parts.getOrNull(3), parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(0))
                    .filterNotNull().filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
            }
            "ORG" -> if (organization == null) organization = value.replace(';', ' ').trim()
            "TITLE" -> if (title == null) title = value
            "URL" -> if (website == null) website = value
            "TEL" -> phones.add(value)
            "EMAIL" -> emails.add(value)
            "ADR" -> if (address == null) address = value.split(';').filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
        }
    }
    val contact = ContactCode(formatted ?: structured, organization, title, phones, emails, website, address)
    if (contact.name == null && phones.isEmpty() && emails.isEmpty()) return null
    return ScannedCode(raw, format, Barcode.TYPE_CONTACT_INFO, contact = contact)
}

private fun vcardUnescape(value: String): String =
    value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

private fun parseMeCard(raw: String, format: Int): ScannedCode? {
    val body = raw.trim().substring(7)
    val parts = splitFields(body)
    var name: String? = null
    var organization: String? = null
    var website: String? = null
    var address: String? = null
    val phones = ArrayList<String>()
    val emails = ArrayList<String>()
    for (part in parts) {
        val colon = part.indexOf(':')
        if (colon <= 0) continue
        val key = part.substring(0, colon).trim().uppercase()
        val value = unescape(part.substring(colon + 1)).trim()
        if (value.isEmpty()) continue
        when (key) {
            // MECARD writes the name as "Family,Given".
            "N" -> if (name == null) name = value.split(',').map { it.trim() }.reversed().filter { it.isNotEmpty() }.joinToString(" ")
            "ORG" -> if (organization == null) organization = value
            "URL" -> if (website == null) website = value
            "ADR" -> if (address == null) address = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            "TEL" -> phones.add(value)
            "EMAIL" -> emails.add(value)
        }
    }
    if (name == null && phones.isEmpty() && emails.isEmpty()) return null
    return ScannedCode(raw, format, Barcode.TYPE_CONTACT_INFO, contact = ContactCode(name, organization, null, phones, emails, website, address))
}

private fun parseEvent(raw: String, format: Int): ScannedCode? {
    val lines = raw.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')
    var summary: String? = null
    var location: String? = null
    var description: String? = null
    var start: String? = null
    var end: String? = null
    var startZone: String? = null
    var endZone: String? = null
    for (line in lines) {
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val head = line.substring(0, colon)
        val name = head.substringBefore(';').trim().uppercase().substringAfterLast('.')
        val value = vcardUnescape(line.substring(colon + 1)).trim()
        // "DTSTART;TZID=Europe/London:20260131T093000": the time is in that zone, not the phone's.
        val zone = head.split(';').drop(1).firstOrNull { it.trim().startsWith("TZID=", ignoreCase = true) }
            ?.substringAfter('=')?.trim()?.trim('"')
        when (name) {
            "SUMMARY" -> if (summary == null) summary = value.ifEmpty { null }
            "LOCATION" -> if (location == null) location = value.ifEmpty { null }
            "DESCRIPTION" -> if (description == null) description = value.ifEmpty { null }
            "DTSTART" -> if (start == null) { start = value; startZone = zone }
            "DTEND" -> if (end == null) { end = value; endZone = zone }
        }
    }
    if (summary == null && start == null && location == null) return null
    val event = EventCode(summary, location, description, icalMillis(start, startZone), icalMillis(end, endZone), allDay = start != null && start.length <= 8)
    return ScannedCode(raw, format, Barcode.TYPE_CALENDAR_EVENT, event = event)
}

/**
 * An iCalendar date or date-time (`20260131`, `20260131T093000`, `20260131T093000Z`) as milliseconds; null when it is
 * not one. [tzid] is the `TZID` parameter of the property, used when the value has no `Z`.
 */
internal fun icalMillis(value: String?, tzid: String? = null): Long? {
    val text = value?.trim().orEmpty()
    val match = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?$").matchEntire(text) ?: return null
    val groups = match.groupValues
    // A "Z" wins; else the code's own zone when it names one Java knows (getTimeZone would turn a bad name into GMT
    // without a word); else this device's zone.
    val zone = when {
        groups[7].isNotEmpty() -> TimeZone.getTimeZone("UTC")
        tzid != null && tzid in TimeZone.getAvailableIDs() -> TimeZone.getTimeZone(tzid)
        else -> TimeZone.getDefault()
    }
    return Calendar.getInstance(zone).apply {
        clear()
        set(
            groups[1].toInt(), groups[2].toInt() - 1, groups[3].toInt(),
            groups[4].toIntOrNull() ?: 0, groups[5].toIntOrNull() ?: 0, groups[6].toIntOrNull() ?: 0
        )
    }.timeInMillis
}

private fun parseGeo(raw: String, format: Int): ScannedCode? {
    val body = raw.trim().substring(4).substringBefore('?')
    val numbers = body.substringBefore(';').split(',')
    val lat = numbers.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
    val lng = numbers.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
    if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return null
    // "geo:0,0?q=34.99,-106.61(Label)" (Google Maps and many generators) puts the place in the query; 0,0 is only a stand-in.
    val place = if (lat == 0.0 && lng == 0.0) geoFromQuery(raw.trim().substring(4).substringAfter('?', "")) else null
    return ScannedCode(raw, format, Barcode.TYPE_GEO, geo = place ?: GeoCode(lat, lng))
}

/** The coordinates in a geo link's `q=lat,lng(label)` parameter; null when there is none or it is a name, not numbers. */
private fun geoFromQuery(query: String): GeoCode? {
    val q = queryParameters(query)["q"] ?: return null
    val numbers = q.substringBefore('(').split(',')
    val lat = numbers.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
    val lng = numbers.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) GeoCode(lat, lng) else null
}

/** Percent-decodes [value]; a plus sign stays a plus (mailto and sms links are not form data). */
internal fun decodeUriPart(value: String): String =
    runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

private fun queryParameters(query: String): Map<String, String> {
    val map = HashMap<String, String>()
    for (pair in query.split('&')) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        map.putIfAbsent(pair.substring(0, eq).lowercase(), decodeUriPart(pair.substring(eq + 1)))
    }
    return map
}

private fun parseMailto(raw: String, format: Int): ScannedCode? {
    val body = raw.trim().substring(7)
    val address = decodeUriPart(body.substringBefore('?')).trim()
    if (address.isEmpty()) return null
    val query = if (body.contains('?')) queryParameters(body.substringAfter('?')) else emptyMap()
    return ScannedCode(raw, format, Barcode.TYPE_EMAIL, email = EmailCode(address, query["subject"], query["body"]))
}

private fun parseSms(raw: String, format: Int): ScannedCode? {
    val text = raw.trim()
    val body = text.substring(text.indexOf(':') + 1)
    val isUri = text.lowercase().startsWith("sms:") || text.lowercase().startsWith("mms:")
    val number: String
    var message: String? = null
    if (isUri && body.contains('?')) {
        number = body.substringBefore('?')
        message = queryParameters(body.substringAfter('?'))["body"]
    } else if (!isUri && body.contains(':')) {
        // SMSTO:number:message
        number = body.substringBefore(':')
        message = body.substringAfter(':')
    } else {
        number = body
    }
    val clean = decodeUriPart(number).trim()
    if (clean.isEmpty()) return null
    return ScannedCode(raw, format, Barcode.TYPE_SMS, sms = SmsCode(clean, message?.ifEmpty { null }))
}
