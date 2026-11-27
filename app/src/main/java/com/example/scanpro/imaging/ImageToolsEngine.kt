package com.example.scanpro.imaging

import com.example.scanpro.core.concurrent.BackgroundWork
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
import com.example.scanpro.core.ui.COPY_BUFFER
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.CropQuad
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

/**
 * A rectangular crop of a photo: the edges as fractions (0 to 1) of the picture after it has been turned by [rotation]
 * and, if [flipHorizontal], mirrored left to right. The Crop image editor produces it.
 */
data class ImageCropSpec(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    /** Clockwise turn in degrees: 0, 90, 180 or 270. */
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
)

object ImageToolsEngine {

    /**
     * The longest side any image tool decodes. Pictures used to be cut to 3400 or 4200 px without a word, so a 12 MP
     * photo came out of Compress or Convert smaller than it went in; [ImageProcessor.decodeScaled] still keeps the
     * pixel count inside memory.
     */
    const val MAX_IMAGE_SIDE = 8000

    /**
     * The most pixels Resize will make. 8000 x 8000 is 256 MB as a bitmap, and a JPEG of a transparent picture needs a
     * second copy beside it; 24 megapixels (6000 x 4000, 96 MB) leaves room on phones with a small memory limit.
     */
    const val MAX_RESIZE_PIXELS = 24_000_000L

    /** The lowest JPEG/WebP quality Compress uses before it gives up resolution instead: below it text and faces smear. */
    private const val MIN_KEEP_QUALITY = 40

