package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperColorsTest {
    private val cream = 0xFFF2EAD8.toInt()
    private val navy = 0xFF1A2A5A.toInt()

    @Test
    fun `paper and ink are told apart on a line of text`() {
        val pixels = IntArray(1000) { if (it % 10 == 0) navy else cream }
        assertEquals(PaperColors.Estimate(cream, navy), PaperColors.estimate(pixels))
    }

    @Test
    fun `blank paper falls back to near-black ink`() {
        val estimate = PaperColors.estimate(IntArray(500) { cream })
        assertEquals(cream, estimate.paper)
        assertEquals(0xFF141414.toInt(), estimate.ink)
    }
}
