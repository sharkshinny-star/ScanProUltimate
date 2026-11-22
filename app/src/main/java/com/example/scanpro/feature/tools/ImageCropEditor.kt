package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.imaging.ImageCropSpec
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.ImageToolsEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The aspect ratios offered, width over height; null is free, 0 is the picture's own. */
private val CROP_RATIOS: List<Pair<Float?, Int>> = listOf(
    null to R.string.img_crop_free,
    0f to R.string.img_crop_original,
    1f to R.string.img_crop_square,
    4f / 5f to R.string.img_crop_4_5,
    3f / 4f to R.string.img_crop_3_4,
    4f / 3f to R.string.img_crop_4_3,
    16f / 9f to R.string.img_crop_16_9,
    9f / 16f to R.string.img_crop_9_16
)

private val EditorBackground = Color(0xFF0E0F12)
private val EditorText = Color(0xFFECEDEF)

/**
 * Crop image (F193): a straight crop for photos, replacing the document corner editor, which bent the picture to a
 * four-corner shape, painted transparency white and always wrote a JPG. Drag the corners or edges, or the frame itself;
 * pick an aspect ratio; turn by quarter turns or mirror. The size in pixels of the result shows as the frame moves.
 */
@Composable
internal fun ImageCropEditor(sourcePath: String, onDismiss: () -> Unit, onConfirm: (ImageCropSpec) -> Unit) {
    // The preview copy, and the full picture's upright size (for the pixel count).
    val loaded by produceState<Pair<Bitmap, IntSize>?>(null, sourcePath) {
        value = withContext(Dispatchers.IO) {
            val preview = ImageProcessor.decodeOrientedScaled(sourcePath, 1600) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourcePath, bounds)
            val turn = runCatching { androidx.exifinterface.media.ExifInterface(sourcePath).rotationDegrees }.getOrDefault(0)
            val size = if (turn == 90 || turn == 270) IntSize(bounds.outHeight, bounds.outWidth) else IntSize(bounds.outWidth, bounds.outHeight)
            preview to size
        }
    }
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var ratioIndex by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableFloatStateOf(0f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) }
    var right by rememberSaveable { mutableFloatStateOf(1f) }
    var bottom by rememberSaveable { mutableFloatStateOf(1f) }

    val source = loaded
    // The preview turned and mirrored as the result will be; the frame is in this picture's fractions.
    val shown = remember(source, rotation, flip) {
        source?.first?.let { ImageToolsEngine.applyCrop(it, ImageCropSpec(rotation = rotation, flipHorizontal = flip)) }
    }
    val fullSize = source?.second?.let { if (rotation % 180 == 0) it else IntSize(it.height, it.width) }

    /** Width over height of the frame, in fractions of the shown picture; null when free. */
    fun lockedRatio(): Float? {
        val pic = shown ?: return null
        val ratio = CROP_RATIOS[ratioIndex].first ?: return null
        val pixels = if (ratio == 0f) pic.width.toFloat() / pic.height else ratio
        return pixels * pic.height / pic.width
    }

    /** The largest frame of the chosen ratio, centred: what picking a ratio or turning the picture starts from. */
    fun resetFrame() {
        val r = lockedRatio()
        if (r == null) {
            left = 0f; top = 0f; right = 1f; bottom = 1f
            return
        }
        var w = 1f
        var h = w / r
        if (h > 1f) { h = 1f; w = h * r }
        left = (1f - w) / 2f; right = left + w
        top = (1f - h) / 2f; bottom = top + h
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(EditorBackground).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = EditorText)
                }
                Text(
                    stringResource(R.string.img_crop_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = EditorText,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(ImageCropSpec(left, top, right, bottom, rotation, flip)) },
                    enabled = shown != null,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(stringResource(R.string.img_crop_done), fontWeight = FontWeight.SemiBold) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val pic = shown
                if (pic == null) {
                    FileLoadingAnimation(size = 96.dp, onDark = true)
                } else {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }
                    val boxH = with(density) { maxHeight.toPx() }
                    val scale = minOf(boxW / pic.width, boxH / pic.height)
                    val imgW = pic.width * scale
                    val imgH = pic.height * scale
                    val origin = Offset((boxW - imgW) / 2f, (boxH - imgH) / 2f)
                    val image = remember(pic) { pic.asImageBitmap() }
                    val handleReach = with(density) { 28.dp.toPx() }
                    val minFraction = 0.06f
                    val frameDescription = stringResource(R.string.img_crop_frame)
                    var dragging by remember { mutableIntStateOf(NONE) }

                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = frameDescription }
                            .pointerInput(pic, ratioIndex, imgW, imgH) {
                                detectDragGestures(
                                    onDragStart = { at ->
                                        val frame = Rect(origin.x + left * imgW, origin.y + top * imgH, origin.x + right * imgW, origin.y + bottom * imgH)
                                        dragging = pickHandle(at, frame, handleReach, edgesAllowed = lockedRatio() == null)
                                    },
                                    onDragEnd = { dragging = NONE },
                                    onDragCancel = { dragging = NONE },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val dx = amount.x / imgW
                                        val dy = amount.y / imgH
                                        val ratio = lockedRatio()
                                        when (dragging) {
                                            MOVE -> {
                                                val w = right - left
                                                val h = bottom - top
                                                val nl = (left + dx).coerceIn(0f, 1f - w)
                                                val nt = (top + dy).coerceIn(0f, 1f - h)
                                                left = nl; right = nl + w; top = nt; bottom = nt + h
                                            }
                                            LEFT -> left = (left + dx).coerceIn(0f, right - minFraction)
                                            RIGHT -> right = (right + dx).coerceIn(left + minFraction, 1f)
                                            TOP -> top = (top + dy).coerceIn(0f, bottom - minFraction)
                                            BOTTOM -> bottom = (bottom + dy).coerceIn(top + minFraction, 1f)
                                            TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT -> {
                                                val movesLeft = dragging == TOP_LEFT || dragging == BOTTOM_LEFT
                                                val movesTop = dragging == TOP_LEFT || dragging == TOP_RIGHT
                                                // The opposite corner stays put.
                                                val anchorX = if (movesLeft) right else left
                                                val anchorY = if (movesTop) bottom else top
                                                val cornerX = (if (movesLeft) left else right) + dx
                                                val cornerY = (if (movesTop) top else bottom) + dy
                                                var w = abs(cornerX - anchorX).coerceAtLeast(minFraction)
                                                var h = abs(cornerY - anchorY).coerceAtLeast(minFraction)
                                                val roomX = if (movesLeft) anchorX else 1f - anchorX
                                                val roomY = if (movesTop) anchorY else 1f - anchorY
                                                w = w.coerceAtMost(roomX)
                                                h = h.coerceAtMost(roomY)
                                                if (ratio != null) {
                                                    // Follows the larger movement, then shrinks both to fit the picture.
                                                    if (w / ratio > h) h = w / ratio else w = h * ratio
                                                    if (w > roomX) { w = roomX; h = w / ratio }
                                                    if (h > roomY) { h = roomY; w = h * ratio }
                                                }
                                                if (movesLeft) left = anchorX - w else right = anchorX + w
                                                if (movesTop) top = anchorY - h else bottom = anchorY + h
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                            dstSize = IntSize(imgW.roundToInt(), imgH.roundToInt())
                        )
                        val frame = Rect(origin.x + left * imgW, origin.y + top * imgH, origin.x + right * imgW, origin.y + bottom * imgH)
                        val shade = Color.Black.copy(alpha = 0.58f)
                        // Everything outside the frame is dimmed.
                        drawRect(shade, Offset(origin.x, origin.y), Size(imgW, frame.top - origin.y))
                        drawRect(shade, Offset(origin.x, frame.bottom), Size(imgW, origin.y + imgH - frame.bottom))
                        drawRect(shade, Offset(origin.x, frame.top), Size(frame.left - origin.x, frame.height))
                        drawRect(shade, Offset(frame.right, frame.top), Size(origin.x + imgW - frame.right, frame.height))
                        // Rule of thirds, stronger while dragging.
                        val gridAlpha = if (dragging != NONE) 0.7f else 0.35f
                        for (i in 1..2) {
                            val x = frame.left + frame.width * i / 3f
                            val y = frame.top + frame.height * i / 3f
                            drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = gridAlpha), Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
                        }
                        drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))
                        // L-shaped corner handles, and short bars on the edges when they can be dragged.
                        val arm = minOf(22.dp.toPx(), frame.width / 3f, frame.height / 3f)
                        val thick = 4.dp.toPx()
                        listOf(
                            Triple(frame.topLeft, 1f, 1f), Triple(frame.topRight, -1f, 1f),
                            Triple(frame.bottomLeft, 1f, -1f), Triple(frame.bottomRight, -1f, -1f)
                        ).forEach { (corner, sx, sy) ->
                            drawLine(Color.White, corner, Offset(corner.x + sx * arm, corner.y), thick, StrokeCap.Round)
                            drawLine(Color.White, corner, Offset(corner.x, corner.y + sy * arm), thick, StrokeCap.Round)
                        }
                        if (lockedRatio() == null) {
                            val bar = minOf(18.dp.toPx(), frame.width / 4f, frame.height / 4f)
                            val c = frame.center
                            drawLine(Color.White, Offset(c.x - bar, frame.top), Offset(c.x + bar, frame.top), thick, StrokeCap.Round)
                            drawLine(Color.White, Offset(c.x - bar, frame.bottom), Offset(c.x + bar, frame.bottom), thick, StrokeCap.Round)
                            drawLine(Color.White, Offset(frame.left, c.y - bar), Offset(frame.left, c.y + bar), thick, StrokeCap.Round)
                            drawLine(Color.White, Offset(frame.right, c.y - bar), Offset(frame.right, c.y + bar), thick, StrokeCap.Round)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (fullSize != null) {
                    Text(
                        stringResource(
                            R.string.img_tool_dims,
                            ((right - left) * fullSize.width).roundToInt().coerceAtLeast(1),
                            ((bottom - top) * fullSize.height).roundToInt().coerceAtLeast(1)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = EditorText.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.size(8.dp))
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(CROP_RATIOS.indices.toList()) { index ->
                        FilterChip(
                            selected = ratioIndex == index,
                            onClick = { ratioIndex = index; resetFrame() },
                            label = { Text(stringResource(CROP_RATIOS[index].second)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = EditorText,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                Spacer(Modifier.size(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.img_crop_rotate)) {
                        rotation = (rotation + 90) % 360
                        resetFrame()
                    }
                    EditorAction(Icons.Default.Flip, stringResource(R.string.img_crop_flip)) { flip = !flip }
                    EditorAction(Icons.Default.Restore, stringResource(R.string.img_crop_reset)) {
                        rotation = 0; flip = false; ratioIndex = 0
                        left = 0f; top = 0f; right = 1f; bottom = 1f
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = EditorText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = EditorText)
    }
}

private const val NONE = 0
private const val MOVE = 1
private const val LEFT = 2
private const val RIGHT = 3
private const val TOP = 4
private const val BOTTOM = 5
private const val TOP_LEFT = 6
private const val TOP_RIGHT = 7
private const val BOTTOM_LEFT = 8
private const val BOTTOM_RIGHT = 9

/** Which part of the frame a drag starting at [at] takes: a corner first, then an edge, then the inside to move it. */
private fun pickHandle(at: Offset, frame: Rect, reach: Float, edgesAllowed: Boolean): Int {
    fun near(p: Offset) = abs(at.x - p.x) <= reach && abs(at.y - p.y) <= reach
    return when {
        near(frame.topLeft) -> TOP_LEFT
        near(frame.topRight) -> TOP_RIGHT
        near(frame.bottomLeft) -> BOTTOM_LEFT
        near(frame.bottomRight) -> BOTTOM_RIGHT
        edgesAllowed && abs(at.x - frame.left) <= reach && at.y in frame.top..frame.bottom -> LEFT
        edgesAllowed && abs(at.x - frame.right) <= reach && at.y in frame.top..frame.bottom -> RIGHT
        edgesAllowed && abs(at.y - frame.top) <= reach && at.x in frame.left..frame.right -> TOP
        edgesAllowed && abs(at.y - frame.bottom) <= reach && at.x in frame.left..frame.right -> BOTTOM
        frame.contains(at) -> MOVE
        else -> NONE
    }
}
