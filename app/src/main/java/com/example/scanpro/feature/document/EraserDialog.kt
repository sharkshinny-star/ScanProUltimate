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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Brush(val labelRes: Int, val size: Float) {
    SMALL(R.string.doc_brush_fine, 0.015f),
    MEDIUM(R.string.doc_brush_medium, 0.03f),
    LARGE(R.string.doc_brush_wide, 0.06f)
}

/**
 * Eraser: brush over stains, pen marks or handwriting, and on Apply they are filled in from the paper around
 * them. Strokes are kept as fractions of the page, so they land in the same place on the full-size page.
 */
@Composable
fun EraserDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    /** Strokes grouped by brush size (a fraction of the page width). */
    onApply: (Map<Float, List<List<NormalizedPoint>>>) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(imagePath, 1600) }
    }
    // Each finished stroke with the brush it was drawn with; Apply sends one eraser per brush size.
    val strokes = remember { mutableStateListOf<Pair<Brush, List<NormalizedPoint>>>() }
    var current by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var brush by remember { mutableStateOf(Brush.MEDIUM) }
    val ink = Color(0xFFFF5252).copy(alpha = 0.55f)
    val pagePreview = stringResource(R.string.a11y_page_preview)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close)) }
                    Text(stringResource(R.string.doc_eraser), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.doc_undo_last_stroke))
                    }
                }
                Text(
                    stringResource(R.string.doc_eraser_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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
                                    detectTapGestures { strokes += brush to listOf(at(it.x, it.y)) }
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
                                            if (current.isNotEmpty()) strokes += brush to simplify(current)
                                            current = emptyList()
                                        },
                                        onDragCancel = { current = emptyList() }
                                    )
                                }
                        ) {
                            drawImage(image.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            fun draw(points: List<NormalizedPoint>, width: Float) {
                                val px = points.map { androidx.compose.ui.geometry.Offset(it.x * size.width, it.y * size.height) }
                                if (px.size == 1) {
                                    drawCircle(ink, radius = width / 2f, center = px[0])
                                } else {
                                    val path = Path().apply {
                                        moveTo(px[0].x, px[0].y)
                                        px.drop(1).forEach { lineTo(it.x, it.y) }
                                    }
                                    drawPath(path, ink, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                            strokes.forEach { (b, points) -> draw(points, b.size * size.width) }
                            if (current.isNotEmpty()) draw(current, brush.size * size.width)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.doc_brush), style = MaterialTheme.typography.labelLarge)
                    Brush.entries.forEach { option ->
                        FilterChip(selected = brush == option, onClick = { brush = option }, label = { Text(stringResource(option.labelRes)) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = { onApply(strokes.groupBy({ it.first.size }, { it.second })) }
                    ) { Text(stringResource(R.string.doc_erase)) }
                }
            }
        }
    }
}

/** Drops points closer than about half a percent of the page to the previous one; finger drags oversample. */
private fun simplify(points: List<NormalizedPoint>): List<NormalizedPoint> {
    if (points.size < 3) return points
    val out = mutableListOf(points.first())
    points.drop(1).forEach { p ->
        val last = out.last()
        if (kotlin.math.hypot(p.x - last.x, p.y - last.y) >= 0.004f) out += p
    }
    if (out.last() != points.last()) out += points.last()
    return out
}
