package com.example.scanpro.imaging

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userRequire
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF to PowerPoint (as iLovePDF offers): every page becomes a slide holding a picture of the page, sized to the
 * page's shape. Scanned pages have no editable text to carry over anyway; for a typed PDF the slides still look exactly
 * like the pages, which is what a presentation of a document needs. The result message says the slides are pictures.
 */
object PdfToPptEngine {
    suspend fun convert(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val scratch = File(context.cacheDir, "pdf2ppt_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            // About 150 dpi: sharp on a projector and a laptop screen, without a huge file.
            val pages = PdfToolsEngine.pdfToImages(context, inputUri, ImageExportFormat.JPEG, scratch, onProgress, null, 2.0f)
            userRequire(pages.isNotEmpty(), R.string.tools_err_no_pages)
            val slides = pages.map { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                PptxExporter.Slide(file, bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1))
            }
            try {
                PptxExporter.export(slides, outputFile)
            } catch (error: Throwable) {
                outputFile.delete()
                throw error
            }
            PdfToolResult(
                outputFile = outputFile,
                pageCount = slides.size,
                // Zero: the result is not a smaller copy of the PDF, so no "saved" badge.
                originalSizeBytes = 0,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_result_pdf2ppt, slides.size, slides.size)
            )
        } finally {
            scratch.deleteRecursively()
        }
    }
}
