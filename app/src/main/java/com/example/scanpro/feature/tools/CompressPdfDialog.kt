package com.example.scanpro.feature.tools

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.displayedKiloByte
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.CompressPdfEngine
import com.example.scanpro.imaging.CompressionPreset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Where Compress PDF keeps the level chosen last time, in the Tools preferences. */
private const val LAST_COMPRESS_PRESET = "last_compress_preset"

/**
 * Compress PDF (rebuilt, F210). Before anything runs the PDF is sampled ([CompressPdfEngine.estimate]): its biggest
 * pictures re-encoded and a page or two rendered at each level, so every level shows the size it is likely to give,
 * a text PDF says it will barely change instead of promising "60% smaller", and a scan shows its first page as the
 * chosen level will leave it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompressPdfDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (preset: CompressionPreset, customTargetBytes: Long?) -> Unit
) {
    val facts by rememberPdfFacts(uri)
    // Starts on the level chosen last time (iLovePDF remembers it too): most people compress the same way every time.
    val toolPrefs = remember(context) { context.getSharedPreferences("tools_ui", Context.MODE_PRIVATE) }
    var preset by rememberSaveable {
        mutableStateOf(
            CompressionPreset.entries.firstOrNull { it.name == toolPrefs.getString(LAST_COMPRESS_PRESET, null) } ?: CompressionPreset.BALANCED
        )
    }
    var isCustomTarget by rememberSaveable { mutableStateOf(false) }
    var targetInput by rememberSaveable { mutableStateOf("") }
    var targetUnit by rememberSaveable { mutableStateOf("KB") }

    // Worked out once per file, off the main thread (the engine moves to its own dispatcher).
    var estimateError by remember(uri) { mutableStateOf<Throwable?>(null) }
    val estimate by produceState<CompressPdfEngine.CompressEstimate?>(null, uri) {
        value = try {
            CompressPdfEngine.estimate(context, uri)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            estimateError = error
            null
        }
    }
    val est = estimate
    val failure = estimateError
    // A file that needs a password cannot be compressed: say so rather than fail at the end.
    val locked = failure is com.example.scanpro.core.text.UserFacingException
    val originalSize = facts?.sizeBytes?.takeIf { it > 0 } ?: est?.originalBytes ?: 0L

    val customBytes: Long? = remember(targetInput, targetUnit) {
        val number = targetInput.trim().toDoubleOrNull()
        if (number != null && number > 0.0) {
            // Same unit as the sizes the page shows (decimal kB/MB on Android 8+), or "170 KB" would be
            // rejected against a file shown as 171 kB.
            val unit = displayedKiloByte()
            if (targetUnit == "MB") (number * unit * unit).toLong() else (number * unit).toLong()
        } else null
    }

    val isTargetTooLarge = isCustomTarget && customBytes != null && originalSize > 0 && customBytes >= originalSize
    val isTargetMissing = isCustomTarget && (customBytes == null || customBytes <= 0)
    val presetBytes = est?.bytes?.get(preset)
    // What the chosen level is expected to give: the preset's estimate, or the target (the engine aims just under it),
    // or, when even the strongest setting stays above the target, the smallest it is likely to reach.
    val expected: Long? = when {
        est == null -> null
        !isCustomTarget -> presetBytes
        customBytes == null || isTargetTooLarge -> null
        else -> maxOf(customBytes, est.smallestBytes).coerceAtMost(est.originalBytes)
    }
    val noGain = !isCustomTarget && est != null && presetBytes != null && presetBytes >= est.originalBytes * 0.98
    val unreachable = isCustomTarget && est != null && customBytes != null && !isTargetTooLarge && est.smallestBytes > customBytes
    val textMayGo = isCustomTarget && est != null && est.hasText && customBytes != null && !isTargetTooLarge &&
        customBytes < (est.bytes[CompressionPreset.MAXIMUM] ?: est.originalBytes)

    val problem = when {
        locked -> failure?.message
        isTargetTooLarge -> stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, originalSize))
        isCustomTarget && targetInput.isNotBlank() && isTargetMissing -> stringResource(R.string.tools_compress_err_target_positive)
        isTargetMissing -> stringResource(R.string.tools_compress_custom_target_hint)
        else -> null
    }
    val previewBitmap = if (!isCustomTarget) est?.firstPage?.get(preset) else null
    val previewImage = remember(previewBitmap) { previewBitmap?.asImageBitmap() }

    ToolPage(
        title = stringResource(R.string.tools_compress_pdf),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_compress_pdf),
        onAction = {
            if (!isCustomTarget) toolPrefs.edit().putString(LAST_COMPRESS_PRESET, preset.name).apply()
            onConfirm(preset, if (isCustomTarget) customBytes else null)
        },
        actionEnabled = problem == null,
        actionIcon = Icons.Default.Compress,
        actionSupport = problem ?: when {
            est == null && failure == null -> stringResource(R.string.pages_compress_estimating)
            expected != null && originalSize > 0 && expected < originalSize ->
                stringResource(R.string.tools_compress_expected_summary, formatBytes(context, expected), formatBytes(context, originalSize), ((originalSize - expected) * 100 / originalSize).toInt().coerceIn(1, 99))
            else -> null
        },
        demoToolId = "compress"
    ) {
        val image = previewImage
        // A scan's first page as the chosen level leaves it; a PDF with text keeps its pages as they are.
        val drawResult: (androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Rect) -> Unit)? =
            if (image == null) null else { page ->
                drawImage(
                    image,
                    dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
                    dstSize = IntSize(page.width.roundToInt().coerceAtLeast(1), page.height.roundToInt().coerceAtLeast(1))
                )
            }
        PdfResultPreview(facts = facts, result = drawResult, height = 260.dp)
        val loaded = facts
        if (loaded != null) {
            PdfOutcomeRow(loaded, if (est == null || expected == null) null else PdfOutcome(pages = loaded.pageCount ?: est.pageCount, bytes = expected))
        }
        when {
            locked -> Unit
            failure != null -> ImageToolNotice(stringResource(R.string.pages_compress_estimate_failed))
            est == null -> Unit
            !isCustomTarget && est.hasText && est.imageBytes < est.originalBytes / 10 -> ImageToolNotice(stringResource(R.string.pages_compress_text_note))
            noGain -> ImageToolNotice(stringResource(R.string.pages_compress_no_gain))
            !isCustomTarget && !est.hasText -> ToolHint(stringResource(R.string.pages_compress_picture_note))
        }

        ToolSection(stringResource(R.string.pages_compress_level)) {
            ChoiceGroup {
                CompressionPreset.entries.forEach { item ->
                    val bytes = est?.bytes?.get(item)
                    ChoiceCard(
                        selected = !isCustomTarget && preset == item,
                        title = stringResource(item.labelRes),
                        subtitle = stringResource(item.descriptionRes),
                        onClick = { isCustomTarget = false; preset = item },
                        trailing = {
                            EstimateTag(
                                text = when {
                                    bytes == null -> "…"
                                    est != null && bytes >= est.originalBytes * 0.98 -> stringResource(R.string.pages_compress_no_change)
                                    else -> stringResource(R.string.tools_compress_estimated_tag, formatBytes(context, bytes))
                                },
                                selected = !isCustomTarget && preset == item
                            )
                        }
                    )
                }
                ChoiceCard(
                    selected = isCustomTarget,
                    title = stringResource(R.string.tools_compress_preset_custom),
                    subtitle = stringResource(R.string.tools_compress_preset_custom_desc),
                    onClick = { isCustomTarget = true }
                )
            }
            if (isCustomTarget) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { text ->
                            if (text.length <= 8 && (text.isEmpty() || text.matches(Regex("""^\d*\.?\d*$""")))) targetInput = text
                        },
                        label = { Text(stringResource(R.string.tools_compress_custom_target_hint)) },
                        singleLine = true,
                        isError = isTargetTooLarge || (targetInput.isNotBlank() && isTargetMissing),
                        supportingText = {
                            when {
                                isTargetTooLarge -> Text(stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, originalSize)))
                                targetInput.isNotBlank() && isTargetMissing -> Text(stringResource(R.string.tools_compress_err_target_positive))
                                customBytes != null && originalSize > 0 -> Text(
                                    stringResource(R.string.tools_compress_target_valid_hint, ((originalSize - customBytes) * 100 / originalSize).toInt().coerceIn(1, 99))
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedChoice(
                        options = listOf("KB" to stringResource(R.string.tools_compress_unit_kb), "MB" to stringResource(R.string.tools_compress_unit_mb)),
                        selected = targetUnit,
                        onSelect = { targetUnit = it },
                        modifier = Modifier.width(132.dp).padding(top = 8.dp)
                    )
                }
                // Sizes a person can pick without typing: the levels' own estimates, below the file's size.
                val suggestions = listOf(100, 200, 500, 1000, 2000, 5000)
                    .map { (it * displayedKiloByte()).toLong() }
                    .filter { originalSize <= 0 || it < originalSize * 0.9 }
                    .filter { est == null || it >= est.smallestBytes }
                    .takeLast(4)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { bytes ->
                            val kb = (bytes / displayedKiloByte()).roundToInt()
                            FilterChip(
                                selected = customBytes == bytes,
                                onClick = {
                                    if (kb >= 1000 && kb % 1000 == 0) { targetUnit = "MB"; targetInput = (kb / 1000).toString() }
                                    else { targetUnit = "KB"; targetInput = kb.toString() }
                                },
                                label = { Text(formatBytes(context, bytes)) }
                            )
                        }
                    }
                }
                if (unreachable && est != null) ImageToolNotice(stringResource(R.string.pages_compress_target_unreachable, formatBytes(context, est.smallestBytes)))
                if (textMayGo) ImageToolNotice(stringResource(R.string.pages_compress_target_text_warning))
            }
        }
        ToolHint(stringResource(R.string.tools_compress_note))
    }
}

/** The expected size on a level's card. */
@Composable
private fun EstimateTag(text: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

@get:StringRes
private val CompressionPreset.labelRes: Int
    get() = when (this) {
        CompressionPreset.HIGH -> R.string.tools_compress_preset_high
        CompressionPreset.BALANCED -> R.string.tools_compress_preset_balanced
        CompressionPreset.MAXIMUM -> R.string.tools_compress_preset_maximum
    }

@get:StringRes
private val CompressionPreset.descriptionRes: Int
    get() = when (this) {
        CompressionPreset.HIGH -> R.string.tools_compress_preset_high_desc
        CompressionPreset.BALANCED -> R.string.tools_compress_preset_balanced_desc
        CompressionPreset.MAXIMUM -> R.string.tools_compress_preset_maximum_desc
    }
