@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.imaging

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext

enum class CompressionPreset(
    val label: String,
    val description: String,
    val targetScale: Float,
    val jpegQuality: Int
) {
    HIGH("High Quality", "Slight compression, preserves fine detail", 0.85f, 85),
    BALANCED("Balanced", "Recommended for email & messaging", 0.65f, 72),
    MAXIMUM("Maximum Compression", "Smallest file size", 0.45f, 52)
}

data class CompressionResult(
    val outputFile: File,
    val pageCount: Int = 1,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val isSearchablePreserved: Boolean
) {
    val savingsPercentage: Int
        get() {
            if (originalSizeBytes <= 0) return 0
            val saved = originalSizeBytes - compressedSizeBytes
            return ((saved.toDouble() / originalSizeBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
        }
}

object CompressPdfEngine {

    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        preset: CompressionPreset,
        /** Where the result goes; under cache/exports, the only cache folder the FileProvider can share. */
        outputFile: File = PdfToolsEngine.outputLocation(context, "compressed_${UUID.randomUUID()}.pdf")
    ): CompressionResult = withContext(Dispatchers.IO) {
        val outputDir = outputFile.parentFile!!.apply { mkdirs() }
        val tempInput = File(outputDir, "temp_in_${UUID.randomUUID()}.pdf")

        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            } ?: error("Could not open input PDF")

            val originalSize = tempInput.length()
            val pageTotal = runCatching { PDDocument.load(tempInput, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages } }.getOrDefault(1)
            val hasSearchableText = checkSearchableText(tempInput)

            if (hasSearchableText) {
                // Strategy A: Preserve vector text/OCR layers while recompressing embedded images
                val success = runCatching {
                    recompressEmbeddedImages(tempInput, outputFile, preset)
                    true
                }.getOrDefault(false)

                if (success && outputFile.exists() && outputFile.length() < originalSize) {
                    return@withContext CompressionResult(
                        outputFile = outputFile,
                        pageCount = pageTotal,
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = outputFile.length(),
                        isSearchablePreserved = true
                    )
                }
            }

            // Strategy B: Rasterize pages at downscaled target resolution & rebuild PDF
            rasterCompressPdf(context, tempInput, outputFile, preset)

            // A scan that was already small can grow when rebuilt; keep the original rather than a bigger file.
            // The original is untouched, so whatever text it had is still there.
            val keptOriginal = outputFile.length() !in 1L until originalSize
            if (keptOriginal) tempInput.copyTo(outputFile, overwrite = true)
            val finalSize = outputFile.length()
            return@withContext CompressionResult(
                outputFile = outputFile,
                pageCount = pageTotal,
                originalSizeBytes = originalSize,
                compressedSizeBytes = if (finalSize > 0) finalSize else originalSize,
                // Only a rebuilt file that had text lost it; the untouched original, or one with no text, lost nothing.
                isSearchablePreserved = keptOriginal || !hasSearchableText
            )
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            tempInput.delete()
        }
    }

    private fun checkSearchableText(pdfFile: File): Boolean = runCatching {
        // Stops at the first page with text; a 300-page scan with none is read at most 25 pages deep.
        PDDocument.load(pdfFile, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            (1..minOf(doc.numberOfPages, 25)).any { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(doc).isNotBlank()
            }
        }
    }.getOrDefault(false)

    private suspend fun recompressEmbeddedImages(
        inputPdf: File,
        outputPdf: File,
        preset: CompressionPreset
    ) {
        with(PdfToolsEngine) {
            PDDocument.load(inputPdf, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).editable()
        }.use { pdDoc ->
            // Pages often share one resource dictionary or one image (a logo, a background). Each original image is
            // recompressed once and reused; without this a shared image was shrunk again on every page.
            val replaced = HashMap<com.tom_roush.pdfbox.cos.COSBase, PDImageXObject>()
            val produced = HashSet<com.tom_roush.pdfbox.cos.COSBase>()
            for (page in pdDoc.pages) {
                val resources = page.resources ?: continue
                for (xObjectName in resources.xObjectNames) {
                    // Cancel stops at the next image rather than after the whole file.
                    currentCoroutineContext().ensureActive()
                    val xObject = resources.getXObject(xObjectName)
                    // Stencil masks (1-bit text layers of scanner PDFs, painted in the fill colour) are already tiny
                    // and cannot be expressed as a JPEG: re-encoding one paints an opaque picture over the page.
                    if (xObject is PDImageXObject && !xObject.isStencil) {
                        val original = xObject.cosObject
                        if (original in produced) continue
                        val already = replaced[original]
                        if (already != null) {
                            resources.put(xObjectName, already)
                            continue
                        }
                        val bitmap = xObject.image ?: continue
                        val scaledW = (bitmap.width * preset.targetScale).toInt().coerceAtLeast(1)
                        val scaledH = (bitmap.height * preset.targetScale).toInt().coerceAtLeast(1)
                        val scaledBitmap = bitmap.scale(scaledW, scaledH, true)
                        try {
                            val recompressedImage = JPEGFactory.createFromImage(pdDoc, scaledBitmap, preset.jpegQuality / 100f)
                            replaced[original] = recompressedImage
                            produced += recompressedImage.cosObject
                            resources.put(xObjectName, recompressedImage)
                        } finally {
                            if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
                        }
                    }
                }
            }
            pdDoc.save(outputPdf)
        }
    }

    private suspend fun rasterCompressPdf(
        context: Context,
        inputPdf: File,
        outputPdf: File,
        preset: CompressionPreset
    ) {
        val renderDir = File(context.cacheDir, "raster_${UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ScanPage>()

        try {
            ParcelFileDescriptor.open(inputPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()
                        renderer.openPage(index).use { page ->
                            val scale = preset.targetScale * 2.0f
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageFile = File(renderDir, "page_${index.toString().padStart(3, '0')}.jpg")
                            ImageProcessor.saveBitmapJpeg(bitmap, pageFile, preset.jpegQuality)
                            bitmap.recycle()

                            pages.add(
                                ScanPage(
                                    id = "compress-$index",
                                    documentId = "compress",
                                    position = index,
                                    sourcePath = pageFile.absolutePath,
                                    imagePath = pageFile.absolutePath
                                )
                            )
                        }
                    }
                }
            }

            val pdfQuality = when (preset) {
                CompressionPreset.HIGH -> PdfQuality.HIGH
                CompressionPreset.BALANCED -> PdfQuality.BALANCED
                CompressionPreset.MAXIMUM -> PdfQuality.SMALL
            }

            PdfExporter.export(
                pages = pages,
                destination = outputPdf,
                quality = pdfQuality,
                pageSize = PdfPageSize.AUTO,
                searchable = false,
                pageNumbers = false
            )
        } finally {
            renderDir.deleteRecursively()
        }
    }
}
