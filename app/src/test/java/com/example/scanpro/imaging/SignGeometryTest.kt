package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sign PDF turns a spot on the page as shown (fractions, y down, clockwise turns) into the matrix that stamps the
 * picture in PDF user space. The crop box here is 600 x 800 pt and starts at (10, 20), not at the origin.
 */
class SignGeometryTest {
    // A picture half as high as wide, half the page's shown width, centred; optionally turned clockwise.
    private fun matrix(pageRotation: Int, turn: Float = 0f, centerX: Float = 0.5f, centerY: Float = 0.5f) =
        SignGeometry.imageMatrix(centerX, centerY, 0.5f, turn, 0.5f, pageRotation, 10f, 20f, 600f, 800f)

    private fun assertMatrix(expected: FloatArray, actual: FloatArray) {
        assertEquals(6, actual.size)
        for (i in expected.indices) assertEquals("element $i", expected[i], actual[i], 0.01f)
    }

    @Test
    fun uprightPageCentresThePictureInTheCropBox() {
        // 300 x 150 pt picture, centre of the box is (310, 420): left edge 160, bottom edge 345.
        assertMatrix(floatArrayOf(300f, 0f, 0f, 150f, 160f, 345f), matrix(0))
    }

    @Test
    fun aQuarterTurnPageDrawsTheImageUpTheUserSpaceYAxis() {
        // Shown page is 800 x 600, so the picture is 400 x 200 pt; the image's right runs up the page.
        assertMatrix(floatArrayOf(0f, 400f, -200f, 0f, 410f, 220f), matrix(90))
    }

    @Test
    fun aHalfTurnPageDrawsTheImageUpsideDown() {
        assertMatrix(floatArrayOf(-300f, 0f, 0f, -150f, 460f, 495f), matrix(180))
    }

    @Test
    fun aThreeQuarterTurnPageDrawsTheImageDownTheUserSpaceYAxis() {
        assertMatrix(floatArrayOf(0f, -400f, 200f, 0f, 210f, 620f), matrix(270))
    }

    @Test
    fun negativeAndLargePageRotationsAreNormalised() {
        assertMatrix(matrix(270), matrix(-90))
        assertMatrix(matrix(90), matrix(450))
    }

    @Test
    fun aClockwiseTurnOfTheStampTurnsItClockwiseOnTheShownPage() {
        // Upright page, stamp turned 90 degrees clockwise: its right edge now points down the page.
        assertMatrix(floatArrayOf(0f, -300f, 150f, 0f, 235f, 570f), matrix(0, turn = 90f))
    }

    @Test
    fun movingTheStampMovesItByTheSameFractionOfThePage() {
        val centre = matrix(0)
        val moved = matrix(0, centerX = 0.6f, centerY = 0.25f)
        // 10% of the 600 pt width to the right; 25% of the 800 pt height up from the centre (y is flipped).
        assertEquals(centre[4] + 60f, moved[4], 0.01f)
        assertEquals(centre[5] + 200f, moved[5], 0.01f)
    }
}
