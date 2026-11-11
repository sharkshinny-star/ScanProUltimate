package com.example.scanpro.scanner

import androidx.annotation.StringRes
import com.example.scanpro.R
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Something about a captured page that leading scanners point out before saving (Genius Scan, Scanbot, Dropbox). */
enum class PageIssue(@StringRes val messageRes: Int) {
    BLURRY(R.string.scan_quality_blurry),
    GLARE(R.string.scan_quality_glare),
    DARK(R.string.scan_quality_dark)
}

/**
 * Page quality, judged the way the live camera judges it ([DocumentEdgeDetector] at its ~900 px analysis size), so a
 * page the camera called sharp and lit is not flagged afterwards, and the other way round.
 */
object PageQuality {
    /** Variance of the Laplacian on the page below which it reads as blurred (the camera's "Hold steady"). */
    const val BLUR_LIMIT = 80.0
    /** Mean grey level below which the frame is too dark (the camera's "Too dark"). */
    const val DARK_LIMIT = 45.0
    /** Grey level from which a pixel counts as clipped white: a reflection, not paper. */
    private const val CLIPPED = 250.0
    /** A clipped patch covering at least this share of the page is glare worth a mention... */
    const val GLARE_MIN_SHARE = 0.015
    /** ...unless it covers most of the page: that is an over-exposed white sheet, not a reflection. */
    const val GLARE_MAX_SHARE = 0.6

    /**
     * Share of [region] of the 8-bit grayscale [gray] covered by its largest clipped-white patch.
     * When provided, [pagePolygon] uses original image coordinates and excludes the background *outside*
     * a tilted page's outline. The two-argument overload remains valid for existing tests/callers.
     */
    @JvmOverloads
    fun glareShare(gray: Mat, region: Rect, pagePolygon: List<Point>? = null): Double {
        require(gray.type() == CvType.CV_8UC1) { "glareShare expects an 8-bit grayscale Mat" }
        if (region.width <= 0 || region.height <= 0) return 0.0
        val sub = gray.submat(region)
        val mask = Mat()
        val pageMask = Mat()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        try {
            Imgproc.threshold(sub, mask, CLIPPED - 1, 255.0, Imgproc.THRESH_BINARY)
            val area = if (pagePolygon != null && pagePolygon.size >= 3) {
                pageMask.create(region.height, region.width, CvType.CV_8UC1)
                pageMask.setTo(Scalar(0.0))
                val points = MatOfPoint(*pagePolygon.map { Point(it.x - region.x, it.y - region.y) }.toTypedArray())
                try {
                    Imgproc.fillConvexPoly(pageMask, points, Scalar(255.0))
                } finally {
                    points.release()
                }
                Core.bitwise_and(mask, pageMask, mask)
                Core.countNonZero(pageMask).toDouble()
            } else {
                region.width.toDouble() * region.height
            }
            if (area <= 0.0) return 0.0
            val clipped = Core.countNonZero(mask)
            if (clipped < area * GLARE_MIN_SHARE) return clipped / area
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            val count = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids, 8, CvType.CV_32S)
            var largest = 0.0
            for (label in 1 until count) largest = maxOf(largest, stats.get(label, Imgproc.CC_STAT_AREA)[0])
            return largest / area
        } finally {
            sub.release(); mask.release(); pageMask.release(); labels.release(); stats.release(); centroids.release(); kernel.release()
        }
    }

    /** Whether a clipped patch of this share is glare (not too small to matter, not a whole over-exposed sheet). */
    fun isGlare(share: Double): Boolean = share >= GLARE_MIN_SHARE && share <= GLARE_MAX_SHARE

    /**
     * What to point out about a captured page. Without a page outline only darkness is judged: sharpness and glare
     * measured over the whole frame would be about the table, not the page.
     */
    fun issues(detection: DocumentDetection): Set<PageIssue> = buildSet {
        if (detection.quad != null && detection.blurScore < BLUR_LIMIT) add(PageIssue.BLURRY)
        if (detection.quad != null && isGlare(detection.glareShare)) add(PageIssue.GLARE)
        if (detection.brightness < DARK_LIMIT) add(PageIssue.DARK)
        // A tilted page is not an issue of the photo: the perspective crop straightens it. The live camera hints at
        // it (Guidance.ALIGN_PAGE) instead, so a tilted capture is neither flagged in review nor kept out of fast save.
    }
}
