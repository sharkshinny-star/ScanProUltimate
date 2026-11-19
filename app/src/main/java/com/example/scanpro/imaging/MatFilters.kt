package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * The page "look" (clean-up, filters, tonal adjustments) on 3-channel RGB OpenCV images. It uses no Android
 * classes, so the unit tests run it on a desktop JVM against the real OpenCV (see MatFiltersTest).
 *
 * Every function takes ownership of its input: it either returns it (nothing to do, or edited in place) or
 * releases it once the result replaces it. Pass a clone to keep the original.
 */
internal object MatFilters {
    private const val SHADOW_ESTIMATE_SIZE = 600.0
    private const val SHADOW_SIGMA = 30.0
    private const val PAPER_MAX_SATURATION = 59.0
    private const val FINGER_ESTIMATE_SIZE = 800.0
    /** Black and white: with the lighting divided out (paper at 255), anything darker than this is solid ink. */
    private const val SOLID_INK_LEVEL = 128.0
    /** A tint on the brightest tones is taken for the light's colour between these saturations (0..255). */
    private const val CAST_MIN_SATURATION = 12.0
    private const val CAST_MAX_SATURATION = 80.0

    /** True when applying this look would change any pixel. */
    fun changesPixels(filter: PageFilter, adjustments: PageAdjustments): Boolean =
        adjustments.removeFingers || adjustments.removeShadows || filter != PageFilter.ORIGINAL || !adjustments.isTonallyNeutral()

    /** Finger and shadow removal, when switched on. */
    fun cleanUp(rgb: Mat, adjustments: PageAdjustments): Mat {
        var page = rgb
        if (adjustments.removeFingers) page = page.then { removeFingers(it) }
        if (adjustments.removeShadows) page = page.then { removeShadows(it) }
        return page
    }

    /** The named filter followed by the user's tonal adjustments. */
    fun style(rgb: Mat, filter: PageFilter, adjustments: PageAdjustments): Mat {
        var page = rgb.then { applyNamedFilter(it, filter) }
        if (!adjustments.isTonallyNeutral()) page = page.then { applyUserAdjustments(it, adjustments.normalized()) }
        return page
    }

    /** The whole look, in the order the page pipeline applies it. */
    fun look(rgb: Mat, filter: PageFilter, adjustments: PageAdjustments): Mat =
        style(cleanUp(rgb, adjustments), filter, adjustments)

    /** Applies [filter] to a 3-channel RGB image; returns [source] itself for the original look. */
    internal fun applyNamedFilter(source: Mat, filter: PageFilter): Mat = when (filter) {
        PageFilter.ORIGINAL -> source
        PageFilter.AUTO -> smartAuto(source)
        PageFilter.COLOR -> colourDocument(source)
        PageFilter.GRAY -> greyDocument(source)
        PageFilter.BW -> blackWhiteDocument(source)
        PageFilter.WHITEBOARD -> boardBase(source).then { whiteboard(it) }
    }

    internal fun applyUserAdjustments(source: Mat, adjustments: PageAdjustments): Mat {
        val a = adjustments.normalized()
        return colorMatrix(source, saturation = a.saturation, contrast = a.contrast, brightness = a.brightness * 92f)
    }

    /**
     * Auto Clean: looks at the picture first. A page of paper gets its shadows and colour cast removed (paper turns
     * white), its ink deepened and a gentle sharpen. Anything else (a whiteboard shot from afar, a photo, an object)
     * is not flattened, which washed such pictures out: it gets white balance, automatic levels, a lift if it is
     * underexposed and a light sharpen, like a camera app's auto-enhance.
     */
    internal fun smartAuto(rgb: Mat): Mat {
        val paper = paperStats(rgb)
        return if (paper.fraction >= DOCUMENT_PAPER_FRACTION) autoDocument(rgb, paper.gains) else autoPhoto(rgb)
    }

    /** Share of the picture that looks like paper: low saturation and close to the brightest tones in it. */
    internal fun paperFraction(rgb: Mat): Double = paperStats(rgb).fraction

    /** [fraction] of the picture that is paper, and the channel [gains] that take the light's tint off it (null: none). */
    private class PaperStats(val fraction: Double, val gains: DoubleArray?)

