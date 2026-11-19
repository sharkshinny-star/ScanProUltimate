package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.imaging.PagePreview
import com.example.scanpro.imaging.PdfTextEditSession
import com.example.scanpro.imaging.PdfTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What a change belongs to: a line of the PDF (its span [index]) or a piece of added text (its [id]). */
internal data class EditTarget(val page: Int, val index: Int, val id: Int)

internal val PdfTextEditor.Edit.target: EditTarget get() = EditTarget(page, index, id)

/** A box on the shown page (points from its top-left) that can be tapped: a line, or added text. */
internal data class PageBox(val target: EditTarget, val left: Float, val top: Float, val right: Float, val bottom: Float, val changed: Boolean)

/** The sheets that slide up over the editor. */
internal enum class EditorSheet { TEXT, FONT, PARAGRAPH, COLOUR, TYPOGRAPHY, LISTS, POSITION, ACTIONS, STYLES, FIND, CHANGES, SAVE }

/** Everything the editor shows at one moment; [EditPdfWorkspace] draws it and keeps nothing of its own but zoom. */
internal class EditorState(
    val fileName: String,
    val edits: List<PdfTextEditor.Edit> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
    val page: Bitmap? = null,
    /** The shown page's size in points. */
    val pageSize: Pair<Float, Float> = 595f to 842f,
    val boxes: List<PageBox> = emptyList(),
    val selected: EditTarget? = null,
    /** The selection as it now stands (the line's own look when it has not been changed), or text being added. */
    val current: PdfTextEditor.Edit? = null,
    /** The selection's colour in the PDF, 0xRRGGBB. */
    val originalColour: Int = 0,
    /** True while the next tap on the page says where new text goes. */
    val adding: Boolean = false,
    /** A message for the page area (no text on the page, the PDF would not open); 0 for none. */
    val notice: Int = 0,
    val busy: Boolean = false,
    val sheet: EditorSheet? = null,
    /** What each changed line read before, for the list of changes. */
    val before: Map<EditTarget, String> = emptyMap(),
    val suggestedName: String = ""
) {
    val selectedBox: PageBox? get() = boxes.firstOrNull { it.target == selected }
}

/** What the editor's controls ask for. */
internal class EditorCalls(
    val onBack: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onSelect: (EditTarget?) -> Unit = {},
    /** A tap on the page while adding text, in page points. */
    val onTapPoint: (Float, Float) -> Unit = { _, _ -> },
    /** Changes the selection: the function is given how it now stands. */
    val onChange: ((PdfTextEditor.Edit) -> PdfTextEditor.Edit) -> Unit = {},
    val onSheet: (EditorSheet?) -> Unit = {},
    val onAddText: () -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSetText: (String) -> Unit = {},
    val onReplaceAll: (query: String, replacement: String, matchCase: Boolean) -> Unit = { _, _, _ -> },
    val onOpenChange: (PdfTextEditor.Edit) -> Unit = {},
    val onRemoveChange: (PdfTextEditor.Edit) -> Unit = {},
    val onSave: (String) -> Unit = {}
)

private const val EDIT_FIELDS = 27

private fun PdfTextEditor.Edit.flat(): List<Any> = listOf(
    page, index, text, family.name, bold, italic, size, colour ?: -1, x, y, align.name, wrap, dx, dy, underline, strike,
    highlight ?: -1, lineSpacing, letterSpacing, pushBelow, list.name, script.name, indentLeft, indentRight, spaceBefore, spaceAfter, id
)

private fun editOf(f: List<Any>) = PdfTextEditor.Edit(
    page = f[0] as Int, index = f[1] as Int, text = f[2] as String, family = PdfTextEditor.Family.valueOf(f[3] as String),
    bold = f[4] as Boolean, italic = f[5] as Boolean, size = f[6] as Float, colour = (f[7] as Int).takeIf { it >= 0 },
    x = f[8] as Float, y = f[9] as Float, align = PdfTextEditor.Align.valueOf(f[10] as String), wrap = f[11] as Boolean,
    dx = f[12] as Float, dy = f[13] as Float, underline = f[14] as Boolean, strike = f[15] as Boolean,
    highlight = (f[16] as Int).takeIf { it >= 0 }, lineSpacing = f[17] as Float, letterSpacing = f[18] as Float,
    pushBelow = f[19] as Boolean, list = PdfTextEditor.ListStyle.valueOf(f[20] as String),
    script = PdfTextEditor.Script.valueOf(f[21] as String), indentLeft = f[22] as Float, indentRight = f[23] as Float,
    spaceBefore = f[24] as Float, spaceAfter = f[25] as Float, id = f[26] as Int
)

