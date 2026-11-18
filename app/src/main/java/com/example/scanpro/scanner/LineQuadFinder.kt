package com.example.scanpro.scanner

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Page outlines rebuilt from four straight edge lines, for when the outline does not close (F168).
 *
 * A hard shadow across a page (a hand, a phone) can darken the page to the brightness of the desk, so the outline
 * vanishes where the shadow meets the page edge. The contour search then only sees the lit part of the page, bounded
 * by the shadow, or nothing at all. The lit stretches of each page side are still straight and collinear, so here
 * the long straight runs of the edge map are found, joined into lines, and every two near-parallel pairs
 * are tried as a page.
 *
 * Lines are read from a thin Canny map; if that gives no acceptable quad, they are read again from a faint-edge map
 * (Canny of a widely blurred copy), where the long page/desk step survives a shadow that defeats plain Canny. The two
 * maps are never mixed in one search: the faint map's long desk-grain lines would crowd out the page's own lines.
 * Side support is always judged against the union of both maps, so a side whose edge is too weak for the Canny map
 * but present in the blurred one still counts. The corner test below stays on the single map the lines came from: the
 * blurred map's outline is thick, and around a corner it would read as the side running on.
 *
 * A quad is kept only when the edge maps really run along its sides (most of the perimeter, and some of every side)
 * and stops at its corners. That last test is what separates a page from the lines of a table edge, a patterned mat
 * or wood grain: those run on past the point where two of them cross, a page's sides do not.
 */
internal object LineQuadFinder {

    /** Four corners in no particular order, and the share of the perimeter (0..1) that has an edge along it. */
    class Candidate(val points: Array<Point>, val support: Double)

    private const val MAX_LINES = 24
    private const val MAX_PER_ORIENTATION = 10
    private const val MAX_SEGMENTS = 400
    /** Overall share of the perimeter that must have an edge, and the least any one side may have. */
    private const val MIN_SUPPORT = 0.5
    private const val MIN_SIDE_SUPPORT = 0.15
    /** Past a corner, a page side must not go on: at most this share of the stretch beyond it may be edge. */
    private const val MAX_OVERSHOOT = 0.35
    /**
     * At most this many opposite-side pairs are combined per pass. Pairs are kept longest lines first, and a page's
     * sides are long, so a page is never cut off; the cap only trims a frame full of grid lines (a patterned mat),
     * whose combination count would otherwise grow with the fourth power of the line count. Measured on an empty
     * patterned-mat frame (the worst case: grid lines in both directions, every quad rejected): the cap took the
     * search from 15.0 ms to 10.6 ms without changing any benchmark scene's result.
     */
    private const val MAX_PAIRS = 36

    private class Line(val nx: Double, val ny: Double, val rho: Double, val length: Double) {
        val angle: Double get() = normalise(atan2(ny, nx))
    }

    /**
     * Candidate page quads, largest support first. [edges] is a thin Canny map; [faint] an optional faint-edge map of
     * the same size whose edges also count. [minArea] is the smallest page area in pixels of those maps.
     */
    fun find(edges: Mat, faint: Mat?, minArea: Double): List<Candidate> {
        val width = edges.cols()
        val height = edges.rows()
        if (width < 32 || height < 32) return emptyList()
        if (faint != null && (faint.cols() != width || faint.rows() != height)) return emptyList()

        // A one-pixel tolerance around the union of the edge maps, read once into an array for the many samples below.
        val union = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
        val pixels = ByteArray(width * height)
        try {
            if (faint == null) edges.copyTo(union) else Core.bitwise_or(edges, faint, union)
            Imgproc.dilate(union, union, kernel)
            union.get(0, 0, pixels)
        } finally {
            union.release()
            kernel.release()
        }
        val support = EdgeLookup(pixels, width, height)

        fun pass(primary: Mat): List<Candidate> {
            // Lines first: on a frame with no page (a bare desk), they run in one direction at most (wood grain, a
            // table edge), no quad can form, and the pass ends before the lookup map below is paid for.
            val lines = longLines(primary, min(width, height).toDouble())
            if (!crossingOrientations(lines)) return emptyList()
            val own = Mat()
            val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
            val ownPixels = ByteArray(width * height)
            try {
                Imgproc.dilate(primary, own, k)
                own.get(0, 0, ownPixels)
            } finally {
                own.release()
                k.release()
            }
            return quads(lines, support, EdgeLookup(ownPixels, width, height), minArea, width, height)
        }
        val fromCanny = pass(edges)
        if (fromCanny.isNotEmpty() || faint == null) return fromCanny
        return pass(faint)
    }

    /** Whether two of the lines cross at a page-corner-like angle; without that no quad can form. */
    private fun crossingOrientations(lines: List<Line>): Boolean {
        for (i in lines.indices) for (j in i + 1 until lines.size) {
            if (angleBetween(lines[i].angle, lines[j].angle) >= PI * 50 / 180) return true
        }
        return false
    }

