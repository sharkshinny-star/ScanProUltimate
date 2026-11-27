package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Corner-angle checks for page candidates. Plain math, so it can be unit tested without OpenCV. */
internal object QuadGeometry {
    data class P(val x: Double, val y: Double)

    /** A page seen at an angle still has corners well away from a diamond or a sliver. */
    const val MIN_CORNER_DEGREES = 55.0
    const val MAX_CORNER_DEGREES = 125.0

    /** Interior angle in degrees at each vertex of a quad given in order around its outline. */
    fun interiorAngles(points: List<P>): List<Double> = points.indices.map { i ->
        val prev = points[(i + points.size - 1) % points.size]
        val cur = points[i]
        val next = points[(i + 1) % points.size]
        val ax = prev.x - cur.x
        val ay = prev.y - cur.y
        val bx = next.x - cur.x
        val by = next.y - cur.y
        val lengths = hypot(ax, ay) * hypot(bx, by)
        if (lengths == 0.0) 0.0 else Math.toDegrees(acos(((ax * bx + ay * by) / lengths).coerceIn(-1.0, 1.0)))
    }

    fun isPlausiblePage(points: List<P>): Boolean =
        interiorAngles(points).all { it in MIN_CORNER_DEGREES..MAX_CORNER_DEGREES }

    /** 1.0 when every corner is square, falling to 0.0 when the worst corner is 45° or more off. */
    fun angleScore(points: List<P>): Double {
        val worst = interiorAngles(points).maxOf { abs(it - 90.0) }
        return (1.0 - worst / 45.0).coerceIn(0.0, 1.0)
    }

    /** Area of the outline as a share of the (normalised) frame, by the shoelace formula. */
    fun area(quad: CropQuad): Float {
        val p = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var sum = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return (abs(sum) / 2.0).toFloat().coerceIn(0f, 1f)
    }
}
