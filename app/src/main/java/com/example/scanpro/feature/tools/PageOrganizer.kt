package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.designsystem.FileLoadingState
import com.example.scanpro.core.designsystem.LoadingShimmer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.DarkChrome
import androidx.compose.runtime.produceState
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfPreviewSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ThumbnailPageModel(
    val index: Int,
    val rotation: Int = 0,
    val selected: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPageOrganizerSheet(
    uri: Uri,
    mode: String,
    /** Switches between Arrange, Rotate and Extract; null hides the switch. */
    onModeChange: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    onApply: (pageSpecs: List<PageSpec>, extractedIndices: List<Int>, rotationsMap: Map<Int, Int>) -> Unit,
    /** Organise only: puts pages from another PDF into the pages as arranged now. */
    onInsertPages: ((kept: List<PageSpec>) -> Unit)? = null
) {
    val context = LocalContext.current
    // Extract starts with nothing chosen; Organise starts with every page kept (untick to delete one).
    val defaultSelected = mode != "extract"
    // Page order, rotations and selections survive rotation and window changes; thumbnails are re-rendered on demand.
    val pages = rememberSaveable(
        uri,
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { list -> list.flatMap { listOf(it.index, it.rotation, if (it.selected) 1 else 0) } },
            restore = { flat -> mutableStateListOf<ThumbnailPageModel>().apply { flat.chunked(3).forEach { (i, r, sel) -> add(ThumbnailPageModel(i, r, sel == 1)) } } }
        )
    ) { mutableStateListOf<ThumbnailPageModel>() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<PdfPreviewSession?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // Read here: the loading coroutine below is not a composable scope.
    val protectedError = stringResource(R.string.tools_organizer_protected)
    val openFailedError = stringResource(R.string.tools_organizer_open_failed)
    // Every change is recorded first, so Undo steps back through deletes, moves and rotations.
    val undoStack = remember { mutableStateListOf<List<ThumbnailPageModel>>() }
    fun change(block: () -> Unit) {
        undoStack.add(pages.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
        block()
    }
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        pages.clear()
        pages.addAll(previous)
    }

    androidx.compose.runtime.DisposableEffect(uri) {
        var opened: PdfPreviewSession? = null
        val job = scope.launch {
            try {
                val preview = PdfPreviewSession.open(context, uri)
                if (!isActive) {
                    // The sheet closed while the file was opening; nothing will close this later.
                    preview.close()
                    return@launch
                }
                opened = preview
                session = preview
                if (pages.isEmpty()) pages.addAll((0 until preview.pageCount).map { ThumbnailPageModel(it, selected = defaultSelected) })
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                loadError = protectedError
            } catch (_: Exception) {
                loadError = openFailedError // not the exception's text: it is English and can contain file paths
            }
        }
        onDispose {
            job.cancel()
            opened?.close()
            session = null
        }
    }

    val isLoading = session == null && loadError == null
    val keptCount = pages.count { it.selected }
    var previewPage by remember { mutableStateOf<ThumbnailPageModel?>(null) }
    var actionPage by remember { mutableStateOf<ThumbnailPageModel?>(null) }
    // Extracting every page gives back the same document, so that is not offered: select fewer.
    val extractsEverything = mode == "extract" && pages.isNotEmpty() && keptCount == pages.size

    if (mode == "organize" || mode == "extract") {
        if (isLoading) {
            val titleRes = if (mode == "extract") R.string.tools_organizer_extract_title else R.string.tools_organizer_organize_title
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(titleRes)) },
                text = {
                    FileLoadingState(stringResource(R.string.tools_organizer_opening), Modifier.fillMaxWidth(), size = 72.dp)
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) } }
            )
            return
        }

        if (session?.pageCount == 1) {
            AlertDialog(
                onDismissRequest = onClose,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (mode == "extract") Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(if (mode == "extract") R.string.tools_extract_single_page_title else R.string.tools_organize_single_page_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(if (mode == "extract") R.string.tools_extract_single_page_hint else R.string.tools_organize_single_page_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(if (mode == "extract") R.string.tools_extract_single_page_desc else R.string.tools_organize_single_page_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
            return
        }
    }

    // Closing with deletes, moves or rotations that were not applied asks first.
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (undoStack.isNotEmpty()) confirmClose = true else onClose() }
    BackHandler(enabled = undoStack.isNotEmpty()) { confirmClose = true }
    if (confirmClose) {
        com.example.scanpro.feature.document.DiscardEditsDialog(
            onKeepEditing = { confirmClose = false },
            onDiscard = { confirmClose = false; onClose() }
        )
    }
    // Each mode starts from the PDF as it is: page order, no turns, and Extract with nothing chosen yet.
    fun switchMode(target: String) {
        val reset = pages.sortedBy { it.index }.map { it.copy(rotation = 0, selected = target != "extract") }
        pages.clear()
        pages.addAll(reset)
        undoStack.clear()
        onModeChange?.invoke(target)
    }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    pendingMode?.let { target ->
        com.example.scanpro.feature.document.DiscardEditsDialog(
            onKeepEditing = { pendingMode = null },
            onDiscard = { pendingMode = null; switchMode(target) }
        )
    }

    // Shown inside the Tools tab, which hides its own title and bottom bar while this is open but still keeps clear of
    // the status and navigation bars: taking those insets again here left a dark band under the pages and a gap above.
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            var organizerMenu by remember { mutableStateOf(false) }
            OrganizerTopBar(
                title = stringResource(
                    when (mode) {
                        // The bar is narrow: a short title here, and "Select pages to extract" in the line under it.
                        "extract" -> R.string.tools_organizer_extract_short_title
                        "rotate" -> R.string.tools_organizer_rotate_title
                        else -> R.string.tools_organizer_organize_title
                    }
                ),
                onClose = requestClose,
                canUndo = undoStack.isNotEmpty(),
                onUndo = ::undo,
                menuEnabled = !isLoading,
                menuOpen = organizerMenu,
                onMenuOpenChange = { organizerMenu = it },
                menu = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tools_organizer_rotate_all)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                        onClick = { organizerMenu = false; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(rotation = (pages[i].rotation + 90) % 360) } } }
                    )
                    if (mode != "rotate") {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (pages.all { it.selected }) R.string.tools_organizer_untick_all else R.string.tools_organizer_tick_all)) },
                            onClick = { organizerMenu = false; val all = pages.all { it.selected }; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(selected = !all) } } }
                        )
                    }
                    if (mode == "organize" && onInsertPages != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tools_organizer_insert_pages)) },
                            enabled = keptCount > 0,
                            onClick = {
                                organizerMenu = false
                                onInsertPages(pages.filter { it.selected }.map { PageSpec(it.index, it.rotation) })
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_reset)) },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = {
                            organizerMenu = false
                            val count = session?.pageCount ?: pages.size
                            change { pages.clear(); pages.addAll((0 until count).map { ThumbnailPageModel(it, selected = defaultSelected) }) }
                        }
                    )
                },
                saveLabel = when (mode) {
                    "extract" -> stringResource(R.string.tools_organizer_extract_count, keptCount)
                    "organize" -> stringResource(R.string.tools_organizer_save_count, keptCount)
                    else -> stringResource(R.string.tools_organizer_save_pdf)
                },
                // Nothing to extract or keep would only end in an error message.
                saveEnabled = !isLoading && pages.isNotEmpty() && (mode == "rotate" || keptCount > 0) && !extractsEverything,
                onSave = {
                    // Organise drops the pages that were unticked; Rotate keeps every page.
                    val specs = pages.filter { mode == "rotate" || it.selected }.map { PageSpec(it.index, it.rotation) }
                    val extracted = pages.filter { it.selected }.map { it.index }
                    val rotations = pages.associate { it.index to it.rotation }
                    onApply(specs, extracted, rotations)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                loadError != null -> Text(loadError!!, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                isLoading -> FileLoadingState(stringResource(R.string.tools_organizer_opening), Modifier.fillMaxSize())
                else -> Column(Modifier.fillMaxSize().widthIn(max = 1200.dp)) {
                    if (onModeChange != null) {
                        SegmentedChoice(
                            options = listOf(
                                "organize" to stringResource(R.string.tools_mode_arrange),
                                "rotate" to stringResource(R.string.doc_rotate),
                                "extract" to stringResource(R.string.doc_extract)
                            ),
                            selected = mode,
                            onSelect = { target ->
                                if (target != mode) {
                                    if (undoStack.isNotEmpty()) pendingMode = target else switchMode(target)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    when {
                        extractsEverything -> OrganizerBanner(stringResource(R.string.tools_extract_all_selected), warning = true)
                        mode == "organize" -> OrganizerBanner(stringResource(R.string.tools_organizer_drag_hint), warning = false)
                        mode == "extract" -> OrganizerBanner(stringResource(R.string.tools_organizer_extract_title), warning = false)
                    }
                    ReorderablePageGrid(
                        items = pages,
                        key = { it.index },
                        dragEnabled = mode == "organize",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onMove = { from, to -> pages.add(to, pages.removeAt(from)) },
                        // One undo step for the whole drag, recorded when the page is picked up.
                        onDragStarted = { undoStack.add(pages.toList()); if (undoStack.size > 50) undoStack.removeAt(0) }
                    ) { item, index, dragging ->
                        // Rendered only while the card is on screen.
                        val thumbnail by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, session, item.index) {
                            val preview = session ?: return@produceState
                            value = withContext(Dispatchers.IO) { preview.render(item.index, 360) }
                        }
                        OrganizerPageCard(
                            item = item,
                            position = index + 1,
                            thumbnail = thumbnail,
                            mode = mode,
                            isDragging = dragging,
                            onClick = {
                                when (mode) {
                                    "organize" -> actionPage = item
                                    "rotate" -> change { pages[index] = item.copy(rotation = (item.rotation + 90) % 360) }
                                    else -> change { pages[index] = item.copy(selected = !item.selected) }
                                }
                            },
                            onPreview = { previewPage = item },
                            onMore = { actionPage = item }
                        )
                    }
                }
            }
            actionPage?.let { target ->
                val index = pages.indexOfFirst { it.index == target.index }
                if (index >= 0) {
                    val current = pages[index]
                    PageActionsSheet(
                        title = stringResource(R.string.tools_page_number, index + 1),
                        subtitle = stringResource(R.string.tools_page_position, index + 1, pages.size),
                        onDismiss = { actionPage = null },
                        actions = listOf(
                            PageAction(Icons.Default.VerticalAlignTop, stringResource(R.string.tools_page_move_start), index > 0) { change { pages.add(0, pages.removeAt(index)) } },
                            PageAction(Icons.Default.KeyboardArrowUp, stringResource(R.string.tools_page_move_earlier), index > 0) { change { pages.add(index - 1, pages.removeAt(index)) } },
                            PageAction(Icons.Default.KeyboardArrowDown, stringResource(R.string.tools_page_move_later), index < pages.lastIndex) { change { pages.add(index + 1, pages.removeAt(index)) } },
                            PageAction(Icons.Default.VerticalAlignBottom, stringResource(R.string.tools_page_move_end), index < pages.lastIndex) { change { pages.add(pages.removeAt(index)) } },
                            PageAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.tools_page_rotate)) { change { pages[index] = current.copy(rotation = (current.rotation + 90) % 360) } },
                            PageAction(Icons.Default.Fullscreen, stringResource(R.string.tools_page_view)) { previewPage = current },
                            if (current.selected) PageAction(Icons.Default.Delete, stringResource(R.string.tools_page_remove), destructive = true) { change { pages[index] = current.copy(selected = false) } }
                            else PageAction(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_page_keep)) { change { pages[index] = current.copy(selected = true) } }
                        )
                    )
                }
            }
            previewPage?.let { page ->
                PdfPagePreviewDialog(
                    page = page,
                    session = session,
                    onDismiss = { previewPage = null }
                )
            }
        }
    }
}

