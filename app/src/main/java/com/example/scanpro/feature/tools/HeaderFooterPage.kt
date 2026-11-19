package com.example.scanpro.feature.tools

import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfHeaderFooterTools
import com.example.scanpro.imaging.PdfTextImage
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlin.math.roundToInt

/** The text sizes offered, in points; the tool's default is the middle one. */
private val HF_SIZES = listOf(8f, 10f, 13f)

/** One header or footer line on the first page, as the tool will place it (display points, top-left origin). */
private class PlacedLine(val text: String, val sizePt: Float, val xPt: Float, val baselinePt: Float, val widthPt: Float, val picture: Boolean)

/**
 * Where [raw] lands on a page [seenWidth] × [seenHeight] points as shown: the same substitution, font, shrink-to-fit
 * and placement the tool uses ([PdfHeaderFooterTools.edgeStamp] works in this same "as shown" space).
 */
private fun placeLine(raw: String, top: Boolean, config: PdfHeaderFooterTools.HeaderFooterConfig, seenWidth: Float, seenHeight: Float, page: Int, total: Int): PlacedLine? {
    if (raw.isBlank()) return null
    val text = PdfHeaderFooterTools.substitute(raw.trim(), page, total)
    val font = PDType1Font.HELVETICA
    val fontSize = config.fontSizePt.coerceIn(7f, 24f)
    val margin = config.marginPt.coerceIn(12f, 100f)
    val encodable = PdfTextImage.canEncode(font, text)
    fun widthAt(size: Float) = if (encodable) font.getStringWidth(text) / 1000f * size else PdfTextImage.widthPt(text, size, bold = false)
    val size = PdfHeaderFooterTools.fittedFontSize(fontSize, widthAt(fontSize), seenWidth - 2f * margin)
    val width = widthAt(size)
    val x = when (config.align) {
        PdfHeaderFooterTools.Align.LEFT -> margin
        PdfHeaderFooterTools.Align.CENTER -> (seenWidth - width) / 2f
        PdfHeaderFooterTools.Align.RIGHT -> seenWidth - margin - width
    }
    val baseline = if (top) margin + size else seenHeight - margin
    return PlacedLine(text, size, x, baseline, width, picture = !encodable)
}

