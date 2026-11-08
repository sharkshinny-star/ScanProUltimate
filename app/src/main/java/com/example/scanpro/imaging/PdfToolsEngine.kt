package com.example.scanpro.imaging

import com.example.scanpro.core.text.userRequire
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.UserFacingPluralException
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.core.text.AppStrings
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.annotation.StringRes
import com.example.scanpro.R

data class PdfToolResult(
    val outputFile: File,
    val pageCount: Int,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val message: String
)

enum class SplitMode(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    SINGLE_PAGES(R.string.tools_split_mode_single, R.string.tools_split_mode_single_desc),
    PAGE_RANGES(R.string.tools_split_mode_ranges, R.string.tools_split_mode_ranges_desc),
    EVERY_N_PAGES(R.string.tools_split_mode_chunks, R.string.tools_split_mode_chunks_desc)
}

data class PageSpec(
    val originalIndex: Int,
    val rotationDegrees: Int = 0
)

enum class ImageExportFormat(val extension: String) {
    JPEG("jpg"),
    PNG("png")
}

/** How a PDF is protected, as far as opening and editing it goes. */
enum class PdfProtection { NONE, PASSWORD_REQUIRED, RESTRICTED_ONLY }

/**
 * Merge reached file [index] (0-based), [uri], which needs a password to open: none was given for it, or
 * [wrongPassword] is true because the one given did not work. The Tools screen asks for it and merges again.
 * Anywhere else it reads as the usual "unlock it first" message.
 */
class MergePasswordNeededException(val index: Int, val uri: Uri, val wrongPassword: Boolean, cause: Throwable? = null) :
    UserFacingException(R.string.tools_err_password_protected, cause = cause)

object PdfToolsEngine {

