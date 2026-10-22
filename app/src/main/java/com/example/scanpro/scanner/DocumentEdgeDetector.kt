package com.example.scanpro.scanner

import com.example.scanpro.imaging.OpenCvRuntime
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
    val guidance: String
) {
    val isBlurry: Boolean get() = blurScore < 80.0
    val isDark: Boolean get() = brightness < 45.0
    val isReady: Boolean get() = quad != null && confidence >= 0.48f && !isBlurry && !isDark
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

            // Blur & Brightness diagnostics
            Imgproc.Laplacian(processed, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stdDev)
            val blur = stdDev.toArray().firstOrNull()?.let { it * it } ?: 0.0
            val brightness = Core.mean(working).`val`[0]

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(combinedEdges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = working.cols().toDouble() * working.rows().toDouble()
            var best: Candidate? = null

            try {
                contours.forEach { contour ->
                    val area = abs(Imgproc.contourArea(contour))
                    if (area < frameArea * MIN_DOCUMENT_AREA_RATIO) return@forEach

                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val perimeter = Imgproc.arcLength(contour2f, true)

                    var candidatePoints: Array<Point>? = null

                    // 1. Multi-Epsilon Polygon Approximation (0.015 to 0.05)
                    for (ratio in listOf(0.015, 0.02, 0.028, 0.035, 0.045)) {
                        val approx = MatOfPoint2f()
                        try {
                            Imgproc.approxPolyDP(contour2f, approx, perimeter * ratio, true)
                            val pts = approx.toArray()
                            if (pts.size == 4) {
                                val asInt = MatOfPoint(*pts)
                                val isConvex = Imgproc.isContourConvex(asInt)
                                asInt.release()
                                if (isConvex) {
                                    candidatePoints = pts
                                    break
                                }
                            }
                        } finally {
                            approx.release()
                        }
                    }

                    // 2. Fallback: MinAreaRect bounding box quad if approxPolyDP didn't yield 4 points
                    if (candidatePoints == null) {
                        val minRect: RotatedRect = Imgproc.minAreaRect(contour2f)
                        val boxPts = Array<Point>(4) { Point() }
                        minRect.points(boxPts)
                        val boxArea = minRect.size.width * minRect.size.height
                        if (boxArea >= frameArea * MIN_DOCUMENT_AREA_RATIO) {
                            candidatePoints = boxPts
                        }
                    }

                    contour2f.release()

                    val points = candidatePoints ?: return@forEach
                    val ordered = order(points)
                    val polygon = MatOfPoint(*ordered.toTypedArray())
                    try {
                        val polygonArea = abs(Imgproc.contourArea(polygon))
                        val areaRatio = (polygonArea / frameArea).coerceIn(0.0, 1.0)
                        val rect = Imgproc.boundingRect(polygon)
                        val rectangularity = if (rect.area() <= 0) 0.0 else (polygonArea / rect.area()).coerceIn(0.0, 1.0)
                        val edgeBalance = edgeBalance(ordered)
                        val score = (areaRatio * 0.52 + rectangularity * 0.30 + edgeBalance * 0.18).toFloat()

                        val current = best
                        if (current == null || score > current.score) {
                            best = Candidate(ordered, score)
                        }
                    } finally {
                        polygon.release()
                    }
                }
            } finally {
                contours.forEach(MatOfPoint::release)
            }

            val candidate = best
            val confidence = candidate?.score ?: 0f
            val quad = candidate?.let { toNormalizedQuad(it.points, working.cols(), working.rows()) }
            val guidance = when {
                quad == null -> "Point camera at document"
                brightness < 45.0 -> "More light needed"
                blur < 80.0 -> "Hold steady"
                confidence < 0.48f -> "Move closer and center page"
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
