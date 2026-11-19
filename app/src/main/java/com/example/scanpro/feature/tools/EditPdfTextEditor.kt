package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfTextEditSession
import com.example.scanpro.imaging.PdfTextEditor
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The edits survive a rotation: each as its fields in a flat list. */
private val EditsSaver = listSaver<List<PdfTextEditor.Edit>, Any>(
    save = { edits ->
        edits.flatMap {
            listOf(it.page, it.index, it.text, it.family.name, it.bold, it.italic, it.size, it.colour ?: -1, it.x, it.y, it.align.name, it.wrap, it.dx, it.dy)
        }
    },
    restore = { flat ->
        flat.chunked(14).map { f ->
            PdfTextEditor.Edit(
                page = f[0] as Int,
                index = f[1] as Int,
                text = f[2] as String,
                family = PdfTextEditor.Family.valueOf(f[3] as String),
                bold = f[4] as Boolean,
                italic = f[5] as Boolean,
                size = f[6] as Float,
                colour = (f[7] as Int).takeIf { it >= 0 },
                x = f[8] as Float,
                y = f[9] as Float,
                align = PdfTextEditor.Align.valueOf(f[10] as String),
                wrap = f[11] as Boolean,
                dx = f[12] as Float,
                dy = f[13] as Float
            )
        }
    }
)

/** Pages are drawn this wide for the editor, in pixels: enough to stay sharp when zoomed in on a phone. */
private const val PAGE_WIDTH_PX = 1600

/** How far the page can be zoomed in. */
private const val MAX_ZOOM = 5f

/** The light blue the editor marks text with (owner: page outlines are blue), and green for text already changed. */
private val SpanBlue = Color(0xFF2979FF)
private val ChangedGreen = Color(0xFF14804F)

