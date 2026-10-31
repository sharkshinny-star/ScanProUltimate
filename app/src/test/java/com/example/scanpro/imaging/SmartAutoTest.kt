package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Auto Clean treats a page of paper as a document and anything else as a photo it must not wash out. */
class SmartAutoTest {
    @Before fun opencv() = NativeOpenCv.require()

    /** A colourful, paperless scene: saturated blocks of colour with gentle shading. */
    private fun scene(): Mat {
        val w = 320; val h = 240
        val bytes = ByteArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 3
            val shade = 0.6 + 0.4 * x / w
            val (r, g, b) = when ((x / 80 + y / 60) % 3) { 0 -> Triple(200, 90, 30); 1 -> Triple(60, 120, 190); else -> Triple(140, 80, 50) }
            bytes[i] = (r * shade).toInt().toByte(); bytes[i + 1] = (g * shade).toInt().toByte(); bytes[i + 2] = (b * shade).toInt().toByte()
        }
        return Mat(h, w, CvType.CV_8UC3).also { it.put(0, 0, bytes) }
    }

    private fun meanSaturation(rgb: Mat): Double {
        val hsv = Mat()
        try {
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            return org.opencv.core.Core.mean(hsv).`val`[1]
        } finally {
            hsv.release()
        }
    }

    @Test
    fun `a page counts as paper and a colourful scene does not`() {
        val page = SyntheticPage.create(); val photo = scene()
        try {
            assertTrue(MatFilters.paperFraction(page) >= MatFilters.DOCUMENT_PAPER_FRACTION)
            assertTrue(MatFilters.paperFraction(photo) < MatFilters.DOCUMENT_PAPER_FRACTION)
        } finally {
            page.release(); photo.release()
        }
    }

    @Test
    fun `auto clean keeps a photo's colour instead of washing it out`() {
        val photo = scene()
        val before = meanSaturation(photo)
        val result = MatFilters.look(photo, PageFilter.AUTO, PageAdjustments.DEFAULT)
        try {
            assertTrue("saturation $before -> ${meanSaturation(result)}", meanSaturation(result) >= before * 0.9)
        } finally {
            result.release()
        }
    }
}
