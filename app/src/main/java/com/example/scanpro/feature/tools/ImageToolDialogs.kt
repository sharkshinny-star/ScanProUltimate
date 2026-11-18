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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.displayedKiloByte
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.ImageCompressConfig
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageResizeConfig
import com.example.scanpro.imaging.ImageResizeFit
import com.example.scanpro.imaging.ImageToolsEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Compress, Convert and Resize image (rebuilt, F193). Each screen shows the picture large with the real result of the
 * chosen settings beside the original, and a Now → After row (size, pixels, format) that updates as the options change,
 * so nobody has to run a tool to find out what it does. Defaults follow the picture: its own format, its own
 * resolution, Fit rather than a crop, and only the sizes that would actually make it smaller.
 */

/** Target sizes offered by Compress, in kB; only those clearly below the picture's own size are shown. */
private val TARGET_SIZES_KB = listOf(50, 100, 200, 300, 500, 1000, 2000)

/** Longest sides offered by Compress, below the picture's own. */
private val COMPRESS_SIDES = listOf(3840, 2560, 1920, 1280, 800)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompressImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageCompressConfig) -> Unit
) {
    val image by rememberLoadedImage(context, uri)
    // 0: a target size; 1: a quality.
    var mode by rememberSaveable { mutableIntStateOf(0) }
    // 0 until the person picks: then a preset in kB, or -1 for a typed size.
    var pickedKb by rememberSaveable { mutableIntStateOf(0) }
    var customKb by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableIntStateOf(70) }
    var maxSide by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickedFormat by rememberSaveable { mutableStateOf<ImageExportFormat?>(null) }

    val img = image
    val kb = displayedKiloByte()
    val originalBytes = img?.meta?.sizeBytes ?: 0L
    val presets = TARGET_SIZES_KB.filter { originalBytes <= 0 || it * kb < originalBytes * 0.9 }
    // About half the size by default: the biggest preset at or under half, else the smallest one there is.
    val defaultKb = presets.lastOrNull { it * kb <= originalBytes / 2.0 } ?: presets.firstOrNull() ?: 0
    val typing = pickedKb == -1 || presets.isEmpty()
    val targetKb = when {
        typing -> customKb.toIntOrNull() ?: 0
        pickedKb > 0 -> pickedKb
        else -> defaultKb
    }
    val targetBytes = (targetKb * kb).toLong()
    // A transparent PNG keeps its transparency in WebP; anything else defaults to JPG, or stays WebP.
    val defaultFormat = if (img?.format == ImageExportFormat.WEBP || img?.transparent == true) ImageExportFormat.WEBP else ImageExportFormat.JPEG
    val format = pickedFormat ?: defaultFormat
    val originalSide = img?.let { max(it.width, it.height) } ?: 0
    val side = minOf(maxSide ?: originalSide, originalSide)

    val targetTooBig = mode == 0 && originalBytes > 0 && targetBytes >= originalBytes
    val targetMissing = mode == 0 && targetKb <= 0
    val estimate by rememberEstimate(img, mode, targetBytes, quality, side, format) {
        val loaded = img ?: return@rememberEstimate null
        if (mode == 0) {
            if (targetBytes <= 0) null else estimateTargetSize(loaded, side, format, targetBytes)
        } else {
            val scale = side.toFloat() / max(loaded.width, loaded.height)
            estimateOutput(loaded, (loaded.width * scale).roundToInt().coerceAtLeast(1), (loaded.height * scale).roundToInt().coerceAtLeast(1), format, quality)
        }
    }

    ToolPage(
        title = stringResource(R.string.tools_compress_img_title),
        demoToolId = "compress_img",
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_compress_img_action),
        onAction = {
            onConfirm(
                ImageCompressConfig(
                    targetSizeBytes = if (mode == 0) targetBytes else null,
                    qualityPercent = quality,
                    maxDimension = maxSide?.takeIf { it < originalSide },
                    outputFormat = format
                )
            )
        },
        actionEnabled = img != null && !targetTooBig && !targetMissing,
        actionSupport = when {
            targetTooBig -> stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, originalBytes))
            targetMissing -> stringResource(R.string.tools_compress_err_target_positive)
            else -> null
        }
    ) {
        ImagePreviewCard(img, estimate, onCrop, loading = img != null && estimate == null && !targetMissing)
        if (img != null) BeforeAfterRow(context, img, estimate, format)
        val noGain = img != null && originalBytes > 0 && estimate?.let { it.bytes >= originalBytes } == true
        if (noGain) ImageToolNotice(stringResource(R.string.img_compress_no_gain))

        ToolSection(stringResource(R.string.img_section_make_smaller)) {
            SegmentedChoice(
                options = listOf(0 to stringResource(R.string.tools_compress_img_mode_target), 1 to stringResource(R.string.tools_compress_img_mode_quality)),
                selected = mode,
                onSelect = { mode = it }
            )
            if (mode == 0) {
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        presets.forEach { size ->
                            FilterChip(
                                selected = !typing && targetKb == size,
                                onClick = { pickedKb = size },
                                label = { Text(if (size >= 1000) "${size / 1000} MB" else "$size kB") }
                            )
                        }
                        FilterChip(
                            selected = typing,
                            onClick = { pickedKb = -1; if (customKb.isEmpty()) customKb = targetKb.toString() },
                            label = { Text(stringResource(R.string.tools_compress_img_custom_size)) }
                        )
                    }
                } else if (img != null) {
                    ToolHint(stringResource(R.string.img_compress_already_small, formatBytes(context, originalBytes)))
                }
                if (typing) {
                    OutlinedTextField(
                        value = customKb,
                        onValueChange = { typed -> if (typed.length <= 6 && typed.all { it.isDigit() }) customKb = typed },
                        label = { Text(stringResource(R.string.tools_compress_img_target_kb)) },
                        suffix = { Text(stringResource(R.string.tools_compress_unit_kb)) },
                        singleLine = true,
                        isError = targetTooBig || targetMissing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ToolHint(stringResource(R.string.img_compress_target_hint))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(85 to R.string.img_quality_high, 70 to R.string.img_quality_balanced, 45 to R.string.img_quality_small).forEach { (q, label) ->
                        FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(stringResource(label)) })
                    }
                }
                QualitySlider(quality, onChange = { quality = it }, range = 10..95)
            }
        }

        if (img != null) {
            ToolSection(stringResource(R.string.img_section_resolution)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = side == originalSide,
                        onClick = { maxSide = null },
                        label = { Text(stringResource(R.string.img_res_original, originalSide)) }
                    )
                    COMPRESS_SIDES.filter { it < originalSide }.forEach { option ->
                        FilterChip(selected = side == option, onClick = { maxSide = option }, label = { Text("$option px") })
                    }
                }
                ToolHint(stringResource(R.string.img_resolution_hint))
            }
        }

        ToolSection(stringResource(R.string.img_section_format)) {
            SegmentedChoice(
                options = listOf(ImageExportFormat.JPEG to "JPG", ImageExportFormat.WEBP to "WebP"),
                selected = format,
                onSelect = { pickedFormat = it }
            )
            if (img?.transparent == true && format == ImageExportFormat.JPEG) ImageToolNotice(stringResource(R.string.img_transparency_white))
            ToolHint(stringResource(R.string.tools_compress_img_help))
        }
    }
}