/**
 * Edit PDF text (F193, reworked in F205): the pages one at a time, every line of text marked; a tap opens that line
 * for editing. The page zooms with two fingers. Find and replace changes a word everywhere; Changes lists what has
 * been done, page by page, and takes any of it back; Undo and Redo step through it. A page with changes is shown as it
 * will be saved. Save asks for a file name and hands the changes back to the tool, which writes a new PDF; the
 * original is not changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPdfTextEditor(
    uri: Uri,
    /** The name offered for the saved PDF, without ".pdf". */
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (List<PdfTextEditor.Edit>, String) -> Unit
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<PdfTextEditSession?>(null) }
    var openFailed by remember { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var edits by rememberSaveable(stateSaver = EditsSaver) { mutableStateOf(emptyList<PdfTextEditor.Edit>()) }
    // The sets of changes before each step, and those stepped back from, newest last (not kept over a rotation; the
    // changes themselves are, and Changes can still take any of them back).
    val history = remember { mutableStateListOf<List<PdfTextEditor.Edit>>() }
    val undone = remember { mutableStateListOf<List<PdfTextEditor.Edit>>() }
    val commit: (List<PdfTextEditor.Edit>) -> Unit = { next ->
        if (next != edits) {
            history.add(edits)
            undone.clear()
            edits = next
        }
    }
    var editing by remember { mutableStateOf<PdfTextEditor.Span?>(null) }
    // Add text: the next tap on the page says where new words start.
    var adding by rememberSaveable { mutableStateOf(false) }
    var finding by rememberSaveable { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var showChanges by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // Back and the close button ask first when there are changes that would be lost.
    val close: () -> Unit = { if (edits.isEmpty()) onDismiss() else confirmDiscard = true }

    DisposableEffect(uri) { onDispose { session?.close(); session = null } }
    val opened by produceState<PdfTextEditSession?>(null, uri) {
        // Not cancellable: an editor closed while the PDF opens still gets the session back, to close it.
        val opened = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { PdfTextEditSession.open(context, uri) }.getOrNull() }
        if (!isActive) { opened?.close(); return@produceState }
        value = opened
        session = value
        openFailed = value == null
    }
    val current = opened
    val pageCount = current?.pageCount ?: 0
    val spans by produceState<List<PdfTextEditor.Span>?>(null, current, pageIndex) {
        value = null
        value = current?.let { s -> withContext(Dispatchers.IO) { s.spans(pageIndex) } }
    }
    val pageEdits = edits.filter { it.page == pageIndex }
    val rendered by produceState<Pair<Bitmap, Pair<Float, Float>>?>(null, current, pageIndex, pageEdits) {
        value = current?.let { s -> withContext(Dispatchers.IO) { s.render(context, pageIndex, PAGE_WIDTH_PX, pageEdits) } } ?: value
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.tools_tile_edit_pdf), style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                        navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } },
                        actions = {
                            IconButton(onClick = { finding = true }, enabled = current != null) {
                                Icon(Icons.Default.FindReplace, contentDescription = stringResource(R.string.tools_edit_find_replace))
                            }
                            IconButton(onClick = { adding = !adding }, enabled = current != null) {
                                Icon(
                                    Icons.Default.TextFields,
                                    contentDescription = stringResource(R.string.tools_edit_add_text),
                                    tint = if (adding) SpanBlue else androidx.compose.material3.LocalContentColor.current
                                )
                            }
                            IconButton(
                                onClick = { undone.add(edits); edits = history.removeAt(history.lastIndex) },
                                enabled = history.isNotEmpty()
                            ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tools_edit_undo)) }
                            IconButton(
                                onClick = { history.add(edits); edits = undone.removeAt(undone.lastIndex) },
                                enabled = undone.isNotEmpty()
                            ) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.tools_edit_redo)) }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Column(Modifier.navigationBarsPadding()) {
                            if (pageCount > 1 || edits.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (pageCount > 1) {
                                        IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.tools_redact_box_previous))
                                        }
                                        Text(stringResource(R.string.viewer_page_of, pageIndex + 1, pageCount), style = MaterialTheme.typography.labelLarge)
                                        IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex < pageCount - 1) {
                                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.tools_redact_box_next))
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    // What has been changed so far, to look over or take back before saving.
                                    if (edits.isNotEmpty()) TextButton(onClick = { showChanges = true }) {
                                        Text(pluralStringResource(R.plurals.tools_edit_changes, edits.size, edits.size))
                                    }
                                }
                            }
                            Button(
                                onClick = { saving = true },
                                enabled = edits.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tools_edit_save), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    val pageSpans = spans
                    Text(
                        stringResource(
                            when {
                                adding -> R.string.tools_edit_add_hint
                                pageSpans != null && pageSpans.isEmpty() -> R.string.tools_edit_no_text
                                else -> R.string.tools_edit_hint
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        val page = rendered
                        when {
                            openFailed -> Text(stringResource(R.string.tools_edit_err_open), style = MaterialTheme.typography.bodyMedium)
                            page == null -> CircularProgressIndicator()
                            else -> PageWithSpans(
                                pageKey = pageIndex,
                                bitmap = page.first,
                                pageSize = page.second,
                                spans = pageSpans.orEmpty(),
                                changed = pageEdits.map { it.index }.toSet(),
                                onTap = { editing = it },
                                onTapPoint = if (!adding) null else { x, y ->
                                    adding = false
                                    // The new words take the size, font name and colour of the nearest line; the tap is
                                    // their middle, so the baseline sits a little below it.
                                    val near = pageSpans.orEmpty().minByOrNull { s ->
                                        kotlin.math.hypot(maxOf(s.left - x, 0f, x - s.right), maxOf(s.top - y, 0f, y - s.bottom))
                                    }
                                    val size = near?.fontSize ?: 12f
                                    val baseline = y + size * 0.35f
                                    editing = PdfTextEditor.Span(
                                        page = pageIndex, index = PdfTextEditor.NEW, text = "",
                                        left = x, top = baseline - size * 0.9f, right = x, bottom = baseline + size * 0.25f,
                                        fontName = near?.fontName.orEmpty(), fontSize = size, bold = false, italic = false,
                                        colour = near?.colour ?: 0
                                    )
                                }
                            )
                        }
                        if (searching) CircularProgressIndicator()
                    }
                }
            }
        }
        editing?.let { span ->
            val isNew = span.index == PdfTextEditor.NEW
            val existing = if (isNew) null else edits.lastOrNull { it.page == span.page && it.index == span.index }
            EditSpanDialog(
                span = span,
                existing = existing,
                onDismiss = { editing = null },
                onApply = { edit ->
                    if (isNew) {
                        if (edit.text.isNotBlank()) commit(edits + edit.copy(x = span.left, y = span.bottom - span.fontSize * 0.25f))
                        editing = null
                        return@EditSpanDialog
                    }
                    val others = edits.filterNot { it.page == edit.page && it.index == edit.index }
                    // Unchanged in every way: nothing to save for this line.
                    val untouched = edit.text == span.text && edit.family == PdfTextEditor.Family.ORIGINAL && edit.bold == span.bold &&
                        edit.italic == span.italic && kotlin.math.abs(edit.size - span.fontSize) < 0.05f && edit.colour == null &&
                        edit.align == PdfTextEditor.Align.LEFT && !edit.wrap && edit.dx == 0f && edit.dy == 0f
                    commit(if (untouched) others else others + edit)
                    editing = null
                },
                onUndo = if (existing != null) {
                    { commit(edits.filterNot { it.page == span.page && it.index == span.index }); editing = null }
                } else null
            )
        }
        if (finding) {
            FindReplaceDialog(
                onDismiss = { finding = false },
                onReplaceAll = { query, replacement, matchCase ->
                    finding = false
                    val open = current
                    if (open != null) scope.launch {
                        searching = true
                        val found = withContext(Dispatchers.IO) { runCatching { open.find(query, matchCase) }.getOrDefault(emptyList()) }
                        searching = false
                        var next = edits
                        var count = 0
                        // Lines of the PDF holding the words: as they now read, if they were changed already.
                        for (span in found) {
                            val existing = next.lastOrNull { it.page == span.page && it.index == span.index }
                            val replaced = PdfTextEditor.replaceIn(existing?.text ?: span.text, query, replacement, matchCase) ?: continue
                            val edit = existing?.copy(text = replaced)
                                ?: PdfTextEditor.Edit(span.page, span.index, replaced, bold = span.bold, italic = span.italic, size = span.fontSize)
                            next = next.filterNot { it.page == span.page && it.index == span.index } + edit
                            count++
                        }
                        // And words typed in this session (added text, or a changed line that only now holds them).
                        val handled = found.map { it.page to it.index }.toSet()
                        next = next.map { edit ->
                            if (!edit.isNew && (edit.page to edit.index) in handled) return@map edit
                            val replaced = PdfTextEditor.replaceIn(edit.text, query, replacement, matchCase) ?: return@map edit
                            count++
                            edit.copy(text = replaced)
                        }
                        commit(next)
                        val message = if (count == 0) resources.getString(R.string.tools_edit_no_matches, query)
                        else resources.getQuantityString(R.plurals.tools_edit_replaced, count, count)
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        if (showChanges) {
            // What each changed line read before, for the list.
            val before by produceState<Map<Pair<Int, Int>, String>>(emptyMap(), current, edits) {
                val open = current ?: return@produceState
                value = withContext(Dispatchers.IO) {
                    edits.filterNot { it.isNew }.associate { edit ->
                        (edit.page to edit.index) to runCatching { open.spans(edit.page).getOrNull(edit.index)?.text }.getOrNull().orEmpty()
                    }
                }
            }
            ChangesDialog(
                edits = edits,
                before = before,
                onDismiss = { showChanges = false },
                onOpen = { edit -> pageIndex = edit.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)); showChanges = false },
                onRemove = { edit ->
                    commit(edits - edit)
                    if (edits.isEmpty()) showChanges = false
                }
            )
        }
        if (saving) {
            SaveEditedDialog(
                suggestedName = suggestedName,
                changes = edits.size,
                onDismiss = { saving = false },
                onSave = { name -> saving = false; onSave(edits, name) }
            )
        }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.tools_edit_discard_title)) },
                text = { Text(stringResource(R.string.tools_edit_discard_body)) },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.tools_edit_discard)) } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
    }
}

