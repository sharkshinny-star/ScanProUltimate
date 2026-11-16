package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSafetyTest {
    @Test
    fun `an ordinary https link has its host and no warnings`() {
        val report = LinkSafety.check("https://www.example.com/menu?table=4")!!
        assertEquals("www.example.com", report.host)
        assertTrue(report.warnings.isEmpty())
    }

    @Test
    fun `http, IP addresses, look-alike letters, at signs and shorteners are flagged`() {
        assertEquals(setOf(LinkWarning.NOT_ENCRYPTED), LinkSafety.check("http://example.com")!!.warnings)
        assertEquals(setOf(LinkWarning.IP_ADDRESS), LinkSafety.check("https://192.168.1.20:8080/login")!!.warnings)
        assertEquals("192.168.1.20", LinkSafety.check("https://192.168.1.20:8080/login")!!.host)
        assertEquals(setOf(LinkWarning.LOOKALIKE_LETTERS), LinkSafety.check("https://xn--pple-43d.com")!!.warnings)
        assertEquals(setOf(LinkWarning.LOOKALIKE_LETTERS), LinkSafety.check("https://аpple.com/")!!.warnings)
        val at = LinkSafety.check("https://mybank.com@evil.example/pay")!!
        assertEquals("evil.example", at.host)
        assertEquals(setOf(LinkWarning.MISLEADING_AT), at.warnings)
        assertEquals(setOf(LinkWarning.SHORTENED), LinkSafety.check("https://bit.ly/3abc")!!.warnings)
        assertTrue(LinkSafety.check("https://[::1]/")!!.warnings.contains(LinkWarning.IP_ADDRESS))
    }

    @Test
    fun `non-web addresses get no report`() {
        assertNull(LinkSafety.check("javascript:alert(1)"))
        assertNull(LinkSafety.check("intent://x#Intent;end"))
        assertNull(LinkSafety.check("https://"))
    }
}
