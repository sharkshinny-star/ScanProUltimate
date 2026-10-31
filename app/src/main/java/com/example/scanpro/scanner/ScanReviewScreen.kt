package com.example.scanpro.scanner

import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
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
import com.example.scanpro.R
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageFilter
import com.example.scanpro.core.ui.FilterStrip
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.time.Duration.Companion.milliseconds

private val Blue = Color(0xFF2FD6A6)
private val Amber = Color(0xFFFFB95C)
private val ReviewBackdrop = Color(0xFF0B0D14)

/** Rotation is a physical direction, so these icons must not flip in right-to-left layouts (the AutoMirrored ones would). */
@Suppress("DEPRECATION")
private val RotateLeftIcon: ImageVector = Icons.Filled.RotateLeft
@Suppress("DEPRECATION")
private val RotateRightIcon: ImageVector = Icons.Filled.RotateRight

/**
 * Full-screen review shown after capturing: swipe through pages with the pager, drag the corners, rotate,
 * add more pages or finish. Under the page, a strip previews each filter on the cropped page. Rotation is baked into a new image file so crop and rotation never disagree.
 */
@Composable
fun ScanReviewScreen(
    pages: SnapshotStateList<Uri>,
    quads: SnapshotStateMap<String, CropQuad>,
    filters: SnapshotStateMap<String, PageFilter>,
    defaultFilter: PageFilter,
    onDefaultFilterChange: (PageFilter) -> Unit,
    index: Int,
    processing: Boolean,
    onIndexChange: (Int) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    /** Returns to the camera to shoot this page again; the next capture replaces it in place. */
    onRetake: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val current = index.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val uri = pages[current]
    val key = uri.toString()
    var working by remember { mutableStateOf(false) }
    // Curve mode shows a handle in the middle of each edge that bends it, for book pages and curled paper.
    var curveMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Bumped after a rotation so the page and its thumbnail are decoded again from the same file.
    var rotationTick by remember { mutableIntStateOf(0) }
    val thumbCache = remember { HashMap<String, Bitmap>() }
    val blocked = working || processing

    // While a rotation or the save is running, Back is swallowed: leaving then would strand a half-written file.
    BackHandler(enabled = blocked) { }
    BackHandler(enabled = !blocked) { onBack() }

    val bitmap by produceState<Bitmap?>(initialValue = null, key, rotationTick) {
        value = null
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 1800) }
    }

    // Filter previews follow the outline, a moment after the last drag so dragging stays smooth. The previous
    // previews stay up meanwhile.
    var previews by remember(key, rotationTick) { mutableStateOf<Map<PageFilter, Bitmap>>(emptyMap()) }
    val previewQuad = quads[key]
    LaunchedEffect(bitmap, previewQuad) {
        val image = bitmap ?: return@LaunchedEffect
        val quad = previewQuad ?: return@LaunchedEffect
        delay(350.milliseconds)
        withContext(Dispatchers.Default) { runCatching { ImageProcessor.filterPreviews(image, quad) }.getOrNull() }
            ?.let { previews = it }
    }
    val pageFilter = filters[key] ?: defaultFilter

    // Pages that have not been outlined yet (imports, or captures not seen before) are detected on first view.
    LaunchedEffect(bitmap) {
        val image = bitmap ?: return@LaunchedEffect
        if (quads[key] == null) {
            val detected = withContext(Dispatchers.Default) { detectQuad(image) }
            // Checked again after detecting: the capture's own detection, or a corner the user has already
            // dragged, must not be replaced.
            if (quads[key] == null) quads[key] = detected
        }
    }

    fun rotate(clockwise: Boolean) {
        if (blocked) return
        working = true
        scope.launch {
            // The turn is written into the photo's EXIF orientation: no pixels are re-encoded, nothing is
            // downscaled, and every later step reads the tag. The outline turns with it.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val exif = androidx.exifinterface.media.ExifInterface(uri.path.orEmpty())
                    exif.rotate(if (clockwise) 90 else -90)
                    exif.saveAttributes()
                }
            }
            result.onSuccess {
                quads[key]?.let { quads[key] = rotateQuad(it, clockwise) }
                rotationTick++
            }.onFailure { message = resources.getString(R.string.scan_review_rotate_failed) }
            working = false
        }
    }

    Column(Modifier.fillMaxSize().background(ReviewBackdrop).systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !blocked, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.scan_review_back_to_camera), tint = Color.White)
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
                Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = stringResource(R.string.scan_review_move_earlier), tint = Color.White.copy(alpha = if (current > 0) 1f else 0.35f))
            }
            IconButton(enabled = !blocked && current < pages.lastIndex, onClick = { move(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = stringResource(R.string.scan_review_move_later), tint = Color.White.copy(alpha = if (current < pages.lastIndex) 1f else 0.35f))
            }
            if (onRetake != null) {
                IconButton(enabled = !blocked, onClick = onRetake) {
                    Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.scan_review_retake_page), tint = Color.White)
                }
            }
            IconButton(
                enabled = !blocked,
                onClick = {
                    quads.remove(key)
                    filters.remove(key)
                    Thread { File(uri.path.orEmpty()).delete() }.start()
                    pages.remove(uri)
                    if (pages.isEmpty()) onBack() else onIndexChange(current.coerceAtMost(pages.lastIndex))
                }
            ) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.scan_review_delete_page), tint = Color.White) }
        }

        var swipe by remember { mutableFloatStateOf(0f) }
        // Pages follow the reading direction: swiping towards the end of the row (left in LTR, right in RTL) goes forward.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(current, blocked, isRtl) {
                // Only drags that start away from a corner reach here; corner drags are consumed by the outline.
                detectHorizontalDragGestures(
                    onDragStart = { swipe = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); swipe += amount },
                    onDragEnd = {
                        if (!blocked) {
                            val travel = if (isRtl) -swipe else swipe
                            if (travel < -120f && current < pages.lastIndex) onIndexChange(current + 1)
                            else if (travel > 120f && current > 0) onIndexChange(current - 1)
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
                rotationTick = rotationTick,
                thumbCache = thumbCache,
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

        FilterStrip(
            previews = previews,
            selected = pageFilter,
            enabled = !blocked,
            onDark = true,
            onSelect = { filter ->
                filters[key] = filter
                // Pages not set by hand follow the newest choice, so a batch is styled with one tap.
                onDefaultFilterChange(filter)
            },
            modifier = Modifier.padding(top = 10.dp)
        )
        if (pages.size > 1 && pages.any { (filters[it.toString()] ?: defaultFilter) != pageFilter }) {
            Text(
                stringResource(R.string.scan_review_use_on_all, stringResource(pageFilter.labelRes)),
                color = Blue,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = !blocked, role = Role.Button) { pages.forEach { filters[it.toString()] = pageFilter } }
                    .padding(vertical = 6.dp)
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
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.scan_review_previous_page), tint = Color.White.copy(alpha = if (current > 0) 1f else 0.35f))
                }
                Text("${current + 1}/${pages.size}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = current < pages.lastIndex && !blocked, onClick = { onIndexChange(current + 1) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.scan_review_next_page), tint = Color.White.copy(alpha = if (current < pages.lastIndex) 1f else 0.35f))
                }
            }
            OutlinedButton(
                enabled = !blocked,
                onClick = onAdd,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.4f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_review_add))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ReviewAction(Icons.Default.CropFree, stringResource(R.string.scan_review_auto_crop), !blocked && bitmap != null) {
                val image = bitmap ?: return@ReviewAction
                scope.launch {
                    quads[key] = withContext(Dispatchers.Default) { detectQuad(image) }
                }
            }
            ReviewAction(Icons.Default.Fullscreen, stringResource(R.string.scan_review_no_crop), !blocked) { quads[key] = CropQuad.DEFAULT }
            // Same label on or off (the colour shows the state): a longer label squeezed "Done" onto four lines.
            ReviewAction(Icons.Default.Gesture, stringResource(R.string.scan_review_curve), !blocked && bitmap != null, highlighted = curveMode) {
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
            ReviewAction(RotateLeftIcon, stringResource(R.string.scan_review_rotate_left), !blocked) { rotate(clockwise = false) }
            ReviewAction(RotateRightIcon, stringResource(R.string.scan_review_rotate_right), !blocked) { rotate(clockwise = true) }
            Button(
                enabled = !blocked,
                onClick = onDone,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color(0xFF04281E))
            ) {
                if (processing) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp, color = Color(0xFF04281E))
                else Text(stringResource(R.string.common_done), maxLines = 1, softWrap = false)
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
    val onDescription = stringResource(R.string.scan_review_state_on)
    Column(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) Amber.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (highlighted) stateDescription = onDescription }
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
    var active by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(crop)
    val latestEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val latestCurveMode by androidx.compose.runtime.rememberUpdatedState(curveMode)
    val pagePreview = stringResource(R.string.a11y_page_preview)
    Canvas(
        Modifier
            .aspectRatio(image.width.toFloat() / image.height.toFloat())
            .semantics { contentDescription = pagePreview }
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
    rotationTick: Int,
    thumbCache: HashMap<String, Bitmap>,
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
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The strip runs right to left in a right-to-left layout, so a drag to the right then moves a page earlier.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
            val key = uri.toString()
            // Decoded once per page and kept, so scrolling a long batch doesn't re-read every JPEG.
            val cacheKey = "$key#$rotationTick"
            val thumb by produceState(initialValue = thumbCache[cacheKey], cacheKey) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 220) }
                    value?.let { thumbCache[cacheKey] = it }
                }
            }
            val isDragging = draggingKey == key
            val isSelected = index == selected
            val pageDescription = stringResource(R.string.viewer_page_of, index + 1, pages.size)
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
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Blue else Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, role = Role.Tab) { latestOnSelect(pages.indexOf(uri)) }
                    .semantics { this.selected = isSelected }
                    .pointerInput(key, enabled, isRtl) {
                        if (!enabled) return@pointerInput
                        val step = (itemWidth + gap).toPx()
                        // +1 slot per step of travel towards the end of the row: rightwards in LTR, leftwards in RTL.
                        val direction = if (isRtl) -1f else 1f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingKey = key; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                val from = pages.indexOf(uri)
                                val travel = dragOffset * direction
                                val to = when {
                                    travel > step / 2 && from < pages.lastIndex -> from + 1
                                    travel < -step / 2 && from > 0 -> from - 1
                                    else -> from
                                }
                                if (to != from) {
                                    val other = pages[to]
                                    pages[to] = uri
                                    pages[from] = other
                                    // The item now sits one slot over, so the finger offset is measured from there.
                                    dragOffset -= (to - from) * step * direction
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
                        contentDescription = pageDescription,
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
