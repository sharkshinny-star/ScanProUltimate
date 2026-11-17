package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadMathTest {
    private val skewed = CropQuad(
        NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.9f, 0.1f),
        NormalizedPoint(0.8f, 0.9f), NormalizedPoint(0.2f, 0.8f)
    )

    private fun assertSame(a: CropQuad, b: CropQuad) {
        quadCorners(a).zip(quadCorners(b)).forEach { (p, q) ->
            assertEquals(p.x, q.x, 1e-5f)
            assertEquals(p.y, q.y, 1e-5f)
        }
    }

    @Test
    fun `full frame stays a full frame when rotated`() {
        assertTrue(rotateQuad(CropQuad.DEFAULT, clockwise = true).isIdentity())
        assertTrue(rotateQuad(CropQuad.DEFAULT, clockwise = false).isIdentity())
    }

    @Test
    fun `clockwise then counter-clockwise returns the original`() {
        assertSame(skewed, rotateQuad(rotateQuad(skewed, clockwise = true), clockwise = false))
    }

    @Test
    fun `four quarter turns return the original`() {
        var quad = skewed
        repeat(4) { quad = rotateQuad(quad, clockwise = true) }
        assertSame(skewed, quad)
    }

    @Test
    fun `clockwise turn moves the top left corner to the top right`() {
        val rotated = rotateQuad(skewed, clockwise = true)
        assertEquals(1f - 0.2f, rotated.topRight.x, 1e-5f)
        assertEquals(0.1f, rotated.topRight.y, 1e-5f)
    }

    @Test
    fun `quads survive encoding`() {
        val decoded = decodeQuad(encodeQuads(mapOf("file:///a b.jpg" to skewed)).single())
        assertEquals("file:///a b.jpg", decoded?.first)
        assertSame(skewed, decoded!!.second)
    }

    @Test
    fun `garbage does not decode`() {
        assertNull(decodeQuad("nonsense"))
    }

    private val bent = skewed.copy(topBow = NormalizedPoint(0.01f, -0.05f), leftBow = NormalizedPoint(-0.03f, 0.02f))

    @Test
    fun `a bent edge keeps its bend through a quarter turn`() {
        val rotated = rotateQuad(bent, clockwise = true)
        // The top edge now faces right. Its bent midpoint must land exactly where the photo point went.
        val oldMid = NormalizedPoint(
            (bent.topLeft.x + bent.topRight.x) / 2f + bent.topBow!!.x,
            (bent.topLeft.y + bent.topRight.y) / 2f + bent.topBow.y
        )
        val expected = NormalizedPoint(1f - oldMid.y, oldMid.x)
        val newMid = NormalizedPoint(
            (rotated.topRight.x + rotated.bottomRight.x) / 2f + rotated.rightBow!!.x,
            (rotated.topRight.y + rotated.bottomRight.y) / 2f + rotated.rightBow.y
        )
        assertEquals(expected.x, newMid.x, 1e-5f)
        assertEquals(expected.y, newMid.y, 1e-5f)
        assertNull(rotated.leftBow)
        assertTrue(rotated.topBow != null)
    }

    @Test
    fun `bends come back after a turn each way and after four turns`() {
        val back = rotateQuad(rotateQuad(bent, clockwise = true), clockwise = false)
        assertEquals(bent.topBow!!.y, back.topBow!!.y, 1e-6f)
        assertEquals(bent.leftBow!!.x, back.leftBow!!.x, 1e-6f)
        var quad = bent
        repeat(4) { quad = rotateQuad(quad, clockwise = true) }
        assertEquals(bent.topBow.x, quad.topBow!!.x, 1e-6f)
        assertEquals(bent.leftBow.y, quad.leftBow!!.y, 1e-6f)
    }

    @Test
    fun `bent outlines survive encoding and straight ones stay short`() {
        val decoded = decodeQuad(encodeQuads(mapOf("k" to bent)).single())!!.second
        assertEquals(bent.topBow!!.y, decoded.topBow!!.y, 1e-6f)
        assertEquals(bent.leftBow!!.x, decoded.leftBow!!.x, 1e-6f)
        assertNull(decoded.bottomBow)
        assertEquals(8, encodeQuads(mapOf("k" to skewed)).single().substringAfter('|').split(',').size)
    }

    @Test
    fun `an edge inside the image moves by the whole drag`() {
        val (a, b) = translateEdge(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.8f, 0.4f), 0.1f, -0.1f)
        assertEquals(0.3f, a.x, 1e-6f); assertEquals(0.2f, a.y, 1e-6f)
        assertEquals(0.9f, b.x, 1e-6f); assertEquals(0.3f, b.y, 1e-6f)
    }

    @Test
    fun `an edge stops at the border and keeps its length and angle`() {
        val (a, b) = translateEdge(NormalizedPoint(0.2f, 0.05f), NormalizedPoint(0.8f, 0.15f), 0.5f, -0.5f)
        // The right end reaches x = 1 after 0.2; the upper end reaches y = 0 after 0.05. Both ends move the same.
        assertEquals(0.4f, a.x, 1e-6f); assertEquals(0f, a.y, 1e-6f)
        assertEquals(1f, b.x, 1e-6f); assertEquals(0.1f, b.y, 1e-6f)
        assertEquals(0.6f, b.x - a.x, 1e-6f)
        assertEquals(0.1f, b.y - a.y, 1e-6f)
    }

    @Test
    fun `an edge already outside the image is not pushed further and does not throw`() {
        val (a, b) = translateEdge(NormalizedPoint(-0.2f, 0.5f), NormalizedPoint(1.1f, 0.5f), 0.3f, 0f)
        assertEquals(-0.2f, a.x, 1e-6f)
        assertEquals(1.1f, b.x, 1e-6f)
        val (c, d) = translateEdge(NormalizedPoint(0.5f, 0.5f), NormalizedPoint(0.6f, 0.5f), Float.NaN, Float.NaN)
        assertEquals(0.5f, c.x, 1e-6f); assertEquals(0.6f, d.x, 1e-6f)
    }

    @Test
    fun `turning by quarter turns matches repeated clockwise rotation and undoes itself`() {
        assertSame(skewed, turnQuad(skewed, 0))
        assertSame(rotateQuad(skewed, clockwise = true), turnQuad(skewed, 1))
        assertSame(rotateQuad(skewed, clockwise = false), turnQuad(skewed, -1))
        assertSame(turnQuad(skewed, 1), turnQuad(skewed, 5))
        for (n in 0..3) assertSame(skewed, turnQuad(turnQuad(skewed, n), -n))
        // The same path gives the same floats, so an untouched outline compares equal after Rotate in the editor.
        assertEquals(turnQuad(skewed, 2), turnQuad(turnQuad(skewed, 1), 1))
    }
}
