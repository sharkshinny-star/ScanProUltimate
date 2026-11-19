package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.designsystem.ToolDemo
import com.example.scanpro.core.designsystem.toolDemoScene
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.PdfPreviewSession
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Shared pieces for the PDF tool pages (F195): what the picked PDF is (pages, size, first page), what the tool will
 * make of it (a Now → After row), the first page with the result drawn on it, and the "How it works" demonstration.
 * Each tool page uses them so every tool says, before it runs, what will come out.
 */

/** The picked PDF as a tool page needs it. [pageCount] is null when the file cannot be opened (a password, damage). */
internal class PdfFacts(
    val pageCount: Int?,
    val sizeBytes: Long,
    /** The first page rendered about 900 px wide, upright, on white; null if it could not be drawn. */
    val firstPage: Bitmap?,
    /** The first page's size in points (1/72 inch). */
    val pageWidthPt: Int,
    val pageHeightPt: Int,
)

/** Reads [uri] once, off the main thread; null while reading. */
@Composable
internal fun rememberPdfFacts(uri: Uri): State<PdfFacts?> {
    val context = LocalContext.current
    return produceState<PdfFacts?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadPdfFacts(context, uri) }
    }
}

internal suspend fun loadPdfFacts(context: Context, uri: Uri): PdfFacts {
    val size = sizeOf(context, uri)
    val session = try {
        PdfPreviewSession.open(context, uri)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        return PdfFacts(null, size, null, 0, 0)
    }
    return session.use {
        val (w, h) = it.pageSizePoints(0) ?: (595 to 842)
        PdfFacts(it.pageCount, size, it.render(0, 900), w, h)
    }
}

/** What a tool will produce from a PDF: pages (or sheets), files and a size, as far as they can be known beforehand. */
internal class PdfOutcome(
    val pages: Int?,
    val bytes: Long? = null,
    /** The size is an estimate ("About"). */
    val approximate: Boolean = true,
    /** More than one file comes out (Split, PDF to images). */
    val files: Int = 1,
    /** What the pages are called in the result, when not "pages" (e.g. "sheets" for N-up). */
    val pagesLabel: String? = null,
)

/**
 * Now → After for a PDF tool: pages and size of the picked file against what the tool will make, with the size change
 * as a badge. Shows "…" while [after] is still being worked out.
 */
@Composable
internal fun PdfOutcomeRow(before: PdfFacts, after: PdfOutcome?) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            OutcomeColumn(
                label = stringResource(R.string.img_tool_now),
                size = before.pageCount?.let { pluralStringResource(R.plurals.pdf_outcome_pages, it, it) } ?: "—",
                detail = formatBytes(context, before.sizeBytes),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                if (after == null) {
                    OutcomeColumn(stringResource(R.string.img_tool_after), "…", stringResource(R.string.img_tool_estimating))
                } else {
                    val pages = after.pages?.let { after.pagesLabel ?: pluralStringResource(R.plurals.pdf_outcome_pages, it, it) } ?: "—"
                    val headline = if (after.files > 1) pluralStringResource(R.plurals.pdf_outcome_files, after.files, after.files) else pages
                    val sizeText = after.bytes?.let {
                        val formatted = formatBytes(context, it)
                        if (after.approximate) stringResource(R.string.img_tool_about, formatted) else formatted
                    } ?: stringResource(R.string.pdf_outcome_size_unknown)
                    OutcomeColumn(
                        label = stringResource(R.string.img_tool_after),
                        size = headline,
                        detail = if (after.files > 1) "$pages · $sizeText" else sizeText
                    )
                    val bytes = after.bytes
                    if (bytes != null && before.sizeBytes > 0) {
                        val change = ((bytes - before.sizeBytes) * 100f / before.sizeBytes).roundToInt()
                        if (change != 0) {
                            Spacer(Modifier.height(6.dp))
                            ChangeBadge(change)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The first page large, with the result drawn on it: [result] paints over the page in its own coordinates (the rect
 * the page occupies) and [resultFilter] tints it (greyscale, invert). A switch underneath shows Original or Result, so
 * the person sees where the header goes, what is blacked out or how the sheet is laid out before running the tool.
 */
@Composable
internal fun PdfResultPreview(
    facts: PdfFacts?,
    result: (DrawScope.(page: Rect) -> Unit)? = null,
    resultFilter: ColorFilter? = null,
    height: androidx.compose.ui.unit.Dp = 300.dp,
) {
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    val hasResult = result != null || resultFilter != null
    val description = stringResource(if (hasResult && !showOriginal) R.string.img_tool_result else R.string.img_tool_original)
    val pageColour = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(20.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = facts?.firstPage
            if (bitmap == null) {
                if (facts == null) FileLoadingAnimation(size = 88.dp)
            } else {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                    val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val page = Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
                    drawRoundRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f), page.topLeft + Offset(0f, 6f), page.size, CornerRadius(6f))
                    drawImage(
                        image,
                        dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        colorFilter = if (showOriginal) null else resultFilter
                    )
                    drawRect(pageColour, page.topLeft, page.size, style = Stroke(1f))
                    if (!showOriginal) result?.invoke(this, page)
                }
            }
        }
        if (hasResult && facts?.firstPage != null) {
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.img_tool_result), true to stringResource(R.string.img_tool_original)),
                selected = showOriginal,
                onSelect = { showOriginal = it }
            )
        }
    }
}

/**
 * "How it works": the tool's own demonstration, with a word on what it does ([about] when the page gives one, else
 * the tool's one-line introduction). Folded when the page opens (declutter, 2026-11-19: it used to open at full height
 * above the file), except on a new user's first visit to the tool; one tap shows or hides it, and it stays as left while
 * the page is open.
 */
@Composable
internal fun ToolDemoCard(toolId: String?, about: String? = null) {
    val scene = toolId?.let { toolDemoScene(it) }
    if (scene == null && about == null) return
    // Open the first time someone new to the app reaches this tool, so they see what it does without hunting for it;
    // folded on every later visit until they tap it (owner, 2026-11-19). "New" is as for the other introductions
    // (FirstRunHints: armed by finishing the welcome, re-armed by Settings > Show introductions again), so someone
    // updating from an older version is not shown every card again.
    val context = androidx.compose.ui.platform.LocalContext.current
    val firstVisit = androidx.compose.runtime.remember(toolId) {
        toolId != null && com.example.scanpro.data.FirstRunHints.isDue(context, com.example.scanpro.data.FirstRunHints.demoKey(toolId))
    }
    var open by rememberSaveable(toolId) { mutableStateOf(firstVisit) }
    androidx.compose.runtime.LaunchedEffect(toolId) {
        if (firstVisit && toolId != null) {
            com.example.scanpro.data.FirstRunHints.markSeen(context, com.example.scanpro.data.FirstRunHints.demoKey(toolId))
        }
    }
    val stateText = stringResource(if (open) R.string.tool_demo_shown else R.string.tool_demo_hidden)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = stateText }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.tool_demo_how_it_works), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (scene != null) ToolDemo(scene, Modifier.fillMaxWidth().height(160.dp))
                    val text = about ?: toolId?.let { id -> toolIntro(id)?.let { stringResource(it.description) } }
                    if (text != null) {
                        if (scene != null) Spacer(Modifier.height(8.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
