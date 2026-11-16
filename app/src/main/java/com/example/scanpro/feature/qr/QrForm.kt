package com.example.scanpro.feature.qr

/** What the generator can make, and the code format each one is drawn in. */
enum class QrKind(val format: CodeFormat) {
    TEXT(CodeFormat.QR),
    LINK(CodeFormat.QR),
    WIFI(CodeFormat.QR),
    PHONE(CodeFormat.QR),
    SMS(CodeFormat.QR),
    EMAIL(CodeFormat.QR),
    CONTACT(CodeFormat.QR),
    LOCATION(CodeFormat.QR),
    EVENT(CodeFormat.QR),
    UPI(CodeFormat.QR),
    EAN_13(CodeFormat.EAN_13),
    CODE_128(CodeFormat.CODE_128)
}

/** A problem with one typed field, shown under it. Empty fields never have one: they are simply not filled in yet. */
enum class FieldProblem {
    LINK, EMAIL, PHONE, LATITUDE, LONGITUDE, DATE, TIME, END_BEFORE_START, AMOUNT,
    EAN_DIGITS, EAN_LENGTH, EAN_CHECK, CODE_128_CHARACTERS, CODE_128_TOO_LONG
}

/**
 * The generator's form as plain data: every field is a string under one of the keys below, so the screen can keep
 * them all in one saveable map and switching between kinds keeps what was typed. Pure Kotlin, unit tested.
 */
object QrForm {
    const val TEXT = "text"
    const val LINK = "link"
    const val SSID = "ssid"
    const val WIFI_PASSWORD = "wifiPassword"
    const val SECURITY = "security"
    const val HIDDEN = "hidden"
    const val PHONE = "phone"
    const val SMS_NUMBER = "smsNumber"
    const val SMS_MESSAGE = "smsMessage"
    const val EMAIL = "email"
    const val SUBJECT = "subject"
    const val BODY = "body"
    const val NAME = "name"
    const val CONTACT_PHONE = "contactPhone"
    const val CONTACT_EMAIL = "contactEmail"
    const val LATITUDE = "lat"
    const val LONGITUDE = "lng"
    const val PLACE = "place"
    const val EVENT_TITLE = "eventTitle"
    const val START_DATE = "startDate"
    const val START_TIME = "startTime"
    const val END_DATE = "endDate"
    const val END_TIME = "endTime"
    const val EVENT_LOCATION = "eventLocation"
    const val EVENT_DESCRIPTION = "eventDescription"
    const val UPI_ADDRESS = "upiAddress"
    const val UPI_NAME = "upiName"
    const val UPI_AMOUNT = "upiAmount"
    const val UPI_NOTE = "upiNote"
    const val EAN = "ean"
    const val CODE_128 = "code128"

    fun security(values: Map<String, String>): WifiSecurity =
        WifiSecurity.entries.firstOrNull { it.name == values[SECURITY] } ?: WifiSecurity.WPA

    /** The text the code will hold, or null while required fields are empty or something is invalid. */
    fun payload(kind: QrKind, values: Map<String, String>): String? {
        fun v(key: String) = values[key].orEmpty()
        return when (kind) {
            QrKind.TEXT -> QrPayloads.text(v(TEXT))
            QrKind.LINK -> QrPayloads.link(v(LINK))
            QrKind.WIFI -> QrPayloads.wifi(v(SSID), v(WIFI_PASSWORD), security(values), v(HIDDEN) == "true")
            QrKind.PHONE -> QrPayloads.phone(v(PHONE))
            QrKind.SMS -> QrPayloads.sms(v(SMS_NUMBER), v(SMS_MESSAGE))
            QrKind.EMAIL -> if (QrPayloads.looksLikeEmail(v(EMAIL))) QrPayloads.email(v(EMAIL), v(SUBJECT), v(BODY)) else null
            QrKind.CONTACT -> QrPayloads.contact(v(NAME), v(CONTACT_PHONE), v(CONTACT_EMAIL))
            QrKind.LOCATION -> QrPayloads.geo(v(LATITUDE), v(LONGITUDE), v(PLACE))
            QrKind.EVENT -> QrPayloads.event(
                v(EVENT_TITLE), v(START_DATE), v(START_TIME), v(END_DATE), v(END_TIME), v(EVENT_LOCATION), v(EVENT_DESCRIPTION)
            )
            QrKind.UPI -> QrPayloads.upi(v(UPI_ADDRESS), v(UPI_NAME), v(UPI_AMOUNT), v(UPI_NOTE))
            QrKind.EAN_13 -> BarcodeChecks.ean13(v(EAN))
            QrKind.CODE_128 -> BarcodeChecks.code128(v(CODE_128))
        }
    }

