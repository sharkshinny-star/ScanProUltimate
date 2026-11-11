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
import com.example.scanpro.core.designsystem.FileLoadingAnimation
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
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
    var selected by rememberSaveable { mutableStateOf(-1) }
    var text by rememberSaveable { mutableStateOf("") }
    val blue = MaterialTheme.colorScheme.primary
    val pagePreview = stringResource(R.string.a11y_page_preview)

    // Edge to edge like the rest of the app: the editor draws behind the bars and pads its own content.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close)) }
                    Text(stringResource(R.string.doc_edit_text), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                }
                Text(
                    if (lines.isEmpty()) stringResource(R.string.doc_edit_text_empty)
                    else stringResource(R.string.doc_edit_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val image = bitmap
                    if (image == null) {
                        FileLoadingAnimation()
                    } else {
                        Canvas(
                            Modifier
                                .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                .semantics { contentDescription = pagePreview }
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
                    shape = MaterialTheme.shapes.small,
                    label = { Text(if (line == null) stringResource(R.string.doc_tap_a_line) else stringResource(R.string.doc_new_text)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        enabled = line != null,
                        onClick = { line?.let { onApply(TextEdit(it.left, it.top, it.right, it.bottom, "")) } }
                    ) { Text(stringResource(R.string.doc_remove_line)) }
                    Button(
                        enabled = line != null && text.trim() != line.text,
                        onClick = { line?.let { onApply(TextEdit(it.left, it.top, it.right, it.bottom, text.trim())) } }
                    ) { Text(stringResource(R.string.doc_replace)) }
                }
            }
        }
    }
}
