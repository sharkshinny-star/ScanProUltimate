package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.NativeOpenCv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class PageSignatureTest {
    @Before fun opencv() = NativeOpenCv.require()

    @Test
    fun `normalising removes brightness and contrast`() {
        val a = PageSignature.normalise(floatArrayOf(10f, 20f, 30f, 40f))
        val b = PageSignature.normalise(floatArrayOf(110f, 130f, 150f, 170f))
        assertEquals(0f, PageSignature.distance(a, b), 1e-5f)
        assertTrue(PageSignature.isSamePage(a, b))
    }

    @Test
    fun `a different pattern is a different page`() {
        val a = PageSignature.normalise(floatArrayOf(0f, 255f, 0f, 255f, 0f, 255f))
        val b = PageSignature.normalise(floatArrayOf(255f, 0f, 255f, 0f, 255f, 0f))
        assertFalse(PageSignature.isSamePage(a, b))
    }

    @Test
    fun `signatures of two pages of text differ, the same page under other light does not`() {
        val quad = CropQuad(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f))
        val page1 = textPage(linesAt = listOf(40, 80, 120, 160))
        val page2 = textPage(linesAt = listOf(60, 100, 140))
        val page1Darker = Mat().also { page1.convertTo(it, -1, 0.6, -20.0) }
        try {
            val s1 = PageSignature.of(page1, quad)
            assertEquals(PageSignature.SIZE * PageSignature.SIZE, s1.size)
            assertTrue("same page, darker", PageSignature.isSamePage(s1, PageSignature.of(page1Darker, quad)))
            assertFalse("another page", PageSignature.isSamePage(s1, PageSignature.of(page2, quad)))
        } finally {
            page1.release(); page2.release(); page1Darker.release()
        }
    }

    @Test
    fun `the shutter waits for a sharp frame once a few steady ones are known`() {
        assertTrue("too few frames to judge", SharpnessGate.shouldFire(listOf(100.0, 120.0), 50.0))
        assertTrue(SharpnessGate.shouldFire(listOf(100.0, 120.0, 110.0), 115.0))
        assertFalse(SharpnessGate.shouldFire(listOf(100.0, 120.0, 110.0), 80.0))
    }

    /** A grey "page" with dark text lines at [linesAt] rows, 200 x 200. */
    private fun textPage(linesAt: List<Int>): Mat {
        val m = Mat(200, 200, CvType.CV_8UC1, Scalar(230.0))
        linesAt.forEach { y -> m.submat(y, y + 6, 20, 180).setTo(Scalar(40.0)) }
        return m
    }
}
