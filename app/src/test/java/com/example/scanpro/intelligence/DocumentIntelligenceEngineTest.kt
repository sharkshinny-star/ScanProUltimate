package com.example.scanpro.intelligence

import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIntelligenceEngineTest {
    @Test
    fun `invoice facts remain exact OCR substrings`() {
        val text = """
            TAX INVOICE
            Invoice No: INV-8831
            Date 08/10/2026
            Amount Due INR 12,450.00
            billing@example.com
        """.trimIndent()

        val insights = DocumentIntelligenceEngine.analyze(text)

        assertEquals(DocumentKind.INVOICE, insights.kind)
        assertTrue(insights.amounts.contains("INR 12,450.00"))
        assertTrue(insights.dates.contains("08/10/2026"))
        assertTrue(insights.emails.contains("billing@example.com"))
        assertTrue(insights.references.contains("INV-8831"))
    }

    @Test
    fun `action items are copied from OCR instead of invented`() {
        val text = "Please send the signed agreement by Friday.\nReference ABC-9911"
        val insights = DocumentIntelligenceEngine.analyze(text)
        assertTrue(insights.actionItems.contains("Please send the signed agreement by Friday."))
    }

    @Test
    fun `question answering refuses unsupported answers`() {
        val text = "Order 7128 ships on 08/10/2026. Total INR 900."
        val answer = DocumentIntelligenceEngine.answer("What is the customer address?", text)
        // The refusal, not a passage: in the user's language on a device, the resource id in JVM tests.
        assertEquals(AppStrings.get(R.string.assistant_no_match), answer)
    }

    @Test
    fun `question answering resolves summary and total queries`() {
        val text = "TAX INVOICE\nNorthwind Traders Pvt Ltd\nGSTIN 29ABCDE1234F1Z5\nSubtotal INR 900\nAmount due INR 1,062"
        val summaryAnswer = DocumentIntelligenceEngine.answer("Give me a summary", text)
        assertTrue(summaryAnswer.contains("Northwind Traders") && summaryAnswer.contains("1,062"))

        val totalAnswer = DocumentIntelligenceEngine.answer("What is the total amount due?", text)
        assertEquals("Total: INR 1,062", totalAnswer)
    }

    @Test
    fun receiptSummaryNamesTheShopTotalAndDate() {
        val text = "Receipt\nBlue Bottle Coffee\n12 Market Street\nDate: 03/10/2026\nLatte 4.50\nSubtotal 7.75\nTax 0.62\nTotal $8.37\nCash 10.00\nChange 1.63\nThank you"
        assertEquals("Receipt from Blue Bottle Coffee · Total $8.37 · 03/10/2026", DocumentIntelligenceEngine.analyze(text).summary)
    }

    @Test
    fun invoiceSummaryUsesTheAmountDue() {
        val text = "TAX INVOICE\nNorthwind Traders Pvt Ltd\nGSTIN 29ABCDE1234F1Z5\nSubtotal INR 900\nAmount due INR 1,062"
        assertEquals("Invoice from Northwind Traders Pvt Ltd · Total INR 1,062", DocumentIntelligenceEngine.analyze(text).summary)
    }

    @Test
    fun aRealBusinessCardIsRecognised() {
        val text = "Asha Rao\nDirector, Rao & Sons\nMobile +91 98765 43210\nasha@raoandsons.in\nwww.raoandsons.in"
        assertEquals(DocumentKind.BUSINESS_CARD, DocumentIntelligenceEngine.analyze(text).kind)
    }

    @Test
    fun aPhotoOfALaptopIsNotABusinessCard() {
        // OCR of a keyboard and a screen: a row of single digits, a stray "manager" and "email", but no way to reach anyone.
        val text = "esc F1 F2 F3 tab caps lock shift control option command\n1 2 3 4 5 6 7 8 9 0\nQ W E R T Y U I O P\nemail manager mobile"
        assertTrue(DocumentIntelligenceEngine.analyze(text).kind != DocumentKind.BUSINESS_CARD)
    }

    @Test
    fun aLongPageWithAnEmailAddressIsNotABusinessCard() {
        val text = "Dear team, " + "please read the attached report carefully before the meeting. ".repeat(20) +
            "Questions to the manager at help@example.com or the mobile desk."
        assertTrue(DocumentIntelligenceEngine.analyze(text).kind != DocumentKind.BUSINESS_CARD)
    }
}