/** The edits survive a rotation: each as its fields in a flat list. */
private val EditsSaver = listSaver<List<PdfTextEditor.Edit>, Any>(
    save = { edits -> edits.flatMap { it.flat() } },
    restore = { flat -> flat.chunked(EDIT_FIELDS).map(::editOf) }
)

/** Pages are drawn this wide for the editor, in pixels: enough to stay sharp when zoomed in on a phone. */
private const val PAGE_WIDTH_PX = 1600

/** How far the page can be zoomed in. */
private const val MAX_ZOOM = 5f

/** The light blue the editor marks text with (owner: page outlines are blue), and green for text already changed. */
private val SpanBlue = Color(0xFF2979FF)
private val ChangedGreen = Color(0xFF14804F)
private val SelectionIndigo = Color(0xFF3344D6)

/** A line of the PDF as an edit that changes nothing: what its controls start from. */
private fun baseEdit(span: PdfTextEditor.Span) =
    PdfTextEditor.Edit(span.page, span.index, span.text, bold = span.bold, italic = span.italic, size = span.fontSize)

/**
 * Edit PDF text (F193; a Word-like workspace since F206, after the owner's ten-screen design): the page with every line
 * marked, a tap selects a line (a second tap opens its words), the selection can be dragged, and a small bar of
 * actions floats by it. The panel at the bottom formats the selection (Home), adds text (Insert), lays it out (Layout)
 * and reviews the work (Review); sheets slide up for fonts, colours, paragraphs, lists, typography and position. Every
 * change is drawn on the page as it will be saved, and can be undone. Done asks for a file name and hands the changes
 * back to the tool, which writes a new PDF; the original is not changed.
 */
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
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    // New text whose words are being typed: on the page once it has some.
    var pending by remember { mutableStateOf<PdfTextEditor.Edit?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<EditorSheet?>(null) }
    var searching by remember { mutableStateOf(false) }
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
    val rendered by produceState<PagePreview?>(null, current, pageIndex, pageEdits) {
        value = current?.let { s -> withContext(Dispatchers.IO) { s.render(context, pageIndex, PAGE_WIDTH_PX, pageEdits) } } ?: value
    }
    val preview = rendered?.takeIf { it.page == pageIndex }
    val pageSpans = spans

    fun spanOf(target: EditTarget): PdfTextEditor.Span? =
        if (target.index >= 0 && target.page == pageIndex) pageSpans?.getOrNull(target.index) else null
    fun standing(target: EditTarget): PdfTextEditor.Edit? = edits.lastOrNull { it.target == target } ?: spanOf(target)?.let(::baseEdit)
    val change: ((PdfTextEditor.Edit) -> PdfTextEditor.Edit) -> Unit = change@{ transform ->
        val target = selected ?: return@change
        val now = standing(target) ?: return@change
        val next = transform(now)
        val others = edits.filterNot { it.target == target }
        val span = spanOf(target)
        val gone = next.isNew && next.text.isBlank()
        // A line put back exactly as the PDF has it is no change at all.
        commit(if (gone || (span != null && next == baseEdit(span))) others else others + next)
        if (gone) selected = null
    }

    // The boxes to tap: each line where it now is (its new words' box, or lower when a line above pushed it down),
    // and each piece of added text.
    val boxes = remember(pageSpans, preview, pageEdits, pageIndex) {
        val placed = preview?.placed.orEmpty().associateBy { it.edit.target }
        val lines = pageSpans.orEmpty().map { span ->
            val target = EditTarget(pageIndex, span.index, 0)
            val at = placed[target]
            val down = preview?.moved?.get(span.index) ?: 0f
            if (at != null) PageBox(target, at.left, at.top, at.right, at.bottom, changed = true)
            else PageBox(target, span.left, span.top + down, span.right, span.bottom + down, changed = pageEdits.any { it.target == target })
        }
        val added = pageEdits.filter { it.isNew }.mapNotNull { edit ->
            placed[edit.target]?.let { PageBox(edit.target, it.left, it.top, it.right, it.bottom, changed = true) }
        }
        lines + added
    }
    val before by produceState<Map<EditTarget, String>>(emptyMap(), current, edits, sheet == EditorSheet.CHANGES) {
        val open = current ?: return@produceState
        if (sheet != EditorSheet.CHANGES) return@produceState
        value = withContext(Dispatchers.IO) {
            edits.filterNot { it.isNew }.associate { edit ->
                edit.target to runCatching { open.spans(edit.page).getOrNull(edit.index)?.text }.getOrNull().orEmpty()
            }
        }
    }
    val state = EditorState(
        fileName = suggestedName.removeSuffix(" edited") + ".pdf",
        edits = edits,
        canUndo = history.isNotEmpty(),
        canRedo = undone.isNotEmpty(),
        pageIndex = pageIndex,
        pageCount = pageCount,
        page = preview?.bitmap,
        pageSize = (preview?.width ?: 595f) to (preview?.height ?: 842f),
        boxes = boxes,
        selected = selected,
        current = pending ?: selected?.let(::standing),
        originalColour = selected?.let(::spanOf)?.colour ?: 0,
        adding = adding,
        notice = when {
            openFailed -> R.string.tools_edit_err_open
            pageSpans != null && pageSpans.isEmpty() && pageEdits.isEmpty() -> R.string.tools_edit_no_text
            else -> 0
        },
        busy = searching,
        sheet = sheet,
        before = before,
        suggestedName = suggestedName
    )
    val nextId = { (edits.maxOfOrNull { it.id } ?: 0) + 1 }
    val calls = EditorCalls(
        onBack = close,
        onDone = { if (edits.isEmpty()) onDismiss() else sheet = EditorSheet.SAVE },
        onUndo = { if (history.isNotEmpty()) { undone.add(edits); edits = history.removeAt(history.lastIndex) } },
        onRedo = { if (undone.isNotEmpty()) { history.add(edits); edits = undone.removeAt(undone.lastIndex) } },
        onPage = { page -> pageIndex = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)); selected = null },
        onSelect = { target -> selected = target },
        onTapPoint = { x, y ->
            adding = false
            // The new words take the size and font of the nearest line; the tap is their middle, so the baseline sits
            // a little below it.
            val near = pageSpans.orEmpty().minByOrNull { s ->
                kotlin.math.hypot(maxOf(s.left - x, 0f, x - s.right), maxOf(s.top - y, 0f, y - s.bottom))
            }
            val size = near?.fontSize ?: 12f
            pending = PdfTextEditor.Edit(pageIndex, PdfTextEditor.NEW, "", bold = false, italic = false, size = size, x = x, y = y + size * 0.35f, id = nextId())
            sheet = EditorSheet.TEXT
        },
        onChange = change,
        onSheet = { next -> sheet = next; if (next != EditorSheet.TEXT) pending = null },
        onAddText = { adding = !adding; selected = null },
        onDuplicate = dup@{
            val target = selected ?: return@dup
            val now = standing(target) ?: return@dup
            val box = boxes.firstOrNull { it.target == target } ?: return@dup
            // A copy on the line below, as text of its own.
            val copy = now.copy(
                index = PdfTextEditor.NEW, id = nextId(), x = box.left, y = box.bottom + now.size * 1.15f, dx = 0f, dy = 0f,
                colour = now.colour ?: spanOf(target)?.colour,
                align = if (now.wrap) now.align else PdfTextEditor.Align.LEFT
            )
            commit(edits + copy)
            selected = copy.target
        },
        onDelete = {
            val target = selected
            if (target != null && target.index == PdfTextEditor.NEW) { commit(edits.filterNot { it.target == target }); selected = null }
            else change { it.copy(text = "") }
        },
        onSetText = { text ->
            val fresh = pending
            if (fresh != null) {
                if (text.isNotBlank()) { commit(edits + fresh.copy(text = text)); selected = fresh.target }
                pending = null
            } else change { it.copy(text = text) }
            sheet = null
        },
        onReplaceAll = { query, replacement, matchCase ->
            sheet = null
            val open = current
            if (open != null) scope.launch {
                searching = true
                val found = withContext(Dispatchers.IO) { runCatching { open.find(query, matchCase) }.getOrDefault(emptyList()) }
                searching = false
                var next = edits
                var count = 0
                // Lines of the PDF holding the words: as they now read, if they were changed already.
                for (span in found) {
                    val target = EditTarget(span.page, span.index, 0)
                    val existing = next.lastOrNull { it.target == target }
                    val replaced = PdfTextEditor.replaceIn(existing?.text ?: span.text, query, replacement, matchCase) ?: continue
                    next = next.filterNot { it.target == target } + (existing?.copy(text = replaced) ?: baseEdit(span).copy(text = replaced))
                    count++
                }
                // And words typed in this session (added text, or a changed line that only now holds them).
                val handled = found.map { EditTarget(it.page, it.index, 0) }.toSet()
                next = next.map { edit ->
                    if (edit.target in handled) return@map edit
                    val replaced = PdfTextEditor.replaceIn(edit.text, query, replacement, matchCase) ?: return@map edit
                    count++
                    edit.copy(text = replaced)
                }
                commit(next)
                val message = if (count == 0) resources.getString(R.string.tools_edit_no_matches, query)
                else resources.getQuantityString(R.plurals.tools_edit_replaced, count, count)
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        },
        onOpenChange = { edit ->
            pageIndex = edit.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            selected = edit.target
            sheet = null
        },
        onRemoveChange = { edit ->
            commit(edits - edit)
            if (edits.isEmpty()) sheet = null
        },
        onSave = { name -> sheet = null; onSave(edits, name) }
    )

    Dialog(
        onDismissRequest = { if (sheet != null) { sheet = null; pending = null } else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        EditPdfWorkspace(state, calls)
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

/** The editor as drawn from [state]: header, mode row, page, the formatting panel, and the open sheet over them. */
@Composable
internal fun EditPdfWorkspace(state: EditorState, calls: EditorCalls, initialTab: Int = 0) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                EditorHeader(state, calls)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ModeRow(state, calls)
                PageArea(state, calls, Modifier.weight(1f))
                FormatPanel(state, calls, initialTab)
            }
            EditorSheets(state, calls)
        }
    }
}

