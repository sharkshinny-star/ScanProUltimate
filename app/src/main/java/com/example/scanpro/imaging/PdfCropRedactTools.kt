package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Crop PDF pages (F172). Each page's crop box is tightened, either to its content (auto-trim, with a little padding)
 * or by margins the user gives. Only the crop box changes: the page's content, text layer and size on disk are kept,
 * and any viewer shows the cropped page. A page is measured as it is displayed (after its /Rotate), so "top" means the
 * top the user sees; [displayRectToUserSpace] turns that back into the page's own coordinates.
 */
object PdfCropTools {
    enum class Mode { AUTO, MARGINS }

    /** Padding is for AUTO (around the content found); the four margins are for MARGINS. All in millimetres. */
    data class CropOptions(
        val mode: Mode = Mode.AUTO,
        val paddingMm: Float = 3f,
        val topMm: Float = 0f,
        val bottomMm: Float = 0f,
        val leftMm: Float = 0f,
        val rightMm: Float = 0f
    )

    /** What a page is before cropping: its crop box in its own coordinates, and how it is turned for display. */
    internal class PageShape(val cropBox: PDRectangle, val rotation: Int) {
        val displayWidth: Float get() = if (rotation % 180 == 0) cropBox.width else cropBox.height
        val displayHeight: Float get() = if (rotation % 180 == 0) cropBox.height else cropBox.width
    }

    const val PT_PER_MM = 72f / 25.4f
    /** Pages are drawn this big (long side, pixels) to find their content: enough for a 1 mm margin at A4. */
    internal const val AUTO_RENDER_SIDE = 600
    /** A pixel darker than this (0..255 luma) counts as content. Paper photographed a little grey still passes. */
    internal const val CONTENT_LUMA = 225
    /** A crop box narrower or shorter than this (points) would be a mistake; the page is then left as it is. */
    private const val MIN_SIDE_PT = 36f

    /**
     * A rectangle given on the displayed page ([left], [top], [right], [bottom] in points from the display's top-left
     * corner) as a rectangle in the page's own coordinate space, inside [shape]'s crop box. The display is the page
     * turned clockwise by [PageShape.rotation] degrees: with 90, the page's left edge runs along the display's top.
     */
    internal fun displayRectToUserSpace(shape: PageShape, left: Float, top: Float, right: Float, bottom: Float): PDRectangle {
        val box = shape.cropBox
        val x0 = box.lowerLeftX
        val y0 = box.lowerLeftY
        val w = box.width
        val h = box.height
        fun user(dx: Float, dy: Float): Pair<Float, Float> = when (((shape.rotation % 360) + 360) % 360) {
            90 -> (x0 + dy) to (y0 + dx)
            180 -> (x0 + w - dx) to (y0 + dy)
            270 -> (x0 + w - dy) to (y0 + h - dx)
            else -> (x0 + dx) to (y0 + h - dy)
        }
        val corners = listOf(user(left, top), user(right, top), user(right, bottom), user(left, bottom))
        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }
        return PDRectangle(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * The smallest rectangle (display points, top-left origin) holding every pixel of [bitmap] darker than
     * [CONTENT_LUMA], grown by [paddingPt] and clamped to the page; null for a blank page.
     */
    internal fun contentBounds(bitmap: Bitmap, displayWidth: Float, displayHeight: Float, paddingPt: Float): RectF? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = pixels[row + x]
                val luma = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (luma < CONTENT_LUMA) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        val sx = displayWidth / w
        val sy = displayHeight / h
        return RectF(
            (minX * sx - paddingPt).coerceAtLeast(0f),
            (minY * sy - paddingPt).coerceAtLeast(0f),
            ((maxX + 1) * sx + paddingPt).coerceAtMost(displayWidth),
            ((maxY + 1) * sy + paddingPt).coerceAtMost(displayHeight)
        )
    }

