package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `receipt is named by kind, shop and date`() {
        val text = "Receipt\nBlue Bottle Coffee\n12 Market St\nDate: 03/10/2026\nLatte 4.50\nTotal $4.50\nCash 5.00\nChange 0.50\nThank you"
        assertEquals("Receipt – Blue Bottle Coffee – 03-10-2026", SmartTitle.fromOcr(text, "Fallback"))
    }

    @Test
    fun `invoice skips generic headings and picks the issuer`() {
        val text = "TAX INVOICE\nNorthwind Traders Pvt Ltd\nGSTIN 29ABCDE1234F1Z5\nInvoice No: INV-2041\nDate 12 Mar 2026\nSubtotal 900\nAmount due INR 1,062"
        assertEquals("Invoice – Northwind Traders Pvt Ltd – 12 Mar 2026", SmartTitle.fromOcr(text, "Fallback"))
    }

    @Test
    fun `letter uses its subject line`() {
        val text = "Dear Ms Rao,\nSubject: Renewal of lease\nPlease find attached the renewal terms.\nSincerely,\nAnand"
        assertEquals("Letter – Renewal of lease", SmartTitle.fromOcr(text, "Fallback"))
    }

    @Test
    fun `identity documents never put the name or number in the title`() {
        val text = "REPUBLIC OF INDIA\nPassport\nSurname SHARMA\nGiven names PRIYA\nNationality INDIAN\nDate of birth 01/02/1990\nP1234567"
        assertEquals("ID – Passport", SmartTitle.fromOcr(text, "Fallback"))
    }

    @Test
    fun `long titles are cut at a word boundary`() {
        val text = "Receipt\n" + "Very Long Restaurant Name With Many Words Going On And On Forever Bistro\nTotal 9.00 cash change"
        val title = SmartTitle.fromOcr(text, "Fallback")
        assertTrue(title.length <= 72)
        assertFalse(title.endsWith(" "))
    }
}
