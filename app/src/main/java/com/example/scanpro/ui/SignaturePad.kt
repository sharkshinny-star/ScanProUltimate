package com.example.scanpro.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
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

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add signature") },
        text = {
            Column {
                Text(
                    "Sign below. The signature will be flattened onto the current page.",
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
                                color = Color.Black,
                                start = stroke[i - 1],
                                end = stroke[i],
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.any { it.size > 1 } && padSize.width > 0,
                onClick = {
                    onConfirm(signatureBitmap(strokes, padSize))
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
}

private fun signatureBitmap(strokes: List<List<Offset>>, size: IntSize): Bitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
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
    return bitmap
}
