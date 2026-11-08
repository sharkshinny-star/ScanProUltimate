package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class BookSpreadTest {
    @Test
    fun `the spread splits at the middle, both halves covering it`() {
        val h = BookSpread.halves(3000)
        assertEquals(0, h.leftStart); assertEquals(1500, h.leftWidth)
        assertEquals(1500, h.rightStart); assertEquals(1500, h.rightWidth)
        val odd = BookSpread.halves(3001)
        assertEquals(3001, odd.leftWidth + odd.rightWidth)
    }

    @Test
    fun `an off-centre spine stays within sensible bounds`() {
        assertEquals(1200, BookSpread.halves(3000, 0.4f).leftWidth)
        assertEquals("never narrower than a fifth", 600, BookSpread.halves(3000, 0.05f).leftWidth)
    }

    @Test
    fun `reading order`() {
        assertEquals(listOf("L", "R"), BookSpread.ordered("L", "R", leftFirst = true))
        assertEquals(listOf("R", "L"), BookSpread.ordered("L", "R", leftFirst = false))
    }
}
