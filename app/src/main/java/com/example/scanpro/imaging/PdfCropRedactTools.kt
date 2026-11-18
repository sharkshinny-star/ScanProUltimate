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
import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    internal const val MIN_SIDE_PT = 36f

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
     * A rectangle in page coordinates relative to the crop box's lower-left corner ([x0], [y0], [x1], [y1]; y up) as
     * display points from the top-left corner of the page shown turned by [rotation]: the inverse of
     * [displayRectToUserSpace]. [width] and [height] are the crop box's own (unturned) size.
     */
    internal fun userRectToDisplay(x0: Float, y0: Float, x1: Float, y1: Float, rotation: Int, width: Float, height: Float): RectF {
        val turn = ((rotation % 360) + 360) % 360
        fun dx(ux: Float, uy: Float): Float = when (turn) { 90 -> uy; 180 -> width - ux; 270 -> height - uy; else -> ux }
        fun dy(ux: Float, uy: Float): Float = when (turn) { 90 -> ux; 180 -> uy; 270 -> width - ux; else -> height - uy }
        val xs = floatArrayOf(dx(x0, y0), dx(x1, y0), dx(x1, y1), dx(x0, y1))
        val ys = floatArrayOf(dy(x0, y0), dy(x1, y0), dy(x1, y1), dy(x0, y1))
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /**
     * A rectangle given in a turned frame (top-left origin, the page turned by [frameRotation], as PDFBox measures text
     * in its own writing direction) as display points on the page shown turned by [pageRotation].
     */
    internal fun frameRectToDisplay(
        left: Float, top: Float, right: Float, bottom: Float,
        frameRotation: Int, pageRotation: Int, width: Float, height: Float
    ): RectF {
        val user = displayRectToUserSpace(PageShape(PDRectangle(0f, 0f, width, height), frameRotation), left, top, right, bottom)
        return userRectToDisplay(user.lowerLeftX, user.lowerLeftY, user.upperRightX, user.upperRightY, pageRotation, width, height)
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
 * Redact text (F172, rebuilt F194): every occurrence of the given words is blacked out, and each page with a match is
 * redrawn as a picture, so the words are gone from the file rather than hidden under a box. Pages without a match keep
 * their text layer, but are copied without anything that could still lead back to a redacted page: references to the
 * original pages (links, comment and form-field parents) point at the new pages, and pictures or forms a page lists but
 * never draws (a shared resource list) are left out. The document's title, author, bookmarks and form data are not
 * carried over at all. Make searchable can give a redacted page its text back, without the redacted words.
 *
 * The search ignores case and accents unless "match case" is on, finds words broken over two lines (with or without a
 * hyphen), treats ligatures, curly quotes and dashes like their plain forms, and also looks in comments and form fields:
 * a comment or field that contains a word is blacked out where it sits, and its page becomes a picture too.
 */
object PdfRedactTools {
    /** Rectangles are in display points from the page's top-left corner (the page as it is shown, after /Rotate). */
    internal class Match(val page: Int, val rect: RectF)

    /** What a search found: the boxes to black out, how many places matched, and on how many pages. */
    class RedactSearch internal constructor(
        internal val matches: List<Match>,
        val occurrences: Int,
        val pages: Int,
        /** Matches for each term, in the order the terms were given (after trimming and removing repeats). */
        val perTerm: List<Int>,
    ) {
        /** The boxes on page [page] (0-based), in display points from its top-left corner. */
        fun boxesOn(page: Int): List<RectF> = matches.filter { it.page == page }.map { it.rect }
    }

    /** One page's searchable text: glyph boxes, their lines, and the text as searched (exact and folded). */
    internal class PageText(
        /** Four floats per glyph: left, top, right, bottom in display points. */
        val boxes: FloatArray,
        /** The line each glyph is on, so a match over two lines gets two boxes rather than one big one. */
        val lines: IntArray,
        val exact: String,
        val exactMap: IntArray,
        val folded: String,
        val foldedMap: IntArray,
        val annotations: List<AnnotationText>,
    )

    /** The words in one comment or form field, and where it sits on the page (display points). */
    internal class AnnotationText(val exact: String, val folded: String, val rect: RectF)

    /** The whole document's text, read once; searching it again as the words are typed is then instant. */
    class TextIndex internal constructor(internal val pages: List<PageText>) {
        val pageCount: Int get() = pages.size

        /** False for a scan without a text layer: nothing can be found until Make searchable has run. */
        val hasText: Boolean get() = pages.any { it.exact.isNotBlank() || it.annotations.isNotEmpty() }
    }

    internal const val REDACT_DPI = 200
    internal const val REDACT_MAX_SIDE = 3500
    internal const val REDACT_JPEG_QUALITY = 85
    /** Boxes grow by this much (points) on every side, so the glyphs' anti-aliased fringe goes too. */
    private const val BOX_PADDING_PT = 1.5f
    private const val WORD_BREAK = -1
    private const val LINE_BREAK = -2

    /** Comment and form-field entries whose text is searched (PDF 32000-1, 12.5.2, 12.7.3). */
    private val ANNOTATION_TEXT_KEYS = listOf("Contents", "RC", "T", "Subj", "V", "DV", "TU").map { COSName.getPDFName(it) }

    // ---- Reading the text -----------------------------------------------------------------------------------------

    /** Reads every page's text and glyph positions. [checkCancelled] is called between pages. */
    internal fun buildIndex(doc: PDDocument, checkCancelled: () -> Unit = {}): TextIndex {
        val pages = ArrayList<PageText>(doc.numberOfPages)
        for (index in 0 until doc.numberOfPages) {
            checkCancelled()
            pages += indexPage(doc, index)
        }
        return TextIndex(pages)
    }

    /** Copies [uri] aside, reads its text into a [TextIndex] and removes the copy again (for the tool page's count). */
    suspend fun loadIndex(context: Context, uri: Uri): TextIndex = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, uri, "redact_find_${UUID.randomUUID()}.pdf")
        try {
            val job = coroutineContext[Job]
            PdfToolsEngine.openPdf(temp).use { doc -> buildIndex(doc) { job?.ensureActive() } }
        } finally {
            temp.delete()
        }
    }

    private fun indexPage(doc: PDDocument, index: Int): PageText {
        val page = doc.getPage(index)
        val rotation = page.rotation
        val glyphText = ArrayList<String>()
        val rects = ArrayList<RectF>()
        val lineOf = ArrayList<Int>()
        val units = ArrayList<Int>()
        var line = 0
        val stripper = object : PDFTextStripper() {
            override fun writeString(string: String, textPositions: List<TextPosition>) {
                // One unit per glyph, with the glyph's own text: a ligature ("ﬁ") stays one glyph with two letters.
                for (p in textPositions) {
                    val unicode: String = p.unicode ?: continue
                    units += glyphText.size
                    glyphText += unicode
                    rects += glyphBox(p, rotation)
                    lineOf += line
                }
            }

            override fun writeWordSeparator() { units += WORD_BREAK }
            override fun writeLineSeparator() { units += LINE_BREAK; line++ }
            override fun writeParagraphSeparator() { units += LINE_BREAK; line++ }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(doc)

        val boxes = FloatArray(rects.size * 4)
        rects.forEachIndexed { i, r ->
            boxes[i * 4] = r.left; boxes[i * 4 + 1] = r.top; boxes[i * 4 + 2] = r.right; boxes[i * 4 + 3] = r.bottom
        }
        val (exact, exactMap) = haystack(units, glyphText, fold = false)
        val (folded, foldedMap) = haystack(units, glyphText, fold = true)
        return PageText(boxes, lineOf.toIntArray(), exact, exactMap, folded, foldedMap, annotationTexts(page))
    }

    /**
     * A glyph's box in display points. PDFBox gives positions in the frame of the text's own direction; the box is
     * taken back to the page and then turned as the page is shown, so vertical text and rotated pages land right. It
     * reaches from the ascent to below the baseline, so descenders (g, p, y) and accents are covered too.
     */
    private fun glyphBox(p: TextPosition, pageRotation: Int): RectF {
        val size = p.textMatrix.scalingFactorY.takeIf { it > 0f } ?: (p.heightDir * 1.4f)
        val ascent = max(p.heightDir, size * 0.8f)
        val descent = size * 0.25f
        val left = p.xDirAdj
        val right = p.xDirAdj + max(p.widthDirAdj, 0.1f)
        val baseline = p.yDirAdj
        return PdfCropTools.frameRectToDisplay(left, baseline - ascent, right, baseline + descent, p.dir.toInt(), pageRotation, p.pageWidth, p.pageHeight)
    }

    /**
     * The text as searched, with each character's glyph (-1 for a space). Spaces and line breaks become one space; a
     * word hyphenated at the end of a line is joined again ("confiden-" + "tial" reads "confidential").
     */
    private fun haystack(units: List<Int>, glyphText: List<String>, fold: Boolean): Pair<String, IntArray> {
        val normalised = glyphText.map { normaliseForSearch(it, fold) }
        val text = StringBuilder()
        val map = ArrayList<Int>()
        var space = false
        var swallow = false
        var softHyphen = false
        for ((k, unit) in units.withIndex()) {
            if (unit < 0) {
                if (swallow) continue
                val joinable = unit == LINE_BREAK && nextStartsWithLetter(units, k, normalised) && (
                    softHyphen || (text.length >= 2 && text[text.length - 1] == '-' && text[text.length - 2].isLetter())
                )
                if (joinable) {
                    if (!softHyphen) {
                        text.setLength(text.length - 1)
                        map.removeAt(map.size - 1)
                    }
                    swallow = true
                    space = false
                } else {
                    space = true
                }
                softHyphen = false
                continue
            }
            swallow = false
            for (ch in normalised[unit]) {
                if (ch == '­') { softHyphen = true; continue }
                softHyphen = false
                if (ch.isWhitespace()) { space = true; continue }
                if (space && text.isNotEmpty()) { text.append(' '); map.add(-1) }
                space = false
                text.append(ch)
                map.add(unit)
            }
        }
        return text.toString() to map.toIntArray()
    }

    private fun nextStartsWithLetter(units: List<Int>, from: Int, normalised: List<String>): Boolean {
        for (k in from + 1 until units.size) {
            val unit = units[k]
            if (unit < 0) continue
            return normalised[unit].firstOrNull()?.isLetter() == true
        }
        return false
    }

    /**
     * Text as it is compared: compatibility forms unfolded (ligatures, full-width letters), curly quotes and dashes as
     * plain ones; with [fold], also lower case and without accents (é → e). Hindi, Tamil and other scripts keep their
     * vowel signs: only the Latin combining accents are dropped.
     */
    internal fun normaliseForSearch(text: String, fold: Boolean): String {
        val compat = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val source = if (fold) Normalizer.normalize(compat, Normalizer.Form.NFD) else compat
        val out = StringBuilder(source.length)
        for (ch in source) {
            if (fold && ch in '̀'..'ͯ') continue
            val plain = when (ch) {
                '‘', '’', '‛', '′' -> '\''
                '“', '”', '„', '″' -> '"'
                '‐', '‑', '‒', '–', '—', '−' -> '-'
                else -> ch
            }
            out.append(if (fold) plain.lowercaseChar() else plain)
        }
        return out.toString()
    }

    /** A typed term as searched, and the same without hyphens (it then also finds "well-" + "known" over two lines). */
    private fun needles(term: String, fold: Boolean): List<String> {
        val base = normaliseForSearch(term, fold).replace("­", "").split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        if (base.isEmpty()) return emptyList()
        val unhyphenated = base.replace("-", "")
        return if (unhyphenated != base && unhyphenated.isNotBlank()) listOf(base, unhyphenated) else listOf(base)
    }

    private val WHITESPACE = Regex("\\s+")

    /** The text of each comment and form field on [page] (not popups, which only show their note's text). */
    private fun annotationTexts(page: PDPage): List<AnnotationText> {
        val annotations = runCatching { page.annotations }.getOrNull() ?: return emptyList()
        if (annotations.isEmpty()) return emptyList()
        val crop = page.cropBox
        val out = ArrayList<AnnotationText>()
        for (annotation in annotations) {
            if (annotation is PDAnnotationPopup) continue
            val text = annotationText(annotation.cosObject)
            if (text.isBlank()) continue
            val r = annotation.rectangle ?: continue
            val rect = PdfCropTools.userRectToDisplay(
                r.lowerLeftX - crop.lowerLeftX, r.lowerLeftY - crop.lowerLeftY,
                r.upperRightX - crop.lowerLeftX, r.upperRightY - crop.lowerLeftY,
                page.rotation, crop.width, crop.height
            )
            val exact = normaliseForSearch(text, fold = false).split(WHITESPACE).joinToString(" ")
            val folded = normaliseForSearch(text, fold = true).split(WHITESPACE).joinToString(" ")
            out += AnnotationText(exact, folded, rect)
        }
        return out
    }

    /** The searchable strings of an annotation and, for a form field, of the fields above it (value, name, tooltip). */
    private fun annotationText(dict: COSDictionary): String {
        val parts = ArrayList<String>()
        var node: COSDictionary? = dict
        var depth = 0
        while (node != null && depth < 16) {
            for (key in ANNOTATION_TEXT_KEYS) cosText(node.getDictionaryObject(key))?.let { parts += it }
            node = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
            depth++
        }
        return parts.joinToString("\n")
    }

    private fun cosText(value: COSBase?): String? = when (value) {
        is COSString -> value.string
        is COSName -> value.name
        is COSStream -> runCatching { value.toTextString() }.getOrNull()
        is COSArray -> (0 until value.size()).mapNotNull { cosText(value.getObject(it)) }.joinToString(" ")
        else -> null
    }

    // ---- Searching ------------------------------------------------------------------------------------------------

    /** Every occurrence of every term in [index]. Terms are trimmed; empty ones and repeats are ignored. */
    fun search(index: TextIndex, terms: List<String>, matchCase: Boolean): RedactSearch {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val fold = !matchCase
        val variants = wanted.map { needles(it, fold) }
        val matches = ArrayList<Match>()
        val perTerm = IntArray(wanted.size)
        val pagesHit = HashSet<Int>()
        index.pages.forEachIndexed { pageIndex, page ->
            val hay = if (fold) page.folded else page.exact
            val map = if (fold) page.foldedMap else page.exactMap
            variants.forEachIndexed { t, needles ->
                for (needle in needles) {
                    var from = hay.indexOf(needle)
                    while (from >= 0) {
                        val boxes = boxesFor(page, map, from, from + needle.length)
                        if (boxes.isNotEmpty()) {
                            boxes.forEach { matches += Match(pageIndex, it) }
                            perTerm[t]++
                            pagesHit += pageIndex
                        }
                        from = hay.indexOf(needle, from + needle.length)
                    }
                }
                for (annotation in page.annotations) {
                    val text = if (fold) annotation.folded else annotation.exact
                    if (needles.any { text.contains(it) }) {
                        matches += Match(pageIndex, RectF(annotation.rect))
                        perTerm[t]++
                        pagesHit += pageIndex
                    }
                }
            }
        }
        return RedactSearch(matches, perTerm.sum(), pagesHit.size, perTerm.toList())
    }

    /** Every occurrence of every term, page by page. Terms are trimmed; empty ones are ignored. */
    internal fun findMatches(doc: PDDocument, terms: List<String>, matchCase: Boolean): List<Match> =
        search(buildIndex(doc), terms, matchCase).matches

    /** The boxes of the glyphs behind characters [from] until [to], one box per line they are on. */
    private fun boxesFor(page: PageText, map: IntArray, from: Int, to: Int): List<RectF> {
        var first = Int.MAX_VALUE
        var last = -1
        for (k in from until min(to, map.size)) {
            val glyph = map[k]
            if (glyph >= 0) {
                first = min(first, glyph)
                last = max(last, glyph)
            }
        }
        if (last < 0) return emptyList()
        val byLine = LinkedHashMap<Int, RectF>()
        for (g in first..last) {
            val o = g * 4
            val glyph = RectF(page.boxes[o], page.boxes[o + 1], page.boxes[o + 2], page.boxes[o + 3])
            val lineBox = byLine[page.lines[g]]
            if (lineBox == null) byLine[page.lines[g]] = glyph else lineBox.union(glyph)
        }
        return byLine.values.map { it.apply { inset(-BOX_PADDING_PT, -BOX_PADDING_PT) } }
    }

    // ---- Writing --------------------------------------------------------------------------------------------------

    /**
     * Writes [outputFile]: pages in [matches] are drawn by [draw] (as displayed, white background), the match boxes
     * painted black, and stored as pictures; every other page is copied from [source] with its text, cleaned of
     * anything that leads back to a redacted page (see the object's notes). A page whose drawing instructions cannot
     * be read for that check is made a picture too, rather than copied unchecked. Returns the pages with matches.
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
                // The source's pages and the pages that replace them, so every link or parent that pointed at an old
                // page points at its new one; and the comments of redacted pages, which must not be reachable at all.
                val pageMap = IdentityHashMap<COSBase, COSDictionary>()
                val banned: MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())
                for (i in 0 until source.numberOfPages) {
                    if (!coroutineContext.isActive) throw InterruptedException("Redact cancelled")
                    onPage(i)
                    val sourcePage = source.getPage(i)
                    val boxes = byPage[i]
                    if (boxes == null) {
                        val copied = importClean(out, sourcePage)
                        if (copied != null) {
                            pageMap[sourcePage.cosObject] = copied.cosObject
                            continue
                        }
                    }
                    runCatching { sourcePage.annotations }.getOrNull()?.forEach { banned += it.cosObject }
                    val (widthPt, heightPt) = pageSize(i)
                    val scale = min(REDACT_DPI / 72f, REDACT_MAX_SIDE / max(widthPt, heightPt).coerceAtLeast(1f))
                    val w = (widthPt * scale).toInt().coerceAtLeast(1)
                    val h = (heightPt * scale).toInt().coerceAtLeast(1)
                    val canvas = bitmap?.takeIf { it.width == w && it.height == h }
                        ?: createBitmap(w, h).also { bitmap?.recycle(); bitmap = it }
                    canvas.eraseColor(Color.WHITE)
                    draw(i, canvas)
                    val painter = Canvas(canvas)
                    boxes?.forEach { m ->
                        painter.drawRect(m.rect.left * scale, m.rect.top * scale, m.rect.right * scale, m.rect.bottom * scale, paint)
                    }
                    buffer.reset()
                    canvas.compress(Bitmap.CompressFormat.JPEG, REDACT_JPEG_QUALITY, buffer)
                    val image = JPEGFactory.createFromByteArray(out, buffer.toByteArray())
                    val page = PDPage(PDRectangle(widthPt, heightPt))
                    out.addPage(page)
                    PDPageContentStream(out, page).use { it.drawImage(image, 0f, 0f, widthPt, heightPt) }
                    pageMap[sourcePage.cosObject] = page.cosObject
                }
                scrubReferences(out.documentCatalog.cosObject, pageMap, banned)
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

    /**
     * [page] copied into [out] with its text, or null when its drawing instructions cannot be read (the caller then
     * makes it a picture). Its resources are set on the page itself (inherited ones do not travel with an imported
     * page) and keep only the pictures and forms the page really draws; form fields lose their link to the form, whose
     * other widgets may sit on redacted pages; beads, private application data and page metadata are dropped.
     */
    private fun importClean(out: PDDocument, page: PDPage): PDPage? {
        val resources = try {
            cleanResources(page.resources, page, Collections.newSetFromMap(IdentityHashMap()))
        } catch (_: Exception) {
            return null
        }
        val copied = out.importPage(page)
        copied.resources = resources
        val dict = copied.cosObject
        dict.removeItem(COSName.getPDFName("B"))
        dict.removeItem(COSName.getPDFName("PieceInfo"))
        dict.removeItem(COSName.METADATA)
        val annotations = dict.getDictionaryObject(COSName.ANNOTS) as? COSArray
        if (annotations != null) {
            for (k in 0 until annotations.size()) {
                val annotation = annotations.getObject(k) as? COSDictionary ?: continue
                if (annotation.getCOSName(COSName.SUBTYPE) == WIDGET) annotation.removeItem(COSName.PARENT)
            }
        }
        return copied
    }

    private val WIDGET = COSName.getPDFName("Widget")

    /**
     * A copy of [resources] whose /XObject list holds only what [content] draws (the `Do` operator), applied to the
     * forms it draws in turn. A shared resource list can name a picture or form that only a redacted page shows;
     * copied as it is, that form's text would still be in the file. Throws when [content] cannot be parsed.
     */
    internal fun cleanResources(resources: PDResources?, content: PDContentStream, seen: MutableSet<COSBase>): PDResources? {
        if (resources == null) return null
        val used = usedXObjects(content)
        val dict = COSDictionary(resources.cosObject)
        val xobjects = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
        if (xobjects != null) {
            val kept = COSDictionary()
            for (name in xobjects.keySet()) {
                if (name.name !in used) continue
                kept.setItem(name, xobjects.getItem(name))
                val stream = xobjects.getDictionaryObject(name) as? COSStream ?: continue
                if (stream.getCOSName(COSName.SUBTYPE) == COSName.FORM && seen.add(stream)) {
                    val form = PDFormXObject(PDStream(stream))
                    form.resources = cleanResources(form.resources, form, seen)
                }
            }
            dict.setItem(COSName.XOBJECT, kept)
        }
        return PDResources(dict)
    }

    /** The names [content] draws with `Do`. */
    internal fun usedXObjects(content: PDContentStream): Set<String> {
        val names = HashSet<String>()
        val parser = PDFStreamParser(content)
        var lastName: String? = null
        while (true) {
            val token = parser.parseNextToken() ?: break
            when (token) {
                is COSName -> lastName = token.name
                is Operator -> {
                    if (token.name == "Do") lastName?.let { names += it }
                    lastName = null
                }
                else -> lastName = null
            }
        }
        return names
    }

    /**
     * Walks everything reachable from [root]: a reference to an old page is pointed at the page that replaced it (a
     * link to a redacted page then opens its picture), and a reference to anything in [banned] (the comments of
     * redacted pages) is removed, so neither the old pages nor their words are written out.
     */
    internal fun scrubReferences(root: COSBase, pages: IdentityHashMap<COSBase, COSDictionary>, banned: Set<COSBase>) {
        val seen: MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())
        val stack = ArrayDeque<COSBase>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!seen.add(node)) continue
            if (node is COSDictionary) {
                for (key in ArrayList(node.keySet())) {
                    val value = deref(node.getItem(key)) ?: continue
                    when {
                        value in banned -> node.removeItem(key)
                        pages.containsKey(value) -> node.setItem(key, pages.getValue(value))
                        value is COSDictionary || value is COSArray -> stack.addLast(value)
                    }
                }
            } else if (node is COSArray) {
                for (k in node.size() - 1 downTo 0) {
                    val value = deref(node.get(k)) ?: continue
                    when {
                        value in banned -> node.remove(k)
                        pages.containsKey(value) -> node.set(k, pages.getValue(value))
                        value is COSDictionary || value is COSArray -> stack.addLast(value)
                    }
                }
            }
        }
    }

    private fun deref(value: COSBase?): COSBase? = if (value is COSObject) value.getObject() else value

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
                val job = coroutineContext[Job]
                val found = search(buildIndex(doc) { job?.ensureActive() }, terms, matchCase)
                if (found.matches.isEmpty()) userError(R.string.tools_redact_err_no_matches)
                val pages = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                        var open: PdfRenderer.Page? = null
                        fun page(i: Int): PdfRenderer.Page = open?.takeIf { it.index == i } ?: renderer.openPage(i).also { open?.close(); open = it }
                        try {
                            writeRedacted(
                                source = doc,
                                matches = found.matches,
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
                    message = AppStrings.get(R.string.tools_redact_done, found.occurrences, pages)
                )
            }
        } finally {
            temp.delete()
        }
    }
}
