package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.example.scanpro.core.designsystem.AccentButton
import com.example.scanpro.core.designsystem.DarkChrome
import com.example.scanpro.core.designsystem.DarkOutlinedButton
import com.example.scanpro.core.designsystem.DarkTextButton
import com.example.scanpro.core.designsystem.GlassIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.JsonCodec
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import androidx.compose.runtime.mutableIntStateOf

private val Blue = Color(0xFF2FD6A6)
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
    // Saved across rotation and theme changes, so the dialog never reopens with its edits silently reset.
    val items = rememberSaveable(saver = AnnotationListSaver) { mutableStateListOf<PageAnnotation>().apply { addAll(initial) } }
    var selectedId by rememberSaveable { mutableStateOf(initial.lastOrNull()?.id) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Back, the arrow and Cancel all ask first when something was changed.
    val close = { if (items.toList() != initial) confirmDiscard = true else onDismiss() }
    val base by produceState<Bitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeScaled(loadBase().absolutePath, 1600) }.getOrNull() }
    }
    val selectedIndex = items.indexOfFirst { it.id == selectedId }
    val pagePreview = stringResource(R.string.a11y_page_preview)

    fun update(index: Int, change: (PageAnnotation) -> PageAnnotation) {
        if (index !in items.indices) return
        val next = change(items[index])
        val w = next.width.coerceIn(MIN_SIZE, 1f)
        val h = next.height.coerceIn(MIN_SIZE, 1f)
        items[index] = next.copy(width = w, height = h, x = next.x.coerceIn(0f, 1f - w), y = next.y.coerceIn(0f, 1f - h))
    }

    // Edge to edge like the rest of the app: the editor draws behind the bars and pads its own content.
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = DarkChrome.backdrop) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_close), onClick = close)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.doc_edit_markup), color = DarkChrome.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.doc_markup_hint),
                            color = DarkChrome.mutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val image = base
                    if (image == null) {
                        androidx.compose.material3.CircularProgressIndicator(color = Blue)
                    } else {
                        val latestItems by rememberUpdatedState(items.toList())
                        val latestSelected by rememberUpdatedState(selectedId)
                        var mode by remember { mutableIntStateOf(0) } // 0 none, 1 move, 2 resize
                        Canvas(
                            Modifier
                                .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                .semantics { contentDescription = pagePreview }
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
                                    AnnotationType.INK -> Color(0x221A49C9)
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
                // The arrows nudge the box on the page itself (physical directions), so they are not mirrored in RTL;
                // the glyphs say nothing to a screen reader, hence the spoken labels.
                val moveLeft = stringResource(R.string.doc_move_left)
                val moveRight = stringResource(R.string.doc_move_right)
                val moveUp = stringResource(R.string.a11y_move_up)
                val moveDown = stringResource(R.string.a11y_move_down)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DarkOutlinedButton("←", enabled = enabled, onClick = { update(selectedIndex) { it.copy(x = it.x - step) } }, modifier = Modifier.semantics { contentDescription = moveLeft })
                    DarkOutlinedButton("→", enabled = enabled, onClick = { update(selectedIndex) { it.copy(x = it.x + step) } }, modifier = Modifier.semantics { contentDescription = moveRight })
                    DarkOutlinedButton("↑", enabled = enabled, onClick = { update(selectedIndex) { it.copy(y = it.y - step) } }, modifier = Modifier.semantics { contentDescription = moveUp })
                    DarkOutlinedButton("↓", enabled = enabled, onClick = { update(selectedIndex) { it.copy(y = it.y + step) } }, modifier = Modifier.semantics { contentDescription = moveDown })
                    DarkOutlinedButton(stringResource(R.string.doc_smaller), enabled = enabled, onClick = { update(selectedIndex) { it.copy(width = it.width * 0.92f, height = it.height * 0.92f) } })
                    DarkOutlinedButton(stringResource(R.string.doc_larger), enabled = enabled, onClick = { update(selectedIndex) { it.copy(width = it.width * 1.08f, height = it.height * 1.08f) } })
                    DarkOutlinedButton(stringResource(R.string.common_delete), enabled = enabled, onClick = { items.removeAt(selectedIndex); selectedId = items.lastOrNull()?.id })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    DarkTextButton(stringResource(R.string.common_cancel), onClick = close)
                    AccentButton(stringResource(R.string.common_apply), onClick = { onApply(items.toList()) })
                }
            }
        }
    }
    if (confirmDiscard) {
        DiscardEditsDialog(onKeepEditing = { confirmDiscard = false }, onDiscard = { confirmDiscard = false; onDismiss() })
    }
}

/** Keeps the editor's boxes through a recreation as JSON, the same form they are stored in. */
private val AnnotationListSaver = Saver<SnapshotStateList<PageAnnotation>, String>(
    save = { JsonCodec.encodeAnnotations(it.toList()) },
    restore = { raw -> mutableStateListOf<PageAnnotation>().apply { addAll(JsonCodec.decodeAnnotations(raw)) } }
)

private fun hit(item: PageAnnotation, point: Offset, width: Float, height: Float): Boolean =
    point.x in item.x * width..(item.x + item.width) * width && point.y in item.y * height..(item.y + item.height) * height

private fun nearHandle(item: PageAnnotation, point: Offset, width: Float, height: Float, reach: Float): Boolean =
    hypot(point.x - (item.x + item.width) * width, point.y - (item.y + item.height) * height) <= reach
