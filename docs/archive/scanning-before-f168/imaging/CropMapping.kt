package com.example.scanpro.imaging

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAnnotation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where each point of a cropped page came from in the original photo, matching how [ImageProcessor] renders
 * it: a perspective (straight) crop or a curved one. Points are fractions of the cropped page (u, v) and of
 * the photo (x, y). [aspect] is the photo's width divided by its height, needed because curved crops are
 * spaced by real length along the bent edges.
 */
class CropMapping(crop: CropQuad, private val aspect: Float) {
    private val straight = Homography.squareToQuad(
        crop.topLeft.x, crop.topLeft.y, crop.topRight.x, crop.topRight.y,
        crop.bottomRight.x, crop.bottomRight.y, crop.bottomLeft.x, crop.bottomLeft.y
    )
    private val inverseStraight = straight?.let(Homography::invert)

    // Curved crops: all four edges sampled evenly by length, in photo coordinates scaled to the true aspect.
    private val top: Array<CurvedPage.Pt>?
    private val bottom: Array<CurvedPage.Pt>?
    private val left: Array<CurvedPage.Pt>?
    private val right: Array<CurvedPage.Pt>?

    init {
        if (crop.isCurved) {
            fun pt(p: NormalizedPoint) = CurvedPage.Pt(p.x * aspect, p.y)
            fun edge(a: CurvedPage.Pt, b: CurvedPage.Pt, bow: NormalizedPoint?): Array<CurvedPage.Pt> {
                val mid = CurvedPage.Pt((a.x + b.x) / 2f + (bow?.x ?: 0f) * aspect, (a.y + b.y) / 2f + (bow?.y ?: 0f))
                return CurvedPage.evenlySpaced(a, CurvedPage.control(a, mid, b), b, SAMPLES)
            }
            val tl = pt(crop.topLeft); val tr = pt(crop.topRight); val br = pt(crop.bottomRight); val bl = pt(crop.bottomLeft)
            top = edge(tl, tr, crop.topBow)
            bottom = edge(bl, br, crop.bottomBow)
            left = edge(tl, bl, crop.leftBow)
            right = edge(tr, br, crop.rightBow)
        } else {
            top = null; bottom = null; left = null; right = null
        }
    }

    private fun Array<CurvedPage.Pt>.at(fraction: Float): CurvedPage.Pt {
        val position = fraction.coerceIn(0f, 1f) * (SAMPLES - 1)
        val i = min(position.toInt(), SAMPLES - 2)
        val f = position - i
        return CurvedPage.Pt(this[i].x + (this[i + 1].x - this[i].x) * f, this[i].y + (this[i + 1].y - this[i].y) * f)
    }

    /** The photo point shown at (u, v) of the cropped page. */
    fun toSource(u: Float, v: Float): Pair<Float, Float>? {
        val t = top; val b = bottom; val l = left; val r = right
        if (t == null || b == null || l == null || r == null) return straight?.let { Homography.apply(it, u, v) }
        val p = CurvedPage.coons(t.at(u), b.at(u), l.at(v), r.at(v), t.first(), t.last(), b.first(), b.last(), u, v)
        return (p.x / aspect) to p.y
    }

    /** Where the photo point (x, y) lands on the cropped page, or null if it can't be placed. */
    fun fromSource(x: Float, y: Float): Pair<Float, Float>? {
        val guess = inverseStraight?.let { Homography.apply(it, x, y) } ?: return null
        if (top == null) return guess
        // Curved: refine the straight-crop estimate with Gauss-Newton on the forward mapping.
        var u = guess.first
        var v = guess.second
        repeat(25) {
            val p = toSource(u, v) ?: return null
            val ex = p.first - x
            val ey = p.second - y
            if (abs(ex) < 1e-5f && abs(ey) < 1e-5f) return u to v
            val h = 1e-3f
            val pu = toSource(u + h, v) ?: return null
            val pv = toSource(u, v + h) ?: return null
            val a = (pu.first - p.first) / h; val c = (pu.second - p.second) / h
            val b = (pv.first - p.first) / h; val d = (pv.second - p.second) / h
            val det = a * d - b * c
            if (abs(det) < 1e-9f) return null
            u -= (d * ex - b * ey) / det
            v -= (-c * ex + a * ey) / det
        }
        val check = toSource(u, v) ?: return null
        return if (abs(check.first - x) < 1e-3f && abs(check.second - y) < 1e-3f) u to v else null
    }