    /**
     * Paper is judged after the light's tint is taken off (2026-11-18). A white sheet under a warm lamp photographs as
     * cream, with a saturation just above the paper limit, so it used to count as "not a page": Auto Clean and Colour
     * then gave it the photo treatment and left it yellow and unevenly lit. The tint is the colour of the brightest
     * tones; it is only believed up to [CAST_MAX_SATURATION], so a sheet that really is coloured stays a picture.
     */
    private fun paperStats(rgb: Mat): PaperStats {
        val small = Mat()
        try {
            val scale = min(1.0, 256.0 / max(rgb.cols(), rgb.rows()))
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            val bytes = ByteArray((small.total() * 3).toInt())
            small[0, 0, bytes]
            val n = bytes.size / 3
            if (n == 0) return PaperStats(0.0, null)
            fun channel(i: Int, c: Int) = bytes[i * 3 + c].toInt() and 0xFF
            val values = IntArray(n) { max(channel(it, 0), max(channel(it, 1), channel(it, 2))) }
            val bright = percentile(values, 0.9)
            // The tint: the median colour of the brightest tones.
            val histograms = Array(3) { IntArray(256) }
            var top = 0
            for (i in 0 until n) if (values[i] >= bright * 0.85) {
                for (c in 0 until 3) histograms[c][channel(i, c)]++
                top++
            }
            val tint = DoubleArray(3) { c ->
                var seen = 0
                var median = 255
                for (v in 0 until 256) { seen += histograms[c][v]; if (seen * 2 >= top) { median = v; break } }
                median.toDouble().coerceAtLeast(1.0)
            }
            val strongest = tint.max()
            val tintSaturation = (strongest - tint.min()) / strongest * 255.0
            val gains = if (tintSaturation in CAST_MIN_SATURATION..CAST_MAX_SATURATION) DoubleArray(3) { strongest / tint[it] } else null
            var paper = 0
            for (i in 0 until n) {
                var hi = 0.0
                var lo = 255.0
                for (c in 0 until 3) {
                    val v = min(255.0, channel(i, c) * (gains?.get(c) ?: 1.0))
                    if (v > hi) hi = v
                    if (v < lo) lo = v
                }
                val saturation = if (hi <= 0.0) 0.0 else (hi - lo) / hi * 255.0
                if (saturation < 45 && hi >= bright * 0.72) paper++
            }
            return PaperStats(paper.toDouble() / n, gains)
        } finally {
            small.release()
        }
    }

    // Ink: pull the darkest few percent to black so faint print reads clearly; paper is already white.
    private fun autoDocument(rgb: Mat, gains: DoubleArray?): Mat =
        cleanPage(rgb, gains, inkQuantile = 0.02, inkCap = 110.0, white = 250.0, saturation = 1f, sharpen = 0.45)

    /**
     * A page of paper with its shadows and colour cast divided out (the paper turns white), the darkest
     * [inkQuantile] of it pulled to black (never from above [inkCap], so a pale page is not crushed), everything
     * from [white] up clipped to white, then [saturation] and an unsharp mask of [sharpen]. [gains] take the light's
     * tint off first (see [paperStats]), so the shadow estimate recognises tinted paper as paper. Leaves [rgb] alone.
     */
    private fun cleanPage(
        rgb: Mat, gains: DoubleArray?, inkQuantile: Double, inkCap: Double, white: Double, saturation: Float, sharpen: Double
    ): Mat {
        val flat = if (gains == null) removeShadows(rgb) else {
            val balanced = Mat()
            try {
                Core.multiply(rgb, Scalar(gains[0], gains[1], gains[2]), balanced)
                removeShadows(balanced)
            } finally {
                balanced.release()
            }
        }
        try {
            val ink = min(percentileOfGray(flat, inkQuantile), inkCap)
            val lut = levelsLut(black = ink, white = white, gains = doubleArrayOf(1.0, 1.0, 1.0), gamma = 1.0)
            var leveled = Mat()
            try {
                Core.LUT(flat, lut, leveled)
            } finally {
                lut.release()
            }
            if (saturation != 1f) leveled = leveled.then { colorMatrix(it, saturation = saturation, contrast = 1f, brightness = 0f) }
            return unsharp(leveled, amount = sharpen)
        } finally {
            flat.release()
        }
    }

