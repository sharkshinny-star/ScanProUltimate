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
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/** R58: a dark rim round a brighter inside (laptop, keyboard) is not a page; a page, lit or shadowed, is. */
class PageFramingTest {
    @Before fun opencv() = NativeOpenCv.require()

    private val outline = listOf(Point(200.0, 150.0), Point(700.0, 150.0), Point(700.0, 550.0), Point(200.0, 550.0))

    /** A 900 x 700 desk of [desk] with a [body] rectangle on the outline, and the inside from 30 px in filled with [inner]. */
    private fun scene(desk: Double, body: Double, inner: Double): Mat {
        val m = Mat(700, 900, CvType.CV_8UC1, Scalar(desk))
        Imgproc.rectangle(m, Point(200.0, 150.0), Point(700.0, 550.0), Scalar(body), -1)
        Imgproc.rectangle(m, Point(230.0, 180.0), Point(670.0, 520.0), Scalar(inner), -1)
        return m
    }

    /** Page "text": thin dark strokes over the inside, as a page has. */
    private fun withText(m: Mat): Mat {
        for (row in 0 until 14) Imgproc.line(m, Point(250.0, 200.0 + row * 22), Point(620.0, 200.0 + row * 22), Scalar(30.0), 3)
        return m
    }

    @Test
    fun laptopBodyRoundAScreenIsFramedOnEverySide() {
        val m = scene(desk = 105.0, body = 50.0, inner = 210.0)
        assertEquals(4, PageFraming.framedSides(outline, m))
        m.release()
    }

    @Test
    fun pageOnADarkOrALightDeskIsNotFramed() {
        for (desk in listOf(40.0, 105.0, 235.0)) {
            val m = withText(scene(desk = desk, body = 245.0, inner = 245.0))
            assertEquals("desk $desk", 0, PageFraming.framedSides(outline, m))
            m.release()
        }
    }

    @Test
    fun shadowedPageIsNotFramed() {
        // A shadow over the left two thirds of the page: that margin is dark, but so is the page's middle beside it.
        val m = withText(scene(desk = 105.0, body = 245.0, inner = 245.0))
        Imgproc.rectangle(m, Point(200.0, 150.0), Point(530.0, 550.0), Scalar(100.0), -1)
        assertTrue(PageFraming.framedSides(outline, m) < DocumentDetection.FRAMED_OBJECT_SIDES)
        m.release()
    }

    @Test
    fun uniformDarkCardIsNotFramed() {
        // A dark ID card or notebook cover on a white desk: darker than the desk, but its edge is as dark as its middle.
        val m = scene(desk = 235.0, body = 80.0, inner = 80.0)
        assertEquals(0, PageFraming.framedSides(outline, m))
        m.release()
    }

    @Test
    fun framedObjectIsNotAConvincingPage() {
        val quad = CropQuad(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.8f, 0.2f), NormalizedPoint(0.8f, 0.8f), NormalizedPoint(0.2f, 0.8f))
        fun detection(framed: Int) = DocumentDetection(quad, 0.68f, 500.0, 160.0, 900, 1200, Guidance.READY, 0.0, framed)
        assertTrue(detection(0).isConvincingPage)
        assertTrue(detection(2).isConvincingPage)
        assertFalse(detection(3).isConvincingPage)
        assertFalse(detection(4).isReady)
    }
}
