package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Detects in-plane page skew in a *rotated, upright camera frame*.
 *
 * This is a framing hint, not text-orientation recognition. The quad alone cannot tell whether
 * a page's writing is upside down or whether a landscape document was intentionally rotated.
 * We therefore warn but do not prohibit manual capture or auto-capture.
 */
internal object ScanAlignment {
    private const val MAX_EDGE_DISAGREEMENT_DEGREES = 12.0
    private const val MIN_NOTICEABLE_SKEW_DEGREES = 12.0

    /** Average direction of the page's top and bottom edges, or null if perspective prevents a reliable estimate. */
    fun skewDegrees(quad: CropQuad, frameWidth: Int, frameHeight: Int): Double? {
        if (frameWidth <= 0 || frameHeight <= 0) return null

        fun direction(from: NormalizedPoint, to: NormalizedPoint): Double {
            val dx = (to.x - from.x) * frameWidth
            val dy = (to.y - from.y) * frameHeight
            return atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI
        }

        val top = direction(quad.topLeft, quad.topRight)
        val bottom = direction(quad.bottomLeft, quad.bottomRight)
        // Strong perspective distortion makes the edges diverge. Reporting that as rotation is misleading.
        if (!top.isFinite() || !bottom.isFinite() || abs(top - bottom) > MAX_EDGE_DISAGREEMENT_DEGREES) return null
        return (top + bottom) / 2.0
    }

    fun isSkewed(quad: CropQuad, frameWidth: Int, frameHeight: Int): Boolean =
        (skewDegrees(quad, frameWidth, frameHeight)?.let { abs(it) >= MIN_NOTICEABLE_SKEW_DEGREES }) == true
}
