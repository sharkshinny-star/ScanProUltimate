package com.example.scanpro.feature.tools

import com.example.scanpro.core.concurrent.BackgroundWork
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.example.scanpro.core.designsystem.LoadingShimmer
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.liveRegion
import com.example.scanpro.core.ui.formatBytes
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
import kotlinx.coroutines.withContext

/**
 * What the Merge editor shows about one picked PDF. [pages] is null when it could not be counted: [locked] when the
 * file needs a password (the merge asks for it), otherwise the system renderer could not read it.
 */
private class MergeFileInfo(val name: String, val sizeBytes: Long, val pages: Int?, val thumbnail: Bitmap?, val locked: Boolean = false)

private fun loadMergeInfo(context: android.content.Context, uri: Uri, name: String, thumbHeightPx: Int): MergeFileInfo {
    var pages: Int? = null
    var thumb: Bitmap? = null
    var size = 0L
    var locked = false
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            size = pfd.statSize.coerceAtLeast(0L)
            // A password-protected PDF makes PdfRenderer throw SecurityException: shown as locked, still mergeable.
            try {
                PdfRenderer(pfd).use { renderer ->
                    pages = renderer.pageCount
                    if (renderer.pageCount > 0) renderer.openPage(0).use { page ->
                        val scale = thumbHeightPx.toFloat() / page.height.coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceIn(1, thumbHeightPx * 4), thumbHeightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumb = bitmap
                    }
                }
            } catch (_: SecurityException) {
                locked = true
            } catch (_: Exception) {
                // Damaged for the system renderer; PDFBox may still read it when merging.
            }
        }
    }
    // Some providers report no size through the descriptor (-1): ask them directly (F210).
    if (size <= 0L) size = sizeOf(context, uri)
    return MergeFileInfo(name, size, pages, thumb, locked)
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
    ) { mutableStateListOf<Uri>().apply { addAll(initialUris.distinct()) } }
    // Read once per file, off the main thread, and kept while the editor is open: the cards and the totals both use it
    // (F210; each card used to read its file again whenever it scrolled back into view).
    val infos = remember { mutableStateMapOf<Uri, MergeFileInfo>() }
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.roundToPx() }
    val orderNow = order.toList()
    LaunchedEffect(orderNow) {
        for (uri in orderNow) {
            if (uri !in infos) infos[uri] = withContext(BackgroundWork.thumbnails) { loadMergeInfo(context, uri, displayName(uri), thumbPx) }
        }
    }
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
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            MergeSummary(orderNow, infos)
                            Button(
                                onClick = { onConfirm(order.toList()) },
                                enabled = order.size >= 2,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Text(
                                    if (order.size >= 2) stringResource(R.string.tools_merge_action, order.size) else stringResource(R.string.tools_merge_need_two),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { ToolDemoCard("merge") }
                    ReorderHintBanner(
                        stringResource(R.string.tools_merge_hint),
                        com.example.scanpro.core.designsystem.DragHintLayout.List,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    ReorderablePageGrid(
                        items = order.toList(),
                        key = { it.toString() },
                        onMove = { from, to -> if (from in order.indices) order.add(to.coerceIn(0, order.lastIndex), order.removeAt(from)) },
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { uri, index, dragging ->
                        MergeFileCard(
                            info = infos[uri],
                            position = index + 1,
                            lifted = dragging,
                            onRemove = { order.remove(uri) },
                            onMoveUp = if (index > 0) ({ order.add(index - 1, order.removeAt(index)) }) else null,
                            onMoveDown = if (index < order.lastIndex) ({ order.add(index + 1, order.removeAt(index)) }) else null,
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
    info: MergeFileInfo?,
    position: Int,
    lifted: Boolean,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    context: android.content.Context
) {
    // Dragging is the only visible way to reorder; TalkBack and switch users get Move up / Move down actions.
    val moveUpLabel = stringResource(R.string.tools_order_move_up)
    val moveDownLabel = stringResource(R.string.tools_order_move_down)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (lifted) 6.dp else 0.dp,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics {
            customActions = buildList {
                onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel) { it(); true }) }
                onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel) { it(); true }) }
            }
        }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
            Box(
                Modifier.size(width = 64.dp, height = 88.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val loaded = info
                when {
                    loaded == null -> LoadingShimmer(Modifier.matchParentSize())
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
                        when {
                            loaded.pages != null -> androidx.compose.ui.res.pluralStringResource(R.plurals.tools_merge_meta, loaded.pages, loaded.pages, size)
                            loaded.locked -> stringResource(R.string.tools_merge_meta_locked, size)
                            else -> stringResource(R.string.pages_merge_unreadable, size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (loaded.pages == null && !loaded.locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * What the merge will make, above its button: one PDF of so many pages and about so big (the sizes added up), or
 * "Counting pages" while files are still being read. Files that need a password are counted when the merge asks for it.
 */
@Composable
private fun MergeSummary(order: List<Uri>, infos: Map<Uri, MergeFileInfo>) {
    if (order.size < 2) return
    val context = LocalContext.current
    val known = order.mapNotNull { infos[it] }
    val text = if (known.size < order.size) {
        stringResource(R.string.pages_merge_counting)
    } else {
        val pages = known.sumOf { it.pages ?: 0 }
        val bytes = known.sumOf { it.sizeBytes }
        val pagesText = androidx.compose.ui.res.pluralStringResource(R.plurals.pdf_outcome_pages, pages, pages)
        val unknown = known.count { it.pages == null }
        stringResource(R.string.pages_merge_summary, pagesText, formatBytes(context, bytes)) +
            if (unknown > 0) " " + androidx.compose.ui.res.pluralStringResource(R.plurals.pages_merge_not_counted, unknown, unknown) else ""
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
    )
}