/** Back, the PDF's name with what state the work is in, and Done. */
@Composable
private fun EditorHeader(state: EditorState, calls: EditorCalls) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = calls.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(state.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(ChangedGreen))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.edits.isEmpty()) stringResource(R.string.tools_edit_status_none)
                    else stringResource(R.string.tools_edit_status, pluralStringResource(R.plurals.tools_edit_changes, state.edits.size, state.edits.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = calls.onDone, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.common_done), fontWeight = FontWeight.SemiBold) }
    }
}

/** The mode the page is in (editing text, or waiting for the tap that places new text), Undo, Redo and more. */
@Composable
private fun ModeRow(state: EditorState, calls: EditorCalls) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
        Surface(
            shape = CircleShape,
            color = if (state.adding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (state.adding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).clickable(enabled = state.adding, onClick = calls.onAddText)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (state.adding) R.string.tools_edit_add_hint else R.string.tools_edit_mode),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
        IconButton(onClick = calls.onUndo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tools_edit_undo)) }
        IconButton(onClick = calls.onRedo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.tools_edit_redo)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.common_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tools_edit_find_replace)) },
                    leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
                    onClick = { menu = false; calls.onSheet(EditorSheet.FIND) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tools_edit_changes_title)) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                    enabled = state.edits.isNotEmpty(),
                    onClick = { menu = false; calls.onSheet(EditorSheet.CHANGES) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tools_edit_add_text)) },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    onClick = { menu = false; calls.onAddText() }
                )
            }
        }
    }
}

