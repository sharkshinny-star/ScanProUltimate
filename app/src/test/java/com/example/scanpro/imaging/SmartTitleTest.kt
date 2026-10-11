package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTitleTest {
    @Test
    fun `invoice title is concise and classified`() {
        val value = SmartTitle.fromOcr("ACME SERVICES\nTax Invoice\nAmount INR 900", "Fallback")
        assertTrue(value.startsWith("Invoice"))
        assertTrue(value.length <= 72)
    }

    @Test
    fun `falls back when OCR has no useful line`() {
        assertEquals("Fallback", SmartTitle.fromOcr("x\n1", "Fallback"))
    }
}
