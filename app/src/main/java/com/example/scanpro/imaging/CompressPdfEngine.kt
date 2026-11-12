@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.imaging

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
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
                        // Decoded at a fraction of its size when it is going to be shrunk anyway: a 48-megapixel photo
                        // used to be decoded in full (about 190 MB) before being scaled down.
                        val subsampling = subsamplingFor(targetScale)
                        val bitmap = runCatching { xObject.getImage(null, subsampling) }.getOrNull() ?: continue
                        val scaledW = (xObject.width * targetScale).toInt().coerceIn(1, bitmap.width.coerceAtLeast(1) * 2)
                        val scaledH = (xObject.height * targetScale).toInt().coerceIn(1, bitmap.height.coerceAtLeast(1) * 2)
                        val scaledBitmap = if (bitmap.width == scaledW && bitmap.height == scaledH) bitmap else bitmap.scale(scaledW, scaledH, true)
                        try {
                            val recompressedImage = JPEGFactory.createFromImage(pdDoc, scaledBitmap, jpegQuality / 100f)
                            // Only a smaller image replaces the original. A black-and-white scan (CCITT or JBIG2) grows
                            // as a JPEG; replacing it anyway made the whole file bigger, which then sent the document
                            // down the picture-only path and lost its text.
                            val before = original?.length ?: Long.MAX_VALUE
                            if (recompressedImage.cosObject.length < before) {
                                replaced[original] = recompressedImage
                                produced += recompressedImage.cosObject
                                resources.put(xObjectName, recompressedImage)
                            } else {
                                produced += original
                            }
                        } finally {
                            if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                }
            }
            onProgress(totalPages, totalPages, context.getString(R.string.tools_compress_progress_rebuilding))
            pdDoc.save(outputPdf)
        }
    }

    /**
     * Rebuilds the PDF from pictures of its pages. Each page is rendered, saved once as a JPEG at [jpegQuality] and
     * embedded as it is, on a page of the original's size. It used to go through the scan exporter, which encoded every
     * page a second time at its own quality (so "Maximum" could not go below 65), changed the pages to A4-like sizes
     * with margins, and could not be cancelled while it rebuilt the file.
     */
    private suspend fun rasterCompressPdf(
        context: Context,
        inputPdf: File,
        outputPdf: File,
        targetScale: Float,
        jpegQuality: Int,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ) {
        val renderDir = File(context.cacheDir, "raster_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            PDDocument(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { rebuilt ->
                ParcelFileDescriptor.open(inputPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val totalPages = renderer.pageCount
                        for (index in 0 until totalPages) {
                            currentCoroutineContext().ensureActive()
                            onProgress(index + 1, totalPages, context.getString(R.string.tools_compress_progress_page, index + 1, totalPages))
                            val pageFile = File(renderDir, "page_${index.toString().padStart(4, '0')}.jpg")
                            // PdfRenderer measures pages in points, as PDF does: the rebuilt page keeps the original size.
                            val (widthPt, heightPt) = renderer.openPage(index).use { page ->
                                // Capped (from the F142 bug hunt): a poster-sized page would otherwise ask for a bitmap of a
                                // gigabyte or more and fail the whole job.
                                val scale = minOf(targetScale * 2.0f, MAX_RASTER_DIMENSION / maxOf(page.width, page.height).coerceAtLeast(1).toFloat())
                                val bitmap = createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1))
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    ImageProcessor.saveBitmapJpeg(bitmap, pageFile, jpegQuality)
                                } finally {
                                    bitmap.recycle()
                                }
                                page.width.toFloat() to page.height.toFloat()
                            }
                            val pdPage = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(widthPt, heightPt))
                            rebuilt.addPage(pdPage)
                            val image = pageFile.inputStream().use { JPEGFactory.createFromStream(rebuilt, it) }
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(rebuilt, pdPage).use { content ->
                                content.drawImage(image, 0f, 0f, widthPt, heightPt)
                            }
                            pageFile.delete()
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                onProgress(rebuilt.numberOfPages, rebuilt.numberOfPages, context.getString(R.string.tools_compress_progress_rebuilding))
                rebuilt.save(outputPdf)
            }
        } finally {
            renderDir.deleteRecursively()
        }
    }

    /** The power-of-two shrink PDFBox can apply while decoding an image that is going to be scaled by [targetScale]. */
    internal fun subsamplingFor(targetScale: Float): Int {
        var sub = 1
        while (targetScale * sub * 2 <= 1f && sub < 8) sub *= 2
        return sub
    }
}
