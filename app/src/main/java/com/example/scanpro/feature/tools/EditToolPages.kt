package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.core.designsystem.ToolDemo
import com.example.scanpro.core.designsystem.toolDemoScene
import com.example.scanpro.imaging.PdfCleanupTools
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/*
 * Edit and secure tools (F212): Greyscale, Invert colours and Flatten as full tool pages, plus the parts the other pages
 * of this group share: an honest size estimate for tools that turn pages into pictures, boxes drawn on the first page
 * at their real place, and a "How it works" button for the editors that have no room for the demonstration card.
 */

/** About this much is added to a PDF for each page that becomes a picture (page object, image header, xref). */
private const val PICTURE_PAGE_OVERHEAD = 900L

/**
 * About how many bytes one page becomes when it is redrawn as a JPEG at [dpi] and [quality] (at most [maxSide] pixels
 * long), worked out from the first page of [facts]: its preview is encoded the same way and the size scaled to the
 * page's real pixel count. Text pages grow less than their area (most bytes sit on edges), so the scale uses the
 * one-and-a-half power of the side ratio. [recolour] is applied first (grey, inverted). Null while it is being worked
 * out or when the page could not be drawn.
 */
@Composable
internal fun rememberPagePictureBytes(
    facts: PdfFacts?,
    dpi: Int,
    quality: Int,
    maxSide: Int,
    recolour: ((Bitmap) -> Unit)? = null,
): State<Long?> = produceState<Long?>(null, facts, dpi, quality, maxSide) {
    val known = facts ?: return@produceState
    val page = known.firstPage ?: return@produceState
    if (known.pageWidthPt <= 0 || known.pageHeightPt <= 0) return@produceState
    value = try {
        withContext(BackgroundWork.tools) {
            val sample = page.copy(Bitmap.Config.ARGB_8888, true) ?: return@withContext null
            try {
                recolour?.invoke(sample)
                val buffer = ByteArrayOutputStream()
                sample.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                val scale = min(dpi / 72f, maxSide / max(known.pageWidthPt, known.pageHeightPt).toFloat())
                val targetWidth = known.pageWidthPt * scale
                val ratio = (targetWidth / sample.width).toDouble()
                (buffer.size() * ratio.pow(1.5)).toLong() + PICTURE_PAGE_OVERHEAD
            } finally {
                sample.recycle()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

/** Draws [rects] (display points of the first page) onto [page], the rectangle the page occupies in the preview. */
internal fun DrawScope.drawOnPage(page: Rect, facts: PdfFacts, rects: List<android.graphics.RectF>, fill: Color, border: Color? = null) {
    if (facts.pageWidthPt <= 0) return
    val k = page.width / facts.pageWidthPt
    rects.forEach { r ->
        val topLeft = Offset(page.left + r.left * k, page.top + r.top * k)
        val size = Size(r.width() * k, r.height() * k)
        drawRect(fill, topLeft, size)
        if (border != null) drawRect(border, topLeft, size, style = Stroke(1.5.dp.toPx()))
    }
}

/**
 * A "How it works" button for the full-screen editors (Watermark, Redact areas, Sign) whose page leaves no room for
 * the demonstration card: it opens the tool's own scene in a small window.
 */
@Composable
internal fun ToolDemoHelpButton(toolId: String) {
    val scene = toolDemoScene(toolId) ?: return
    var open by rememberSaveable(toolId) { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.PlayCircle, contentDescription = stringResource(R.string.tool_demo_how_it_works))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.tool_demo_how_it_works)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolDemo(scene, Modifier.fillMaxWidth().height(200.dp))
                    toolIntro(toolId)?.let {
                        Text(stringResource(it.description), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }
}

/** Label and value on one line, value bold: a count on a summary card. */
@Composable
internal fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** A quiet card holding [SummaryLine]s. */
@Composable
internal fun SummaryCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/**
 * Greyscale and Invert colours (F212): the first page with the real colour change applied (the same weights the tool
 * uses), what the file becomes (every page redrawn as a picture at 150 dpi, so the size can go either way) and the
 * honest note that text stops being selectable.
 */
@Composable
internal fun RecolourPdfPage(uri: Uri, invert: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val facts by rememberPdfFacts(uri)
    val f = facts
    val perPage by rememberPagePictureBytes(
        f,
        PdfCleanupTools.GREYSCALE_DPI,
        PdfCleanupTools.GREYSCALE_JPEG_QUALITY,
        PdfCleanupTools.GREYSCALE_MAX_SIDE,
        recolour = if (invert) PdfCleanupTools::toInvertedColors else PdfCleanupTools::toGreyscale
    )
    // The tool's own arithmetic: Rec. 601 grey (0.299 R + 0.587 G + 0.114 B), or 255 minus each channel.
    val filter = remember(invert) {
        ColorFilter.colorMatrix(
            ColorMatrix(
                if (invert) floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ) else floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    val pages = f?.pageCount
    val unreadable = f != null && pages == null
    val estimate = perPage
    ToolPage(
        title = stringResource(if (invert) R.string.tools_card_invert_pdf else R.string.tools_card_greyscale),
        onClose = onDismiss,
        actionLabel = stringResource(if (invert) R.string.tools_invert_confirm_action else R.string.tools_grey_confirm_action),
        onAction = onConfirm,
        actionEnabled = pages != null && pages > 0,
        actionSupport = if (unreadable) stringResource(R.string.edit_err_cannot_read) else null,
        demoToolId = if (invert) "invert_pdf" else "greyscale"
    ) {
        PickedFileRow(uri)
        PdfResultPreview(f, resultFilter = filter, uri = uri)
        if (f != null) {
            PdfOutcomeRow(f, if (pages == null || estimate == null) null else PdfOutcome(pages, estimate * pages, approximate = true))
        }
        ToolSection(stringResource(R.string.edit_section_what_happens)) {
            ToolHint(
                stringResource(
                    if (invert) R.string.tools_invert_confirm_body else R.string.tools_grey_confirm_body,
                    PdfCleanupTools.GREYSCALE_DPI
                )
            )
            ToolHint(stringResource(R.string.edit_recolour_estimate_note))
        }
    }
}

/** What Flatten found in the picked file; [preview] is null when the file could not be read. */
private class FlattenLoad(val preview: PdfCleanupTools.FlattenPreview?)

/**
 * Flatten (F212): counts what will be drawn into the pages before anything runs (form fields, comments and stamps,
 * links that stay), marks them on the first page, and refuses a file with nothing to flatten instead of making a copy
 * that is the same.
 */
@Composable
internal fun FlattenPage(uri: Uri, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val facts by rememberPdfFacts(uri)
    val load by produceState<FlattenLoad?>(null, uri) {
        value = try {
            FlattenLoad(PdfCleanupTools.previewFlatten(context, uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            FlattenLoad(null)
        }
    }
    val f = facts
    val found = load?.preview
    val nothing = found != null && found.fields + found.annotations == 0
    val failed = load != null && found == null
    val mint = Color(0xFF14B8A6)
    ToolPage(
        title = stringResource(R.string.tools_card_flatten),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_flatten_confirm_action),
        onAction = onConfirm,
        actionEnabled = found != null && !nothing,
        actionSupport = when {
            failed -> stringResource(R.string.edit_err_cannot_read)
            nothing -> stringResource(R.string.tools_flatten_nothing)
            else -> null
        },
        demoToolId = "flatten"
    ) {
        PickedFileRow(uri)
        val marks: (DrawScope.(Rect) -> Unit)? = if (f == null || found == null || found.firstPageAreas.isEmpty()) null
        else fun DrawScope.(page: Rect) { drawOnPage(page, f, found.firstPageAreas, mint.copy(alpha = 0.22f), mint) }
        PdfResultPreview(f, result = marks)
        if (found != null && found.firstPageAreas.isNotEmpty()) ToolHint(stringResource(R.string.edit_flatten_marked))
        if (f != null) {
            PdfOutcomeRow(f, f.pageCount?.let { PdfOutcome(it, f.sizeBytes, approximate = true) })
        }
        if (found != null) {
            ToolSection(stringResource(R.string.edit_section_found)) {
                SummaryCard {
                    SummaryLine(stringResource(R.string.edit_flatten_fields), found.fields.toString())
                    SummaryLine(stringResource(R.string.edit_flatten_comments), found.annotations.toString())
                    SummaryLine(stringResource(R.string.edit_flatten_links), found.links.toString())
                }
            }
        }
        ToolSection(stringResource(R.string.edit_section_what_happens)) {
            ToolHint(stringResource(R.string.edit_flatten_what_happens))
        }
    }
}

/** A dark border around [page], used by previews that show what is cut away. */
internal fun DrawScope.dimOutside(page: Rect, keep: Rect, colour: Color) {
    drawRect(colour, page.topLeft, Size(page.width, keep.top - page.top))
    drawRect(colour, Offset(page.left, keep.bottom), Size(page.width, page.bottom - keep.bottom))
    drawRect(colour, Offset(page.left, keep.top), Size(keep.left - page.left, keep.height))
    drawRect(colour, Offset(keep.right, keep.top), Size(page.right - keep.right, keep.height))
}

/** A rounded frame with corner handles, as crop frames are drawn elsewhere in the app. */
internal fun DrawScope.drawCropFrame(keep: Rect, colour: Color) {
    drawRoundRect(colour, keep.topLeft, keep.size, CornerRadius(2.dp.toPx()), style = Stroke(2.dp.toPx()))
    val arm = min(keep.width, keep.height) * 0.12f
    val w = 4.dp.toPx()
    listOf(keep.topLeft to Offset(1f, 1f), Offset(keep.right, keep.top) to Offset(-1f, 1f), Offset(keep.left, keep.bottom) to Offset(1f, -1f), keep.bottomRight to Offset(-1f, -1f))
        .forEach { (corner, dir) ->
            drawLine(colour, corner, corner + Offset(arm * dir.x, 0f), w)
            drawLine(colour, corner, corner + Offset(0f, arm * dir.y), w)
        }
}
