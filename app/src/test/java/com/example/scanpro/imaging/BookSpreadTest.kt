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

    @Test
    fun `the swap button changes which half becomes page 1, and the half lettered A is always page 1`() {
        fun firstPage(layout: Int) = BookSpread.ordered("left", "right", BookSpread.leftFirst(layout)).first()
        fun halfLetteredA(layout: Int): String {
            val (leftLetter, _) = com.example.scanpro.core.designsystem.bookHalfLetters(BookSpread.leftFirst(layout))
            return if (leftLetter == "A") "left" else "right"
        }
        assertEquals("left", firstPage(1))
        val swapped = BookSpread.swapped(1)
        assertEquals(2, swapped)
        assertEquals("right", firstPage(swapped))
        assertEquals(1, BookSpread.swapped(swapped))
        assertEquals("one page per shot is not a spread", 0, BookSpread.swapped(0))
        for (layout in listOf(1, 2)) assertEquals(halfLetteredA(layout), firstPage(layout))
    }
}
