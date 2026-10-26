package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

private val Blue = Color(0xFF2F7BFF)
private const val MIN_SIZE = 0.03f

/**
 * Move, resize and delete the markup already on a page. Drag a box to move it, drag the round handle on the
 * selected box to resize it, or use the buttons for precise, non-drag changes.
 */
@Composable
fun MarkupEditorDialog(
    initial: List<PageAnnotation>,
    loadBase: suspend () -> File,
    onDismiss: () -> Unit,
    onApply: (List<PageAnnotation>) -> Unit
) {
    val items = remember { mutableStateListOf<PageAnnotation>().apply { addAll(initial) } }
    var selectedId by remember { mutableStateOf<String?>(initial.lastOrNull()?.id) }
    val base by produceState<Bitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeScaled(loadBase().absolutePath, 1600) }.getOrNull() }
    }
    val selectedIndex = items.indexOfFirst { it.id == selectedId }

    fun update(index: Int, change: (PageAnnotation) -> PageAnnotation) {
        if (index !in items.indices) return
        val next = change(items[index])
        val w = next.width.coerceIn(MIN_SIZE, 1f)
        val h = next.height.coerceIn(MIN_SIZE, 1f)
        items[index] = next.copy(width = w, height = h, x = next.x.coerceIn(0f, 1f - w), y = next.y.coerceIn(0f, 1f - h))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0F18)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Text("Edit markup", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Drag a box to move it. Drag the round handle to resize. Tap a box to select it.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val image = base
                    if (image == null) {
                        Text("Loading page…", color = Color.White)
                    } else {
                        val latestItems by rememberUpdatedState(items.toList())
                        val latestSelected by rememberUpdatedState(selectedId)
                        var mode by remember { mutableStateOf(0) } // 0 none, 1 move, 2 resize
                        Canvas(
                            Modifier
                                .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                .pointerInput(Unit) {
                                    detectTapGestures { tap ->
                                        selectedId = latestItems.lastOrNull { hit(it, tap, size.width.toFloat(), size.height.toFloat()) }?.id
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            val selected = latestItems.firstOrNull { it.id == latestSelected }
                                            mode = when {
                                                selected != null && nearHandle(selected, start, w, h, 28.dp.toPx()) -> 2
                                                else -> {
                                                    val target = latestItems.lastOrNull { hit(it, start, w, h) }
                                                    if (target != null) { selectedId = target.id; 1 } else 0
                                                }
                                            }
                                        },
                                        onDragEnd = { mode = 0 },
                                        onDragCancel = { mode = 0 },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val index = items.indexOfFirst { it.id == selectedId }
                                            val dx = drag.x / size.width
                                            val dy = drag.y / size.height
                                            when (mode) {
                                                1 -> update(index) { it.copy(x = it.x + dx, y = it.y + dy) }
                                                2 -> update(index) { it.copy(width = it.width + dx, height = it.height + dy) }
                                            }
                                        }
                                    )
                                }
                        ) {
                            drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            items.forEach { item ->
                                val topLeft = Offset(item.x * size.width, item.y * size.height)
                                val boxSize = Size(item.width * size.width, item.height * size.height)
                                val fill = when (item.type) {
                                    AnnotationType.REDACT -> Color.Black
                                    AnnotationType.HIGHLIGHT -> Color(0x88FFEB3B)
                                    AnnotationType.TEXT -> Color(0x332F7BFF)
                                    AnnotationType.ERASE -> Color(0x33FF5252)
                                    else -> Color(0x3300A86B)
                                }
                                drawRect(fill, topLeft, boxSize)
                                val selected = item.id == selectedId
                                drawRect(
                                    if (selected) Blue else Color.White.copy(alpha = 0.8f),
                                    topLeft, boxSize,
                                    style = Stroke(width = if (selected) 3.dp.toPx() else 1.dp.toPx())
                                )
                                if (selected) {
                                    drawCircle(Blue, 11.dp.toPx(), Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height))
                                }
                            }
                        }
                    }
                }

                val enabled = selectedIndex >= 0
                val step = 0.01f
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(x = it.x - step) } }) { Text("←") }
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(x = it.x + step) } }) { Text("→") }
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(y = it.y - step) } }) { Text("↑") }
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(y = it.y + step) } }) { Text("↓") }
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(width = it.width * 0.92f, height = it.height * 0.92f) } }) { Text("Smaller") }
                    OutlinedButton(enabled = enabled, onClick = { update(selectedIndex) { it.copy(width = it.width * 1.08f, height = it.height * 1.08f) } }) { Text("Larger") }
                    OutlinedButton(enabled = enabled, onClick = { items.removeAt(selectedIndex); selectedId = items.lastOrNull()?.id }) { Text("Delete") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onApply(items.toList()) }) { Text("Apply") }
                }
            }
        }
    }
}

private fun hit(item: PageAnnotation, point: Offset, width: Float, height: Float): Boolean =
    point.x in item.x * width..(item.x + item.width) * width && point.y in item.y * height..(item.y + item.height) * height

private fun nearHandle(item: PageAnnotation, point: Offset, width: Float, height: Float, reach: Float): Boolean =
    hypot(point.x - (item.x + item.width) * width, point.y - (item.y + item.height) * height) <= reach
