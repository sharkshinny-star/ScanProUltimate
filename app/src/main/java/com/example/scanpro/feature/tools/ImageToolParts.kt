package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageMetadata
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.ImageResizeFit
import com.example.scanpro.imaging.ImageToolsEngine
import com.example.scanpro.imaging.PdfToolsEngine
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The longest side of the copy a tool screen shows and estimates from. */
private const val PREVIEW_SIDE = 1280

/** A picked picture as a tool screen needs it: its facts, its format and an upright preview-sized copy. */
internal class LoadedImage(
    val meta: ImageMetadata,
    /** JPEG, PNG or WebP; null for other formats (HEIC and the like), which every tool turns into one of those. */
    val format: ImageExportFormat?,
    val preview: Bitmap,
    /** True when the picture has see-through pixels: a JPG would turn them white. */
    val transparent: Boolean,
    /** Width and height as the picture is seen, with the camera's EXIF turn applied (as every tool writes it). */
    val width: Int,
    val height: Int,
)

/** Loads [uri] once, off the main thread; null while loading, and null again if it cannot be read. */
@Composable
internal fun rememberLoadedImage(context: Context, uri: Uri): State<LoadedImage?> =
    produceState<LoadedImage?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadImage(context, uri) }
    }

internal fun loadImage(context: Context, uri: Uri): LoadedImage? = runCatching {
    val meta = ImageToolsEngine.inspectImage(context, uri) ?: return null
    val temp = PdfToolsEngine.copyUriToTemp(context, uri, "img_preview_${UUID.randomUUID()}.tmp")
    try {
        val preview = ImageProcessor.decodeOrientedScaled(temp.absolutePath, PREVIEW_SIDE) ?: return null
        val turn = runCatching { androidx.exifinterface.media.ExifInterface(temp.absolutePath).rotationDegrees }.getOrDefault(0)
        val sideways = turn == 90 || turn == 270
        LoadedImage(
            meta = meta,
            format = ImageToolsEngine.formatForMime(meta.mimeType),
            preview = preview,
            transparent = preview.hasAlpha() && hasSeeThroughPixels(preview),
            width = if (sideways) meta.height else meta.width,
            height = if (sideways) meta.width else meta.height
        )
    } finally {
        temp.delete()
    }
}.getOrNull()

/** Samples a grid of pixels: a PNG can carry an alpha channel without using it. */
private fun hasSeeThroughPixels(bitmap: Bitmap): Boolean {
    val stepX = max(1, bitmap.width / 48)
    val stepY = max(1, bitmap.height / 48)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) < 250) return true
            x += stepX
        }
        y += stepY
    }
    return false
}

/** What a tool will produce, worked out on the preview copy. [approximate] when it was scaled up from a smaller sample. */
internal class ImageEstimate(val bytes: Long, val width: Int, val height: Int, val preview: Bitmap?, val approximate: Boolean)

/**
 * Re-runs [compute] a moment after its inputs ([keys]) stop changing, off the main thread, so the screen shows what the
 * tool will really produce while the person is still choosing.
 */
@Composable
internal fun rememberEstimate(vararg keys: Any?, compute: () -> ImageEstimate?): State<ImageEstimate?> =
    produceState<ImageEstimate?>(null, *keys) {
        value = null
        delay(250)
        value = withContext(Dispatchers.Default) { runCatching(compute).getOrNull() }
    }

/**
 * Encodes [image]'s preview made into a [outWidth] × [outHeight] picture by [shape] (resize and fit), as [format] at
 * [quality], and scales the size up to the full picture. Sizes from a sample are close, not exact, hence "About".
 */
