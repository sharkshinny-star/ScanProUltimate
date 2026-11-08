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
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire

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
        try {
            renderPageAt(source, destination, filter, manualRotationDegrees, crop, adjustments, annotations, maxDimension)
        } catch (memory: OutOfMemoryError) {
            // A page the device can't hold at full size is still saved, at half the resolution, rather than lost.
            System.gc()
            renderPageAt(source, destination, filter, manualRotationDegrees, crop, adjustments, annotations, maxDimension / 2)
        }
    }

    private fun renderPageAt(
        source: File,
        destination: File,
        filter: PageFilter,
        manualRotationDegrees: Int,
        crop: CropQuad,
        adjustments: PageAdjustments,
        annotations: List<PageAnnotation>,
        maxDimension: Int
    ) {
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
            ?: userError(R.string.err_could_not_decode_image, source.name)
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
        if (!MatFilters.changesPixels(filter, adjustments)) return page
        val rgb = page.toRgb()
        page.recycle()
        val styled = MatFilters.look(rgb, filter, adjustments)
        try {
            return styled.toBitmap()
        } finally {
            styled.release()
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
            source.scale((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1))
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (!crop.isIdentity()) page = page.then { cropPage(it, crop) }
        val fit = min(1f, maxDimension.toFloat() / max(page.width, page.height))
        if (fit < 1f) page = page.then { it.scale((it.width * fit).toInt().coerceAtLeast(1), (it.height * fit).toInt().coerceAtLeast(1)) }
        val turn = ((rotationDegrees % 360) + 360) % 360
        if (turn != 0) page = page.then { rotate(it, turn) }
        var base = page.toRgb()
        page.recycle()
        try {
            base = MatFilters.cleanUp(base, adjustments)
            return PageFilter.entries.associateWith { filter ->
                val styled = MatFilters.style(base.clone(), filter, adjustments)
                try {
                    styled.toBitmap()
                } finally {
                    styled.release()
                }
            }
        } finally {
            base.release()
        }
    }

    /**
     * [filter] applied to the whole, uncropped [source], at most [maxDimension] on its long side. For the review
     * screen's crop editor: the outline is drawn in the photo's own coordinates, so the image under it must not be
     * cropped, but it should still look like the page will.
     */
    fun filterUncropped(
        source: Bitmap,
        filter: PageFilter,
        adjustments: PageAdjustments = PageAdjustments.DEFAULT,
        maxDimension: Int = 1400
    ): Bitmap {
        val fit = min(1f, maxDimension.toFloat() / max(source.width, source.height))
        val page = if (fit < 1f) {
            source.scale((source.width * fit).toInt().coerceAtLeast(1), (source.height * fit).toInt().coerceAtLeast(1))
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        var base = page.toRgb()
        page.recycle()
        try {
            base = MatFilters.cleanUp(base, adjustments)
            val styled = MatFilters.style(base.clone(), filter, adjustments)
            try {
                return styled.toBitmap()
            } finally {
                styled.release()
            }
        } finally {
            base.release()
        }
    }

    fun saveSignature(bitmap: Bitmap, destination: File) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            userRequire(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), R.string.err_could_not_save_signature)
        }
    }

    fun cropSourceToNewFile(source: File, destination: File, quad: CropQuad) {
        val cropped = cropSourceToBitmap(source, quad, 4200)
        try {
            saveJpeg(cropped, destination, 98)
        } finally {
            cropped.recycle()
        }
    }

    /** [source] decoded to at most [maxDimension] px, turned upright and cut along [quad]; the caller recycles it. */
    fun cropSourceToBitmap(source: File, quad: CropQuad, maxDimension: Int): Bitmap {
        var bitmap = decodeScaled(source.absolutePath, maxDimension) ?: userError(R.string.err_could_not_decode_source)
        val exifRotation = runCatching { ExifInterface(source.absolutePath).rotationDegrees }.getOrDefault(0)
        if (exifRotation != 0) bitmap = bitmap.then { rotate(it, exifRotation) }
        return bitmap.then { cropPage(it, quad) }
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
        fun pt(p: NormalizedPoint) = CurvedPage.Pt(p.x.coerceIn(0f, 1f) * w, p.y.coerceIn(0f, 1f) * h)
        val tl = pt(crop.topLeft); val tr = pt(crop.topRight); val br = pt(crop.bottomRight); val bl = pt(crop.bottomLeft)
        val corners = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        if (!isUsableQuad(corners, w * h)) return source
        fun bowed(a: CurvedPage.Pt, b: CurvedPage.Pt, bow: NormalizedPoint?): CurvedPage.Pt {
            val mid = CurvedPage.Pt((a.x + b.x) / 2f + (bow?.x ?: 0f) * w, (a.y + b.y) / 2f + (bow?.y ?: 0f) * h)
            return CurvedPage.control(a, mid, b)
        }
        val topControl = bowed(tl, tr, crop.topBow)
        val bottomControl = bowed(bl, br, crop.bottomBow)
        val leftControl = bowed(tl, bl, crop.leftBow)
        val rightControl = bowed(tr, br, crop.rightBow)
        val topLength = CurvedPage.length(tl, topControl, tr)
        val bottomLength = CurvedPage.length(bl, bottomControl, br)
        // Never larger than the photo itself: the extra pixels would hold no detail and cost hundreds of megabytes.
        val columns = max(topLength, bottomLength).toInt().coerceIn(64, source.width.coerceAtLeast(64))
        val top = CurvedPage.evenlySpaced(tl, topControl, tr, columns)
        val bottom = CurvedPage.evenlySpaced(bl, bottomControl, br, columns)
        val rows = CurvedPage.outputHeight(
            top, bottom, CurvedPage.length(tl, leftControl, bl), CurvedPage.length(tr, rightControl, br)
        ).coerceIn(64, source.height.coerceAtLeast(64))
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
            fun pt(p: NormalizedPoint) = CurvedPage.Pt(p.x * sw, p.y * sh)
            val tl = pt(crop.topLeft); val tr = pt(crop.topRight); val br = pt(crop.bottomRight); val bl = pt(crop.bottomLeft)
            val topMid = (tl + tr) * 0.5f
            val bottomMid = (bl + br) * 0.5f
            val pageHeight = (bottomMid - topMid).length()
            val search = pageHeight * 0.12f
            fun toBow(p: CurvedPage.Pt?) = p?.let { NormalizedPoint(it.x / sw, it.y / sh) }
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

    /** Pen strokes inside [rect] (the mark-up's box, laid out unturned), [pageWidth] px wide page for the pen width. */
    private fun drawInk(canvas: Canvas, item: PageAnnotation, rect: RectF, pageWidth: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                (255 * item.opacity.coerceIn(0.05f, 1f)).toInt(),
                Color.red(item.color), Color.green(item.color), Color.blue(item.color)
            )
            style = Paint.Style.STROKE
            strokeWidth = (item.brushSize * pageWidth).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        item.strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            fun px(p: NormalizedPoint) = rect.left + p.x * rect.width() to rect.top + p.y * rect.height()
            if (stroke.size == 1) {
                val (x, y) = px(stroke[0])
                canvas.drawPoint(x, y, paint)
            } else {
                val path = android.graphics.Path()
                stroke.forEachIndexed { i, p -> val (x, y) = px(p); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                canvas.drawPath(path, paint)
            }
        }
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
                // Pen width is a fraction of the page's width when it was drawn; a quarter turn since swapped the sides.
                AnnotationType.INK -> drawInk(canvas, item, rect, if (turned) output.height else output.width)
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
        val small = if (scale < 1f) region.scale(sw, sh) else region
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
            patchFull = if (scale < 1f) patch.scale(roiW, roiH) else patch
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
            userRequire(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output), R.string.err_could_not_save_image, file.name)
        }
    }
}

private const val MIN_TEXT_SIZE = 22f
