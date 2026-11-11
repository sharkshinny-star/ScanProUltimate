package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

/** Page numbers are placed on the page as seen, so /Rotate pages get them on the edge the reader sees, upright. */
class PageNumberGeometryTest {
    private val w = 612f
    private val h = 792f
    private val text = 40f
    private val font = 11f
    private val margin = 36f

    private fun place(position: PageNumberPosition, rotation: Int) =
        PageNumberGeometry.place(position, text, font, margin, w, h, rotation)

    private fun degrees(stamp: WatermarkGeometry.Stamp) = Math.round(Math.toDegrees(stamp.angleRad)).toInt()

    @Test
    fun `an upright page keeps the old placement`() {
        val bottom = place(PageNumberPosition.BOTTOM_CENTER, 0)
        assertEquals((w - text) / 2f, bottom.x, 0.01f)
        assertEquals(margin, bottom.y, 0.01f)
        assertEquals(0, degrees(bottom))
        val topRight = place(PageNumberPosition.TOP_RIGHT, 0)
        assertEquals(w - margin - text, topRight.x, 0.01f)
        assertEquals(h - margin - font, topRight.y, 0.01f)
        val bottomLeft = place(PageNumberPosition.BOTTOM_LEFT, 0)
        assertEquals(margin, bottomLeft.x, 0.01f)
    }

    @Test
    fun `a page turned 90 degrees gets the number on the stored right edge, reading upwards`() {
        // /Rotate 90 turns the page clockwise for display, so the stored right edge is the bottom the reader sees.
        val spot = place(PageNumberPosition.BOTTOM_CENTER, 90)
        assertEquals(w - margin, spot.x, 0.01f)
        assertEquals((h - text) / 2f, spot.y, 0.01f)
        assertEquals(90, degrees(spot))
    }

    @Test
    fun `a page turned upside down gets the number on the stored top edge`() {
        val spot = place(PageNumberPosition.BOTTOM_CENTER, 180)
        assertEquals(h - margin, spot.y, 0.01f)
        // The text runs right to left in stored coordinates, starting from the right of centre.
        assertEquals(w - (w - text) / 2f, spot.x, 0.01f)
        assertEquals(180, degrees(spot))
    }

    @Test
    fun `a page turned 270 degrees gets the number on the stored left edge`() {
        val spot = place(PageNumberPosition.BOTTOM_LEFT, 270)
        assertEquals(margin, spot.x, 0.01f)
        assertEquals(h - margin, spot.y, 0.01f)
        assertEquals(270, degrees(spot))
    }

    @Test
    fun `every spot stays inside the page`() {
        for (rotation in listOf(0, 90, 180, 270, -90, 450)) for (position in PageNumberPosition.entries) {
            val spot = place(position, rotation)
            assert(spot.x in 0f..w && spot.y in 0f..h) { "$position at $rotation: ${spot.x}, ${spot.y}" }
        }
    }
}
