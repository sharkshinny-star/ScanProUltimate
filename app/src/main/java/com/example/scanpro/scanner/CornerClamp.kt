package com.example.scanpro.scanner

/**
 * Brings the corners of a page outline that runs past the picture's edge back inside it.
 *
 * A corner that is off the frame is slid along the side it ends (towards a neighbouring corner) to the point where
 * that side enters the frame, taking whichever of its two sides moves it less. Clamping the x and y of such a
 * corner separately instead pushed it along the border and off the lines of both its sides, so a page cut off by the
 * frame edge was outlined with a skewed edge and warped crooked. A corner with no side entering the frame (both
 * sides run outside it) falls back to the nearest point of the frame, which is then the page's frame-edge side.
 */
internal object CornerClamp {
    /** [corners] in order around the page as (x, y) pairs in pixels; the same four corners with every one inside 0..[width] x 0..[height]. */
    fun intoFrame(corners: List<Pair<Double, Double>>, width: Double, height: Double): List<Pair<Double, Double>> {
        fun inside(p: Pair<Double, Double>) = p.first in 0.0..width && p.second in 0.0..height
        return corners.mapIndexed { index, corner ->
            if (inside(corner)) return@mapIndexed corner
            val neighbours = listOf(corners[(index + 3) % corners.size], corners[(index + 1) % corners.size])
            val slid = neighbours.mapNotNull { toward -> entry(corner, toward, width, height) }
                .minByOrNull { (slidTo, _) -> distance(corner, slidTo) }
            slid?.first ?: (corner.first.coerceIn(0.0, width) to corner.second.coerceIn(0.0, height))
        }
    }

    /** Where the segment from [from] (outside the frame) towards [to] first enters the frame, or null if it never does. */
    private fun entry(from: Pair<Double, Double>, to: Pair<Double, Double>, width: Double, height: Double): Pair<Pair<Double, Double>, Double>? {
        var enter = 0.0
        var leave = 1.0
        fun axis(start: Double, end: Double, limit: Double): Boolean {
            val delta = end - start
            if (delta == 0.0) return start in 0.0..limit
            val a = (0.0 - start) / delta
            val b = (limit - start) / delta
            enter = maxOf(enter, minOf(a, b))
            leave = minOf(leave, maxOf(a, b))
            return true
        }
        if (!axis(from.first, to.first, width) || !axis(from.second, to.second, height)) return null
        if (enter > leave) return null
        val x = (from.first + enter * (to.first - from.first)).coerceIn(0.0, width)
        val y = (from.second + enter * (to.second - from.second)).coerceIn(0.0, height)
        return (x to y) to enter
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>) = kotlin.math.hypot(a.first - b.first, a.second - b.second)
}
