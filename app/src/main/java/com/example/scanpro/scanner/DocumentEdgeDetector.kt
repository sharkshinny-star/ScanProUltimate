package com.example.scanpro.scanner

import com.example.scanpro.imaging.OpenCvRuntime
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.example.scanpro.R
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
import org.opencv.core.Scalar
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
    val guidance: Guidance,
    /** Share of the page covered by its largest clipped-white patch (see [PageQuality.glareShare]); 0 without a page. */
    val glareShare: Double = 0.0,
    /**
     * Sides of the outline whose inside edge is clearly darker than both the surface just outside and the middle of
     * the outline (R58): a dark rim round a brighter inside, as a laptop or a keyboard has and a page does not.
     */
    val framedSides: Int = 0
) {
    val isBlurry: Boolean get() = blurScore < PageQuality.BLUR_LIMIT
    val isDark: Boolean get() = brightness < PageQuality.DARK_LIMIT
    val isSkewed: Boolean get() = quad?.let { ScanAlignment.isSkewed(it, frameWidth, frameHeight) } ?: false
    /** Share of the frame the outline covers, 0..1. */
    val areaRatio: Float get() = quad?.let { QuadGeometry.area(it) } ?: 0f
    /** The outline is (nearly) the whole frame: the background, not a page. */
    val fillsFrame: Boolean get() = areaRatio > 0.9f
    /**
     * Steady enough to capture: a confident outline that takes up a real share of the frame (a phone or a
     * card held far away does not), is not just the frame edge, and is sharp and lit.
     */
    val isReady: Boolean get() = isConvincingPage && !isBlurry && !isDark
    /** A dark rim round a brighter inside on most sides: a laptop or a keyboard, not a page (R58). */
    val isFramedObject: Boolean get() = framedSides >= FRAMED_OBJECT_SIDES
    /**
     * The outline is trusted to be a page: confident, a real share of the frame, not just the frame edge, and not a
     * dark-rimmed object such as a laptop or a keyboard (which score as high as pages do).
     */
    val isConvincingPage: Boolean get() = quad != null && confidence >= 0.48f && areaRatio >= MIN_READY_AREA && !fillsFrame && !isFramedObject

    companion object {
        const val MIN_READY_AREA = 0.2f
        /** Three, not four: an object at the frame edge has a side with no surface outside it to compare. */
        const val FRAMED_OBJECT_SIDES = 3
    }
}

/** What the live scanner tells the user to do next; the screen resolves [labelRes] in the current language. */
enum class Guidance(@StringRes val labelRes: Int) {
    POINT_AT_DOCUMENT(R.string.scan_guidance_point_at_document),
    MORE_LIGHT(R.string.scan_guidance_more_light),
    HOLD_STEADY(R.string.scan_guidance_hold_steady),
    MOVE_CLOSER(R.string.scan_guidance_move_closer),
    /** A hint only: glare does not hold back the capture (glossy paper under a ceiling light would never be taken). */
    GLARE(R.string.scan_guidance_glare),
    /** A gentle geometric alignment hint; not a claim about text orientation. */
    ALIGN_PAGE(R.string.scan_guidance_align_page),
    /** Auto-capture only: this is the page just taken (R32), so the shutter waits for the next one. */
    SAME_PAGE(R.string.scan_guidance_same_page),
    READY(R.string.scan_guidance_ready)
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
    /** Canny limits for the faint-edge source, on a widely blurred copy where a 12-level step peaks near 5. */
    private const val FAINT_CANNY_LOW = 2.5
    private const val FAINT_CANNY_HIGH = 7.0
    /** Weight of an outline rebuilt from straight edges ([LineQuadFinder]), before its edge support. */
    private const val LINE_WEIGHT = 0.95
    /** How far a snapped side may slide (px at the analysis scale), the samples per side, and the least mean step. */
    private const val SNAP_RANGE = 5
    private const val SNAP_SAMPLES = 40
    private const val SNAP_MIN_STEP = 6.0
    /**
     * The straight-edge search runs only when the best closed outline scores below this. A page the contour sources
     * already agree on (a plain page scores 0.56 and up on the benchmark) never pays for the search; a page broken by
     * a shadow scores 0.2 to 0.45 there and does.
     */
    private const val LINE_SEARCH_BELOW = 0.5f
    /**
     * F175, colour source: the least gap between the mean colour strengths of the two sides of the Otsu split
     * (chroma map units: chromaticity distance from neutral times [CHROMA_SCALE]) for the split to count as page
     * against surface, and the smallest share of the frame each side must cover. Wood against white paper is about 80,
     * wood against yellow paper about 35; a grey or white desk is near 0.
     */
    private const val CHROMA_MIN_GAP = 24.0
    private const val CHROMA_MIN_SHARE = 0.08
    private const val CHROMA_WEIGHT = 0.96
    internal const val CHROMA_SCALE = 600.0
    internal const val CHROMA_DARK_SUM = 90.0

