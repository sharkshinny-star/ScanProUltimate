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
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.ScanPage
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Low-resolution live preview; full-resolution processing is committed only on Apply. */
@Composable
fun AdjustmentsDialog(
    page: ScanPage,
    onDismiss: () -> Unit,
    onConfirm: (PageAdjustments) -> Unit
) {
    var brightness by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.brightness) }
    var contrast by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.contrast) }
    var saturation by remember(page.id, page.adjustments) { mutableFloatStateOf(page.adjustments.saturation) }
    val adjustments = PageAdjustments(brightness, contrast, saturation).normalized()

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
        delay(70)
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
        title = { Text("Fine tune page") },
        text = {
            Column {
                preview?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Adjustment preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxHeight = 300.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text("Preparing preview…", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(12.dp))
                AdjustmentSlider("Brightness", brightness, -1f..1f) { brightness = it }
                AdjustmentSlider("Contrast", contrast, 0.6f..1.6f) { contrast = it }
                AdjustmentSlider("Saturation", saturation, 0f..2f) { saturation = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(adjustments) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = {
                brightness = 0f
                contrast = 1f
                saturation = 1f
            }) { Text("Reset") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val display = when (label) {
        "Brightness" -> "${(value * 100).roundToInt()}"
        else -> "${(value * 100).roundToInt()}%"
    }
    Text("$label  $display", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}