    /**
     * Tells whether a PDF needs a password to open (PASSWORD_REQUIRED), opens freely but has editing or
     * copying restrictions (RESTRICTED_ONLY), or has no protection at all (NONE).
     */
    suspend fun inspectProtection(context: Context, inputUri: Uri): PdfProtection = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTemp(context, inputUri, "inspect_${UUID.randomUUID()}.pdf")
        try {
            PdfSecurity.protection(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Where tool results go: under cache/exports/, the only cache folder the FileProvider shares,
     * and which DocumentRepository clears of anything older than a day.
     */
    fun outputLocation(context: Context, name: String): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val plain = File(dir, name)
        if (!plain.exists()) return plain
        // A second run on the same file gets " (2)", not a silently replaced result.
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var n = 2
        while (File(dir, "$base ($n)" + if (extension.isEmpty()) "" else ".$extension").exists()) n++
        return File(dir, "$base ($n)" + if (extension.isEmpty()) "" else ".$extension")
    }

    /** The picked file's own name without its extension, safe for a file name, or [fallback]. */
    fun sourceBaseName(context: Context, uri: Uri, fallback: String = "PDF"): String {
        val shown = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        val base = shown.orEmpty().substringAfterLast('/').substringBeforeLast('.').trim()
        return com.example.scanpro.data.fileSafeName(base.ifBlank { fallback })
    }

    /**
     * Merges [inputUris] in order. A file that needs a password to open is opened with its entry in [passwords]; when
     * it has none, or that one is wrong, the merge stops with [MergePasswordNeededException] so the user can be asked.
     * The merged PDF itself has no password.
     */
    suspend fun mergePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        passwords: Map<Uri, String> = emptyMap()
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(inputUris.size >= 2, R.string.tools_err_merge_two)
        outputFile.parentFile?.mkdirs()

        val mergedDoc = PDDocument()
        // Pages added from a source document still read from it when the merged file is saved, so the
        // sources (and the files behind them) must stay open until after the save. Closing each one as
        // soon as its pages were copied is what produced "COSStream has been closed".
        val sources = mutableListOf<PDDocument>()
        val tempFiles = mutableListOf<File>()
        var totalPagesMerged = 0
        var originalSizeSum = 0L

        try {
            inputUris.forEachIndexed { index, uri ->
                if (!coroutineContext.isActive) throw InterruptedException("Merge operation cancelled")
                onProgress(index + 1, inputUris.size, AppStrings.get(R.string.tools_progress_merging, index + 1, inputUris.size))

                val tempFile = copyUriToTemp(context, uri, "merge_in_${UUID.randomUUID()}.pdf")
                tempFiles += tempFile
                originalSizeSum += tempFile.length()
                val password = passwords[uri]
                val doc = try {
                    openPdf(tempFile, password.orEmpty())
                } catch (locked: InvalidPasswordException) {
                    throw MergePasswordNeededException(index, uri, wrongPassword = password != null, cause = locked)
                } catch (error: IllegalArgumentException) {
                    throw UserFacingException(R.string.tools_err_file_reason, index + 1, error.message.orEmpty(), cause = error)
                } catch (error: Exception) {
                    throw UserFacingException(R.string.tools_err_file_unreadable, index + 1, cause = error)
                }
                sources += doc
                for (i in 0 until doc.numberOfPages) {
                    mergedDoc.addImportedPage(doc.getPage(i))
                    totalPagesMerged++
                }
            }
            userRequire(totalPagesMerged > 0, R.string.tools_err_merge_no_pages)

            onProgress(inputUris.size, inputUris.size, AppStrings.get(R.string.tools_progress_saving_merged))
            mergedDoc.save(outputFile)
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = totalPagesMerged,
                originalSizeBytes = originalSizeSum,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_result_merged, totalPagesMerged, inputUris.size, totalPagesMerged)
            )
        } finally {
            runCatching { mergedDoc.close() }
            sources.forEach { runCatching { it.close() } }
            tempFiles.forEach { it.delete() }
        }
    }

    suspend fun splitPdf(
        context: Context,
        inputUri: Uri,
        mode: SplitMode,
        customRangesText: String = "",
        everyNPages: Int = 1,
        outputDir: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "split_in_${UUID.randomUUID()}.pdf")
        val outputFiles = mutableListOf<File>()

        try {
            loadPdf(tempFile).use { doc ->
                val totalPages = doc.numberOfPages
                userRequire(totalPages > 0, R.string.tools_err_no_pages)
                userRequire(totalPages > 1, R.string.tools_split_single_page_desc)

                when (mode) {
                    SplitMode.SINGLE_PAGES -> {
                        for (i in 0 until totalPages) {
                            if (!coroutineContext.isActive) throw InterruptedException("Split operation cancelled")
                            onProgress(i + 1, totalPages, AppStrings.get(R.string.tools_progress_splitting_page, i + 1, totalPages))
                            val outFile = File(outputDir, "page_${(i + 1).toString().padStart(3, '0')}.pdf")
                            PDDocument().use { singleDoc ->
                                singleDoc.addImportedPage(doc.getPage(i))
                                singleDoc.save(outFile)
                            }
                            outputFiles.add(outFile)
                        }
                    }
                    SplitMode.EVERY_N_PAGES -> {
                        val chunkSize = everyNPages.coerceAtLeast(1)
                        var chunkIndex = 1
                        var pageIndex = 0
                        while (pageIndex < totalPages) {
                            if (!coroutineContext.isActive) throw InterruptedException("Split operation cancelled")
                            val end = (pageIndex + chunkSize).coerceAtMost(totalPages)
                            onProgress(chunkIndex, (totalPages + chunkSize - 1) / chunkSize, AppStrings.get(R.string.tools_progress_splitting_chunk, chunkIndex))
                            val outFile = File(outputDir, "part_${chunkIndex}_pages_${pageIndex + 1}_to_$end.pdf")
                            PDDocument().use { chunkDoc ->
                                for (i in pageIndex until end) chunkDoc.addImportedPage(doc.getPage(i))
                                chunkDoc.save(outFile)
                            }
                            outputFiles.add(outFile)
                            chunkIndex++
                            pageIndex = end
                        }
                    }
                    SplitMode.PAGE_RANGES -> {
                        val ranges = parsePageRanges(customRangesText, totalPages)
                        userRequire(ranges.isNotEmpty(), R.string.tools_err_no_ranges)
                        ranges.forEachIndexed { idx, range ->
                            if (!coroutineContext.isActive) throw InterruptedException("Split operation cancelled")
                            onProgress(idx + 1, ranges.size, AppStrings.get(R.string.tools_progress_extracting_range, idx + 1, ranges.size))
                            val outFile = File(outputDir, "range_${idx + 1}_pages_${range.first}_to_${range.last}.pdf")
                            PDDocument().use { rangeDoc ->
                                for (pageNum in range) {
                                    if (pageNum in 1..totalPages) rangeDoc.addImportedPage(doc.getPage(pageNum - 1))
                                }
                                rangeDoc.save(outFile)
                            }
                            outputFiles.add(outFile)
                        }
                    }
                }
            }
            return@withContext outputFiles
        } finally {
            tempFile.delete()
        }
    }

    suspend fun extractPages(
        context: Context,
        inputUri: Uri,
        selectedPageIndices: List<Int>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(selectedPageIndices.isNotEmpty(), R.string.tools_err_extract_none)
        outputFile.parentFile?.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "extract_in_${UUID.randomUUID()}.pdf")

        try {
            val originalSize = tempFile.length()
            PDDocument().use { extractedDoc -> loadPdf(tempFile).use { doc ->
                selectedPageIndices.forEachIndexed { index, pageIdx ->
                    if (!coroutineContext.isActive) throw InterruptedException("Extract operation cancelled")
                    onProgress(index + 1, selectedPageIndices.size, AppStrings.get(R.string.tools_progress_extracting_page, index + 1, selectedPageIndices.size))
                    if (pageIdx in 0 until doc.numberOfPages) {
                        extractedDoc.addImportedPage(doc.getPage(pageIdx))
                    }
                }
                extractedDoc.save(outputFile)
            } }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = selectedPageIndices.size,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_result_extracted, selectedPageIndices.size, selectedPageIndices.size)
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun rotatePages(
        context: Context,
        inputUri: Uri,
        rotationsMap: Map<Int, Int>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "rotate_in_${UUID.randomUUID()}.pdf")

        try {
            val originalSize = tempFile.length()
            var totalPages = 0
            loadPdf(tempFile).use { doc ->
                totalPages = doc.numberOfPages
                for (i in 0 until totalPages) {
                    if (!coroutineContext.isActive) throw InterruptedException("Rotation operation cancelled")
                    onProgress(i + 1, totalPages, AppStrings.get(R.string.tools_progress_rotating, i + 1, totalPages))
                    val addDegrees = rotationsMap[i] ?: 0
                    if (addDegrees != 0) {
                        val page = doc.getPage(i)
                        page.rotation = Math.floorMod(page.rotation + addDegrees, 360)
                    }
                }
                doc.save(outputFile)
            }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = totalPages,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.get(R.string.tools_result_rotated)
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun organizePages(
        context: Context,
        inputUri: Uri,
        pageSpecs: List<PageSpec>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(pageSpecs.isNotEmpty(), R.string.tools_err_keep_one_page)
        outputFile.parentFile?.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "organize_in_${UUID.randomUUID()}.pdf")

        try {
            val originalSize = tempFile.length()
            PDDocument().use { newDoc -> loadPdf(tempFile).use { doc ->
                pageSpecs.forEachIndexed { index, spec ->
                    if (!coroutineContext.isActive) throw InterruptedException("Organize operation cancelled")
                    onProgress(index + 1, pageSpecs.size, AppStrings.get(R.string.tools_progress_processing_page, index + 1, pageSpecs.size))
                    if (spec.originalIndex in 0 until doc.numberOfPages) {
                        val page = doc.getPage(spec.originalIndex)
                        if (spec.rotationDegrees != 0) {
                            page.rotation = Math.floorMod(page.rotation + spec.rotationDegrees, 360)
                        }
                        newDoc.addImportedPage(page)
                    }
                }
                newDoc.save(outputFile)
            } }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = pageSpecs.size,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_result_organized, pageSpecs.size, pageSpecs.size)
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        pageSize: PdfPageSize = PdfPageSize.AUTO,
        quality: PdfQuality = PdfQuality.BALANCED,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(imageUris.isNotEmpty(), R.string.tools_err_no_images)
        outputFile.parentFile?.mkdirs()

        val tempDir = File(context.cacheDir, "img2pdf_${UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ScanPage>()
        var originalSize = 0L

        try {
            imageUris.forEachIndexed { index, uri ->
                if (!coroutineContext.isActive) throw InterruptedException("Images to PDF operation cancelled")
                onProgress(index + 1, imageUris.size, AppStrings.get(R.string.tools_progress_loading_image, index + 1, imageUris.size))
                // The picked file byte for byte. It is decoded once, when its page is drawn (PdfExporter.decodePicture
                // turns it upright and lays it on white), so the PDF is the only compression it goes through.
                val imgFile = File(tempDir, "img_$index")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(imgFile).use { output -> input.copyTo(output) }
                } ?: userError(R.string.tools_err_image_unreadable, index + 1)
                // Only the header is read here: a file that is not a picture is named before any page is drawn.
                ImageProcessor.imageAspect(imgFile.absolutePath) ?: userError(R.string.tools_err_image_undecodable, index + 1)
                originalSize += imgFile.length()

                pages.add(
                    ScanPage(
                        id = "img2pdf-$index",
                        documentId = "img2pdf",
                        position = index,
                        sourcePath = imgFile.absolutePath,
                        imagePath = imgFile.absolutePath
                    )
                )
            }

            val creating = AppStrings.get(R.string.tools_progress_creating_pdf)
            var current = 0
            PdfExporter.export(
                pages = pages,
                destination = outputFile,
                quality = quality,
                pageSize = pageSize,
                searchable = false,
                pageNumbers = false,
                // Only the header was checked above: a picture whose pixels can't be read is still named as a picture.
                decode = { path, max -> PdfExporter.decodePicture(path, max) ?: userError(R.string.tools_err_image_undecodable, current) },
                beforePage = { number, total ->
                    current = number
                    // Decoding is the slow part, so Cancel is honoured between pages here too.
                    if (!coroutineContext.isActive) throw InterruptedException("Images to PDF operation cancelled")
                    onProgress(number, total, creating)
                }
            )

            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = imageUris.size,
                // What the user picked, not a copy made along the way.
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_result_images_to_pdf, imageUris.size, imageUris.size)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun pdfToImages(
        context: Context,
        inputUri: Uri,
        format: ImageExportFormat = ImageExportFormat.JPEG,
        outputDir: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        /** 1-based pages to export; null for every page. */
        pages: List<Int>? = null,
        /** Render scale over the PDF's 72 dpi: 2 is about 150 dpi, 2.5 is sharp on screen, 4 is print quality. */
        scale: Float = 2.5f
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "pdf2img_in_${UUID.randomUUID()}.pdf")
        val imageFiles = mutableListOf<File>()

        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val totalPages = renderer.pageCount
                    val chosen = pages?.filter { it in 1..totalPages }?.distinct()?.sorted() ?: (1..totalPages).toList()
                    userRequire(chosen.isNotEmpty(), R.string.tools_err_pages_missing)
                    chosen.forEachIndexed { step, pageNumber ->
                        val i = pageNumber - 1
                        if (!coroutineContext.isActive) throw InterruptedException("PDF to images operation cancelled")
                        onProgress(step + 1, chosen.size, AppStrings.get(R.string.tools_progress_exporting_page, pageNumber, format.extension.uppercase()))

                        renderer.openPage(i).use { page ->
                            // Capped so a poster-sized page can't run out of memory.
                            val scale = minOf(scale, 4000f / maxOf(page.width, page.height))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            // PdfRenderer draws onto what is there; transparent pixels turn black in a JPEG.
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val imgFile = File(outputDir, "page_${(i + 1).toString().padStart(3, '0')}.${format.extension}")
                            FileOutputStream(imgFile).use { out ->
                                if (format == ImageExportFormat.JPEG) {
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                } else {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }
                            bitmap.recycle()
                            imageFiles.add(imgFile)
                        }
                    }
                }
            }
            return@withContext imageFiles
        } finally {
            tempFile.delete()
        }
    }

    suspend fun protectPdf(
        context: Context,
        inputUri: Uri,
        password: String,
        outputFile: File
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTemp(context, inputUri, "protect_in_${UUID.randomUUID()}.pdf")
        try {
            val originalSize = tempFile.length()
            if (PdfSecurity.protection(tempFile) == PdfProtection.PASSWORD_REQUIRED) {
                userError(R.string.tools_err_already_protected)
            }
            PdfSecurity.protect(tempFile, outputFile, password)
            var pageCount = 0
            PDDocument.load(outputFile, password).use { pageCount = it.numberOfPages }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = pageCount,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = AppStrings.get(R.string.tools_result_protected)
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun unlockPdf(
        context: Context,
        inputUri: Uri,
        password: String,
        outputFile: File
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTemp(context, inputUri, "unlock_in_${UUID.randomUUID()}.pdf")
        try {
            val originalSize = tempFile.length()
            val before = PdfSecurity.protection(tempFile)
            PdfSecurity.unlock(tempFile, outputFile, password)
            var pageCount = 0
            PDDocument.load(outputFile).use { pageCount = it.numberOfPages }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = pageCount,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = if (before == PdfProtection.PASSWORD_REQUIRED) AppStrings.get(R.string.tools_result_password_removed)
                else AppStrings.get(R.string.tools_result_restrictions_removed)
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Adds a page taken from another document. Resources, page size, crop box and rotation may be
     * inherited from the source's page tree, which does not travel with the page, so they are copied
     * onto the page itself first. Without this such pages come out blank or the wrong size.
     */
    private fun PDDocument.addImportedPage(page: PDPage) {
        page.resources = page.resources
        page.mediaBox = page.mediaBox
        page.cropBox = page.cropBox
        page.rotation = page.rotation
        addPage(page)
    }

    /** One file is returned as it is; several are bundled into a single zip so they can be shared together. */
    suspend fun bundleOutputs(context: Context, files: List<File>, name: String): File = withContext(Dispatchers.IO) {
        if (files.size == 1) return@withContext files.first()
        val zip = outputLocation(context, "$name.zip")
        ZipOutputStream(FileOutputStream(zip).buffered()).use { out ->
            files.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        // The zip is the result; keeping the loose pages beside it would use the space twice.
        files.forEach { it.delete() }
        files.firstOrNull()?.parentFile?.takeIf { it != zip.parentFile }?.delete()
        zip
    }

    /** Opens a PDF, turning "wrong password" on a protected file into a message that says what to do. */
    internal fun loadPdf(file: File): PDDocument = try {
        openPdf(file)
    } catch (error: InvalidPasswordException) {
        throw UserFacingException(R.string.tools_err_password_protected, cause = error)
    }

    /** Opens a PDF with [password] ("" when there is none); a missing or wrong one throws [InvalidPasswordException]. */
    private fun openPdf(file: File, password: String = ""): PDDocument =
        // Streams go to a scratch file rather than the heap, so a 200-page scan doesn't take 200 pages of memory.
        PDDocument.load(file, password, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).editable()

    /**
     * A PDF that opens without a password can still carry an owner-password encryption dictionary (print or copy
     * restrictions). PDFBox refuses to save such a document as it is, so every tool that edits one in place
     * (rotate, fill form, make searchable, compress) would fail at the very end. Dropping the dictionary writes
     * the file unrestricted, as the Unlock tool does.
     */
    internal fun PDDocument.editable(): PDDocument = apply { if (isEncrypted) isAllSecurityToBeRemoved = true }

    internal fun copyUriToTemp(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: userError(R.string.tools_err_unreadable_location)
        } catch (error: Throwable) {
            // A copy that failed half way must not leave part of the user's file behind in the cache.
            tempFile.delete()
            throw error
        }
        return tempFile
    }

    private fun parsePageRanges(rangeStr: String, maxPages: Int): List<IntRange> =
        PageRanges.parse(rangeStr, maxPages).getOrElse {
            // PageRanges explains what is wrong in the user's language; anything else gets the general message.
            if (it is UserFacingException || it is UserFacingPluralException) throw it
            userError(R.string.tools_err_invalid_ranges, cause = it)
        }
}