/** A short line above the pages: how to use the screen, or why the action is not available. */
@Composable
internal fun OrganizerBanner(text: String, warning: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (warning) Icons.Default.Info else Icons.Default.TouchApp,
            contentDescription = null,
            tint = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One page in the organiser: a portrait thumbnail and, under it, a footer with the page's current position and what
 * the page offers (Organise: its menu, Rotate: a preview, Extract: whether it is ticked). Nothing is drawn over the page
 * itself, so the controls never hide its content. A tap does what the tool does (Organise: open the page menu, whose
 * "View" shows the page large; Rotate: turn it; Extract: tick it). Pressing and holding picks the page up to move it.
 */
@Composable
internal fun OrganizerPageCard(
    item: ThumbnailPageModel,
    position: Int,
    thumbnail: Bitmap?,
    mode: String,
    isDragging: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    onMore: () -> Unit
) {
    val extract = mode == "extract"
    val removed = mode == "organize" && !item.selected
    val chosen = extract && item.selected
    val pageName = stringResource(R.string.tools_page_number, position)
    val removedLabel = stringResource(R.string.tools_page_removed_label)
    val previewLabel = stringResource(R.string.tools_organizer_preview_page, position)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (chosen) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (extract) Modifier.toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .semantics {
                contentDescription = if (removed) "$pageName, $removedLabel" else pageName
                // Rotate turns the page on a tap, so its preview is offered to screen readers as an action too.
                if (mode == "rotate") customActions = listOf(CustomAccessibilityAction(previewLabel) { onPreview(); true })
            }
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = item.rotation.toFloat()
                                // A page turned on its side is wider than the card: shrink it to fit instead of clipping.
                                if (item.rotation % 180 != 0) { scaleX = 0.78f; scaleY = 0.78f }
                                alpha = if (removed) 0.35f else 1f
                            }
                    )
                } else {
                    // A blank page that shimmers until its thumbnail is drawn.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) { LoadingShimmer(Modifier.matchParentSize()) }
                }
                if (removed) {
                    // On a faded page, in a chip of its own so it reads over any content.
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            removedLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$position",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { }
                )
                when (mode) {
                    "extract" -> Icon(
                        if (item.selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 8.dp).size(24.dp)
                    )
                    "organize" -> IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> IconButton(onClick = onPreview, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = previewLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * The organiser's bar: close, the title on one line (it shrinks a little before it is cut short), Undo, the menu and
 * a compact Save button, so the actions leave the title room on a narrow phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizerTopBar(
    title: String,
    onClose: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    menuEnabled: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    menu: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    saveLabel: String,
    saveEnabled: Boolean,
    onSave: () -> Unit
) {
    TopAppBar(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        title = {
            Text(
                title,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.titleMedium,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = MaterialTheme.typography.titleMedium.fontSize
                ),
                modifier = Modifier.semantics { heading() }
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close))
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.common_undo))
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }, enabled = menuEnabled) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }, content = menu)
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 36.dp).padding(start = 2.dp, end = 10.dp)
            ) {
                Text(saveLabel, maxLines = 1)
            }
        }
    )
}

/** Full-resolution preview of a single page when tapped or long-pressed in the organiser. */
@Composable
private fun PdfPagePreviewDialog(
    page: ThumbnailPageModel,
    session: PdfPreviewSession?,
    onDismiss: () -> Unit
) {
    val fullBitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, page.index, session) {
        val preview = session ?: return@produceState
        value = withContext(Dispatchers.IO) { preview.render(page.index, 1400) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkChrome.backdrop)
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tools_page_number, page.index + 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkChrome.text
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = DarkChrome.text
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = fullBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.tools_page_number, page.index + 1),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = page.rotation.toFloat() },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        FileLoadingAnimation(size = 96.dp, onDark = true)
                    }
                }
            }
        }
    }
}