    companion object {
        private const val SAMPLES = 257
    }
}

/** Keeps mark-up over the same content when a page's crop changes. */
object AnnotationRemap {
    /**
     * Moves each box from the page cropped with [oldCrop] to the page cropped with [newCrop]; both are turned by
     * [rotationDegrees] after cropping, as rendering does. A box becomes the smallest upright box around where
     * its corners land, so a redaction still covers everything it covered. Boxes whose content is now cropped
     * away are dropped.
     */
    fun remap(
        annotations: List<PageAnnotation>,
        oldCrop: CropQuad,
        newCrop: CropQuad,
        rotationDegrees: Int,
        aspect: Float
    ): List<PageAnnotation> {
        if (annotations.isEmpty() || oldCrop == newCrop) return annotations
        val from = CropMapping(oldCrop, aspect)
        val to = CropMapping(newCrop, aspect)
        val turns = Math.floorMod(rotationDegrees, 360) / 90
        return annotations.mapNotNull { item ->
            val corners = listOf(
                item.x to item.y, item.x + item.width to item.y,
                item.x to item.y + item.height, item.x + item.width to item.y + item.height
            )
            val moved = corners.map { (x, y) ->
                val (u, v) = unturn(x, y, turns)
                val source = from.toSource(u, v) ?: return@mapNotNull item
                val target = to.fromSource(source.first, source.second) ?: return@mapNotNull item
                turn(target.first, target.second, turns)
            }
            val left = moved.minOf { it.first }; val right = moved.maxOf { it.first }
            val top = moved.minOf { it.second }; val bottom = moved.maxOf { it.second }
            if (right <= 0f || bottom <= 0f || left >= 1f || top >= 1f) return@mapNotNull null
            val x = max(0f, left); val y = max(0f, top)
            val width = min(1f, right) - x
            val height = min(1f, bottom) - y
            if (width < 0.005f || height < 0.005f) null else item.copy(x = x, y = y, width = width, height = height)
        }
    }

    /** From the turned page back to the page as cropped: each step undoes one quarter turn clockwise. */
    private fun unturn(x: Float, y: Float, turns: Int): Pair<Float, Float> {
        var p = x to y
        repeat(turns) { p = p.second to (1f - p.first) }
        return p
    }

    /** One quarter turn clockwise per step, as [com.example.scanpro.data.PageAnnotation] rotation does. */
    private fun turn(x: Float, y: Float, turns: Int): Pair<Float, Float> {
        var p = x to y
        repeat(turns) { p = (1f - p.second) to p.first }
        return p
    }
}

/** 3×3 projective maps stored row-major. */
internal object Homography {
    /** The map taking the unit square's corners (0,0) (1,0) (1,1) (0,1) to the given quad, or null if degenerate. */
    fun squareToQuad(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): DoubleArray? {
        val dx1 = (x1 - x2).toDouble(); val dx2 = (x3 - x2).toDouble()
        val dy1 = (y1 - y2).toDouble(); val dy2 = (y3 - y2).toDouble()
        val sx = (x0 - x1 + x2 - x3).toDouble(); val sy = (y0 - y1 + y2 - y3).toDouble()
        val (g, h) = if (abs(sx) < 1e-12 && abs(sy) < 1e-12) 0.0 to 0.0 else {
            val det = dx1 * dy2 - dx2 * dy1
            if (abs(det) < 1e-12) return null
            (sx * dy2 - dx2 * sy) / det to (dx1 * sy - sx * dy1) / det
        }
        val a = x1 - x0 + g * x1; val b = x3 - x0 + h * x3
        val d = y1 - y0 + g * y1; val e = y3 - y0 + h * y3
        return doubleArrayOf(a, b, x0.toDouble(), d, e, y0.toDouble(), g, h, 1.0)
    }

    fun apply(m: DoubleArray, x: Float, y: Float): Pair<Float, Float>? {
        val w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-12) return null
        return ((m[0] * x + m[1] * y + m[2]) / w).toFloat() to ((m[3] * x + m[4] * y + m[5]) / w).toFloat()
    }

    fun invert(m: DoubleArray): DoubleArray? {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-12) return null
        return doubleArrayOf(
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det
        )
    }
}
