package com.example.scanpro.feature.tools

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.displayedKiloByte
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.CompressionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Below this a PDF is too small for the compression presets to promise anything. */
private const val SMALL_PDF_BYTES = 150_000L

/** Where Compress PDF keeps the level chosen last time, in the Tools preferences. */
private const val LAST_COMPRESS_PRESET = "last_compress_preset"

@Composable
internal fun CompressPdfDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (preset: CompressionPreset, customTargetBytes: Long?) -> Unit
) {
    var originalSize by remember { mutableLongStateOf(0L) }
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

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            originalSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        }
    }

    val customBytes: Long? = remember(targetInput, targetUnit) {
        val number = targetInput.trim().toDoubleOrNull()
        if (number != null && number > 0.0) {
            // Same unit as the sizes the dialog shows (decimal kB/MB on Android 8+), or "170 KB" would be
            // rejected against a file shown as 171 kB.
            val unit = displayedKiloByte()
            if (targetUnit == "MB") (number * unit * unit).toLong() else (number * unit).toLong()
        } else null
    }

    val isTargetTooLarge = isCustomTarget && customBytes != null && originalSize > 0 && customBytes >= originalSize
    val isTargetZero = isCustomTarget && targetInput.isNotBlank() && (customBytes == null || customBytes <= 0)

    val canConfirm = !isCustomTarget || customBytes != null && customBytes > 0 && (originalSize <= 0 || customBytes < originalSize)
    // A full tool page (F146): the dialog crammed seven controls into a pop-up and hid its button on small phones.
    ToolPage(
        title = stringResource(R.string.tools_compress_pdf),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_compress_pdf),
        onAction = {
            if (!isCustomTarget) toolPrefs.edit().putString(LAST_COMPRESS_PRESET, preset.name).apply()
            onConfirm(preset, if (isCustomTarget) customBytes else null)
        },
        actionEnabled = canConfirm,
        actionIcon = Icons.Default.Compress
    ) {
            Column {
                PickedFileRow(uri)
                Spacer(modifier = Modifier.height(12.dp))

                CompressionPreset.entries.forEach { item ->
                    val isSelected = !isCustomTarget && preset == item
                    val estFraction = when (item) {
                        CompressionPreset.HIGH -> 0.65f
                        CompressionPreset.BALANCED -> 0.40f
                        CompressionPreset.MAXIMUM -> 0.20f
                    }
                    val estBytes = (originalSize * estFraction).toLong()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = isSelected, role = Role.RadioButton) {
                                isCustomTarget = false
                                preset = item
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(item.labelRes),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(item.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // The guess is a fixed share of the size, which only holds for PDFs made of photos. For a small file the
                        // note below already says it may not shrink, so no "~196 B" promise next to it.
                        if (originalSize >= SMALL_PDF_BYTES) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = stringResource(R.string.tools_compress_estimated_tag, formatBytes(LocalContext.current, estBytes)),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // 4th option: Custom Target Size
                val isCustomSelected = isCustomTarget
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = isCustomSelected, role = Role.RadioButton) { isCustomTarget = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    RadioButton(selected = isCustomSelected, onClick = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.tools_compress_preset_custom),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.tools_compress_preset_custom_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCustomSelected && customBytes != null && customBytes > 0 && !isTargetTooLarge) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = stringResource(R.string.tools_compress_estimated_tag, formatBytes(LocalContext.current, customBytes)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (isCustomTarget) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { text ->
                                if (text.isEmpty() || text.matches(Regex("""^\d*\.?\d*$"""))) {
                                    targetInput = text
                                }
                            },
                            label = { Text(stringResource(R.string.tools_compress_custom_target_hint)) },
                            placeholder = { Text(if (targetUnit == "KB") "e.g. 500" else "e.g. 0.5") },
                            singleLine = true,
                            isError = isTargetTooLarge || isTargetZero,
                            supportingText = {
                                if (isTargetTooLarge) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_larger, formatBytes(LocalContext.current, originalSize)),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isTargetZero) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_positive),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (customBytes != null && customBytes > 0 && originalSize > 0) {
                                    val saved = originalSize - customBytes
                                    val pct = ((saved.toDouble() / originalSize.toDouble()) * 100).toInt().coerceIn(1, 95)
                                    Text(stringResource(R.string.tools_compress_target_valid_hint, pct))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = targetUnit == "KB",
                                onClick = { targetUnit = "KB" },
                                label = { Text(stringResource(R.string.tools_compress_unit_kb)) }
                            )
                            FilterChip(
                                selected = targetUnit == "MB",
                                onClick = { targetUnit = "MB" },
                                label = { Text(stringResource(R.string.tools_compress_unit_mb)) }
                            )
                        }
                    }
                }

                if (originalSize > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isTargetTooLarge) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.tools_compress_warning_larger_than_original,
                                        formatBytes(LocalContext.current, originalSize)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (!isCustomTarget && originalSize in 1 until SMALL_PDF_BYTES) {
                        // Under about 150 kB there is rarely anything to squeeze, and a made-up percentage would mislead.
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.tools_compress_small_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    } else if (!isCustomTarget || (customBytes != null && customBytes > 0)) {
                        val (summaryBytes, summaryPercent) = if (isCustomTarget) {
                            val bytes = customBytes ?: (originalSize * 0.40f).toLong()
                            val pct = (((originalSize - bytes).toDouble() / originalSize.toDouble()) * 100).toInt().coerceIn(1, 95)
                            bytes to pct
                        } else {
                            val fraction = when (preset) {
                                CompressionPreset.HIGH -> 0.65f
                                CompressionPreset.BALANCED -> 0.40f
                                CompressionPreset.MAXIMUM -> 0.20f
                            }
                            val pct = when (preset) {
                                CompressionPreset.HIGH -> 35
                                CompressionPreset.BALANCED -> 60
                                CompressionPreset.MAXIMUM -> 80
                            }
                            (originalSize * fraction).toLong() to pct
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Compress,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.tools_compress_expected_summary,
                                        formatBytes(LocalContext.current, summaryBytes),
                                        formatBytes(LocalContext.current, originalSize),
                                        summaryPercent
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tools_compress_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
