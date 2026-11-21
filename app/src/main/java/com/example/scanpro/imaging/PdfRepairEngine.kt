package com.example.scanpro.imaging

import com.example.scanpro.core.concurrent.BackgroundWork
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

    /** Pages are rebuilt as pictures at twice the PDF's 72 dpi (144 dpi), capped like PDF to images. */
    private const val REBUILD_SCALE = 2f

    suspend fun repairPdf(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "repair_in_${UUID.randomUUID()}.pdf")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_repair_empty)

            // How many pages the phone's own viewer still sees: the yardstick for "nothing was lost" (F211). The
            // forgiving parser can rebuild a file with fewer pages than it had, and that used to be reported as repaired.
            val viewerPages = viewerPageCount(tempFile)
            val rewritten = rewriteStructure(tempFile, outputFile)
            val rewrittenOpens = if (rewritten > 0 && viewerPages != null) viewerPageCount(outputFile) != null else null
            fun rewrittenResult(): PdfToolResult = PdfToolResult(
                outputFile = outputFile,
                pageCount = rewritten,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = if (viewerPages != null && viewerPages > rewritten) {
                    AppStrings.plural(R.plurals.tools_repair_partial, rewritten, rewritten, viewerPages)
                } else AppStrings.plural(R.plurals.tools_result_repaired, rewritten, rewritten)
            )
            when (RepairDecision.pick(rewritten, viewerPages, rewrittenOpens)) {
                RepairDecision.Pick.REWRITTEN -> rewrittenResult()
                RepairDecision.Pick.NOTHING -> userError(R.string.tools_err_repair_failed)
                RepairDecision.Pick.PICTURES -> {
                    // Whatever pages the system renderer can still draw are kept as pictures, at their own page size.
                    val staged = File(outputFile.parentFile, "${outputFile.name}.pictures")
                    try {
                        val rebuilt = try {
                            rebuildAsPictures(context, tempFile, staged, onProgress)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (cancelled: InterruptedException) {
                            throw cancelled
                        } catch (_: Exception) {
                            0
                        }
                        when {
                            rebuilt > rewritten -> {
                                staged.copyTo(outputFile, overwrite = true)
                                PdfToolResult(
                                    outputFile = outputFile,
                                    pageCount = rebuilt,
                                    originalSizeBytes = originalSize,
                                    outputSizeBytes = outputFile.length(),
                                    message = AppStrings.plural(R.plurals.tools_result_repaired_pictures, rebuilt, rebuilt)
                                )
                            }
                            rewritten > 0 -> rewrittenResult()
                            else -> {
                                outputFile.delete()
                                userError(R.string.tools_err_repair_failed)
                            }
                        }
                    } finally {
                        staged.delete()
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Pages the system renderer finds in [file]; null when it cannot open it. */
    internal fun viewerPageCount(file: File): Int? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
    }.getOrNull()

    /** Draws every page the system renderer can read into [output] as a picture, each page at its own size; the page count. */
    private suspend fun rebuildAsPictures(
        context: Context,
        source: File,
        output: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Int {
        val scratch = File(context.cacheDir, "repair_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val pages = mutableListOf<ScanPage>()
            val sizes = mutableListOf<Pair<Int, Int>>()
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    for (index in 0 until total) {
                        if (!currentCoroutineContext().isActive) throw InterruptedException("Repair cancelled")
                        onProgress(index + 1, total, AppStrings.get(R.string.tools_progress_exporting_page, index + 1, "JPG"))
                        renderer.openPage(index).use { page ->
                            val (width, height) = PdfToImagesMath.pixelSize(page.width, page.height, REBUILD_SCALE)
                            val bitmap = createBitmap(width, height)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val file = File(scratch, "page_$index.jpg")
                                ImageProcessor.saveBitmapJpeg(bitmap, file, PdfToImagesMath.JPEG_QUALITY)
                                pages += ScanPage(id = "repair-$index", documentId = "repair", position = index, sourcePath = file.absolutePath, imagePath = file.absolutePath)
                                sizes += page.width.coerceAtLeast(1) to page.height.coerceAtLeast(1)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            if (pages.isEmpty()) return 0
            onProgress(pages.size, pages.size, AppStrings.get(R.string.tools_progress_creating_pdf))
            PdfExporter.export(pages, output, PdfQuality.HIGH, PdfPageSize.AUTO, searchable = false, pageNumbers = false, pointSizes = sizes)
            return pages.size
        } finally {
            scratch.deleteRecursively()
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
