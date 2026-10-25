package com.example.scanpro.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withRotation
import androidx.core.graphics.withRotation
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import java.io.File
import java.io.FileOutputStream
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {
    init { OpenCvRuntime.ensureLoaded() }

    private const val MAX_DECODE_PIXELS = 24_000_000L

    fun renderPage(
        source: File,
        destination: File,
        filter: PageFilter,
        manualRotationDegrees: Int,
        crop: CropQuad = CropQuad.DEFAULT,
        adjustments: PageAdjustments = PageAdjustments.DEFAULT,
        annotations: List<PageAnnotation> = emptyList()
    ) {
        val maxDimension = if (filter == PageFilter.BW) 2200 else 3400
        var bitmap = decodeScaled(source.absolutePath, maxDimension)
            ?: error("Unable to decode ${source.name}")

        val exifRotation = runCatching {
            ExifInterface(source.absolutePath).rotationDegrees
        }.getOrDefault(0)

        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        if (!crop.isIdentity()) bitmap = bitmap.then { perspectiveCrop(it, crop) }

        val totalRotation = ((manualRotationDegrees % 360) + 360) % 360
        if (totalRotation != 0) bitmap = bitmap.then { rotate(it, totalRotation) }

        bitmap = bitmap.then { applyNamedFilter(it, filter) }
        if (!adjustments.isNeutral()) bitmap = bitmap.then { applyUserAdjustments(it, adjustments.normalized()) }
        if (annotations.isNotEmpty()) bitmap = bitmap.then { applyAnnotations(it, annotations) }
        try {
            saveJpeg(bitmap, destination, 96)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Lower-resolution non-destructive preview used by adjustment/filter UIs. The same
     * perspective/filter/tonal pipeline is used as final rendering, but on a bounded bitmap.
     */
    fun renderPreview(
        source: File,
        filter: PageFilter,
        manualRotationDegrees: Int,
        crop: CropQuad = CropQuad.DEFAULT,
        adjustments: PageAdjustments = PageAdjustments.DEFAULT,
        annotations: List<PageAnnotation> = emptyList(),
        maxDimension: Int = 960
    ): Bitmap {
        var bitmap = decodeScaled(source.absolutePath, maxDimension)
            ?: error("Unable to decode ${source.name}")
        val exifRotation = runCatching { ExifInterface(source.absolutePath).rotationDegrees }.getOrDefault(0)
        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        if (!crop.isIdentity()) bitmap = bitmap.then { perspectiveCrop(it, crop) }
        val totalRotation = ((manualRotationDegrees % 360) + 360) % 360
        if (totalRotation != 0) bitmap = bitmap.then { rotate(it, totalRotation) }
        bitmap = bitmap.then { applyNamedFilter(it, filter) }
        if (!adjustments.isNeutral()) bitmap = bitmap.then { applyUserAdjustments(it, adjustments.normalized()) }
        if (annotations.isNotEmpty()) bitmap = bitmap.then { applyAnnotations(it, annotations) }
        return bitmap
    }

    private fun applyNamedFilter(source: Bitmap, filter: PageFilter): Bitmap = when (filter) {
        PageFilter.ORIGINAL -> source
        PageFilter.AUTO -> autoDocumentEnhance(source, colorBoost = false)
        PageFilter.COLOR -> autoDocumentEnhance(source, colorBoost = true)
        PageFilter.GRAY -> enhancedGrayscale(source)
        PageFilter.BW -> adaptiveBlackWhite(source)
        PageFilter.WHITEBOARD -> autoDocumentEnhance(source, colorBoost = true).then { whiteboard(it) }
    }

    private fun applyUserAdjustments(source: Bitmap, adjustments: PageAdjustments): Bitmap {
        val a = adjustments.normalized()
        val saturationMatrix = ColorMatrix().apply { setSaturation(a.saturation) }
        val brightnessShift = a.brightness * 92f
        val translation = 128f * (1f - a.contrast) + brightnessShift
        val tonalMatrix = ColorMatrix(
            floatArrayOf(
                a.contrast, 0f, 0f, 0f, translation,
                0f, a.contrast, 0f, 0f, translation,
                0f, 0f, a.contrast, 0f, translation,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturationMatrix.postConcat(tonalMatrix)
        val output = createBitmap(source.width, source.height)
        Canvas(output).drawBitmap(
            source, 0f, 0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(saturationMatrix)
            }
        )
        return output
    }

    fun saveSignature(bitmap: Bitmap, destination: File) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode signature"
            }
        }
    }

    fun cropSourceToNewFile(source: File, destination: File, quad: CropQuad) {
        var bitmap = decodeScaled(source.absolutePath, 4200) ?: error("Unable to decode source")
        val exifRotation = runCatching { ExifInterface(source.absolutePath).rotationDegrees }.getOrDefault(0)
        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        val cropped = bitmap.then { perspectiveCrop(it, quad) }
        try {
            saveJpeg(cropped, destination, 98)
        } finally {
            cropped.recycle()
        }
    }


    fun decodeOrientedScaled(path: String, maxDimension: Int): Bitmap? {
        var bitmap = decodeScaled(path, maxDimension) ?: return null
        val exifRotation = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        return bitmap
    }

    fun decodeScaled(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        // Decode up to 2x the target and scale down for quality, but never more than MAX_DECODE_PIXELS:
        // a 48-50 MP capture would otherwise decode at full size (~200 MB) and run out of memory.
        while (
            max(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2 ||
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_DECODE_PIXELS
        ) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val largest = max(decoded.width, decoded.height)
        if (largest <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / largest.toFloat()
        val scaled = decoded.scale(
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
        return scaled
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun perspectiveCrop(source: Bitmap, crop: CropQuad): Bitmap {
        val tl = crop.topLeft.clamped()
        val tr = crop.topRight.clamped()
        val br = crop.bottomRight.clamped()
        val bl = crop.bottomLeft.clamped()

        fun px(pointX: Float) = pointX * source.width
        fun py(pointY: Float) = pointY * source.height
        fun distance(ax: Float, ay: Float, bx: Float, by: Float) = hypot(bx - ax, by - ay)

        val tlx = px(tl.x); val tly = py(tl.y)
        val trx = px(tr.x); val tryy = py(tr.y)
        val brx = px(br.x); val bry = py(br.y)
        val blx = px(bl.x); val bly = py(bl.y)

        val outputWidth = max(
            distance(tlx, tly, trx, tryy),
            distance(blx, bly, brx, bry)
        ).toInt().coerceIn(64, 5000)
        val outputHeight = max(
            distance(tlx, tly, blx, bly),
            distance(trx, tryy, brx, bry)
        ).toInt().coerceIn(64, 7000)

        val src = floatArrayOf(tlx, tly, trx, tryy, brx, bry, blx, bly)
        val dst = floatArrayOf(
            0f, 0f,
            outputWidth.toFloat(), 0f,
            outputWidth.toFloat(), outputHeight.toFloat(),
            0f, outputHeight.toFloat()
        )
        // A crop with (near) zero area or crossed edges can't be mapped to a rectangle; keep the page
        // uncropped rather than failing the whole edit or producing a mirrored page.
        if (!isUsableQuad(src, source.width.toFloat() * source.height)) return source
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return source
        val output = createBitmap(outputWidth, outputHeight)
        Canvas(output).drawBitmap(
            source,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    /** True for a convex, consistently wound quad covering at least 1% of the image. */
    private fun isUsableQuad(points: FloatArray, imageArea: Float): Boolean {
        var area = 0f
        var sign = 0
        for (i in 0 until 4) {
            val ax = points[i * 2]; val ay = points[i * 2 + 1]
            val bx = points[(i + 1) % 4 * 2]; val by = points[(i + 1) % 4 * 2 + 1]
            val cx = points[(i + 2) % 4 * 2]; val cy = points[(i + 2) % 4 * 2 + 1]
            area += ax * by - bx * ay
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            val turn = if (cross > 0f) 1 else if (cross < 0f) -1 else 0
            if (turn == 0) return false
            if (sign == 0) sign = turn else if (turn != sign) return false
        }
        return kotlin.math.abs(area) / 2f >= imageArea * 0.01f
    }

    private fun applyAnnotations(source: Bitmap, annotations: List<PageAnnotation>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        annotations.forEach { item ->
            val left = item.x.coerceIn(0f, 1f) * output.width
            val top = item.y.coerceIn(0f, 1f) * output.height
            val width = item.width.coerceIn(0.01f, 1f) * output.width
            val height = item.height.coerceIn(0.01f, 1f) * output.height
            val rect = RectF(left, top, (left + width).coerceAtMost(output.width.toFloat()), (top + height).coerceAtMost(output.height.toFloat()))
            canvas.withRotation(item.rotationDegrees, rect.centerX(), rect.centerY()) {
            when (item.type) {
                AnnotationType.REDACT -> canvas.drawRect(rect, Paint().apply { color = Color.BLACK })
                AnnotationType.HIGHLIGHT -> canvas.drawRect(rect, Paint().apply {
                    color = Color.argb((item.opacity.coerceIn(0.05f, 1f) * 120).toInt(), 255, 235, 59)
                })
                AnnotationType.TEXT -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(
                            (255 * item.opacity.coerceIn(0.05f, 1f)).toInt(),
                            Color.red(item.color), Color.green(item.color), Color.blue(item.color)
                        )
                        textSize = max(22f, rect.height() * 0.52f)
                        isFakeBoldText = false
                    }
                    fitTextToBox(item.text.take(400), rect, paint)
                    drawWrappedText(canvas, item.text.take(400), rect, paint)
                }
                AnnotationType.SIGNATURE -> {
                    if (item.assetPath.isNotBlank()) {
                        // Downscale: a full-resolution signature is decoded on every re-render.
                        decodeScaled(item.assetPath, 1600)?.let { signature ->
                            try {
                                // Fit inside the box keeping the signature's proportions; stretching it
                                // to the box distorted every signature.
                                val scale = min(rect.width() / signature.width, rect.height() / signature.height)
                                val w = signature.width * scale
                                val h = signature.height * scale
                                val fitted = RectF(rect.centerX() - w / 2f, rect.centerY() - h / 2f, rect.centerX() + w / 2f, rect.centerY() + h / 2f)
                                canvas.drawBitmap(
                                    signature,
                                    null,
                                    fitted,
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                        alpha = (255 * item.opacity.coerceIn(0f, 1f)).toInt()
                                    }
                                )
                            } finally {
                                signature.recycle()
                            }
                        }
                    }
                }
            }
            }
        }
        return output
    }

    /** Shrinks [paint] until every word fits the box's width and the wrapped lines fit its height. */
    private fun fitTextToBox(text: String, rect: RectF, paint: Paint) {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val widest = words.maxOf { paint.measureText(it) }
        if (widest > rect.width()) paint.textSize = max(MIN_TEXT_SIZE, paint.textSize * rect.width() / widest)
        repeat(12) {
            if (paint.textSize <= MIN_TEXT_SIZE || wrappedLineCount(words, rect.width(), paint) * paint.fontSpacing <= rect.height()) return
            paint.textSize = max(MIN_TEXT_SIZE, paint.textSize * 0.88f)
        }
    }

    private fun wrappedLineCount(words: List<String>, width: Float, paint: Paint): Int {
        val space = paint.measureText(" ")
        var lines = 1
        var x = 0f
        for (word in words) {
            val measured = paint.measureText(word)
            if (x > 0f && x + measured > width) {
                lines++
                x = 0f
            }
            x += measured + space
        }
        return lines
    }

    private fun drawWrappedText(canvas: Canvas, text: String, rect: RectF, paint: Paint) {
        if (text.isBlank()) return
        val words = text.split(Regex("\\s+"))
        val lineHeight = paint.fontSpacing
        var x = rect.left
        var y = rect.top - paint.ascent()
        val space = paint.measureText(" ")
        for (word in words) {
            val measured = paint.measureText(word)
            if (x + measured > rect.right && x > rect.left) {
                x = rect.left
                y += lineHeight
            }
            if (y > rect.bottom) break
            canvas.drawText(word, x, y, paint)
            x += measured + space
        }
    }

    /**
     * Premium document enhancement: normalize uneven illumination in LAB luminance,
     * apply local contrast (CLAHE), edge-preserving denoise and a restrained unsharp mask.
     * This improves both visual output and OCR without sending pixels off-device.
     */
    private fun autoDocumentEnhance(source: Bitmap, colorBoost: Boolean): Bitmap {
        val rgba = Mat()
        val rgb = Mat()
        val lab = Mat()
        val channels = mutableListOf<Mat>()
        val background = Mat()
        val normalized = Mat()
        val denoised = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        val outputRgba = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
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
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

            Imgproc.bilateralFilter(rgb, denoised, 5, 34.0, 34.0)
            Imgproc.GaussianBlur(denoised, blurred, Size(0.0, 0.0), 1.1)
            Core.addWeighted(denoised, 1.22, blurred, -0.22, 0.0, sharpened)
            Imgproc.cvtColor(sharpened, outputRgba, Imgproc.COLOR_RGB2RGBA)

            val output = createBitmap(source.width, source.height)
            Utils.matToBitmap(outputRgba, output)
            return if (colorBoost) {
                output.then { applyColorMatrix(it, saturation = 1.10f, contrast = 1.04f, brightness = 1f) }
            } else output
        } finally {
            channels.forEach(Mat::release)
            rgba.release(); rgb.release(); lab.release(); background.release(); normalized.release()
            denoised.release(); blurred.release(); sharpened.release(); outputRgba.release()
        }
    }

    private fun enhancedGrayscale(source: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        val background = Mat()
        val normalized = Mat()
        val outputRgba = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, background, Size(0.0, 0.0), 24.0)
            Core.divide(gray, background, normalized, 235.0)
            val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
            try { clahe.apply(normalized, normalized) } finally { clahe.collectGarbage() }
            Imgproc.cvtColor(normalized, outputRgba, Imgproc.COLOR_GRAY2RGBA)
            val output = createBitmap(source.width, source.height)
            Utils.matToBitmap(outputRgba, output)
            return output
        } finally {
            rgba.release(); gray.release(); background.release(); normalized.release(); outputRgba.release()
        }
    }

    private fun applyColorMatrix(
        source: Bitmap,
        saturation: Float,
        contrast: Float,
        brightness: Float
    ): Bitmap {
        val saturationMatrix = ColorMatrix().apply { setSaturation(saturation) }
        val translation = 128f * (1f - contrast) + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translation,
                0f, contrast, 0f, 0f, translation,
                0f, 0f, contrast, 0f, translation,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturationMatrix.postConcat(contrastMatrix)

        val output = createBitmap(source.width, source.height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(saturationMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun whiteboard(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val c = pixels[index]
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val minChannel = min(r, min(g, b))
            val boost = if (minChannel > 150) 42 else 16
            val nr = (r + boost).coerceAtMost(255)
            val ng = (g + boost).coerceAtMost(255)
            val nb = (b + boost).coerceAtMost(255)
            pixels[index] = Color.rgb(nr, ng, nb)
        }
        val bright = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return bright.then { applyColorMatrix(it, saturation = 1.08f, contrast = 1.28f, brightness = 6f) }
    }

    /** Adaptive thresholding keeps text readable under uneven paper lighting. */
    private fun adaptiveBlackWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            val sourceRow = (y - 1) * width
            val integralRow = y * stride
            val integralPrevRow = (y - 1) * stride
            for (x in 1..width) {
                val color = pixels[sourceRow + x - 1]
                val gray = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)).toInt()
                rowSum += gray
                integral[integralRow + x] = integral[integralPrevRow + x] + rowSum
            }
        }

        val radius = max(10, min(width, height) / 55)
        val bias = 13
        val outputPixels = IntArray(pixels.size)
        for (y in 0 until height) {
            val y1 = max(0, y - radius)
            val y2 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x1 = max(0, x - radius)
                val x2 = min(width - 1, x + radius)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                    integral[y1 * stride + (x2 + 1)] -
                    integral[(y2 + 1) * stride + x1] +
                    integral[y1 * stride + x1]
                val localMean = (sum / area).toInt()
                val color = pixels[y * width + x]
                val gray = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)).toInt()
                outputPixels[y * width + x] = if (gray < localMean - bias) Color.BLACK else Color.WHITE
            }
        }

        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Runs one pipeline step and frees the input bitmap if the step produced a new one. Pages are
     * up to ~3400px (~45 MB each), so dropping intermediates for GC causes avoidable OOMs.
     */
    private inline fun Bitmap.then(step: (Bitmap) -> Bitmap): Bitmap {
        val result = step(this)
        if (result !== this && !isRecycled) recycle()
        return result
    }

    fun saveBitmapJpeg(bitmap: Bitmap, file: File, quality: Int = 96) {
        saveJpeg(bitmap, file, quality)
    }

    private fun saveJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                "Could not encode ${file.name}"
            }
        }
    }
}

private const val MIN_TEXT_SIZE = 22f
