package com.example.scanpro.feature.document

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Choose where a two-page spread is cut: drag the line, or use the slider for a precise, non-drag alternative. */
@Composable
fun BookSplitDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(imagePath, 1400) }
    }
    var fraction by remember { mutableFloatStateOf(0.5f) }
    val pagePreview = stringResource(R.string.a11y_page_preview)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_split_book_spread)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.doc_split_book_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                bitmap?.let { image ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .semantics { contentDescription = pagePreview }
                            // The line follows the finger across the picture itself, so no RTL flip here; the slider is the non-drag way.
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, amount ->
                                    change.consume()
                                    fraction = (fraction + amount / size.width).coerceIn(0.1f, 0.9f)
                                }
                            }
                    ) {
                        drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                        val x = size.width * fraction
                        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 7.dp.toPx())
                        drawLine(Color(0xFF2FD6A6), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                    }
                } ?: Text(stringResource(R.string.doc_loading_page), modifier = Modifier.padding(top = 16.dp))
                Slider(
                    value = fraction,
                    onValueChange = { fraction = it },
                    valueRange = 0.1f..0.9f,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(enabled = bitmap != null, onClick = { onConfirm(fraction) }) { Text(stringResource(R.string.doc_split)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
