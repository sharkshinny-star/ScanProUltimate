package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Core
import org.opencv.core.Mat
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
        PageFilter.AUTO -> autoDocumentEnhance(source, colorBoost = false)
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
     * Premium document enhancement: normalize uneven illumination in LAB luminance,
     * apply local contrast (CLAHE), edge-preserving denoise and a restrained unsharp mask.
     * This improves both visual output and OCR without sending pixels off-device.
     */
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

            // Large-radius illumination estimate. Dividing by it suppresses page shadows.
            Imgproc.GaussianBlur(luminance, background, Size(0.0, 0.0), 24.0)
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
            Imgproc.GaussianBlur(gray, background, Size(0.0, 0.0), 24.0)
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
        val c = contrast
        val transform = Mat(3, 4, org.opencv.core.CvType.CV_32F)
        val out = Mat()
        try {
            transform.put(
                0, 0,
                floatArrayOf(
                    c * (r + saturation), c * g, c * b, t,
                    c * r, c * (g + saturation), c * b, t,
                    c * r, c * g, c * (b + saturation), t
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
    private inline fun Mat.then(step: (Mat) -> Mat): Mat {
        val result = step(this)
        if (result !== this) release()
        return result
    }
}