@Composable
internal fun ConvertImageDialog(
    uris: List<Uri>,
    onDismiss: () -> Unit,
    onCropImage: (Int) -> Unit,
    onConfirm: (targetFormat: ImageExportFormat, quality: Int) -> Unit,
    onConvertToPdf: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val single = uris.size == 1
    val image by rememberLoadedImage(context, uris.first())
    // Null until picked: then a format, or PDF (exportPdf).
    var pickedFormat by rememberSaveable { mutableStateOf<ImageExportFormat?>(null) }
    var exportPdf by rememberSaveable { mutableStateOf(false) }
    var quality by rememberSaveable { mutableIntStateOf(92) }

    val img = image
    val source = img?.format
    // The usual conversion away from what the picture is: JPG and WebP to PNG, PNG to JPG (or WebP when transparent).
    val defaultFormat = when (source) {
        ImageExportFormat.PNG -> if (img?.transparent == true) ImageExportFormat.WEBP else ImageExportFormat.JPEG
        null -> ImageExportFormat.JPEG
        else -> ImageExportFormat.PNG
    }
    val format = pickedFormat ?: defaultFormat
    val sameFormat = single && !exportPdf && source == format
    val lossy = !exportPdf && format != ImageExportFormat.PNG
    val chosenQuality = if (format == ImageExportFormat.PNG) 100 else quality

    val estimate by rememberEstimate(img, format, chosenQuality, exportPdf, single) {
        val loaded = img ?: return@rememberEstimate null
        if (!single || exportPdf) null else estimateOutput(loaded, loaded.width, loaded.height, format, chosenQuality)
    }

    ToolPage(
        title = if (single) stringResource(R.string.tools_convert_img_title)
        else stringResource(R.string.tools_convert_img_title) + " (${uris.size})",
        demoToolId = "convert_img",
        onClose = onDismiss,
        actionLabel = if (exportPdf) stringResource(R.string.img_convert_make_pdf) else stringResource(R.string.img_convert_action, formatLabel(format)),
        onAction = { if (exportPdf) onConvertToPdf() else onConfirm(format, chosenQuality) },
        actionEnabled = !sameFormat && (img != null || !single),
        actionSupport = if (sameFormat) stringResource(R.string.img_convert_already, formatLabel(source)) else null
    ) {
        if (single) {
            ImagePreviewCard(img, estimate, onCrop = { onCropImage(0) }, loading = img != null && estimate == null && !exportPdf)
            if (img != null && !exportPdf) BeforeAfterRow(context, img, estimate, format)
        } else {
            ToolSection(pluralStringResource(R.plurals.img_pictures_count, uris.size, uris.size)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(uris, key = { index, item -> "$index:$item" }) { index, item ->
                        ConvertThumb(context, item, index, onCrop = { onCropImage(index) })
                    }
                }
                ToolHint(stringResource(R.string.img_convert_tap_to_crop))
            }
        }

        ToolSection(stringResource(R.string.img_section_convert_to)) {
            SegmentedChoice(
                options = listOf("jpg" to "JPG", "png" to "PNG", "webp" to "WebP", "pdf" to "PDF"),
                selected = if (exportPdf) "pdf" else when (format) {
                    ImageExportFormat.JPEG -> "jpg"
                    ImageExportFormat.PNG -> "png"
                    ImageExportFormat.WEBP -> "webp"
                },
                onSelect = { choice ->
                    exportPdf = choice == "pdf"
                    when (choice) {
                        "jpg" -> pickedFormat = ImageExportFormat.JPEG
                        "png" -> pickedFormat = ImageExportFormat.PNG
                        "webp" -> pickedFormat = ImageExportFormat.WEBP
                    }
                }
            )
            ToolHint(
                stringResource(
                    when {
                        exportPdf -> R.string.tools_convert_img_format_pdf_desc
                        format == ImageExportFormat.PNG -> R.string.tools_convert_img_format_png_desc
                        format == ImageExportFormat.JPEG -> R.string.tools_convert_img_format_jpg_desc
                        else -> R.string.tools_convert_img_format_webp_desc
                    }
                )
            )
            if (!exportPdf && format == ImageExportFormat.JPEG && (img?.transparent == true || (!single && source == ImageExportFormat.PNG))) {
                ImageToolNotice(stringResource(R.string.img_transparency_white))
            }
        }

        if (lossy) {
            ToolSection(stringResource(R.string.img_section_quality)) {
                QualitySlider(quality, onChange = { quality = it }, range = 60..100, losslessAtTop = format == ImageExportFormat.WEBP)
                ToolHint(stringResource(R.string.img_convert_quality_hint))
            }
        }
    }
}

