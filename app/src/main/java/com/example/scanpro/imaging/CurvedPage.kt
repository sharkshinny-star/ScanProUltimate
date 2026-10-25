package com.example.scanpro.imaging

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Geometry for flattening a curved page (a thick book, a bent receipt): each edge is a quadratic curve through
 * its two corners and a bent midpoint, and the inside is filled by blending the four edges (a Coons patch).
 * With straight sides this is simply a blend of the top and bottom curves. Points along each curve are spaced
 * by length rather than by curve parameter, so letters keep their width across the bend.
 */
object CurvedPage {
    data class Pt(val x: Float, val y: Float) {
        operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
        operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
        operator fun times(k: Float) = Pt(x * k, y * k)
        fun length() = hypot(x, y)
    }

    /** The Bezier control point that makes the curve from [a] to [b] pass through [mid] halfway along. */
    fun control(a: Pt, mid: Pt, b: Pt) = Pt(2f * mid.x - (a.x + b.x) / 2f, 2f * mid.y - (a.y + b.y) / 2f)

    fun bezier(a: Pt, c: Pt, b: Pt, u: Float): Pt {
        val v = 1f - u
        return Pt(v * v * a.x + 2f * u * v * c.x + u * u * b.x, v * v * a.y + 2f * u * v * c.y + u * u * b.y)
    }

    fun length(a: Pt, c: Pt, b: Pt, steps: Int = 64): Float {
        var total = 0f
        var previous = a
        for (i in 1..steps) {
            val next = bezier(a, c, b, i / steps.toFloat())
            total += (next - previous).length()
            previous = next
        }
        return total
    }

    /** [count] points along the curve, evenly spaced by distance, from [a] to [b] inclusive. */
    fun evenlySpaced(a: Pt, c: Pt, b: Pt, count: Int): Array<Pt> {
        require(count >= 2)
        val steps = 256
        val lengths = FloatArray(steps + 1)
        var previous = a
        for (i in 1..steps) {
            val next = bezier(a, c, b, i / steps.toFloat())
            lengths[i] = lengths[i - 1] + (next - previous).length()
            previous = next
        }
        val total = lengths[steps]
        if (total <= 0f) return Array(count) { a }
        var segment = 0
        return Array(count) { index ->
            val target = total * index / (count - 1)
            while (segment < steps - 1 && lengths[segment + 1] < target) segment++
            val span = lengths[segment + 1] - lengths[segment]
            val fraction = if (span > 0f) ((target - lengths[segment]) / span).coerceIn(0f, 1f) else 0f
            bezier(a, c, b, (segment + fraction) / steps)
        }
    }

    /**
     * Height of the flattened page: the longer side, or the widest gap between the top and bottom curves when
     * those bulge apart more than the sides are long.
     */
    fun outputHeight(top: Array<Pt>, bottom: Array<Pt>, leftLength: Float, rightLength: Float): Int {
        var height = max(leftLength, rightLength)
        val stride = max(1, top.size / 32)
        for (i in top.indices step stride) height = max(height, (bottom[i] - top[i]).length())
        return height.roundToInt()
    }

    /** The point of the page at (u, v), blended from the four edges sampled at those fractions. */
    fun coons(t: Pt, b: Pt, l: Pt, r: Pt, tl: Pt, tr: Pt, bl: Pt, br: Pt, u: Float, v: Float): Pt {
        val iu = 1f - u
        val iv = 1f - v
        val x = iv * t.x + v * b.x + iu * l.x + u * r.x - (iu * iv * tl.x + u * iv * tr.x + iu * v * bl.x + u * v * br.x)
        val y = iv * t.y + v * b.y + iu * l.y + u * r.y - (iu * iv * tl.y + u * iv * tr.y + iu * v * bl.y + u * v * br.y)
        return Pt(x, y)
    }

    /**
     * Fills [mapX]/[mapY] (row-major, [rows] × top.size) with the source position of each output pixel in rows
     * [firstRow] until firstRow + rows. [top]/[bottom] are sampled once per output column and [left]/[right]
     * (top to bottom) once per output row.
     */
    fun fillRows(
        top: Array<Pt>, bottom: Array<Pt>, left: Array<Pt>, right: Array<Pt>,
        firstRow: Int, rows: Int, mapX: FloatArray, mapY: FloatArray
    ) {
        val width = top.size
        val height = left.size
        val tl = top.first(); val tr = top.last(); val bl = bottom.first(); val br = bottom.last()
        val uDenominator = max(1, width - 1).toFloat()
        val vDenominator = max(1, height - 1).toFloat()
        for (r in 0 until rows) {
            val row = firstRow + r
            val v = row / vDenominator
            val l = left[row]
            val rr = right[row]
            val offset = r * width
            for (x in 0 until width) {
                val p = coons(top[x], bottom[x], l, rr, tl, tr, bl, br, x / uDenominator, v)
                mapX[offset + x] = p.x
                mapY[offset + x] = p.y
            }
        }
    }

    /**
     * How far the real page edge between corners [a] and [b] bends away from the straight line at its middle,
     * found by looking for the page's edge (dark outside, bright inside) along the line's normal at several
     * places. [inward] points from the edge towards the page. Returns the offset of the curve's midpoint from
     * the line's midpoint, or null when the edge can't be found reliably.
     */
    fun estimateBow(a: Pt, b: Pt, inward: Pt, searchPx: Float, intensity: (Float, Float) -> Float): Pt? {
        val chord = b - a
        val chordLength = chord.length()
        if (chordLength < 20f || searchPx < 4f) return null
        var normal = Pt(-chord.y / chordLength, chord.x / chordLength)
        if (normal.x * inward.x + normal.y * inward.y < 0f) normal = normal * -1f
        val step = 2f
        val estimates = mutableListOf<Float>()
        for (u in floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)) {
            val p = a + chord * u
            var bestT = 0f
            var bestContrast = 0f
            var t = -searchPx
            while (t <= searchPx) {
                val inside = p + normal * (t + step)
                val outside = p + normal * (t - step)
                val contrast = intensity(inside.x, inside.y) - intensity(outside.x, outside.y)
                if (contrast > bestContrast) {
                    bestContrast = contrast
                    bestT = t
                }
                t += 1f
            }
            if (bestContrast >= MIN_EDGE_CONTRAST) {
                // A quadratic curve's offset from its chord at u is 4u(1-u) times the offset at the middle.
                estimates += bestT / (4f * u * (1f - u))
            }
        }
        if (estimates.size < 3) return null
        estimates.sort()
        val middle = estimates[estimates.size / 2]
        // Disagreeing samples mean the edge isn't one clean curve (clutter, a hand); leave the edge straight.
        val spread = estimates.last() - estimates.first()
        if (spread > max(6f, abs(middle) * 0.8f + 6f)) return null
        return if (abs(middle) < 2f) Pt(0f, 0f) else normal * middle
    }

    private const val MIN_EDGE_CONTRAST = 18f
}