/**
 * The page with a thin blue box round every line of text (green once changed); a tap opens the line under it. Two
 * fingers zoom in (small print is hard to hit on a phone) and a drag moves the zoomed page; the zoom stays while lines
 * are changed and goes back to the whole page on another page.
 */
@Composable
private fun PageWithSpans(
    pageKey: Int,
    bitmap: Bitmap,
    pageSize: Pair<Float, Float>,
    spans: List<PdfTextEditor.Span>,
    changed: Set<Int>,
    onTap: (PdfTextEditor.Span) -> Unit,
    /** Set while adding text: a tap anywhere gives its place in page points instead of opening a line. */
    onTapPoint: ((Float, Float) -> Unit)? = null
) {
    var zoom by remember(pageKey) { mutableFloatStateOf(1f) }
    var pan by remember(pageKey) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
        val shownW = bitmap.width * scale
        val shownH = bitmap.height * scale
        // Spans are in points of the page as shown; the picture is that page at shownW x shownH.
        val perPoint = if (pageSize.first > 0f) shownW / pageSize.first else 1f
        // The zoomed page can be dragged until its edge reaches the edge of the space, no further.
        fun held(offset: Offset, at: Float) = Offset(
            offset.x.coerceIn(-maxOf(0f, (shownW * at - maxW) / 2f), maxOf(0f, (shownW * at - maxW) / 2f)),
            offset.y.coerceIn(-maxOf(0f, (shownH * at - maxH) / 2f), maxOf(0f, (shownH * at - maxH) / 2f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageKey, shownW, shownH, maxW, maxH) {
                    detectTransformGestures { _, drag, change, _ ->
                        val next = (zoom * change).coerceIn(1f, MAX_ZOOM)
                        pan = held(pan * (next / zoom) + drag, next)
                        zoom = next
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
            ) {
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(spans, perPoint, onTapPoint) {
                            detectTapGestures { tap ->
                                val x = tap.x / perPoint
                                val y = tap.y / perPoint
                                if (onTapPoint != null) { onTapPoint(x, y); return@detectTapGestures }
                                // The smallest box under the finger, with a little slack round thin lines of small text
                                // (less of it when zoomed in, where the finger is exact).
                                val slack = 4f / zoom
                                spans.filter { x >= it.left - slack && x <= it.right + slack && y >= it.top - slack && y <= it.bottom + slack }
                                    .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
                                    ?.let(onTap)
                            }
                        }
                ) {
                    val stroke = Stroke(width = 1.dp.toPx() / zoom)
                    for (span in spans) {
                        val isChanged = span.index in changed
                        val colour = if (isChanged) ChangedGreen else SpanBlue
                        val topLeft = Offset(span.left * perPoint, span.top * perPoint)
                        val size = Size((span.right - span.left) * perPoint, (span.bottom - span.top) * perPoint)
                        drawRect(colour.copy(alpha = if (isChanged) 0.12f else 0.06f), topLeft, size)
                        drawRect(colour.copy(alpha = 0.7f), topLeft, size, style = stroke)
                    }
                }
            }
        }
        if (zoom > 1.01f) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { zoom = 1f; pan = Offset.Zero },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) { Text(stringResource(R.string.tools_edit_zoom_reset)) }
        }
    }
}

