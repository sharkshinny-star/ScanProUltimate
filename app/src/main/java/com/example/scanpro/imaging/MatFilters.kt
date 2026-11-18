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
    /** The illumination estimate never drops below this share of the paper level (see [illuminationEstimate]). */
    private const val ILLUMINATION_FLOOR = 0.55

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
        PageFilter.COLOR -> autoDocumentEnhance(source, colorBoost = true)
        PageFilter.GRAY -> enhancedGrayscale(source)
        PageFilter.BW -> adaptiveBlackWhite(source)
        PageFilter.WHITEBOARD -> autoDocumentEnhance(source, colorBoost = true).then { whiteboard(it) }
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
    internal fun smartAuto(rgb: Mat): Mat =
        if (paperFraction(rgb) >= DOCUMENT_PAPER_FRACTION) autoDocument(rgb) else autoPhoto(rgb)

    /** Share of the picture that looks like paper: low saturation and close to the brightest tones in it. */
    internal fun paperFraction(rgb: Mat): Double {
        val small = Mat(); val hsv = Mat()
        try {
            val scale = min(1.0, 256.0 / max(rgb.cols(), rgb.rows()))
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, hsv, Imgproc.COLOR_RGB2HSV)
            val bytes = ByteArray((hsv.total() * 3).toInt())
            hsv[0, 0, bytes]
            val values = IntArray(bytes.size / 3) { bytes[it * 3 + 2].toInt() and 0xFF }
            val bright = percentile(values, 0.9)
            var paper = 0
            for (i in values.indices) {
                val sat = bytes[i * 3 + 1].toInt() and 0xFF
                if (sat < 45 && values[i] >= bright * 0.72) paper++
            }
            return paper.toDouble() / values.size.coerceAtLeast(1)
        } finally {
            small.release(); hsv.release()
        }
    }

    private fun autoDocument(rgb: Mat): Mat {
        val flat = removeShadows(rgb)
        try {
            // Ink: pull the darkest few percent to black so faint print reads clearly; paper is already white.
            val ink = min(percentileOfGray(flat, 0.02), 110.0)
            val lut = levelsLut(black = ink, white = 250.0, gains = doubleArrayOf(1.0, 1.0, 1.0), gamma = 1.0)
            val leveled = Mat()
            try {
                Core.LUT(flat, lut, leveled)
            } finally {
                lut.release()
            }
            return unsharp(leveled, amount = 0.45)
        } finally {
            flat.release()
        }
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
     * Premium document enhancement: normalise uneven illumination in LAB luminance,
     * apply local contrast (CLAHE), edge-preserving denoise and a restrained unsharp mask.
     * This improves both visual output and OCR without sending pixels off-device.
     */
    /**
     * An estimate of the illumination on the page, for dividing out shadows: the luminance at a low resolution with
     * the dark content closed away, then smoothed. A plain blur of the page itself darkens wherever text is dense,
     * so dividing by it over-brightens a halo around every paragraph (measured by `scripts/scan-bench` `enhance`
     * before this estimate: a background spread of about 20 grey levels and a glow of about 20 around the strokes;
     * after: both under 3). The closing kernel is wider than a text line, so ink vanishes from the estimate, while
     * blocks larger than it (photos, logos) survive; the floor at [ILLUMINATION_FLOOR] of the paper level keeps such
     * a block dark instead of treating it as a shadow to lift.
     */
    private fun illuminationEstimate(luminance: Mat): Mat {
        val longSide = max(luminance.cols(), luminance.rows()).toDouble()
        val scale = (SHADOW_ESTIMATE_SIZE / longSide).coerceAtMost(1.0)
        val small = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
        val paperMask = Mat()
        val masked = Mat()
        val weight = Mat()
        try {
            Imgproc.resize(luminance, small, Size(luminance.cols() * scale, luminance.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.morphologyEx(small, small, Imgproc.MORPH_CLOSE, kernel)
            // The paper level is where most of the (closed) page sits. Blocks that stay darker (a photo, a logo)
            // are masked out and their area filled from the paper around them, so the estimate neither lifts them
            // to white nor leaves a bright ring on the paper beside them.
            val histogram = histogramOf(small)
            val target = small.rows().toLong() * small.cols() * 6 / 10
            var seen = 0L
            var paper = 255
            for (v in 0..255) {
                seen += histogram[v]
                if (seen >= target) { paper = v; break }
            }
            Imgproc.threshold(small, paperMask, paper * ILLUMINATION_FLOOR, 1.0, Imgproc.THRESH_BINARY)
            small.convertTo(masked, CvType.CV_32F)
            paperMask.convertTo(weight, CvType.CV_32F)
            Core.multiply(masked, weight, masked)
            Imgproc.GaussianBlur(masked, masked, Size(0.0, 0.0), 10.0)
            Imgproc.GaussianBlur(weight, weight, Size(0.0, 0.0), 10.0)
            Core.max(weight, Scalar(1e-4), weight)
            Core.divide(masked, weight, masked)
            // A page that is mostly one dark block leaves no paper to fill from; the floor keeps the divide sane.
            Core.max(masked, Scalar(paper * ILLUMINATION_FLOOR), masked)
            masked.convertTo(small, CvType.CV_8U)
            val background = Mat()
            Imgproc.resize(small, background, luminance.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            return background
        } finally {
            small.release()
            kernel.release()
            paperMask.release()
            masked.release()
            weight.release()
        }
    }

    /**
     * Shifts Lab [a] and [b] so the paper reads neutral grey, killing a warm or cool light cast. The cast is read
     * from the pixels that are bright in [luminance] (the paper); a page with no cast shifts by nearly nothing.
     */
    private fun neutraliseCast(luminance: Mat, a: Mat, b: Mat) {
        val brightMask = Mat()
        try {
            Imgproc.threshold(luminance, brightMask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val meanA = Core.mean(a, brightMask).`val`[0]
            val meanB = Core.mean(b, brightMask).`val`[0]
            // At most a gentle pull: a strongly coloured scene (a poster) is not paper to neutralise.
            val shiftA = (128.0 - meanA).coerceIn(-14.0, 14.0)
            val shiftB = (128.0 - meanB).coerceIn(-14.0, 14.0)
            if (kotlin.math.abs(shiftA) > 0.8) Core.add(a, Scalar(shiftA), a)
            if (kotlin.math.abs(shiftB) > 0.8) Core.add(b, Scalar(shiftB), b)
        } finally {
            brightMask.release()
        }
    }

    /** The grey-level histogram of a single-channel image, for [percentile]. */
    private fun histogramOf(gray: Mat): IntArray {
        val hist = Mat()
        try {
            Imgproc.calcHist(listOf(gray), org.opencv.core.MatOfInt(0), Mat(), hist, org.opencv.core.MatOfInt(256), org.opencv.core.MatOfFloat(0f, 256f))
            val values = FloatArray(256)
            hist.get(0, 0, values)
            return IntArray(256) { values[it].toInt() }
        } finally {
            hist.release()
        }
    }

    internal fun autoDocumentEnhance(rgb: Mat, colorBoost: Boolean): Mat {
        val lab = Mat()
        val channels = mutableListOf<Mat>()
        val background = Mat()
        val normalized = Mat()
        val enhanced = Mat()
        val denoised = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        try {
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            val luminance = channels[0]
            // The lighting's colour cast sits in a and b; the paper says how far from neutral it is.
            neutraliseCast(luminance, channels[1], channels[2])

            // Content-free illumination estimate. Dividing by it suppresses page shadows without the halo a plain
            // blur leaves around text (see illuminationEstimate).
            illuminationEstimate(luminance).let { it.copyTo(background); it.release() }
            Core.divide(luminance, background, normalized, 235.0)
            val clahe = Imgproc.createCLAHE(if (colorBoost) 2.0 else 2.35, Size(8.0, 8.0))
            try {
                clahe.apply(normalized, luminance)
            } finally {
                clahe.collectGarbage()
            }
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, enhanced, Imgproc.COLOR_Lab2RGB)

            Imgproc.bilateralFilter(enhanced, denoised, 5, 34.0, 34.0)
            Imgproc.GaussianBlur(denoised, blurred, Size(0.0, 0.0), 1.1)
            Core.addWeighted(denoised, 1.22, blurred, -0.22, 0.0, sharpened)
            return if (colorBoost) {
                colorMatrix(sharpened, saturation = 1.10f, contrast = 1.04f, brightness = 1f).also { sharpened.release() }
            } else {
                sharpened
            }
        } catch (error: Throwable) {
            sharpened.release()
            throw error
        } finally {
            channels.forEach(Mat::release)
            lab.release(); background.release(); normalized.release(); enhanced.release(); denoised.release(); blurred.release()
        }
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

    internal fun enhancedGrayscale(rgb: Mat): Mat {
        val gray = Mat()
        val background = Mat()
        val normalized = Mat()
        val out = Mat()
        try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            illuminationEstimate(gray).let { it.copyTo(background); it.release() }
            Core.divide(gray, background, normalized, 235.0)
            val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
            try { clahe.apply(normalized, normalized) } finally { clahe.collectGarbage() }
            Imgproc.cvtColor(normalized, out, Imgproc.COLOR_GRAY2RGB)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            gray.release(); background.release(); normalized.release()
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
