package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.data.OcrWord
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.intelligence.OcrLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A replacement for one line of printed words: its box on the page (fractions) and the new text. */
data class TextEdit(val left: Float, val top: Float, val right: Float, val bottom: Float, val text: String)

/**
 * Edit the printed text of a page: tap a line, retype it, and the old words are painted over in the paper's
 * colour and the new ones written in their place. Works from the words OCR found and where it found them.
 */
@Composable
fun EditTextDialog(
    imagePath: String,
    words: List<OcrWord>,
    onDismiss: () -> Unit,
    onApply: (TextEdit) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(imagePath, 1600) }
    }
    val lines = remember(words) { OcrLayout.lines(words) }
    var selected by remember { mutableStateOf(-1) }
    var text by remember { mutableStateOf("") }
    val blue = MaterialTheme.colorScheme.primary

    // Edge to edge like the rest of the app: the editor draws behind the bars and pads its own content.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                    Text("Edit text", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    if (lines.isEmpty()) "No text was found on this page, so there is nothing to edit."
                    else "Tap the line you want to change, then type the new words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image == null) {
                        CircularProgressIndicator()
                    } else {
                        Canvas(
                            Modifier
                                .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                .pointerInput(lines) {
                                    detectTapGestures { tap ->
                                        val x = tap.x / size.width
                                        val y = tap.y / size.height
                                        // The line under the finger, else the nearest one (lines are small on a phone).
                                        val hit = lines.indices.minByOrNull { i ->
                                            val line = lines[i]
                                            val dx = maxOf(line.left - x, 0f, x - line.right)
                                            val dy = maxOf(line.top - y, 0f, y - line.bottom) * 2f
                                            dx + dy
                                        }
                                        if (hit != null) {
                                            selected = hit
                                            text = lines[hit].text
                                        }
                                    }
                                }
                        ) {
                            drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            lines.forEachIndexed { i, line ->
                                val topLeft = Offset(line.left * size.width, line.top * size.height)
                                val box = Size((line.right - line.left) * size.width, (line.bottom - line.top) * size.height)
                                if (i == selected) {
                                    drawRect(blue.copy(alpha = 0.18f), topLeft, box)
                                    drawRect(blue, topLeft, box, style = Stroke(2.dp.toPx()))
                                } else {
                                    drawRect(blue.copy(alpha = 0.35f), topLeft, box, style = Stroke(1.dp.toPx()))
                                }
                            }
                        }
                    }
                }
                val line = lines.getOrNull(selected)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace('\n', ' ').take(400) },
                    enabled = line != null,
                    singleLine = true,
                    label = { Text(if (line == null) "Tap a line on the page" else "New text") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        enabled = line != null,
                        onClick = { line?.let { onApply(TextEdit(it.left, it.top, it.right, it.bottom, "")) } }
                    ) { Text("Remove line") }
                    Button(
                        enabled = line != null && text.trim() != line.text,
                        onClick = { line?.let { onApply(TextEdit(it.left, it.top, it.right, it.bottom, text.trim())) } }
                    ) { Text("Replace") }
                }
            }
        }
    }
}
