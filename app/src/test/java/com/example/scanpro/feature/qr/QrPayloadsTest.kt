package com.example.scanpro.feature.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadsTest {
    @Test
    fun `wifi escapes backslash semicolon comma colon and quote`() {
        assertEquals("a\\\\b\\;c\\,d\\:e\\\"f", QrPayloads.escapeWifi("a\\b;c,d:e\"f"))
        assertEquals(
            "WIFI:T:WPA;S:cafe\\;guest;P:p\\:w\\\\d;;",
            QrPayloads.wifi("cafe;guest", "p:w\\d", WifiSecurity.WPA, hidden = false)
        )
    }

    @Test
    fun `wifi hidden and open networks`() {
        assertEquals("WIFI:T:WEP;S:old;P:12345;H:true;;", QrPayloads.wifi("old", "12345", WifiSecurity.WEP, hidden = true))
        // An open network has no password field, whatever was typed before switching to None.
        assertEquals("WIFI:T:nopass;S:guest;;", QrPayloads.wifi("guest", "ignored", WifiSecurity.NONE, hidden = false))
    }

    @Test
    fun `wifi needs a name, and a password unless open`() {
        assertNull(QrPayloads.wifi("", "pw", WifiSecurity.WPA, false))
        assertNull(QrPayloads.wifi("home", "", WifiSecurity.WPA, false))
        assertEquals("WIFI:T:nopass;S:home;;", QrPayloads.wifi("home", "", WifiSecurity.NONE, false))
    }

    @Test
    fun `links get https when they have no scheme`() {
        assertEquals("https://example.com/a", QrPayloads.link(" example.com/a "))
        assertEquals("http://example.com", QrPayloads.link("http://example.com"))
        assertEquals("mailto:a@b.com", QrPayloads.link("mailto:a@b.com"))
        assertNull(QrPayloads.link("   "))
        assertNull(QrPayloads.link("two words"))
    }

    @Test
    fun `text is kept as typed but blank gives nothing`() {
        assertEquals("  keep me ", QrPayloads.text("  keep me "))
        assertNull(QrPayloads.text("   "))
    }

    @Test
    fun `phone keeps digits and a leading plus only`() {
        assertEquals("tel:+919876543210", QrPayloads.phone("+91 (98765) 43-210"))
        assertNull(QrPayloads.phone("call me"))
    }

    @Test
    fun `email encodes subject and body`() {
        assertEquals("mailto:a@b.com", QrPayloads.email("a@b.com", "", ""))
        assertEquals(
            "mailto:a@b.com?subject=Hi%20there%20%26%20you&body=Line%201%0ALine%202",
            QrPayloads.email(" a@b.com ", "Hi there & you", "Line 1\nLine 2")
        )
        assertNull(QrPayloads.email("", "s", "b"))
        assertNull(QrPayloads.email("a b@c.com", "", ""))
    }

    @Test
    fun `contact is a vCard 3 with escaped text`() {
        val card = QrPayloads.contact("Asha Devi Rao", "+91 98765 43210", "asha@example.com")!!
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Rao;Asha Devi;;;\r\nFN:Asha Devi Rao\r\nTEL:+91 98765 43210\r\nEMAIL:asha@example.com\r\nEND:VCARD",
            card
        )
        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Rao;;;;\r\nFN:Rao\r\nEND:VCARD", QrPayloads.contact("Rao", "", ""))
        assertTrue(QrPayloads.contact("Rao; Asha, Jr", "", "")!!.contains("FN:Rao\\; Asha\\, Jr"))
        assertNull(QrPayloads.contact("  ", "123", "a@b.com"))
    }

    @Test
    fun `upi builds a pay link with encoded values and INR`() {
        assertEquals(
            "upi://pay?pa=shop@okbank&pn=Fresh%20%26%20Co&am=250.50&cu=INR&tn=Order%20%2312",
            QrPayloads.upi("shop@okbank", "Fresh & Co", "250.50", "Order #12")
        )
        assertEquals("upi://pay?pa=shop@okbank&cu=INR", QrPayloads.upi(" shop@okbank ", "", "", ""))
    }

    @Test
    fun `upi needs an address and a sensible amount`() {
        assertNull(QrPayloads.upi("", "Shop", "10", ""))
        assertNull(QrPayloads.upi("shop@bank", "Shop", "abc", ""))
        assertNull(QrPayloads.upi("shop@bank", "Shop", "0", ""))
        assertNull(QrPayloads.upi("shop@bank", "Shop", "1.234", ""))
        assertNull(QrPayloads.upi("shop@bank", "Shop", "-5", ""))
        assertTrue(QrPayloads.isValidUpiAmount(""))
        assertTrue(QrPayloads.isValidUpiAmount("99"))
        assertTrue(QrPayloads.isValidUpiAmount("99.5"))
        assertFalse(QrPayloads.isValidUpiAmount("99,50"))
    }
}
