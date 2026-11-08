package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardComposerTest {
    @Test
    fun `front sits above the back, same width, centred on an A4 page`() {
        val l = IdCardComposer.arrange(1600, 1000, 1500, 960)
        assertEquals("both sides take the narrower width", 1500, l.front.width)
        assertEquals(1500, l.back.width)
        assertEquals("front keeps its shape", 938, l.front.height)
        assertEquals(960, l.back.height)
        assertTrue("front is above the back", l.front.bottom < l.back.top)
        assertEquals("same gap as the side margin", l.front.left, l.back.top - l.front.bottom)
        assertEquals("A4 shape", (l.pageWidth * 1.4142f).toInt(), l.pageHeight)
        assertTrue("centred", kotlin.math.abs(l.front.top - (l.pageHeight - l.back.bottom)) <= 1)
        assertEquals("centred sideways", l.front.left, l.pageWidth - l.front.right)
    }

    @Test
    fun `a huge photo is scaled down and a tall pair gets a taller page`() {
        val big = IdCardComposer.arrange(6000, 4000, 6000, 4000)
        assertEquals(2000, big.front.width)
        val tall = IdCardComposer.arrange(1000, 1500, 1000, 1500)
        assertTrue(tall.pageHeight >= tall.back.bottom + tall.front.left)
        assertTrue(tall.front.top >= 0)
    }
}