    /** The format a picture is stored in, from its own bytes; null for anything other than JPEG, PNG or WebP. */
    fun formatOf(path: String): ImageExportFormat? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return formatForMime(bounds.outMimeType)
    }

    fun formatForMime(mime: String?): ImageExportFormat? = when (mime?.lowercase()) {
        "image/jpeg", "image/jpg" -> ImageExportFormat.JPEG
        "image/png" -> ImageExportFormat.PNG
        "image/webp" -> ImageExportFormat.WEBP
        else -> null
    }

    /** [bitmap] encoded as [format] at [quality], in memory: what the screens use for their size estimates and previews. */
    fun encodeBytes(bitmap: Bitmap, format: ImageExportFormat, quality: Int): ByteArray {
        val prepared = prepareForFormat(bitmap, format)
        return try {
            ByteArrayOutputStream().also { encodeToStream(prepared, format, quality, it) }.toByteArray()
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    /**
     * The highest quality from [minQuality] to 95 at which [bitmap] fits in [targetBytes], with its bytes; null when even
     * [minQuality] is too big. PNG has no quality setting, so it either fits or it does not.
     */
    fun bestQualityWithin(bitmap: Bitmap, format: ImageExportFormat, targetBytes: Long, minQuality: Int): Pair<Int, ByteArray>? {
        if (format == ImageExportFormat.PNG) {
            val bytes = encodeBytes(bitmap, format, 100)
            return if (bytes.size <= targetBytes) 100 to bytes else null
        }
        var low = minQuality
        var high = 95
        var best: Pair<Int, ByteArray>? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = encodeBytes(bitmap, format, mid)
            if (bytes.size <= targetBytes) {
                best = mid to bytes
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    /**
     * Turns, mirrors and cuts [source] as [spec] says; returns a new bitmap (or [source] itself when nothing changes).
     * The caller recycles both.
     */
    fun applyCrop(source: Bitmap, spec: ImageCropSpec): Bitmap {
        val turn = ((spec.rotation % 360) + 360) % 360
        val turned = if (turn != 0 || spec.flipHorizontal) {
            val matrix = android.graphics.Matrix().apply {
                if (turn != 0) postRotate(turn.toFloat())
                if (spec.flipHorizontal) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else source
        val left = (spec.left.coerceIn(0f, 1f) * turned.width).roundToInt().coerceIn(0, turned.width - 1)
        val top = (spec.top.coerceIn(0f, 1f) * turned.height).roundToInt().coerceIn(0, turned.height - 1)
        val right = (spec.right.coerceIn(0f, 1f) * turned.width).roundToInt().coerceIn(left + 1, turned.width)
        val bottom = (spec.bottom.coerceIn(0f, 1f) * turned.height).roundToInt().coerceIn(top + 1, turned.height)
        if (left == 0 && top == 0 && right == turned.width && bottom == turned.height) return turned
        val cut = Bitmap.createBitmap(turned, left, top, right - left, bottom - top)
        if (turned !== source && turned !== cut) turned.recycle()
        return cut
    }

    /**
     * Writes [source] cut as [spec] says to [destination] in [format] (the picture's own format when null, so a
     * transparent PNG stays a transparent PNG), at full resolution up to [MAX_IMAGE_SIDE]. Returns the format written.
     */
    fun cropToFile(source: File, destination: File, spec: ImageCropSpec, format: ImageExportFormat? = null): ImageExportFormat {
        val outFormat = format ?: formatOf(source.absolutePath) ?: ImageExportFormat.JPEG
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, MAX_IMAGE_SIDE)
            ?: userError(R.string.tools_err_image_undecodable, 1)
        val cut = applyCrop(bitmap, spec)
        if (cut !== bitmap) bitmap.recycle()
        val prepared = prepareForFormat(cut, outFormat)
        if (prepared !== cut) cut.recycle()
        try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).buffered(COPY_BUFFER).use { out ->
                // Near-lossless: a crop should not visibly cost quality.
                encodeToStream(prepared, outFormat, 95, out)
            }
        } finally {
            prepared.recycle()
        }
        return outFormat
    }

    /** Crop image tool: [inputUri] cut as [spec] says, kept in its own format unless [format] is given. */
    suspend fun cropImageRect(
        context: Context,
        inputUri: Uri,
        spec: ImageCropSpec,
        outputDir: File,
        format: ImageExportFormat? = null,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "crop_in_${UUID.randomUUID()}.tmp")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_image_unreadable, 1)
            onProgress(1, 2, AppStrings.get(R.string.tools_progress_cropping_img))
            val outFormat = format ?: formatOf(tempFile.absolutePath) ?: ImageExportFormat.JPEG
            val base = PdfToolsEngine.sourceBaseName(context, inputUri, "image")
            val outputFile = PdfToolsEngine.uniqueFile(outputDir.also { it.mkdirs() }, "${base}_cropped.${outFormat.extension}")
            cropToFile(tempFile, outputFile, spec, outFormat)
            userRequire(outputFile.exists() && outputFile.length() > 0, R.string.tools_err_crop_img)
            onProgress(2, 2, AppStrings.get(R.string.tools_progress_cropping_img))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outputFile.absolutePath, bounds)
            val outputSize = outputFile.length()
            PdfToolResult(
                outputFile = outputFile,
                pageCount = 1,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputSize,
                message = AppStrings.get(R.string.tools_crop_img_done, bounds.outWidth, bounds.outHeight, formatBytes(context, outputSize)),
            )
        } finally {
            tempFile.delete()
        }
    }

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
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "compress_img_${UUID.randomUUID()}.tmp")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_image_unreadable, 1)

            val aspect = ImageProcessor.imageAspect(tempFile.absolutePath)
            userRequire(aspect != null, R.string.tools_err_image_undecodable, 1)

            onProgress(1, 2, AppStrings.get(R.string.tools_progress_compressing_img))

            val targetBytes = config.targetSizeBytes
            val reachedTarget = if ((targetBytes != null) && (targetBytes > 0)) {
                compressToTargetSize(tempFile, outputFile, targetBytes, config.maxDimension, config.outputFormat)
            } else {
                compressWithQuality(tempFile, outputFile, config.qualityPercent, config.maxDimension, config.outputFormat)
                true
            }

            // Tiny or already-optimised pictures can come out bigger. When the format is unchanged, keep the original
            // bytes instead of handing back something larger than what the user picked.
            val sourceMime = BitmapFactory.Options().also {
                it.inJustDecodeBounds = true
                BitmapFactory.decodeFile(tempFile.absolutePath, it)
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
            // Says so when the size asked for could not be reached, rather than reporting the smallest try as a success.
            else if (!reachedTarget && targetBytes != null) AppStrings.get(
                R.string.tools_compress_img_target_missed,
                formatBytes(context, targetBytes),
                formatBytes(context, outputSize),
                savings,
            )
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
        val targetDim = maxDim ?: MAX_IMAGE_SIDE
        val bitmap = ImageProcessor.decodeOrientedScaled(sourceFile.absolutePath, targetDim)
            ?: userError(R.string.tools_err_image_undecodable, 1)

        val prepared = prepareForFormat(bitmap, format)
        if ((prepared !== bitmap) && !bitmap.isRecycled) bitmap.recycle()

        outputFile.deleteIfFails {
            FileOutputStream(outputFile).buffered(COPY_BUFFER).use { out ->
                encodeToStream(prepared, format, quality, out)
            }
        }
        prepared.recycle()
    }

    /**
     * Writes the best-looking version of [sourceFile] that fits in [targetBytes]: the full resolution (or [userMaxDim])
     * at the highest quality that fits, giving up resolution a step at a time only once the quality would have to drop
     * below [MIN_KEEP_QUALITY]. The old search started from a guessed resolution (1280 px for 50 KB), so a photo that
     * would have fitted at full size came out small. Returns false when nothing fitted; the smallest try is written then.
     */
    private fun compressToTargetSize(
        sourceFile: File,
        outputFile: File,
        targetBytes: Long,
        userMaxDim: Int?,
        format: ImageExportFormat
    ): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val origMax = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var currentDim = minOf(userMaxDim ?: origMax, origMax, MAX_IMAGE_SIDE)

        var smallest: ByteArray? = null
        var fitted: ByteArray? = null
        for (attempt in 0 until 8) {
            val bitmap = ImageProcessor.decodeOrientedScaled(sourceFile.absolutePath, currentDim) ?: break
            try {
                val found = bestQualityWithin(bitmap, format, targetBytes, MIN_KEEP_QUALITY)
                    // At the smallest size tried, any quality is better than missing the target.
                    ?: if (currentDim <= 320 || attempt == 7) bestQualityWithin(bitmap, format, targetBytes, 10) else null
                if (found != null) {
                    fitted = found.second
                    break
                }
                val floor = encodeBytes(bitmap, format, if (format == ImageExportFormat.PNG) 100 else 10)
                if (smallest == null || floor.size < smallest.size) smallest = floor
            } finally {
                bitmap.recycle()
            }
            val next = (currentDim * 0.8f).toInt().coerceAtLeast(320)
            if (next == currentDim) break
            currentDim = next
        }

        val bytes = fitted ?: smallest
        outputFile.deleteIfFails {
            if (bytes != null) FileOutputStream(outputFile).buffered(COPY_BUFFER).use { it.write(bytes) }
            else sourceFile.copyTo(outputFile, overwrite = true)
        }
        return fitted != null
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
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        userRequire(inputUris.isNotEmpty(), R.string.tools_err_no_images)
        outputLocation.parentFile?.mkdirs()

        val tempFiles = mutableListOf<File>()
        val convertedFiles = mutableListOf<File>()
        // Several pictures go to a folder of their own, named uniquely: two photos both called "IMG_0001" (from two
        // albums) used to overwrite each other, and so did an earlier run's results. The bundle step removes the folder.
        val runDir = if (inputUris.size > 1) File(outputLocation.parentFile, "convert_${UUID.randomUUID()}").also { it.mkdirs() } else null
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

                val bitmap = ImageProcessor.decodeOrientedScaled(tempFile.absolutePath, MAX_IMAGE_SIDE)
                    ?: userError(R.string.tools_err_image_undecodable, index + 1)

                val prepared = prepareForFormat(bitmap, targetFormat)
                if ((prepared !== bitmap) && !bitmap.isRecycled) bitmap.recycle()

                val singleOut = if (runDir == null) {
                    outputLocation
                } else {
                    PdfToolsEngine.uniqueFile(runDir, "$base.${targetFormat.extension}")
                }

                FileOutputStream(singleOut).buffered(COPY_BUFFER).use { out ->
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
        } catch (failure: Throwable) {
            runDir?.deleteRecursively()
            throw failure
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
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val targetWidth = config.targetWidth
        val targetHeight = config.targetHeight
        userRequire(targetWidth in 10..8000 && targetHeight in 10..8000, R.string.tools_err_invalid_dimensions)
        userRequire(targetWidth.toLong() * targetHeight <= MAX_RESIZE_PIXELS, R.string.tools_err_resize_too_large)
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

            outputFile.deleteIfFails {
                FileOutputStream(outputFile).buffered(COPY_BUFFER).use { out ->
                    encodeToStream(prepared, config.outputFormat, config.qualityPercent, out)
                }
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
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        outputFile.parentFile?.mkdirs()
        val tempFile = PdfToolsEngine.copyUriToTemp(context, inputUri, "crop_in_${UUID.randomUUID()}.tmp")
        val stagingOut = File(outputFile.parentFile, "staged_${UUID.randomUUID()}.jpg")
        try {
            val originalSize = tempFile.length()
            userRequire(originalSize > 0, R.string.tools_err_image_unreadable, 1)
            onProgress(1, 2, AppStrings.get(R.string.tools_progress_cropping_img))

            ImageProcessor.cropSourceToNewFile(tempFile, stagingOut, quad)
            userRequire(stagingOut.exists() && stagingOut.length() > 0, R.string.tools_err_crop_img)

            if (format != ImageExportFormat.JPEG) {
                val bmp = ImageProcessor.decodeOrientedScaled(stagingOut.absolutePath, 4200)
                    ?: userError(R.string.tools_err_image_undecodable, 1)
                val prepared = prepareForFormat(bmp, format)
                if (prepared !== bmp && !bmp.isRecycled) bmp.recycle()
                outputFile.deleteIfFails {
                    FileOutputStream(outputFile).buffered(COPY_BUFFER).use { out ->
                        encodeToStream(prepared, format, 100, out)
                    }
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
            stagingOut.delete() // also when the crop failed part-way
        }
    }

    private fun prepareForFormat(source: Bitmap, format: ImageExportFormat): Bitmap {
        if (format == ImageExportFormat.JPEG && source.hasAlpha()) {
            // A bitmap we may draw on is flattened where it is: a second copy beside a very large one is what ran out of memory.
            if (source.isMutable) {
                Canvas(source).drawColor(Color.WHITE, android.graphics.PorterDuff.Mode.DST_OVER)
                source.setHasAlpha(false)
                return source
            }
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
