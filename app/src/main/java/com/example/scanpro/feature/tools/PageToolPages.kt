package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.InsertPagesEngine
import com.example.scanpro.imaging.PageInsertion
import com.example.scanpro.imaging.PageNumberFormat
import com.example.scanpro.imaging.PageNumberGeometry
import com.example.scanpro.imaging.PageNumberPosition
import com.example.scanpro.imaging.PageNumbersConfig
import com.example.scanpro.imaging.PageRanges
import com.example.scanpro.imaging.PdfNupTools
import com.example.scanpro.imaging.PdfPreviewSession
import com.example.scanpro.imaging.SplitMode
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The page tools' option pages (F210): Split, Page numbers, N-up, Remove blank pages and Insert pages. Each shows the
 * picked PDF, works out before it runs exactly what will come out (files, pages, sheets, and a size where one can be
 * honestly guessed), and refuses input that makes no sense with the reason above the button. Page numbers and N-up
 * draw their result on the real first page.
 */

/** "About 1.2 MB" for an estimate that scales with the share of pages kept: [bytes] × [part] / [whole]. */
internal fun shareOf(bytes: Long, part: Int, whole: Int): Long? =
    if (bytes <= 0L || whole <= 0) null else (bytes.toDouble() * part.coerceAtLeast(0) / whole).toLong()

/** The pages of each file Split makes, from 1, as the engine makes them; empty while that cannot be known. */
internal fun splitParts(mode: SplitMode, pages: Int?, everyN: Int?, ranges: List<IntRange>?): List<IntRange> = when (mode) {
    SplitMode.SINGLE_PAGES -> if (pages != null) (1..pages).map { it..it } else emptyList()
    SplitMode.EVERY_N_PAGES ->
        if (pages != null && everyN != null && everyN >= 1) (1..pages step everyN).map { it..minOf(it + everyN - 1, pages) } else emptyList()
    SplitMode.PAGE_RANGES -> ranges.orEmpty()
}

/** "4" or "1–3": a range of pages as people write it. */
private fun rangeLabel(range: IntRange): String = if (range.first == range.last) "${range.first}" else "${range.first}–${range.last}"

