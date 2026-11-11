package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.imaging.PdfToolResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the preview slot of the result shows when the file cannot be drawn (a zip, a text file, a locked PDF). */
private sealed interface ResultPreview {
    class Thumb(val bitmap: Bitmap) : ResultPreview
    class Placeholder(val icon: ImageVector) : ResultPreview
}

private fun placeholderFor(file: File): ImageVector = when (file.extension.lowercase()) {
    "zip" -> Icons.Default.FolderZip
    "txt", "docx", "xlsx", "pptx" -> Icons.Default.Description
    "png", "jpg", "jpeg", "webp" -> Icons.Default.Image
    else -> Icons.Default.PictureAsPdf
}

/** The first page of a PDF, or the image itself, scaled to [heightPx] tall. Off the main thread. */
private fun loadResultPreview(file: File, heightPx: Int): ResultPreview = runCatching<ResultPreview> {
    when (file.extension.lowercase()) {
        "pdf" -> try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@use ResultPreview.Placeholder(Icons.Default.PictureAsPdf)
                    renderer.openPage(0).use { page ->
                        val scale = heightPx.toFloat() / page.height
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ResultPreview.Thumb(bitmap)
                    }
                }
            }
        } catch (_: SecurityException) {
            // A password-protected PDF cannot be drawn without the password: say so with a lock.
            ResultPreview.Placeholder(Icons.Default.Lock)
        }
        "png", "jpg", "jpeg", "webp" -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap != null) ResultPreview.Thumb(bitmap) else ResultPreview.Placeholder(placeholderFor(file))
        }
        else -> ResultPreview.Placeholder(placeholderFor(file))
    }
}.getOrElse { ResultPreview.Placeholder(placeholderFor(file)) }

/**
 * The screen every tool ends on. It shows what was made (a real thumbnail, not a generic icon), what it is called and
 * how big it is, the tool's own one-line message, and the three things people do next: open it, share it, save it.
 */
@Composable
internal fun ResultDialog(
    result: PdfToolResult,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onSaveAs: () -> Unit
) {
    val context = LocalContext.current
    val file = result.outputFile
    val mime = mimeFor(file)
    val saveLabel = stringResource(
        when (mime) {
            "application/zip" -> R.string.tools_result_save_zip_cd
            "application/pdf" -> R.string.tools_result_save_pdf_cd
            "text/plain" -> R.string.tools_result_save_text_cd
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> R.string.tools_result_save_file_cd
            else -> R.string.tools_result_save_image_cd
        }
    )
    val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 156.dp.roundToPx() }
    val preview by produceState<ResultPreview?>(null, file) { value = withContext(Dispatchers.IO) { loadResultPreview(file, heightPx) } }

    val isCompression = result.comparesSize && result.originalSizeBytes > 0 && result.outputSizeBytes > 0
    val savedBytes = if (isCompression) (result.originalSizeBytes - result.outputSizeBytes).coerceAtLeast(0L) else 0L
    val savingsPct = if (isCompression) ((savedBytes.toDouble() / result.originalSizeBytes.toDouble()) * 100).toInt().coerceIn(0, 99) else 0

    // The tick springs in once, so completion feels like an event rather than a screen change.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val tickScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tick"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                        modifier = Modifier.height(156.dp).semantics { contentDescription = file.name }
                    ) {
                        when (val p = preview) {
                            is ResultPreview.Thumb -> Image(
                                bitmap = p.bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(156.dp)
                            )
                            is ResultPreview.Placeholder -> Box(Modifier.size(width = 118.dp, height = 156.dp), contentAlignment = Alignment.Center) {
                                Icon(p.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                            }
                            null -> Box(Modifier.size(width = 118.dp, height = 156.dp))
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp).size(30.dp).scale(tickScale)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                    // Drawn over the thumbnail, so the specks are seen leaving it rather than hidden behind it.
                    SuccessBurst(Modifier.matchParentSize())
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    file.extension.uppercase() + " · " + formatBytes(context, result.outputSizeBytes.takeIf { it > 0 } ?: file.length()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.message.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (isCompression) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SizeColumn(stringResource(R.string.tools_result_original_label), formatBytes(context, result.originalSizeBytes), highlight = false)
                            Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            SizeColumn(stringResource(R.string.tools_result_compressed_label), formatBytes(context, result.outputSizeBytes), highlight = true)
                        }
                    }
                    if (savingsPct > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                stringResource(R.string.tools_result_saved_badge, savingsPct),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                FilledTonalButton(onClick = onView, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (file.extension.equals("pdf", ignoreCase = true)) R.string.tools_result_preview_pdf else R.string.tools_result_open),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { shareFile(context, file.absolutePath, mime, file.name) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_share))
                    }
                    OutlinedButton(
                        onClick = onSaveAs,
                        modifier = Modifier.weight(1f).semantics { contentDescription = saveLabel },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_save), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.common_done))
            }
        }
    )
}

@Composable
private fun SizeColumn(label: String, size: String, highlight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            size,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** One speck of the success burst: its direction, how far it flies, size, shape and colour. */
private class Speck(val angle: Float, val reach: Float, val sizeDp: Float, val shape: Int, val colour: Int, val spin: Float)

/**
 * A short burst of brand-coloured specks from the result, played once when a tool finishes (a celebration, the way
 * iLovePDF marks a finished task, drawn in ScanPro's own colours). Under a second, behind nothing important, and not
 * played at all when the phone's animations are turned off (Developer options or the accessibility setting).
 */
@Composable
private fun SuccessBurst(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    if (!animationsOn) return
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, androidx.compose.animation.core.tween(950, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    val colours = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        androidx.compose.ui.graphics.Color(0xFF2BD4A4),
        androidx.compose.ui.graphics.Color(0xFFF59E0B),
        MaterialTheme.colorScheme.secondary
    )
    val specks = remember {
        val random = java.util.Random(20261111L)
        List(24) { i ->
            Speck(
                angle = (i / 24f) * (2f * Math.PI.toFloat()) + random.nextFloat() * 0.35f,
                reach = 0.55f + random.nextFloat() * 0.5f,
                sizeDp = 4f + random.nextFloat() * 4f,
                shape = i % 3,
                colour = i % 5,
                spin = random.nextFloat() * 360f
            )
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val distance = size.maxDimension * 1.05f
        // Stays bright while the specks fly out, then fades quickly at the end.
        val fade = 1f - t * t
        specks.forEach { speck ->
            val travel = distance * speck.reach * t
            val x = center.x + kotlin.math.cos(speck.angle) * travel
            // A little gravity, so the specks fall away rather than freeze in a ring.
            val y = center.y + kotlin.math.sin(speck.angle) * travel + distance * 0.25f * t * t
            val px = speck.sizeDp * density
            val colour = colours[speck.colour].copy(alpha = fade)
            val at = androidx.compose.ui.geometry.Offset(x, y)
            rotate(speck.spin + 300f * t, at) {
                when (speck.shape) {
                    0 -> drawCircle(colour, px / 2f, at)
                    1 -> drawRect(colour, androidx.compose.ui.geometry.Offset(x - px / 2f, y - px / 4f), androidx.compose.ui.geometry.Size(px, px / 2f))
                    else -> drawRoundRect(
                        colour, androidx.compose.ui.geometry.Offset(x - px / 2f, y - px / 2f), androidx.compose.ui.geometry.Size(px, px),
                        androidx.compose.ui.geometry.CornerRadius(px / 4f)
                    )
                }
            }
        }
    }
}
