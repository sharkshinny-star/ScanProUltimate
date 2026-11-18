package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.withContext

/** The edits survive a rotation: each as its fields in a flat list. */
private val EditsSaver = listSaver<MutableList<PdfTextEditor.Edit>, Any>(
    save = { edits ->
        edits.flatMap { listOf(it.page, it.index, it.text, it.family.name, it.bold, it.italic, it.size, it.colour ?: -1) }
    },
    restore = { flat ->
        flat.chunked(8).map { f ->
            PdfTextEditor.Edit(
                page = f[0] as Int,
                index = f[1] as Int,
                text = f[2] as String,
                family = PdfTextEditor.Family.valueOf(f[3] as String),
                bold = f[4] as Boolean,
                italic = f[5] as Boolean,
                size = f[6] as Float,
                colour = (f[7] as Int).takeIf { it >= 0 }
            )
        }.toMutableStateList()
    }
)

/** Pages are drawn this wide for the editor, in pixels. */
private const val PAGE_WIDTH_PX = 1200

/** The light blue the editor marks text with (owner: page outlines are blue), and green for text already changed. */
private val SpanBlue = Color(0xFF2979FF)
private val ChangedGreen = Color(0xFF14804F)

/**
 * Edit PDF text (F193): the pages one at a time, every line of text marked; a tap opens that line for editing (words,
 * font, bold, italic, size). A page with changes is shown as it will be saved. Save hands the changes back to the tool,
 * which writes a new PDF; the original is not changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPdfTextEditor(uri: Uri, onDismiss: () -> Unit, onSave: (List<PdfTextEditor.Edit>) -> Unit) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<PdfTextEditSession?>(null) }
    var openFailed by remember { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val edits = rememberSaveable(saver = EditsSaver) { mutableListOf<PdfTextEditor.Edit>().toMutableStateList() }
    var editing by remember { mutableStateOf<PdfTextEditor.Span?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // Back and the close button ask first when there are changes that would be lost.
    val close: () -> Unit = { if (edits.isEmpty()) onDismiss() else confirmDiscard = true }

    DisposableEffect(uri) { onDispose { session?.close(); session = null } }
    val opened by produceState<PdfTextEditSession?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { PdfTextEditSession.open(context, uri) }.getOrNull() }
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
                        title = { Text(stringResource(R.string.tools_card_edit_pdf), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } },
                        actions = {
                            if (edits.isNotEmpty()) IconButton(onClick = { edits.removeAt(edits.size - 1) }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tools_edit_undo))
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Column(Modifier.navigationBarsPadding()) {
                            if (pageCount > 1) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.tools_redact_box_previous))
                                    }
                                    Text(stringResource(R.string.viewer_page_of, pageIndex + 1, pageCount), style = MaterialTheme.typography.labelLarge)
                                    IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex < pageCount - 1) {
                                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.tools_redact_box_next))
                                    }
                                }
                            }
                            Button(
                                onClick = { onSave(edits.toList()) },
                                enabled = edits.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (edits.isEmpty()) stringResource(R.string.tools_edit_save)
                                    else pluralStringResource(R.plurals.tools_edit_save_count, edits.size, edits.size),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    val pageSpans = spans
                    Text(
                        stringResource(if (pageSpans != null && pageSpans.isEmpty()) R.string.tools_edit_no_text else R.string.tools_edit_hint),
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
                                bitmap = page.first,
                                pageSize = page.second,
                                spans = pageSpans.orEmpty(),
                                changed = pageEdits.map { it.index }.toSet(),
                                onTap = { editing = it }
                            )
                        }
                    }
                }
            }
        }
        editing?.let { span ->
            val existing = edits.lastOrNull { it.page == span.page && it.index == span.index }
            EditSpanDialog(
                span = span,
                existing = existing,
                onDismiss = { editing = null },
                onApply = { edit ->
                    edits.removeAll { it.page == edit.page && it.index == edit.index }
                    // Unchanged in every way: nothing to save for this line.
                    val untouched = edit.text == span.text && edit.family == PdfTextEditor.Family.ORIGINAL && edit.bold == span.bold &&
                        edit.italic == span.italic && kotlin.math.abs(edit.size - span.fontSize) < 0.05f
                    if (!untouched) edits.add(edit)
                    editing = null
                },
                onUndo = if (existing != null) {
                    { edits.removeAll { it.page == span.page && it.index == span.index }; editing = null }
                } else null
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

/** The page with a thin blue box round every line of text (green once changed); a tap opens the line under it. */
@Composable
private fun PageWithSpans(
    bitmap: Bitmap,
    pageSize: Pair<Float, Float>,
    spans: List<PdfTextEditor.Span>,
    changed: Set<Int>,
    onTap: (PdfTextEditor.Span) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
        val shownW = bitmap.width * scale
        val shownH = bitmap.height * scale
        // Spans are in points of the page as shown; the picture is that page at shownW x shownH.
        val perPoint = if (pageSize.first > 0f) shownW / pageSize.first else 1f
        Box(Modifier.size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(spans, perPoint) {
                        detectTapGestures { tap ->
                            val x = tap.x / perPoint
                            val y = tap.y / perPoint
                            // The smallest box under the finger, with a little slack round thin lines of small text.
                            val slack = 4f
                            spans.filter { x >= it.left - slack && x <= it.right + slack && y >= it.top - slack && y <= it.bottom + slack }
                                .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
                                ?.let(onTap)
                        }
                    }
            ) {
                val stroke = Stroke(width = 1.dp.toPx())
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
}

/** One line of text: its words, font (the PDF's own or a standard one), bold, italic and size. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditSpanDialog(
    span: PdfTextEditor.Span,
    existing: PdfTextEditor.Edit?,
    onDismiss: () -> Unit,
    onApply: (PdfTextEditor.Edit) -> Unit,
    onUndo: (() -> Unit)?
) {
    var text by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.text ?: span.text) }
    var family by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.family ?: PdfTextEditor.Family.ORIGINAL) }
    var bold by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.bold ?: span.bold) }
    var italic by rememberSaveable(span.page, span.index) { mutableStateOf(existing?.italic ?: span.italic) }
    var size by rememberSaveable(span.page, span.index) { mutableFloatStateOf(existing?.size ?: span.fontSize) }
    fun shown(points: Float) = if (points % 1f == 0f) points.toInt().toString() else String.format(Locale.getDefault(), "%.1f", points)
    val edit = { value: String -> PdfTextEditor.Edit(span.page, span.index, value, family, bold, italic, size) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_edit_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace('\n', ' ').take(500) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onApply(edit("")) }) { Text(stringResource(R.string.tools_edit_delete)) }
                    if (onUndo != null) TextButton(onClick = onUndo) { Text(stringResource(R.string.tools_edit_undo_change)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(edit(text.trim())) }) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
