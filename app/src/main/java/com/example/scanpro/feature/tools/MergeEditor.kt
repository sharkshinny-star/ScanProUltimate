package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the Merge editor shows about one picked PDF; [pages] is null when the file is password protected or unreadable. */
private class MergeFileInfo(val name: String, val sizeBytes: Long, val pages: Int?, val thumbnail: Bitmap?)

private fun loadMergeInfo(context: android.content.Context, uri: Uri, name: String, thumbHeightPx: Int): MergeFileInfo {
    var pages: Int? = null
    var thumb: Bitmap? = null
    var size = 0L
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            size = pfd.statSize.coerceAtLeast(0L)
            // A password-protected PDF makes PdfRenderer throw SecurityException: shown as locked, still mergeable.
            runCatching {
                PdfRenderer(pfd).use { renderer ->
                    pages = renderer.pageCount
                    if (renderer.pageCount > 0) renderer.openPage(0).use { page ->
                        val scale = thumbHeightPx.toFloat() / page.height
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), thumbHeightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumb = bitmap
                    }
                }
            }
        }
    }
    return MergeFileInfo(name, size, pages, thumb)
}

/**
 * Arranges the PDFs before they are merged: each file is a card with its first page, page count and size; press and
 * hold a card to drag it up or down, remove it with the cross, or add more files. The first card comes first in the
 * merged PDF. [onConfirm] gets the files in the order shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MergeEditor(
    initialUris: List<Uri>,
    displayName: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onPicked: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val order = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Uri>, String>(
            save = { list -> list.map(Uri::toString) },
            restore = { saved -> mutableStateListOf(*saved.map(Uri::parse).toTypedArray()) }
        )
    ) { mutableStateListOf<Uri>().apply { addAll(initialUris) } }
    val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val fresh = uris.filter { it !in order }
        if (fresh.isNotEmpty()) { order.addAll(fresh); onPicked(fresh) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.tools_order_merge_title), style = MaterialTheme.typography.titleLarge)
                                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.tools_merge_files_count, order.size, order.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } },
                        actions = {
                            IconButton(onClick = { addFiles.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tools_merge_add_files))
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Button(
                            onClick = { onConfirm(order.toList()) },
                            enabled = order.size >= 2,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                        ) {
                            Text(
                                if (order.size >= 2) stringResource(R.string.tools_merge_action, order.size) else stringResource(R.string.tools_merge_need_two),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        stringResource(R.string.tools_merge_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    ReorderablePageGrid(
                        items = order.toList(),
                        key = { it.toString() },
                        onMove = { from, to -> if (from in order.indices) order.add(to.coerceIn(0, order.lastIndex), order.removeAt(from)) },
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { uri, index, dragging ->
                        MergeFileCard(
                            uri = uri,
                            position = index + 1,
                            lifted = dragging,
                            displayName = displayName,
                            onRemove = { order.remove(uri) },
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeFileCard(
    uri: Uri,
    position: Int,
    lifted: Boolean,
    displayName: (Uri) -> String,
    onRemove: () -> Unit,
    context: android.content.Context
) {
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.roundToPx() }
    val info by produceState<MergeFileInfo?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadMergeInfo(context, uri, displayName(uri), thumbPx) }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (lifted) 6.dp else 0.dp,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
            Box(
                Modifier.size(width = 64.dp, height = 88.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val loaded = info
                when {
                    loaded == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    loaded.thumbnail != null -> Image(loaded.thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    loaded.pages == null -> Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(info?.name ?: stringResource(R.string.tools_file_fallback_name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val loaded = info
                if (loaded != null) {
                    val size = Formatter.formatShortFileSize(context, loaded.sizeBytes)
                    Text(
                        if (loaded.pages == null) stringResource(R.string.tools_merge_meta_locked, size) else androidx.compose.ui.res.pluralStringResource(R.plurals.tools_merge_meta, loaded.pages, loaded.pages, size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tools_merge_remove, info?.name ?: ""))
            }
        }
    }
}
