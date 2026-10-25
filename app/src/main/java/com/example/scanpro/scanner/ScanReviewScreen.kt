package com.example.scanpro.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.hypot

private val Blue = Color(0xFF2F7BFF)
private val Amber = Color(0xFFF59E0B)

/**
 * Full-screen review shown after capturing: swipe through pages with the pager, drag the corners, rotate,
 * add more pages or finish. Rotation is baked into a new image file so crop and rotation never disagree.
 */
@Composable
fun ScanReviewScreen(
    pages: SnapshotStateList<Uri>,
    quads: SnapshotStateMap<String, CropQuad>,
    index: Int,
    sessionDir: File,
    processing: Boolean,
    onIndexChange: (Int) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val current = index.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val uri = pages[current]
    val key = uri.toString()
    var working by remember { mutableStateOf(false) }
    // Curve mode shows a handle in the middle of each edge that bends it, for book pages and curled paper.
    var curveMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val blocked = working || processing

    // While a rotation or the save is running, Back is swallowed: leaving then would strand a half-written file.
    BackHandler(enabled = blocked) { }
    BackHandler(enabled = !blocked) { onBack() }

    val bitmap by produceState<Bitmap?>(initialValue = null, key) {
        value = null
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 1800) }
    }

    // Pages that have not been outlined yet (imports, or captures not seen before) are detected on first view.
    LaunchedEffect(bitmap) {
        val image = bitmap ?: return@LaunchedEffect
        if (quads[key] == null) {
            quads[key] = withContext(Dispatchers.Default) { detectQuad(image) }
        }
    }

    fun rotate(clockwise: Boolean) {
        if (blocked) return
        working = true
        scope.launch {
            val oldKey = key
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 4200) ?: error("decode")
                    val matrix = Matrix().apply { postRotate(if (clockwise) 90f else -90f) }
                    val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    if (turned !== source) source.recycle()
                    val target = File(sessionDir, "rot_${UUID.randomUUID()}.jpg")
                    try { ImageProcessor.saveBitmapJpeg(turned, target, 98) } finally { turned.recycle() }
                    target
                }
            }
            result.onSuccess { file ->
                val newUri = Uri.fromFile(file)
                val quad = quads.remove(oldKey)?.let { rotateQuad(it, clockwise) }
                // The page is only replaced once the rotated file exists, so a failure loses nothing.
                val position = pages.indexOf(uri)
                if (position >= 0) {
                    pages[position] = newUri
                    if (quad != null) quads[newUri.toString()] = quad
                    File(uri.path.orEmpty()).delete()
                }
            }.onFailure { message = "Could not rotate this page" }
            working = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0F18)).systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !blocked, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera", tint = Color.White)
            }
            Box(Modifier.weight(1f))
            fun move(delta: Int) {
                val target = current + delta
                if (target !in pages.indices) return
                val moved = pages[current]
                pages[current] = pages[target]
                pages[target] = moved
                onIndexChange(target)
            }
            IconButton(enabled = !blocked && current > 0, onClick = { move(-1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Move page earlier", tint = Color.White.copy(alpha = if (current > 0) 1f else 0.35f))
            }
            IconButton(enabled = !blocked && current < pages.lastIndex, onClick = { move(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Move page later", tint = Color.White.copy(alpha = if (current < pages.lastIndex) 1f else 0.35f))
            }
            IconButton(
                enabled = !blocked,
                onClick = {
                    quads.remove(key)
                    File(uri.path.orEmpty()).delete()
                    pages.remove(uri)
                    if (pages.isEmpty()) onBack() else onIndexChange(current.coerceAtMost(pages.lastIndex))
                }
            ) { Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = Color.White) }
        }

        var swipe by remember { mutableStateOf(0f) }
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(current, blocked) {
                // Only drags that start away from a corner reach here; corner drags are consumed by the outline.
                detectHorizontalDragGestures(
                    onDragStart = { swipe = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); swipe += amount },
                    onDragEnd = {
                        if (!blocked) {
                            if (swipe < -120f && current < pages.lastIndex) onIndexChange(current + 1)
                            else if (swipe > 120f && current > 0) onIndexChange(current - 1)
                        }
                        swipe = 0f
                    },
                    onDragCancel = { swipe = 0f }
                )
            },
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            val quad = quads[key]
            if (image == null || quad == null) {
                CircularProgressIndicator(color = Blue)
            } else {
                CropCanvas(image = image, crop = quad, curveMode = curveMode, enabled = !blocked, onCropChange = { quads[key] = it })
            }
        }

        if (pages.size > 1) {
            ThumbnailStrip(
                pages = pages,
                selected = current,
                enabled = !blocked,
                onSelect = onIndexChange,
                onMoved = { from, to ->
                    // Keep the same page selected after the order changes.
                    onIndexChange(
                        when (current) {
                            from -> to
                            else -> if (from < to && current in (from + 1)..to) current - 1 else if (from > to && current in to until from) current + 1 else current
                        }
                    )
                }
            )
        }

        message?.let { Text(it, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(enabled = current > 0 && !blocked, onClick = { onIndexChange(current - 1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", tint = Color.White.copy(alpha = if (current > 0) 1f else 0.35f))
                }
                Text("${current + 1}/${pages.size}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = current < pages.lastIndex && !blocked, onClick = { onIndexChange(current + 1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page", tint = Color.White.copy(alpha = if (current < pages.lastIndex) 1f else 0.35f))
                }
            }
            OutlinedButton(enabled = !blocked, onClick = onAdd) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ReviewAction(Icons.Default.CropFree, "Auto crop", !blocked && bitmap != null) {
                val image = bitmap ?: return@ReviewAction
                scope.launch {
                    quads[key] = withContext(Dispatchers.Default) { detectQuad(image) }
                }
            }
            ReviewAction(Icons.Default.Fullscreen, "No crop", !blocked) { quads[key] = CropQuad.DEFAULT }
            ReviewAction(Icons.Default.Gesture, if (curveMode) "Curve on" else "Curve", !blocked && bitmap != null, highlighted = curveMode) {
                val image = bitmap ?: return@ReviewAction
                val start = quads[key] ?: return@ReviewAction
                if (curveMode) {
                    curveMode = false
                    quads[key] = start.straightened()
                } else {
                    curveMode = true
                    // Start from the page's own edges; the orange handles then fine-tune each bend.
                    scope.launch {
                        val estimated = withContext(Dispatchers.Default) {
                            runCatching { ImageProcessor.estimateCurves(image, start) }.getOrDefault(start)
                        }
                        if (curveMode && quads[key] == start) quads[key] = estimated
                    }
                }
            }
            ReviewAction(Icons.AutoMirrored.Filled.RotateLeft, "Left", !blocked) { rotate(clockwise = false) }
            ReviewAction(Icons.AutoMirrored.Filled.RotateRight, "Right", !blocked) { rotate(clockwise = true) }
            Button(enabled = !blocked, onClick = onDone) {
                if (processing) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Done")
            }
        }
    }
}

