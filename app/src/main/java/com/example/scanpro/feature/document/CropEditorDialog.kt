package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.example.scanpro.scanner.DocumentEdgeDetector
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
fun CropEditorDialog(
    sourcePath: String,
    initial: CropQuad,
    onDismiss: () -> Unit,
    onConfirm: (CropQuad) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, sourcePath) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(sourcePath, 1800) }
    }
    var crop by remember(initial) { mutableStateOf(initial) }
    val latestCrop by rememberUpdatedState(crop)
    var activeCorner by remember { mutableIntStateOf(0) }
    var draggingCorner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var autoMessage by remember { mutableStateOf<String?>(null) }
    val undoStack = remember(initial) { mutableStateListOf<CropQuad>() }
    val redoStack = remember(initial) { mutableStateListOf<CropQuad>() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0F18)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White) }
                    Text("Adjust corners", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(
                    "Drag each corner to the paper edge. The page is perspective-corrected when you apply the crop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                autoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                bitmap?.let { image ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        undoStack.add(latestCrop)
                                        if (undoStack.size > 20) undoStack.removeAt(0)
                                        redoStack.clear()
                                        draggingCorner = true
                                        val points = cropPoints(latestCrop, size.width.toFloat(), size.height.toFloat())
                                        activeCorner = points.indices.minByOrNull { index ->
                                            hypot(
                                                (points[index].x - start.x).toDouble(),
                                                (points[index].y - start.y).toDouble()
                                            )
                                        } ?: 0
                                    },
                                    onDragEnd = { draggingCorner = false },
                                    onDragCancel = { draggingCorner = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val points = listOf(
                                            latestCrop.topLeft,
                                            latestCrop.topRight,
                                            latestCrop.bottomRight,
                                            latestCrop.bottomLeft
                                        ).toMutableList()
                                        val current = points[activeCorner]
                                        points[activeCorner] = NormalizedPoint(
                                            x = (current.x + dragAmount.x / size.width).coerceIn(0f, 1f),
                                            y = (current.y + dragAmount.y / size.height).coerceIn(0f, 1f)
                                        )
                                        crop = CropQuad(points[0], points[1], points[2], points[3])
                                    }
                                )
                            }
                    ) {
                        drawImage(
                            image = image.asImageBitmap(),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                        val points = cropPoints(crop, size.width, size.height)
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            lineTo(points[1].x, points[1].y)
                            lineTo(points[2].x, points[2].y)
                            lineTo(points[3].x, points[3].y)
                            close()
                        }
                        drawPath(path, Color.White, style = Stroke(width = 4f))
                        points.forEach { point ->
                            drawCircle(Color.White, radius = 14f, center = point)
                            drawCircle(Color.Black, radius = 8f, center = point)
                        }

                        if (draggingCorner) {
                            val normalized = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)[activeCorner]
                            val imageBitmap = image.asImageBitmap()
                            val sampleSize = 96
                            val sourceX = (normalized.x * image.width).toInt().coerceIn(0, image.width - 1)
                            val sourceY = (normalized.y * image.height).toInt().coerceIn(0, image.height - 1)
                            val left = (sourceX - sampleSize / 2).coerceIn(0, (image.width - sampleSize).coerceAtLeast(0))
                            val top = (sourceY - sampleSize / 2).coerceIn(0, (image.height - sampleSize).coerceAtLeast(0))
                            val actualWidth = sampleSize.coerceAtMost(image.width - left)
                            val actualHeight = sampleSize.coerceAtMost(image.height - top)
                            val loupe = 132.dp.toPx()
                            val dstLeft = (size.width - loupe) / 2f
                            val dstTop = 16.dp.toPx()
                            drawRect(
                                color = Color.Black.copy(alpha = 0.76f),
                                topLeft = Offset(dstLeft - 5.dp.toPx(), dstTop - 5.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(loupe + 10.dp.toPx(), loupe + 10.dp.toPx())
                            )
                            drawImage(
                                image = imageBitmap,
                                srcOffset = IntOffset(left, top),
                                srcSize = IntSize(actualWidth, actualHeight),
                                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                dstSize = IntSize(loupe.toInt(), loupe.toInt())
                            )
                            val center = Offset(dstLeft + loupe / 2f, dstTop + loupe / 2f)
                            drawLine(Color.White, Offset(center.x - 18.dp.toPx(), center.y), Offset(center.x + 18.dp.toPx(), center.y), strokeWidth = 2f)
                            drawLine(Color.White, Offset(center.x, center.y - 18.dp.toPx()), Offset(center.x, center.y + 18.dp.toPx()), strokeWidth = 2f)
                        }
                    }
                } ?: Text("Loading page…", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CropAction(Icons.Default.CropFree, "Auto crop", enabled = bitmap != null) {
                        val image = bitmap ?: return@CropAction
                        scope.launch {
                            val detected = withContext(Dispatchers.Default) {
                                runCatching { DocumentEdgeDetector.detect(image).quad }.getOrNull()
                            }
                            if (detected != null) {
                                undoStack.add(crop)
                                redoStack.clear()
                                crop = detected
                                autoMessage = null
                            } else autoMessage = "No document edges found. Drag the corners instead."
                        }
                    }
                    CropAction(Icons.Default.Fullscreen, "No crop") {
                        undoStack.add(crop)
                        redoStack.clear()
                        crop = CropQuad.DEFAULT
                        autoMessage = null
                    }
                    CropAction(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = undoStack.isNotEmpty()) {
                        redoStack.add(crop)
                        crop = undoStack.removeAt(undoStack.lastIndex)
                    }
                    CropAction(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = redoStack.isNotEmpty()) {
                        undoStack.add(crop)
                        crop = redoStack.removeAt(redoStack.lastIndex)
                    }
                    Button(enabled = bitmap != null, onClick = { onConfirm(crop) }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CropAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

private fun cropPoints(crop: CropQuad, width: Float, height: Float): List<Offset> = listOf(
    Offset(crop.topLeft.x * width, crop.topLeft.y * height),
    Offset(crop.topRight.x * width, crop.topRight.y * height),
    Offset(crop.bottomRight.x * width, crop.bottomRight.y * height),
    Offset(crop.bottomLeft.x * width, crop.bottomLeft.y * height)
)
