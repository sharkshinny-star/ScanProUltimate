package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAdjustmentsTest {
    @Test
    fun `default adjustments are neutral`() {
        assertTrue(PageAdjustments.DEFAULT.isNeutral())
    }

    @Test
    fun `adjustments are clamped to safe editing range`() {
        val normalized = PageAdjustments(2f, 0.1f, 3f).normalized()
        assertEquals(1f, normalized.brightness)
        assertEquals(0.6f, normalized.contrast)
        assertEquals(2f, normalized.saturation)
    }
}
