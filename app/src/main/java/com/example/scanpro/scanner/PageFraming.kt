package com.example.scanpro.scanner

import org.opencv.core.Mat
import org.opencv.core.Point
import kotlin.math.hypot

/**
 * R58: tells a dark-rimmed object (a laptop's body round its screen, a keyboard's frame round its keys) from a page,
 * which the page score alone cannot: on the desktop benchmark a laptop scores 0.68 and a keyboard 0.63, as high as
 * pages do. Kept apart from [DocumentEdgeDetector] so it runs in JVM unit tests without the Android OpenCV loader.
 */
object PageFraming {
    /** How much darker (grey levels) a side's inside edge must be than both its outside and the middle. */
    const val FRAME_STEP = 30

    private val grayBuffer = ThreadLocal<ByteArray>()

    /**
     * How many sides of the outline [points] (ordered, analysis scale) have an inside edge at least [FRAME_STEP] grey
     * levels darker than both the surface just outside that side and the middle of the outline (R58). A page's edge is
     * its margin, as bright as its middle or brighter than a dark desk, and a shadow darkens the margin and the desk or
     * the middle with it; a laptop's body round its screen, or a keyboard's frame round its keys, is darker than both.
     * Medians of [gray] samples: inside 3 to 10 px in, outside 4 to 12 px out, along the middle 76% of each side, and a
     * 12 by 12 grid over the middle half of the outline. A side whose outside strip is mostly off frame is not framed.
     */
    fun framedSides(points: List<Point>, gray: Mat): Int {
        val width = gray.cols()
        val height = gray.rows()
        var buffer = grayBuffer.get()
        if (buffer == null || buffer.size < width * height) {
            buffer = ByteArray(width * height)
            grayBuffer.set(buffer)
        }
        gray.get(0, 0, buffer)
        fun at(x: Double, y: Double): Int? {
            val xi = x.toInt(); val yi = y.toInt()
            return if (xi < 0 || yi < 0 || xi >= width || yi >= height) null else buffer[yi * width + xi].toInt() and 255
        }
        fun median(values: MutableList<Int>): Int? = if (values.isEmpty()) null else values.sorted()[values.size / 2]
        val cx = points.sumOf { it.x } / 4
        val cy = points.sumOf { it.y } / 4
        val middle = ArrayList<Int>(144)
        for (i in 0 until 12) for (j in 0 until 12) {
            // Bilinear position in the outline shrunk to half about its centre.
            val u = 0.25 + 0.5 * (i + 0.5) / 12
            val v = 0.25 + 0.5 * (j + 0.5) / 12
            val topX = points[0].x + (points[1].x - points[0].x) * u; val topY = points[0].y + (points[1].y - points[0].y) * u
            val botX = points[3].x + (points[2].x - points[3].x) * u; val botY = points[3].y + (points[2].y - points[3].y) * u
            at(topX + (botX - topX) * v, topY + (botY - topY) * v)?.let(middle::add)
        }
        val centre = median(middle) ?: return 0
        var framed = 0
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length < 8.0) continue
            var nx = -(b.y - a.y) / length
            var ny = (b.x - a.x) / length
            // Point the normal inwards, towards the centre.
            if (nx * (cx - (a.x + b.x) / 2) + ny * (cy - (a.y + b.y) / 2) < 0) { nx = -nx; ny = -ny }
            val inside = ArrayList<Int>(250)
            val outside = ArrayList<Int>(280)
            for (k in 0..30) {
                val t = 0.12 + 0.76 * k / 30
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                for (d in 3..10) at(x + nx * d, y + ny * d)?.let(inside::add)
                for (d in 4..12) at(x - nx * d, y - ny * d)?.let(outside::add)
            }
            if (outside.size < 31 * 9 / 2) continue
            val inner = median(inside) ?: continue
            val outer = median(outside) ?: continue
            if (inner + FRAME_STEP <= outer && inner + FRAME_STEP <= centre) framed++
        }
        return framed
    }
}
