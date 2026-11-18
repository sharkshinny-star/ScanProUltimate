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

    /**
     * [page] at [width] pixels wide, with [edits] (those for this page) applied when there are any; also the page's
     * size in points as shown, which the editor maps the spans with. Null if it can't be drawn.
     */
    fun render(context: Context, page: Int, width: Int, edits: List<PdfTextEditor.Edit>): Pair<Bitmap, Pair<Float, Float>>? {
        val mine = edits.filter { it.page == page }
        if (mine.isEmpty()) return renderPage(source, page, width)
        val preview = File(context.cacheDir, "pdfedit_preview_${UUID.randomUUID()}.pdf")
        return try {
            PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { copy ->
                // A file with only an owner password (restrictions) can be read but not saved as it is.
                if (copy.isEncrypted) copy.isAllSecurityToBeRemoved = true
                for (i in copy.numberOfPages - 1 downTo 0) if (i != page) copy.removePage(i)
                PdfTextEditor.apply(copy, mine.map { it.copy(page = 0) }, ::phoneUnicodeFont)
                copy.save(preview)
            }
            renderPage(preview, 0, width)
        } catch (_: Exception) {
            renderPage(source, page, width)
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

internal object PdfTextEditTool {
    /** Writes [outputFile]: the PDF at [uri] with [edits] applied. */
    suspend fun save(context: Context, uri: Uri, edits: List<PdfTextEditor.Edit>, outputFile: File): PdfToolResult =
        withContext(BackgroundWork.tools) {
            val temp = PdfToolsEngine.copyUriToTemp(context, uri, "pdfedit_in_${UUID.randomUUID()}.pdf")
            try {
                val (pages, outcome) = PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
                    val outcome = PdfTextEditor.apply(document, edits, ::phoneUnicodeFont)
                    outputFile.parentFile?.mkdirs()
                    document.save(outputFile)
                    document.numberOfPages to outcome
                }
                val notes = buildList {
                    add(AppStrings.plural(R.plurals.tools_edit_done, outcome.applied, outcome.applied))
                    if (outcome.similarFont > 0) add(AppStrings.plural(R.plurals.tools_edit_similar_font, outcome.similarFont, outcome.similarFont))
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
