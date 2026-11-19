package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.Closeable
import java.io.File
import java.util.UUID
import com.example.scanpro.core.concurrent.BackgroundWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Edit PDF text (F193) on the phone: the editor's working session and the final save. The engine is [PdfTextEditor].
 *
 * The session keeps a private copy of the PDF open for reading its text, and shows each page as Android draws it. A
 * page with edits is shown from a one-page preview made with the same engine that saves, so what the editor shows is
 * what the saved PDF will look like, font substitutions included.
 */
internal class PdfTextEditSession private constructor(private val source: File, private val document: PDDocument) : Closeable {
    val pageCount: Int get() = document.numberOfPages
    private val spans = HashMap<Int, List<PdfTextEditor.Span>>()
    private var closed = false

    /** The text runs on [page] (0-based), read once. */
    @Synchronized
    fun spans(page: Int): List<PdfTextEditor.Span> {
        check(!closed)
        return spans.getOrPut(page) { runCatching { PdfTextEditor.spans(document, page) }.getOrDefault(emptyList()) }
    }

    /** Every line in the PDF that holds [query], page by page, for Find and replace. */
    fun find(query: String, matchCase: Boolean): List<PdfTextEditor.Span> {
        if (query.isEmpty()) return emptyList()
        return (0 until pageCount).flatMap { page -> spans(page).filter { it.text.contains(query, ignoreCase = !matchCase) } }
    }

    /**
     * [page] at [width] pixels wide, with [edits] (those for this page) applied when there are any; also the page's
     * size in points as shown, which the editor maps the spans with, where each edit's words now are and which lines
     * moved down to make room. Null if it can't be drawn.
     */
    fun render(context: Context, page: Int, width: Int, edits: List<PdfTextEditor.Edit>): PagePreview? {
        val mine = edits.filter { it.page == page }
        fun plain() = renderPage(source, page, width)?.let { (bitmap, size) -> PagePreview(page, bitmap, size.first, size.second) }
        if (mine.isEmpty()) return plain()
        val preview = File(context.cacheDir, "pdfedit_preview_${UUID.randomUUID()}.pdf")
        return try {
            var outcome: PdfTextEditor.Outcome? = null
            // Only this page is copied into a new PDF (a fresh read of the file, so the session's copy is never
            // changed): a preview costs one page however long the PDF is.
            PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { copy ->
                PDDocument().use { single ->
                    val original = copy.getPage(page)
                    val imported = single.importPage(original)
                    // Inherited from the page tree in the source; the imported page has to carry them itself.
                    imported.resources = original.resources
                    imported.mediaBox = original.mediaBox
                    imported.cropBox = original.cropBox
                    imported.rotation = original.rotation
                    outcome = PdfTextEditor.apply(single, mine.map { it.copy(page = 0) }, ::phoneUnicodeFont, PhoneTextShaper, CarriedFonts(context))
                    single.save(preview)
                }
            }
            renderPage(preview, 0, width)?.let { (bitmap, size) ->
                PagePreview(
                    page, bitmap, size.first, size.second,
                    placed = outcome?.placed.orEmpty().map { it.copy(edit = it.edit.copy(page = page)) },
                    moved = outcome?.moved.orEmpty().mapKeys { it.key.second }
                )
            } ?: plain()
        } catch (_: Exception) {
            plain()
        } finally {
            preview.delete()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { document.close() }
        source.delete()
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfTextEditSession = withContext(Dispatchers.IO) {
            val copy = PdfToolsEngine.copyUriToTemp(context, uri, "pdfedit_src_${UUID.randomUUID()}.pdf")
            try {
                PdfTextEditSession(copy, PDDocument.load(copy, MemoryUsageSetting.setupTempFileOnly()))
            } catch (error: Throwable) {
                copy.delete()
                throw error
            }
        }

        /** Page [index] of [file] drawn on white at [width] pixels, and its shown size in points. */
        private fun renderPage(file: File, index: Int, width: Int): Pair<Bitmap, Pair<Float, Float>>? = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap to (page.width.toFloat() to page.height.toFloat())
                    }
                }
            }
        }.getOrNull()
    }
}

/**
 * A page as the editor shows it: [bitmap] of page [page], its size in points as shown, the box of each edit's words
 * ([placed]) and the lines that moved down to make room (span index to points, [moved]).
 */
internal class PagePreview(
    val page: Int,
    val bitmap: Bitmap,
    val width: Float,
    val height: Float,
    val placed: List<PdfTextEditor.Placed> = emptyList(),
    val moved: Map<Int, Float> = emptyMap()
)

/**
 * Hindi, Kannada, Malayalam, Tamil, Telugu and other scripts whose letters join: Android sets the words (it knows how
 * the letters combine) and gives their outlines, which the editor draws on the page as shapes. PDFBox writing the same
 * letters one by one leaves them apart and out of order.
 */
internal object PhoneTextShaper : PdfTextEditor.Shaper {
    /** Outlines are taken at this size and scaled down, so curves keep their shape. */
    private const val EM = 200f

