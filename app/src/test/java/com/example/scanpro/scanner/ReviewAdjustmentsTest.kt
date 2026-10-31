package com.example.scanpro.scanner

import com.example.scanpro.data.PageAdjustments
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewAdjustmentsTest {
    private val eps = 0.0001f

    @Test
    fun `zero on every slider is the neutral page`() {
        assertEquals(PageAdjustments.DEFAULT, reviewAdjustments(0, 0, 0))
    }

    @Test
    fun `slider extremes reach the adjustment ranges`() {
        val low = reviewAdjustments(-50, -50, -50)
        assertEquals(-1f, low.brightness, eps)
        assertEquals(0.6f, low.contrast, eps)
        assertEquals(0f, low.saturation, eps)
        val high = reviewAdjustments(50, 50, 50)
        assertEquals(1f, high.brightness, eps)
        // +50 reaches 1.4, not the 1.6 ceiling: the slider is symmetric around the neutral 1.0.
        assertEquals(1.4f, high.contrast, eps)
        assertEquals(2f, high.saturation, eps)
    }

    @Test
    fun `middle values scale linearly`() {
        val mid = reviewAdjustments(25, -25, 10)
        assertEquals(0.5f, mid.brightness, eps)
        assertEquals(0.8f, mid.contrast, eps)
        assertEquals(1.2f, mid.saturation, eps)
    }

    @Test
    fun `values outside the slider range are clamped`() {
        assertEquals(reviewAdjustments(50, -50, 50), reviewAdjustments(900, -900, 51))
    }

    @Test
    fun `slider positions round-trip through adjustments`() {
        for (v in listOf(-50, -17, 0, 3, 50)) {
            assertEquals(Triple(v, v, v), reviewSliders(reviewAdjustments(v, v, v)))
        }
    }
}
