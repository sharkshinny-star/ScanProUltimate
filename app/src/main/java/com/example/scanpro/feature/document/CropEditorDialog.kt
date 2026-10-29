package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.core.designsystem.AccentButton
import com.example.scanpro.core.designsystem.DarkChrome
import com.example.scanpro.core.designsystem.DarkToolAction
import com.example.scanpro.core.designsystem.GlassIconButton
import com.example.scanpro.core.designsystem.ScanProTheme
import androidx.compose.ui.text.font.FontWeight
import com.example.scanpro.scanner.DocumentEdgeDetector
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
fun CropEditorDialog(
    sourcePath: String,
    initial: CropQuad,
    onDismiss: () -> Unit,
    onConfirm: (CropQuad) -> Unit,
    onRotateLeft: (() -> Unit)? = null,
    onRotateRight: (() -> Unit)? = null,
    onAddPage: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val bitmap by produceState<Bitmap?>(initialValue = null, sourcePath) {
            value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(sourcePath, 2000) }
        }
        var crop by remember(initial) { mutableStateOf(initial) }
        val scope = rememberCoroutineScope()
        val latestCrop by rememberUpdatedState(crop)
        var activeCorner by remember { mutableIntStateOf(0) }
        var draggingCorner by remember { mutableStateOf(false) }
        // Curve mode: the middle handles bend their edges instead of moving them.
        var curveMode by remember(initial) { mutableStateOf(initial.isCurved) }
        val latestCurveMode by rememberUpdatedState(curveMode)

        // The outline and its handles use the same mint as the live scanner, so "found page" looks the same everywhere.
        val handleColor = ScanProTheme.colors.scanLine
        val boundaryColor = ScanProTheme.colors.scanLine

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkChrome.backdrop)
        ) {
            // Top bar: back on glass, with the title and a hint in the middle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onDismiss)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Crop", color = DarkChrome.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (curveMode) "Drag the middle handles to follow the page's curve" else "Drag the corners to the page edges",
                        color = DarkChrome.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            // Center Viewport with Document Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 56.dp, bottom = 180.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { image ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        draggingCorner = true
                                        val handles = handlesForCrop(latestCrop, size.width.toFloat(), size.height.toFloat())
                                        activeCorner = handles.indices.minByOrNull { index ->
                                            hypot((handles[index].x - start.x).toDouble(), (handles[index].y - start.y).toDouble())
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

                                        if (latestCurveMode && activeCorner >= 4) {
                                            val dx = dragAmount.x / size.width
                                            val dy = dragAmount.y / size.height
                                            fun moved(bow: NormalizedPoint?) = NormalizedPoint(
                                                ((bow?.x ?: 0f) + dx).coerceIn(-0.3f, 0.3f),
                                                ((bow?.y ?: 0f) + dy).coerceIn(-0.3f, 0.3f)
                                            )
                                            crop = when (activeCorner) {
                                                4 -> latestCrop.copy(topBow = moved(latestCrop.topBow))
                                                5 -> latestCrop.copy(rightBow = moved(latestCrop.rightBow))
                                                6 -> latestCrop.copy(bottomBow = moved(latestCrop.bottomBow))
                                                else -> latestCrop.copy(leftBow = moved(latestCrop.leftBow))
                                            }
                                            return@detectDragGestures
                                        }
                                        if (activeCorner < 4) {
                                            // Corner handle
                                            val current = points[activeCorner]
                                            points[activeCorner] = NormalizedPoint(
                                                x = (current.x + dragAmount.x / size.width).coerceIn(0f, 1f),
                                                y = (current.y + dragAmount.y / size.height).coerceIn(0f, 1f)
                                            )
                                        } else {
                                            // Edge midpoint handle
                                            val dx = dragAmount.x / size.width
                                            val dy = dragAmount.y / size.height
                                            when (activeCorner) {
                                                4 -> { // Top edge
                                                    points[0] = NormalizedPoint(points[0].x, (points[0].y + dy).coerceIn(0f, 1f))
                                                    points[1] = NormalizedPoint(points[1].x, (points[1].y + dy).coerceIn(0f, 1f))
                                                }
                                                5 -> { // Right edge
                                                    points[1] = NormalizedPoint((points[1].x + dx).coerceIn(0f, 1f), points[1].y)
                                                    points[2] = NormalizedPoint((points[2].x + dx).coerceIn(0f, 1f), points[2].y)
                                                }
                                                6 -> { // Bottom edge
                                                    points[2] = NormalizedPoint(points[2].x, (points[2].y + dy).coerceIn(0f, 1f))
                                                    points[3] = NormalizedPoint(points[3].x, (points[3].y + dy).coerceIn(0f, 1f))
                                                }
                                                7 -> { // Left edge
                                                    points[0] = NormalizedPoint((points[0].x + dx).coerceIn(0f, 1f), points[0].y)
                                                    points[3] = NormalizedPoint((points[3].x + dx).coerceIn(0f, 1f), points[3].y)
                                                }
                                            }
                                        }
                                        // copy() keeps any bend: it is stored relative to the corners, so it follows them.
                                        crop = latestCrop.copy(topLeft = points[0], topRight = points[1], bottomRight = points[2], bottomLeft = points[3])
                                    }
                                )
                            }
                    ) {
                        drawImage(
                            image = image.asImageBitmap(),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                        val handles = handlesForCrop(crop, size.width, size.height)
                        val corners = handles.take(4)

                        // Every edge is drawn as the curve the page will be flattened along.
                        val topControl = curveControl(corners[0], corners[1], handles[4])
                        val rightControl = curveControl(corners[1], corners[2], handles[5])
                        val bottomControl = curveControl(corners[2], corners[3], handles[6])
                        val leftControl = curveControl(corners[3], corners[0], handles[7])
                        val path = Path().apply {
                            moveTo(corners[0].x, corners[0].y)
                            quadraticTo(topControl.x, topControl.y, corners[1].x, corners[1].y)
                            quadraticTo(rightControl.x, rightControl.y, corners[2].x, corners[2].y)
                            quadraticTo(bottomControl.x, bottomControl.y, corners[3].x, corners[3].y)
                            quadraticTo(leftControl.x, leftControl.y, corners[0].x, corners[0].y)
                            close()
                        }
                        drawPath(path, boundaryColor, style = Stroke(width = 6f))

                        // Draw 8 Handles (4 Corners + 4 Midpoints)
                        handles.forEachIndexed { index, point ->
                            val radius = if (index < 4) 18f else 14f
                            val bendHandle = curveMode && index >= 4
                            drawCircle(if (bendHandle) DarkChrome.highlight else handleColor, radius = radius, center = point)
                            drawCircle(Color.White, radius = radius * 0.45f, center = point)
                        }

                        // Loupe magnifier on drag
                        if (draggingCorner && activeCorner < 4) {
                            val normalized = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)[activeCorner]
                            val sampleSize = 96
                            val sourceX = (normalized.x * image.width).toInt().coerceIn(0, image.width - 1)
                            val sourceY = (normalized.y * image.height).toInt().coerceIn(0, image.height - 1)
                            val left = (sourceX - sampleSize / 2).coerceIn(0, (image.width - sampleSize).coerceAtLeast(0))
                            val top = (sourceY - sampleSize / 2).coerceIn(0, (image.height - sampleSize).coerceAtLeast(0))
                            val actualWidth = sampleSize.coerceAtMost(image.width - left)
                            val actualHeight = sampleSize.coerceAtMost(image.height - top)
                            val loupe = 132.dp.toPx()
                            val dstLeft = (size.width - loupe) / 2f
                            val dstTop = 12.dp.toPx()

                            drawRect(
                                color = Color.Black.copy(alpha = 0.85f),
                                topLeft = Offset(dstLeft - 4.dp.toPx(), dstTop - 4.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(loupe + 8.dp.toPx(), loupe + 8.dp.toPx())
                            )
                            drawImage(
                                image = image.asImageBitmap(),
                                srcOffset = IntOffset(left, top),
                                srcSize = IntSize(actualWidth, actualHeight),
                                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                dstSize = IntSize(loupe.toInt(), loupe.toInt())
                            )
                            val center = Offset(dstLeft + loupe / 2f, dstTop + loupe / 2f)
                            drawLine(Color.White, Offset(center.x - 16.dp.toPx(), center.y), Offset(center.x + 16.dp.toPx(), center.y), strokeWidth = 2f)
                            drawLine(Color.White, Offset(center.x, center.y - 16.dp.toPx()), Offset(center.x, center.y + 16.dp.toPx()), strokeWidth = 2f)
                        }
                    }
                } ?: androidx.compose.material3.CircularProgressIndicator(color = ScanProTheme.colors.scanLine)
            }

            // Bottom Floating Controls + Toolbar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(DarkChrome.panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp)
            ) {
                // Page Navigation Bar (e.g. 1/1 + Add Page)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous", tint = DarkChrome.disabledText, modifier = Modifier.size(20.dp))
                            Text("1 of 1", color = DarkChrome.text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next", tint = DarkChrome.disabledText, modifier = Modifier.size(20.dp))
                        }
                    }

                    if (onAddPage != null) {
                        Surface(
                            onClick = onAddPage,
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom Tool Actions (Auto crop, No crop, Left, Right, Done)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DarkToolAction(
                        Icons.Default.Crop,
                        "Auto crop",
                        enabled = bitmap != null,
                        onClick = {
                            val image = bitmap ?: return@DarkToolAction
                            // Outline the page with the edge detector; keep the current corners if none is found.
                            scope.launch {
                                val detection = withContext(Dispatchers.Default) { runCatching { DocumentEdgeDetector.detect(image) }.getOrNull() }
                                val quad = detection?.quad
                                if (quad != null && detection.confidence >= 0.34f) {
                                    crop = if (curveMode) withContext(Dispatchers.Default) {
                                        runCatching { ImageProcessor.estimateCurves(image, quad) }.getOrDefault(quad)
                                    } else quad
                                }
                            }
                        }
                    )

                    DarkToolAction(
                        Icons.Default.Gesture,
                        "Curve",
                        enabled = bitmap != null,
                        highlighted = curveMode,
                        onClick = {
                            val image = bitmap ?: return@DarkToolAction
                            if (curveMode) {
                                curveMode = false
                                crop = crop.straightened()
                            } else {
                                curveMode = true
                                // Start from the page's own edges; the orange handles then fine-tune the bend.
                                scope.launch {
                                    val start = crop
                                    val estimated = withContext(Dispatchers.Default) {
                                        runCatching { ImageProcessor.estimateCurves(image, start) }.getOrDefault(start)
                                    }
                                    if (curveMode && crop == start) crop = estimated
                                }
                            }
                        }
                    )

                    DarkToolAction(Icons.Default.Fullscreen, "No crop") {
                        curveMode = false
                        crop = CropQuad(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 0f), NormalizedPoint(1f, 1f), NormalizedPoint(0f, 1f))
                    }

                    if (onRotateLeft != null) DarkToolAction(Icons.AutoMirrored.Filled.RotateLeft, "Left", onClick = onRotateLeft)
                    if (onRotateRight != null) DarkToolAction(Icons.AutoMirrored.Filled.RotateRight, "Right", onClick = onRotateRight)

                    AccentButton("Done", enabled = bitmap != null, onClick = { onConfirm(crop) })
                }
            }
        }
    }
}

