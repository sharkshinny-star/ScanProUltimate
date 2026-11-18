package com.example.scanpro.feature.tools

import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.imaging.PdfCropTools
import com.example.scanpro.imaging.PdfRedactTools
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Margins offered in one tap for all four sides, in millimetres. */
private val MARGIN_PRESETS = listOf(5, 10, 15, 20)

/**
 * Crop PDF pages (F172, page F194): the first page with the crop frame drawn where it will fall (auto-trim finds the
 * content exactly as the tool does), the page size before and after in millimetres, and the button held back with the
 * reason when the margins would leave less than the tool accepts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CropPdfDialog(uri: Uri, onDismiss: () -> Unit, onConfirm: (PdfCropTools.CropOptions) -> Unit) {
    var auto by rememberSaveable { mutableStateOf(true) }
    var padding by rememberSaveable { mutableFloatStateOf(3f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) }
    var bottom by rememberSaveable { mutableFloatStateOf(0f) }
    var left by rememberSaveable { mutableFloatStateOf(0f) }
    var right by rememberSaveable { mutableFloatStateOf(0f) }
    val facts by rememberPdfFacts(uri)
    val f = facts
    val widthPt = f?.pageWidthPt?.toFloat() ?: 0f
    val heightPt = f?.pageHeightPt?.toFloat() ?: 0f
    val known = widthPt > 0f && heightPt > 0f
    // Auto-trim on the first page, found the way the tool finds it (pixels darker than paper), without padding: the
    // padding is added below, so moving its slider does not start the search again.
    val content by produceState<RectF?>(null, f) {
        val page = f?.firstPage ?: return@produceState
        if (!known) return@produceState
        value = withContext(BackgroundWork.tools) { PdfCropTools.contentBounds(page, widthPt, heightPt, 0f) }
    }

    val k = PdfCropTools.PT_PER_MM
    val keep: RectF? = when {
        !known -> null
        auto -> content?.let { c ->
            val p = padding * k
            RectF((c.left - p).coerceAtLeast(0f), (c.top - p).coerceAtLeast(0f), (c.right + p).coerceAtMost(widthPt), (c.bottom + p).coerceAtMost(heightPt))
        }
        else -> RectF(left * k, top * k, widthPt - right * k, heightPt - bottom * k)
    }
    val minSide = PdfCropTools.MIN_SIDE_PT
    val tooSmall = !auto && keep != null && (keep.width() < minSide || keep.height() < minSide)
    val noMargins = !auto && top + bottom + left + right <= 0f
    val options = if (auto) {
        PdfCropTools.CropOptions(PdfCropTools.Mode.AUTO, paddingMm = padding)
    } else {
        PdfCropTools.CropOptions(PdfCropTools.Mode.MARGINS, topMm = top, bottomMm = bottom, leftMm = left, rightMm = right)
    }
    // Each side may take up to 45 % of the page (100 mm at most), so the two opposite sides can never meet.
    val maxSide = { pt: Float -> if (pt <= 0f) 60f else floor(pt / k * 0.45f).coerceIn(5f, 100f) }
    val maxVertical = maxSide(heightPt)
    val maxHorizontal = maxSide(widthPt)

    ToolPage(
        title = stringResource(R.string.tools_card_crop_pdf),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_crop_pdf_action),
        onAction = { onConfirm(options) },
        actionEnabled = !tooSmall && !noMargins && f?.pageCount != null,
        actionSupport = when {
            f != null && f.pageCount == null -> stringResource(R.string.edit_err_cannot_read)
            noMargins -> stringResource(R.string.edit_crop_err_no_margins)
            tooSmall -> stringResource(R.string.edit_crop_err_too_small, (minSide / k).roundToInt())
            else -> null
        },
        demoToolId = "crop_pdf"
    ) {
        PickedFileRow(uri)
        val frame = MaterialTheme.colorScheme.primary
        val marks: (DrawScope.(Rect) -> Unit)? = if (keep == null || !known) null else fun DrawScope.(page: Rect) {
            val s = page.width / widthPt
            val kept = Rect(page.left + keep.left * s, page.top + keep.top * s, page.left + keep.right * s, page.top + keep.bottom * s)
            dimOutside(page, kept, Color.Black.copy(alpha = 0.5f))
            drawCropFrame(kept, frame)
        }
        PdfResultPreview(f, result = marks)
        if (f != null) {
            PdfOutcomeRow(f, f.pageCount?.let { PdfOutcome(it, f.sizeBytes, approximate = true) })
        }
        if (known && keep != null) {
            Text(
                stringResource(R.string.edit_crop_size_change, mmSize(widthPt, heightPt), mmSize(keep.width(), keep.height())),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (auto && known && f?.firstPage != null && content == null) {
            ToolHint(stringResource(R.string.edit_crop_first_blank))
        }

        ToolSection(stringResource(R.string.edit_crop_section_how)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.tools_crop_pdf_mode_auto), false to stringResource(R.string.tools_crop_pdf_mode_margins)),
                selected = auto,
                onSelect = { auto = it }
            )
            if (auto) {
                MillimetreSlider(stringResource(R.string.tools_crop_pdf_padding), padding, 0f..15f) { padding = it }
                ToolHint(stringResource(R.string.edit_crop_auto_hint))
            } else {
                Text(stringResource(R.string.edit_crop_all_sides), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MARGIN_PRESETS.forEach { mm ->
                        val v = mm.toFloat()
                        FilterChip(
                            selected = top == v && bottom == v && left == v && right == v,
                            onClick = {
                                top = v.coerceAtMost(maxVertical); bottom = v.coerceAtMost(maxVertical)
                                left = v.coerceAtMost(maxHorizontal); right = v.coerceAtMost(maxHorizontal)
                            },
                            label = { Text(stringResource(R.string.tools_crop_pdf_mm, mm)) }
                        )
                    }
                }
                MillimetreSlider(stringResource(R.string.tools_crop_pdf_top), top, 0f..maxVertical) { top = it }
                MillimetreSlider(stringResource(R.string.tools_crop_pdf_bottom), bottom, 0f..maxVertical) { bottom = it }
                MillimetreSlider(stringResource(R.string.tools_crop_pdf_left), left, 0f..maxHorizontal) { left = it }
                MillimetreSlider(stringResource(R.string.tools_crop_pdf_right), right, 0f..maxHorizontal) { right = it }
            }
        }
        ToolSection(stringResource(R.string.edit_section_what_happens)) {
            ToolHint(stringResource(R.string.tools_crop_pdf_body))
            ImageToolNotice(stringResource(R.string.edit_crop_not_redaction))
        }
    }
}

/** "210 × 297 mm" for a size in points. */
@Composable
private fun mmSize(widthPt: Float, heightPt: Float): String =
    stringResource(R.string.edit_size_mm, (widthPt / PdfCropTools.PT_PER_MM).roundToInt(), (heightPt / PdfCropTools.PT_PER_MM).roundToInt())

