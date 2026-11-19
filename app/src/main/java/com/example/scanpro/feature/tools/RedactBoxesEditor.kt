package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Rect
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
import com.example.scanpro.imaging.PdfPreviewSession
import com.example.scanpro.imaging.PdfRedactTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A finished drag as a drawn box on [page], normalised to the page shown at [size]; null for a slip of the finger
 * (shorter than [PdfRedactTools.MIN_BOX_SHARE] of the page either way). Edges are clamped and ordered, so a drag in
 * any direction works.
 */
internal fun dragToBox(page: Int, start: Offset, end: Offset, size: Size): PdfRedactTools.DrawnBox? {
    if (size.width <= 0f || size.height <= 0f) return null
    val left = (minOf(start.x, end.x) / size.width).coerceIn(0f, 1f)
    val right = (maxOf(start.x, end.x) / size.width).coerceIn(0f, 1f)
    val top = (minOf(start.y, end.y) / size.height).coerceIn(0f, 1f)
    val bottom = (maxOf(start.y, end.y) / size.height).coerceIn(0f, 1f)
    if (right - left < PdfRedactTools.MIN_BOX_SHARE || bottom - top < PdfRedactTools.MIN_BOX_SHARE) return null
    return PdfRedactTools.DrawnBox(page, left, top, right, bottom)
}

private val BoxSaver = listSaver<MutableList<PdfRedactTools.DrawnBox>, Float>(
    save = { boxes -> boxes.flatMap { listOf(it.page.toFloat(), it.left, it.top, it.right, it.bottom) } },
    restore = { flat ->
        flat.chunked(5).map { (p, l, t, r, b) -> PdfRedactTools.DrawnBox(p.toInt(), l, t, r, b) }.toMutableStateList()
    }
)

