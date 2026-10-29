package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.data.OcrScript
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** What "Make searchable" did, page by page. */
data class PdfOcrOutcome(
    /** Null when no page needed text, so there was nothing to save. */
    val outputFile: File?,
    val pageCount: Int,
    val madeSearchable: Int,
    val alreadyHadText: Int,
    val noTextFound: Int,
    val originalSizeBytes: Long
)

/**
 * Makes a PDF searchable: pages that already carry text are left alone; every other page is drawn, read with
 * the on-device OCR, and given an invisible text layer on top of its original content, so it looks the same.
 */
object PdfOcrEngine {
    /** About 300 dpi, the usual scanning resolution, but never more than this many pixels on the long side. */
    private const val RENDER_DPI = 300f
    private const val MAX_RENDER_SIDE = 3000f

    suspend fun makeSearchable(
        context: Context,
        inputUri: Uri,
        script: OcrScript,
        ocr: OcrEngine,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfOcrOutcome = withContext(Dispatchers.IO) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "ocr_in_${UUID.randomUUID()}.pdf")
        val pageImage = File(context.cacheDir, "ocr_page_${UUID.randomUUID()}.jpg")
        try {
            PdfToolsEngine.loadPdf(input).use { document ->
                val total = document.numberOfPages
                require(total > 0) { "This PDF has no pages" }
                val stripper = PDFTextStripper()
                val needsText = (0 until total).filter { index ->
                    coroutineContext.ensureActive()
                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    runCatching { stripper.getText(document) }.getOrDefault("").isBlank()
                }
                if (needsText.isEmpty()) {
                    return@withContext PdfOcrOutcome(null, total, 0, total, 0, input.length())
                }
                // Fetch the text model first (once), so a missing download is one clear error, not one per page.
                ocr.prepare(script)

                val font = PdfTextLayer.loadFont(document)
                var searchable = 0
                var empty = 0
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        needsText.forEachIndexed { step, index ->
                            if (!coroutineContext.isActive) throw InterruptedException("OCR cancelled")
                            onProgress(step + 1, needsText.size, "Reading page ${index + 1} of $total…")
                            renderPage(renderer, index, pageImage)
                            val result = ocr.recognizeDetailed(pageImage, script)
                            val written = PdfTextLayer.addInvisibleWords(document, document.getPage(index), result.words, font)
                            if (written > 0) searchable++ else empty++
                        }
                    }
                }
                if (searchable == 0) {
                    return@withContext PdfOcrOutcome(null, total, 0, total - needsText.size, empty, input.length())
                }
                onProgress(needsText.size, needsText.size, "Saving searchable PDF…")
                outputFile.parentFile?.mkdirs()
                document.save(outputFile)
                PdfOcrOutcome(outputFile, total, searchable, total - needsText.size, empty, input.length())
            }
        } finally {
            input.delete()
            pageImage.delete()
        }
    }

    /** Draws one page as it is shown (rotation applied) onto a white background and saves it for OCR. */
    private fun renderPage(renderer: PdfRenderer, index: Int, target: File) {
        renderer.openPage(index).use { page ->
            val scale = minOf(RENDER_DPI / 72f, MAX_RENDER_SIDE / maxOf(page.width, page.height))
            val bitmap = createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1))
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