    /**
     * Colour (2026-11-18): the paper evened out and lifted to white like Auto Clean, but the colours and the tones in
     * between kept close to the page: a light black point, a little saturation, a gentle sharpen. It used to even the
     * light out in Lab and then run CLAHE, which on a flat sheet has nothing to work on but the paper itself: it left
     * the page mid-grey and mottled, amplified sensor noise and put a bright glow round and inside every solid block
     * (seen in `scripts/scan-bench` `enhance dump=`). A picture that is not a page (see [paperFraction]) gets the
     * photo treatment, as in Auto Clean.
     */
    internal fun colourDocument(rgb: Mat): Mat {
        val paper = paperStats(rgb)
        return if (paper.fraction >= DOCUMENT_PAPER_FRACTION) {
            cleanPage(rgb, paper.gains, inkQuantile = 0.01, inkCap = 56.0, white = 246.0, saturation = 1.08f, sharpen = 0.35)
        } else autoPhoto(rgb)
    }

    /** Greyscale: [colourDocument] with the colour taken out, so the paper is white and even here too. */
    internal fun greyDocument(rgb: Mat): Mat {
        val colour = colourDocument(rgb)
        val gray = Mat()
        val out = Mat()
        try {
            Imgproc.cvtColor(colour, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGB)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            colour.release(); gray.release()
        }
    }

