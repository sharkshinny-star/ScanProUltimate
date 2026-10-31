package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextLayerMathTest {
    // A 600 x 800 pt crop box starting at (10, 20).
    private fun map(u: Float, v: Float, rotation: Int) = PdfTextLayerMath.toPageSpace(u, v, rotation, 10f, 20f, 600f, 800f)

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: PagePoint) {
        assertEquals(expectedX, actual.x, 0.01f)
        assertEquals(expectedY, actual.y, 0.01f)
    }

    @Test
    fun `upright page maps top-left of the shown page to the top-left of the box`() {
        assertPoint(10f, 820f, map(0f, 0f, 0))
        assertPoint(610f, 20f, map(1f, 1f, 0))
        assertPoint(310f, 620f, map(0.5f, 0.25f, 0))
    }

    @Test
    fun `quarter turns map every shown corner onto a box corner`() {
        // Shown top-left after a clockwise quarter turn is the box's bottom-left.
        assertPoint(10f, 20f, map(0f, 0f, 90))
        assertPoint(10f, 820f, map(1f, 0f, 90))
        assertPoint(610f, 20f, map(0f, 1f, 90))
        // Half turn: shown top-left is the box's bottom-right.
        assertPoint(610f, 20f, map(0f, 0f, 180))
        assertPoint(10f, 820f, map(1f, 1f, 180))
        // Three quarters: shown top-left is the box's top-right.
        assertPoint(610f, 820f, map(0f, 0f, 270))
        assertPoint(10f, 820f, map(0f, 1f, 270))
    }

    @Test
    fun `negative and oversized rotations are normalised`() {
        assertEquals(270, PdfTextLayerMath.normalized(-90))
        assertEquals(90, PdfTextLayerMath.normalized(450))
        assertPoint(10f, 20f, map(0f, 0f, -270))
    }

    @Test
    fun `quarter turns swap the shown width and height`() {
        assertEquals(600f to 800f, PdfTextLayerMath.shownSize(600f, 800f, 0))
        assertEquals(800f to 600f, PdfTextLayerMath.shownSize(600f, 800f, 90))
        assertEquals(800f to 600f, PdfTextLayerMath.shownSize(600f, 800f, 270))
    }
}
