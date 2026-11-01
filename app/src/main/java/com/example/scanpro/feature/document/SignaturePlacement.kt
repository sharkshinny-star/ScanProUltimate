package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a signature goes on its page, as fractions of the page (the box the stamp is fitted into). */
data class SignaturePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

private const val MIN_WIDTH = 0.08f
private const val MAX_WIDTH = 0.95f

/**
 * Full-screen step after drawing or choosing a signature: the page is shown with the signature on it, the user
 * drags it into place and resizes it (pinch, or the corner handle), then taps Save. Nothing is written until Save.
 */
@Composable
fun SignaturePlacementDialog(
    loadPage: suspend () -> File,
    loadSignature: suspend () -> Bitmap?,
    onDismiss: () -> Unit,
    onSave: (SignaturePlacement) -> Unit
) {
    val page by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeScaled(loadPage().absolutePath, 1600) }.getOrNull() }
    }
    val signature by produceState<Bitmap?>(null) { value = withContext(Dispatchers.IO) { runCatching { loadSignature() }.getOrNull() } }

    // Centre and width as fractions of the page; the height follows from the signature's proportions.
    var centerX by rememberSaveable { mutableFloatStateOf(0.73f) }
    var centerY by rememberSaveable { mutableFloatStateOf(0.84f) }
    var width by rememberSaveable { mutableFloatStateOf(0.34f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D14))
                .safeDrawingPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.common_cancel), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.doc_place_signature), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.doc_place_signature_hint), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                val pageImage = page
                val sig = signature
                if (pageImage == null || sig == null) {
                    CircularProgressIndicator()
                    return@BoxWithConstraints
                }
                val density = LocalDensity.current
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()
                val scale = minOf(boxW / pageImage.width, boxH / pageImage.height)
                val shownW = pageImage.width * scale
                val shownH = pageImage.height * scale
                // Height of the signature box as a fraction of the page, for a given width fraction.
                val heightFor = { w: Float -> w * (sig.height.toFloat() / sig.width) * (pageImage.width.toFloat() / pageImage.height) }
                fun clampAll() {
                    width = width.coerceIn(MIN_WIDTH, minOf(MAX_WIDTH, MAX_WIDTH / heightFor(1f).coerceAtLeast(0.01f)))
                    val h = heightFor(width)
                    centerX = centerX.coerceIn(width / 2f, 1f - width / 2f)
                    centerY = centerY.coerceIn(h / 2f, 1f - h / 2f)
                }

                Box(
                    Modifier
                        .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                        .pointerInput(shownW, shownH) {
                            // Drag anywhere to move; pinch to resize.
                            detectTransformGestures { _, pan, zoom, _ ->
                                width *= zoom
                                centerX += pan.x / shownW
                                centerY += pan.y / shownH
                                clampAll()
                            }
                        }
                ) {
                    val sigH = heightFor(width)
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(pageImage.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                        val left = (centerX - width / 2f) * size.width
                        val top = (centerY - sigH / 2f) * size.height
                        val w = width * size.width
                        val h = sigH * size.height
                        drawImage(sig.asImageBitmap(), dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)))
                        drawRect(
                            Color(0xFF2FD6A6),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                        )
                    }
                    // Corner handle: one-finger resize, keeping the top-left corner where it is.
                    val handle = 28.dp
                    val handlePx = with(density) { handle.toPx() }
                    val rightPx = (centerX + width / 2f) * shownW
                    val bottomPx = (centerY + sigH / 2f) * shownH
                    val resizeLabel = stringResource(R.string.doc_signature_resize)
                    Box(
                        Modifier
                            .offset { IntOffset((rightPx - handlePx / 2f).roundToInt(), (bottomPx - handlePx / 2f).roundToInt()) }
                            .size(handle)
                            .background(Color(0xFF2FD6A6), CircleShape)
                            .semantics { contentDescription = resizeLabel }
                            .pointerInput(shownW, shownH) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val left = centerX - width / 2f
                                    val newWidth = (width + drag.x / shownW).coerceIn(MIN_WIDTH, MAX_WIDTH)
                                    val oldTop = centerY - heightFor(width) / 2f
                                    width = newWidth
                                    centerX = left + newWidth / 2f
                                    centerY = oldTop + heightFor(newWidth) / 2f
                                    clampAll()
                                }
                            }
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = page != null && signature != null,
                    onClick = {
                        val h = signature?.let { s -> page?.let { p -> width * (s.height.toFloat() / s.width) * (p.width.toFloat() / p.height) } } ?: return@Button
                        onSave(SignaturePlacement(x = centerX - width / 2f, y = centerY - h / 2f, width = width, height = h))
                    }
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}
