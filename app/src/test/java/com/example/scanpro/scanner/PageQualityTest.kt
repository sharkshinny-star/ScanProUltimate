package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.NativeOpenCv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class PageQualityTest {
    @Before fun opencv() = NativeOpenCv.require()

    /** A 600 x 800 grey page with "text": dark strokes, so it is sharp and not clipped anywhere. */
    private fun page(level: Double = 205.0): Mat {
        val m = Mat(800, 600, CvType.CV_8UC1, Scalar(level))
        for (row in 0 until 30) {
            Imgproc.line(m, Point(60.0, 80.0 + row * 22), Point(60.0 + (row * 37 % 420) + 60, 80.0 + row * 22), Scalar(30.0), 3)
        }
        return m
    }

    private val whole = Rect(0, 0, 600, 800)

    @Test
    fun aReflectionIsGlare() {
        val m = page()
        // A solid clipped patch of about 4 % of the page, like a ceiling light on glossy paper.
        Imgproc.ellipse(m, Point(380.0, 300.0), Size(90.0, 55.0), 20.0, 0.0, 360.0, Scalar(255.0), -1)
        val share = PageQuality.glareShare(m, whole)
        assertTrue("share $share", PageQuality.isGlare(share))
        m.release()
    }

    @Test
    fun scatteredWhiteSpecksAreNotGlare() {
        val m = page()
        // Many tiny white dots (a white logo's anti-aliasing, sparkle in the paper): opened away, never one patch.
        for (i in 0 until 400) Imgproc.circle(m, Point((i * 53 % 580) + 10.0, (i * 97 % 780) + 10.0), 1, Scalar(255.0), -1)
        assertFalse(PageQuality.isGlare(PageQuality.glareShare(m, whole)))
        m.release()
    }

    @Test
    fun aCleanPageAndAnOverExposedSheetAreNotGlare() {
        val clean = page()
        assertEquals(0.0, PageQuality.glareShare(clean, whole), 1e-9)
        val blown = Mat(800, 600, CvType.CV_8UC1, Scalar(255.0))
        assertFalse("an evenly over-exposed sheet is not a reflection", PageQuality.isGlare(PageQuality.glareShare(blown, whole)))
        clean.release(); blown.release()
    }

    @Test
    fun onlyThePageRegionCounts() {
        val m = page()
        // Glare on the table outside the region asked about is not the page's problem.
        Imgproc.rectangle(m, Point(0.0, 0.0), Point(140.0, 140.0), Scalar(255.0), -1)
        assertFalse(PageQuality.isGlare(PageQuality.glareShare(m, Rect(200, 200, 400, 600))))
        m.release()
    }

    private fun detection(quad: CropQuad?, blur: Double = 500.0, brightness: Double = 160.0, glare: Double = 0.0) =
        DocumentDetection(quad, 0.9f, blur, brightness, 900, 1200, Guidance.READY, glare)

    @Test
    fun issuesFollowTheLiveCamerasLimits() {
        val quad = CropQuad.DEFAULT
        assertEquals(emptySet<PageIssue>(), PageQuality.issues(detection(quad)))
        assertEquals(setOf(PageIssue.BLURRY), PageQuality.issues(detection(quad, blur = 40.0)))
        assertEquals(setOf(PageIssue.DARK), PageQuality.issues(detection(quad, brightness = 30.0)))
        assertEquals(setOf(PageIssue.GLARE), PageQuality.issues(detection(quad, glare = 0.05)))
        assertEquals(setOf(PageIssue.BLURRY, PageIssue.GLARE, PageIssue.DARK), PageQuality.issues(detection(quad, 40.0, 30.0, 0.05)))
    }

    @Test
    fun withoutAPageOnlyDarknessIsJudged() {
        // Sharpness and glare over the whole frame would be about the table, not the page.
        assertEquals(setOf(PageIssue.DARK), PageQuality.issues(detection(null, blur = 10.0, brightness = 20.0, glare = 0.05)))
    }

    @Test
    fun theDetectorReportsGlareOnAPageAndSaysSo() {
        // A dark desk with a lit page on it and a reflection on the page.
        val frame = Mat(1200, 900, CvType.CV_8UC1, Scalar(45.0))
        val sheet = page()
        sheet.copyTo(frame.submat(Rect(150, 200, 600, 800)))
        Imgproc.ellipse(frame, Point(530.0, 500.0), Size(90.0, 55.0), 20.0, 0.0, 360.0, Scalar(255.0), -1)
        val result = DocumentEdgeDetector.detect(frame)
        assertTrue("page found", result.quad != null)
        assertTrue("glare ${result.glareShare}", PageQuality.isGlare(result.glareShare))
        assertEquals(Guidance.GLARE, result.guidance)
        assertTrue("glare is a hint only: it does not stop the capture", result.isReady)
        assertEquals(setOf(PageIssue.GLARE), PageQuality.issues(result))
        frame.release(); sheet.release()
    }
}
