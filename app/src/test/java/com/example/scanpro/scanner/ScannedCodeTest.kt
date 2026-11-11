package com.example.scanpro.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scan history reopens a code from its text alone, so the standard formats must parse back to what the sheet needs. */
class ScannedCodeTest {
    private fun parse(raw: String, hint: Int = Barcode.TYPE_UNKNOWN) = parseScannedCode(raw, Barcode.FORMAT_QR_CODE, hint)

    @Test
    fun wifiWithEscapesAndHiddenFlag() {
        val code = parse("WIFI:T:WPA;S:cafe\\;guest;P:pa\\:ss\\\\word;H:true;;")
        assertEquals(Barcode.TYPE_WIFI, code.valueType)
        val wifi = code.wifi!!
        assertEquals("cafe;guest", wifi.ssid)
        assertEquals("pa:ss\\word", wifi.password)
        assertEquals(Barcode.WiFi.TYPE_WPA, wifi.encryption)
        assertTrue(wifi.hidden)
        assertFalse(wifi.sae)
    }

    @Test
    fun aPasswordEndingInABackslashDoesNotSwallowTheNextField() {
        val wifi = parse("WIFI:T:WPA;S:home;P:end\\\\;H:true;;").wifi!!
        assertEquals("end\\", wifi.password)
        assertTrue(wifi.hidden)
    }

    @Test
    fun wifiSaeIsWpaWithTheSaeFlag() {
        val wifi = parse("WIFI:T:SAE;S:home;P:secret;;").wifi!!
        assertEquals(Barcode.WiFi.TYPE_WPA, wifi.encryption)
        assertTrue(wifi.sae)
    }

    @Test
    fun wifiOpenAndWep() {
        assertEquals(Barcode.WiFi.TYPE_OPEN, parse("WIFI:T:nopass;S:guest;;").wifi!!.encryption)
        assertEquals(Barcode.WiFi.TYPE_OPEN, parse("WIFI:S:guest;;").wifi!!.encryption)
        assertEquals(Barcode.WiFi.TYPE_WEP, parse("WIFI:T:WEP;S:old;P:12345;;").wifi!!.encryption)
    }

    @Test
    fun wifiWithoutANetworkNameIsPlainText() {
        assertEquals(Barcode.TYPE_TEXT, parse("WIFI:T:WPA;P:secret;;").valueType)
    }

    @Test
    fun vCardGivesNamePhonesAndEmails() {
        val raw = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Rao;Asha;;;\r\nFN:Asha Rao\r\nORG:Acme\\, Inc\r\nTITLE:Engineer\r\n" +
            "TEL;TYPE=CELL:+91 98765 43210\r\nEMAIL:asha@example.com\r\nURL:https://example.com\r\nEND:VCARD"
        val code = parse(raw)
        assertEquals(Barcode.TYPE_CONTACT_INFO, code.valueType)
        val contact = code.contact!!
        assertEquals("Asha Rao", contact.name)
        assertEquals("Acme, Inc", contact.organization)
        assertEquals("Engineer", contact.title)
        assertEquals(listOf("+91 98765 43210"), contact.phones)
        assertEquals(listOf("asha@example.com"), contact.emails)
        assertEquals("https://example.com", contact.website)
        assertTrue(code.displayValue!!.contains("Asha Rao"))
    }

    @Test
    fun vCardWithoutFormattedNameUsesTheStructuredName() {
        val contact = parse("BEGIN:VCARD\nVERSION:3.0\nN:Rao;Asha;;;\nTEL:123\nEND:VCARD").contact!!
        assertEquals("Asha Rao", contact.name)
    }

    @Test
    fun meCardNameIsFamilyCommaGiven() {
        val code = parse("MECARD:N:Rao,Asha;TEL:+919876543210;EMAIL:asha@example.com;;")
        assertEquals(Barcode.TYPE_CONTACT_INFO, code.valueType)
        val contact = code.contact!!
        assertEquals("Asha Rao", contact.name)
        assertEquals(listOf("+919876543210"), contact.phones)
        assertEquals(listOf("asha@example.com"), contact.emails)
    }

