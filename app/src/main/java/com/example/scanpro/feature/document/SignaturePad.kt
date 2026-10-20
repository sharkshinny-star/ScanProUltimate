package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.PageThumbnail

data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidthDp: Float
)

private val PaletteColors = listOf(
    Color.Black,
    Color(0xFF2563EB), // Blue
    Color(0xFFEF4444), // Red
    Color(0xFF10B981), // Green
    Color(0xFF8B5CF6), // Purple
    Color(0xFFF59E0B), // Orange
    Color(0xFF475569)  // Slate
)

private val StrokeWidths = listOf(3f, 6f, 10f, 16f, 22f)

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var strokes by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentWidthDp by remember { mutableFloatStateOf(6f) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw & Sign") },
        text = {
            Column {
                Text(
                    "Draw or sign below. Select color and thickness.",
                    style = MaterialTheme.typography.bodySmall
                )
                Canvas(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val newStroke = DrawingStroke(listOf(start), currentColor, currentWidthDp)
                                    strokes = strokes + newStroke
                                    redoStack = emptyList()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val last = strokes.lastOrNull() ?: return@detectDragGestures
                                    val updatedPoints = last.points + change.position
                                    strokes = strokes.dropLast(1) + last.copy(points = updatedPoints)
                                }
                            )
                        }
                ) {
                    strokes.forEach { stroke ->
                        val points = stroke.points
                        for (i in 1 until points.size) {
                            drawLine(
                                color = stroke.color,
                                start = points[i - 1],
                                end = points[i],
                                strokeWidth = stroke.strokeWidthDp * density,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Ink Color", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaletteColors.forEach { color ->
                        Surface(
                            shape = CircleShape,
                            color = color,
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    width = if (currentColor == color) 2.5.dp else 0.dp,
                                    color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { currentColor = color }
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Thickness", style = MaterialTheme.typography.labelSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            StrokeWidths.forEach { width ->
                                Surface(
                                    shape = CircleShape,
                                    color = if (currentWidthDp == width) currentColor else Color.LightGray,
                                    modifier = Modifier
                                        .size((width + 8).dp)
                                        .clickable { currentWidthDp = width }
                                ) {}
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    val last = strokes.last()
                                    strokes = strokes.dropLast(1)
                                    redoStack = redoStack + last
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            enabled = redoStack.isNotEmpty(),
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val last = redoStack.last()
                                    redoStack = redoStack.dropLast(1)
                                    strokes = strokes + last
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.any { it.points.size > 1 } && padSize.width > 0,
                onClick = {
                    onConfirm(signatureBitmap(strokes, padSize))
                }
            ) { Text("Place drawing") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (strokes.isEmpty()) onDismiss() else {
                    strokes = emptyList()
                    redoStack = emptyList()
                }
            }) {
                Text(if (strokes.isEmpty()) "Cancel" else "Clear")
            }
        }
    )
}

@Composable
fun SignaturePlacementDialog(
    pageImagePath: String,
    signatureBitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirmPlacement: (normX: Float, normY: Float, normW: Float, normH: Float) -> Unit
) {
    var containerWidth by remember { mutableFloatStateOf(1f) }
    var containerHeight by remember { mutableFloatStateOf(1f) }

    var posX by remember { mutableFloatStateOf(0.35f) }
    var posY by remember { mutableFloatStateOf(0.65f) }
    var sizeW by remember { mutableFloatStateOf(0.35f) }
    var sizeH by remember { mutableFloatStateOf(0.15f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place Signature on Page") },
        text = {
            Column {
                Text(
                    "Drag signature to position it on the document page:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged {
                            containerWidth = it.width.toFloat().coerceAtLeast(1f)
                            containerHeight = it.height.toFloat().coerceAtLeast(1f)
                        }
                ) {
                    PageThumbnail(pageImagePath, modifier = Modifier.fillMaxSize())

                    val sigPixelX = posX * containerWidth
                    val sigPixelY = posY * containerHeight
                    val sigPixelW = (sizeW * containerWidth).coerceAtLeast(60f)
                    val sigPixelH = (sizeH * containerHeight).coerceAtLeast(30f)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(sigPixelX.toInt(), sigPixelY.toInt()) }
                            .size(
                                width = (sigPixelW / LocalDensity.current.density).dp,
                                height = (sigPixelH / LocalDensity.current.density).dp
                            )
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newX = (sigPixelX + dragAmount.x) / containerWidth
                                    val newY = (sigPixelY + dragAmount.y) / containerHeight
                                    posX = newX.coerceIn(0f, (1f - sizeW).coerceAtLeast(0f))
                                    posY = newY.coerceIn(0f, (1f - sizeH).coerceAtLeast(0f))
                                }
                            }
                    ) {
                        Image(
                            bitmap = signatureBitmap.asImageBitmap(),
                            contentDescription = "Signature",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmPlacement(posX, posY, sizeW, sizeH)
                }
            ) {
                Text("Confirm Placement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun signatureBitmap(strokes: List<DrawingStroke>, size: IntSize): Bitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)

    strokes.forEach { stroke ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = stroke.strokeWidthDp * 1.5f
        }
        val points = stroke.points
        for (i in 1 until points.size) {
            canvas.drawLine(
                points[i - 1].x,
                points[i - 1].y,
                points[i].x,
                points[i].y,
                paint
            )
        }
    }
    // Crop to the ink plus a margin, so blank pad space doesn't shrink the signature on the page.
    val points = strokes.flatMap { it.points }
    if (points.isEmpty()) return bitmap
    val margin = (strokes.maxOf { it.strokeWidthDp } * 1.5f) * 2
    val left = (points.minOf { it.x } - margin).toInt().coerceIn(0, width - 1)
    val top = (points.minOf { it.y } - margin).toInt().coerceIn(0, height - 1)
    val right = (points.maxOf { it.x } + margin).toInt().coerceIn(left + 1, width)
    val bottom = (points.maxOf { it.y } + margin).toInt().coerceIn(top + 1, height)
    val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    if (cropped !== bitmap) bitmap.recycle()
    return cropped
}
