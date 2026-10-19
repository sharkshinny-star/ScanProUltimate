package com.example.scanpro.scanner

import android.graphics.Bitmap
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
    val guidance: String
) {
    val isBlurry: Boolean get() = blurScore < 85.0
    val isDark: Boolean get() = brightness < 48.0
    val isReady: Boolean get() = quad != null && confidence >= 0.52f && !isBlurry && !isDark
}

/**
 * OpenCV document detector tuned for live mobile preview frames.
 * Detection intentionally runs on grayscale, down-scaled images. The original capture
 * is only used after the shutter is fired, keeping preview latency low.
 */
object DocumentEdgeDetector {
    private const val ANALYSIS_LONG_SIDE = 900.0
    private const val MIN_DOCUMENT_AREA_RATIO = 0.12

    fun detect(grayInput: Mat): DocumentDetection {
        require(grayInput.channels() == 1) { "DocumentEdgeDetector expects a grayscale Mat" }

        val working = resizeForAnalysis(grayInput)
        val processed = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val laplacian = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val stdDev = MatOfDouble()
        val mean = MatOfDouble()

        try {
            Imgproc.GaussianBlur(working, processed, Size(5.0, 5.0), 0.0)
            val median = Core.mean(processed).`val`[0].coerceIn(20.0, 230.0)
            val lower = max(20.0, median * 0.55)
            val upper = min(255.0, median * 1.45)
            Imgproc.Canny(processed, edges, lower, upper)
            Imgproc.morphologyEx(
                edges,
                edges,
                Imgproc.MORPH_CLOSE,
                kernel
            )

            Imgproc.Laplacian(processed, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stdDev)
            val blur = stdDev.toArray().firstOrNull()?.let { it * it } ?: 0.0
            val brightness = Core.mean(working).`val`[0]

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = working.cols().toDouble() * working.rows().toDouble()
            var best: Candidate? = null

            try {
            contours.forEach { contour ->
                val area = abs(Imgproc.contourArea(contour))
                if (area < frameArea * MIN_DOCUMENT_AREA_RATIO) return@forEach

                val contour2f = MatOfPoint2f(*contour.toArray())
                val perimeter = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(contour2f, approx, perimeter * 0.02, true)
                    val points = approx.toArray()
                    if (points.size != 4) return@forEach
                    val asInt = MatOfPoint(*points)
                    try {
                        if (!Imgproc.isContourConvex(asInt)) return@forEach
                    } finally {
                        asInt.release()
                    }

                    val ordered = order(points)
                    val polygon = MatOfPoint(*ordered.toTypedArray())
                    try {
                        val polygonArea = abs(Imgproc.contourArea(polygon))
                        val areaRatio = (polygonArea / frameArea).coerceIn(0.0, 1.0)
                        val rect = Imgproc.boundingRect(polygon)
                        val rectangularity = if (rect.area() <= 0) 0.0 else (polygonArea / rect.area()).coerceIn(0.0, 1.0)
                        val edgeBalance = edgeBalance(ordered)
                        val score = (areaRatio * 0.58 + rectangularity * 0.25 + edgeBalance * 0.17).toFloat()
                        val current = best
                        if (current == null || score > current.score) best = Candidate(ordered, score)
                    } finally {
                        polygon.release()
                    }
                } finally {
                    approx.release()
                    contour2f.release()
                }
            }
            } finally {
                // Release native contour memory even if candidate scoring throws.
                contours.forEach(MatOfPoint::release)
            }

            val candidate = best
            val confidence = candidate?.score ?: 0f
            val quad = candidate?.let { toNormalizedQuad(it.points, working.cols(), working.rows()) }
            val guidance = when {
                quad == null -> "Point the camera at a document"
                brightness < 48.0 -> "More light needed"
                blur < 85.0 -> "Hold steady"
                confidence < 0.52f -> "Move closer and fit all four corners"
                else -> "Ready"
            }

            return DocumentDetection(
                quad = quad,
                confidence = confidence,
                blurScore = blur,
                brightness = brightness,
                frameWidth = working.cols(),
                frameHeight = working.rows(),
                guidance = guidance
            )
        } finally {
            if (working !== grayInput) working.release()
            processed.release()
            edges.release()
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

    private fun resizeForAnalysis(source: Mat): Mat {
        val longSide = max(source.cols(), source.rows()).toDouble()
        if (longSide <= ANALYSIS_LONG_SIDE) return source
        val scale = ANALYSIS_LONG_SIDE / longSide
        val output = Mat()
        Imgproc.resize(source, output, Size(source.cols() * scale, source.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        return output
    }

    /**
     * Orders four corners as top-left, top-right, bottom-right, bottom-left. Sorting by angle around the
     * centroid always yields four distinct corners; picking by min/max of x+y and x-y could choose the
     * same point twice for a page tilted near 45 degrees.
     */
    private fun order(points: Array<Point>): List<Point> {
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        // Clockwise in image coordinates (y grows downward), starting from the upper-left.
        val clockwise = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        val start = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
        return List(4) { clockwise[(start + it) % 4] }
    }

    private fun toNormalizedQuad(points: List<Point>, width: Int, height: Int): CropQuad {
        fun norm(point: Point) = NormalizedPoint(
            (point.x / width.toDouble()).toFloat().coerceIn(0f, 1f),
            (point.y / height.toDouble()).toFloat().coerceIn(0f, 1f)
        )
        return CropQuad(norm(points[0]), norm(points[1]), norm(points[2]), norm(points[3]))
    }

    /** Prefers quadrilaterals whose opposite edges have similar length. */
    private fun edgeBalance(points: List<Point>): Double {
        fun d(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
        val top = d(points[0], points[1])
        val right = d(points[1], points[2])
        val bottom = d(points[2], points[3])
        val left = d(points[3], points[0])
        fun similarity(a: Double, b: Double): Double {
            val max = max(a, b)
            return if (max <= 1.0) 0.0 else min(a, b) / max
        }
        return (similarity(top, bottom) + similarity(left, right)) / 2.0
    }

    private data class Candidate(val points: List<Point>, val score: Float)
}