    /**
     * The new crop box of every page, or null where the page is left alone (blank, or the result would be too small).
     * [draw] renders page [i] as displayed into the bitmap given, white background; it is only called in AUTO mode.
     */
    internal suspend fun cropBoxes(
        pageCount: Int,
        shape: (Int) -> PageShape,
        draw: (index: Int, target: Bitmap) -> Unit,
        options: CropOptions,
        onPage: (Int) -> Unit = {}
    ): List<PDRectangle?> {
        var bitmap: Bitmap? = null
        try {
            return List(pageCount) { i ->
                if (!coroutineContext.isActive) throw InterruptedException("Crop cancelled")
                onPage(i)
                val page = shape(i)
                val dw = page.displayWidth
                val dh = page.displayHeight
                val display: RectF? = when (options.mode) {
                    Mode.MARGINS -> RectF(
                        options.leftMm * PT_PER_MM,
                        options.topMm * PT_PER_MM,
                        dw - options.rightMm * PT_PER_MM,
                        dh - options.bottomMm * PT_PER_MM
                    )
                    Mode.AUTO -> {
                        val scale = AUTO_RENDER_SIDE / max(dw, dh).coerceAtLeast(1f)
                        val w = (dw * scale).toInt().coerceAtLeast(1)
                        val h = (dh * scale).toInt().coerceAtLeast(1)
                        val canvas = bitmap?.takeIf { it.width == w && it.height == h }
                            ?: createBitmap(w, h).also { bitmap?.recycle(); bitmap = it }
                        canvas.eraseColor(Color.WHITE)
                        draw(i, canvas)
                        contentBounds(canvas, dw, dh, options.paddingMm * PT_PER_MM)
                    }
                }
                if (display == null || display.width() < MIN_SIDE_PT || display.height() < MIN_SIDE_PT) return@List null
                val box = displayRectToUserSpace(page, display.left, display.top, display.right, display.bottom)
                // Unchanged within a point: not worth marking the page as cropped.
                val old = page.cropBox
                val same = kotlin.math.abs(box.lowerLeftX - old.lowerLeftX) < 1f && kotlin.math.abs(box.lowerLeftY - old.lowerLeftY) < 1f &&
                    kotlin.math.abs(box.width - old.width) < 1f && kotlin.math.abs(box.height - old.height) < 1f
                if (same) null else box
            }
        } finally {
            bitmap?.recycle()
        }
    }

    /** Sets each page's crop box from [boxes] (null leaves the page as it is) and returns how many changed. */
    internal fun applyCropBoxes(doc: PDDocument, boxes: List<PDRectangle?>): Int {
        var changed = 0
        boxes.forEachIndexed { i, box ->
            if (box != null && i < doc.numberOfPages) {
                doc.getPage(i).cropBox = box
                changed++
            }
        }
        return changed
    }

    suspend fun cropPdf(
        context: Context,
        inputUri: Uri,
        options: CropOptions,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "crop_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.openPdf(temp).use { doc ->
                val total = doc.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                val shapes = List(total) { i -> doc.getPage(i).let { PageShape(it.cropBox, it.rotation) } }
                val boxes = if (options.mode == Mode.MARGINS) {
                    cropBoxes(total, { shapes[it] }, { _, _ -> }, options) { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                } else {
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                            var open: PdfRenderer.Page? = null
                            fun page(i: Int): PdfRenderer.Page = open?.takeIf { it.index == i } ?: renderer.openPage(i).also { open?.close(); open = it }
                            try {
                                cropBoxes(
                                    pageCount = min(total, renderer.pageCount),
                                    shape = { shapes[it] },
                                    draw = { i, target -> page(i).render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) },
                                    options = options
                                ) { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                            } finally {
                                open?.close()
                            }
                        }
                    }
                }
                val changed = applyCropBoxes(doc, boxes)
                userRequire(changed > 0, R.string.tools_crop_pdf_err_nothing)
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = total,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.plural(R.plurals.tools_crop_pdf_done, changed, changed)
                )
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }
}

/**
 * Redact text (F172): every occurrence of the given words is blacked out, and each page with a match is redrawn as a
 * picture, so the words are gone from the file rather than hidden under a box. Pages without a match keep their text
 * layer. Make searchable can give a redacted page its text back, without the redacted words.
 */
object PdfRedactTools {
    /** Rectangles are in display points from the page's top-left corner, as [TextPosition]'s adjusted coordinates are. */
    internal class Match(val page: Int, val rect: RectF)

