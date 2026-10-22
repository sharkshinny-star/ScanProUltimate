package com.example.scanpro.imaging

import com.example.scanpro.data.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WordPlacementTest {
    @Test
    fun `word box maps into the page image rectangle`() {
        // Image drawn at (20, 40), 400 wide and 600 tall. The word covers the left half of a band near the top.
        val placed = WordPlacementMath.place(OcrWord("Total", 0.0f, 0.1f, 0.5f, 0.15f), 20f, 40f, 400f, 600f)
        assertNotNull(placed)
        placed!!
        assertEquals(20f, placed.x, 1e-3f)
        assertEquals(200f, placed.width, 1e-3f)
        // Box bottom is at 40 + 0.15 * 600 = 130; the baseline sits a little above it.
        assertEquals(true, placed.baseline < 130f && placed.baseline > 40f + 0.1f * 600f)
    }

    @Test
    fun `empty boxes are skipped`() {
        assertNull(WordPlacementMath.place(OcrWord("x", 0.2f, 0.2f, 0.2f, 0.3f), 0f, 0f, 100f, 100f))
    }
}