private fun handlesForCrop(crop: CropQuad, width: Float, height: Float): List<Offset> {
    val tl = Offset(crop.topLeft.x * width, crop.topLeft.y * height)
    val tr = Offset(crop.topRight.x * width, crop.topRight.y * height)
    val br = Offset(crop.bottomRight.x * width, crop.bottomRight.y * height)
    val bl = Offset(crop.bottomLeft.x * width, crop.bottomLeft.y * height)

    // Midpoints, sitting on the bent edge when the page is curved.
    val tm = Offset((tl.x + tr.x) / 2f + (crop.topBow?.x ?: 0f) * width, (tl.y + tr.y) / 2f + (crop.topBow?.y ?: 0f) * height)
    val rm = Offset((tr.x + br.x) / 2f + (crop.rightBow?.x ?: 0f) * width, (tr.y + br.y) / 2f + (crop.rightBow?.y ?: 0f) * height)
    val bm = Offset((br.x + bl.x) / 2f + (crop.bottomBow?.x ?: 0f) * width, (br.y + bl.y) / 2f + (crop.bottomBow?.y ?: 0f) * height)
    val lm = Offset((bl.x + tl.x) / 2f + (crop.leftBow?.x ?: 0f) * width, (bl.y + tl.y) / 2f + (crop.leftBow?.y ?: 0f) * height)

    return listOf(tl, tr, br, bl, tm, rm, bm, lm)
}

/** Control point of the quadratic curve from [a] to [b] that passes through [mid]. */
private fun curveControl(a: Offset, b: Offset, mid: Offset) = Offset(2f * mid.x - (a.x + b.x) / 2f, 2f * mid.y - (a.y + b.y) / 2f)