@Composable
private fun ReviewAction(icon: ImageVector, label: String, enabled: Boolean, highlighted: Boolean = false, onClick: () -> Unit) {
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        highlighted -> Amber
        else -> Color.White
    }
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The page with a draggable four-corner outline. Dragging moves whichever corner is nearest the touch; in
 * [curveMode] the handle in the middle of each edge bends that edge.
 */
@Composable
private fun CropCanvas(image: Bitmap, crop: CropQuad, curveMode: Boolean, enabled: Boolean, onCropChange: (CropQuad) -> Unit) {
    var active by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(crop)
    val latestEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val latestCurveMode by androidx.compose.runtime.rememberUpdatedState(curveMode)
    Canvas(
        Modifier
            .aspectRatio(image.width.toFloat() / image.height.toFloat())
            .pointerInput(image) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val points = handles(latest, latestCurveMode).map { Offset(it.x * size.width, it.y * size.height) }
                    val nearest = points.indices.minByOrNull { hypot(points[it].x - down.position.x, points[it].y - down.position.y) } ?: 0
                    val reach = 44.dp.toPx()
                    val distance = hypot(points[nearest].x - down.position.x, points[nearest].y - down.position.y)
                    // Away from every corner the touch is left alone, so the page can be swiped.
                    if (distance > reach || !latestEnabled) return@awaitEachGesture
                    active = nearest
                    dragging = true
                    drag(down.id) { change ->
                        change.consume()
                        val delta = change.position - change.previousPosition
                        if (active >= 4) {
                            onCropChange(bend(latest, active - 4, delta.x / size.width, delta.y / size.height))
                            return@drag
                        }
                        val list = corners(latest).toMutableList()
                        val p = list[active]
                        list[active] = NormalizedPoint(
                            (p.x + delta.x / size.width).coerceIn(0f, 1f),
                            (p.y + delta.y / size.height).coerceIn(0f, 1f)
                        )
                        // copy() keeps any bends: they are stored relative to the corners, so they follow them.
                        onCropChange(latest.copy(topLeft = list[0], topRight = list[1], bottomRight = list[2], bottomLeft = list[3]))
                    }
                    dragging = false
                }
            }
    ) {
        drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        val all = handles(crop, curveMode = true).map { Offset(it.x * size.width, it.y * size.height) }
        val points = all.take(4)
        // Each edge as the curve it will be flattened along (a straight edge's midpoint gives a straight line).
        fun control(a: Offset, b: Offset, mid: Offset) = Offset(2f * mid.x - (a.x + b.x) / 2f, 2f * mid.y - (a.y + b.y) / 2f)
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (edge in 0 until 4) {
                val a = points[edge]
                val b = points[(edge + 1) % 4]
                val c = control(a, b, all[4 + edge])
                quadraticTo(c.x, c.y, b.x, b.y)
            }
            close()
        }
        drawPath(path, Blue, style = Stroke(width = 3.dp.toPx()))
        points.forEach { drawCircle(Blue, radius = 11.dp.toPx(), center = it) }
        if (curveMode) all.drop(4).forEach { drawCircle(Amber, radius = 9.dp.toPx(), center = it) }
        if (dragging && active < 4) {
            val corner = corners(crop)[active]
            val sample = 96
            val sx = (corner.x * image.width).toInt().coerceIn(0, image.width - 1)
            val sy = (corner.y * image.height).toInt().coerceIn(0, image.height - 1)
            val left = (sx - sample / 2).coerceIn(0, (image.width - sample).coerceAtLeast(0))
            val top = (sy - sample / 2).coerceIn(0, (image.height - sample).coerceAtLeast(0))
            val loupe = 120.dp.toPx()
            val dstLeft = if (corner.x < 0.5f) size.width - loupe - 8.dp.toPx() else 8.dp.toPx()
            val dstTop = 8.dp.toPx()
            drawRect(Color.Black.copy(alpha = 0.8f), Offset(dstLeft - 4.dp.toPx(), dstTop - 4.dp.toPx()), androidx.compose.ui.geometry.Size(loupe + 8.dp.toPx(), loupe + 8.dp.toPx()))
            drawImage(
                image.asImageBitmap(),
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(sample.coerceAtMost(image.width - left), sample.coerceAtMost(image.height - top)),
                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                dstSize = IntSize(loupe.toInt(), loupe.toInt())
            )
            val c = Offset(dstLeft + loupe / 2f, dstTop + loupe / 2f)
            drawLine(Color.White, Offset(c.x - 14.dp.toPx(), c.y), Offset(c.x + 14.dp.toPx(), c.y), strokeWidth = 2f)
            drawLine(Color.White, Offset(c.x, c.y - 14.dp.toPx()), Offset(c.x, c.y + 14.dp.toPx()), strokeWidth = 2f)
        }
    }
}

