package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repair PDF: gets a damaged PDF that will not open, or opens with missing pages, into a clean file again.
 *
 * Two steps, in order. First the file is read again with PDFBox's forgiving parser, which rebuilds a missing or broken
 * cross-reference table by scanning the file for its objects, and written out fresh: the text and everything else
 * stay as they were. If that fails, each page the phone's own PDF renderer can still draw is rebuilt as a picture of
 * the page: the pages come back but their text is no longer selectable, and the result says so.
 * A password-protected file is not repaired (it cannot be read without the password): Unlock comes first.
 */
object PdfRepairEngine {

    suspend fun repairPdf(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "repair_in_${UUID.randomUUID()}.pdf")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_repair_empty)

            val rewritten = rewriteStructure(tempFile, outputFile)
            if (rewritten > 0) {
                return@withContext PdfToolResult(
                    outputFile = outputFile,
                    pageCount = rewritten,
                    originalSizeBytes = originalSize,
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.plural(R.plurals.tools_result_repaired, rewritten, rewritten)
                )
            }

            // The structure could not be read. Whatever pages the system renderer can still draw are kept as pictures.
            val scratch = File(context.cacheDir, "repair_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val pictures = runCatching {
                    PdfToolsEngine.pdfToImages(context, inputUri, ImageExportFormat.JPEG, scratch, onProgress, null, 2.0f)
                }.getOrDefault(emptyList())
                userRequire(pictures.isNotEmpty(), R.string.tools_err_repair_failed)
                val rebuilt = PdfToolsEngine.imagesToPdf(
                    context, pictures.map(Uri::fromFile), PdfPageSize.AUTO, PdfQuality.HIGH, outputFile, onProgress
                )
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = rebuilt.pageCount,
                    originalSizeBytes = originalSize,
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.plural(R.plurals.tools_result_repaired_pictures, rebuilt.pageCount, rebuilt.pageCount)
                )
            } finally {
                scratch.deleteRecursively()
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Pages written to [output] after re-reading [source] with the forgiving parser; 0 when it could not be read. */
    internal fun rewriteStructure(source: File, output: File): Int {
        return try {
            PdfToolsEngine.openPdf(source).use { document ->
                val pages = document.numberOfPages
                if (pages > 0) document.save(output)
                pages
            }
        } catch (_: InvalidPasswordException) {
            userError(R.string.tools_err_repair_protected)
        } catch (_: Exception) {
            // Whatever went wrong half way must not leave a partly written file for the next step to trip over.
            output.delete()
            0
        }
    }
}
