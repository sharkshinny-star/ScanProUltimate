package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.CropQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class ImageMetadata(
    val fileName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String?,
)

data class ImageCompressConfig(
    val targetSizeBytes: Long? = null,
    val qualityPercent: Int = 80,
    val maxDimension: Int? = null,
    val outputFormat: ImageExportFormat = ImageExportFormat.JPEG,
)

/** What to do when the picture's shape is not the box's shape. */
enum class ImageResizeFit {
    /** Keep the proportions and fit inside the box: the result may be smaller than the box on one side. */
    FIT,
    /** Keep the proportions and fill the box exactly, cutting the overflow from the centre. */
    FILL,
    /** Force the exact size by stretching: distorts anything that is not the box's shape. */
    STRETCH
}

data class ImageResizeConfig(
    val targetWidth: Int,
    val targetHeight: Int,
    val outputFormat: ImageExportFormat = ImageExportFormat.JPEG,
    val qualityPercent: Int = 92,
    val fit: ImageResizeFit = ImageResizeFit.STRETCH,
)

object ImageToolsEngine {

    /**
     * Inspects image dimensions and file size without loading full pixel buffers into memory.
     */
    fun inspectImage(context: Context, uri: Uri): ImageMetadata? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        val localFile = if ((uri.scheme == "file") || (uri.scheme == null)) {
            uri.path?.let(::File)?.takeIf { it.exists() }
        } else null