private fun corners(crop: CropQuad) = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)

/** The corners, then (in curve mode) the middle of the top, right, bottom and left edges, bends included. */
private fun handles(crop: CropQuad, curveMode: Boolean): List<NormalizedPoint> {
    val c = corners(crop)
    if (!curveMode) return c
    fun mid(a: NormalizedPoint, b: NormalizedPoint, bow: NormalizedPoint?) =
        NormalizedPoint((a.x + b.x) / 2f + (bow?.x ?: 0f), (a.y + b.y) / 2f + (bow?.y ?: 0f))
    return c + listOf(
        mid(c[0], c[1], crop.topBow), mid(c[1], c[2], crop.rightBow),
        mid(c[2], c[3], crop.bottomBow), mid(c[3], c[0], crop.leftBow)
    )
}

/** Bends edge [edge] (0 top, 1 right, 2 bottom, 3 left) by a drag of (dx, dy), within sensible limits. */
private fun bend(crop: CropQuad, edge: Int, dx: Float, dy: Float): CropQuad {
    fun moved(bow: NormalizedPoint?) = NormalizedPoint(((bow?.x ?: 0f) + dx).coerceIn(-0.3f, 0.3f), ((bow?.y ?: 0f) + dy).coerceIn(-0.3f, 0.3f))
    return when (edge) {
        0 -> crop.copy(topBow = moved(crop.topBow))
        1 -> crop.copy(rightBow = moved(crop.rightBow))
        2 -> crop.copy(bottomBow = moved(crop.bottomBow))
        else -> crop.copy(leftBow = moved(crop.leftBow))
    }
}