    @Test
    fun geoReadsLatitudeAndLongitude() {
        val geo = parse("geo:12.9716,77.5946?q=Bengaluru").geo!!
        assertEquals(12.9716, geo.lat, 1e-9)
        assertEquals(77.5946, geo.lng, 1e-9)
        assertEquals(Barcode.TYPE_TEXT, parse("geo:123,45").valueType)
        assertEquals(Barcode.TYPE_TEXT, parse("geo:abc").valueType)
    }

    @Test
    fun mailtoReadsAddressSubjectAndBody() {
        val email = parse("mailto:team@example.com?subject=Hello%20there&body=Line%201%2B2").email!!
        assertEquals("team@example.com", email.address)
        assertEquals("Hello there", email.subject)
        assertEquals("Line 1+2", email.body)
        assertNull(parse("mailto:team@example.com").email!!.subject)
    }

    @Test
    fun telAndSms() {
        val phone = parse("tel:+911234567890")
        assertEquals(Barcode.TYPE_PHONE, phone.valueType)
        assertEquals("+911234567890", phone.phone)

        val smsTo = parse("SMSTO:+911234567890:See you at 5").sms!!
        assertEquals("+911234567890", smsTo.number)
        assertEquals("See you at 5", smsTo.message)

        val smsUri = parse("sms:+911234567890?body=Hi%20there").sms!!
        assertEquals("+911234567890", smsUri.number)
        assertEquals("Hi there", smsUri.message)

        assertNull(parse("smsto:12345").sms!!.message)
    }

    @Test
    fun calendarEventReadsSummaryAndTimes() {
        val raw = "BEGIN:VEVENT\nSUMMARY:Team lunch\nLOCATION:Canteen\nDTSTART:20260131T093000Z\nDTEND:20260131T103000Z\nEND:VEVENT"
        val code = parse(raw)
        assertEquals(Barcode.TYPE_CALENDAR_EVENT, code.valueType)
        val event = code.event!!
        assertEquals("Team lunch", event.summary)
        assertEquals("Canteen", event.location)
        assertEquals(3_600_000L, event.endMillis!! - event.startMillis!!)
        assertFalse(event.allDay)
        assertEquals(1_769_851_800_000L, event.startMillis)
    }

    @Test
    fun allDayEventHasADateOnlyStart() {
        val event = parse("BEGIN:VEVENT\nSUMMARY:Holiday\nDTSTART:20261231\nEND:VEVENT").event!!
        assertTrue(event.allDay)
        assertNotNull(event.startMillis)
    }

    @Test
    fun icalMillisRejectsOtherText() {
        assertNull(icalMillis("tomorrow"))
        assertNull(icalMillis(null))
        assertEquals(0L, icalMillis("19700101T000000Z"))
    }

    @Test
    fun webLinksAreLinksButOtherSchemesAreNot() {
        val link = parse("https://example.com/a?b=c")
        assertEquals(Barcode.TYPE_URL, link.valueType)
        assertEquals("https://example.com/a?b=c", link.url)
        assertEquals(Barcode.TYPE_TEXT, parse("javascript:alert(1)").valueType)
        assertEquals(Barcode.TYPE_TEXT, parse("upi://pay?pa=shop@bank&pn=Shop").valueType)
        assertEquals("upi://pay?pa=shop@bank&pn=Shop", parse("upi://pay?pa=shop@bank&pn=Shop").raw)
    }

    @Test
    fun productBarcodesKeepMlKitsType() {
        val code = parseScannedCode("9780306406157", Barcode.FORMAT_EAN_13, Barcode.TYPE_ISBN)
        assertEquals(Barcode.TYPE_ISBN, code.valueType)
        assertEquals(Barcode.TYPE_TEXT, parse("just some words").valueType)
    }

    @Test
    fun summaryNeverShowsAWifiPassword() {
        val summary = codeSummary(parse("WIFI:T:WPA;S:home;P:hunter2;;"))
        assertEquals("home", summary)
        assertFalse(summary.contains("hunter2"))
    }

    @Test
    fun summaryIsOneLineAndShortened() {
        val summary = codeSummary(parse("line one\nline two " + "x".repeat(200)), limit = 20)
        assertEquals(20, summary.length)
        assertFalse(summary.contains('\n'))
        assertTrue(summary.endsWith("…"))
    }
}