/**
 * Redact by drawing boxes (F174): the pages are shown one at a time, a drag draws a box, a tap on a box selects it
 * for removal. Apply hands the boxes back; the engine blacks them out and redraws those pages as pictures, exactly as
 * the word-based Redact does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedactBoxesEditor(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (List<PdfRedactTools.DrawnBox>) -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<PdfPreviewSession?>(null) }
    var openFailed by remember { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val boxes = rememberSaveable(saver = BoxSaver) { mutableListOf<PdfRedactTools.DrawnBox>().toMutableStateList() }
    var selected by remember { mutableStateOf<PdfRedactTools.DrawnBox?>(null) }

    DisposableEffect(uri) { onDispose { session?.close(); session = null } }
    val opened by produceState<PdfPreviewSession?>(null, uri) {
        val ready = withContext(Dispatchers.IO) { runCatching { PdfPreviewSession.open(context, uri) }.getOrNull() }
        value = ready
        session = ready
        openFailed = ready == null
        // Closed here too: a session that finished opening after the page was closed would otherwise keep its copy.
        awaitDispose { ready?.close() }
    }
    val preview = opened
    val pageCount = preview?.pageCount ?: 0
    val bitmap by produceState<Bitmap?>(null, preview, pageIndex) {
        value = preview?.let { withContext(Dispatchers.IO) { runCatching { it.render(pageIndex, 1100) }.getOrNull() } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.tools_card_redact_box), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } },
                        actions = {
                            ToolDemoHelpButton("redact_box")
                            val chosen = selected
                            if (chosen != null) {
                                IconButton(onClick = { boxes.remove(chosen); selected = null }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tools_redact_box_remove))
                                }
                            } else if (boxes.isNotEmpty()) {
                                IconButton(onClick = { boxes.removeAt(boxes.size - 1); selected = null }) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tools_redact_box_undo))
                                }
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
                                    IconButton(onClick = { selected = null; pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.tools_redact_box_previous))
                                    }
                                    Text(
                                        stringResource(R.string.viewer_page_of, pageIndex + 1, pageCount),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    IconButton(onClick = { selected = null; pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex < pageCount - 1) {
                                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.tools_redact_box_next))
                                    }
                                }
                            }
                            // Always one line tall (empty before the first box), so the page does not jump when it appears.
                            run {
                                val pagesWithBoxes = boxes.map { it.page }.distinct().size
                                Text(
                                    if (boxes.isEmpty()) "" else stringResource(
                                        R.string.edit_redact_box_summary,
                                        pluralStringResource(R.plurals.edit_redact_box_areas, boxes.size, boxes.size),
                                        pluralStringResource(R.plurals.pdf_outcome_pages, pagesWithBoxes, pagesWithBoxes)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
                                )
                            }
                            Button(
                                onClick = { onConfirm(boxes.toList()) },
                                enabled = boxes.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(
                                    if (boxes.isEmpty()) stringResource(R.string.tools_redact_box_apply_empty)
                                    else stringResource(R.string.tools_redact_box_apply, boxes.size),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        stringResource(R.string.tools_redact_box_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        val shown = bitmap
                        when {
                            openFailed -> Text(stringResource(R.string.tools_redact_box_err_preview), style = MaterialTheme.typography.bodyMedium)
                            shown == null -> CircularProgressIndicator()
                            else -> PageWithBoxes(
                                bitmap = shown,
                                page = pageIndex,
                                boxes = boxes,
                                selected = selected,
                                onSelect = { selected = it },
                                onDrawn = { box -> selected = null; boxes.add(box) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageWithBoxes(
    bitmap: Bitmap,
    page: Int,
    boxes: List<PdfRedactTools.DrawnBox>,
    selected: PdfRedactTools.DrawnBox?,
    onSelect: (PdfRedactTools.DrawnBox?) -> Unit,
    onDrawn: (PdfRedactTools.DrawnBox) -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
        val shownW = bitmap.width * scale
        val shownH = bitmap.height * scale
        val pageBoxes = boxes.filter { it.page == page }
        var dragStart by remember { mutableStateOf<Offset?>(null) }
        var dragNow by remember { mutableStateOf<Offset?>(null) }
        Box(Modifier.size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Keyed on the page: a drag that outlives a page turn must not land boxes on the wrong page.
                    .pointerInput(page, shownW, shownH) {
                        detectDragGestures(
                            onDragStart = { start -> dragStart = start; dragNow = start },
                            onDragCancel = { dragStart = null; dragNow = null },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragNow
                                if (start != null && end != null) {
                                    dragToBox(page, start, end, Size(shownW, shownH))?.let(onDrawn)
                                }
                                dragStart = null
                                dragNow = null
                            }
                        ) { change, _ ->
                            change.consume()
                            dragNow = change.position
                        }
                    }
                    .pointerInput(page, shownW, shownH, pageBoxes) {
                        detectTapGestures { tap ->
                            val x = tap.x / shownW
                            val y = tap.y / shownH
                            onSelect(pageBoxes.lastOrNull { x in it.left..it.right && y in it.top..it.bottom })
                        }
                    }
            ) {
                pageBoxes.forEach { box ->
                    val rect = Rect(box.left * shownW, box.top * shownH, box.right * shownW, box.bottom * shownH)
                    // Nearly as black as the result, with the words just showing through so the box can be checked.
                    drawRect(Color.Black.copy(alpha = 0.82f), rect.topLeft, rect.size)
                    if (box === selected) {
                        drawRect(Color(0xFFE53935), rect.topLeft, rect.size, style = Stroke(width = 3.dp.toPx()))
                    }
                }
                val start = dragStart
                val now = dragNow
                if (start != null && now != null) {
                    val rect = Rect(
                        minOf(start.x, now.x), minOf(start.y, now.y),
                        maxOf(start.x, now.x), maxOf(start.y, now.y)
                    )
                    drawRect(Color.Black.copy(alpha = 0.3f), rect.topLeft, rect.size)
                    drawRect(Color(0xFF1E88E5), rect.topLeft, rect.size, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}