    /**
     * Every acceptable quad the given lines form, largest support first. Sides are supported by [map] (the union of
     * the edge maps); the corner overshoot test reads [own], only the map the lines were found in.
     */
    private fun quads(lines: List<Line>, map: EdgeLookup, own: EdgeLookup, minArea: Double, width: Int, height: Int): List<Candidate> {
        if (lines.size < 4) return emptyList()
        val shortSide = min(width, height).toDouble()

        // Opposite sides: near-parallel (perspective tilts them by up to ~30 degrees) and well apart.
        val pairs = ArrayList<Pair<Line, Line>>()
        for (i in lines.indices) for (j in i + 1 until lines.size) {
            val a = lines[i]
            val b = lines[j]
            if (angleBetween(a.angle, b.angle) > PI / 6) continue
            if (separation(a, b, width, height) < shortSide * 0.15) continue
            pairs += a to b
        }
        pairs.sortByDescending { it.first.length + it.second.length }
        if (pairs.size > MAX_PAIRS) pairs.subList(MAX_PAIRS, pairs.size).clear()
        val results = ArrayList<Candidate>()
        for (i in pairs.indices) for (j in i + 1 until pairs.size) {
            val (a1, a2) = pairs[i]
            val (b1, b2) = pairs[j]
            // The two pairs must cross at a real angle, as a page's neighbouring sides do.
            if (angleBetween(meanAngle(a1, a2), meanAngle(b1, b2)) < PI * 50 / 180) continue
            val corners = arrayOf(
                intersect(a1, b1) ?: continue,
                intersect(a1, b2) ?: continue,
                intersect(a2, b2) ?: continue,
                intersect(a2, b1) ?: continue
            )
            val slack = shortSide * 0.02
            if (corners.any { it.x < -slack || it.y < -slack || it.x > width - 1 + slack || it.y > height - 1 + slack }) continue
            val ring = ringOrder(corners)
            if (polygonArea(ring) < minArea) continue
            val support = support(ring, map, own) ?: continue
            results += Candidate(ring, support)
        }
        return results.sortedByDescending { it.support }
    }

