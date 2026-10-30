package com.example.scanpro.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
        val bitmap = processPage(source, filter, manualRotationDegrees, crop, adjustments, annotations, maxDimension)
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
    ): Bitmap = processPage(source, filter, manualRotationDegrees, crop, adjustments, annotations, maxDimension)

    /**
     * The whole page pipeline: decode, turn and cut out the page as a bitmap, then clean it up and apply the
     * look on one 3-channel OpenCV image (a third less memory than a bitmap, and no copy per step), and
     * finally convert back once for the annotations, which are drawn with the Android canvas.
     */
    private fun processPage(
        source: File,
        filter: PageFilter,
        manualRotationDegrees: Int,
        crop: CropQuad,
        adjustments: PageAdjustments,
        annotations: List<PageAnnotation>,
        maxDimension: Int
    ): Bitmap {
        var bitmap = decodeScaled(source.absolutePath, maxDimension)
            ?: error("Unable to decode ${source.name}")
        val exifRotation = runCatching { ExifInterface(source.absolutePath).rotationDegrees }.getOrDefault(0)
        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        if (!crop.isIdentity()) bitmap = bitmap.then { cropPage(it, crop) }
        val totalRotation = ((manualRotationDegrees % 360) + 360) % 360
        if (totalRotation != 0) bitmap = bitmap.then { rotate(it, totalRotation) }
        bitmap = applyLook(bitmap, filter, adjustments)
        if (annotations.isNotEmpty()) bitmap = bitmap.then { applyAnnotations(it, annotations) }
        return bitmap
    }

    /** Clean-up, filter and tonal adjustments on one OpenCV image; returns [page] itself when there is nothing to do. */
    private fun applyLook(page: Bitmap, filter: PageFilter, adjustments: PageAdjustments): Bitmap {
        val tonal = !adjustments.isTonallyNeutral()
        if (!adjustments.removeFingers && !adjustments.removeShadows && filter == PageFilter.ORIGINAL && !tonal) return page
        var rgb = page.toRgb()
        page.recycle()
        try {
            if (adjustments.removeFingers) rgb = rgb.then { removeFingers(it) }
            if (adjustments.removeShadows) rgb = rgb.then { removeShadows(it) }
            rgb = rgb.then { applyNamedFilter(it, filter) }
            if (tonal) rgb = rgb.then { applyUserAdjustments(it, adjustments.normalized()) }
            return rgb.toBitmap()
        } finally {
            rgb.release()
        }
    }

    /**
     * Small previews of the cropped page under every filter, for the filter strips. [source] (already upright
     * for its EXIF tag) is left as it is; the page is cut out, turned and cleaned up once at preview size, and
     * each filter runs on its own copy of that.
     */
    fun filterPreviews(
        source: Bitmap,
        crop: CropQuad,
        rotationDegrees: Int = 0,
        adjustments: PageAdjustments = PageAdjustments.DEFAULT,
        maxDimension: Int = 360
    ): Map<PageFilter, Bitmap> {
        val scale = min(1f, maxDimension * 2f / max(source.width, source.height))
        var page = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (!crop.isIdentity()) page = page.then { cropPage(it, crop) }
        val fit = min(1f, maxDimension.toFloat() / max(page.width, page.height))
        if (fit < 1f) page = page.then { Bitmap.createScaledBitmap(it, (it.width * fit).toInt().coerceAtLeast(1), (it.height * fit).toInt().coerceAtLeast(1), true) }
        val turn = ((rotationDegrees % 360) + 360) % 360
        if (turn != 0) page = page.then { rotate(it, turn) }
        var base = page.toRgb()
        page.recycle()
        try {
            if (adjustments.removeFingers) base = base.then { removeFingers(it) }
            if (adjustments.removeShadows) base = base.then { removeShadows(it) }
            val tonal = !adjustments.isTonallyNeutral()
            return PageFilter.entries.associateWith { filter ->
                var copy = base.clone().then { applyNamedFilter(it, filter) }
                if (tonal) copy = copy.then { applyUserAdjustments(it, adjustments.normalized()) }
                try {
                    copy.toBitmap()
                } finally {
                    copy.release()
                }
            }
        } finally {
            base.release()
        }
    }

    /** Applies [filter] to a 3-channel RGB image; returns [source] itself for the original look. */
    private fun applyNamedFilter(source: Mat, filter: PageFilter): Mat = when (filter) {
        PageFilter.ORIGINAL -> source
        PageFilter.AUTO -> autoDocumentEnhance(source, colorBoost = false)
        PageFilter.COLOR -> autoDocumentEnhance(source, colorBoost = true)
        PageFilter.GRAY -> enhancedGrayscale(source)
        PageFilter.BW -> adaptiveBlackWhite(source)
        PageFilter.WHITEBOARD -> autoDocumentEnhance(source, colorBoost = true).then { whiteboard(it) }
    }

    private fun applyUserAdjustments(source: Mat, adjustments: PageAdjustments): Mat {
        val a = adjustments.normalized()
        return colorMatrix(source, saturation = a.saturation, contrast = a.contrast, brightness = a.brightness * 92f)
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
        val cropped = bitmap.then { cropPage(it, quad) }
        try {
            saveJpeg(cropped, destination, 98)
        } finally {
            cropped.recycle()
        }
    }


    /** Width divided by height of the photo as shown (after its EXIF rotation), read without decoding pixels. */
    fun orientedAspect(path: String): Float? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val turned = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0) % 180 != 0
        return if (turned) bounds.outHeight.toFloat() / bounds.outWidth else bounds.outWidth.toFloat() / bounds.outHeight
    }

    /** Width divided by height of an image as stored (rendered pages are already upright), read without decoding pixels. */
    fun imageAspect(path: String): Float? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return bounds.outWidth.toFloat() / bounds.outHeight
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

    /** Straightens the outlined page: a curved outline is flattened, a straight one gets a perspective fix. */
    private fun cropPage(source: Bitmap, crop: CropQuad): Bitmap =
        if (crop.isCurved) curvedCrop(source, crop) else perspectiveCrop(source, crop)

    /**
     * Flattens a curved page: each row of the output follows a straight blend between the bent top and bottom
     * edges, sampled with OpenCV in strips so the remap tables stay small however large the page is.
     */
    private fun curvedCrop(source: Bitmap, crop: CropQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        fun pt(p: com.example.scanpro.data.NormalizedPoint) = CurvedPage.Pt(p.x.coerceIn(0f, 1f) * w, p.y.coerceIn(0f, 1f) * h)
        val tl = pt(crop.topLeft); val tr = pt(crop.topRight); val br = pt(crop.bottomRight); val bl = pt(crop.bottomLeft)
        val corners = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        if (!isUsableQuad(corners, w * h)) return source
        fun bowed(a: CurvedPage.Pt, b: CurvedPage.Pt, bow: com.example.scanpro.data.NormalizedPoint?): CurvedPage.Pt {
            val mid = CurvedPage.Pt((a.x + b.x) / 2f + (bow?.x ?: 0f) * w, (a.y + b.y) / 2f + (bow?.y ?: 0f) * h)
            return CurvedPage.control(a, mid, b)
        }
        val topControl = bowed(tl, tr, crop.topBow)
        val bottomControl = bowed(bl, br, crop.bottomBow)
        val leftControl = bowed(tl, bl, crop.leftBow)
        val rightControl = bowed(tr, br, crop.rightBow)
        val topLength = CurvedPage.length(tl, topControl, tr)
        val bottomLength = CurvedPage.length(bl, bottomControl, br)
        val columns = max(topLength, bottomLength).toInt().coerceIn(64, 5000)
        val top = CurvedPage.evenlySpaced(tl, topControl, tr, columns)
        val bottom = CurvedPage.evenlySpaced(bl, bottomControl, br, columns)
        val rows = CurvedPage.outputHeight(
            top, bottom, CurvedPage.length(tl, leftControl, bl), CurvedPage.length(tr, rightControl, br)
        ).coerceIn(64, 7000)
        val left = CurvedPage.evenlySpaced(tl, leftControl, bl, rows)
        val right = CurvedPage.evenlySpaced(tr, rightControl, br, rows)

        val sourceMat = Mat()
        val outputMat = Mat(rows, columns, org.opencv.core.CvType.CV_8UC4)
        val stripRows = 128
        val mapX = Mat(stripRows, columns, org.opencv.core.CvType.CV_32FC1)
        val mapY = Mat(stripRows, columns, org.opencv.core.CvType.CV_32FC1)
        val xs = FloatArray(stripRows * columns)
        val ys = FloatArray(stripRows * columns)
        try {
            Utils.bitmapToMat(source, sourceMat)
            var first = 0
            while (first < rows) {
                val count = min(stripRows, rows - first)
                CurvedPage.fillRows(top, bottom, left, right, first, count, xs, ys)
                val stripX = mapX.rowRange(0, count)
                val stripY = mapY.rowRange(0, count)
                stripX.put(0, 0, xs.copyOf(count * columns))
                stripY.put(0, 0, ys.copyOf(count * columns))
                val target = outputMat.rowRange(first, first + count)
                Imgproc.remap(sourceMat, target, stripX, stripY, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
                stripX.release(); stripY.release(); target.release()
                first += count
            }
            val output = createBitmap(columns, rows)
            Utils.matToBitmap(outputMat, output)
            return output
        } finally {
            sourceMat.release(); outputMat.release(); mapX.release(); mapY.release()
        }
    }

    /**
     * Estimates how the top and bottom edges of the page outlined by [crop] bend, from the page's own edges in
     * [image]. Edges that can't be found reliably stay straight.
     */
    fun estimateCurves(image: Bitmap, crop: CropQuad): CropQuad {
        val scale = min(1f, 900f / max(image.width, image.height))
        val small = if (scale < 1f) image.scale((image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1)) else image
        try {
            val sw = small.width; val sh = small.height
            val pixels = IntArray(sw * sh)
            small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
            val gray = FloatArray(pixels.size) { i ->
                val c = pixels[i]
                0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
            }
            // Light 3x3 box blur so paper texture and text near the edge don't look like the edge itself.
            val smooth = FloatArray(gray.size)
            for (y in 0 until sh) for (x in 0 until sw) {
                var sum = 0f; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val yy = y + dy; val xx = x + dx
                    if (yy in 0 until sh && xx in 0 until sw) { sum += gray[yy * sw + xx]; n++ }
                }
                smooth[y * sw + x] = sum / n
            }
            val intensity: (Float, Float) -> Float = { fx, fy ->
                val x = fx.coerceIn(0f, sw - 1f); val y = fy.coerceIn(0f, sh - 1f)
                val x0 = x.toInt(); val y0 = y.toInt()
                val x1 = min(x0 + 1, sw - 1); val y1 = min(y0 + 1, sh - 1)
                val ax = x - x0; val ay = y - y0
                val top = smooth[y0 * sw + x0] * (1 - ax) + smooth[y0 * sw + x1] * ax
                val bottom = smooth[y1 * sw + x0] * (1 - ax) + smooth[y1 * sw + x1] * ax
                top * (1 - ay) + bottom * ay
            }
            fun pt(p: com.example.scanpro.data.NormalizedPoint) = CurvedPage.Pt(p.x * sw, p.y * sh)
            val tl = pt(crop.topLeft); val tr = pt(crop.topRight); val br = pt(crop.bottomRight); val bl = pt(crop.bottomLeft)
            val topMid = (tl + tr) * 0.5f
            val bottomMid = (bl + br) * 0.5f
            val pageHeight = (bottomMid - topMid).length()
            val search = pageHeight * 0.12f
            fun toBow(p: CurvedPage.Pt?) = p?.let { com.example.scanpro.data.NormalizedPoint(it.x / sw, it.y / sh) }
            val leftMid = (tl + bl) * 0.5f
            val rightMid = (tr + br) * 0.5f
            val sideSearch = (rightMid - leftMid).length() * 0.12f
            val topBow = CurvedPage.estimateBow(tl, tr, bottomMid - topMid, search, intensity)
            val bottomBow = CurvedPage.estimateBow(bl, br, topMid - bottomMid, search, intensity)
            val leftBow = CurvedPage.estimateBow(tl, bl, rightMid - leftMid, sideSearch, intensity)
            val rightBow = CurvedPage.estimateBow(tr, br, leftMid - rightMid, sideSearch, intensity)
            return crop.copy(
                topBow = toBow(topBow) ?: crop.topBow,
                bottomBow = toBow(bottomBow) ?: crop.bottomBow,
                leftBow = toBow(leftBow) ?: crop.leftBow,
                rightBow = toBow(rightBow) ?: crop.rightBow
            )
        } finally {
            if (small !== image) small.recycle()
        }
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
        // Erasing comes first: it removes what was printed, never text or signatures added on top.
        annotations.filter { it.type == AnnotationType.ERASE }.forEach { eraseStrokes(output, it) }
        val canvas = Canvas(output)
        annotations.filter { it.type != AnnotationType.ERASE }.forEach { item ->
            val left = item.x.coerceIn(0f, 1f) * output.width
            val top = item.y.coerceIn(0f, 1f) * output.height
            val width = item.width.coerceIn(0.01f, 1f) * output.width
            val height = item.height.coerceIn(0.01f, 1f) * output.height
            // The box is the mark-up's footprint on the page. Content that was turned a quarter (the page was
            // rotated after it was added) is laid out in the box turned back, then rotated into place.
            val box = RectF(left, top, (left + width).coerceAtMost(output.width.toFloat()), (top + height).coerceAtMost(output.height.toFloat()))
            val turned = quarterTurned(item.rotationDegrees)
            val rect = if (turned) RectF(
                box.centerX() - box.height() / 2f, box.centerY() - box.width() / 2f,
                box.centerX() + box.height() / 2f, box.centerY() + box.width() / 2f
            ) else box
            canvas.withRotation(item.rotationDegrees, box.centerX(), box.centerY()) {
            when (item.type) {
                AnnotationType.REDACT -> canvas.drawRect(rect, Paint().apply { color = Color.BLACK })
                AnnotationType.HIGHLIGHT -> canvas.drawRect(rect, Paint().apply {
                    color = Color.argb((item.opacity.coerceIn(0.05f, 1f) * 120).toInt(), 255, 235, 59)
                })
                AnnotationType.ERASE -> Unit
                AnnotationType.TEXT -> if (item.background != null) {
                    drawReplacementText(output, canvas, item, rect, box, turned)
                } else {
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

    /**
     * Replaced words: the old words are filled in from the paper around them (as the eraser does), then the new
     * text is drawn on one line at the height of the words it replaces, narrowed only if it would run past the
     * box. The box carries a margin of 15% of its height on each side (see DocumentRepository.replaceText).
     */
    private fun drawReplacementText(output: Bitmap, canvas: Canvas, item: PageAnnotation, rect: RectF, box: RectF, turned: Boolean) {
        val pad = rect.height() * 0.12f
        // The paper is filled in on the page itself (not through the rotated canvas), so the covered area is the
        // box's footprint, padded above and below the words: sideways when the words run down the page.
        val cover = if (turned) RectF(box.left - pad, box.top, box.right + pad, box.bottom)
        else RectF(box.left, box.top - pad, box.right, box.bottom + pad)
        val filled = runCatching {
            inpaintPatch(output, cover, maskFor = { mask, scale, origin ->
                Imgproc.rectangle(
                    mask,
                    org.opencv.core.Point(((cover.left - origin.x) * scale).toDouble(), ((cover.top - origin.y) * scale).toDouble()),
                    org.opencv.core.Point(((cover.right - origin.x) * scale).toDouble(), ((cover.bottom - origin.y) * scale).toDouble()),
                    org.opencv.core.Scalar(255.0), -1
                )
            }) { target, paint -> target.drawRect(cover, paint.apply { style = Paint.Style.FILL }) }
        }.isSuccess
        if (!filled) Canvas(output).drawRect(cover, Paint().apply { color = item.background ?: Color.WHITE })

        val text = item.text.take(400).replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return
        val margin = rect.height() * 0.15f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(Color.red(item.color), Color.green(item.color), Color.blue(item.color))
            textSize = 100f
        }
        // OCR boxes run from the tallest letter to the lowest tail, so size "Hgy" to the box: the new words then
        // stand as tall as the ones they replace.
        val reference = android.graphics.Rect().also { paint.getTextBounds("Hgy", 0, 3, it) }
        paint.textSize = 100f * rect.height() / reference.height().coerceAtLeast(1)
        val width = rect.width() - 2 * margin
        val measured = paint.measureText(text)
        if (measured > width && width > 0) paint.textSize = max(6f, paint.textSize * width / measured)
        val tail = android.graphics.Rect().also { paint.getTextBounds("Hgy", 0, 3, it) }.bottom
        canvas.drawText(text, rect.left + margin, rect.bottom - tail, paint)
    }

    /**
     * Paints out an eraser annotation's strokes in place: the area under the brush is filled in from the paper
     * around it, so stains, pen marks and handwriting disappear without leaving a patch.
     */
    private fun eraseStrokes(output: Bitmap, item: PageAnnotation) {
        val w = output.width
        val h = output.height
        val brush = max(2f, item.brushSize * w)
        val strokes = item.strokes.map { stroke ->
            stroke.map { p -> android.graphics.PointF((item.x + p.x * item.width) * w, (item.y + p.y * item.height) * h) }
        }.filter { it.isNotEmpty() }
        if (strokes.isEmpty()) return
        val all = strokes.flatten()
        val area = RectF(all.minOf { it.x } - brush, all.minOf { it.y } - brush, all.maxOf { it.x } + brush, all.maxOf { it.y } + brush)
        inpaintPatch(output, area, maskFor = { mask, scale, origin ->
            // A little wider than the brush so the fill starts from clean paper at the edges.
            val thickness = max(1, (brush * scale * 1.2f).toInt())
            strokes.forEach { stroke ->
                val points = stroke.map { org.opencv.core.Point(((it.x - origin.x) * scale).toDouble(), ((it.y - origin.y) * scale).toDouble()) }
                if (points.size == 1) Imgproc.circle(mask, points[0], max(1, thickness / 2), org.opencv.core.Scalar(255.0), -1)
                else points.zipWithNext { a, b -> Imgproc.line(mask, a, b, org.opencv.core.Scalar(255.0), thickness) }
            }
        }) { canvas, paint ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = brush
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            strokes.forEach { stroke ->
                if (stroke.size == 1) {
                    canvas.drawPoint(stroke[0].x, stroke[0].y, paint)
                } else {
                    val path = android.graphics.Path().apply {
                        moveTo(stroke[0].x, stroke[0].y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    /**
     * Fills in the pixels [maskFor] marks inside [area] from their surroundings (OpenCV inpainting), then hands
     * [copyBack] a paint holding the filled image so it can copy back just the marked shape with soft edges.
     * Large areas are worked on at a reduced size. The mask is drawn in patch pixels: page point p lands at
     * (p - origin) * scale.
     */
    private fun inpaintPatch(
        output: Bitmap,
        area: RectF,
        maskFor: (mask: Mat, scale: Float, origin: android.graphics.PointF) -> Unit,
        copyBack: (Canvas, Paint) -> Unit
    ) {
        val w = output.width
        val h = output.height
        val margin = max(8f, min(area.width(), area.height()) * 0.5f)
        val left = (area.left - margin).toInt().coerceIn(0, w - 1)
        val top = (area.top - margin).toInt().coerceIn(0, h - 1)
        val right = (area.right + margin).toInt().coerceIn(left + 1, w)
        val bottom = (area.bottom + margin).toInt().coerceIn(top + 1, h)
        val roiW = right - left
        val roiH = bottom - top
        if (roiW < 2 || roiH < 2) return
        val scale = min(1f, 1200f / max(roiW, roiH))
        val sw = max(2, (roiW * scale).toInt())
        val sh = max(2, (roiH * scale).toInt())

        val region = Bitmap.createBitmap(output, left, top, roiW, roiH)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(region, sw, sh, true) else region
        val rgba = Mat()
        val rgb = Mat()
        val mask = Mat.zeros(sh, sw, org.opencv.core.CvType.CV_8UC1)
        val filled = Mat()
        val filledRgba = Mat()
        var patch: Bitmap? = null
        var patchFull: Bitmap? = null
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            maskFor(mask, scale, android.graphics.PointF(left.toFloat(), top.toFloat()))
            val radius = max(3.0, min(area.width(), area.height()) * scale / 4.0)
            org.opencv.photo.Photo.inpaint(rgb, mask, filled, radius, org.opencv.photo.Photo.INPAINT_TELEA)
            Imgproc.cvtColor(filled, filledRgba, Imgproc.COLOR_RGB2RGBA)
            patch = createBitmap(sw, sh).also { Utils.matToBitmap(filledRgba, it) }
            patchFull = if (scale < 1f) Bitmap.createScaledBitmap(patch, roiW, roiH, true) else patch
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.BitmapShader(patchFull, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                    .apply { setLocalMatrix(Matrix().apply { setTranslate(left.toFloat(), top.toFloat()) }) }
            }
            copyBack(Canvas(output), paint)
        } finally {
            rgba.release(); rgb.release(); mask.release(); filled.release(); filledRgba.release()
            if (patchFull != null && patchFull !== patch) patchFull.recycle()
            patch?.recycle()
            if (small !== region) small.recycle()
            if (region !== output) region.recycle()
        }
    }

    /** True when [degrees] is nearer a quarter turn than upright or upside down. */
    private fun quarterTurned(degrees: Float): Boolean {
        val turn = ((degrees % 180f) + 180f) % 180f
        return turn in 45f..135f
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
    private fun autoDocumentEnhance(rgb: Mat, colorBoost: Boolean): Mat {
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
    private fun removeShadows(rgb: Mat): Mat {
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
    private fun removeFingers(rgb: Mat): Mat {
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

    private fun enhancedGrayscale(rgb: Mat): Mat {
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
    private fun colorMatrix(rgb: Mat, saturation: Float, contrast: Float, brightness: Float): Mat {
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
    private fun whiteboard(rgb: Mat): Mat {
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
    private fun adaptiveBlackWhite(rgb: Mat): Mat {
        val gray = Mat()
        val bw = Mat()
        val out = Mat()
        try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            // Black where a pixel is darker than its neighbourhood's mean by more than the bias.
            val radius = max(10, min(rgb.cols(), rgb.rows()) / 55)
            Imgproc.adaptiveThreshold(gray, bw, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 2 * radius + 1, 13.0)
            Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGB)
            return out
        } catch (error: Throwable) {
            out.release()
            throw error
        } finally {
            gray.release(); bw.release()
        }
    }

    /** The bitmap's pixels as a 3-channel RGB image (the bitmap is opaque, so alpha carries nothing). */
    private fun Bitmap.toRgb(): Mat {
        val rgba = Mat()
        val rgb = Mat()
        try {
            Utils.bitmapToMat(this, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            return rgb
        } finally {
            rgba.release()
        }
    }

    private fun Mat.toBitmap(): Bitmap {
        val output = createBitmap(cols(), rows())
        Utils.matToBitmap(this, output)
        return output
    }

    /** Runs one step on an OpenCV image and frees the input when the step produced a new one. */
    private inline fun Mat.then(step: (Mat) -> Mat): Mat {
        val result = step(this)
        if (result !== this) release()
        return result
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
private const val SHADOW_ESTIMATE_SIZE = 600.0
private const val SHADOW_SIGMA = 30.0
private const val PAPER_MAX_SATURATION = 59.0
private const val FINGER_ESTIMATE_SIZE = 800.0
