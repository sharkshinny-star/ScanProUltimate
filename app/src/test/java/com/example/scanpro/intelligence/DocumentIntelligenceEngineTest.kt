package com.example.scanpro.intelligence

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
        assertTrue(answer.contains("couldn't find", ignoreCase = true))
    }
}