    internal const val REDACT_DPI = 200
    private const val REDACT_MAX_SIDE = 3500
    private const val REDACT_JPEG_QUALITY = 85
    /** Boxes grow by this much (points) on every side, so the glyphs' anti-aliased fringe goes too. */
    private const val BOX_PADDING_PT = 1.5f

    /** Every occurrence of every term, page by page. Terms are trimmed; empty ones are ignored. */
    internal fun findMatches(doc: PDDocument, terms: List<String>, matchCase: Boolean): List<Match> {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return emptyList()
        val matches = ArrayList<Match>()
        for (index in 0 until doc.numberOfPages) {
            val text = StringBuilder()
            val positions = ArrayList<TextPosition?>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(string: String, textPositions: List<TextPosition>) {
                    // One position per character where the stripper gives them; a ligature or a missing one is padded.
                    for (k in string.indices) {
                        text.append(string[k])
                        positions.add(textPositions.getOrNull(k))
                    }
                }

                override fun writeLineSeparator() { text.append('\n'); positions.add(null) }
                override fun writeWordSeparator() { text.append(' '); positions.add(null) }
                override fun writeParagraphSeparator() { text.append('\n'); positions.add(null) }
            }
            stripper.sortByPosition = true
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            stripper.getText(doc)
            val haystack = if (matchCase) text.toString() else text.toString().lowercase()
            for (term in wanted) {
                val needle = if (matchCase) term else term.lowercase()
                var from = haystack.indexOf(needle)
                while (from >= 0) {
                    val rect = unionOf(positions, from, from + needle.length)
                    if (rect != null) matches += Match(index, rect)
                    from = haystack.indexOf(needle, from + needle.length)
                }
            }
        }
        return matches
    }

    private fun unionOf(positions: List<TextPosition?>, from: Int, to: Int): RectF? {
        var rect: RectF? = null
        for (k in from until min(to, positions.size)) {
            val p = positions[k] ?: continue
            val glyph = RectF(p.xDirAdj, p.yDirAdj - p.heightDir, p.xDirAdj + p.widthDirAdj, p.yDirAdj)
            rect = rect?.apply { union(glyph) } ?: glyph
        }
        return rect?.apply { inset(-BOX_PADDING_PT, -BOX_PADDING_PT) }
    }

    /**
     * Writes [outputFile]: pages in [matches] are drawn by [draw] (as displayed, white background), the match boxes
     * painted black, and stored as pictures; every other page is imported from [source] unchanged.
     */
    internal suspend fun writeRedacted(
        source: PDDocument,
        matches: List<Match>,
        pageSize: (Int) -> Pair<Float, Float>,
        draw: (index: Int, target: Bitmap) -> Unit,
        outputFile: File,
        onPage: (Int) -> Unit = {}
    ): Int {
        val byPage = matches.groupBy { it.page }
        var bitmap: Bitmap? = null
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
                val buffer = ByteArrayOutputStream()
                for (i in 0 until source.numberOfPages) {
                    if (!coroutineContext.isActive) throw InterruptedException("Redact cancelled")
                    onPage(i)
                    val boxes = byPage[i]
                    if (boxes == null) {
                        out.importPage(source.getPage(i))
                        continue
                    }
                    val (widthPt, heightPt) = pageSize(i)
                    val scale = min(REDACT_DPI / 72f, REDACT_MAX_SIDE / max(widthPt, heightPt).coerceAtLeast(1f))
                    val w = (widthPt * scale).toInt().coerceAtLeast(1)
                    val h = (heightPt * scale).toInt().coerceAtLeast(1)
                    val canvas = bitmap?.takeIf { it.width == w && it.height == h }
                        ?: createBitmap(w, h).also { bitmap?.recycle(); bitmap = it }
                    canvas.eraseColor(Color.WHITE)
                    draw(i, canvas)
                    val painter = Canvas(canvas)
                    boxes.forEach { m ->
                        painter.drawRect(m.rect.left * scale, m.rect.top * scale, m.rect.right * scale, m.rect.bottom * scale, paint)
                    }
                    buffer.reset()
                    canvas.compress(Bitmap.CompressFormat.JPEG, REDACT_JPEG_QUALITY, buffer)
                    val image = JPEGFactory.createFromByteArray(out, buffer.toByteArray())
                    val page = PDPage(PDRectangle(widthPt, heightPt))
                    out.addPage(page)
                    PDPageContentStream(out, page).use { it.drawImage(image, 0f, 0f, widthPt, heightPt) }
                }
                outputFile.parentFile?.mkdirs()
                out.save(outputFile)
            }
            return byPage.size
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            bitmap?.recycle()
        }
    }

    /** A box drawn by hand on page [page] (0-based): edges as shares (0..1) of the page as displayed, top-left origin. */
    class DrawnBox(val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** A drawn box narrower or shorter than this share of the page is a slip of the finger, not a redaction. */
    internal const val MIN_BOX_SHARE = 0.005f

    /**
     * The drawn boxes as page matches in display points. Boxes on pages that do not exist, and boxes too small to
     * mean anything, are dropped; edges are clamped to the page and put in order, so an upward drag still works.
     */
    internal fun boxesToMatches(boxes: List<DrawnBox>, pageCount: Int, pageSize: (Int) -> Pair<Float, Float>): List<Match> =
        boxes.filter { it.page in 0 until pageCount }.mapNotNull { box ->
            val left = minOf(box.left, box.right).coerceIn(0f, 1f)
            val right = maxOf(box.left, box.right).coerceIn(0f, 1f)
            val top = minOf(box.top, box.bottom).coerceIn(0f, 1f)
            val bottom = maxOf(box.top, box.bottom).coerceIn(0f, 1f)
            if (right - left < MIN_BOX_SHARE || bottom - top < MIN_BOX_SHARE) return@mapNotNull null
            val (width, height) = pageSize(box.page)
            Match(box.page, RectF(left * width, top * height, right * width, bottom * height))
        }

    /** Redact by drawn boxes (F174): the same black-out-and-rasterise as [redact], with hand-drawn areas. */
    suspend fun redactBoxes(
        context: Context,
        inputUri: Uri,
        boxes: List<DrawnBox>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "redactb_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.openPdf(temp).use { doc ->
                val total = doc.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                val (matches, pages) = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                        var open: PdfRenderer.Page? = null
                        fun page(i: Int): PdfRenderer.Page = open?.takeIf { it.index == i } ?: renderer.openPage(i).also { open?.close(); open = it }
                        try {
                            val matches = boxesToMatches(boxes, min(total, renderer.pageCount)) { i ->
                                page(i).let { it.width.toFloat() to it.height.toFloat() }
                            }
                            userRequire(matches.isNotEmpty(), R.string.tools_redact_box_err_none)
                            matches to writeRedacted(
                                source = doc,
                                matches = matches,
                                pageSize = { i -> page(i).let { it.width.toFloat() to it.height.toFloat() } },
                                draw = { i, target -> page(i).render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) },
                                outputFile = outputFile
                            ) { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                        } finally {
                            open?.close()
                        }
                    }
                }
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = total,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.get(R.string.tools_redact_box_done, matches.size, pages)
                )
            }
        } finally {
            temp.delete()
        }
    }

    suspend fun redact(
        context: Context,
        inputUri: Uri,
        terms: List<String>,
        matchCase: Boolean,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "redact_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.openPdf(temp).use { doc ->
                val total = doc.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                onProgress(0, total, AppStrings.get(R.string.tools_redact_progress_searching))
                val matches = findMatches(doc, terms, matchCase)
                if (matches.isEmpty()) userError(R.string.tools_redact_err_no_matches)
                val pages = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                        var open: PdfRenderer.Page? = null
                        fun page(i: Int): PdfRenderer.Page = open?.takeIf { it.index == i } ?: renderer.openPage(i).also { open?.close(); open = it }
                        try {
                            writeRedacted(
                                source = doc,
                                matches = matches,
                                pageSize = { i -> page(i).let { it.width.toFloat() to it.height.toFloat() } },
                                draw = { i, target -> page(i).render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) },
                                outputFile = outputFile
                            ) { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                        } finally {
                            open?.close()
                        }
                    }
                }
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = total,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.get(R.string.tools_redact_done, matches.size, pages)
                )
            }
        } finally {
            temp.delete()
        }
    }
}
