package com.example.scanpro.imaging

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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PdfToolResult(
    val outputFile: File,
    val pageCount: Int,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val message: String
)

enum class SplitMode(val label: String, val description: String) {
    SINGLE_PAGES("Single Pages", "Split into individual 1-page PDF files"),
    PAGE_RANGES("Page Ranges", "Split by specific ranges (e.g., 1-3, 4-6)"),
    EVERY_N_PAGES("Fixed Chunks", "Split every N pages into separate PDFs")
}

data class PageSpec(
    val originalIndex: Int,
    val rotationDegrees: Int = 0
)

enum class ImageExportFormat(val extension: String, val mimeType: String, val label: String) {
    JPEG("jpg", "image/jpeg", "JPEG Image (.jpg)"),
    PNG("png", "image/png", "PNG Image (.png)")
}

/** How a PDF is protected, as far as opening and editing it goes. */
enum class PdfProtection { NONE, PASSWORD_REQUIRED, RESTRICTED_ONLY }

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
    fun outputLocation(context: Context, name: String): File =
        File(File(context.cacheDir, "exports"), name).also { it.parentFile?.mkdirs() }

    suspend fun mergePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        require(inputUris.size >= 2) { "Select at least 2 PDF files to merge" }
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
                onProgress(index + 1, inputUris.size, "Merging file ${index + 1} of ${inputUris.size}…")

                val tempFile = copyUriToTemp(context, uri, "merge_in_${UUID.randomUUID()}.pdf")
                tempFiles += tempFile
                originalSizeSum += tempFile.length()
                val doc = try {
                    loadPdf(tempFile)
                } catch (error: IllegalArgumentException) {
                    throw IllegalArgumentException("File ${index + 1}: ${error.message}", error)
                } catch (error: Exception) {
                    throw IllegalArgumentException("File ${index + 1} could not be read as a PDF (it may be damaged or password protected)", error)
                }
                sources += doc
                for (i in 0 until doc.numberOfPages) {
                    mergedDoc.addImportedPage(doc.getPage(i))
                    totalPagesMerged++
                }
            }
            require(totalPagesMerged > 0) { "The selected PDFs contain no pages" }

            onProgress(inputUris.size, inputUris.size, "Saving merged PDF…")
            mergedDoc.save(outputFile)
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = totalPagesMerged,
                originalSizeBytes = originalSizeSum,
                outputSizeBytes = outputFile.length(),
                message = "Merged ${inputUris.size} PDFs ($totalPagesMerged total pages)"
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
                require(totalPages > 0) { "The PDF document contains no pages" }

                when (mode) {
                    SplitMode.SINGLE_PAGES -> {
                        for (i in 0 until totalPages) {
                            if (!coroutineContext.isActive) throw InterruptedException("Split operation cancelled")
                            onProgress(i + 1, totalPages, "Splitting page ${i + 1} of $totalPages…")
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
                            onProgress(chunkIndex, (totalPages + chunkSize - 1) / chunkSize, "Splitting chunk $chunkIndex…")
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
                        require(ranges.isNotEmpty()) { "No valid page ranges provided" }
                        ranges.forEachIndexed { idx, range ->
                            if (!coroutineContext.isActive) throw InterruptedException("Split operation cancelled")
                            onProgress(idx + 1, ranges.size, "Extracting range ${idx + 1} of ${ranges.size}…")
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
        require(selectedPageIndices.isNotEmpty()) { "Select at least one page to extract" }
        outputFile.parentFile?.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "extract_in_${UUID.randomUUID()}.pdf")

        try {
            val originalSize = tempFile.length()
            PDDocument().use { extractedDoc -> loadPdf(tempFile).use { doc ->
                selectedPageIndices.forEachIndexed { index, pageIdx ->
                    if (!coroutineContext.isActive) throw InterruptedException("Extract operation cancelled")
                    onProgress(index + 1, selectedPageIndices.size, "Extracting page ${index + 1} of ${selectedPageIndices.size}…")
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
                message = "Successfully extracted ${selectedPageIndices.size} pages"
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
                    onProgress(i + 1, totalPages, "Rotating page ${i + 1} of $totalPages…")
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
                message = "Successfully rotated PDF pages"
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
        require(pageSpecs.isNotEmpty()) { "At least one page must remain in the PDF" }
        outputFile.parentFile?.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "organize_in_${UUID.randomUUID()}.pdf")

        try {
            val originalSize = tempFile.length()
            PDDocument().use { newDoc -> loadPdf(tempFile).use { doc ->
                pageSpecs.forEachIndexed { index, spec ->
                    if (!coroutineContext.isActive) throw InterruptedException("Organize operation cancelled")
                    onProgress(index + 1, pageSpecs.size, "Processing page ${index + 1} of ${pageSpecs.size}…")
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
                message = "Successfully reordered and saved ${pageSpecs.size} pages"
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
        require(imageUris.isNotEmpty()) { "Select at least one image" }
        outputFile.parentFile?.mkdirs()

        val tempDir = File(context.cacheDir, "img2pdf_${UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ScanPage>()

        try {
            imageUris.forEachIndexed { index, uri ->
                if (!coroutineContext.isActive) throw InterruptedException("Images to PDF operation cancelled")
                onProgress(index + 1, imageUris.size, "Loading image ${index + 1} of ${imageUris.size}…")
                val imgFile = File(tempDir, "img_$index.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(imgFile).use { output -> input.copyTo(output) }
                } ?: error("Could not read image file at position ${index + 1}")
                // Gallery photos carry their rotation in EXIF, which the PDF writer ignores; bake it in.
                val oriented = ImageProcessor.decodeOrientedScaled(imgFile.absolutePath, 4200)
                    ?: error("Image ${index + 1} could not be decoded")
                try {
                    ImageProcessor.saveBitmapJpeg(oriented, imgFile, 95)
                } finally {
                    oriented.recycle()
                }

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

            onProgress(imageUris.size, imageUris.size, "Creating PDF document…")
            PdfExporter.export(
                pages = pages,
                destination = outputFile,
                quality = quality,
                pageSize = pageSize,
                searchable = false,
                pageNumbers = false
            )

            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = imageUris.size,
                originalSizeBytes = pages.sumOf { File(it.imagePath).length() },
                outputSizeBytes = outputFile.length(),
                message = "Created PDF with ${imageUris.size} pages"
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
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val tempFile = copyUriToTemp(context, inputUri, "pdf2img_in_${UUID.randomUUID()}.pdf")
        val imageFiles = mutableListOf<File>()

        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val totalPages = renderer.pageCount
                    for (i in 0 until totalPages) {
                        if (!coroutineContext.isActive) throw InterruptedException("PDF to images operation cancelled")
                        onProgress(i + 1, totalPages, "Exporting page ${i + 1} of $totalPages as ${format.extension.uppercase()}…")

                        renderer.openPage(i).use { page ->
                            // 2.5x for sharp text, but capped so a poster-sized page can't run out of memory.
                            val scale = minOf(2.5f, 4000f / maxOf(page.width, page.height))
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
            PdfSecurity.protect(tempFile, outputFile, password)
            var pageCount = 0
            PDDocument.load(outputFile, password).use { pageCount = it.numberOfPages }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = pageCount,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = "PDF protected with password successfully"
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
            PdfSecurity.unlock(tempFile, outputFile, password)
            var pageCount = 0
            PDDocument.load(outputFile).use { pageCount = it.numberOfPages }
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = pageCount,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputFile.length(),
                message = "PDF password protection removed successfully"
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
        zip
    }

    /** Opens a PDF, turning "wrong password" on a protected file into a message that says what to do. */
    internal fun loadPdf(file: File): PDDocument = try {
        PDDocument.load(file)
    } catch (error: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        throw IllegalArgumentException("This PDF is password-protected. Unlock it first with the Unlock PDF tool.", error)
    }

    internal fun copyUriToTemp(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: error("Unable to read PDF file from selected location")
        } catch (error: Throwable) {
            // A copy that failed half way must not leave part of the user's file behind in the cache.
            tempFile.delete()
            throw error
        }
        return tempFile
    }

    private fun parsePageRanges(rangeStr: String, maxPages: Int): List<IntRange> =
        PageRanges.parse(rangeStr, maxPages).getOrElse { error(it.message ?: "Invalid page ranges") }
}
