package com.example.scanpro.intelligence

import com.example.scanpro.data.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutTest {
    /** A word at [left] on the line whose top is [top]; 0.01 of the page per character, lines 0.02 tall. */
    private fun w(text: String, left: Float, top: Float) = OcrWord(text, left, top, left + text.length * 0.01f, top + 0.02f)

    @Test
    fun `words are grouped into lines left to right even when OCR lists them out of order`() {
        val lines = OcrLayout.lines(listOf(w("world", 0.2f, 0.1f), w("Second", 0.1f, 0.2f), w("Hello", 0.1f, 0.101f)))
        assertEquals(listOf("Hello world", "Second"), lines.map { it.text })
    }

    @Test
    fun `a receipt becomes a table with item and price columns`() {
        val words = listOf(
            w("Blue", 0.1f, 0.05f), w("Bottle", 0.15f, 0.05f), w("Coffee", 0.22f, 0.05f),
            w("Latte", 0.1f, 0.1f), w("4.50", 0.7f, 0.1f),
            w("Iced", 0.1f, 0.13f), w("tea", 0.15f, 0.13f), w("3.25", 0.7f, 0.13f),
            w("Total", 0.1f, 0.16f), w("$8.37", 0.69f, 0.16f)
        )
        assertEquals(
            listOf(
                listOf("Blue Bottle Coffee", ""),
                listOf("Latte", "4.50"),
                listOf("Iced tea", "3.25"),
                listOf("Total", "$8.37")
            ),
            OcrLayout.table(words)
        )
    }

    @Test
    fun `three columns line up even when one row leaves a cell empty`() {
        val words = listOf(
            w("Name", 0.1f, 0.1f), w("Qty", 0.5f, 0.1f), w("Price", 0.8f, 0.1f),
            w("Pens", 0.1f, 0.13f), w("2", 0.51f, 0.13f), w("10", 0.81f, 0.13f),
            w("Paper", 0.1f, 0.16f), w("5", 0.81f, 0.16f)
        )
        assertEquals(
            listOf(listOf("Name", "Qty", "Price"), listOf("Pens", "2", "10"), listOf("Paper", "", "5")),
            OcrLayout.table(words)
        )
    }

    @Test
    fun `plain prose stays one column, a row per line`() {
        val words = listOf(w("Dear", 0.1f, 0.1f), w("Sir,", 0.15f, 0.1f), w("Thanks", 0.1f, 0.13f), w("again", 0.17f, 0.13f))
        assertEquals(listOf(listOf("Dear Sir,"), listOf("Thanks again")), OcrLayout.table(words))
    }
}
