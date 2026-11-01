package com.example.scanpro.feature.document

import androidx.compose.material3.minimumInteractiveComponentSize
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pen colours: ink black, ballpoint blue and correction red. */
private enum class PenColour(val labelRes: Int, val color: Color) {
    BLACK(R.string.doc_pen_black, Color(0xFF1B1B1F)),
    BLUE(R.string.doc_pen_blue, Color(0xFF1A49C9)),
    RED(R.string.doc_pen_red, Color(0xFFD32F2F))
}

/** Pen widths as a fraction of the page width. */
private enum class PenWidth(val labelRes: Int, val size: Float) {
    FINE(R.string.doc_brush_fine, 0.004f),
    MEDIUM(R.string.doc_brush_medium, 0.008f),
    BOLD(R.string.doc_pen_bold, 0.014f)
}

/** One finished stroke: points as fractions of the page, with the pen it was drawn with. */
data class InkStroke(val points: List<NormalizedPoint>, val color: Int, val width: Float)

/**
 * Freehand pen: draw on the page with a finger in one of three colours and widths, then Add. Each colour and width
 * becomes one mark-up box (see [inkAnnotations]), so it can be moved, resized or removed later like any other mark-up.
 */
@Composable
fun PenDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onApply: (List<PageAnnotation>) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(imagePath, 1600) }
    }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var current by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var colour by remember { mutableStateOf(PenColour.BLUE) }
    var width by remember { mutableStateOf(PenWidth.MEDIUM) }
    val pagePreview = stringResource(R.string.a11y_page_preview)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close)) }
                    Text(stringResource(R.string.doc_pen), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.doc_undo_last_stroke))
                    }
                }
                Text(
                    stringResource(R.string.doc_pen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val image = bitmap
                    if (image == null) {
                        CircularProgressIndicator()
                    } else {
                        Canvas(
                            Modifier
                                .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                .semantics { contentDescription = pagePreview }
                                .pointerInput(Unit) {
                                    fun at(x: Float, y: Float) = NormalizedPoint(x / size.width, y / size.height).clamped()
                                    detectTapGestures { strokes += InkStroke(listOf(at(it.x, it.y)), colour.color.toArgb(), width.size) }
                                }
                                .pointerInput(Unit) {
                                    fun at(x: Float, y: Float) = NormalizedPoint(x / size.width, y / size.height).clamped()
                                    detectDragGestures(
                                        onDragStart = { current = listOf(at(it.x, it.y)) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            current = current + at(change.position.x, change.position.y)
                                        },
                                        onDragEnd = {
                                            if (current.isNotEmpty()) strokes += InkStroke(thinPoints(current), colour.color.toArgb(), width.size)
                                            current = emptyList()
                                        },
                                        onDragCancel = { current = emptyList() }
                                    )
                                }
                        ) {
                            drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            fun draw(points: List<NormalizedPoint>, ink: Color, w: Float) {
                                val px = points.map { Offset(it.x * size.width, it.y * size.height) }
                                if (px.size == 1) {
                                    drawCircle(ink, radius = w / 2f, center = px[0])
                                } else {
                                    val path = Path().apply {
                                        moveTo(px[0].x, px[0].y)
                                        px.drop(1).forEach { lineTo(it.x, it.y) }
                                    }
                                    drawPath(path, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                            strokes.forEach { draw(it.points, Color(it.color), it.width * size.width) }
                            if (current.isNotEmpty()) draw(current, colour.color, width.size * size.width)
                        }
                    }
                }
                // Colour swatches, then widths.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PenColour.entries.forEach { option ->
                        val label = stringResource(option.labelRes)
                        Box(
                            Modifier
                                // The swatch stays 36 dp; the 48 dp minimum is invisible touch area around it.
                                .minimumInteractiveComponentSize()
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                // A thin ring keeps the black swatch visible on a dark surface.
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .border(3.dp, if (colour == option) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .semantics { contentDescription = label; selected = colour == option; role = Role.RadioButton }
                                .pointerInput(option) { detectTapGestures { colour = option } }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    PenWidth.entries.forEach { option ->
                        FilterChip(
                            selected = width == option,
                            onClick = { width = option },
                            label = { Text(stringResource(option.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = CircleShape
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Button(
                        enabled = strokes.isNotEmpty() && bitmap != null,
                        onClick = {
                            val image = bitmap ?: return@Button
                            onApply(inkAnnotations(strokes.toList(), image.width.toFloat() / image.height.toFloat()))
                        }
                    ) { Text(stringResource(R.string.doc_add)) }
                }
            }
        }
    }
}

/**
 * Turns pen strokes (points as fractions of the page) into INK mark-up: one box per colour and width, just large
 * enough for its strokes and the pen's width, with the points stored as fractions of that box so moving or resizing
 * the box carries the ink along. [pageAspect] is width / height, so the vertical margin matches the horizontal one.
 */
internal fun inkAnnotations(strokes: List<InkStroke>, pageAspect: Float): List<PageAnnotation> =
    strokes.groupBy { it.color to it.width }.map { (pen, group) ->
        val (color, width) = pen
        val padX = width / 2f
        val padY = width / 2f * pageAspect
        val all = group.flatMap { it.points }
        val left = (all.minOf { it.x } - padX).coerceIn(0f, 1f)
        val top = (all.minOf { it.y } - padY).coerceIn(0f, 1f)
        val right = (all.maxOf { it.x } + padX).coerceIn(0f, 1f)
        val bottom = (all.maxOf { it.y } + padY).coerceIn(0f, 1f)
        val w = (right - left).coerceAtLeast(0.01f)
        val h = (bottom - top).coerceAtLeast(0.01f)
        PageAnnotation(
            type = AnnotationType.INK,
            x = left,
            y = top,
            width = w,
            height = h,
            color = color,
            brushSize = width,
            strokes = group.map { stroke -> stroke.points.map { NormalizedPoint((it.x - left) / w, (it.y - top) / h) } }
        )
    }

/** Drops points closer than about a third of a percent of the page to the previous one; finger drags oversample. */
private fun thinPoints(points: List<NormalizedPoint>): List<NormalizedPoint> {
    if (points.size < 3) return points
    val out = mutableListOf(points.first())
    points.drop(1).forEach { p ->
        val last = out.last()
        if (kotlin.math.hypot(p.x - last.x, p.y - last.y) >= 0.003f) out += p
    }
    if (out.last() != points.last()) out += points.last()
    return out
}
