@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.imaging

import com.example.scanpro.core.concurrent.BackgroundWork
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.ui.COPY_BUFFER

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
    ): CompressionResult = withContext(BackgroundWork.tools) {
        val outputDir = outputFile.parentFile!!.apply { mkdirs() }
        val tempInput = File(outputDir, "temp_in_${UUID.randomUUID()}.pdf")

        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).buffered(COPY_BUFFER).use { output -> input.copyTo(output, COPY_BUFFER) }
            } ?: userError(R.string.err_could_not_open_input_pdf)

            val originalSize = tempInput.length()
            if (customTargetBytes != null && originalSize > 0 && customTargetBytes >= originalSize) {
                userError(R.string.tools_compress_err_target_larger_engine)
            }
            // A file that needs a password to open used to fall through to the picture pass, where the system
            // renderer refused it with a general "could not compress" (F194): say what is wrong instead.
            val pageTotal = try {
                PDDocument.load(tempInput, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
            } catch (locked: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                userError(R.string.tools_err_password_protected, cause = locked)
            } catch (_: Exception) {
                1
            }
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
            // [rasterTextPages]: whether a PDF with text may be rebuilt from pictures of its pages when shrinking its
            // images did not help. Only for a custom target, which asks for a size above all else.
            suspend fun attempt(scale: Float, quality: Int, rasterTextPages: Boolean): CompressionResult {
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
                    if (!rasterTextPages) {
                        // F194: a preset used to rebuild a text PDF (a letter, a report) from pictures of its pages at
                        // 65 to 120 dpi when it had no photos to shrink: the text came out blurred and could no longer be
                        // searched or copied, for a file that was small already. It is kept as it is instead.
                        tempInput.copyTo(outputFile, overwrite = true)
                        return CompressionResult(
                            outputFile = outputFile,
                            pageCount = pageTotal,
                            originalSizeBytes = originalSize,
                            compressedSizeBytes = originalSize,
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
                        val tried = attempt(scaleAt(u), qualityAt(u), rasterTextPages = true)
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
                result = attempt(effectiveScale, effectiveQuality, rasterTextPages = false)
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

    // ---- Estimate before compressing (F194) ------------------------------------------------------------------------

    /**
     * What Compress is expected to make of a PDF, worked out before it runs. The old screen promised a fixed share of
     * the size (65%, 40%, 20%) whatever the file held: a text PDF was "about 60% smaller" and came out the same size.
     * [bytes] has an estimate for each preset, [smallestBytes] the least a custom target can reach, [firstPage] the
     * first page as the picture pass would leave it (only for a PDF with no text, the one that pass rebuilds).
     */
    class CompressEstimate(
        val originalBytes: Long,
        val pageCount: Int,
        val hasText: Boolean,
        /** Bytes of the pictures the image pass can shrink; 0 for a PDF of text and drawings only. */
        val imageBytes: Long,
        val bytes: Map<CompressionPreset, Long>,
        val smallestBytes: Long,
        val firstPage: Map<CompressionPreset, android.graphics.Bitmap> = emptyMap(),
    )

    /** Biggest pictures sampled for the image pass, and the longest side samples are decoded or rendered at. */
    private const val SAMPLE_IMAGES = 3
    private const val SAMPLE_SIDE = 1600
    /** What a rebuilt page costs beyond its picture (page, image and content objects), and the file's own overhead. */
    internal const val RASTER_PAGE_OVERHEAD = 700L
    internal const val RASTER_FILE_OVERHEAD = 1500L

    /**
     * The image pass, worked out from a sample: the pictures sampled shrank from [sampleBefore] to [sampleAfter] bytes,
     * so all [imageBytes] of pictures shrink by the same share and the rest of the file stays as it is. A picture that
     * would grow is kept as it is, so the share is never above 1.
     */
    internal fun imagePassBytes(originalBytes: Long, imageBytes: Long, sampleBefore: Long, sampleAfter: Long): Long {
        if (imageBytes <= 0L || sampleBefore <= 0L) return originalBytes
        val ratio = (sampleAfter.toDouble() / sampleBefore).coerceIn(0.0, 1.0)
        val rest = (originalBytes - imageBytes).coerceAtLeast(0L)
        return rest + (imageBytes * ratio).toLong()
    }

    /** The picture pass: [pages] pages at an average of [averagePageBytes] each, plus the overheads. */
    internal fun rasterPassBytes(pages: Int, averagePageBytes: Long): Long =
        RASTER_FILE_OVERHEAD + pages.coerceAtLeast(1) * (averagePageBytes + RASTER_PAGE_OVERHEAD)

    /** JPEG bytes scaled from an encoded size of [encodedPixels] to [targetPixels] (JPEG size grows about with area). */
    internal fun scaleByArea(bytes: Long, encodedPixels: Long, targetPixels: Long): Long =
        if (encodedPixels <= 0L) bytes else (bytes.toDouble() * targetPixels / encodedPixels).toLong()

    private fun jpegBytes(bitmap: android.graphics.Bitmap, quality: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    /** Encodes [bitmap] at [quality] as if it were [targetW] x [targetH]: a smaller size is encoded as it is, a larger one scaled up by area. */
    private fun sampleJpeg(bitmap: android.graphics.Bitmap, targetW: Int, targetH: Int, quality: Int): Long {
        val w = minOf(targetW, bitmap.width).coerceAtLeast(1)
        val h = minOf(targetH, bitmap.height).coerceAtLeast(1)
        val sized = if (w == bitmap.width && h == bitmap.height) bitmap else bitmap.scale(w, h, true)
        try {
            val bytes = jpegBytes(sized, quality).size.toLong()
            return scaleByArea(bytes, w.toLong() * h, targetW.toLong() * targetH)
        } finally {
            if (sized !== bitmap) sized.recycle()
        }
    }

    /**
     * Works out a [CompressEstimate] for [inputUri] from samples: its biggest pictures re-encoded at each preset's scale
     * and quality, and the first and middle pages rendered as the picture pass would. Takes a second or two on a phone
     * for a large scan; call it off the main thread (it moves to the tools dispatcher itself).
     */
    suspend fun estimate(context: Context, inputUri: Uri): CompressEstimate = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "compress_est_${UUID.randomUUID()}.pdf")
        try {
            val original = temp.length()
            // The presets, and (null) the strongest setting a custom target can go down to.
            val settings = LinkedHashMap<CompressionPreset?, Pair<Float, Int>>()
            CompressionPreset.entries.forEach { settings[it] = it.targetScale to it.jpegQuality }
            settings[null] = MIN_TARGET_SCALE to MIN_TARGET_QUALITY
            val hasText = checkSearchableText(temp)

            // The image pass, from the biggest pictures.
            var imageBytes = 0L
            var pageCount = 0
            val sampleBefore = HashMap<CompressionPreset?, Long>()
            val sampleAfter = HashMap<CompressionPreset?, Long>()
            val doc = try {
                PDDocument.load(temp, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
            } catch (locked: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                userError(R.string.tools_err_password_protected, cause = locked)
            }
            doc.use { pdf ->
                pageCount = pdf.numberOfPages
                val seen = HashSet<com.tom_roush.pdfbox.cos.COSBase>()
                val images = ArrayList<PDImageXObject>()
                for (page in pdf.pages) {
                    currentCoroutineContext().ensureActive()
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        val image = runCatching { resources.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                        if (image.isStencil || !seen.add(image.cosObject)) continue
                        images += image
                        imageBytes += image.cosObject.length
                    }
                }
                for (image in images.sortedByDescending { it.cosObject.length }.take(SAMPLE_IMAGES)) {
                    currentCoroutineContext().ensureActive()
                    var sub = 1
                    while (maxOf(image.width, image.height) / sub > SAMPLE_SIDE && sub < 16) sub *= 2
                    val bitmap = runCatching { image.getImage(null, sub) }.getOrNull() ?: continue
                    try {
                        val before = image.cosObject.length
                        for ((preset, setting) in settings) {
                            val w = (image.width * setting.first).toInt().coerceAtLeast(1)
                            val h = (image.height * setting.first).toInt().coerceAtLeast(1)
                            // Only a smaller picture replaces the original, as in the real pass.
                            val after = minOf(before, sampleJpeg(bitmap, w, h, setting.second))
                            sampleBefore[preset] = (sampleBefore[preset] ?: 0L) + before
                            sampleAfter[preset] = (sampleAfter[preset] ?: 0L) + after
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            fun imagePass(preset: CompressionPreset?): Long =
                imagePassBytes(original, imageBytes, sampleBefore[preset] ?: 0L, sampleAfter[preset] ?: 0L)

            // The picture pass, from the first page and one in the middle.
            val rasterAverage = HashMap<CompressionPreset?, Long>()
            val previews = HashMap<CompressionPreset, android.graphics.Bitmap>()
            runCatching {
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val samples = listOf(0, renderer.pageCount / 2).distinct().filter { it in 0 until renderer.pageCount }
                        for (index in samples) {
                            currentCoroutineContext().ensureActive()
                            renderer.openPage(index).use { page ->
                                val longest = maxOf(page.width, page.height).coerceAtLeast(1).toFloat()
                                val renderScale = minOf(CompressionPreset.HIGH.targetScale * 2f, SAMPLE_SIDE / longest)
                                val bitmap = createBitmap((page.width * renderScale).toInt().coerceAtLeast(1), (page.height * renderScale).toInt().coerceAtLeast(1))
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    for ((preset, setting) in settings) {
                                        // The size the real pass renders this page at.
                                        val real = minOf(setting.first * 2f, MAX_RASTER_DIMENSION / longest)
                                        val w = (page.width * real).toInt().coerceAtLeast(1)
                                        val h = (page.height * real).toInt().coerceAtLeast(1)
                                        rasterAverage[preset] = (rasterAverage[preset] ?: 0L) + sampleJpeg(bitmap, w, h, setting.second) / samples.size
                                        if (index == 0 && preset != null && !hasText) {
                                            // The first page as the picture pass leaves it, for the Result preview.
                                            val shown = if (w < bitmap.width) bitmap.scale(w, (bitmap.height.toLong() * w / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
                                            try {
                                                val jpeg = jpegBytes(shown, setting.second)
                                                val options = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
                                                android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.let { previews[preset] = it }
                                            } finally {
                                                if (shown !== bitmap) shown.recycle()
                                            }
                                        }
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            fun rasterPass(preset: CompressionPreset?): Long? = rasterAverage[preset]?.let { rasterPassBytes(pageCount, it) }

            // The pass the engine takes, as in compressPdf: a PDF with text gets the image pass (and, for a preset,
            // nothing more); one without is rebuilt from pictures. A result that is not smaller is never kept.
            val bytes = CompressionPreset.entries.associateWith { preset ->
                val after = if (hasText) imagePass(preset) else rasterPass(preset) ?: original
                minOf(after, original)
            }
            val images = imagePass(null)
            val smallest = minOf(if (hasText && images < original) images else rasterPass(null) ?: images, original)
            CompressEstimate(original, pageCount, hasText, imageBytes, bytes, smallest, previews)
        } finally {
            temp.delete()
        }
    }

    /** The power-of-two shrink PDFBox can apply while decoding an image that is going to be scaled by [targetScale]. */
    internal fun subsamplingFor(targetScale: Float): Int {
        var sub = 1
        while (targetScale * sub * 2 <= 1f && sub < 8) sub *= 2
        return sub
    }
}
