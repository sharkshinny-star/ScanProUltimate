package com.example.scanpro.scanner

import kotlin.math.min
import kotlin.math.sqrt

/**
 * The colour-strength map of [DocumentEdgeDetector.chromaMap], made straight from a camera frame's Y, U and V planes
 * (2026-11-18, R57). The live outline and auto-capture used to see only brightness, while the saved crop also saw
 * colour (F175): a page on a wooden desk with a shadow over most of one side was outlined wrongly live and correctly
 * once saved. One value is made for every [STEP] x [STEP] pixels: the detector shrinks the map to its working size
 * anyway, and a quarter-size map costs a small fraction of a frame's detection time.
 *
 * Plain arrays in and out, so it is tested on the JVM without a camera or OpenCV.
 */
internal object LiveChroma {
    const val STEP = 4

    fun width(frameWidth: Int) = frameWidth / STEP
    fun height(frameHeight: Int) = frameHeight / STEP

    /**
     * Fills [out] (at least [width] x [height] values, row by row) from the frame's packed [luma] ([frameWidth] x
     * [frameHeight]) and its [u] and [v] planes as the camera delivers them (half size, with [uvRowStride] and
     * [uvPixelStride]). False, with [out] untouched, when an array is too short for the frame.
     */
    fun compute(
        luma: ByteArray, frameWidth: Int, frameHeight: Int,
        u: ByteArray, v: ByteArray, uvRowStride: Int, uvPixelStride: Int,
        out: ByteArray
    ): Boolean {
        val w = width(frameWidth)
        val h = height(frameHeight)
        if (w <= 0 || h <= 0 || out.size < w * h || luma.size < frameWidth * frameHeight) return false
        val lastUv = (2 * (h - 1) + 1) * uvRowStride + (2 * (w - 1) + 1) * uvPixelStride
        if (uvRowStride <= 0 || uvPixelStride <= 0 || lastUv >= u.size || lastUv >= v.size) return false
        var o = 0
        for (qy in 0 until h) {
            // The chroma sample in the middle of each STEP x STEP block, and the four luma pixels it covers.
            val cy = 2 * qy + 1
            val uvRow = cy * uvRowStride
            val lumaRow = 2 * cy * frameWidth
            for (qx in 0 until w) {
                val cx = 2 * qx + 1
                val a = lumaRow + 2 * cx
                val y = ((luma[a].toInt() and 0xFF) + (luma[a + 1].toInt() and 0xFF) +
                    (luma[a + frameWidth].toInt() and 0xFF) + (luma[a + frameWidth + 1].toInt() and 0xFF)) / 4f
                val uv = uvRow + cx * uvPixelStride
                val cb = (u[uv].toInt() and 0xFF) - 128
                val cr = (v[uv].toInt() and 0xFF) - 128
                // Full-range BT.601, as phone cameras deliver their preview frames.
                val r = (y + 1.402f * cr).coerceIn(0f, 255f)
                val g = (y - 0.344f * cb - 0.714f * cr).coerceIn(0f, 255f)
                val b = (y + 1.772f * cb).coerceIn(0f, 255f)
                val sum = r + g + b + 1f
                val rn = r / sum - 1f / 3f
                val gn = g / sum - 1f / 3f
                val strength = sqrt(rn * rn + gn * gn) * min(1f, sum / DocumentEdgeDetector.CHROMA_DARK_SUM.toFloat()) *
                    DocumentEdgeDetector.CHROMA_SCALE.toFloat()
                out[o++] = (strength + 0.5f).toInt().coerceAtMost(255).toByte()
            }
        }
        return true
    }

    /**
     * The frame's two colour-difference planes at the map's size, interleaved (Cb then Cr for each cell) into [out]
     * (at least 2 x [width] x [height] bytes), for [DocumentEdgeDetector]'s colour edges (2026-11-19): each cell is the
     * mean of the four colour samples its [STEP] x [STEP] block covers. False, with [out] untouched, when an array is
     * too short for the frame.
     */
    fun planes(
        frameWidth: Int, frameHeight: Int,
        u: ByteArray, v: ByteArray, uvRowStride: Int, uvPixelStride: Int,
        out: ByteArray
    ): Boolean {
        val w = width(frameWidth)
        val h = height(frameHeight)
        if (w <= 0 || h <= 0 || out.size < 2 * w * h) return false
        val lastUv = (2 * (h - 1) + 1) * uvRowStride + (2 * (w - 1) + 1) * uvPixelStride
        if (uvRowStride <= 0 || uvPixelStride <= 0 || lastUv >= u.size || lastUv >= v.size) return false
        var o = 0
        for (qy in 0 until h) {
            val row0 = 2 * qy * uvRowStride
            val row1 = row0 + uvRowStride
            for (qx in 0 until w) {
                val a = 2 * qx * uvPixelStride
                val b = a + uvPixelStride
                out[o++] = (((u[row0 + a].toInt() and 0xFF) + (u[row0 + b].toInt() and 0xFF) +
                    (u[row1 + a].toInt() and 0xFF) + (u[row1 + b].toInt() and 0xFF) + 2) / 4).toByte()
                out[o++] = (((v[row0 + a].toInt() and 0xFF) + (v[row0 + b].toInt() and 0xFF) +
                    (v[row1 + a].toInt() and 0xFF) + (v[row1 + b].toInt() and 0xFF) + 2) / 4).toByte()
            }
        }
        return true
    }
}