/** Find and replace: the words to look for, what to put in their place, everywhere in the PDF. */
@Composable
private fun FindReplaceDialog(onDismiss: () -> Unit, onReplaceAll: (query: String, replacement: String, matchCase: Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_edit_find_replace)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it.replace('\n', ' ').take(200) }, singleLine = true, label = { Text(stringResource(R.string.tools_edit_find_label)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(replacement, { replacement = it.replace('\n', ' ').take(200) }, singleLine = true, label = { Text(stringResource(R.string.tools_edit_replace_label)) }, modifier = Modifier.fillMaxWidth())
                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text(stringResource(R.string.tools_edit_match_case)) })
                Text(stringResource(R.string.tools_edit_find_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onReplaceAll(query, replacement, matchCase) }, enabled = query.isNotEmpty() && query != replacement) {
                Text(stringResource(R.string.tools_edit_replace_all))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** The changes made so far, page by page: a tap goes to its page, the cross takes it back. */
@Composable
private fun ChangesDialog(
    edits: List<PdfTextEditor.Edit>,
    before: Map<Pair<Int, Int>, String>,
    onDismiss: () -> Unit,
    onOpen: (PdfTextEditor.Edit) -> Unit,
    onRemove: (PdfTextEditor.Edit) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_edit_changes_title)) },
        text = { ChangesList(edits, before, onOpen, onRemove) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } }
    )
}

