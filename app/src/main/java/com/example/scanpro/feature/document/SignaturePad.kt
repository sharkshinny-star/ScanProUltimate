package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val InkColors = listOf("Black" to Color(0xFF111111), "Blue" to Color(0xFF1A3FB8))

/**
 * Draw a signature, or tap one saved earlier. New signatures are kept for next time unless unticked;
 * long-press a saved one to delete it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignaturePadDialog(
    saved: List<File> = emptyList(),
    onUseSaved: (File) -> Unit = {},
    onDeleteSaved: (File) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (signature: Bitmap, keep: Boolean) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(InkColors.first().second) }
    var keep by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add signature") },
        text = {
            Column {
                if (saved.isNotEmpty()) {
                    Text("Saved signatures", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    ) {
                        items(saved, key = { it.absolutePath }) { file ->
                            SavedSignature(file, onClick = { onUseSaved(file) }, onLongClick = { deleting = file })
                        }
                    }
                }
                Text(
                    if (saved.isEmpty()) "Sign below. The signature will be flattened onto the current page."
                    else "Or draw a new one below.",
                    style = MaterialTheme.typography.bodySmall
                )
                Canvas(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    strokes = strokes + listOf(listOf(start))
                                },
                                onDrag = { change, _ ->
                                    if (strokes.isNotEmpty()) {
                                        val updated = strokes.toMutableList()
                                        updated[updated.lastIndex] = updated.last() + change.position
                                        strokes = updated
                                    }
                                }
                            )
                        }
                ) {
                    strokes.forEach { stroke ->
                        for (i in 1 until stroke.size) {
                            drawLine(
                                color = ink,
                                start = stroke[i - 1],
                                end = stroke[i],
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InkColors.forEach { (name, color) ->
                        val chosen = ink == color
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(28.dp)
                                .background(color, CircleShape)
                                .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { ink = color }
                                .semantics { contentDescription = "$name ink"; selected = chosen }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.toggleable(value = keep, role = Role.Checkbox, onValueChange = { keep = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keep, onCheckedChange = null)
                        Text("Save for next time", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.any { it.size > 1 } && padSize.width > 0,
                onClick = {
                    onConfirm(signatureBitmap(strokes, padSize, ink.toArgb()), keep)
                }
            ) { Text("Place signature") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (strokes.isEmpty()) onDismiss() else strokes = emptyList()
            }) {
                Text(if (strokes.isEmpty()) "Cancel" else "Clear")
            }
        }
    )

    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved signature?") },
            text = { Text("Pages already signed keep their signature.") },
            confirmButton = { TextButton(onClick = { onDeleteSaved(file); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedSignature(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val image by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeScaled(file.absolutePath, 400) }
    }
    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 56.dp)
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClickLabel = "Place this signature", onLongClickLabel = "Delete", onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        image?.let {
            Image(it.asImageBitmap(), contentDescription = "Saved signature", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun signatureBitmap(strokes: List<List<Offset>>, size: IntSize, inkColor: Int): Bitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = 6f
    }
    strokes.forEach { stroke ->
        for (i in 1 until stroke.size) {
            canvas.drawLine(
                stroke[i - 1].x,
                stroke[i - 1].y,
                stroke[i].x,
                stroke[i].y,
                paint
            )
        }
    }
    // Crop to the ink plus a margin, so blank pad space doesn't shrink the signature on the page.
    val points = strokes.flatten()
    if (points.isEmpty()) return bitmap
    val margin = paint.strokeWidth * 2
    val left = (points.minOf { it.x } - margin).toInt().coerceIn(0, width - 1)
    val top = (points.minOf { it.y } - margin).toInt().coerceIn(0, height - 1)
    val right = (points.maxOf { it.x } + margin).toInt().coerceIn(left + 1, width)
    val bottom = (points.maxOf { it.y } + margin).toInt().coerceIn(top + 1, height)
    val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    if (cropped !== bitmap) bitmap.recycle()
    return cropped
}