internal fun estimateOutput(
    image: LoadedImage,
    outWidth: Int,
    outHeight: Int,
    format: ImageExportFormat,
    quality: Int,
    shape: (Bitmap, Int, Int) -> Bitmap = { b, w, h -> Bitmap.createScaledBitmap(b, w, h, true) },
): ImageEstimate {
    val (sample, k) = sampleFor(image, outWidth, outHeight, shape)
    val bytes = ImageToolsEngine.encodeBytes(sample, format, quality)
    if (sample !== image.preview) sample.recycle()
    return ImageEstimate(
        bytes = (bytes.size / (k * k)).toLong(),
        width = outWidth,
        height = outHeight,
        preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size),
        approximate = k < 0.999f
    )
}

/**
 * What Compress with a target size will produce: the engine's own search (keep the resolution while the quality can stay
 * at 40 or more, otherwise 80% smaller a step at a time), run on samples.
 */
internal fun estimateTargetSize(image: LoadedImage, maxSide: Int, format: ImageExportFormat, targetBytes: Long): ImageEstimate {
    var side = maxSide
    var last: ImageEstimate? = null
    repeat(8) { attempt ->
        val scale = side.toFloat() / max(image.width, image.height)
        val w = (image.width * scale).roundToInt().coerceAtLeast(1)
        val h = (image.height * scale).roundToInt().coerceAtLeast(1)
        val (sample, k) = sampleFor(image, w, h) { b, sw, sh -> Bitmap.createScaledBitmap(b, sw, sh, true) }
        val sampleTarget = (targetBytes * k * k).toLong().coerceAtLeast(1)
        val minQuality = if (side <= 320 || attempt == 7) 10 else 40
        val found = ImageToolsEngine.bestQualityWithin(sample, format, sampleTarget, minQuality)
        val bytes = found?.second ?: ImageToolsEngine.encodeBytes(sample, format, 10)
        if (sample !== image.preview) sample.recycle()
        last = ImageEstimate((bytes.size / (k * k)).toLong(), w, h, BitmapFactory.decodeByteArray(bytes, 0, bytes.size), k < 0.999f)
        if (found != null) return last!!
        val next = (side * 0.8f).toInt().coerceAtLeast(320)
        if (next == side) return last!!
        side = next
    }
    return last!!
}

/** The preview shaped to [w] × [h], or to the same proportions at the preview's own size when that is smaller; and the scale used. */
private fun sampleFor(image: LoadedImage, w: Int, h: Int, shape: (Bitmap, Int, Int) -> Bitmap): Pair<Bitmap, Float> {
    val previewPixels = image.preview.width.toFloat() * image.preview.height
    val k = minOf(1f, sqrt(previewPixels / (w.toFloat() * h)))
    val sw = (w * k).roundToInt().coerceAtLeast(1)
    val sh = (h * k).roundToInt().coerceAtLeast(1)
    val sample = if (sw == image.preview.width && sh == image.preview.height) image.preview else shape(image.preview, sw, sh)
    return sample to k
}

/** [source] fitted into, filled to or stretched over a [w] × [h] box, as Resize does it. */
internal fun shapeForFit(fit: ImageResizeFit): (Bitmap, Int, Int) -> Bitmap = { source, w, h ->
    when (fit) {
        ImageResizeFit.STRETCH, ImageResizeFit.FIT -> Bitmap.createScaledBitmap(source, w, h, true)
        ImageResizeFit.FILL -> {
            val k = maxOf(w.toFloat() / source.width, h.toFloat() / source.height)
            val gw = kotlin.math.ceil(source.width * k).toInt().coerceAtLeast(w)
            val gh = kotlin.math.ceil(source.height * k).toInt().coerceAtLeast(h)
            val grown = Bitmap.createScaledBitmap(source, gw, gh, true)
            val cut = Bitmap.createBitmap(grown, (gw - w) / 2, (gh - h) / 2, w, h)
            if (grown !== cut && grown !== source) grown.recycle()
            cut
        }
    }
}

