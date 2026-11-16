package com.example.scanpro.feature.tools

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.scanpro.imaging.ImageCompressConfig
import com.example.scanpro.imaging.ImageMetadata
import com.example.scanpro.imaging.ImageResizeConfig
import com.example.scanpro.imaging.ImageResizeFit
import com.example.scanpro.imaging.ImageToolsEngine
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.displayedKiloByte
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.ImageExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CompressImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageCompressConfig) -> Unit
) {
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            metadata = ImageToolsEngine.inspectImage(context, uri)
        }
    }

    var mode by rememberSaveable { mutableStateOf(0) }
    var selectedPresetKb by rememberSaveable { mutableStateOf(100) }
    var customKbInput by rememberSaveable { mutableStateOf("100") }
    var qualityPercent by rememberSaveable { mutableStateOf(75) }
    var maxDim by rememberSaveable { mutableStateOf<Int?>(null) }
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }

    val origBytes = metadata?.sizeBytes ?: 0L
    val targetKb = if (selectedPresetKb > 0) selectedPresetKb else customKbInput.toIntOrNull() ?: 0
    val targetBytes = if (mode == 0 && targetKb > 0) (targetKb * displayedKiloByte()).toLong() else null

    val isTargetLarger = mode == 0 && origBytes > 0 && (targetBytes ?: 0L) >= origBytes
    val isTargetZero = mode == 0 && targetKb <= 0

    val canConfirm = mode != 0 || targetKb > 0 && !isTargetLarger
    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(R.string.tools_compress_img_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_compress_img_action),
        onAction = {
            onConfirm(
                ImageCompressConfig(
                    targetSizeBytes = if (mode == 0) targetBytes else null,
                    qualityPercent = qualityPercent,
                    maxDimension = maxDim,
                    outputFormat = format
                )
            )
        },
        actionEnabled = canConfirm,
        actionSupport = when {
            // The reason the button is disabled, where it can be seen: it used to be greyed out with no explanation
            // when the chosen size was bigger than the photo.
            isTargetLarger -> stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, origBytes))
            isTargetZero -> stringResource(R.string.tools_compress_err_target_positive)
            else -> null
        }
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                metadata?.let { meta ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    meta.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatBytes(context, meta.sizeBytes)} • ${meta.width} × ${meta.height} px",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tools_crop_img_button))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(stringResource(R.string.tools_compress_img_mode_target)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(stringResource(R.string.tools_compress_img_mode_quality)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (mode == 0) {
                    Text(
                        stringResource(R.string.tools_compress_img_quick_sizes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 100, 200, 500, 1024, 2048).forEach { kb ->
                            val label = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
                            FilterChip(
                                selected = selectedPresetKb == kb,
                                onClick = {
                                    selectedPresetKb = kb
                                    customKbInput = kb.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = selectedPresetKb == -1,
                            onClick = { selectedPresetKb = -1 },
                            label = { Text(stringResource(R.string.tools_compress_img_custom_size)) }
                        )
                    }

                    if (selectedPresetKb == -1) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customKbInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    customKbInput = str
                                }
                            },
                            label = { Text(stringResource(R.string.tools_compress_img_target_kb)) },
                            suffix = { Text(stringResource(R.string.tools_compress_unit_kb)) },
                            singleLine = true,
                            isError = isTargetLarger || isTargetZero,
                            supportingText = {
                                if (isTargetLarger) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, origBytes)),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isTargetZero) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_positive),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.tools_compress_img_quality_label, qualityPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = qualityPercent.toFloat(),
                        onValueChange = { qualityPercent = it.roundToInt() },
                        valueRange = 15f..95f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.tools_compress_img_resolution_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val resOptions = listOf(
                        null to stringResource(R.string.tools_compress_img_res_auto),
                        2048 to stringResource(R.string.tools_compress_img_res_2048),
                        1600 to stringResource(R.string.tools_compress_img_res_1600),
                        1280 to stringResource(R.string.tools_compress_img_res_1280),
                        800 to stringResource(R.string.tools_compress_img_res_800)
                    )
                    resOptions.forEach { (dim, label) ->
                        FilterChip(
                            selected = maxDim == dim,
                            onClick = { maxDim = dim },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.tools_convert_img_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ImageExportFormat.JPEG,
                        onClick = { format = ImageExportFormat.JPEG },
                        label = { Text("JPG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.WEBP,
                        onClick = { format = ImageExportFormat.WEBP },
                        label = { Text("WEBP") }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.tools_compress_img_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.PNG) }
    var exportPdf by rememberSaveable { mutableStateOf(false) }
    var jpegQuality by rememberSaveable { mutableStateOf(92) }

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = if (uris.size <= 1) stringResource(R.string.tools_convert_img_title)
                else stringResource(R.string.tools_convert_img_title) + " (${uris.size})",
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_convert_img_action),
        onAction = {
            if (exportPdf) onConvertToPdf()
            else onConfirm(format, if (format == ImageExportFormat.PNG) 100 else jpegQuality)
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uris.size == 1) {
                    OutlinedButton(
                        onClick = { onCropImage(0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tools_crop_img_button))
                    }
                } else {
                    Text(
                        stringResource(R.string.tools_crop_img_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(uris, key = { idx, uri -> "$idx:$uri" }, contentType = { _, _ -> "crop_item" }) { idx, _ ->
                            OutlinedButton(
                                onClick = { onCropImage(idx) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.tools_page_number, idx + 1), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.PNG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.PNG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.PNG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.PNG,
                                onClick = { exportPdf = false; format = ImageExportFormat.PNG }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_png),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    stringResource(R.string.tools_convert_img_lossless_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_png_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.JPEG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.JPEG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.JPEG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.JPEG,
                                onClick = { exportPdf = false; format = ImageExportFormat.JPEG }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_jpg),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_jpg_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                        if (!exportPdf && format == ImageExportFormat.JPEG) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tools_compress_img_quality_label, jpegQuality),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 40.dp)
                            )
                            Slider(
                                value = jpegQuality.toFloat(),
                                onValueChange = { jpegQuality = it.roundToInt() },
                                valueRange = 70f..100f,
                                steps = 5,
                                modifier = Modifier.padding(start = 32.dp).fillMaxWidth()
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.WEBP) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.WEBP) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.WEBP }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.WEBP,
                                onClick = { exportPdf = false; format = ImageExportFormat.WEBP }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_webp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_webp_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (exportPdf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (exportPdf) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = true }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportPdf,
                                onClick = { exportPdf = true }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_pdf),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_pdf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ResizeImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageResizeConfig) -> Unit
) {
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var resizeMode by rememberSaveable { mutableIntStateOf(1) } // 0 = Dimensions, 1 = Percentage (default: half size)
    var percentage by rememberSaveable { mutableIntStateOf(50) }
    var widthInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var lockAspect by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }
    var fit by rememberSaveable { mutableStateOf(ImageResizeFit.FILL) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val meta = ImageToolsEngine.inspectImage(context, uri)
            metadata = meta
            if (meta != null && widthInput.isEmpty()) {
                widthInput = meta.width.toString()
                heightInput = meta.height.toString()
            }
        }
    }

    val origW = metadata?.width ?: 1
    val origH = metadata?.height ?: 1
    val aspectRatio = origW.toFloat() / origH.toFloat()

    // Percent mode scales both sides by one factor, clamped together so the picture is never distorted.
    val percentScale = run {
        val wanted = percentage / 100f
        val maxScale = 8000f / maxOf(origW, origH)
        val minScale = 10f / minOf(origW, origH)
        wanted.coerceIn(minScale, maxOf(minScale, maxScale))
    }
    val w = if (resizeMode == 1) (origW * percentScale).roundToInt() else widthInput.toIntOrNull() ?: 0
    val h = if (resizeMode == 1) (origH * percentScale).roundToInt() else heightInput.toIntOrNull() ?: 0

    val unchanged = metadata != null && w == origW && h == origH
    val isValid = w in 10..8000 && h in 10..8000 && !unchanged

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(R.string.tools_resize_img_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_resize_img_action),
        onAction = {
            onConfirm(
                ImageResizeConfig(
                    targetWidth = w,
                    targetHeight = h,
                    outputFormat = format,
                    fit = if (resizeMode == 0) fit else ImageResizeFit.STRETCH
                )
            )
        },
        actionEnabled = isValid,
        actionSupport = if (unchanged) stringResource(R.string.tools_resize_img_unchanged) else null
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                metadata?.let { meta ->
                    Text(
                        "${meta.fileName} (${meta.width} × ${meta.height} px)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tools_crop_img_button))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = resizeMode == 0,
                        onClick = { resizeMode = 0 },
                        label = { Text(stringResource(R.string.tools_resize_img_mode_dimensions)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = resizeMode == 1,
                        onClick = {
                            resizeMode = 1
                            widthInput = ((origW * percentage / 100f).roundToInt()).toString()
                            heightInput = ((origH * percentage / 100f).roundToInt()).toString()
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_mode_percent)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (resizeMode == 1) {
                    Text(
                        "${percentage}% (${w} × ${h} px)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 50, 75, 150, 200).forEach { pct ->
                            FilterChip(
                                selected = percentage == pct,
                                onClick = { percentage = pct },
                                label = { Text("$pct%") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = percentage.toFloat(),
                        onValueChange = { percentage = it.roundToInt() },
                        valueRange = 10f..300f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        stringResource(R.string.tools_compress_img_quick_sizes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            600 to 600 to stringResource(R.string.tools_resize_img_preset_passport),
                            400 to 400 to stringResource(R.string.tools_resize_img_preset_avatar),
                            1080 to 1080 to stringResource(R.string.tools_resize_img_preset_instagram),
                            1080 to 1920 to stringResource(R.string.tools_resize_img_preset_story),
                            1920 to 1080 to stringResource(R.string.tools_resize_img_preset_fhd),
                            1280 to 720 to stringResource(R.string.tools_resize_img_preset_hd),
                            2480 to 3508 to stringResource(R.string.tools_resize_img_preset_a4)
                        )
                        presets.forEach { (dims, label) ->
                            val (pw, ph) = dims
                            FilterChip(
                                selected = w == pw && h == ph,
                                onClick = {
                                    lockAspect = false
                                    widthInput = pw.toString()
                                    heightInput = ph.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = widthInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    widthInput = str
                                    if (lockAspect && str.isNotEmpty()) {
                                        val newW = str.toIntOrNull()
                                        if (newW != null && newW > 0) {
                                            heightInput = (newW / aspectRatio).roundToInt().coerceIn(10, 8000).toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.tools_resize_img_width)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    heightInput = str
                                    if (lockAspect && str.isNotEmpty()) {
                                        val newH = str.toIntOrNull()
                                        if (newH != null && newH > 0) {
                                            widthInput = (newH * aspectRatio).roundToInt().coerceIn(10, 8000).toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.tools_resize_img_height)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lockAspect = !lockAspect },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = lockAspect,
                            onCheckedChange = { lockAspect = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.tools_resize_img_lock_aspect),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.tools_resize_fit_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ImageResizeFit.FIT to R.string.tools_resize_fit_fit,
                            ImageResizeFit.FILL to R.string.tools_resize_fit_fill,
                            ImageResizeFit.STRETCH to R.string.tools_resize_fit_stretch
                        ).forEach { (mode, label) ->
                            FilterChip(selected = fit == mode, onClick = { fit = mode }, label = { Text(stringResource(label), maxLines = 1) })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(R.string.tools_convert_img_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ImageExportFormat.JPEG,
                        onClick = { format = ImageExportFormat.JPEG },
                        label = { Text("JPG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.PNG,
                        onClick = { format = ImageExportFormat.PNG },
                        label = { Text("PNG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.WEBP,
                        onClick = { format = ImageExportFormat.WEBP },
                        label = { Text("WEBP") }
                    )
                }

                if (!isValid && (widthInput.isNotEmpty() || heightInput.isNotEmpty())) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tools_err_invalid_dimensions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
}
