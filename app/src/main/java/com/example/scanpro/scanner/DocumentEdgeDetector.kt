package com.example.scanpro.scanner

import com.example.scanpro.imaging.OpenCvRuntime
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.example.scanpro.R
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class DocumentDetection(
    val quad: CropQuad?,
    val confidence: Float,
    val blurScore: Double,
    val brightness: Double,
    val frameWidth: Int,
    val frameHeight: Int,
    val guidance: Guidance,
    /** Share of the page covered by its largest clipped-white patch (see [PageQuality.glareShare]); 0 without a page. */
    val glareShare: Double = 0.0
) {
    val isBlurry: Boolean get() = blurScore < PageQuality.BLUR_LIMIT
    val isDark: Boolean get() = brightness < PageQuality.DARK_LIMIT
    /** Share of the frame the outline covers, 0..1. */
    val areaRatio: Float get() = quad?.let { QuadGeometry.area(it) } ?: 0f
    /** The outline is (nearly) the whole frame: the background, not a page. */
    val fillsFrame: Boolean get() = areaRatio > 0.9f
    /**
     * Steady enough to capture: a confident outline that takes up a real share of the frame (a phone or a
     * card held far away does not), is not just the frame edge, and is sharp and lit.
     */
    val isReady: Boolean get() = quad != null && confidence >= 0.48f && areaRatio >= MIN_READY_AREA && !fillsFrame && !isBlurry && !isDark

    companion object {
        const val MIN_READY_AREA = 0.2f
    }
}

/** What the live scanner tells the user to do next; the screen resolves [labelRes] in the current language. */
enum class Guidance(@StringRes val labelRes: Int) {
    POINT_AT_DOCUMENT(R.string.scan_guidance_point_at_document),
    MORE_LIGHT(R.string.scan_guidance_more_light),
    HOLD_STEADY(R.string.scan_guidance_hold_steady),
    MOVE_CLOSER(R.string.scan_guidance_move_closer),
    /** A hint only: glare does not hold back the capture (glossy paper under a ceiling light would never be taken). */
    GLARE(R.string.scan_guidance_glare),
    /** Auto-capture only: this is the page just taken (R32), so the shutter waits for the next one. */
    SAME_PAGE(R.string.scan_guidance_same_page),
    READY(R.string.scan_guidance_ready)
}

/**
 * High-precision OpenCV document detector for mobile scanning.
 * Combines Canny edge analysis, adaptive thresholding, multi-epsilon polygon approximation,
 * convex hull quad extraction, and corner geometry scoring.
 */
object DocumentEdgeDetector {
    init { OpenCvRuntime.ensureLoaded() }

    private const val ANALYSIS_LONG_SIDE = 900.0
    private const val MIN_DOCUMENT_AREA_RATIO = 0.08
    /** Canny limits for the faint-edge source, on a widely blurred copy where a 12-level step peaks near 5. */
    private const val FAINT_CANNY_LOW = 2.5
    private const val FAINT_CANNY_HIGH = 7.0