/** The list inside [ChangesDialog], apart from its window so it can be drawn in a screenshot test. */
@Composable
internal fun ChangesList(
    edits: List<PdfTextEditor.Edit>,
    before: Map<Pair<Int, Int>, String>,
    onOpen: (PdfTextEditor.Edit) -> Unit,
    onRemove: (PdfTextEditor.Edit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        edits.sortedWith(compareBy({ it.page }, { it.index })).forEach { edit ->
            val old = before[edit.page to edit.index].orEmpty()
            val kind = when {
                edit.isNew -> stringResource(R.string.tools_edit_change_added)
                edit.text.isEmpty() -> stringResource(R.string.tools_edit_change_deleted)
                else -> null
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { onOpen(edit) }
            ) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(stringResource(R.string.tools_edit_change_page, edit.page + 1), kind).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (old.isNotEmpty()) Text(
                            old,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (edit.text.isNotEmpty()) Text(
                            edit.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(edit) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tools_edit_change_remove))
                    }
                }
            }
        }
    }
}

/** Save: the new PDF's name, and a reminder that the original stays as it is. */
@Composable
private fun SaveEditedDialog(suggestedName: String, changes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Save, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_edit_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace('\n', ' ').take(80) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tools_edit_save_name)) },
                    suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    pluralStringResource(R.plurals.tools_edit_changes, changes, changes) + ". " + stringResource(R.string.tools_edit_save_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.tools_edit_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** One line of text: its words, font (the PDF's own or a standard one), bold, italic, size, position and colour. */
@Composable
private fun EditSpanDialog(
    span: PdfTextEditor.Span,
    existing: PdfTextEditor.Edit?,
    onDismiss: () -> Unit,
    onApply: (PdfTextEditor.Edit) -> Unit,
    onUndo: (() -> Unit)?
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            EditSpanForm(span, existing, onDismiss, onApply, onUndo, Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp))
        }
    }
}