/**
 * Header and footer (F173, page F212): the first page with the header and footer drawn at their real place and size
 * ({page} and {total} filled in, long text shrunk to fit as the tool does), quick patterns for page numbers, a text
 * size, and the button held back with the reason when there is nothing to add.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeaderFooterDialog(uri: Uri, onDismiss: () -> Unit, onConfirm: (PdfHeaderFooterTools.HeaderFooterConfig) -> Unit) {
    var header by rememberSaveable { mutableStateOf("") }
    var footer by rememberSaveable { mutableStateOf("") }
    var align by rememberSaveable { mutableStateOf(PdfHeaderFooterTools.Align.CENTER.name) }
    var skipFirst by rememberSaveable { mutableStateOf(false) }
    var sizePt by rememberSaveable { mutableFloatStateOf(10f) }
    val config = PdfHeaderFooterTools.HeaderFooterConfig(
        headerText = header,
        footerText = footer,
        align = PdfHeaderFooterTools.Align.valueOf(align),
        fontSizePt = sizePt,
        skipFirstPage = skipFirst
    )
    val facts by rememberPdfFacts(uri)
    val f = facts
    val pages = f?.pageCount
    val widthPt = f?.pageWidthPt?.toFloat() ?: 0f
    val heightPt = f?.pageHeightPt?.toFloat() ?: 0f
    val lines = remember(config, widthPt, heightPt, pages) {
        if (widthPt <= 0f || heightPt <= 0f) emptyList()
        else listOfNotNull(
            placeLine(config.headerText, true, config, widthPt, heightPt, 1, pages ?: 1),
            placeLine(config.footerText, false, config, widthPt, heightPt, 1, pages ?: 1)
        )
    }
    val onlyPageSkipped = skipFirst && pages == 1
    val stamped = when {
        pages == null -> 0
        skipFirst -> (pages - 1).coerceAtLeast(0)
        else -> pages
    }
    val shrunk = lines.minOfOrNull { it.sizePt }?.takeIf { it < sizePt - 0.05f }

    ToolPage(
        title = stringResource(R.string.tools_card_header_footer),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_hf_action),
        onAction = { onConfirm(config) },
        actionEnabled = config.hasAny && !onlyPageSkipped && pages != null,
        actionSupport = when {
            f != null && pages == null -> stringResource(R.string.edit_err_cannot_read)
            !config.hasAny -> stringResource(R.string.edit_hf_err_empty)
            onlyPageSkipped -> stringResource(R.string.edit_hf_err_only_page_skipped)
            else -> null
        },
        demoToolId = "header_footer",
        // Said once, in the folded "How it works", instead of a "What happens" section under the options.
        about = stringResource(R.string.tools_hf_body)
    ) {
        PickedFileRow(uri)
        val ink = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; color = 0xFF404040.toInt() } }
        val marks: (DrawScope.(Rect) -> Unit)? = if (lines.isEmpty() || skipFirst || widthPt <= 0f) null else fun DrawScope.(page: Rect) {
            val s = page.width / widthPt
            drawIntoCanvas { canvas ->
                lines.forEach { line ->
                    ink.textScaleX = 1f
                    ink.textSize = line.sizePt * s
                    val measured = ink.measureText(line.text)
                    // Stretched to the width Helvetica gives it in the PDF, so the preview is as wide as the result.
                    if (measured > 0f && !line.picture) ink.textScaleX = (line.widthPt * s / measured).coerceIn(0.6f, 1.6f)
                    canvas.nativeCanvas.drawText(line.text, page.left + line.xPt * s, page.top + line.baselinePt * s, ink)
                }
            }
        }
        PdfResultPreview(f, result = marks)
        if (skipFirst && config.hasAny && !onlyPageSkipped) ToolHint(stringResource(R.string.edit_hf_first_skipped))
        if (shrunk != null) ImageToolNotice(stringResource(R.string.edit_hf_shrunk, shrunk.roundToInt().coerceAtLeast(1)))

        ToolSection(stringResource(R.string.edit_hf_section_text)) {
            OutlinedTextField(
                value = header,
                onValueChange = { header = it.replace('\n', ' ') },
                label = { Text(stringResource(R.string.tools_hf_header_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = footer,
                onValueChange = { footer = it.replace('\n', ' ') },
                label = { Text(stringResource(R.string.tools_hf_footer_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ToolHint(stringResource(R.string.tools_hf_placeholders_hint))
            Text(stringResource(R.string.edit_hf_quick_footer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(stringResource(R.string.edit_hf_quick_page_of), "{page} / {total}", "{page}").forEach { pattern ->
                    FilterChip(selected = footer == pattern, onClick = { footer = pattern }, label = { Text(pattern, maxLines = 1) })
                }
            }
        }
        // Alignment, size and the first page are seldom changed, and the preview above shows them: folded away,
        // still applied while folded.
        MoreOptions(summary = if (skipFirst) stringResource(R.string.tools_hf_skip_first) else null) {
            ToolSection(stringResource(R.string.edit_hf_section_look)) {
                SegmentedChoice(
                    options = listOf(
                        PdfHeaderFooterTools.Align.LEFT.name to stringResource(R.string.tools_hf_align_left),
                        PdfHeaderFooterTools.Align.CENTER.name to stringResource(R.string.tools_hf_align_centre),
                        PdfHeaderFooterTools.Align.RIGHT.name to stringResource(R.string.tools_hf_align_right)
                    ),
                    selected = align,
                    onSelect = { align = it }
                )
                SegmentedChoice(
                    options = listOf(
                        HF_SIZES[0] to stringResource(R.string.edit_hf_size_small),
                        HF_SIZES[1] to stringResource(R.string.edit_hf_size_medium),
                        HF_SIZES[2] to stringResource(R.string.edit_hf_size_large)
                    ),
                    selected = sizePt,
                    onSelect = { sizePt = it }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = skipFirst, role = Role.Switch, onValueChange = { skipFirst = it })
                ) {
                    Text(stringResource(R.string.tools_hf_skip_first), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = skipFirst, onCheckedChange = null)
                }
            }
        }

        if (f != null) {
            val perPage = if (lines.any { it.picture }) 3_500L else 150L
            PdfOutcomeRow(f, pages?.let { PdfOutcome(it, f.sizeBytes + perPage * stamped, approximate = true) })
        }
    }
}
