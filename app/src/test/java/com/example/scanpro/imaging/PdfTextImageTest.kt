package com.example.scanpro.imaging

import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Watermark text Helvetica cannot write goes out as a picture, placed exactly where the vector text would have been. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextImageTest {

    @Test
    fun `latin text stays vector and Indian scripts fall back to a picture`() {
        val font = PDType1Font.HELVETICA_BOLD
        assertTrue(PdfTextImage.canEncode(font, "CONFIDENTIAL draft – 2026"))
        assertFalse(PdfTextImage.canEncode(font, "गोपनीय"))
        assertFalse(PdfTextImage.canEncode(font, "ரகசியம்"))
    }

    private fun corner(m: Matrix, u: Float, v: Float) = Pair(
        m.scaleX * u + m.shearX * v + m.translateX,
        m.shearY * u + m.scaleY * v + m.translateY
    )

    @Test
    fun `an unturned picture is centred on its point`() {
        val m = placementFor(width = 100f, height = 20f, cx = 300f, cy = 400f, angle = 0.0)
        assertPoint(250f, 390f, corner(m, 0f, 0f))
        assertPoint(350f, 410f, corner(m, 1f, 1f))
    }

    @Test
    fun `a turned picture keeps its centre`() {
        val m = placementFor(width = 100f, height = 20f, cx = 300f, cy = 400f, angle = Math.toRadians(45.0))
        val (x0, y0) = corner(m, 0f, 0f)
        val (x1, y1) = corner(m, 1f, 1f)
        assertPoint(300f, 400f, Pair((x0 + x1) / 2f, (y0 + y1) / 2f))
    }

    private fun placementFor(width: Float, height: Float, cx: Float, cy: Float, angle: Double): Matrix =
        PdfTextImage.placementOf(width, height, cx, cy, angle)

    private fun assertPoint(x: Float, y: Float, actual: Pair<Float, Float>) {
        assertEquals(x, actual.first, 1e-3f)
        assertEquals(y, actual.second, 1e-3f)
    }
}