/**
 * The dialog's content, apart from its window so it can be drawn in a screenshot test: the words on top, the options
 * in a scrolling column, and Cancel and Apply always in view below them.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EditSpanForm(
    span: PdfTextEditor.Span,
    existing: PdfTextEditor.Edit?,
    onDismiss: () -> Unit,
    onApply: (PdfTextEditor.Edit) -> Unit,
    onUndo: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.text ?: span.text) }
    var family by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.family ?: PdfTextEditor.Family.ORIGINAL) }
    var bold by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.bold ?: span.bold) }
    var italic by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.italic ?: span.italic) }
    var size by rememberSaveable(span.page, span.index) { mutableFloatStateOf(existing?.size ?: span.fontSize) }
    // Null: the colour the line already has.
    var colour by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.colour) }
    var align by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.align ?: PdfTextEditor.Align.LEFT) }
    var wrap by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.wrap ?: false) }
    // Moved by this many points from where it would sit (right and down are positive).
    var dx by rememberSaveable(span.page, span.index) { mutableFloatStateOf(existing?.dx ?: 0f) }
    var dy by rememberSaveable(span.page, span.index) { mutableFloatStateOf(existing?.dy ?: 0f) }
    val isNew = span.index == PdfTextEditor.NEW
    fun shown(points: Float) = if (points % 1f == 0f) points.toInt().toString() else String.format(Locale.getDefault(), "%.1f", points)
    val edit = { value: String -> PdfTextEditor.Edit(span.page, span.index, value, family, bold, italic, size, colour, align = align, wrap = wrap, dx = dx, dy = dy) }

    Column(modifier) {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isNew) R.string.tools_edit_new_title else R.string.tools_edit_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            // Beside the title, always in view: it used to sit under the options, below the fold on a phone.
            if (!isNew) IconButton(onClick = { onApply(edit("")) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tools_edit_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
        run {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    // Enter starts a new line in the PDF too.
                    onValueChange = { text = it.take(1000) },
                    minLines = 1,
                    maxLines = 6,
                    label = { Text(stringResource(R.string.tools_edit_text_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.tools_edit_original, span.fontName.ifBlank { "?" }, shown(span.fontSize)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.tools_edit_font), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfTextEditor.Family.entries.forEach { option ->
                        FilterChip(
                            selected = family == option,
                            onClick = { family = option },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            PdfTextEditor.Family.ORIGINAL -> R.string.tools_edit_font_same
                                            PdfTextEditor.Family.SANS -> R.string.tools_edit_font_sans
                                            PdfTextEditor.Family.SERIF -> R.string.tools_edit_font_serif
                                            PdfTextEditor.Family.MONO -> R.string.tools_edit_font_mono
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = bold,
                        onClick = { bold = !bold },
                        label = { Text(stringResource(R.string.tools_edit_bold)) },
                        leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = italic,
                        onClick = { italic = !italic },
                        label = { Text(stringResource(R.string.tools_edit_italic)) },
                        leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tools_edit_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { size = (size - 1f).coerceAtLeast(4f) }, enabled = size > 4f) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.tools_edit_smaller))
                    }
                    Text(stringResource(R.string.tools_edit_size_value, shown(size)), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { size = (size + 1f).coerceAtMost(96f) }, enabled = size < 96f) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tools_edit_larger))
                    }
                }
                Text(stringResource(R.string.tools_edit_align), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(PdfTextEditor.Align.LEFT, R.string.tools_edit_align_left, Icons.AutoMirrored.Filled.FormatAlignLeft),
                        Triple(PdfTextEditor.Align.CENTRE, R.string.tools_edit_align_centre, Icons.Default.FormatAlignCenter),
                        Triple(PdfTextEditor.Align.RIGHT, R.string.tools_edit_align_right, Icons.AutoMirrored.Filled.FormatAlignRight)
                    ).forEach { (option, label, icon) ->
                        FilterChip(
                            selected = align == option,
                            onClick = { align = option },
                            label = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    FilterChip(
                        selected = wrap,
                        onClick = { wrap = !wrap },
                        label = { Text(stringResource(R.string.tools_edit_wrap)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                // A point at a time, to line a word up by eye.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tools_edit_move), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (dx != 0f || dy != 0f) TextButton(onClick = { dx = 0f; dy = 0f }) { Text(stringResource(R.string.common_reset)) }
                    IconButton(onClick = { dx -= 1f }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_edit_move_left)) }
                    IconButton(onClick = { dy -= 1f }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_edit_move_up)) }
                    IconButton(onClick = { dy += 1f }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_edit_move_down)) }
                    IconButton(onClick = { dx += 1f }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_edit_move_right)) }
                }
                Text(
                    stringResource(if (isNew) R.string.tools_edit_align_hint_new else R.string.tools_edit_align_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.tools_edit_colour), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val options = listOf<Pair<Int?, Int>>(
                        null to R.string.tools_edit_colour_same,
                        0x000000 to R.string.doc_ink_black,
                        0x1A3FB8 to R.string.doc_ink_blue,
                        0xC62828 to R.string.tools_watermark_color_red,
                        0x616161 to R.string.tools_watermark_color_grey
                    )
                    options.forEach { (value, name) ->
                        val chosen = colour == value
                        val swatch = Color(0xFF000000.toInt() or (value ?: span.colour))
                        val label = stringResource(name)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(swatch)
                                .border(
                                    if (chosen) 3.dp else 1.dp,
                                    if (chosen) SpanBlue else MaterialTheme.colorScheme.outlineVariant,
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .selectable(selected = chosen, role = androidx.compose.ui.semantics.Role.RadioButton) { colour = value }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center
                        ) {
                            // "As in the PDF" is marked, since its swatch can look like one of the others.
                            if (value == null) Text("=", color = if (span.colour == 0) Color.White else Color.Black, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onUndo != null) TextButton(onClick = onUndo) { Text(stringResource(R.string.tools_edit_undo_change)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onApply(edit(text.trim())) }) { Text(stringResource(R.string.common_apply)) }
        }
    }
}
