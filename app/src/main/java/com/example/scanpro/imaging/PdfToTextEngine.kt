package com.example.scanpro.imaging

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.data.OcrScript
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.UUID

/** What "PDF to text" writes. */
enum class TextExportFormat(val extension: String) {
    TEXT("txt"),
    WORD("docx")
}

/** Where a page's text came from. */
enum class PageTextSource { TEXT_LAYER, OCR, NONE }

data class PdfTextPage(val text: String, val source: PageTextSource)

/** What "PDF to text" did. Page numbers in the lists start at 1. */
data class PdfTextOutcome(
    val outputFile: File,
    val pageCount: Int,
    val textLayerPages: Int,
    val ocrPages: List<Int>,
    val emptyPages: List<Int>,
    val originalSizeBytes: Long
)

/**
 * Turns a PDF into plain text or a Word document. A page with a text layer is read with PDFBox's text stripper; a
 * page without one (a scan) is drawn, one page at a time, and read with the on-device OCR. The output has no
 * password, whatever the PDF had.
 */
object PdfToTextEngine {
    /**
     * Reads every page of [document]: its text layer when it has text, otherwise whatever [ocrPage] (given the
     * 0-based page index) reads from it. [ocrPage] is called only for pages without text, one at a time.
     */
    internal suspend fun readPages(
        document: PDDocument,
        ocrPage: suspend (index: Int) -> String,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): List<PdfTextPage> {
        val total = document.numberOfPages
        val stripper = PDFTextStripper()
        val pages = ArrayList<PdfTextPage>(total)
        for (index in 0 until total) {
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, total, AppStrings.get(R.string.tools_progress_reading_text, index + 1, total))
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            val layer = runCatching { stripper.getText(document) }.getOrDefault("").trim()
            if (layer.isNotEmpty()) {
                pages += PdfTextPage(layer, PageTextSource.TEXT_LAYER)
            } else {
                val read = ocrPage(index).trim()
                pages += PdfTextPage(read, if (read.isEmpty()) PageTextSource.NONE else PageTextSource.OCR)
            }
        }
        return pages
    }

    /** The text of a page as shown in the output: a heading, then its text (or a note that there was none). */
    internal fun pageBlock(number: Int, page: PdfTextPage): String {
        val body = if (page.source == PageTextSource.NONE) AppStrings.get(R.string.tools_text_page_empty) else page.text
        return AppStrings.get(R.string.tools_text_page_heading, number) + "\n" + body
    }

    /** One block per page, a blank line between them. */
    internal fun plainText(pages: List<PdfTextPage>): String =
        pages.mapIndexed { index, page -> pageBlock(index + 1, page) }.joinToString("\n\n") + "\n"

    internal fun write(title: String, pages: List<PdfTextPage>, format: TextExportFormat, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        when (format) {
            TextExportFormat.TEXT -> outputFile.writeText(plainText(pages), Charsets.UTF_8)
            // The Word writer puts one paragraph per line and a page break between pages.
            TextExportFormat.WORD -> DocxExporter.export(title, pages.mapIndexed { index, page -> pageBlock(index + 1, page) }, outputFile)
        }
    }

    /** "3, 4, 9", cut short with an ellipsis when long. */
    fun pageList(pages: List<Int>, limit: Int = 20): String =
        pages.take(limit).joinToString(", ") + if (pages.size > limit) "…" else ""

    /**
     * Converts [inputUri] to [format] in [outputFile]. A protected PDF is opened with [password]; when it has none, or
     * it is wrong, this stops with [PdfPasswordNeededException] so the user can be asked.
     */
    suspend fun convert(
        context: Context,
        inputUri: Uri,
        format: TextExportFormat,
        script: OcrScript,
        ocr: OcrEngine,
        title: String,
        outputFile: File,
        password: String? = null,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfTextOutcome = withContext(Dispatchers.IO) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "pdftext_in_${UUID.randomUUID()}.pdf")
        // PdfRenderer cannot draw a protected file, so pages that need reading are drawn from an unprotected copy.
        val plainCopy = File(context.cacheDir, "pdftext_plain_${UUID.randomUUID()}.pdf")
        val pageImage = File(context.cacheDir, "pdftext_page_${UUID.randomUUID()}.jpg")
        try {
            val document = try {
                PdfToolsEngine.openPdf(input, password.orEmpty())
            } catch (locked: InvalidPasswordException) {
                throw PdfPasswordNeededException(inputUri, wrongPassword = password != null, cause = locked)
            }
            document.use {
                val total = document.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                val wasEncrypted = document.isEncrypted
                val pageOcr = PageOcr(
                    renderSource = {
                        if (!wasEncrypted) input
                        else {
                            if (!plainCopy.exists()) document.save(plainCopy)
                            plainCopy
                        }
                    },
                    script = script,
                    ocr = ocr,
                    pageImage = pageImage
                )
                val pages = pageOcr.use { reader -> readPages(document, { index -> reader.read(index) }, onProgress) }

                userRequire(pages.any { it.source != PageTextSource.NONE }, R.string.tools_err_pdf_text_none)
                onProgress(total, total, AppStrings.get(R.string.tools_progress_saving_text))
                try {
                    write(title, pages, format, outputFile)
                } catch (error: Throwable) {
                    outputFile.delete()
                    throw error
                }
                PdfTextOutcome(
                    outputFile = outputFile,
                    pageCount = total,
                    textLayerPages = pages.count { it.source == PageTextSource.TEXT_LAYER },
                    ocrPages = pages.withIndex().filter { it.value.source == PageTextSource.OCR }.map { it.index + 1 },
                    emptyPages = pages.withIndex().filter { it.value.source == PageTextSource.NONE }.map { it.index + 1 },
                    originalSizeBytes = input.length()
                )
            }
        } finally {
            input.delete()
            plainCopy.delete()
            pageImage.delete()
        }
    }

    /** Reads pages with the on-device OCR, drawing one page at a time (never all page images at once). */
    private class PageOcr(
        private val renderSource: () -> File,
        private val script: OcrScript,
        private val ocr: OcrEngine,
        private val pageImage: File
    ) : Closeable {
        private var descriptor: ParcelFileDescriptor? = null
        private var renderer: PdfRenderer? = null
        private var prepared = false

        suspend fun read(index: Int): String {
            // Fetch the text model first (once), so a missing download is one clear error, not one per page.
            if (!prepared) {
                ocr.prepare(script)
                prepared = true
            }
            val active = renderer ?: run {
                val opened = ParcelFileDescriptor.open(renderSource(), ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor = opened
                PdfRenderer(opened).also { renderer = it }
            }
            PdfOcrEngine.renderPage(active, index, pageImage)
            return ocr.recognizeDetailed(pageImage, script).text
        }

        override fun close() {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            pageImage.delete()
        }
    }
}
