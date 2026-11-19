package com.example.scanpro.scanner

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live camera's colour-strength map, on hand-made frames: white paper on the left, a wooden desk on the right. */
class LiveChromaTest {
    private val w = 64
    private val h = 48

    // Wood (170, 120, 70) in full-range BT.601; paper is neutral, so its colour planes sit at 128.
    private val woodY = 129
    private val woodU = 95
    private val woodV = 157

    private fun luma(dark: Boolean = false) = ByteArray(w * h) { i -> (if (dark) 6 else if (i % w < w / 2) 235 else woodY).toByte() }

    /** A half-size colour plane with [pixelStride] bytes per sample and [padding] unused bytes at the end of each row. */
    private fun plane(wood: Int, pixelStride: Int, padding: Int): ByteArray {
        val rowStride = (w / 2) * pixelStride + padding
        return ByteArray(rowStride * (h / 2)) { i ->
            val x = (i % rowStride) / pixelStride
            (if (x < w / 4) 128 else wood).toByte()
        }
    }

    private fun map(pixelStride: Int = 1, padding: Int = 0, dark: Boolean = false): ByteArray {
        val out = ByteArray(LiveChroma.width(w) * LiveChroma.height(h))
        val rowStride = (w / 2) * pixelStride + padding
        // In a frame that dark the colour planes are close to neutral too: the same wood, about twenty times dimmer.
        val u = plane(if (dark) 126 else woodU, pixelStride, padding)
        val v = plane(if (dark) 130 else woodV, pixelStride, padding)
        assertTrue(LiveChroma.compute(luma(dark), w, h, u, v, rowStride, pixelStride, out))
        return out
    }

    @Test
    fun paperIsNeutralAndWoodIsColoured() {
        val out = map()
        val mw = LiveChroma.width(w)
        for (y in 0 until LiveChroma.height(h)) {
            val paper = out[y * mw + 2].toInt() and 0xFF
            val wood = out[y * mw + mw - 3].toInt() and 0xFF
            assertTrue("paper reads neutral ($paper)", paper <= 2)
            // DocumentEdgeDetector.chromaMap gives wood (170, 120, 70) a strength of about 83.
            assertTrue("wood reads coloured ($wood)", wood in 76..90)
        }
    }

    @Test
    fun interleavedAndPaddedPlanesGiveTheSameMap() {
        val plain = map()
        assertArrayEquals(plain, map(pixelStride = 2))
        assertArrayEquals(plain, map(pixelStride = 2, padding = 6))
        assertArrayEquals(plain, map(pixelStride = 1, padding = 16))
    }

    @Test
    fun nearBlackPixelsFadeToNeutral() {
        val out = map(dark = true)
        // At full brightness this wood reads about 83.
        assertTrue(out.all { (it.toInt() and 0xFF) < 30 })
    }

    @Test
    fun shortPlanesAreRefused() {
        val out = ByteArray(LiveChroma.width(w) * LiveChroma.height(h)) { 7 }
        val short = ByteArray(10)
        assertFalse(LiveChroma.compute(luma(), w, h, short, short, w / 2, 1, out))
        assertFalse(LiveChroma.compute(ByteArray(10), w, h, plane(woodU, 1, 0), plane(woodV, 1, 0), w / 2, 1, out))
        assertTrue("the output is left alone", out.all { it == 7.toByte() })
    }

    /** The interleaved Cb, Cr planes for the detector's colour edges. */
    private fun planes(pixelStride: Int = 1, padding: Int = 0): ByteArray {
        val out = ByteArray(2 * LiveChroma.width(w) * LiveChroma.height(h))
        val rowStride = (w / 2) * pixelStride + padding
        assertTrue(LiveChroma.planes(w, h, plane(woodU, pixelStride, padding), plane(woodV, pixelStride, padding), rowStride, pixelStride, out))
        return out
    }

    @Test
    fun colourPlanesKeepTheCameraValuesCellByCell() {
        val out = planes()
        val mw = LiveChroma.width(w)
        for (y in 0 until LiveChroma.height(h)) {
            val paper = (y * mw + 2) * 2
            val wood = (y * mw + mw - 3) * 2
            assertEquals(128, out[paper].toInt() and 0xFF)
            assertEquals(128, out[paper + 1].toInt() and 0xFF)
            assertEquals(woodU, out[wood].toInt() and 0xFF)
            assertEquals(woodV, out[wood + 1].toInt() and 0xFF)
        }
    }

    @Test
    fun colourPlanesReadInterleavedAndPaddedPlanesTheSame() {
        val plain = planes()
        assertArrayEquals(plain, planes(pixelStride = 2))
        assertArrayEquals(plain, planes(pixelStride = 2, padding = 6))
        assertArrayEquals(plain, planes(pixelStride = 1, padding = 16))
    }

    @Test
    fun colourPlanesRefuseShortArrays() {
        val out = ByteArray(2 * LiveChroma.width(w) * LiveChroma.height(h)) { 7 }
        val short = ByteArray(10)
        assertFalse(LiveChroma.planes(w, h, short, short, w / 2, 1, out))
        assertFalse(LiveChroma.planes(w, h, plane(woodU, 1, 0), plane(woodV, 1, 0), w / 2, 1, ByteArray(10)))
        assertTrue("the output is left alone", out.all { it == 7.toByte() })
    }
}
