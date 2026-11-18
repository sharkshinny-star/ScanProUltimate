package com.example.scanpro.feature.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfCropTools
import kotlin.math.roundToInt

/** Crop PDF pages (F172): auto-trim to the content with a little padding, or margins in millimetres. */
@Composable
internal fun CropPdfDialog(onDismiss: () -> Unit, onConfirm: (PdfCropTools.CropOptions) -> Unit) {
    var auto by rememberSaveable { mutableStateOf(true) }
    var padding by rememberSaveable { mutableFloatStateOf(3f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) }
    var bottom by rememberSaveable { mutableFloatStateOf(0f) }
    var left by rememberSaveable { mutableFloatStateOf(0f) }
    var right by rememberSaveable { mutableFloatStateOf(0f) }
    val options = if (auto) {
        PdfCropTools.CropOptions(PdfCropTools.Mode.AUTO, paddingMm = padding)
    } else {
        PdfCropTools.CropOptions(PdfCropTools.Mode.MARGINS, topMm = top, bottomMm = bottom, leftMm = left, rightMm = right)
    }
    val anyMargin = top + bottom + left + right > 0f
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Crop, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_card_crop_pdf)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tools_crop_pdf_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(selected = auto, onClick = { auto = true }, label = { Text(stringResource(R.string.tools_crop_pdf_mode_auto)) })
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    FilterChip(selected = !auto, onClick = { auto = false }, label = { Text(stringResource(R.string.tools_crop_pdf_mode_margins)) })
                }
                Spacer(Modifier.height(8.dp))
                if (auto) {
                    MillimetreSlider(stringResource(R.string.tools_crop_pdf_padding), padding, 0f..15f) { padding = it }
                } else {
                    MillimetreSlider(stringResource(R.string.tools_crop_pdf_top), top, 0f..60f) { top = it }
                    MillimetreSlider(stringResource(R.string.tools_crop_pdf_bottom), bottom, 0f..60f) { bottom = it }
                    MillimetreSlider(stringResource(R.string.tools_crop_pdf_left), left, 0f..60f) { left = it }
                    MillimetreSlider(stringResource(R.string.tools_crop_pdf_right), right, 0f..60f) { right = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options) }, enabled = auto || anyMargin) { Text(stringResource(R.string.tools_crop_pdf_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun MillimetreSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.tools_crop_pdf_mm, value.roundToInt()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = { onChange(it.roundToInt().toFloat()) }, valueRange = range)
    }
}

/** Redact text (F172): the words to black out, one per line or comma-separated, and whether case matters. */
@Composable
internal fun RedactDialog(onDismiss: () -> Unit, onConfirm: (terms: List<String>, matchCase: Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    val terms = text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_card_redact)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tools_redact_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.tools_redact_terms_label)) },
                    placeholder = { Text(stringResource(R.string.tools_redact_terms_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tools_redact_match_case), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = matchCase, onCheckedChange = { matchCase = it })
                }
                if (terms.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.tools_redact_terms_count, terms.size, terms.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(terms, matchCase) }, enabled = terms.isNotEmpty()) { Text(stringResource(R.string.tools_redact_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
