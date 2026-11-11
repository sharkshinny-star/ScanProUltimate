package com.example.scanpro.feature.tools

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.ToolResultFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

private fun iconFor(extension: String): ImageVector = when (extension.lowercase()) {
    "zip" -> Icons.Default.FolderZip
    "txt", "docx", "xlsx", "pptx" -> Icons.Default.Description
    in IMAGE_EXTENSIONS -> Icons.Default.Image
    else -> Icons.Default.PictureAsPdf
}

/** Small first-page and picture thumbnails, kept while the app runs so scrolling a list does not render them again. */
private object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/** Why a thumbnail could not be drawn: a protected PDF shows a lock, anything else its type icon. */
private sealed interface Thumb {
    class Picture(val bitmap: Bitmap) : Thumb
    class Symbol(val icon: ImageVector) : Thumb
}

private fun openDescriptor(context: Context, source: Any): ParcelFileDescriptor? = when (source) {
    is File -> ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
    is Uri -> if (source.scheme == "file") ParcelFileDescriptor.open(File(source.path.orEmpty()), ParcelFileDescriptor.MODE_READ_ONLY)
        else context.contentResolver.openFileDescriptor(source, "r")
    else -> null
}

/** First page of a PDF or the picture itself, [heightPx] tall. Reads disk: call off the main thread. */
private fun loadThumb(context: Context, source: Any, extension: String, heightPx: Int): Thumb = runCatching<Thumb> {
    when (extension.lowercase()) {
        "pdf" -> try {
            openDescriptor(context, source)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@use Thumb.Symbol(Icons.Default.PictureAsPdf)
                    renderer.openPage(0).use { page ->
                        val scale = heightPx.toFloat() / page.height
                        val bitmap = createBitmap((page.width * scale).toInt().coerceAtLeast(1), heightPx)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Thumb.Picture(bitmap)
                    }
                }
            } ?: Thumb.Symbol(Icons.Default.PictureAsPdf)
        } catch (_: SecurityException) {
            Thumb.Symbol(Icons.Default.Lock)
        }
        in IMAGE_EXTENSIONS -> openDescriptor(context, source)?.use { pfd ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.let { Thumb.Picture(it) }
        } ?: Thumb.Symbol(iconFor(extension))
        else -> Thumb.Symbol(iconFor(extension))
    }
}.getOrElse { Thumb.Symbol(iconFor(extension)) }

/**
 * A portrait thumbnail of a file, the way iLovePDF lists files: the PDF's first page or the picture, a type icon for
 * anything else. [cacheKey] must change when the file does (include its modified time).
 */
@Composable
internal fun FileThumbnail(
    source: Any,
    extension: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
    /** How tall to render the page: Home's cards (F140) are 175 dp, the list rows 60 dp. */
    renderHeight: androidx.compose.ui.unit.Dp = 64.dp
) {
    val context = LocalContext.current
    val heightPx = with(LocalDensity.current) { renderHeight.roundToPx() }
    val thumb by produceState<Thumb?>(ThumbCache.get(cacheKey)?.let { Thumb.Picture(it) }, cacheKey) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            loadThumb(context, source, extension, heightPx).also { if (it is Thumb.Picture) ThumbCache.put(cacheKey, it.bitmap) }
        }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .size(width = 46.dp, height = 60.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        when (val t = thumb) {
            is Thumb.Picture -> Image(
                bitmap = t.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            )
            is Thumb.Symbol -> Box(contentAlignment = Alignment.Center) {
                Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            null -> Unit
        }
    }
}

/** "PDF · 1.2 kB · 12 Nov, 12:25 am": what kind of file, how big, and when. */
internal fun fileDetails(context: Context, extension: String, sizeBytes: Long, timeMillis: Long): String =
    listOfNotNull(
        extension.uppercase().takeIf { it.isNotBlank() },
        sizeBytes.takeIf { it > 0 }?.let { formatBytes(context, it) },
        timeMillis.takeIf { it > 0 }?.let {
            DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME)
        }
    ).joinToString(" · ")

/** Size of a picked file, from its provider. Reads the provider: call off the main thread. */
internal fun sizeOf(context: Context, uri: Uri): Long = runCatching {
    if (uri.scheme == "file") File(uri.path.orEmpty()).length()
    else context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
    } ?: 0L
}.getOrDefault(0L)

/** The heading of a file section on the Tools screen, with an optional action on the right. */
@Composable
internal fun FileSectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** The shared row layout: thumbnail, name over details, and the overflow button with its menu. */
@Composable
internal fun FileRow(
    name: String,
    details: String,
    thumbnail: @Composable () -> Unit,
    onOpen: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tools_recent_open), onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        thumbnail()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "${stringResource(R.string.common_more)}: $name")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
        }
    }
}

/**
 * One file a tool made. Tap opens it; the menu shares it, saves a copy to the phone, sends it into another tool, or
 * deletes it (after asking: it is the only copy unless it was saved or shared).
 */
@Composable
internal fun ToolResultRow(
    item: ToolResultFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onUseWith: (PdfToolCard) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val extension = item.file.extension
    var toolsOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val nextTools = remember(extension) { nextToolsFor(extension, justUsed = null) }
    Box {
        FileRow(
            name = item.name,
            details = fileDetails(context, extension, item.sizeBytes, item.modifiedAt),
            thumbnail = { FileThumbnail(item.file, extension, "${item.file.path}|${item.modifiedAt}") },
            onOpen = onOpen
        ) { close ->
            DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_open)) }, onClick = { close(); onOpen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.common_share)) }, onClick = { close(); onShare() })
            DropdownMenuItem(text = { Text(stringResource(R.string.tools_results_save)) }, onClick = { close(); onSave() })
            if (nextTools.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_use_with_tool)) }, onClick = { close(); toolsOpen = true })
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tools_results_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { close(); deleting = true }
            )
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                nextTools.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text(stringResource(tool.title)) },
                        leadingIcon = { Icon(tool.icon, contentDescription = null) },
                        onClick = { toolsOpen = false; onUseWith(tool) }
                    )
                }
            }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tools_results_delete_title)) },
            text = { Text(stringResource(R.string.tools_results_delete_body, item.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; onDelete() }) {
                    Text(stringResource(R.string.tools_results_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