/** "JPG", "PNG", "WebP"; "Image" for anything else. */
@Composable
internal fun formatLabel(format: ImageExportFormat?): String = when (format) {
    ImageExportFormat.JPEG -> "JPG"
    ImageExportFormat.PNG -> "PNG"
    ImageExportFormat.WEBP -> "WebP"
    null -> stringResource(R.string.img_tool_other_format)
}

/**
 * The picture large, on a chequerboard so transparency shows, with Crop in the corner. When there is a [result], a
 * switch underneath shows Original or Result: the result is the real output of the chosen settings (on a sample), so
 * compression blur, a crop from Fill or a white background is seen before the tool runs.
 */
@Composable
internal fun ImagePreviewCard(image: LoadedImage?, result: ImageEstimate?, onCrop: (() -> Unit)?, loading: Boolean = false) {
    var showOriginal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .chequerboard()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            val resultPreview = result?.preview
            val shown = if (showOriginal || resultPreview == null) image?.preview else resultPreview
            if (shown != null) {
                Image(
                    shown.asImageBitmap(),
                    contentDescription = stringResource(if (shown === image?.preview) R.string.img_tool_original else R.string.img_tool_result),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                )
            } else {
                FileLoadingAnimation(size = 88.dp)
            }
            if (loading && image != null) {
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                        Text(
                            stringResource(R.string.img_tool_estimating),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (onCrop != null && image != null) {
                FilledTonalButton(
                    onClick = onCrop,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.img_tool_crop))
                }
            }
        }
        if (result?.preview != null) {
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.img_tool_result), true to stringResource(R.string.img_tool_original)),
                selected = showOriginal,
                onSelect = { showOriginal = it }
            )
        }
    }
}

/** A chequerboard behind the picture, so see-through areas read as see-through, not as white. */
@Composable
private fun Modifier.chequerboard(): Modifier {
    val light = MaterialTheme.colorScheme.surfaceContainerLow
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    return drawBehind {
        drawRect(light)
        val cell = 10.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(dark, topLeft = Offset(x, y), size = Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

/**
 * Now → After, side by side: size, pixel size and format, with the change in size as a badge. [after] null while it
 * is being worked out.
 */
@Composable
internal fun BeforeAfterRow(context: Context, image: LoadedImage, after: ImageEstimate?, afterFormat: ImageExportFormat?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SizeColumn(
                label = stringResource(R.string.img_tool_now),
                size = formatBytes(context, image.meta.sizeBytes),
                detail = stringResource(R.string.img_tool_dims, image.width, image.height) + " · " + formatLabel(image.format),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                if (after == null) {
                    SizeColumn(stringResource(R.string.img_tool_after), "…", stringResource(R.string.img_tool_estimating))
                } else {
                    val size = formatBytes(context, after.bytes)
                    SizeColumn(
                        label = stringResource(R.string.img_tool_after),
                        size = if (after.approximate) stringResource(R.string.img_tool_about, size) else size,
                        detail = stringResource(R.string.img_tool_dims, after.width, after.height) + " · " + formatLabel(afterFormat)
                    )
                    val original = image.meta.sizeBytes
                    if (original > 0) {
                        val change = ((after.bytes - original) * 100f / original).roundToInt()
                        if (change != 0) {
                            Spacer(Modifier.height(6.dp))
                            ChangeBadge(change)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeColumn(label: String, size: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(size, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** "62% smaller" in green, or "12% larger" in the error colour. */
@Composable
private fun ChangeBadge(changePercent: Int) {
    val smaller = changePercent < 0
    val background = if (smaller) Color(0xFF1B7F4B).copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer
    val foreground = if (smaller) Color(0xFF1B7F4B) else MaterialTheme.colorScheme.onErrorContainer
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(
            if (smaller) stringResource(R.string.img_tool_smaller, -changePercent) else stringResource(R.string.img_tool_larger, changePercent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
    }
}

/** A notice on a tinted card: a warning that is not an error (white background, enlarging). */
@Composable
internal fun ImageToolNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