    /**
     * Black and white (2026-11-18): [adaptiveBlackWhite] for the strokes, plus everything that is solidly dark once
     * the lighting is divided out. The adaptive rule alone compares each pixel with its neighbourhood, so the inside
     * of a filled block (a logo, a table header, a stamp, a photo) matched its surroundings and came out white with
     * only an outline left.
     */
    internal fun blackWhiteDocument(rgb: Mat): Mat {
        val out = adaptiveBlackWhite(rgb)
        val flat = removeShadows(rgb)
        val gray = Mat()
        val solid = Mat()
        try {
            Imgproc.cvtColor(flat, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.threshold(gray, solid, SOLID_INK_LEVEL, 255.0, Imgproc.THRESH_BINARY)
            // A block right at the level would come out speckled; the median makes each neighbourhood one or the other.
            Imgproc.medianBlur(solid, solid, 5)
            Imgproc.cvtColor(solid, flat, Imgproc.COLOR_GRAY2RGB)
            Core.min(out, flat, out)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            flat.release(); gray.release(); solid.release()
        }
    }

    /** What [whiteboard] works on: a clean page with stronger colours, or the photo treatment for anything else. */
    private fun boardBase(rgb: Mat): Mat {
        val paper = paperStats(rgb)
        return if (paper.fraction >= DOCUMENT_PAPER_FRACTION) {
            cleanPage(rgb, paper.gains, inkQuantile = 0.02, inkCap = 80.0, white = 244.0, saturation = 1.25f, sharpen = 0.3)
        } else autoPhoto(rgb)
    }

    private fun autoPhoto(rgb: Mat): Mat {
        val small = Mat()
        try {
            val scale = min(1.0, 512.0 / max(rgb.cols(), rgb.rows()))
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            val bytes = ByteArray((small.total() * 3).toInt())
            small.get(0, 0, bytes)
            val n = bytes.size / 3
            // Grey-world white balance from the mid-tones only, held to a gentle correction.
            val sums = DoubleArray(3); var count = 0
            for (i in 0 until n) {
                val r = bytes[i * 3].toInt() and 0xFF; val g = bytes[i * 3 + 1].toInt() and 0xFF; val b = bytes[i * 3 + 2].toInt() and 0xFF
                val l = (r + g + b) / 3
                if (l in 24..232) { sums[0] += r.toDouble(); sums[1] += g.toDouble(); sums[2] += b.toDouble(); count++ }
            }
            val gains = if (count < n / 20) doubleArrayOf(1.0, 1.0, 1.0) else {
                val grey = (sums[0] + sums[1] + sums[2]) / 3.0
                DoubleArray(3) { c -> (1.0 + (grey / sums[c].coerceAtLeast(1.0) - 1.0) * 0.6).coerceIn(0.88, 1.15) }
            }
            // Levels from the luminance spread, without clipping more than a sliver at either end.
            val luma = IntArray(n) { i ->
                ((bytes[i * 3].toInt() and 0xFF) * 0.299 + (bytes[i * 3 + 1].toInt() and 0xFF) * 0.587 + (bytes[i * 3 + 2].toInt() and 0xFF) * 0.114).toInt()
            }
            val black = percentile(luma, 0.005).coerceAtMost(48.0)
            val white = percentile(luma, 0.995).coerceAtLeast(200.0)
            // Underexposed shots get a midtone lift (gamma), so a dim room reads like the eye saw it.
            val mean = luma.average()
            val stretchedMean = ((mean - black) / (white - black)).coerceIn(0.05, 0.95)
            val gamma = if (stretchedMean < 0.42) (kotlin.math.ln(0.46) / kotlin.math.ln(stretchedMean)).coerceIn(0.6, 1.0) else 1.0
            val lut = levelsLut(black, white, gains, gamma)
            val leveled = Mat()
            try {
                Core.LUT(rgb, lut, leveled)
            } finally {
                lut.release()
            }
            val vivid = colorMatrix(leveled, saturation = 1.06f, contrast = 1.0f, brightness = 0f)
            leveled.release()
            return unsharp(vivid, amount = 0.3)
        } finally {
            small.release()
        }
    }

    /** One table for all three channels: per-channel gain, then black/white points, then gamma. */
    private fun levelsLut(black: Double, white: Double, gains: DoubleArray, gamma: Double): Mat {
        val table = ByteArray(256 * 3)
        val span = (white - black).coerceAtLeast(1.0)
        for (v in 0 until 256) for (c in 0 until 3) {
            val x = ((v * gains[c] - black) / span).coerceIn(0.0, 1.0)
            table[v * 3 + c] = (x.pow(gamma) * 255.0 + 0.5).toInt().coerceIn(0, 255).toByte()
        }
        return Mat(1, 256, org.opencv.core.CvType.CV_8UC3).also { it.put(0, 0, table) }
    }

    /** Unsharp mask scaled to the picture's size; consumes [rgb]. */
    private fun unsharp(rgb: Mat, amount: Double): Mat {
        val blurred = Mat(); val out = Mat()
        try {
            val sigma = max(1.0, max(rgb.cols(), rgb.rows()) / 1500.0)
            Imgproc.GaussianBlur(rgb, blurred, Size(0.0, 0.0), sigma)
            Core.addWeighted(rgb, 1.0 + amount, blurred, -amount, 0.0, out)
            return out
        } catch (error: Throwable) {
            out.release(); throw error
        } finally {
            blurred.release(); rgb.release()
        }
    }

    private fun percentileOfGray(rgb: Mat, q: Double): Double {
        val small = Mat(); val gray = Mat()
        try {
            val scale = min(1.0, 512.0 / max(rgb.cols(), rgb.rows()))
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
            val bytes = ByteArray(gray.total().toInt())
            gray.get(0, 0, bytes)
            return percentile(IntArray(bytes.size) { bytes[it].toInt() and 0xFF }, q)
        } finally {
            small.release(); gray.release()
        }
    }

    private fun percentile(values: IntArray, q: Double): Double {
        if (values.isEmpty()) return 0.0
        val histogram = IntArray(256)
        values.forEach { histogram[it.coerceIn(0, 255)]++ }
        val target = (q * values.size).toLong()
        var seen = 0L
        for (v in 0 until 256) {
            seen += histogram[v]
            if (seen > target) return v.toDouble()
        }
        return 255.0
    }

    /**
     * Evens out shadows and lighting: every colour channel is divided by an estimate of the bare paper under it.
     * The estimate is made at low resolution from text-free (dilated), low-saturation pixels only, so coloured
     * blocks such as logos and photos keep their colour instead of being washed out to white.
     */
    internal fun removeShadows(rgb: Mat): Mat {
        val small = Mat(); val dilated = Mat(); val paper = Mat(); val hsv = Mat()
        val saturation = Mat(); val mask = Mat(); val maskF = Mat(); val paperF = Mat(); val masked = Mat()
        val den = Mat(); val den3 = Mat(); val bgF = Mat(); val fallback = Mat(); val fallbackF = Mat()
        val sparse = Mat(); val background = Mat(); val full = Mat(); val out = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        try {
            val scale = min(1.0, SHADOW_ESTIMATE_SIZE / max(rgb.cols(), rgb.rows()).toDouble())
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            // Dilating keeps the brightest nearby value, which wipes dark text strokes out of the paper estimate.
            Imgproc.dilate(small, dilated, kernel)
            Imgproc.medianBlur(dilated, paper, 9)
            Imgproc.cvtColor(paper, hsv, Imgproc.COLOR_RGB2HSV)
            Core.extractChannel(hsv, saturation, 1)
            Imgproc.threshold(saturation, mask, PAPER_MAX_SATURATION, 1.0, Imgproc.THRESH_BINARY_INV)
            mask.convertTo(maskF, org.opencv.core.CvType.CV_32F)
            paper.convertTo(paperF, org.opencv.core.CvType.CV_32FC3)
            Core.merge(listOf(maskF, maskF, maskF), den3)
            Core.multiply(paperF, den3, masked)
            // Normalised convolution: the average paper colour nearby, ignoring coloured (masked-out) pixels.
            Imgproc.GaussianBlur(masked, masked, Size(0.0, 0.0), SHADOW_SIGMA)
            Imgproc.GaussianBlur(maskF, den, Size(0.0, 0.0), SHADOW_SIGMA)
            Core.max(den, org.opencv.core.Scalar(1e-3), den)
            Core.merge(listOf(den, den, den), den3)
            Core.divide(masked, den3, bgF)
            // Large coloured areas with no paper nearby fall back to a plain local median.
            Imgproc.medianBlur(paper, fallback, 21)
            fallback.convertTo(fallbackF, org.opencv.core.CvType.CV_32FC3)
            Core.compare(den, org.opencv.core.Scalar(0.02), sparse, Core.CMP_LE)
            fallbackF.copyTo(bgF, sparse)
            bgF.convertTo(background, org.opencv.core.CvType.CV_8UC3)
            Core.max(background, org.opencv.core.Scalar(1.0, 1.0, 1.0), background)
            Imgproc.resize(background, full, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.divide(rgb, full, out, 255.0)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            listOf(small, dilated, paper, hsv, saturation, mask, maskF, paperF, masked, den, den3, bgF,
                fallback, fallbackF, sparse, background, full, kernel).forEach(Mat::release)
        }
    }

    /**
     * Paints over fingers holding the page: skin-coloured patches that touch the image border and stay near it
     * are filled from the surrounding paper. Patches deep inside the page (a photo of a face, say) are left alone.
     * Works on [rgb] in place and returns it.
     */
    internal fun removeFingers(rgb: Mat): Mat {
        val small = Mat(); val ycc = Mat(); val skin = Mat()
        val labels = Mat(); val stats = Mat(); val centroids = Mat(); val keep = Mat(); val part = Mat()
        val filled = Mat(); val filledFull = Mat(); val maskFull = Mat()
        val open = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        val close = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
        try {
            val scale = min(1.0, FINGER_ESTIMATE_SIZE / max(rgb.cols(), rgb.rows()).toDouble())
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, ycc, Imgproc.COLOR_RGB2YCrCb)
            Core.inRange(ycc, org.opencv.core.Scalar(0.0, 138.0, 77.0), org.opencv.core.Scalar(255.0, 178.0, 127.0), skin)
            Imgproc.morphologyEx(skin, skin, Imgproc.MORPH_OPEN, open)
            Imgproc.morphologyEx(skin, skin, Imgproc.MORPH_CLOSE, close)

            val w = small.cols(); val h = small.rows()
            val count = Imgproc.connectedComponentsWithStats(skin, labels, stats, centroids)
            keep.create(skin.size(), org.opencv.core.CvType.CV_8UC1)
            keep.setTo(org.opencv.core.Scalar(0.0))
            var found = false
            for (label in 1 until count) {
                val x = stats.get(label, Imgproc.CC_STAT_LEFT)[0].toInt()
                val y = stats.get(label, Imgproc.CC_STAT_TOP)[0].toInt()
                val cw = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
                val ch = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()
                val area = stats.get(label, Imgproc.CC_STAT_AREA)[0]
                val touchesBorder = x == 0 || y == 0 || x + cw == w || y + ch == h
                val fingerSized = area > 0.001 * w * h && area < 0.12 * w * h && cw < 0.4 * w && ch < 0.4 * h
                if (touchesBorder && fingerSized) {
                    Core.compare(labels, org.opencv.core.Scalar(label.toDouble()), part, Core.CMP_EQ)
                    Core.bitwise_or(keep, part, keep)
                    found = true
                }
            }
            if (!found) return rgb
            // Grow the mask to cover the finger's soft edge and shadow, then fill it from the paper around it.
            Imgproc.dilate(keep, keep, close)
            org.opencv.photo.Photo.inpaint(small, keep, filled, 7.0, org.opencv.photo.Photo.INPAINT_TELEA)
            Imgproc.resize(filled, filledFull, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.resize(keep, maskFull, rgb.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
            filledFull.copyTo(rgb, maskFull)
            return rgb
        } finally {
            listOf(small, ycc, skin, labels, stats, centroids, keep, part, filled, filledFull, maskFull, open, close)
                .forEach(Mat::release)
        }
    }

    /**
     * The same 4x5 colour matrix Android's ColorMatrix builds for setSaturation followed by a contrast and
     * brightness shift, applied with OpenCV: out = contrast * saturate(in) + 128 * (1 - contrast) + brightness.
     */
    internal fun colorMatrix(rgb: Mat, saturation: Float, contrast: Float, brightness: Float): Mat {
        // Android weights luminance as 0.213 R + 0.715 G + 0.072 B when desaturating.
        val inv = 1f - saturation
        val r = 0.213f * inv; val g = 0.715f * inv; val b = 0.072f * inv
        val t = 128f * (1f - contrast) + brightness
        val transform = Mat(3, 4, org.opencv.core.CvType.CV_32F)
        val out = Mat()
        try {
            transform.put(
                0, 0,
                floatArrayOf(
                    contrast * (r + saturation), contrast * g, contrast * b, t,
                    contrast * r, contrast * (g + saturation), contrast * b, t,
                    contrast * r, contrast * g, contrast * (b + saturation), t
                )
            )
            Core.transform(rgb, out, transform)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            transform.release()
        }
    }

    /** Lifts the paper towards white while keeping pen strokes: a small boost everywhere, a larger one on bright pixels. */
    internal fun whiteboard(rgb: Mat): Mat {
        val channels = mutableListOf<Mat>()
        val darkest = Mat()
        val bright = Mat()
        val boosted = Mat()
        try {
            Core.split(rgb, channels)
            Core.min(channels[0], channels[1], darkest)
            Core.min(darkest, channels[2], darkest)
            Imgproc.threshold(darkest, bright, 150.0, 255.0, Imgproc.THRESH_BINARY)
            Core.add(rgb, org.opencv.core.Scalar(16.0, 16.0, 16.0), boosted)
            Core.add(boosted, org.opencv.core.Scalar(26.0, 26.0, 26.0), boosted, bright)
            return colorMatrix(boosted, saturation = 1.08f, contrast = 1.28f, brightness = 6f)
        } finally {
            channels.forEach(Mat::release)
            darkest.release(); bright.release(); boosted.release()
        }
    }

    /** Adaptive thresholding keeps text readable under uneven paper lighting. */
    internal fun adaptiveBlackWhite(rgb: Mat): Mat {
        val gray = Mat()
        val bw = Mat()
        val out = Mat()
        try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            // The old rule painted a pixel black when its grey level was below the neighbourhood mean minus 13, so a
            // pixel exactly at the threshold stayed white. OpenCV paints it black when the level is not above
            // mean minus C, which takes C = 14 to match. Against the old code on 30 real photographs this halves the
            // differing pixels (mean 1.2% to 0.5%); inside the page every remaining one is within one grey level.
            val radius = max(10, min(rgb.cols(), rgb.rows()) / 55)
            Imgproc.adaptiveThreshold(gray, bw, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 2 * radius + 1, 14.0)
            Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGB)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            gray.release(); bw.release()
        }
    }

    /** Runs one step on an OpenCV image and frees the input when the step produced a new one. */
    /** Above this share of paper-like pixels, Auto Clean treats the picture as a document. */
    internal const val DOCUMENT_PAPER_FRACTION = 0.45

    private inline fun Mat.then(step: (Mat) -> Mat): Mat {
        val result = step(this)
        if (result !== this) release()
        return result
    }
}