/** One picture of a several-picture Convert: a thumbnail with its format, and Crop on it. */
@Composable
private fun ConvertThumb(context: Context, uri: Uri, index: Int, onCrop: () -> Unit) {
    val thumb by produceState<Pair<Bitmap?, String?>>(null to null, uri) {
        value = withContext(Dispatchers.IO) { decodeThumb(context, uri, 240) }
    }
    val cropLabel = stringResource(R.string.img_crop_picture_n, index + 1)
    Box(
        Modifier
            .size(width = 96.dp, height = 120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        thumb.first?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        thumb.second?.let { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        IconButton(
            onClick = onCrop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .semantics { contentDescription = cropLabel }
        ) {
            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** A small copy of [uri] and its format label ("JPG"), read from the stream without loading the full picture. */
private fun decodeThumb(context: Context, uri: Uri, side: Int): Pair<Bitmap?, String?> = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    val label = when (ImageToolsEngine.formatForMime(bounds.outMimeType)) {
        ImageExportFormat.JPEG -> "JPG"
        ImageExportFormat.PNG -> "PNG"
        ImageExportFormat.WEBP -> "WebP"
        null -> bounds.outMimeType?.substringAfter('/')?.uppercase()
    }
    bitmap to label
}.getOrDefault(null to null)

/** Quality as a slider with its value; "Lossless" at the top for WebP, which switches to its lossless mode there. */
@Composable
private fun QualitySlider(value: Int, onChange: (Int) -> Unit, range: IntRange, losslessAtTop: Boolean = false) {
    val label = if (losslessAtTop && value >= 100) stringResource(R.string.img_lossless) else "$value%"
    val description = stringResource(R.string.img_section_quality)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.img_quality_smaller_file), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.img_quality_better_picture), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / 5f).roundToInt() * 5) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }
        )
    }
}

