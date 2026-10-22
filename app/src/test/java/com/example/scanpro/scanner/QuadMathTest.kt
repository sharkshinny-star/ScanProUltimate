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
}