    /** What is wrong with the field [key], if anything; null for empty fields and fields without checks. */
    fun problem(key: String, values: Map<String, String>): FieldProblem? {
        val value = values[key].orEmpty()
        if (value.isBlank()) return null
        return when (key) {
            LINK -> FieldProblem.LINK.takeIf { QrPayloads.link(value) == null }
            EMAIL, CONTACT_EMAIL -> FieldProblem.EMAIL.takeIf { !QrPayloads.looksLikeEmail(value) }
            PHONE, SMS_NUMBER, CONTACT_PHONE -> FieldProblem.PHONE.takeIf { value.none { it.isDigit() } }
            LATITUDE -> FieldProblem.LATITUDE.takeIf { !QrPayloads.isValidLatitude(value) }
            LONGITUDE -> FieldProblem.LONGITUDE.takeIf { !QrPayloads.isValidLongitude(value) }
            START_DATE -> FieldProblem.DATE.takeIf { QrPayloads.icalDate(value) == null }
            END_DATE -> when {
                QrPayloads.icalDate(value) == null -> FieldProblem.DATE
                endsEarly(values) -> FieldProblem.END_BEFORE_START
                else -> null
            }
            START_TIME -> FieldProblem.TIME.takeIf { QrPayloads.icalTime(value) == null }
            END_TIME -> when {
                QrPayloads.icalTime(value) == null -> FieldProblem.TIME
                endsEarly(values) -> FieldProblem.END_BEFORE_START
                else -> null
            }
            UPI_AMOUNT -> FieldProblem.AMOUNT.takeIf { !QrPayloads.isValidUpiAmount(value) }
            EAN -> when (BarcodeChecks.ean13Problem(value)) {
                Ean13Problem.NOT_DIGITS -> FieldProblem.EAN_DIGITS
                Ean13Problem.WRONG_LENGTH -> FieldProblem.EAN_LENGTH
                Ean13Problem.WRONG_CHECK_DIGIT -> FieldProblem.EAN_CHECK
                null -> null
            }
            CODE_128 -> when (BarcodeChecks.code128Problem(value)) {
                Code128Problem.UNSUPPORTED_CHARACTERS -> FieldProblem.CODE_128_CHARACTERS
                Code128Problem.TOO_LONG -> FieldProblem.CODE_128_TOO_LONG
                null -> null
            }
            else -> null
        }
    }

    private fun endsEarly(values: Map<String, String>): Boolean = QrPayloads.eventEndsBeforeStart(
        values[START_DATE].orEmpty(), values[START_TIME].orEmpty(), values[END_DATE].orEmpty(), values[END_TIME].orEmpty()
    )

    /** The check digit an EAN-13 number of 12 typed digits will get, for the field's helper line; null otherwise. */
    fun eanCheckDigitToAdd(values: Map<String, String>): Int? {
        val digits = values[EAN].orEmpty().filterNot { it == ' ' || it == '-' }
        return if (digits.length == 12 && digits.all { it in '0'..'9' }) BarcodeChecks.ean13CheckDigit(digits) else null
    }

    /** The check digit a 13-digit EAN should end with, for the "should be" message. */
    fun eanExpectedCheckDigit(values: Map<String, String>): Int? {
        val digits = values[EAN].orEmpty().filterNot { it == ' ' || it == '-' }
        return if (digits.length == 13 && digits.all { it in '0'..'9' }) BarcodeChecks.ean13CheckDigit(digits.take(12)) else null
    }
}
