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
import com.example.scanpro.R
import com.example.scanpro.core.text.userError

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
    val isSearchablePreserved: Boolean,
    /** A custom target was asked for and even the strongest squeeze stayed above it. */
    val missedTarget: Boolean = false
) {
    val savingsPercentage: Int
        get() {
            if (originalSizeBytes <= 0) return 0
            val saved = originalSizeBytes - compressedSizeBytes
            return ((saved.toDouble() / originalSizeBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
        }
}

object CompressPdfEngine {
    private const val MAX_TARGET_PASSES = 6
    private const val MAX_RASTER_DIMENSION = 4000f
    /** A result at least this fraction of the target is close enough; the search stops. */
    private const val CLOSE_ENOUGH = 0.85
    private const val MIN_TARGET_SCALE = 0.25f
    private const val MIN_TARGET_QUALITY = 30


    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        preset: CompressionPreset,
        /** Where the result goes; under cache/exports, the only cache folder the FileProvider can share. */
        outputFile: File = PdfToolsEngine.outputLocation(context, "compressed_${UUID.randomUUID()}.pdf"),
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        customTargetBytes: Long? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        val outputDir = outputFile.parentFile!!.apply { mkdirs() }
        val tempInput = File(outputDir, "temp_in_${UUID.randomUUID()}.pdf")

        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            } ?: userError(R.string.err_could_not_open_input_pdf)

            val originalSize = tempInput.length()
            if (customTargetBytes != null && originalSize > 0 && customTargetBytes >= originalSize) {
                userError(R.string.tools_compress_err_target_larger_engine)
            }
            val pageTotal = runCatching { PDDocument.load(tempInput, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages } }.getOrDefault(1)
            val hasSearchableText = checkSearchableText(tempInput)

            val (effectiveScale, effectiveQuality) = if (customTargetBytes != null && customTargetBytes > 0L && originalSize > 0L) {
                val ratio = (customTargetBytes.toDouble() / originalSize.toDouble()).coerceIn(0.05, 1.2)
                val scale = (Math.sqrt(ratio) * 0.9).toFloat().coerceIn(0.25f, 0.95f)
                val quality = (30 + (ratio * 55)).toInt().coerceIn(30, 92)
                scale to quality
            } else {
                preset.targetScale to preset.jpegQuality
            }

            // One pass at a given scale and quality; the custom-target loop below runs it again, tighter, if the result is too big.
            suspend fun attempt(scale: Float, quality: Int): CompressionResult {
                if (hasSearchableText) {
                    // Strategy A: Preserve vector text/OCR layers while recompressing embedded images
                    val success = runCatching {
                        recompressEmbeddedImages(context, tempInput, outputFile, scale, quality, onProgress)
                        true
                    }.getOrDefault(false)

                    if (success && outputFile.exists() && outputFile.length() < originalSize) {
                        return CompressionResult(
                            outputFile = outputFile,
                            pageCount = pageTotal,
                            originalSizeBytes = originalSize,
                            compressedSizeBytes = outputFile.length(),
                            isSearchablePreserved = true
                        )
                    }
                }

                // Strategy B: Rasterise pages at downscaled target resolution & rebuild PDF
                rasterCompressPdf(context, tempInput, outputFile, scale, quality, onProgress)

                // A scan that was already small can grow when rebuilt; keep the original rather than a bigger file.
                // The original is untouched, so whatever text it had is still there.
                val keptOriginal = outputFile.length() !in 1L until originalSize
                if (keptOriginal) tempInput.copyTo(outputFile, overwrite = true)
                val finalSize = outputFile.length()
                return CompressionResult(
                    outputFile = outputFile,
                    pageCount = pageTotal,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = if (finalSize > 0) finalSize else originalSize,
                    // Only a rebuilt file that had text lost it; the untouched original, or one with no text, lost nothing.
                    isSearchablePreserved = keptOriginal || !hasSearchableText
                )
            }

            val result: CompressionResult
            if (customTargetBytes != null && customTargetBytes > 0L) {
                // The scale and quality estimated above are a guess from the size ratio, not a promise. Search for the
                // strongest setting that still fits: u runs from 0 (smallest) to 1 (best quality). A pass over the target
                // moves u down, a pass well under it moves u up, and the largest file that fits the target is kept.
                val maxScale = 0.95f
                val maxQuality = 92
                fun scaleAt(u: Float) = MIN_TARGET_SCALE + (maxScale - MIN_TARGET_SCALE) * u
                fun qualityAt(u: Float) = MIN_TARGET_QUALITY + ((maxQuality - MIN_TARGET_QUALITY) * u).toInt()
                var u = ((effectiveScale - MIN_TARGET_SCALE) / (maxScale - MIN_TARGET_SCALE)).coerceIn(0f, 1f)
                var lo = 0f
                var hi = 1f
                var best: CompressionResult? = null
                var last: CompressionResult? = null
                val bestFile = File(outputDir, "best_${UUID.randomUUID()}.pdf")
                try {
                    for (pass in 0 until MAX_TARGET_PASSES) {
                        outputFile.delete()
                        val tried = attempt(scaleAt(u), qualityAt(u))
                        last = tried
                        if (tried.compressedSizeBytes <= customTargetBytes) {
                            if (best == null || tried.compressedSizeBytes > best.compressedSizeBytes) {
                                tried.outputFile.copyTo(bestFile, overwrite = true)
                                best = tried
                            }
                            if (tried.compressedSizeBytes >= customTargetBytes * CLOSE_ENOUGH) break
                            lo = u
                            u = (u + hi) / 2f
                        } else {
                            hi = u
                            if (u <= 0.02f) break
                            u = (lo + u) / 2f
                        }
                    }
                    result = if (best != null) {
                        bestFile.copyTo(outputFile, overwrite = true)
                        best
                    } else {
                        last!!.copy(missedTarget = true)
                    }
                } finally {
                    bestFile.delete()
                }
            } else {
                result = attempt(effectiveScale, effectiveQuality)
            }
            return@withContext result
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
        context: Context,
        inputPdf: File,
        outputPdf: File,
        targetScale: Float,
        jpegQuality: Int,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ) {
        with(PdfToolsEngine) {
            PDDocument.load(inputPdf, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).editable()
        }.use { pdDoc ->
            val totalPages = pdDoc.numberOfPages
            val replaced = HashMap<com.tom_roush.pdfbox.cos.COSBase, PDImageXObject>()
            val produced = HashSet<com.tom_roush.pdfbox.cos.COSBase>()
            pdDoc.pages.forEachIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val pageLabel = context.getString(R.string.tools_compress_progress_page, index + 1, totalPages)
                onProgress(index + 1, totalPages, pageLabel)
                val resources = page.resources ?: return@forEachIndexed
                for (xObjectName in resources.xObjectNames) {
                    currentCoroutineContext().ensureActive()
                    val xObject = resources.getXObject(xObjectName)
                    if (xObject is PDImageXObject && !xObject.isStencil) {
                        val original = xObject.cosObject
                        if (original in produced) continue
                        val already = replaced[original]
                        if (already != null) {
                            resources.put(xObjectName, already)
                            continue
                        }
                        val bitmap = xObject.image ?: continue
                        val scaledW = (bitmap.width * targetScale).toInt().coerceAtLeast(1)
                        val scaledH = (bitmap.height * targetScale).toInt().coerceAtLeast(1)
                        var scaledBitmap: android.graphics.Bitmap? = null
                        try {
                            val scaled = bitmap.scale(scaledW, scaledH, true).also { scaledBitmap = it }
                            val recompressedImage = JPEGFactory.createFromImage(pdDoc, scaled, jpegQuality / 100f)
                            replaced[original] = recompressedImage
                            produced += recompressedImage.cosObject
                            resources.put(xObjectName, recompressedImage)
                        } finally {
                            // Free the full-size decode too: with many large scans they would pile up until GC.
                            scaledBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                }
            }
            onProgress(totalPages, totalPages, context.getString(R.string.tools_compress_progress_rebuilding))
            pdDoc.save(outputPdf)
        }
    }

    private suspend fun rasterCompressPdf(
        context: Context,
        inputPdf: File,
        outputPdf: File,
        targetScale: Float,
        jpegQuality: Int,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ) {
        val renderDir = File(context.cacheDir, "raster_${UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ScanPage>()
        val pointSizes = mutableListOf<Pair<Int, Int>>()

        try {
            ParcelFileDescriptor.open(inputPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val totalPages = renderer.pageCount
                    for (index in 0 until totalPages) {
                        currentCoroutineContext().ensureActive()
                        val pageLabel = context.getString(R.string.tools_compress_progress_page, index + 1, totalPages)
                        onProgress(index + 1, totalPages, pageLabel)
                        renderer.openPage(index).use { page ->
                            // Capped like PDF to images: a poster-sized page would otherwise ask for a bitmap of a
                            // gigabyte or more and fail the whole job, and the exporter scales it down anyway.
                            val scale = minOf(targetScale * 2.0f, MAX_RASTER_DIMENSION / maxOf(page.width, page.height).coerceAtLeast(1).toFloat())
                            pointSizes.add(page.width.coerceAtLeast(1) to page.height.coerceAtLeast(1))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageFile = File(renderDir, "page_${index.toString().padStart(3, '0')}.jpg")
                            ImageProcessor.saveBitmapJpeg(bitmap, pageFile, jpegQuality)
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

            val pdfQuality = when {
                jpegQuality >= 80 -> PdfQuality.HIGH
                jpegQuality >= 65 -> PdfQuality.BALANCED
                else -> PdfQuality.SMALL
            }

            val rebuildingLabel = context.getString(R.string.tools_compress_progress_rebuilding)
            onProgress(pages.size, pages.size, rebuildingLabel)

            PdfExporter.export(
                pages = pages,
                destination = outputPdf,
                quality = pdfQuality,
                pageSize = PdfPageSize.AUTO,
                searchable = false,
                pageNumbers = false,
                // Each page keeps its original size, edge to edge, so printing at actual size still matches.
                pointSizes = pointSizes,
                beforePage = { number, total ->
                    onProgress(number, total, rebuildingLabel)
                }
            )
        } finally {
            renderDir.deleteRecursively()
        }
    }
}