    /**
     * Colour strength of every pixel: how far its chromaticity (each channel's share of r + g + b) is from neutral, as
     * an 8-bit map the size of [rgba]. A share does not change when the light dims, so a shadow leaves a page's value
     * where it was (CIE a/b, used first, shrank in shadow: lit yellow paper read stronger than wood and shadowed yellow
     * weaker, so no split separated them). Near-black pixels, whose shares are noise, fade towards neutral. Against a
     * coloured surface the page edge stays visible here where the brightness edge is lost (F175). The live camera path
     * makes the same map from the frame's own colour planes ([LiveChroma]).
     */
    fun chromaMap(rgba: Mat): Mat {
        val rgb = Mat(); val f = Mat(); val ch = ArrayList<Mat>(); val sum = Mat(); val rn = Mat(); val gn = Mat()
        val magnitude = Mat(); val damp = Mat()
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            rgb.convertTo(f, CvType.CV_32FC3)
            Core.split(f, ch)
            Core.add(ch[0], ch[1], sum); Core.add(sum, ch[2], sum); Core.add(sum, Scalar(1.0), sum)
            Core.divide(ch[0], sum, rn); Core.subtract(rn, Scalar(1.0 / 3.0), rn)
            Core.divide(ch[1], sum, gn); Core.subtract(gn, Scalar(1.0 / 3.0), gn)
            Core.magnitude(rn, gn, magnitude)
            // Full weight from a channel sum of CHROMA_DARK_SUM up; below it the shares fade out linearly.
            Core.multiply(sum, Scalar(1.0 / CHROMA_DARK_SUM), damp)
            Imgproc.threshold(damp, damp, 1.0, 1.0, Imgproc.THRESH_TRUNC)
            Core.multiply(magnitude, damp, magnitude)
            val out = Mat()
            magnitude.convertTo(out, CvType.CV_8U, CHROMA_SCALE)
            return out
        } finally {
            rgb.release(); f.release(); ch.forEach(Mat::release); sum.release(); rn.release(); gn.release()
            magnitude.release(); damp.release()
        }
    }

    fun detect(grayInput: Mat, chroma: Mat? = null): DocumentDetection {
        require(grayInput.channels() == 1) { "DocumentEdgeDetector expects a grayscale Mat" }
        // Any size with the frame's shape: the map is brought to the working size below (the live camera passes a quarter-size one).
        require(chroma == null || (chroma.channels() == 1 && !chroma.empty())) { "The colour map must be a single-channel Mat" }
        // Any size, but the frame's shape: a map of another shape would be stretched onto the frame and point the colour
        // outlines at the wrong place, so it is left out (brightness alone, as without colour).
        val colourMap = chroma?.takeIf {
            kotlin.math.abs(it.cols().toDouble() / it.rows() - grayInput.cols().toDouble() / grayInput.rows()) <
                2.0 / minOf(it.rows(), it.cols()).coerceAtLeast(1)
        }

        val working = resizeForAnalysis(grayInput)
        val processed = Mat()
        val edgesCanny = Mat()
        val edgesAdaptive = Mat()
        val combinedEdges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val bigKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        // Every per-frame Mat is released in the outer finally, so an exception part-way through a frame
        // (the analyser runs this up to 30 times a second) cannot leak native memory.
        val half = Mat()
        val halfKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val brightMask = Mat()
        val darkMask = Mat()
        val cannyClosed = Mat()
        val smooth = Mat()
        val faintEdges = Mat()
        val faintClosed = Mat()
        val thinEdges = Mat()
        val chromaWork = Mat()
        val chromaHigh = Mat()
        val chromaLow = Mat()

        try {
            Imgproc.GaussianBlur(working, processed, Size(5.0, 5.0), 0.0)

            // Method 1: Dynamic Canny
            val median = Core.mean(processed).`val`[0].coerceIn(20.0, 230.0)
            val lower = max(15.0, median * 0.5)
            val upper = min(255.0, median * 1.4)
            Imgproc.Canny(processed, edgesCanny, lower, upper)

            // Methods 3 and 4 only need the rough region of the page, not pixel-exact edges, so both run on a half-
            // size copy and their maps are scaled back up (nearest, so they stay binary). Measured 2026-11-18: 3.0 ms
            // less per frame, and every benchmark scene's corner error within 0.1 points of the full-size maps.
            val size = working.size()
            Imgproc.resize(processed, half, Size(size.width / 2, size.height / 2), 0.0, 0.0, Imgproc.INTER_AREA)

            // Method 3: paper is usually the big bright (or big dark) region against its surroundings, so
            // a global threshold finds it even where the page edge itself is faint or shadowed.
            Imgproc.threshold(half, brightMask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Core.bitwise_not(brightMask, darkMask)
            Imgproc.morphologyEx(brightMask, brightMask, Imgproc.MORPH_CLOSE, halfKernel)
            Imgproc.morphologyEx(brightMask, brightMask, Imgproc.MORPH_OPEN, halfKernel)
            Imgproc.resize(brightMask, brightMask, size, 0.0, 0.0, Imgproc.INTER_NEAREST)
            Imgproc.resize(darkMask, darkMask, size, 0.0, 0.0, Imgproc.INTER_NEAREST)

            // Cleaner Canny map for the contour search: dilated so a small gap in the page outline closes.
            Imgproc.dilate(edgesCanny, cannyClosed, kernel)
            Imgproc.morphologyEx(cannyClosed, cannyClosed, Imgproc.MORPH_CLOSE, bigKernel)

            // Method 4: faint edges. The Canny limits above scale with the frame's brightness, so a bright page with a
            // faint edge (grey on grey, white on a pale table) sits below them. Here Canny runs on a widely blurred
            // copy with low limits: text, wood grain and noise average out, while the long step between the page
            // margin and the surface behind it stays. Hysteresis keeps a faint stretch of the outline when it joins a
            // strong one (a page with a shadow along two sides), so the outline still closes.
            // On the half-size copy: a 13x13 blur there matches the 25x25 blur here, and the same brightness step
            // climbs in half the pixels, so the gradient doubles and the Canny limits double with it.
            Imgproc.GaussianBlur(half, smooth, Size(13.0, 13.0), 0.0)
            Imgproc.Canny(smooth, faintEdges, FAINT_CANNY_LOW * 2, FAINT_CANNY_HIGH * 2)
            Imgproc.resize(faintEdges, faintEdges, size, 0.0, 0.0, Imgproc.INTER_NEAREST)
            Imgproc.dilate(faintEdges, faintClosed, kernel)
            Imgproc.morphologyEx(faintClosed, faintClosed, Imgproc.MORPH_CLOSE, bigKernel)

            // Brightness is cheap. Sharpness is measured after candidate selection, on the page alone, so a
            // frame with a page pays for one Laplacian; the whole-frame one runs only when there is no page.
            val brightness = Core.mean(working).`val`[0]

            val frameArea = working.cols().toDouble() * working.rows().toDouble()
            val frameWidth = working.cols().toDouble()
            val frameHeight = working.rows().toDouble()
            var best: Candidate? = null

            // Each source finds its own candidates; the best-scoring page outline overall wins. Weights
            // favour the sources that are least likely to trace text or texture instead of the page.
            // The thin Canny map keeps a page separate from a neighbouring object it nearly touches, which
            // the heavily closed maps would fuse into one blob; it is searched with every contour, not just outer ones.
            Imgproc.morphologyEx(edgesCanny, thinEdges, Imgproc.MORPH_CLOSE, kernel)

            /** The shared page score for four corners, or null when they cannot be a page. */
            fun scoreOf(points: Array<Point>, weight: Double): Pair<List<Point>, Double>? {
                val ordered = order(points)
                val geometry = ordered.map { QuadGeometry.P(it.x, it.y) }
                if (!QuadGeometry.isPlausiblePage(geometry)) return null
                val polygon = MatOfPoint(*ordered.toTypedArray())
                try {
                    val polygonArea = abs(Imgproc.contourArea(polygon))
                    val areaRatio = (polygonArea / frameArea).coerceIn(0.0, 1.0)
                    val rect = Imgproc.boundingRect(polygon)
                    val rectangularity = if (rect.area() <= 0) 0.0 else (polygonArea / rect.area()).coerceIn(0.0, 1.0)
                    var score = areaRatio * 0.46 + rectangularity * 0.22 + edgeBalance(ordered) * 0.14 +
                        QuadGeometry.angleScore(geometry) * 0.18
                    // An outline that is just the camera frame is the background, not a page.
                    val margin = 0.012 * max(frameWidth, frameHeight)
                    val onBorder = ordered.count {
                        it.x < margin || it.y < margin || it.x > frameWidth - margin || it.y > frameHeight - margin
                    }
                    if (areaRatio > 0.9 && onBorder >= 3) score *= 0.3
                    return ordered to score * weight
                } finally {
                    polygon.release()
                }
            }
            fun consider(points: Array<Point>, weight: Double, precise: Boolean) {
                val (ordered, score) = scoreOf(points, weight) ?: return
                val current = best
                if (current == null || score.toFloat() > current.score) best = Candidate(ordered, score.toFloat(), precise)
            }

            fun searchSources(sources: List<Source>) = sources.forEach { (map, weight, retrieval, precise) ->
                val contours = mutableListOf<MatOfPoint>()
                val sourceHierarchy = Mat()
                try {
                    // External contours only: a page is the outer outline, and text inside it is noise.
                    Imgproc.findContours(map, contours, sourceHierarchy, retrieval, Imgproc.CHAIN_APPROX_SIMPLE)
                    contours.forEach { contour ->
                        if (abs(Imgproc.contourArea(contour)) < frameArea * MIN_DOCUMENT_AREA_RATIO) return@forEach
                        val (points, solidity) = pageCorners(contour, frameArea) ?: return@forEach
                        // Several objects fused into one blob have a dented outline; a page is nearly solid.
                        consider(points, weight * solidity * solidity * solidity, precise)
                    }
                } finally {
                    contours.forEach(MatOfPoint::release)
                    sourceHierarchy.release()
                }
            }

            searchSources(listOf(
                Source(cannyClosed, 1.0, Imgproc.RETR_EXTERNAL, precise = true),
                Source(thinEdges, 0.98, Imgproc.RETR_LIST, precise = true),
                Source(brightMask, 0.96, Imgproc.RETR_EXTERNAL, precise = false),
                Source(faintClosed, 0.97, Imgproc.RETR_EXTERNAL, precise = false)
            ))

            // The fallback sources only run when the main four found no confident outline, so a plain page never
            // pays for them (F170; on the benchmark no scene's winner comes from them at a score under the gate).
            // Method 2: adaptive threshold for low-contrast edges on light or dark backgrounds, merged with the
            // Canny map; and the dark counterpart of the Otsu mask.
            if ((best?.score ?: 0f) < LINE_SEARCH_BELOW) {
                Imgproc.adaptiveThreshold(
                    processed,
                    edgesAdaptive,
                    255.0,
                    // A box mean, not a Gaussian one: 4.5 ms less per frame, and this map is only a fallback source.
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    15,
                    4.0
                )
                Core.bitwise_or(edgesCanny, edgesAdaptive, combinedEdges)
                Imgproc.morphologyEx(combinedEdges, combinedEdges, Imgproc.MORPH_CLOSE, kernel)
                searchSources(listOf(
                    Source(combinedEdges, 0.85, Imgproc.RETR_EXTERNAL, precise = true),
                    Source(darkMask, 0.7, Imgproc.RETR_EXTERNAL, precise = false)
                ))
            }

            // F175: colour source (only when the caller has colour, i.e. a captured photo). A shadow over most of one
            // side leaves that side of the page about as bright as the wood behind it, so no brightness source can
            // see its edge, but the page stays neutral while the surface is coloured. Split the colour-strength map
            // at its Otsu level and search both halves for the page; the usual page score then prefers the whole
            // page over its lit part. Skipped unless the two halves really differ (a grey or white desk has no colour
            // edge) and each covers a real share of the frame.
            if (colourMap != null) {
                Imgproc.resize(colourMap, chromaWork, working.size(), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.GaussianBlur(chromaWork, chromaWork, Size(7.0, 7.0), 0.0)
                Imgproc.threshold(chromaWork, chromaHigh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                Core.bitwise_not(chromaHigh, chromaLow)
                val highShare = Core.countNonZero(chromaHigh) / frameArea
                if (highShare in CHROMA_MIN_SHARE..(1.0 - CHROMA_MIN_SHARE)) {
                    val gap = Core.mean(chromaWork, chromaHigh).`val`[0] - Core.mean(chromaWork, chromaLow).`val`[0]
                    if (gap >= CHROMA_MIN_GAP) {
                        for (mask in listOf(chromaHigh, chromaLow)) {
                            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
                            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
                        }
                        searchSources(listOf(
                            Source(chromaLow, CHROMA_WEIGHT, Imgproc.RETR_EXTERNAL, precise = false),
                            Source(chromaHigh, CHROMA_WEIGHT, Imgproc.RETR_EXTERNAL, precise = false)
                        ))
                    }
                }
            }

            // F168: an outline broken by a hard shadow never closes, so no contour above traces it, and the best
            // outline is then only the lit part of the page, or nothing. Rebuild the page from its straight sides,
            // weighted by how much of the perimeter really has an edge. Only when no confident outline closed: a
            // clean page keeps exactly the result, and the time, it had before.
            if ((best?.score ?: 0f) < LINE_SEARCH_BELOW) {
                LineQuadFinder.find(edgesCanny, faintEdges, frameArea * MIN_DOCUMENT_AREA_RATIO).forEach { line ->
                    // Both line passes end with corners from full-resolution line fits, but a pass2 line can sit on
                    // the blurred map's thick edge, so its corners get the same snap as the coarse sources.
                    consider(line.points, LINE_WEIGHT * line.support, precise = false)
                }
            }

            // F170: a winner from a half-size source is up to 2 px off at the analysis scale (about 1% corner error
            // on an A4 benchmark page). Each side is slid a few pixels along its normal onto the strongest brightness
            // step of the full-resolution blurred frame; a side with no clear step nearby stays where it was.
            best?.let { chosen ->
                if (!chosen.precise) {
                    val snapped = snapToGradient(chosen.points, processed)
                    if (snapped != null) best = chosen.copy(points = snapped)
                }
            }

            val candidate = best
            val confidence = candidate?.score ?: 0f
            val quad = candidate?.let { toNormalizedQuad(it.points, working.cols(), working.rows()) }
            // One native contour and bounding box per frame, shared by sharpness, exposure and glare.
            // The prior implementation allocated the contour twice on every detector pass.
            val pageBox = candidate?.let { selected ->
                runCatching {
                    val outline = MatOfPoint(*selected.points.toTypedArray())
                    try { Imgproc.boundingRect(outline) } finally { outline.release() }
                }.getOrNull()
            }
            // A dark desk surrounding a correctly exposed page should not show "More light" or block capture.
            val pageBrightness = pageBox?.let { box ->
                val x = box.x.coerceIn(0, working.cols() - 1)
                val y = box.y.coerceIn(0, working.rows() - 1)
                val region = working.submat(org.opencv.core.Rect(
                    x, y, box.width.coerceIn(1, working.cols() - x), box.height.coerceIn(1, working.rows() - y)
                ))
                try { Core.mean(region).`val`[0] } finally { region.release() }
            } ?: brightness
            // Sharpness is judged on the page itself: a blurry background around a sharp page, or the other
            // way round, must not decide whether the capture is taken.
            val blur = pageBox?.let { box ->
                runCatching {
                    val x = box.x.coerceIn(0, working.cols() - 1)
                    val y = box.y.coerceIn(0, working.rows() - 1)
                    varianceOfLaplacian(processed, org.opencv.core.Rect(
                        x, y, box.width.coerceIn(1, working.cols() - x), box.height.coerceIn(1, working.rows() - y)
                    ))
                }.getOrNull()
            } ?: varianceOfLaplacian(processed)
            // Mask the page polygon, not its bounding box: a white table outside a skewed page is not glare.
            val glare = if (candidate == null || pageBox == null) 0.0 else runCatching {
                val box = pageBox
                val x = box.x.coerceIn(0, working.cols() - 1)
                val y = box.y.coerceIn(0, working.rows() - 1)
                PageQuality.glareShare(
                    working,
                    org.opencv.core.Rect(x, y, box.width.coerceIn(1, working.cols() - x), box.height.coerceIn(1, working.rows() - y)),
                    candidate.points.toList()
                )
            }.getOrDefault(0.0)
            val framed = if (candidate == null) 0 else runCatching { PageFraming.framedSides(candidate.points, processed) }.getOrDefault(0)
            val guidance = when {
                // Darkness first: a dark frame hides the outline, so "point at a document" would mislead.
                pageBrightness < PageQuality.DARK_LIMIT -> Guidance.MORE_LIGHT
                quad == null -> Guidance.POINT_AT_DOCUMENT
                // R58: a laptop or a keyboard outlined as if it were a page.
                framed >= DocumentDetection.FRAMED_OBJECT_SIDES -> Guidance.POINT_AT_DOCUMENT
                blur < PageQuality.BLUR_LIMIT -> Guidance.HOLD_STEADY
                confidence < 0.48f -> Guidance.MOVE_CLOSER
                PageQuality.isGlare(glare) -> Guidance.GLARE
                ScanAlignment.isSkewed(quad, working.cols(), working.rows()) -> Guidance.ALIGN_PAGE
                else -> Guidance.READY
            }

            return DocumentDetection(
                quad = quad,
                confidence = confidence,
                blurScore = blur,
                brightness = pageBrightness,
                frameWidth = working.cols(),
                frameHeight = working.rows(),
                guidance = guidance,
                glareShare = glare,
                framedSides = framed
            )
        } finally {
            if (working !== grayInput) working.release()
            processed.release()
            edgesCanny.release()
            edgesAdaptive.release()
            combinedEdges.release()
            kernel.release()
            chromaWork.release()
            chromaHigh.release()
            chromaLow.release()
            bigKernel.release()
            brightMask.release()
            darkMask.release()
            cannyClosed.release()
            smooth.release()
            faintEdges.release()
            faintClosed.release()
            thinEdges.release()
            half.release()
            halfKernel.release()
        }
    }

    /** Variance of the Laplacian (higher is sharper) over [source], or only over [bounds] when given. */
    private fun varianceOfLaplacian(source: Mat, bounds: org.opencv.core.Rect? = null): Double {
        val region = if (bounds == null) source else source.submat(bounds)
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        try {
            Imgproc.Laplacian(region, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stdDev)
            return stdDev.toArray().firstOrNull()?.let { it * it } ?: 0.0
        } finally {
            if (region !== source) region.release()
            laplacian.release()
            mean.release()
            stdDev.release()
        }
    }

    fun detect(bitmap: Bitmap): DocumentDetection {
        val rgba = Mat()
        val gray = Mat()
        var chroma: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            // A captured photo has colour, which finds a page edge a shadow has hidden from the brightness (F175).
            chroma = runCatching { chromaMap(rgba) }.getOrNull()
            return detect(gray, chroma)
        } finally {
            rgba.release()
            gray.release()
            chroma?.release()
        }
    }

    /**
     * Four corners for a contour, or null if it does not look like a page. Works on the convex hull, so a
     * broken or notched outline (a thumb over the edge, a fold) still gives a clean quad.
     */
    private fun pageCorners(contour: MatOfPoint, frameArea: Double): Pair<Array<Point>, Double>? {
        val hullIndices = org.opencv.core.MatOfInt()
        val hull = try {
            Imgproc.convexHull(contour, hullIndices)
            val all = contour.toArray()
            hullIndices.toArray().map { all[it] }.toTypedArray()
        } finally {
            hullIndices.release()
        }
        if (hull.size < 4) return null

        val hull2f = MatOfPoint2f(*hull)
        try {
            val hullArea = abs(Imgproc.contourArea(hull2f))
            val solidity = if (hullArea <= 0) 0.0 else (abs(Imgproc.contourArea(contour)) / hullArea).coerceIn(0.0, 1.0)
            if (solidity < 0.8) return null
            val perimeter = Imgproc.arcLength(hull2f, true)
            for (ratio in listOf(0.015, 0.02, 0.028, 0.035, 0.045, 0.06)) {
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(hull2f, approx, perimeter * ratio, true)
                    val pts = approx.toArray()
                    if (pts.size == 4) return pts to solidity
                } finally {
                    approx.release()
                }
            }
            // No clean four-sided fit: use the tightest rectangle, but only if the outline really fills it.
            val minRect: RotatedRect = Imgproc.minAreaRect(hull2f)
            val boxArea = minRect.size.width * minRect.size.height
            if (boxArea < frameArea * MIN_DOCUMENT_AREA_RATIO || hullArea / boxArea < 0.82) return null
            val boxPts = Array(4) { Point() }
            minRect.points(boxPts)
            return boxPts to solidity
        } finally {
            hull2f.release()
        }
    }

    private fun resizeForAnalysis(source: Mat): Mat {
        val longSide = max(source.cols(), source.rows()).toDouble()
        if (longSide <= ANALYSIS_LONG_SIDE) return source
        val scale = ANALYSIS_LONG_SIDE / longSide
        val output = Mat()
        // Measured on the benchmark (2026-11-16): area averaging at a fractional scale took 14.5 ms of a 37 ms
        // detection for a 1280 px frame; bilinear takes 0.3 ms. Down to half size bilinear still reads every source
        // pixel's neighbourhood, and the 5x5 blur that follows removes what little aliasing is left. Below half size
        // it would skip pixels, so larger inputs keep area averaging.
        val method = if (scale >= 0.5) Imgproc.INTER_LINEAR else Imgproc.INTER_AREA
        Imgproc.resize(source, output, Size(source.cols() * scale, source.rows() * scale), 0.0, 0.0, method)
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

    private data class Source(val map: Mat, val weight: Double, val retrieval: Int, val precise: Boolean)

    /** A reused full-frame buffer for [snapToGradient]; the detector can run on more than one thread. */
    private val grayBuffer = ThreadLocal<ByteArray>()

    /**
     * The four corners with each side slid (at most [SNAP_RANGE] px) along its normal onto the strongest brightness
     * step of [gray] near it, or null when no side moved. A side moves only to a clear step (mean central difference
     * of at least [SNAP_MIN_STEP] grey levels) that beats the step where the side already is; the snapped quad is
     * kept only if it is still a plausible page and no corner moved further than twice the range.
     */
    private fun snapToGradient(points: List<Point>, gray: Mat): List<Point>? {
        val width = gray.cols()
        val height = gray.rows()
        var buffer = grayBuffer.get()
        if (buffer == null || buffer.size < width * height) {
            buffer = ByteArray(width * height)
            grayBuffer.set(buffer)
        }
        gray.get(0, 0, buffer)

        /** Mean |I(p+n) - I(p-n)| along the side from [ax],[ay] to [bx],[by], or null off frame. */
        fun ridge(ax: Double, ay: Double, bx: Double, by: Double, nx: Double, ny: Double): Double? {
            var sum = 0.0
            var inside = 0
            for (k in 0..SNAP_SAMPLES) {
                val t = 0.06 + 0.88 * k / SNAP_SAMPLES
                val x = ax + (bx - ax) * t
                val y = ay + (by - ay) * t
                val x0 = (x - nx * 1.5).toInt()
                val y0 = (y - ny * 1.5).toInt()
                val x1 = (x + nx * 1.5).toInt()
                val y1 = (y + ny * 1.5).toInt()
                if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0 || x0 >= width || y0 >= height || x1 >= width || y1 >= height) continue
                inside++
                sum += abs((buffer[y1 * width + x1].toInt() and 255) - (buffer[y0 * width + x0].toInt() and 255))
            }
            return if (inside < SNAP_SAMPLES / 2) null else sum / inside
        }

        var moved = false
        val shifted = ArrayList<DoubleArray>(4)
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length < 8.0) return null
            val nx = -(b.y - a.y) / length
            val ny = (b.x - a.x) / length
            var bestShift = 0
            var bestRidge = ridge(a.x, a.y, b.x, b.y, nx, ny) ?: return null
            val atRest = bestRidge
            for (shift in -SNAP_RANGE..SNAP_RANGE) {
                if (shift == 0) continue
                val r = ridge(a.x + nx * shift, a.y + ny * shift, b.x + nx * shift, b.y + ny * shift, nx, ny) ?: continue
                if (r > bestRidge) {
                    bestRidge = r
                    bestShift = shift
                }
            }
            // Move only onto a clear step that beats where the side already is; drift onto noise helps nothing.
            val keep = bestShift != 0 && bestRidge >= SNAP_MIN_STEP && bestRidge >= atRest * 1.15
            val use = if (keep) bestShift.toDouble() else 0.0
            if (keep) moved = true
            shifted += doubleArrayOf(a.x + nx * use, a.y + ny * use, b.x + nx * use, b.y + ny * use)
        }
        if (!moved) return null

        // The corners are where neighbouring shifted sides cross, in the same order as before.
        val result = ArrayList<Point>(4)
        for (i in 0 until 4) {
            val s1 = shifted[(i + 3) % 4]
            val s2 = shifted[i]
            val d1x = s1[2] - s1[0]; val d1y = s1[3] - s1[1]
            val d2x = s2[2] - s2[0]; val d2y = s2[3] - s2[1]
            val det = d1x * d2y - d1y * d2x
            if (abs(det) < 1e-9) return null
            val t = ((s2[0] - s1[0]) * d2y - (s2[1] - s1[1]) * d2x) / det
            val x = s1[0] + d1x * t
            val y = s1[1] + d1y * t
            if (!x.isFinite() || !y.isFinite() || hypot(x - points[i].x, y - points[i].y) > SNAP_RANGE * 2.0) return null
            result += Point(x, y)
        }
        return if (QuadGeometry.isPlausiblePage(result.map { QuadGeometry.P(it.x, it.y) })) result else null
    }

    /** [precise] when the points came from a full-resolution edge map; a half-size source is 2 px coarse. */
    private data class Candidate(val points: List<Point>, val score: Float, val precise: Boolean)
}