/** The page on a grey desk, with the page number and any message for it. */
@Composable
private fun PageArea(state: EditorState, calls: EditorCalls, modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val bitmap = state.page
        when {
            bitmap != null -> PageCanvas(state, bitmap, calls)
            state.notice != 0 -> Text(stringResource(state.notice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
            else -> CircularProgressIndicator()
        }
        if (bitmap != null && state.notice != 0) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                Text(
                    stringResource(state.notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (state.busy) CircularProgressIndicator()
        if (state.pageCount > 1) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { calls.onPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.tools_redact_box_previous))
                    }
                    Text("${state.pageIndex + 1} / ${state.pageCount}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { calls.onPage(state.pageIndex + 1) }, enabled = state.pageIndex < state.pageCount - 1, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.tools_redact_box_next))
                    }
                }
            }
        }
    }
}

/**
 * The page with a thin blue box round every line of text (green once changed). A tap selects the line under it; the
 * selection is filled, has a handle at two corners, can be dragged to move it and is opened by a second tap. Two
 * fingers zoom in (small print is hard to hit on a phone) and a drag moves the zoomed page; the zoom stays while lines
 * are changed and goes back to the whole page on another page.
 */
@Composable
private fun PageCanvas(state: EditorState, bitmap: Bitmap, calls: EditorCalls) {
    val pageKey = state.pageIndex
    var zoom by remember(pageKey) { mutableFloatStateOf(1f) }
    var pan by remember(pageKey) { mutableStateOf(Offset.Zero) }
    val selectedBox = state.selectedBox
    // How far the selection has been dragged; it is let go of once the page is drawn again with it moved.
    var drag by remember(state.selected) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(selectedBox) { drag = Offset.Zero }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    // The page is white whatever the theme, so the selection keeps one strong colour on it.
    val accent = SelectionIndigo
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val scale = minOf((maxW - margin * 2) / bitmap.width, (maxH - margin * 2) / bitmap.height).coerceAtLeast(0.01f)
        val shownW = bitmap.width * scale
        val shownH = bitmap.height * scale
        // Boxes are in points of the page as shown; the picture is that page at shownW x shownH.
        val perPoint = if (state.pageSize.first > 0f) shownW / state.pageSize.first else 1f
        // The zoomed page can be dragged until its edge reaches the edge of the space, no further.
        fun held(offset: Offset, at: Float) = Offset(
            offset.x.coerceIn(-maxOf(0f, (shownW * at - maxW) / 2f + margin), maxOf(0f, (shownW * at - maxW) / 2f + margin)),
            offset.y.coerceIn(-maxOf(0f, (shownH * at - maxH) / 2f + margin), maxOf(0f, (shownH * at - maxH) / 2f + margin))
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageKey, shownW, shownH, maxW, maxH) {
                    detectTransformGestures { _, moved, change, _ ->
                        val next = (zoom * change).coerceIn(1f, MAX_ZOOM)
                        pan = if (next <= 1.001f) Offset.Zero else held(pan * (next / zoom) + moved, next)
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
                        .pointerInput(state.boxes, perPoint, state.adding) {
                            detectTapGestures { tap ->
                                val x = tap.x / perPoint
                                val y = tap.y / perPoint
                                if (state.adding) { calls.onTapPoint(x, y); return@detectTapGestures }
                                // The smallest box under the finger, with a little slack round thin lines of small text
                                // (less of it when zoomed in, where the finger is exact). A tap on nothing lets go.
                                val slack = 4f / zoom
                                calls.onSelect(
                                    state.boxes.filter { x >= it.left - slack && x <= it.right + slack && y >= it.top - slack && y <= it.bottom + slack }
                                        .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }?.target
                                )
                            }
                        }
                ) {
                    val stroke = Stroke(width = 1.dp.toPx() / zoom)
                    for (box in state.boxes) {
                        if (box.target == state.selected) continue
                        val colour = if (box.changed) ChangedGreen else SpanBlue
                        val topLeft = Offset(box.left * perPoint, box.top * perPoint)
                        val size = Size((box.right - box.left) * perPoint, (box.bottom - box.top) * perPoint)
                        drawRect(colour.copy(alpha = if (box.changed) 0.12f else 0.05f), topLeft, size)
                        drawRect(colour.copy(alpha = 0.6f), topLeft, size, style = stroke)
                    }
                }
                if (selectedBox != null) {
                    // A line whose words were deleted has nothing to move.
                    val movable = state.current?.text?.isNotEmpty() == true
                    Box(
                        Modifier
                            .offset { IntOffset((selectedBox.left * perPoint + drag.x).roundToInt(), (selectedBox.top * perPoint + drag.y).roundToInt()) }
                            .size(
                                with(density) { ((selectedBox.right - selectedBox.left) * perPoint).coerceAtLeast(1f).toDp() },
                                with(density) { ((selectedBox.bottom - selectedBox.top) * perPoint).coerceAtLeast(1f).toDp() }
                            )
                            .drawBehind {
                                val pad = 2.dp.toPx() / zoom
                                val topLeft = Offset(-pad, -pad)
                                val around = Size(size.width + pad * 2, size.height + pad * 2)
                                drawRect(accent.copy(alpha = 0.14f), topLeft, around)
                                drawRect(accent, topLeft, around, style = Stroke(2.dp.toPx() / zoom))
                                drawCircle(accent, 5.dp.toPx() / zoom, topLeft)
                                drawCircle(accent, 5.dp.toPx() / zoom, Offset(size.width + pad, size.height + pad))
                            }
                            .pointerInput(selectedBox.target) { detectTapGestures { calls.onSheet(EditorSheet.TEXT) } }
                            .pointerInput(selectedBox.target, perPoint, movable) {
                                if (movable) detectDragGestures(
                                    onDragEnd = {
                                        val moved = drag
                                        if (moved.getDistance() < perPoint * 0.5f) drag = Offset.Zero
                                        else calls.onChange { it.copy(dx = it.dx + moved.x / perPoint, dy = it.dy + moved.y / perPoint) }
                                    },
                                    onDragCancel = { drag = Offset.Zero }
                                ) { change, amount -> change.consume(); drag += amount }
                            }
                    )
                }
            }
        }
        if (selectedBox != null && !state.adding) {
            // The actions float just above the selection (below it when there is no room above), whatever the zoom.
            val centre = maxW / 2f + pan.x + ((selectedBox.left + selectedBox.right) / 2f * perPoint + drag.x - shownW / 2f) * zoom
            val top = maxH / 2f + pan.y + (selectedBox.top * perPoint + drag.y - shownH / 2f) * zoom
            val bottom = maxH / 2f + pan.y + (selectedBox.bottom * perPoint + drag.y - shownH / 2f) * zoom
            val gap = with(density) { 10.dp.toPx() }
            val x = (centre - barSize.width / 2f).coerceIn(4f, maxOf(4f, maxW - barSize.width - 4f))
            val y = if (top - barSize.height - gap >= 0f) top - barSize.height - gap
            else (bottom + gap).coerceIn(0f, maxOf(0f, maxH - barSize.height))
            FloatingActions(calls, Modifier.align(Alignment.TopStart).offset { IntOffset(x.roundToInt(), y.roundToInt()) }.onSizeChanged { barSize = it })
        }
        if (zoom > 1.01f) {
            FilledTonalButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text(stringResource(R.string.tools_edit_zoom_reset))
            }
        }
    }
}

/** The small bar by the selection: change its words, format it, colour it, or see everything that can be done. */
@Composable
private fun FloatingActions(calls: EditorCalls, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(Modifier.padding(4.dp)) {
            FloatingAction(Icons.Default.Edit, stringResource(R.string.tools_edit_edit)) { calls.onSheet(EditorSheet.TEXT) }
            FloatingAction(Icons.Default.TextFormat, stringResource(R.string.tools_edit_format)) { calls.onSheet(EditorSheet.FONT) }
            FloatingAction(Icons.Default.FormatColorText, stringResource(R.string.tools_edit_colour)) { calls.onSheet(EditorSheet.COLOUR) }
            FloatingAction(Icons.Default.MoreHoriz, stringResource(R.string.common_more)) { calls.onSheet(EditorSheet.ACTIONS) }
        }
    }
}

@Composable
private fun FloatingAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
