package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Duplicate-aware auto-capture (R32), after CamScanner's "turn the page to capture" and Adobe's high-speed scan:
 * a 32 x 32 greyscale signature of the page just captured, compared with what is in view, so the shutter does not
 * fire again on the same page while it is still (or comes back into) the frame, and does fire on the next one.
 */
object PageSignature {
    const val SIZE = 32

    /** Mean absolute difference, on brightness-and-contrast-normalised signatures, below which it is the same page. */
    const val SAME_PAGE = 0.22f

    /** The page in [gray] (one channel) inside [quad]'s bounding box, shrunk to [SIZE] x [SIZE] and normalised. */
    fun of(gray: Mat, quad: CropQuad): FloatArray {
        val w = gray.cols()
        val h = gray.rows()
        val xs = listOf(quad.topLeft.x, quad.topRight.x, quad.bottomRight.x, quad.bottomLeft.x)
        val ys = listOf(quad.topLeft.y, quad.topRight.y, quad.bottomRight.y, quad.bottomLeft.y)
        val left = (xs.min() * w).roundToInt().coerceIn(0, w - 1)
        val top = (ys.min() * h).roundToInt().coerceIn(0, h - 1)
        val right = (xs.max() * w).roundToInt().coerceIn(left + 1, w)
        val bottom = (ys.max() * h).roundToInt().coerceIn(top + 1, h)
        val page = gray.submat(Rect(left, top, right - left, bottom - top))
        val small = Mat()
        try {
            Imgproc.resize(page, small, Size(SIZE.toDouble(), SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            val bytes = ByteArray(SIZE * SIZE)
            small.get(0, 0, bytes)
            return normalise(FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF).toFloat() })
        } finally {
            small.release()
            page.release()
        }
    }

    /** [values] with their mean taken out and divided by their spread, so lighting and exposure drop out. */
    fun normalise(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val mean = values.average().toFloat()
        var variance = 0f
        for (v in values) variance += (v - mean) * (v - mean)
        val spread = max(sqrt(variance / values.size), 1e-3f)
        return FloatArray(values.size) { (values[it] - mean) / spread }
    }

    /** Mean absolute difference of two signatures of the same length (the larger of two lengths otherwise). */
    fun distance(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) sum += abs(a[i] - b[i])
        return sum / n
    }

    fun isSamePage(a: FloatArray, b: FloatArray): Boolean = distance(a, b) < SAME_PAGE
}

/**
 * Fires the shutter on a sharp frame rather than the first steady one: the current frame must be about as sharp
 * as the sharpest of the recent steady frames, so a hand that is still settling does not get the photo.
 */
object SharpnessGate {
    const val WINDOW = 5
    const val SHARE_OF_BEST = 0.9

    fun shouldFire(recentBlurScores: Collection<Double>, current: Double): Boolean {
        return recentBlurScores.size < 3 || current >= recentBlurScores.max() * SHARE_OF_BEST
    }
}