    fun detect(grayInput: Mat): DocumentDetection {
        require(grayInput.channels() == 1) { "DocumentEdgeDetector expects a grayscale Mat" }

        val working = resizeForAnalysis(grayInput)
        val processed = Mat()
        val edgesCanny = Mat()
        val edgesAdaptive = Mat()
        val combinedEdges = Mat()
        val hierarchy = Mat()
        val laplacian = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val stdDev = MatOfDouble()
        val mean = MatOfDouble()

        try {
            Imgproc.GaussianBlur(working, processed, Size(5.0, 5.0), 0.0)

            // Method 1: Dynamic Canny
            val median = Core.mean(processed).`val`[0].coerceIn(20.0, 230.0)
            val lower = max(15.0, median * 0.5)
            val upper = min(255.0, median * 1.4)
            Imgproc.Canny(processed, edgesCanny, lower, upper)

            // Method 2: Adaptive Threshold for low-contrast edges on light or dark backgrounds
            Imgproc.adaptiveThreshold(
                processed,
                edgesAdaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15,
                4.0
            )

            // Combine both edge maps
            Core.bitwise_or(edgesCanny, edgesAdaptive, combinedEdges)
            Imgproc.morphologyEx(combinedEdges, combinedEdges, Imgproc.MORPH_CLOSE, kernel)

            // Method 3: paper is usually the big bright (or big dark) region against its surroundings, so
            // a global threshold finds it even where the page edge itself is faint or shadowed.
            val brightMask = Mat()
            val darkMask = Mat()
            Imgproc.threshold(processed, brightMask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Core.bitwise_not(brightMask, darkMask)
            val bigKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            Imgproc.morphologyEx(brightMask, brightMask, Imgproc.MORPH_CLOSE, bigKernel)
            Imgproc.morphologyEx(brightMask, brightMask, Imgproc.MORPH_OPEN, bigKernel)

            // Cleaner Canny map for the contour search: dilated so a small gap in the page outline closes.
            val cannyClosed = Mat()
            Imgproc.dilate(edgesCanny, cannyClosed, kernel)
            Imgproc.morphologyEx(cannyClosed, cannyClosed, Imgproc.MORPH_CLOSE, bigKernel)

            // Method 4: faint edges. The Canny limits above scale with the frame's brightness, so a bright page with a
            // faint edge (grey on grey, white on a pale table) sits below them. Here Canny runs on a widely blurred
            // copy with low limits: text, wood grain and noise average out, while the long step between the page
            // margin and the surface behind it stays. Hysteresis keeps a faint stretch of the outline when it joins a
            // strong one (a page with a shadow along two sides), so the outline still closes.
            val smooth = Mat()
            val faintEdges = Mat()
            val faintClosed = Mat()
            try {
                Imgproc.GaussianBlur(processed, smooth, Size(25.0, 25.0), 0.0)
                Imgproc.Canny(smooth, faintEdges, FAINT_CANNY_LOW, FAINT_CANNY_HIGH)
                Imgproc.dilate(faintEdges, faintClosed, kernel)
                Imgproc.morphologyEx(faintClosed, faintClosed, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)))
            } finally {
                smooth.release(); faintEdges.release()
            }

            // Blur & Brightness diagnostics
            Imgproc.Laplacian(processed, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stdDev)
            val blurWhole = stdDev.toArray().firstOrNull()?.let { it * it } ?: 0.0
            val brightness = Core.mean(working).`val`[0]

            val frameArea = working.cols().toDouble() * working.rows().toDouble()
            val frameWidth = working.cols().toDouble()
            val frameHeight = working.rows().toDouble()
            var best: Candidate? = null

            // Each source finds its own candidates; the best-scoring page outline overall wins. Weights
            // favour the sources that are least likely to trace text or texture instead of the page.
            // The thin Canny map keeps a page separate from a neighbouring object it nearly touches, which
            // the heavily closed maps would fuse into one blob; it is searched with every contour, not just outer ones.
            val thinEdges = Mat()
            Imgproc.morphologyEx(edgesCanny, thinEdges, Imgproc.MORPH_CLOSE, kernel)
            val sources = listOf(
                Triple(cannyClosed, 1.0, Imgproc.RETR_EXTERNAL),
                Triple(thinEdges, 0.98, Imgproc.RETR_LIST),
                Triple(brightMask, 0.96, Imgproc.RETR_EXTERNAL),
                Triple(faintClosed, 0.97, Imgproc.RETR_EXTERNAL),
                Triple(combinedEdges, 0.85, Imgproc.RETR_EXTERNAL),
                Triple(darkMask, 0.7, Imgproc.RETR_EXTERNAL)
            )
            try {
                sources.forEach { (map, weight, retrieval) ->
                    val contours = mutableListOf<MatOfPoint>()
                    val sourceHierarchy = Mat()
                    try {
                        // External contours only: a page is the outer outline, and text inside it is noise.
                        Imgproc.findContours(map, contours, sourceHierarchy, retrieval, Imgproc.CHAIN_APPROX_SIMPLE)
                        contours.forEach { contour ->
                            if (abs(Imgproc.contourArea(contour)) < frameArea * MIN_DOCUMENT_AREA_RATIO) return@forEach
                            val (points, solidity) = pageCorners(contour, frameArea) ?: return@forEach
                            val ordered = order(points)
                            val geometry = ordered.map { QuadGeometry.P(it.x, it.y) }
                            if (!QuadGeometry.isPlausiblePage(geometry)) return@forEach

                            val polygon = MatOfPoint(*ordered.toTypedArray())
                            try {
                                val polygonArea = abs(Imgproc.contourArea(polygon))
                                val areaRatio = (polygonArea / frameArea).coerceIn(0.0, 1.0)
                                val rect = Imgproc.boundingRect(polygon)
                                val rectangularity = if (rect.area() <= 0) 0.0 else (polygonArea / rect.area()).coerceIn(0.0, 1.0)
                                var score = areaRatio * 0.46 + rectangularity * 0.22 + edgeBalance(ordered) * 0.14 +
                                    QuadGeometry.angleScore(geometry) * 0.18
                                // An outline that is just the camera frame is the background, not a page.
                                val margin = 0.012 * max(frameWidth, frameHeight)
                                val onBorder = ordered.count {
                                    it.x < margin || it.y < margin || it.x > frameWidth - margin || it.y > frameHeight - margin
                                }
                                if (areaRatio > 0.9 && onBorder >= 3) score *= 0.3
                                // Several objects fused into one blob have a dented outline; a page is nearly solid.
                                score *= weight * solidity * solidity * solidity

                                val current = best
                                if (current == null || score.toFloat() > current.score) {
                                    best = Candidate(ordered, score.toFloat())
                                }
                            } finally {
                                polygon.release()
                            }
                        }
                    } finally {
                        contours.forEach(MatOfPoint::release)
                        sourceHierarchy.release()
                    }
                }
            } finally {
                brightMask.release()
                darkMask.release()
                bigKernel.release()
                cannyClosed.release()
                faintClosed.release()
                thinEdges.release()
            }

            val candidate = best
            val confidence = candidate?.score ?: 0f
            val quad = candidate?.let { toNormalizedQuad(it.points, working.cols(), working.rows()) }
            // Sharpness is judged on the page itself: a blurry background around a sharp page, or the other
            // way round, must not decide whether the capture is taken.
            val blur = if (candidate == null) blurWhole else runCatching {
                val box = Imgproc.boundingRect(MatOfPoint(*candidate.points.toTypedArray()))
                val inside = org.opencv.core.Rect(
                    box.x.coerceIn(0, working.cols() - 1), box.y.coerceIn(0, working.rows() - 1),
                    box.width.coerceIn(1, working.cols() - box.x.coerceIn(0, working.cols() - 1)),
                    box.height.coerceIn(1, working.rows() - box.y.coerceIn(0, working.rows() - 1))
                )
                val region = processed.submat(inside)
                val regionLaplacian = Mat()
                val regionMean = MatOfDouble()
                val regionStd = MatOfDouble()
                try {
                    Imgproc.Laplacian(region, regionLaplacian, CvType.CV_64F)
                    Core.meanStdDev(regionLaplacian, regionMean, regionStd)
                    regionStd.toArray().firstOrNull()?.let { it * it } ?: blurWhole
                } finally {
                    region.release(); regionLaplacian.release(); regionMean.release(); regionStd.release()
                }
            }.getOrDefault(blurWhole)
            // Glare is judged on the unsmoothed page: a reflection is a solid patch of clipped white.
            val glare = if (candidate == null) 0.0 else runCatching {
                val box = Imgproc.boundingRect(MatOfPoint(*candidate.points.toTypedArray()))
                val x = box.x.coerceIn(0, working.cols() - 1)
                val y = box.y.coerceIn(0, working.rows() - 1)
                PageQuality.glareShare(
                    working,
                    org.opencv.core.Rect(x, y, box.width.coerceIn(1, working.cols() - x), box.height.coerceIn(1, working.rows() - y))
                )
            }.getOrDefault(0.0)
            val guidance = when {
                // Darkness first: a dark frame hides the outline, so "point at a document" would mislead.
                brightness < 45.0 -> Guidance.MORE_LIGHT
                quad == null -> Guidance.POINT_AT_DOCUMENT
                blur < 80.0 -> Guidance.HOLD_STEADY
                confidence < 0.48f -> Guidance.MOVE_CLOSER
                PageQuality.isGlare(glare) -> Guidance.GLARE
                else -> Guidance.READY
            }

            return DocumentDetection(
                quad = quad,
                confidence = confidence,
                blurScore = blur,
                brightness = brightness,
                frameWidth = working.cols(),
                frameHeight = working.rows(),
                guidance = guidance,
                glareShare = glare
            )
        } finally {
            if (working !== grayInput) working.release()
            processed.release()
            edgesCanny.release()
            edgesAdaptive.release()
            combinedEdges.release()
            hierarchy.release()
            laplacian.release()
            kernel.release()
            stdDev.release()
            mean.release()
        }
    }

    fun detect(bitmap: Bitmap): DocumentDetection {
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return detect(gray)
        } finally {
            rgba.release()
            gray.release()
        }
    }

    /**
     * Four corners for a contour, or null if it does not look like a page. Works on the convex hull, so a
     * broken or notched outline (a thumb over the edge, a fold) still gives a clean quad.
     */
    private fun pageCorners(contour: MatOfPoint, frameArea: Double): Pair<Array<Point>, Double>? {
        val hullIndices = org.opencv.core.MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val all = contour.toArray()
        val hull = hullIndices.toArray().map { all[it] }.toTypedArray()
        hullIndices.release()
        if (hull.size < 4) return null

        val hull2f = MatOfPoint2f(*hull)
        try {
            val hullArea = abs(Imgproc.contourArea(hull2f))
            val solidity = if (hullArea <= 0) 0.0 else (abs(Imgproc.contourArea(contour)) / hullArea).coerceIn(0.0, 1.0)
            if (solidity < 0.8) return null
            val perimeter = Imgproc.arcLength(hull2f, true)
            for (ratio in listOf(0.015, 0.02, 0.028, 0.035, 0.045, 0.06)) {
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(hull2f, approx, perimeter * ratio, true)
                    val pts = approx.toArray()
                    if (pts.size == 4) return pts to solidity
                } finally {
                    approx.release()
                }
            }
            // No clean four-sided fit: use the tightest rectangle, but only if the outline really fills it.
            val minRect: RotatedRect = Imgproc.minAreaRect(hull2f)
            val boxArea = minRect.size.width * minRect.size.height
            if (boxArea < frameArea * MIN_DOCUMENT_AREA_RATIO || hullArea / boxArea < 0.82) return null
            val boxPts = Array(4) { Point() }
            minRect.points(boxPts)
            return boxPts to solidity
        } finally {
            hull2f.release()
        }
    }

    private fun resizeForAnalysis(source: Mat): Mat {
        val longSide = max(source.cols(), source.rows()).toDouble()
        if (longSide <= ANALYSIS_LONG_SIDE) return source
        val scale = ANALYSIS_LONG_SIDE / longSide
        val output = Mat()
        Imgproc.resize(source, output, Size(source.cols() * scale, source.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        return output
    }

    private fun order(points: Array<Point>): List<Point> {
        val pts = points.toList()
        val tl = pts.minByOrNull { it.x + it.y } ?: points[0]
        val br = pts.maxByOrNull { it.x + it.y } ?: points[2]
        val tr = pts.maxByOrNull { it.x - it.y } ?: points[1]
        val bl = pts.minByOrNull { it.x - it.y } ?: points[3]

        // Ensure 4 distinct points in order: Top-Left, Top-Right, Bottom-Right, Bottom-Left
        val list = listOf(tl, tr, br, bl)
        if (list.distinct().size == 4) return list

        // Centroid angle fallback if duplicate corners selected
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        val clockwise = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        val start = clockwise.indices.minByOrNull { clockwise[it].x + clockwise[it].y } ?: 0
        return List(4) { clockwise[(start + it) % 4] }
    }

    private fun edgeBalance(points: List<Point>): Double {
        val top = hypot(points[1].x - points[0].x, points[1].y - points[0].y)
        val right = hypot(points[2].x - points[1].x, points[2].y - points[1].y)
        val bottom = hypot(points[3].x - points[2].x, points[3].y - points[2].y)
        val left = hypot(points[0].x - points[3].x, points[0].y - points[3].y)
        val hRatio = min(top, bottom) / max(top, bottom).coerceAtLeast(1.0)
        val vRatio = min(left, right) / max(left, right).coerceAtLeast(1.0)
        return (hRatio + vRatio) / 2.0
    }

    private fun toNormalizedQuad(points: List<Point>, width: Int, height: Int): CropQuad {
        fun norm(point: Point) = NormalizedPoint(
            (point.x / width.toDouble()).toFloat().coerceIn(0f, 1f),
            (point.y / height.toDouble()).toFloat().coerceIn(0f, 1f)
        )
        return CropQuad(norm(points[0]), norm(points[1]), norm(points[2]), norm(points[3]))
    }

    private data class Candidate(val points: List<Point>, val score: Float)
}