/** Detected page outline, or the whole frame when nothing convincing is found. */
private fun detectQuad(image: Bitmap): CropQuad {
    val detection = runCatching { DocumentEdgeDetector.detect(image) }.getOrNull()
    val quad = detection?.quad
    return if (quad != null && detection.confidence >= 0.34f) quad else CropQuad.DEFAULT
}

/**
 * Page thumbnails: tap to jump, press and hold then drag sideways to reorder. A page moves one slot each
 * time it is dragged a full thumbnail width past its neighbour.
 */
@Composable
private fun ThumbnailStrip(
    pages: SnapshotStateList<Uri>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onMoved: (from: Int, to: Int) -> Unit
) {
    val itemWidth = 56.dp
    val gap = 8.dp
    // Gesture handlers outlive recompositions, so they read the newest callbacks (and the selection inside them).
    val latestOnMoved by androidx.compose.runtime.rememberUpdatedState(onMoved)
    val latestOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
            val key = uri.toString()
            val thumb by produceState<Bitmap?>(initialValue = null, key) {
                value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 220) }
            }
            val isDragging = draggingKey == key
            Box(
                Modifier
                    .size(itemWidth, 72.dp)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragging) dragOffset else 0f
                        alpha = if (isDragging) 0.85f else 1f
                        scaleX = if (isDragging) 1.08f else 1f
                        scaleY = if (isDragging) 1.08f else 1f
                    }
                    .border(if (index == selected) 2.dp else 1.dp, if (index == selected) Blue else Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { latestOnSelect(pages.indexOf(uri)) }
                    .pointerInput(key, enabled) {
                        if (!enabled) return@pointerInput
                        val step = (itemWidth + gap).toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingKey = key; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                val from = pages.indexOf(uri)
                                val to = when {
                                    dragOffset > step / 2 && from < pages.lastIndex -> from + 1
                                    dragOffset < -step / 2 && from > 0 -> from - 1
                                    else -> from
                                }
                                if (to != from) {
                                    val other = pages[to]
                                    pages[to] = uri
                                    pages[from] = other
                                    // The item now sits one slot over, so the finger offset is measured from there.
                                    dragOffset -= (to - from) * step
                                    latestOnMoved(from, to)
                                }
                            },
                            onDragEnd = { draggingKey = null; dragOffset = 0f },
                            onDragCancel = { draggingKey = null; dragOffset = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(2.dp)
                    )
                }
                Text(
                    "${index + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 6.dp)).padding(horizontal = 4.dp)
                )
            }
        }
    }
}
