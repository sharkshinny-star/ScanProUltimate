package com.example.scanpro.imaging

import com.example.scanpro.imaging.CurvedPage.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvedPageTest {
    @Test
    fun curvePassesThroughTheChosenMidpoint() {
        val a = Pt(0f, 0f); val b = Pt(100f, 0f); val mid = Pt(50f, -20f)
        val mid2 = CurvedPage.bezier(a, CurvedPage.control(a, mid, b), b, 0.5f)
        assertEquals(50f, mid2.x, 1e-3f)
        assertEquals(-20f, mid2.y, 1e-3f)
    }

    @Test
    fun evenlySpacedPointsAreEquidistantAlongTheCurve() {
        val a = Pt(0f, 0f); val b = Pt(200f, 0f)
        val c = CurvedPage.control(a, Pt(100f, -60f), b)
        val points = CurvedPage.evenlySpaced(a, c, b, 41)
        assertEquals(a, points.first())
        assertEquals(b.x, points.last().x, 0.01f)
        val gaps = points.toList().zipWithNext { p, q -> (q - p).length() }
        val mean = gaps.average().toFloat()
        gaps.forEach { assertEquals(mean, it, mean * 0.03f) }
        assertEquals(CurvedPage.length(a, c, b), gaps.sum(), 1f)
    }

    @Test
    fun straightEdgesGiveAPlainRectangleMap() {
        val top = CurvedPage.evenlySpaced(Pt(10f, 20f), Pt(60f, 20f), Pt(110f, 20f), 101)
        val bottom = CurvedPage.evenlySpaced(Pt(10f, 220f), Pt(60f, 220f), Pt(110f, 220f), 101)
        val (w, h) = CurvedPage.outputSize(top, bottom, 100f, 100f)
        assertEquals(100, w)
        assertEquals(200, h)
        val mapX = FloatArray(101 * 3); val mapY = FloatArray(101 * 3)
        CurvedPage.fillRows(top, bottom, 0, 3, 201, mapX, mapY)
        assertEquals(10f, mapX[0], 1e-3f); assertEquals(20f, mapY[0], 1e-3f)
        assertEquals(60f, mapX[2 * 101 + 50], 0.01f); assertEquals(22f, mapY[2 * 101 + 50], 0.01f)
    }

    /** A bright page on a dark table whose top edge sags into the page by [sag] px at its middle. */
    private fun page(sag: Float): (Float, Float) -> Float = { x, y ->
        val u = ((x - 50f) / 300f).coerceIn(0f, 1f)
        val edgeY = 50f + 4f * u * (1f - u) * sag
        if (x in 50f..350f && y >= edgeY && y <= 450f) 220f else 40f
    }

    @Test
    fun findsHowFarACurvedTopEdgeBends() {
        val bow = CurvedPage.estimateBow(Pt(50f, 50f), Pt(350f, 50f), Pt(0f, 1f), 40f, page(sag = 24f))
        assertNotNull(bow)
        assertEquals(0f, bow!!.x, 0.5f)
        assertEquals(24f, bow.y, 2.5f)
    }

    @Test
    fun aStraightEdgeStaysStraight() {
        val bow = CurvedPage.estimateBow(Pt(50f, 50f), Pt(350f, 50f), Pt(0f, 1f), 40f, page(sag = 0f))
        assertNotNull(bow)
        assertTrue(bow!!.length() < 2.5f)
    }

    @Test
    fun noVisibleEdgeGivesNoEstimate() {
        assertNull(CurvedPage.estimateBow(Pt(50f, 50f), Pt(350f, 50f), Pt(0f, 1f), 40f) { _, _ -> 128f })
    }
}
