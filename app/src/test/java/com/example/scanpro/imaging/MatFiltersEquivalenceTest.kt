package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * The filters rewritten onto OpenCV images must give the pictures the old per-pixel Kotlin and Android
 * ColorMatrix code gave (ReferenceFilters), within the rounding differences between the two.
 */
class MatFiltersEquivalenceTest {
    private val w = SyntheticPage.WIDTH
    private val h = SyntheticPage.HEIGHT

    @Before fun requireOpenCv() = NativeOpenCv.require()

    private fun maxDifference(a: ByteArray, b: ByteArray): Int {
        assertEquals(a.size, b.size)
        var worst = 0
        for (i in a.indices) worst = maxOf(worst, abs(a[i].u() - b[i].u()))
        return worst
    }

    private fun colorMatrixDifference(saturation: Float, contrast: Float, brightness: Float): Int {
        val input = SyntheticPage.createBytes()
        val expected = ReferenceFilters.colorMatrix(input, saturation, contrast, brightness)
        val actual = MatFilters.colorMatrix(bytesToMat(input, w, h), saturation, contrast, brightness)
        return try { maxDifference(expected, actual.toBytes()) } finally { actual.release() }
    }

    @Test
    fun `colour matrix matches the old Android colour matrix within rounding`() {
        // The two fixed looks the filters use, plus extremes of the user's sliders.
        listOf(
            Triple(1.10f, 1.04f, 1f),
            Triple(1.08f, 1.28f, 6f),
            Triple(0f, 1f, 0f),
            Triple(1f, 1.5f, 0f),
            Triple(1f, 0.5f, -92f),
            Triple(2f, 1f, 92f)
        ).forEach { (saturation, contrast, brightness) ->
            val worst = colorMatrixDifference(saturation, contrast, brightness)
            assertTrue("saturation=$saturation contrast=$contrast brightness=$brightness differs by $worst", worst <= 1)
        }
    }

    @Test
    fun `user adjustments use the same matrix as before`() {
        val adjustments = PageAdjustments(brightness = 0.25f, contrast = 1.2f, saturation = 1.4f).normalized()
        val input = SyntheticPage.createBytes()
        val expected = ReferenceFilters.colorMatrix(input, adjustments.saturation, adjustments.contrast, adjustments.brightness * 92f)
        val actual = MatFilters.applyUserAdjustments(bytesToMat(input, w, h), adjustments)
        try {
            assertTrue(maxDifference(expected, actual.toBytes()) <= 1)
        } finally {
            actual.release()
        }
    }

    @Test
    fun `whiteboard matches the old per-pixel boost`() {
        val input = SyntheticPage.createBytes()
        val expected = ReferenceFilters.whiteboard(input)
        val actual = MatFilters.whiteboard(bytesToMat(input, w, h))
        try {
            val worst = maxDifference(expected, actual.toBytes())
            assertTrue("whiteboard differs by $worst", worst <= 1)
        } finally {
            actual.release()
        }
    }

    @Test
    fun `black and white agrees with the old integral-image threshold`() {
        val input = SyntheticPage.createBytes()
        val expected = ReferenceFilters.adaptiveBlackWhite(input, w, h)
        val result = MatFilters.adaptiveBlackWhite(bytesToMat(input, w, h))
        val actual = try { result.toBytes() } finally { result.release() }

        val radius = maxOf(10, minOf(w, h) / 55)
        var interiorChecked = 0; var interiorWrong = 0; var wrong = 0
        for (y in 0 until h) for (x in 0 until w) {
            val value = actual[(y * w + x) * 3].u()
            assertTrue("black and white must be pure black or white", value == 0 || value == 255)
            val mismatch = (value == 255) != expected[y * w + x]
            if (mismatch) wrong++
            if (x in radius until w - radius && y in radius until h - radius) {
                interiorChecked++
                if (mismatch) interiorWrong++
            }
        }
        // Inside, the two differ only on pixels within a grey level of the threshold; at the borders the old
        // window shrank while OpenCV's repeats the edge.
        assertTrue("interior: $interiorWrong of $interiorChecked differ", interiorWrong <= interiorChecked / 200)
        assertTrue("overall: $wrong of ${w * h} differ", wrong <= w * h / 50)
    }

    @Test
    fun `a filter consumes its input and hands back a new image`() {
        val input = SyntheticPage.create()
        val result = MatFilters.look(input, PageFilter.AUTO, PageAdjustments.DEFAULT)
        try {
            assertTrue("the consumed input is released", input.empty())
            assertFalse(result.empty())
            assertEquals(w, result.cols()); assertEquals(h, result.rows())
            assertEquals(CvType.CV_8UC3, result.type())
        } finally {
            result.release()
        }
    }

    @Test
    fun `the original look with neutral adjustments hands the same image back`() {
        val input = SyntheticPage.create()
        try {
            assertFalse(MatFilters.changesPixels(PageFilter.ORIGINAL, PageAdjustments.DEFAULT))
            val result: Mat = MatFilters.look(input, PageFilter.ORIGINAL, PageAdjustments.DEFAULT)
            assertSame(input, result)
            assertFalse(result.empty())
        } finally {
            input.release()
        }
    }
}
