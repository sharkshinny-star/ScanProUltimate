package com.example.scanpro.imaging

import com.example.scanpro.data.PdfQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfShareTest {
    @Test
    fun `an estimate scales the sample to the page count`() {
        assertEquals(500_000L, PdfShare.extrapolate(200_000L, 2, 5))
        assertEquals(0L, PdfShare.extrapolate(200_000L, 0, 5))
    }

    @Test
    fun `the best quality under the target wins, else the smallest, else the preferred`() {
        val estimates = mapOf(PdfQuality.HIGH to 6_000_000L, PdfQuality.BALANCED to 2_500_000L, PdfQuality.SMALL to 900_000L)
        assertEquals(PdfQuality.BALANCED, PdfShare.pickQuality(estimates, 5_000_000L, PdfQuality.HIGH))
        assertEquals(PdfQuality.SMALL, PdfShare.pickQuality(estimates, 1_000_000L, PdfQuality.HIGH))
        assertEquals("nothing fits: smallest anyway", PdfQuality.SMALL, PdfShare.pickQuality(estimates, 100_000L, PdfQuality.HIGH))
        assertEquals("no target: as chosen", PdfQuality.HIGH, PdfShare.pickQuality(estimates, null, PdfQuality.HIGH))
        assertEquals("no estimates yet: smallest", PdfQuality.SMALL, PdfShare.pickQuality(emptyMap(), 5_000_000L, PdfQuality.HIGH))
    }

    @Test
    fun `the shrink plan steps down through the qualities and then the sides`() {
        assertEquals(listOf(PdfShare.Step(PdfQuality.HIGH, 3200)), PdfShare.shrinkPlan(PdfQuality.HIGH, shrink = false))
        val plan = PdfShare.shrinkPlan(PdfQuality.BALANCED, shrink = true)
        assertEquals(
            listOf(
                PdfShare.Step(PdfQuality.BALANCED, 2200), PdfShare.Step(PdfQuality.SMALL, 1400),
                PdfShare.Step(PdfQuality.SMALL, 1000), PdfShare.Step(PdfQuality.SMALL, 800), PdfShare.Step(PdfQuality.SMALL, 600)
            ),
            plan
        )
    }
}