        if (localFile != null) {
            BitmapFactory.decodeFile(localFile.absolutePath, bounds)
        } else {
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                } ?: return null
            } catch (_: Throwable) {
                return null
            }
        }
        if ((bounds.outWidth <= 0) || (bounds.outHeight <= 0)) return null

        val size = if (localFile != null) {
            localFile.length()
        } else {
            val contentSize = runCatching {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
            }.getOrNull() ?: 0L
            if (contentSize > 0) contentSize else {
                runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                }.getOrDefault(0L)
            }
        }

        val name = PdfToolsEngine.sourceBaseName(context, uri, "image")
        val mime = bounds.outMimeType ?: resolver.getType(uri)

        return ImageMetadata(
            fileName = name,
            width = bounds.outWidth,
            height = bounds.outHeight,
            sizeBytes = size,
            mimeType = mime,
        )
    }

    /**
     * Compresses an image to either a target file size in bytes (via intelligent resolution & quality search)
     * or a specified quality percentage / dimension constraint.
     */
    suspend fun compressImage(
        context: Context,
        inputUri: Uri,
        config: ImageCompressConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): PdfToolResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "compress_img_${UUID.randomUUID()}.tmp")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_image_unreadable, 1)

            val aspect = ImageProcessor.imageAspect(tempFile.absolutePath)
            userRequire(aspect != null, R.string.tools_err_image_undecodable, 1)

            onProgress(1, 2, AppStrings.get(R.string.tools_progress_compressing_img))

            val targetBytes = config.targetSizeBytes
            if ((targetBytes != null) && (targetBytes > 0)) {
                compressToTargetSize(tempFile, outputFile, targetBytes, config.maxDimension, config.outputFormat)
            } else {
                compressWithQuality(tempFile, outputFile, config.qualityPercent, config.maxDimension, config.outputFormat)
            }

            // Tiny or already-optimised pictures can come out bigger. When the format is unchanged, keep the original
            // bytes instead of handing back something larger than what the user picked.
            val sourceMime = android.graphics.BitmapFactory.Options().also {
                it.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, it)
            }.outMimeType.orEmpty().lowercase()
            val outExt = outputFile.extension.lowercase()
            val sameFormat = when (outExt) {
                "jpg", "jpeg" -> sourceMime == "image/jpeg"
                "png" -> sourceMime == "image/png"
                "webp" -> sourceMime == "image/webp"
                else -> false
            }
            val keptOriginal = outputFile.length() >= originalSize && sameFormat
            if (keptOriginal) tempFile.copyTo(outputFile, overwrite = true)

            val outputSize = outputFile.length()
            val savings = if (originalSize > 0) {
                maxOf(0, (((originalSize - outputSize).toFloat() / originalSize) * 100).roundToInt())
            } else 0

            onProgress(2, 2, AppStrings.get(R.string.tools_progress_compressing_img))

            val msg = if (keptOriginal) AppStrings.get(R.string.tools_compress_img_kept, formatBytes(context, originalSize))
            else AppStrings.get(
                R.string.tools_compress_img_success,
                savings,
                formatBytes(context, originalSize),
                formatBytes(context, outputSize),
            )

            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = 1,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputSize,
                message = msg,
                comparesSize = true,
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun compressWithQuality(
        sourceFile: File,
        outputFile: File,
        quality: Int,
        maxDim: Int?,
        format: ImageExportFormat
    ) {
        val targetDim = maxDim ?: 3400
        val bitmap = ImageProcessor.decodeOrientedScaled(sourceFile.absolutePath, targetDim)
            ?: userError(R.string.tools_err_image_undecodable, 1)

        val prepared = prepareForFormat(bitmap, format)
        if ((prepared !== bitmap) && !bitmap.isRecycled) bitmap.recycle()

        FileOutputStream(outputFile).use { out ->
            encodeToStream(prepared, format, quality, out)
        }
        prepared.recycle()
    }

    private fun compressToTargetSize(
        sourceFile: File,
        outputFile: File,
        targetBytes: Long,
        userMaxDim: Int?,
        format: ImageExportFormat
    ) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val origMax = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

        val suggestedMax = when {
            targetBytes <= (50 * 1024L) -> 1280
            targetBytes <= (100 * 1024L) -> 1600
            targetBytes <= (200 * 1024L) -> 2048
            targetBytes <= (500 * 1024L) -> 2560
            else -> 3200
        }
        var currentDim = minOf(userMaxDim ?: origMax, suggestedMax, origMax)

        var bestBytes: ByteArray? = null

        run searchLoop@{
            repeat(4) {
                val bitmap = ImageProcessor.decodeOrientedScaled(sourceFile.absolutePath, currentDim)
                    ?: return@searchLoop

                val prepared = prepareForFormat(bitmap, format)
                if ((prepared !== bitmap) && !bitmap.isRecycled) bitmap.recycle()

                var lowQ = 15
                var highQ = 95
                var foundQForDim: ByteArray? = null

                while (lowQ <= highQ) {
                    val midQ = (lowQ + highQ) / 2
                    val stream = ByteArrayOutputStream()
                    encodeToStream(prepared, format, midQ, stream)
                    val bytes = stream.toByteArray()

                    if (bytes.size <= targetBytes) {
                        foundQForDim = bytes
                        lowQ = midQ + 1
                    } else {
                        highQ = midQ - 1
                    }
                }

                if (foundQForDim != null) {
                    bestBytes = foundQForDim
                    prepared.recycle()
                    return@searchLoop
                } else {
                    val fallbackStream = ByteArrayOutputStream()
                    encodeToStream(prepared, format, 15, fallbackStream)
                    bestBytes = fallbackStream.toByteArray()
                    prepared.recycle()
                    currentDim = (currentDim * 0.75f).toInt().coerceAtLeast(400)
                }
            }
        }

        if (bestBytes != null) {
            FileOutputStream(outputFile).use { it.write(bestBytes) }
        } else {
            sourceFile.copyTo(outputFile, overwrite = true)
        }
    }

    /**
     * Converts one or more images into the target format (JPG, PNG, or WEBP).
     * For JPG to PNG conversion, quality is 100% lossless with zero compression artifacts.
     * For PNG to JPG conversion, transparent backgrounds are cleanly filled with solid white.
     */
    suspend fun convertImage(
        context: Context,
        inputUris: List<Uri>,
        targetFormat: ImageExportFormat,
        quality: Int = 100,
        outputLocation: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(inputUris.isNotEmpty(), R.string.tools_err_no_images)
        outputLocation.parentFile?.mkdirs()

        val tempFiles = mutableListOf<File>()
        val convertedFiles = mutableListOf<File>()
        var totalOriginalSize = 0L

        try {
            val label = AppStrings.get(R.string.tools_progress_converting_img)
            inputUris.forEachIndexed { index, uri ->
                if (!coroutineContext.isActive) throw InterruptedException("Convert operation cancelled")
                onProgress(index + 1, inputUris.size, label)

                val base = PdfToolsEngine.sourceBaseName(context, uri, "image")
                val tempFile = PdfToolsEngine.copyUriToTemp(context, uri, "conv_in_${UUID.randomUUID()}.tmp")
                tempFiles.add(tempFile)
                totalOriginalSize += tempFile.length()

                val bitmap = ImageProcessor.decodeOrientedScaled(tempFile.absolutePath, 4200)
                    ?: userError(R.string.tools_err_image_undecodable, index + 1)

                val prepared = prepareForFormat(bitmap, targetFormat)
                if ((prepared !== bitmap) && !bitmap.isRecycled) bitmap.recycle()

                val singleOut = if (inputUris.size == 1) {
                    outputLocation
                } else {
                    File(outputLocation.parentFile, "$base.${targetFormat.extension}")
                }

                FileOutputStream(singleOut).use { out ->
                    encodeToStream(prepared, targetFormat, quality, out)
                }
                prepared.recycle()
                convertedFiles.add(singleOut)
            }

            val finalOutput = if (convertedFiles.size == 1) {
                convertedFiles.first()
            } else {
                PdfToolsEngine.bundleOutputs(context, convertedFiles, "converted_${System.currentTimeMillis()}")
            }

            val outputSize = finalOutput.length()
            val msg = AppStrings.plural(R.plurals.tools_convert_img_done, inputUris.size, inputUris.size)

            return@withContext PdfToolResult(
                outputFile = finalOutput,
                pageCount = inputUris.size,
                originalSizeBytes = totalOriginalSize,
                outputSizeBytes = outputSize,
                message = msg
            )
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    /**
     * Resizes an image to specified pixel dimensions with high quality bilinear filtering.
     */
    suspend fun resizeImage(
        context: Context,
        inputUri: Uri,
        config: ImageResizeConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val targetWidth = config.targetWidth
        val targetHeight = config.targetHeight
        userRequire(targetWidth in 10..8000 && targetHeight in 10..8000, R.string.tools_err_invalid_dimensions)
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "resize_in_${UUID.randomUUID()}.tmp")

        try {
            val originalSize = tempFile.length()
            onProgress(1, 2, AppStrings.get(R.string.tools_progress_resizing_img))

            val bitmap = ImageProcessor.decodeOrientedScaled(tempFile.absolutePath, max(targetWidth, targetHeight) * 2)
                ?: userError(R.string.tools_err_image_undecodable, 1)

            val scaled = when (config.fit) {
                ImageResizeFit.STRETCH -> Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                ImageResizeFit.FIT -> {
                    val k = minOf(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).roundToInt().coerceAtLeast(1), (bitmap.height * k).roundToInt().coerceAtLeast(1), true)
                }
                ImageResizeFit.FILL -> {
                    val k = maxOf(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
                    val w = kotlin.math.ceil(bitmap.width * k).toInt().coerceAtLeast(targetWidth)
                    val h = kotlin.math.ceil(bitmap.height * k).toInt().coerceAtLeast(targetHeight)
                    val grown = Bitmap.createScaledBitmap(bitmap, w, h, true)
                    val cropped = Bitmap.createBitmap(grown, (w - targetWidth) / 2, (h - targetHeight) / 2, targetWidth, targetHeight)
                    if (grown !== cropped && grown !== bitmap && !grown.isRecycled) grown.recycle()
                    cropped
                }
            }
            if ((scaled !== bitmap) && !bitmap.isRecycled) bitmap.recycle()
            val resultWidth = scaled.width
            val resultHeight = scaled.height

            val prepared = prepareForFormat(scaled, config.outputFormat)
            if ((prepared !== scaled) && !scaled.isRecycled) scaled.recycle()

            FileOutputStream(outputFile).use { out ->
                encodeToStream(prepared, config.outputFormat, config.qualityPercent, out)
            }
            prepared.recycle()

            onProgress(2, 2, AppStrings.get(R.string.tools_progress_resizing_img))
            val outputSize = outputFile.length()
            val msg = AppStrings.get(
                R.string.tools_resize_img_success,
                resultWidth,
                resultHeight,
                formatBytes(context, outputSize)
            )

            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = 1,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputSize,
                message = msg
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Crops an image according to the specified normalized corner quadrilateral.
     */
    suspend fun cropImage(
        context: Context,
        inputUri: Uri,
        quad: CropQuad,
        outputFile: File,
        format: ImageExportFormat = ImageExportFormat.JPEG,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): PdfToolResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "crop_in_${UUID.randomUUID()}.tmp")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_image_unreadable, 1)
            onProgress(1, 2, AppStrings.get(R.string.tools_progress_cropping_img))

            val stagingOut = File(outputFile.parentFile, "staged_${UUID.randomUUID()}.jpg")
            ImageProcessor.cropSourceToNewFile(tempFile, stagingOut, quad)
            userRequire(stagingOut.exists() && stagingOut.length() > 0, R.string.tools_err_crop_img)

            if (format != ImageExportFormat.JPEG) {
                val bmp = ImageProcessor.decodeOrientedScaled(stagingOut.absolutePath, 4200)
                    ?: userError(R.string.tools_err_image_undecodable, 1)
                val prepared = prepareForFormat(bmp, format)
                if (prepared !== bmp && !bmp.isRecycled) bmp.recycle()
                FileOutputStream(outputFile).use { out ->
                    encodeToStream(prepared, format, 100, out)
                }
                prepared.recycle()
                stagingOut.delete()
            } else {
                stagingOut.copyTo(outputFile, overwrite = true)
                stagingOut.delete()
            }

            onProgress(2, 2, AppStrings.get(R.string.tools_progress_cropping_img))
            val outputSize = outputFile.length()
            val msg = AppStrings.get(R.string.tools_crop_img_success, formatBytes(context, outputSize))

            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = 1,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputSize,
                message = msg,
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun prepareForFormat(source: Bitmap, format: ImageExportFormat): Bitmap {
        if (format == ImageExportFormat.JPEG && source.hasAlpha()) {
            val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(source, 0f, 0f, null)
            return flattened
        }
        return source
    }

    private fun encodeToStream(
        bitmap: Bitmap,
        format: ImageExportFormat,
        quality: Int,
        out: OutputStream
    ) {
        when (format) {
            ImageExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            ImageExportFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            ImageExportFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (quality >= 100) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality.coerceIn(1, 100), out)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), out)
                }
            }
        }
    }
}