// ---------------------------------------------------------------------------------------------------------------
// Split
// ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (SplitMode, String, Int) -> Unit
) {
    val context = LocalContext.current
    val facts by rememberPdfFacts(uri)
    var mode by rememberSaveable { mutableStateOf(SplitMode.SINGLE_PAGES) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var chunkText by rememberSaveable { mutableStateOf("") }
    val loaded = facts
    val pages = loaded?.pageCount
    // Sensible starting values once the page count is known: two halves, and files of about a quarter each.
    LaunchedEffect(pages) {
        if (pages != null && pages > 1) {
            if (rangesText.isEmpty()) rangesText = "1-${(pages + 1) / 2}, ${(pages + 1) / 2 + 1}-$pages"
            if (chunkText.isEmpty()) chunkText = (pages / 4).coerceIn(1, pages - 1).toString()
        }
    }
    val everyN = chunkText.toIntOrNull()
    val parsed = remember(rangesText, pages) { PageRanges.parse(rangesText, pages ?: Int.MAX_VALUE) }
    val parts = splitParts(mode, pages, everyN, parsed.getOrNull())
    val outPages = parts.sumOf { it.last - it.first + 1 }

    val problem: String? = when {
        loaded == null -> stringResource(R.string.pages_reading_pdf)
        pages != null && pages <= 1 -> stringResource(R.string.tools_split_single_page_desc)
        mode == SplitMode.EVERY_N_PAGES && (everyN == null || everyN < 1) -> stringResource(R.string.tools_split_enter_count)
        mode == SplitMode.EVERY_N_PAGES && pages != null && everyN != null && everyN >= pages ->
            pluralStringResource(R.plurals.tools_split_whole_document, pages, pages)
        mode == SplitMode.PAGE_RANGES -> parsed.exceptionOrNull()?.message
            ?: if (pages != null && parts.size == 1 && parts[0].first == 1 && parts[0].last == pages) pluralStringResource(R.plurals.tools_split_whole_document, pages, pages) else null
        else -> null
    }

    ToolPage(
        title = stringResource(R.string.tools_split_pdf),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_split_pdf),
        actionIcon = Icons.AutoMirrored.Filled.CallSplit,
        onAction = { onConfirm(mode, rangesText, if (mode == SplitMode.EVERY_N_PAGES) everyN ?: 1 else 1) },
        actionEnabled = problem == null,
        actionSupport = problem ?: if (parts.size > 1) pluralStringResource(R.plurals.pages_split_zip_hint, parts.size, parts.size) else null,
        demoToolId = "split"
    ) {
        PickedFileRow(uri)
        if (loaded != null && pages == null) ImageToolNotice(stringResource(R.string.pages_count_unknown))
        if (pages != null && pages <= 1) {
            ImageToolNotice(stringResource(R.string.tools_split_single_page_desc))
            return@ToolPage
        }

        ToolSection(stringResource(R.string.pages_split_how)) {
            ChoiceGroup {
                SplitMode.entries.forEach { item ->
                    ChoiceCard(
                        selected = mode == item,
                        title = stringResource(item.labelRes),
                        subtitle = stringResource(item.descriptionRes),
                        onClick = { mode = item }
                    )
                }
            }
            when (mode) {
                SplitMode.PAGE_RANGES -> OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it },
                    label = { Text(stringResource(R.string.tools_split_ranges_example)) },
                    singleLine = true,
                    isError = parsed.isFailure && rangesText.isNotBlank(),
                    supportingText = { Text(parsed.exceptionOrNull()?.message ?: stringResource(R.string.pages_split_ranges_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SplitMode.EVERY_N_PAGES -> {
                    if (pages != null) {
                        val suggestions = listOf(2, 3, 5, 10, 20).filter { it < pages }
                        if (suggestions.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                suggestions.forEach { n ->
                                    FilterChip(
                                        selected = everyN == n,
                                        onClick = { chunkText = n.toString() },
                                        label = { Text(pluralStringResource(R.plurals.pdf_outcome_pages, n, n)) }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = chunkText,
                        onValueChange = { typed -> if (typed.length <= 5) chunkText = typed.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.tools_split_pages_per_file)) },
                        singleLine = true,
                        isError = problem != null && loaded != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SplitMode.SINGLE_PAGES -> Unit
            }
        }

        // The estimate follows the choice it depends on.
        if (loaded != null) {
            PdfOutcomeRow(
                loaded,
                if (problem != null || parts.isEmpty()) null
                else PdfOutcome(pages = outPages, bytes = shareOf(loaded.sizeBytes, outPages, pages ?: 0), files = parts.size)
            )
        }
        if (problem == null && parts.isNotEmpty()) {
            ToolSection(pluralStringResource(R.plurals.pdf_outcome_files, parts.size, parts.size)) {
                SplitFileList(parts)
                // A file that repeats pages of another: fine, but say so, since it is easy to type by mistake.
                val overlapping = parts.sortedBy { it.first }.zipWithNext().any { (a, b) -> b.first <= a.last }
                if (overlapping) ToolHint(stringResource(R.string.pages_split_overlap))
            }
        }
    }
}

/** The files Split will make, one line each (the first few, then how many more). */
@Composable
private fun SplitFileList(parts: List<IntRange>) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = if (parts.size <= 6) parts else parts.take(5)
            shown.forEachIndexed { index, range ->
                val count = range.last - range.first + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index % 2 == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        pluralStringResource(R.plurals.pages_split_file_line, count, index + 1, rangeLabel(range), count),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (shown.size < parts.size) {
                val more = parts.size - shown.size
                Text(
                    pluralStringResource(R.plurals.pages_split_more_files, more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Page numbers
// ---------------------------------------------------------------------------------------------------------------

private val PageNumberPosition.atTop: Boolean
    get() = this == PageNumberPosition.TOP_LEFT || this == PageNumberPosition.TOP_CENTER || this == PageNumberPosition.TOP_RIGHT

/** 0 left, 1 centre, 2 right. */
private val PageNumberPosition.column: Int
    get() = when (this) {
        PageNumberPosition.TOP_LEFT, PageNumberPosition.BOTTOM_LEFT -> 0
        PageNumberPosition.TOP_CENTER, PageNumberPosition.BOTTOM_CENTER -> 1
        PageNumberPosition.TOP_RIGHT, PageNumberPosition.BOTTOM_RIGHT -> 2
    }

private fun numberPosition(top: Boolean, column: Int): PageNumberPosition = when (column) {
    0 -> if (top) PageNumberPosition.TOP_LEFT else PageNumberPosition.BOTTOM_LEFT
    2 -> if (top) PageNumberPosition.TOP_RIGHT else PageNumberPosition.BOTTOM_RIGHT
    else -> if (top) PageNumberPosition.TOP_CENTER else PageNumberPosition.BOTTOM_CENTER
}

@Composable
internal fun PageNumbersPdfDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (PageNumbersConfig) -> Unit
) {
    val context = LocalContext.current
    val facts by rememberPdfFacts(uri)
    var position by rememberSaveable { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var format by rememberSaveable { mutableStateOf(PageNumberFormat.PAGE_N_OF_M) }
    var skipCover by rememberSaveable { mutableStateOf(false) }
    val loaded = facts
    val pages = loaded?.pageCount
    val config = PageNumbersConfig(position = position, format = format, skipFirstPage = skipCover)

    // With the cover skipped, the preview shows page 2: the first page that gets a number.
    val secondPage by produceState<Bitmap?>(null, uri, skipCover, pages) {
        value = null
        if (skipCover && pages != null && pages > 1) {
            value = withContext(Dispatchers.IO) {
                runCatching { PdfPreviewSession.open(context, uri).use { it.render(1, 900) } }.getOrNull()
            }
        }
    }
    val shownFacts = loaded?.let { base ->
        val second = secondPage
        if (skipCover && second != null) PdfFacts(base.pageCount, base.sizeBytes, second, base.pageWidthPt, base.pageHeightPt) else base
    }
    val shownPage = if (skipCover) 2 else 1
    val total = pages ?: 1
    val numbered = if (pages == null) 0 else if (skipCover) pages - 1 else pages
    val measurer = rememberTextMeasurer()
    val ink = Color(0xFF333333)

    val problem = when {
        loaded == null -> stringResource(R.string.pages_reading_pdf)
        pages != null && numbered <= 0 -> stringResource(R.string.pages_numbers_only_cover)
        else -> null
    }

    ToolPage(
        title = stringResource(R.string.tools_page_numbers_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_page_numbers_action),
        actionIcon = Icons.Default.FormatListNumbered,
        onAction = { onConfirm(config) },
        actionEnabled = problem == null,
        actionSupport = problem ?: if (pages != null) pluralStringResource(R.plurals.pages_numbers_numbered, numbered, numbered) else null,
        demoToolId = "page_numbers"
    ) {
        PdfResultPreview(
            facts = shownFacts,
            result = { page ->
                // The same text, size and place as the engine: points scaled to the page as drawn.
                val widthPt = (loaded?.pageWidthPt ?: 0).takeIf { it > 0 }?.toFloat() ?: 595f
                val heightPt = (loaded?.pageHeightPt ?: 0).takeIf { it > 0 }?.toFloat() ?: 842f
                val pxPerPt = page.width / widthPt
                val grow = PageNumberGeometry.sizeScale(widthPt, heightPt)
                val margin = config.marginPt * grow
                val text = PageNumberGeometry.text(format, shownPage, total)
                val style = TextStyle(color = ink, fontSize = 11.sp)
                val unit = measurer.measure(text, style)
                // Width of the text per point of font size, from the measured layout at 11 sp.
                val widthPerPoint = unit.size.width / 11.sp.toPx()
                val fontPt = PageNumberGeometry.fittedFontSize(config.fontSizePt * grow, widthPerPoint, margin, widthPt)
                val fontPx = fontPt * pxPerPt
                val layout = measurer.measure(text, style.copy(fontSize = fontPx.toSp()))
                val x = when (position.column) {
                    0 -> page.left + margin * pxPerPt
                    2 -> page.right - margin * pxPerPt - layout.size.width
                    else -> page.center.x - layout.size.width / 2f
                }
                val baseline = if (position.atTop) page.top + (margin + fontPt) * pxPerPt else page.bottom - margin * pxPerPt
                val top = baseline - layout.firstBaseline
                drawRoundRect(Color(0x33FFD54F), Offset(x - fontPx * 0.4f, top - fontPx * 0.15f), Size(layout.size.width + fontPx * 0.8f, layout.size.height + fontPx * 0.3f), CornerRadius(fontPx * 0.3f))
                drawText(layout, topLeft = Offset(x, top))
            }
        )
        if (skipCover && pages != null && pages > 1) ToolHint(stringResource(R.string.pages_numbers_preview_page_two))

        ToolSection(stringResource(R.string.pages_numbers_where)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.pages_numbers_edge_top), false to stringResource(R.string.pages_numbers_edge_bottom)),
                selected = position.atTop,
                onSelect = { position = numberPosition(it, position.column) }
            )
            SegmentedChoice(
                options = listOf(
                    0 to stringResource(R.string.pages_numbers_align_left),
                    1 to stringResource(R.string.pages_numbers_align_centre),
                    2 to stringResource(R.string.pages_numbers_align_right)
                ),
                selected = position.column,
                onSelect = { position = numberPosition(position.atTop, it) }
            )
        }

        ToolSection(stringResource(R.string.tools_page_numbers_format_label)) {
            ChoiceGroup {
                PageNumberFormat.entries.forEach { item ->
                    ChoiceCard(
                        selected = format == item,
                        title = PageNumberGeometry.text(item, shownPage, total),
                        onClick = { format = item }
                    )
                }
            }
            ToolHint(stringResource(R.string.pages_numbers_latin_note))
        }

        // Leaving the cover plain is a rarer wish: folded away, still applied while folded.
        MoreOptions(summary = if (skipCover) stringResource(R.string.tools_page_numbers_skip_cover) else null) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .toggleable(value = skipCover, role = Role.Switch, onValueChange = { skipCover = it })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_page_numbers_skip_cover), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pages_numbers_cover_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = skipCover, onCheckedChange = null)
                }
            }
        }

        if (loaded != null) {
            PdfOutcomeRow(loaded, PdfOutcome(pages = pages, bytes = loaded.sizeBytes.takeIf { it > 0 }?.let { it + numbered * 220L }))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// N-up
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun NupDialog(uri: Uri, onDismiss: () -> Unit, onConfirm: (perSheet: Int) -> Unit) {
    val facts by rememberPdfFacts(uri)
    var perSheet by rememberSaveable { mutableIntStateOf(2) }
    val loaded = facts
    val pages = loaded?.pageCount
    val sheets = pages?.let { PdfNupTools.sheetCount(it, perSheet) }
    val measurer = rememberTextMeasurer()
    val backdrop = MaterialTheme.colorScheme.background
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    val placeholderInk = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    // The geometry the engine uses, from the first page as seen.
    val widthPt = (loaded?.pageWidthPt ?: 0).takeIf { it > 0 }?.toFloat() ?: 595f
    val heightPt = (loaded?.pageHeightPt ?: 0).takeIf { it > 0 }?.toFloat() ?: 842f
    val (sheetW, sheetH) = PdfNupTools.sheetSize(perSheet, widthPt, heightPt)
    val cells = PdfNupTools.cells(perSheet, sheetW, sheetH)
    val scalePercent = (PdfNupTools.fitScale(cells.first(), widthPt, heightPt) * 100).roundToInt()
    val image = remember(loaded?.firstPage) { loaded?.firstPage?.asImageBitmap() }

    val problem = when {
        loaded == null -> stringResource(R.string.pages_reading_pdf)
        pages != null && pages < 2 -> stringResource(R.string.tools_nup_err_one_page)
        else -> null
    }

    ToolPage(
        title = stringResource(R.string.tools_card_nup),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_nup_action),
        actionIcon = Icons.Default.GridView,
        onAction = { onConfirm(perSheet) },
        actionEnabled = problem == null,
        actionSupport = problem,
        demoToolId = "nup",
        about = stringResource(R.string.tools_nup_body)
    ) {
        PdfResultPreview(
            facts = loaded,
            result = { _ ->
                // The first sheet in place of the page: the canvas cleared, the sheet fitted in, page 1 in its cell
                // and numbered stand-ins for the rest.
                drawRect(backdrop, Offset.Zero, size)
                val fit = minOf(size.width * 0.94f / sheetW, size.height * 0.94f / sheetH)
                val sheet = Rect(Offset((size.width - sheetW * fit) / 2f, (size.height - sheetH * fit) / 2f), Size(sheetW * fit, sheetH * fit))
                drawRect(Color.Black.copy(alpha = 0.12f), sheet.topLeft + Offset(0f, 6f), sheet.size)
                drawRect(Color.White, sheet.topLeft, sheet.size)
                val onSheet = pages ?: perSheet
                cells.forEachIndexed { slot, cell ->
                    // Cell in sheet points, y up: to canvas pixels, y down.
                    val cellRect = Rect(
                        Offset(sheet.left + cell.x * fit, sheet.bottom - (cell.y + cell.height) * fit),
                        Size(cell.width * fit, cell.height * fit)
                    )
                    val s = PdfNupTools.fitScale(cell, widthPt, heightPt) * fit
                    val pageRect = Rect(
                        Offset(cellRect.center.x - widthPt * s / 2f, cellRect.center.y - heightPt * s / 2f),
                        Size(widthPt * s, heightPt * s)
                    )
                    if (slot >= onSheet) return@forEachIndexed
                    val picture = image
                    if (slot == 0 && picture != null) {
                        drawImage(
                            picture,
                            dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                            dstSize = IntSize(pageRect.width.roundToInt().coerceAtLeast(1), pageRect.height.roundToInt().coerceAtLeast(1))
                        )
                    } else {
                        drawRect(placeholder, pageRect.topLeft, pageRect.size)
                        val label = measurer.measure("${slot + 1}", TextStyle(color = placeholderInk, fontSize = (pageRect.height * 0.22f).toSp(), fontWeight = FontWeight.SemiBold))
                        drawText(label, topLeft = Offset(pageRect.center.x - label.size.width / 2f, pageRect.center.y - label.size.height / 2f))
                    }
                    drawRect(accent.copy(alpha = 0.55f), pageRect.topLeft, pageRect.size, style = Stroke(1.2f))
                }
            }
        )

        ToolSection(stringResource(R.string.pages_nup_per_sheet_title)) {
            SegmentedChoice(
                options = PdfNupTools.PER_SHEET_CHOICES.map { it to "$it" },
                selected = perSheet,
                onSelect = { perSheet = it }
            )
            ToolHint(stringResource(R.string.pages_nup_scale, scalePercent))
            if (scalePercent < 35) ImageToolNotice(stringResource(R.string.pages_nup_small_print))
            if (pages != null && pages >= 2) {
                val last = pages % perSheet
                if (last != 0) ToolHint(pluralStringResource(R.plurals.pages_nup_last_sheet, last, last))
            }
        }
        if (loaded != null) {
            PdfOutcomeRow(
                loaded,
                if (sheets == null || problem != null) null
                else PdfOutcome(pages = sheets, bytes = loaded.sizeBytes.takeIf { it > 0 }?.let { it + sheets * 400L }, pagesLabel = pluralStringResource(R.plurals.pages_nup_sheets, sheets, sheets))
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Remove blank pages
// ---------------------------------------------------------------------------------------------------------------

/**
 * What the blank-page check found, page by page with a picture of each, all ticked to go. Untick any to keep it:
 * nothing is removed that the person has not seen (F210; the old pop-up only listed the page numbers).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlankPagesDialog(found: BlankPagesFound, onDismiss: () -> Unit, onRemove: (List<Int>) -> Unit) {
    val context = LocalContext.current
    val facts by rememberPdfFacts(found.uri)
    val ticked = rememberSaveable(
        found,
        saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Int>, Int>(
            save = { it.toList() },
            restore = { mutableStateListOf(*it.toTypedArray()) }
        )
    ) { mutableStateListOf(*found.blankPages.toTypedArray()) }
    val session by produceState<PdfPreviewSession?>(null, found.uri) {
        val opened = runCatching { PdfPreviewSession.open(context, found.uri) }.getOrNull()
        value = opened
        awaitDispose { opened?.close() }
    }
    val count = found.blankPages.size
    val removing = found.blankPages.filter { it in ticked }
    val everyPage = count >= found.pageCount
    val problem = when {
        count == 0 -> null
        everyPage -> stringResource(R.string.tools_blank_all_blank)
        removing.isEmpty() -> stringResource(R.string.pages_blank_none_ticked)
        else -> null
    }

    ToolPage(
        title = stringResource(if (count == 0) R.string.tools_blank_none_title else R.string.tools_blank_found_title),
        onClose = onDismiss,
        actionLabel = if (count == 0 || everyPage) stringResource(R.string.common_close)
            else pluralStringResource(R.plurals.tools_blank_remove_action, removing.size.coerceAtLeast(1), removing.size),
        actionIcon = if (count == 0 || everyPage) null else Icons.Default.DeleteSweep,
        onAction = { if (count == 0 || everyPage) onDismiss() else onRemove(removing) },
        actionEnabled = count == 0 || everyPage || removing.isNotEmpty(),
        actionSupport = problem,
        demoToolId = "blank_pages"
    ) {
        PickedFileRow(found.uri)
        when {
            count == 0 -> ImageToolNotice(stringResource(R.string.tools_blank_none_body))
            everyPage -> ImageToolNotice(stringResource(R.string.tools_blank_all_blank))
            else -> ToolSection(pluralStringResource(R.plurals.pages_blank_found_count, count, count, found.pageCount)) {
                ToolHint(stringResource(R.string.pages_blank_review))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    found.blankPages.forEach { index ->
                        BlankPageTile(
                            index = index,
                            session = session,
                            checked = index in ticked,
                            onToggle = { if (index in ticked) ticked.remove(index) else ticked.add(index) }
                        )
                    }
                }
                ToolHint(stringResource(R.string.pages_blank_copy_note))
            }
        }
        val loaded = facts
        if (loaded != null) {
            val kept = found.pageCount - removing.size
            PdfOutcomeRow(
                loaded,
                if (count == 0 || everyPage) PdfOutcome(pages = found.pageCount, bytes = loaded.sizeBytes.takeIf { it > 0 })
                else PdfOutcome(pages = kept, bytes = shareOf(loaded.sizeBytes, kept, found.pageCount))
            )
        }
    }
}

@Composable
private fun BlankPageTile(index: Int, session: PdfPreviewSession?, checked: Boolean, onToggle: () -> Unit) {
    val thumbnail by produceState<Bitmap?>(null, session, index) {
        val preview = session ?: return@produceState
        value = withContext(BackgroundWork.thumbnails) { preview.render(index, 240) }
    }
    val name = stringResource(R.string.tools_page_number, index + 1)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(96.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) { contentDescription = name }
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Insert pages
// ---------------------------------------------------------------------------------------------------------------

private const val POSITION_START = 0
private const val POSITION_AFTER = 1
private const val POSITION_END = 2

/**
 * Organise's "insert pages from another PDF", as a full page (F210): which PDF, which of its pages, and where among
 * the [keptPages] pages being organised; the second PDF's pages are counted as soon as it is picked, so the range and
 * the total are checked before anything runs, and a strip shows where the new pages land.
 */
@Composable
internal fun InsertPagesDialog(
    baseUri: Uri?,
    keptPages: Int,
    nameOf: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (PageInsertion) -> Unit
) {
    val context = LocalContext.current
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var rangeText by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableIntStateOf(POSITION_END) }
    var afterText by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = uri
    }
    val source = picked
    val baseFacts = baseUri?.let { rememberPdfFacts(it).value }
    val sourceFacts by produceState<PdfFacts?>(null, source) {
        value = null
        if (source != null) value = withContext(Dispatchers.IO) { runCatching { loadPdfFacts(context, source) }.getOrNull() }
    }
    val sourcePages = sourceFacts?.pageCount
    val after = afterText.trim().toIntOrNull()
    val afterPosition = when (position) {
        POSITION_START -> 0
        POSITION_AFTER -> after ?: -1
        else -> keptPages
    }
    // The pages to take, checked the way the engine checks them (ranges, a page named twice, the page limit).
    val chosen: Result<List<Int>>? = remember(allPages, rangeText, sourcePages, keptPages) {
        if (sourcePages == null) null
        else runCatching {
            InsertPagesEngine.pagesToInsert(if (allPages) null else rangeText.trim(), sourcePages).also {
                InsertPagesEngine.requireWithinLimit(keptPages, it.size)
            }
        }
    }
    val inserting = chosen?.getOrNull()?.size

    val problem = when {
        source == null -> stringResource(R.string.pages_insert_pick_first)
        sourceFacts == null -> stringResource(R.string.pages_reading_pdf)
        !allPages && rangeText.isBlank() -> stringResource(R.string.tools_insert_range_label)
        chosen?.exceptionOrNull() != null -> chosen?.exceptionOrNull()?.message ?: stringResource(R.string.tools_insert_range_label)
        position == POSITION_AFTER && (after == null || after !in 1..keptPages) -> stringResource(R.string.tools_insert_after_label, keptPages)
        else -> null
    }

    ToolPage(
        title = stringResource(R.string.tools_insert_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_insert_action),
        actionIcon = Icons.Default.PostAdd,
        onAction = { if (source != null && problem == null) onConfirm(PageInsertion(source, if (allPages) null else rangeText.trim(), afterPosition)) },
        actionEnabled = problem == null,
        actionSupport = problem,
        demoToolId = "insert_pages",
        // What Insert does is said once, in the folded "How it works", rather than as a paragraph above the choices.
        about = stringResource(R.string.tools_insert_body)
    ) {
        ToolSection(stringResource(R.string.tools_insert_choose_pdf)) {
            if (source != null) PickedFileRow(source)
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (source == null) stringResource(R.string.tools_insert_choose_pdf) else stringResource(R.string.pages_insert_change_pdf),
                    maxLines = 1
                )
            }
            if (source != null && sourceFacts != null && sourcePages == null) ImageToolNotice(stringResource(R.string.pages_count_unknown))
        }

        ToolSection(stringResource(R.string.tools_insert_which_pages)) {
            ChoiceGroup {
                ChoiceCard(
                    selected = allPages,
                    title = stringResource(R.string.tools_insert_all_pages),
                    subtitle = sourcePages?.let { pluralStringResource(R.plurals.pdf_outcome_pages, it, it) },
                    onClick = { allPages = true }
                )
                ChoiceCard(selected = !allPages, title = stringResource(R.string.tools_insert_page_range), onClick = { allPages = false })
            }
            if (!allPages) {
                OutlinedTextField(
                    value = rangeText,
                    onValueChange = { rangeText = it },
                    label = { Text(stringResource(R.string.tools_insert_range_label)) },
                    singleLine = true,
                    isError = chosen?.isFailure == true && rangeText.isNotBlank(),
                    supportingText = chosen?.exceptionOrNull()?.message?.takeIf { rangeText.isNotBlank() }?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ToolSection(stringResource(R.string.tools_insert_where)) {
            ChoiceGroup {
                ChoiceCard(selected = position == POSITION_START, title = stringResource(R.string.tools_insert_at_start), onClick = { position = POSITION_START })
                ChoiceCard(selected = position == POSITION_AFTER, title = stringResource(R.string.tools_insert_after_page), onClick = { position = POSITION_AFTER })
                ChoiceCard(selected = position == POSITION_END, title = stringResource(R.string.tools_insert_at_end), onClick = { position = POSITION_END })
            }
            if (position == POSITION_AFTER) {
                OutlinedTextField(
                    value = afterText,
                    onValueChange = { typed -> if (typed.length <= 4) afterText = typed.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tools_insert_after_label, keptPages)) },
                    singleLine = true,
                    isError = afterText.isNotEmpty() && (after == null || after !in 1..keptPages),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (inserting != null && afterPosition in 0..keptPages) {
                InsertRibbon(keptPages, inserting, afterPosition)
            }
        }

        val base = baseFacts
        if (base != null) {
            val before = PdfFacts(keptPages, base.sizeBytes, base.firstPage, base.pageWidthPt, base.pageHeightPt)
            val extra = sourceFacts
            val outcome = if (inserting == null || problem != null) null else {
                val baseShare = shareOf(base.sizeBytes, keptPages, base.pageCount ?: keptPages) ?: 0L
                val extraShare = extra?.let { shareOf(it.sizeBytes, inserting, it.pageCount ?: inserting) } ?: 0L
                PdfOutcome(pages = keptPages + inserting, bytes = (baseShare + extraShare).takeIf { it > 0 })
            }
            PdfOutcomeRow(before, outcome)
        }
    }
}

/**
 * Where the new pages land: the organised pages as grey blocks, the inserted ones in the accent colour, in order, with
 * the page numbers at the joins.
 */
@Composable
private fun InsertRibbon(kept: Int, inserted: Int, after: Int) {
    val baseColour = MaterialTheme.colorScheme.surfaceContainerHighest
    val newColour = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (after == 0) pluralStringResource(R.plurals.pages_insert_summary_start, inserted, inserted, kept + inserted)
        else pluralStringResource(R.plurals.pages_insert_summary, inserted, inserted, after, kept + inserted)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = description }
        ) {
            val total = (kept + inserted).coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val w = size.width
            val h = size.height * 0.62f
            fun block(from: Int, to: Int, colour: Color) {
                if (to <= from) return
                val left = w * from / total
                val right = w * to / total
                drawRoundRect(colour, Offset(left + gap / 2f, 0f), Size((right - left - gap).coerceAtLeast(1f), h), CornerRadius(4.dp.toPx()))
            }
            block(0, after, baseColour)
            block(after, after + inserted, newColour)
            block(after + inserted, total, baseColour)
            drawRoundRect(outline, Offset.Zero, Size(w, h), CornerRadius(4.dp.toPx()), style = Stroke(1f))
            val style = TextStyle(color = labelColour, fontSize = 11.sp)
            fun label(text: String, x: Float) {
                val layout = measurer.measure(text, style)
                drawText(layout, topLeft = Offset((x - layout.size.width / 2f).coerceIn(0f, (w - layout.size.width).coerceAtLeast(0f)), h + 2.dp.toPx()))
            }
            label("1", 0f)
            if (after in 1 until kept) label("$after", w * after / total)
            label("${kept + inserted}", w)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