@Composable
private fun MillimetreSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.tools_crop_pdf_mm, value.roundToInt()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = range
        )
    }
}

/** The words typed into Redact: one per line or separated by commas or semicolons, trimmed, each once. */
internal fun splitRedactTerms(text: String): List<String> =
    text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** The document's text as read for Redact; [index] is null when it could not be read. */
private class RedactLoad(val index: PdfRedactTools.TextIndex?)

/** A finished count and the words and case setting it was made for. */
private class RedactCount(val terms: List<String>, val matchCase: Boolean, val search: PdfRedactTools.RedactSearch)

/**
 * Redact text (F172, page F194). The document's text is read once when the page opens; every change to the words is
 * then searched at once (after a short pause in typing), so the page shows, before anything runs, how many places
 * match on how many pages, each word's own count, the first page with its matches blacked out, and the size the
 * file will have once those pages are pictures. The search is the one the tool runs, so what is counted is what goes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RedactDialog(uri: Uri, onDismiss: () -> Unit, onConfirm: (terms: List<String>, matchCase: Boolean) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    val terms = remember(text) { splitRedactTerms(text) }
    val facts by rememberPdfFacts(uri)
    val load by produceState<RedactLoad?>(null, uri) {
        value = try {
            RedactLoad(PdfRedactTools.loadIndex(context, uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            RedactLoad(null)
        }
    }
    val index = load?.index
    // The last count is kept while the next one is worked out, so the numbers change in place instead of blinking.
    var counted by remember { mutableStateOf<RedactCount?>(null) }
    LaunchedEffect(index, terms, matchCase) {
        if (index == null || terms.isEmpty()) {
            counted = null
            return@LaunchedEffect
        }
        delay(250)
        val result = withContext(BackgroundWork.tools) { PdfRedactTools.search(index, terms, matchCase) }
        counted = RedactCount(terms, matchCase, result)
    }
    val last = counted
    // Only a count of exactly these words decides the button; an older one may still be shown below it.
    val found = last?.takeIf { it.terms == terms && it.matchCase == matchCase }?.search
    val shown = last?.search
    val f = facts
    val perPage by rememberPagePictureBytes(f, PdfRedactTools.REDACT_DPI, PdfRedactTools.REDACT_JPEG_QUALITY, PdfRedactTools.REDACT_MAX_SIDE)
    val outcome = redactOutcome(f, shown, perPage)

    val reason: String? = when {
        load != null && index == null -> stringResource(R.string.edit_err_cannot_read)
        index != null && !index.hasText -> stringResource(R.string.edit_redact_no_text)
        terms.isEmpty() -> stringResource(R.string.edit_redact_need_words)
        found != null && found.occurrences == 0 -> stringResource(R.string.tools_redact_err_no_matches)
        else -> null
    }
    ToolPage(
        title = stringResource(R.string.tools_card_redact),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_redact_action),
        onAction = { onConfirm(terms, matchCase) },
        actionEnabled = reason == null && found != null && found.occurrences > 0,
        actionSupport = reason ?: shown?.let {
            stringResource(
                R.string.edit_redact_found,
                pluralStringResource(R.plurals.edit_redact_matches, it.occurrences, it.occurrences),
                pluralStringResource(R.plurals.pdf_outcome_pages, it.pages, it.pages)
            )
        },
        demoToolId = "redact"
    ) {
        PickedFileRow(uri)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.tools_redact_terms_label)) },
            placeholder = { Text(stringResource(R.string.tools_redact_terms_hint)) },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tools_redact_match_case), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = matchCase, onCheckedChange = { matchCase = it })
        }
        // Each word with its own count, so a typo shows at once as a 0.
        if (terms.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                terms.forEachIndexed { i, term ->
                    val count = found?.perTerm?.getOrNull(i)
                    TermCount(term, count)
                }
            }
        }
        // Shown once, while the text is read; while typing, the words wait without a count instead (no line that
        // comes and goes with every key and moves the page below it).
        if (load == null) ToolHint(stringResource(R.string.edit_redact_reading))

        val firstPage = shown?.boxesOn(0).orEmpty()
        val marks: (DrawScope.(Rect) -> Unit)? = if (f == null || firstPage.isEmpty()) null
        else fun DrawScope.(page: Rect) { drawOnPage(page, f, firstPage, Color.Black) }
        PdfResultPreview(f, result = marks)
        if (shown != null && shown.occurrences > 0 && firstPage.isEmpty()) ToolHint(stringResource(R.string.edit_redact_none_on_first))
        if (f != null) PdfOutcomeRow(f, outcome)

        ToolSection(stringResource(R.string.edit_section_what_happens)) {
            ToolHint(stringResource(R.string.tools_redact_body))
            ToolHint(stringResource(R.string.edit_redact_search_rules))
        }
    }
}

/** A typed word and how often it was found; no count yet while searching, and the error colour for none. */
@Composable
private fun TermCount(term: String, count: Int?) {
    val none = count == 0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (none) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            if (count == null) term else stringResource(R.string.edit_redact_term_count, term, count),
            style = MaterialTheme.typography.labelLarge,
            color = if (none) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/**
 * The redacted file: the same pages; pages with a match become pictures (about [perPage] bytes each), the others keep
 * their share of the original size.
 */
private fun redactOutcome(facts: PdfFacts?, found: PdfRedactTools.RedactSearch?, perPage: Long?): PdfOutcome? {
    if (facts == null || found == null || perPage == null) return null
    val pages = facts.pageCount ?: return null
    if (pages <= 0) return null
    val pictured = found.pages.coerceAtMost(pages)
    val kept = (facts.sizeBytes * (pages - pictured).toDouble() / pages).toLong()
    return PdfOutcome(pages, kept + pictured * perPage, approximate = true)
}