/** Common sizes offered by Resize, in pixels, with their names. */
private val RESIZE_PRESETS = listOf(
    Triple(600, 600, R.string.tools_resize_img_preset_passport),
    Triple(400, 400, R.string.tools_resize_img_preset_avatar),
    Triple(1080, 1080, R.string.tools_resize_img_preset_instagram),
    Triple(1080, 1920, R.string.tools_resize_img_preset_story),
    Triple(1920, 1080, R.string.tools_resize_img_preset_fhd),
    Triple(1280, 720, R.string.tools_resize_img_preset_hd),
    Triple(2480, 3508, R.string.tools_resize_img_preset_a4)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResizeImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageResizeConfig) -> Unit
) {
    val image by rememberLoadedImage(context, uri)
    // 0: by percentage; 1: to a size in pixels.
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var percent by rememberSaveable { mutableIntStateOf(50) }
    var widthInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var linked by rememberSaveable { mutableStateOf(true) }
    var fit by rememberSaveable { mutableStateOf(ImageResizeFit.FIT) }
    var pickedFormat by rememberSaveable { mutableStateOf<ImageExportFormat?>(null) }

    val img = image
    val origW = img?.width ?: 0
    val origH = img?.height ?: 0
    androidx.compose.runtime.LaunchedEffect(img) {
        if (img != null && widthInput.isEmpty() && heightInput.isEmpty()) {
            widthInput = (img.width / 2).coerceAtLeast(10).toString()
            heightInput = (img.height / 2).coerceAtLeast(10).toString()
        }
    }
    val aspect = if (origH > 0) origW.toFloat() / origH else 1f

    // The box asked for.
    val boxW: Int
    val boxH: Int
    if (mode == 0) {
        val scale = (percent / 100f).coerceIn(10f / maxOf(1, minOf(origW, origH)), 8000f / maxOf(1, max(origW, origH)))
        boxW = (origW * scale).roundToInt()
        boxH = (origH * scale).roundToInt()
    } else {
        boxW = widthInput.toIntOrNull() ?: 0
        boxH = heightInput.toIntOrNull() ?: 0
    }
    // Only a box of another shape needs Fit, Fill or Stretch.
    val shapeDiffers = mode == 1 && boxW > 0 && boxH > 0 && origH > 0 &&
        abs(boxW.toFloat() / boxH - aspect) / aspect > 0.01f
    val usedFit = if (shapeDiffers) fit else ImageResizeFit.STRETCH
    // What Fit really gives: the picture's shape inside the box, so maybe smaller than the box on one side.
    val outW: Int
    val outH: Int
    if (usedFit == ImageResizeFit.FIT && origW > 0 && origH > 0 && boxW > 0 && boxH > 0) {
        val k = minOf(boxW.toFloat() / origW, boxH.toFloat() / origH)
        outW = (origW * k).roundToInt().coerceAtLeast(1)
        outH = (origH * k).roundToInt().coerceAtLeast(1)
    } else {
        outW = boxW
        outH = boxH
    }

    val format = pickedFormat ?: img?.format ?: ImageExportFormat.JPEG
    val valid = boxW in 10..8000 && boxH in 10..8000
    val unchanged = img != null && outW == origW && outH == origH && format == img.format
    val enlarging = img != null && (outW > origW || outH > origH)

    val estimate by rememberEstimate(img, outW, outH, usedFit, format, valid) {
        val loaded = img ?: return@rememberEstimate null
        if (!valid) null else estimateOutput(loaded, outW, outH, format, 92, shapeForFit(usedFit))
    }

    ToolPage(
        title = stringResource(R.string.tools_resize_img_title),
        demoToolId = "resize_img",
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_resize_img_action),
        onAction = { onConfirm(ImageResizeConfig(targetWidth = boxW, targetHeight = boxH, outputFormat = format, fit = usedFit)) },
        actionEnabled = img != null && valid && !unchanged,
        actionSupport = when {
            img != null && !valid -> stringResource(R.string.tools_err_invalid_dimensions)
            unchanged -> stringResource(R.string.tools_resize_img_unchanged)
            else -> null
        }
    ) {
        ImagePreviewCard(img, estimate, onCrop, loading = img != null && estimate == null && valid)
        if (img != null) BeforeAfterRow(context, img, estimate, format)
        if (enlarging) ImageToolNotice(stringResource(R.string.img_resize_enlarge_warning))

        ToolSection(stringResource(R.string.img_section_new_size)) {
            SegmentedChoice(
                options = listOf(0 to stringResource(R.string.tools_resize_img_mode_percent), 1 to stringResource(R.string.tools_resize_img_mode_dimensions)),
                selected = mode,
                onSelect = { mode = it }
            )
            if (mode == 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 75, 150, 200).forEach { pct ->
                        FilterChip(selected = percent == pct, onClick = { percent = pct }, label = { Text("$pct%") })
                    }
                }
                val percentDescription = stringResource(R.string.tools_resize_img_mode_percent)
                Text(
                    "$percent%  ·  " + stringResource(R.string.img_tool_dims, boxW, boxH),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { percent = ((it / 5f).roundToInt() * 5).coerceIn(10, 300) },
                    valueRange = 10f..300f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = percentDescription }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { typed ->
                            if (typed.length <= 5 && typed.all { it.isDigit() }) {
                                widthInput = typed
                                val w = typed.toIntOrNull()
                                if (linked && w != null && w > 0) heightInput = (w / aspect).roundToInt().coerceAtLeast(1).toString()
                            }
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_width)) },
                        suffix = { Text("px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        linked = !linked
                        // Re-linking snaps the height back to the picture's proportions.
                        val w = widthInput.toIntOrNull()
                        if (linked && w != null && w > 0) heightInput = (w / aspect).roundToInt().coerceAtLeast(1).toString()
                    }) {
                        Icon(
                            if (linked) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = stringResource(R.string.tools_resize_img_lock_aspect),
                            tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { typed ->
                            if (typed.length <= 5 && typed.all { it.isDigit() }) {
                                heightInput = typed
                                val h = typed.toIntOrNull()
                                if (linked && h != null && h > 0) widthInput = (h * aspect).roundToInt().coerceAtLeast(1).toString()
                            }
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_height)) },
                        suffix = { Text("px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                ToolHint(stringResource(if (linked) R.string.img_resize_linked_hint else R.string.img_resize_unlinked_hint))
                Text(stringResource(R.string.img_section_common_sizes), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RESIZE_PRESETS.forEach { (pw, ph, label) ->
                        FilterChip(
                            selected = boxW == pw && boxH == ph,
                            onClick = {
                                linked = false
                                widthInput = pw.toString()
                                heightInput = ph.toString()
                            },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }

        if (shapeDiffers) {
            ToolSection(stringResource(R.string.tools_resize_fit_label)) {
                ChoiceGroup {
                    ChoiceCard(fit == ImageResizeFit.FIT, stringResource(R.string.tools_resize_fit_fit), { fit = ImageResizeFit.FIT }, stringResource(R.string.img_fit_fit_desc))
                    ChoiceCard(fit == ImageResizeFit.FILL, stringResource(R.string.tools_resize_fit_fill), { fit = ImageResizeFit.FILL }, stringResource(R.string.img_fit_fill_desc))
                    ChoiceCard(fit == ImageResizeFit.STRETCH, stringResource(R.string.tools_resize_fit_stretch), { fit = ImageResizeFit.STRETCH }, stringResource(R.string.img_fit_stretch_desc))
                }
            }
        }

        ToolSection(stringResource(R.string.img_section_format)) {
            SegmentedChoice(
                options = listOf(ImageExportFormat.JPEG to "JPG", ImageExportFormat.PNG to "PNG", ImageExportFormat.WEBP to "WebP"),
                selected = format,
                onSelect = { pickedFormat = it }
            )
            val sourceFormat = img?.format
            if (sourceFormat != null && format == sourceFormat) ToolHint(stringResource(R.string.img_format_kept, formatLabel(sourceFormat)))
            if (img?.transparent == true && format == ImageExportFormat.JPEG) ImageToolNotice(stringResource(R.string.img_transparency_white))
        }
    }
}
