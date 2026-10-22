package com.example.scanpro.scanner

import com.example.scanpro.scanner.QuadGeometry.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadGeometryTest {
    private val rectangle = listOf(P(0.0, 0.0), P(100.0, 0.0), P(100.0, 140.0), P(0.0, 140.0))
    private val tilted = listOf(P(10.0, 0.0), P(100.0, 15.0), P(90.0, 140.0), P(0.0, 120.0))
    private val diamond = listOf(P(50.0, 0.0), P(100.0, 70.0), P(50.0, 140.0), P(0.0, 70.0))
    private val sliver = listOf(P(0.0, 0.0), P(200.0, 0.0), P(180.0, 10.0), P(20.0, 10.0))

    @Test
    fun `rectangle has square corners`() {
        QuadGeometry.interiorAngles(rectangle).forEach { assertEquals(90.0, it, 1e-6) }
        assertEquals(1.0, QuadGeometry.angleScore(rectangle), 1e-6)
    }

    @Test
    fun `a page seen at an angle is still plausible`() {
        assertTrue(QuadGeometry.isPlausiblePage(tilted))
        assertTrue(QuadGeometry.angleScore(tilted) in 0.0..1.0)
    }

    @Test
    fun `diamonds and slivers are rejected`() {
        assertTrue(QuadGeometry.angleScore(diamond) < 0.9)
        assertFalse(QuadGeometry.isPlausiblePage(sliver))
    }

    @Test
    fun `degenerate points do not crash`() {
        val same = List(4) { P(5.0, 5.0) }
        assertFalse(QuadGeometry.isPlausiblePage(same))
    }
}
