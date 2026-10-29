package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.ScanPage
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Low-resolution live preview; full-resolution processing is committed only on Apply. */
@Composable
fun AdjustmentsDialog(
    page: ScanPage,
    pageCount: Int = 1,
    onDismiss: () -> Unit,
    /** The new settings, and whether the shadow and finger clean-up should go to every page. */
    onConfirm: (PageAdjustments, Boolean) -> Unit
) {
    var allPages by remember(page.id) { mutableStateOf(false) }
    var brightness by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.brightness) }
    var contrast by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.contrast) }
    var saturation by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.saturation) }
    var removeShadows by remember(page.id, page.adjustments) { mutableStateOf(page.adjustments.removeShadows) }
    var removeFingers by remember(page.id, page.adjustments) { mutableStateOf(page.adjustments.removeFingers) }
    val adjustments = PageAdjustments(brightness, contrast, saturation, removeShadows, removeFingers).normalized()

    val preview by produceState<Bitmap?>(
        initialValue = null,
        page.id,
        page.filter,
        page.rotationDegrees,
        page.crop,
        adjustments,
        page.annotations
    ) {
        // Avoid spawning expensive preview work for every tiny slider movement.
        delay(70.milliseconds)
        value = withContext(Dispatchers.Default) {
            runCatching {
                ImageProcessor.renderPreview(
                    source = File(page.sourcePath),
                    filter = page.filter,
                    manualRotationDegrees = page.rotationDegrees,
                    crop = page.crop,
                    adjustments = adjustments,
                    annotations = page.annotations,
                    maxDimension = 900
                )
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_adjustments_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                preview?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.doc_adjustment_preview),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxHeight = 300.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text(stringResource(R.string.doc_preparing_preview), style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(8.dp))
                CleanupSwitch(stringResource(R.string.doc_remove_shadows), stringResource(R.string.doc_remove_shadows_hint), removeShadows) { removeShadows = it }
                CleanupSwitch(stringResource(R.string.doc_remove_fingers), stringResource(R.string.doc_remove_fingers_hint), removeFingers) { removeFingers = it }
                if (pageCount > 1) {
                    CleanupSwitch(stringResource(R.string.doc_clean_up_all_pages, pageCount), stringResource(R.string.doc_clean_up_all_pages_hint), allPages) { allPages = it }
                }
                AdjustmentSlider(stringResource(R.string.doc_brightness), false, brightness, -1f..1f) { brightness = it }
                AdjustmentSlider(stringResource(R.string.doc_contrast), true, contrast, 0.6f..1.6f) { contrast = it }
                AdjustmentSlider(stringResource(R.string.doc_saturation), true, saturation, 0f..2f) { saturation = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(adjustments, allPages) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = {
            TextButton(onClick = {
                brightness = 0f
                contrast = 1f
                saturation = 1f
                removeShadows = false
                removeFingers = false
            }) { Text(stringResource(R.string.common_reset)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun CleanupSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    /** Show the value as a percentage; brightness is a plain offset. */
    percent: Boolean,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val display = if (percent) stringResource(R.string.doc_percent_value, (value * 100).roundToInt()) else "${(value * 100).roundToInt()}"
    Text(stringResource(R.string.doc_slider_label, label, display), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}