    /** Long straight edges, merged so each page side is one line however many pieces the shadow broke it into. */
    private fun longLines(edges: Mat, shortSide: Double): List<Line> {
        // Straight runs of the edge map's own curves. Unlike a Hough transform, letters are separate small curves
        // here, so a line of text never adds up to one long segment; page sides, rules and desk edges do. A grain or
        // text junction can chop the page edge into short runs, so the floor is low; the support test does the vetting.
        val minLength = shortSide * 0.045
        val segments = ArrayList<DoubleArray>()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.forEach { contour ->
                val box = Imgproc.boundingRect(contour)
                if (max(box.width, box.height) < minLength) return@forEach
                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(curve, approx, 2.0, false)
                    val pts = approx.toArray()
                    for (k in 0 until pts.size - 1) {
                        val p = pts[k]
                        val q = pts[k + 1]
                        if (hypot(q.x - p.x, q.y - p.y) >= minLength) segments += doubleArrayOf(p.x, p.y, q.x, q.y)
                    }
                } finally {
                    curve.release()
                    approx.release()
                }
            }
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
        }
        // Longest first, so each line is seeded by its best piece; a frame full of clutter is capped.
        segments.sortByDescending { hypot(it[2] - it[0], it[3] - it[1]) }
        if (segments.size > MAX_SEGMENTS) segments.subList(MAX_SEGMENTS, segments.size).clear()

        class Cluster(seed: DoubleArray) {
            val angle = segmentAngle(seed)
            val nx = cos(angle + PI / 2)
            val ny = sin(angle + PI / 2)
            val rho = nx * seed[0] + ny * seed[1]
            val members = ArrayList<DoubleArray>().apply { add(seed) }
            var length = hypot(seed[2] - seed[0], seed[3] - seed[1])
        }
        val clusters = ArrayList<Cluster>()
        val rhoTolerance = max(4.0, shortSide * 0.008)
        segments.forEach { s ->
            val angle = segmentAngle(s)
            val home = clusters.firstOrNull { c ->
                angleBetween(c.angle, angle) < PI / 60 &&
                    abs(c.nx * s[0] + c.ny * s[1] - c.rho) < rhoTolerance &&
                    abs(c.nx * s[2] + c.ny * s[3] - c.rho) < rhoTolerance
            }
            if (home == null) clusters += Cluster(s) else {
                home.members += s
                home.length += hypot(s[2] - s[0], s[3] - s[1])
            }
        }
        // Text rows are long straight lines too, and all run one way. Keep at most a few lines of any one
        // orientation, so the page's sides are not crowded out by the lines of text between them.
        val kept = ArrayList<Cluster>()
        clusters.sortedByDescending { it.length }.forEach { c ->
            if (kept.size < MAX_LINES && kept.count { angleBetween(it.angle, c.angle) < PI / 9 } < MAX_PER_ORIENTATION) kept += c
        }
        return kept.mapNotNull { c -> fit(c.members, c.length) }
    }

    /** A least-squares line through points sampled along every member segment, so long pieces weigh more. */
    private fun fit(members: List<DoubleArray>, length: Double): Line? {
        val points = ArrayList<Point>()
        members.forEach { s ->
            val n = max(2, (hypot(s[2] - s[0], s[3] - s[1]) / 4).toInt())
            for (k in 0..n) {
                val t = k.toDouble() / n
                points += Point(s[0] + (s[2] - s[0]) * t, s[1] + (s[3] - s[1]) * t)
            }
        }
        val input = MatOfPoint2f(*points.toTypedArray())
        val out = Mat()
        try {
            Imgproc.fitLine(input, out, Imgproc.DIST_L2, 0.0, 0.01, 0.01)
            val v = DoubleArray(4)
            for (i in 0 until 4) v[i] = out.get(i, 0)[0]
            val norm = hypot(v[0], v[1])
            if (norm < 1e-9) return null
            val nx = -v[1] / norm
            val ny = v[0] / norm
            return Line(nx, ny, nx * v[2] + ny * v[3], length)
        } finally {
            input.release()
            out.release()
        }
    }

    /**
     * Share of the perimeter with an edge along it, or null when a side has too little, or a side runs on past a
     * corner (a line of the desk or a mat, not the edge of a page).
     */
    private fun support(ring: Array<Point>, map: EdgeLookup, own: EdgeLookup): Double? {
        var hits = 0.0
        var total = 0.0
        for (i in 0 until 4) {
            val a = ring[i]
            val b = ring[(i + 1) % 4]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length < 8.0) return null
            val ux = (b.x - a.x) / length
            val uy = (b.y - a.y) / length
            val side = map.share(a.x + ux * length * 0.03, a.y + uy * length * 0.03, ux, uy, length * 0.94) ?: return null
            if (side < MIN_SIDE_SUPPORT) return null
            // Beyond each corner: start a few pixels out so the neighbouring side's own edge is not counted.
            val reach = max(10.0, length * 0.1)
            val past = own.share(b.x + ux * 4, b.y + uy * 4, ux, uy, reach)
            val before = own.share(a.x - ux * 4, a.y - uy * 4, -ux, -uy, reach)
            if ((past ?: 0.0) > MAX_OVERSHOOT || (before ?: 0.0) > MAX_OVERSHOOT) return null
            hits += side * length
            total += length
        }
        val overall = if (total <= 0) 0.0 else hits / total
        return if (overall >= MIN_SUPPORT) overall else null
    }

    private class EdgeLookup(val pixels: ByteArray, val width: Int, val height: Int) {
        /** Share of in-frame samples, one every two pixels along a ray, that land on an edge; null if none are in frame. */
        fun share(x0: Double, y0: Double, ux: Double, uy: Double, length: Double): Double? {
            val steps = max(1, (length / 2).toInt())
            var inside = 0
            var hit = 0
            for (k in 0..steps) {
                val d = length * k / steps
                val x = (x0 + ux * d).toInt()
                val y = (y0 + uy * d).toInt()
                if (x < 0 || y < 0 || x >= width || y >= height) continue
                inside++
                if (pixels[y * width + x].toInt() != 0) hit++
            }
            return if (inside == 0) null else hit.toDouble() / inside
        }
    }

    private fun segmentAngle(s: DoubleArray) = normalise(atan2(s[3] - s[1], s[2] - s[0]))

    /** Folds an angle into [0, PI): a line has no direction. */
    private fun normalise(angle: Double): Double {
        var a = angle % PI
        if (a < 0) a += PI
        return a
    }

    private fun angleBetween(a: Double, b: Double): Double {
        val d = abs(a - b) % PI
        return min(d, PI - d)
    }

    private fun meanAngle(a: Line, b: Line): Double {
        // Average on the doubled angle, so 1 degree and 179 degrees average to 0, not 90.
        val x = cos(2 * a.angle) + cos(2 * b.angle)
        val y = sin(2 * a.angle) + sin(2 * b.angle)
        return normalise(atan2(y, x) / 2)
    }

    /** How far apart two near-parallel lines are, measured at the centre of the frame. */
    private fun separation(a: Line, b: Line, width: Int, height: Int): Double {
        val cx = width / 2.0
        val cy = height / 2.0
        // The foot of the centre on line a, then its distance to line b.
        val d = a.nx * cx + a.ny * cy - a.rho
        val px = cx - a.nx * d
        val py = cy - a.ny * d
        return abs(b.nx * px + b.ny * py - b.rho)
    }

    private fun intersect(a: Line, b: Line): Point? {
        val det = a.nx * b.ny - a.ny * b.nx
        if (abs(det) < 1e-6) return null
        val x = (a.rho * b.ny - b.rho * a.ny) / det
        val y = (a.nx * b.rho - b.nx * a.rho) / det
        return if (x.isFinite() && y.isFinite()) Point(x, y) else null
    }

    /** The corners in order round the centre, so consecutive points share a side. */
    private fun ringOrder(points: Array<Point>): Array<Point> {
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        return points.sortedBy { atan2(it.y - cy, it.x - cx) }.toTypedArray()
    }

    private fun polygonArea(ring: Array<Point>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }
}