    private fun paint(family: PdfTextEditor.Family, bold: Boolean, italic: Boolean) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = EM
        val base = when (family) {
            PdfTextEditor.Family.SERIF, PdfTextEditor.Family.GELASIO -> android.graphics.Typeface.SERIF
            PdfTextEditor.Family.MONO -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> android.graphics.Typeface.BOLD_ITALIC
            bold -> android.graphics.Typeface.BOLD
            italic -> android.graphics.Typeface.ITALIC
            else -> android.graphics.Typeface.NORMAL
        }
        typeface = android.graphics.Typeface.create(base, style)
    }

    override fun width(text: String, family: PdfTextEditor.Family, bold: Boolean, italic: Boolean): Float =
        paint(family, bold, italic).measureText(text) / EM

    override fun outline(text: String, family: PdfTextEditor.Family, bold: Boolean, italic: Boolean): List<FloatArray> {
        val path = android.graphics.Path()
        paint(family, bold, italic).getTextPath(text, 0, text.length, 0f, 0f, path)
        val contours = ArrayList<FloatArray>()
        val measure = android.graphics.PathMeasure(path, false)
        val point = FloatArray(2)
        do {
            val length = measure.length
            if (length <= 0f) continue
            val points = if (android.os.Build.VERSION.SDK_INT >= 26) {
                // The contour on its own, as straight pieces that stay within a quarter of a unit of the curve.
                val contour = android.graphics.Path()
                measure.getSegment(0f, length, contour, true)
                val approximate = contour.approximate(0.25f)
                FloatArray(approximate.size / 3 * 2) { i -> approximate[i / 2 * 3 + 1 + i % 2] }
            } else {
                val steps = (length / 1.5f).toInt().coerceIn(8, 2000)
                FloatArray(steps * 2).also { out ->
                    for (step in 0 until steps) {
                        measure.getPosTan(length * step / steps, point, null)
                        out[step * 2] = point[0]
                        out[step * 2 + 1] = point[1]
                    }
                }
            }
            // Android draws downwards; the page's text goes upwards.
            for (i in points.indices) points[i] = if (i % 2 == 0) points[i] / EM else -points[i] / EM
            if (points.size >= 6) contours += points
        } while (measure.nextContour())
        return contours
    }
}

/**
 * The fonts the app carries for Edit PDF (F207): Roboto, Inter, Montserrat and Gelasio (a free serif drawn to the
 * measure of Georgia, which is not free to ship), each in regular, bold, italic and bold italic. The files in
 * `res/font` hold Latin letters, punctuation and money signs only (about 70 KB each); text they can't show falls back
 * to a standard font. PDFBox embeds just the letters used.
 */
internal class CarriedFonts(private val context: Context) : PdfTextEditor.FontSource {
    override fun load(doc: PDDocument, family: PdfTextEditor.Family, bold: Boolean, italic: Boolean): com.tom_roush.pdfbox.pdmodel.font.PDFont? {
        val resource = resourceOf(family, bold, italic) ?: return null
        return context.resources.openRawResource(resource).use { com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc, it, true) }
    }

    companion object {
        /** The font file for [family] in the weight and slant asked for; null for a family the app carries no file for. */
        fun resourceOf(family: PdfTextEditor.Family, bold: Boolean, italic: Boolean): Int? = when (family) {
            PdfTextEditor.Family.ROBOTO -> pick(bold, italic, R.font.roboto_regular, R.font.roboto_bold, R.font.roboto_italic, R.font.roboto_bold_italic)
            PdfTextEditor.Family.INTER -> pick(bold, italic, R.font.inter_regular, R.font.inter_bold, R.font.inter_italic, R.font.inter_bold_italic)
            PdfTextEditor.Family.GELASIO -> pick(bold, italic, R.font.gelasio_regular, R.font.gelasio_bold, R.font.gelasio_italic, R.font.gelasio_bold_italic)
            PdfTextEditor.Family.MONTSERRAT -> pick(bold, italic, R.font.montserrat_regular, R.font.montserrat_bold, R.font.montserrat_italic, R.font.montserrat_bold_italic)
            else -> null
        }

        private fun pick(bold: Boolean, italic: Boolean, regular: Int, boldFont: Int, italicFont: Int, boldItalic: Int): Int = when {
            bold && italic -> boldItalic
            bold -> boldFont
            italic -> italicFont
            else -> regular
        }
    }
}

internal object PdfTextEditTool {
    /** Writes [outputFile]: the PDF at [uri] with [edits] applied. */
    suspend fun save(context: Context, uri: Uri, edits: List<PdfTextEditor.Edit>, outputFile: File): PdfToolResult =
        withContext(BackgroundWork.tools) {
            val temp = PdfToolsEngine.copyUriToTemp(context, uri, "pdfedit_in_${UUID.randomUUID()}.pdf")
            try {
                val (pages, outcome) = PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
                    val outcome = PdfTextEditor.apply(document, edits, ::phoneUnicodeFont, PhoneTextShaper, CarriedFonts(context))
                    outputFile.parentFile?.mkdirs()
                    document.save(outputFile)
                    document.numberOfPages to outcome
                }
                val notes = buildList {
                    add(AppStrings.plural(R.plurals.tools_edit_done, outcome.applied, outcome.applied))
                    if (outcome.shaped > 0) add(AppStrings.plural(R.plurals.tools_edit_shaped, outcome.shaped, outcome.shaped))
                    val similar = outcome.similarFont - outcome.shaped
                    if (similar > 0) add(AppStrings.plural(R.plurals.tools_edit_similar_font, similar, similar))
                    if (outcome.skipped > 0) add(AppStrings.plural(R.plurals.tools_edit_skipped, outcome.skipped, outcome.skipped))
                    if (outcome.covered > 0) add(AppStrings.plural(R.plurals.tools_edit_covered, outcome.covered, outcome.covered))
                }
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = pages,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = notes.joinToString(" ")
                )
            } catch (error: Throwable) {
                outputFile.delete()
                throw error
            } finally {
                temp.delete()
            }
        }
}
