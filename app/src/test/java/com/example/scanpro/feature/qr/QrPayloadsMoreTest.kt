package com.example.scanpro.feature.qr

import com.example.scanpro.scanner.parseScannedCode
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SMS, location and calendar payloads, and that the app's own scanner parser reads them back. */
class QrPayloadsMoreTest {
    @Test
    fun `sms keeps digits and plus and reads back as an SMS code`() {
        val payload = QrPayloads.sms("+91 98450-12345", " Running late: 10 min ")!!
        assertEquals("SMSTO:+919845012345:Running late: 10 min", payload)
        val code = parseScannedCode(payload, Barcode.FORMAT_QR_CODE)
        assertEquals(Barcode.TYPE_SMS, code.valueType)
        assertEquals("+919845012345", code.sms!!.number)
        assertEquals("Running late: 10 min", code.sms!!.message)
        assertNull(QrPayloads.sms("abc", "hi"))
    }

    @Test
    fun `coordinates accept a comma decimal mark and check their ranges`() {
        assertEquals(12.9716, QrPayloads.parseCoordinate(" 12,9716 ")!!, 1e-9)
        assertTrue(QrPayloads.isValidLatitude("-90"))
        assertFalse(QrPayloads.isValidLatitude("90.5"))
        assertTrue(QrPayloads.isValidLongitude("179.99"))
        assertFalse(QrPayloads.isValidLongitude("181"))
        assertNull(QrPayloads.parseCoordinate("12.9.7"))
        assertNull(QrPayloads.parseCoordinate("1e3"))
    }

    @Test
    fun `geo payload with and without a label reads back as a location`() {
        assertEquals("geo:12.9716,77.5946", QrPayloads.geo("12.97160", "77.5946"))
        val labelled = QrPayloads.geo("12.9716", "77.5946", "Cubbon Park")!!
        assertEquals("geo:12.9716,77.5946?q=12.9716,77.5946(Cubbon%20Park)", labelled)
        val code = parseScannedCode(labelled, Barcode.FORMAT_QR_CODE)
        assertEquals(Barcode.TYPE_GEO, code.valueType)
        assertEquals(77.5946, code.geo!!.lng, 1e-9)
        assertNull(QrPayloads.geo("95", "10"))
        assertNull(QrPayloads.geo("", "10"))
    }

    @Test
    fun `dates and times are checked as real calendar values`() {
        assertEquals("20280229", QrPayloads.icalDate("2028-02-29"))
        assertNull(QrPayloads.icalDate("2027-02-29"))
        assertNull(QrPayloads.icalDate("2026-13-01"))
        assertNull(QrPayloads.icalDate("16/11/2026"))
        assertEquals("093000", QrPayloads.icalTime("9:30"))
        assertEquals("235900", QrPayloads.icalTime("23.59"))
        assertNull(QrPayloads.icalTime("24:00"))
    }

    @Test
    fun `a timed event is a VEVENT the scanner reads back with escaped text`() {
        val payload = QrPayloads.event("Team lunch; Friday", "2026-11-20", "12:30", "", "14:00", "Café, MG Road", "Bring\nreceipts")!!
        assertTrue(payload.startsWith("BEGIN:VEVENT\r\n"))
        assertTrue(payload.contains("DTSTART:20261120T123000\r\n"))
        assertTrue("an end time alone is the same day", payload.contains("DTEND:20261120T140000\r\n"))
        assertTrue(payload.contains("SUMMARY:Team lunch\\; Friday"))
        assertTrue(payload.contains("LOCATION:Café\\, MG Road"))
        val code = parseScannedCode(payload, Barcode.FORMAT_QR_CODE)
        assertEquals(Barcode.TYPE_CALENDAR_EVENT, code.valueType)
        assertEquals("Team lunch; Friday", code.event!!.summary)
        assertEquals("Café, MG Road", code.event!!.location)
        assertEquals(90 * 60_000L, code.event!!.endMillis!! - code.event!!.startMillis!!)
        assertFalse(code.event!!.allDay)
    }

    @Test
    fun `an event without a time is all day`() {
        val payload = QrPayloads.event("Holiday", "2026-12-25", "", "2026-12-26", "", "", "")!!
        assertTrue(payload.contains("DTSTART;VALUE=DATE:20261225"))
        assertTrue(payload.contains("DTEND;VALUE=DATE:20261226"))
        assertTrue(parseScannedCode(payload, Barcode.FORMAT_QR_CODE).event!!.allDay)
    }

    @Test
    fun `an event needs a title and a start, and cannot end before it starts`() {
        assertNull(QrPayloads.event("", "2026-11-20", "", "", "", "", ""))
        assertNull(QrPayloads.event("X", "", "", "", "", "", ""))
        assertNull(QrPayloads.event("X", "2026-11-20", "25:00", "", "", "", ""))
        assertNull(QrPayloads.event("X", "2026-11-20", "10:00", "", "09:00", "", ""))
        assertTrue(QrPayloads.eventEndsBeforeStart("2026-11-20", "10:00", "", "09:00"))
        assertFalse(QrPayloads.eventEndsBeforeStart("2026-11-20", "10:00", "2026-11-21", "09:00"))
        assertFalse(QrPayloads.eventEndsBeforeStart("2026-11-20", "10:00", "", ""))
    }

    @Test
    fun `email check is a rough shape check`() {
        assertTrue(QrPayloads.looksLikeEmail(" asha@example.in "))
        assertFalse(QrPayloads.looksLikeEmail("asha@example"))
        assertFalse(QrPayloads.looksLikeEmail("asha example.in"))
    }
}
