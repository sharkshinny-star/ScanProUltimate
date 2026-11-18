package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageMargin
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.OfficeSurvey
import com.example.scanpro.imaging.OfficeToHtml
import com.example.scanpro.imaging.PageRanges
import com.example.scanpro.imaging.PaperSizes
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.PdfImageSurvey
import com.example.scanpro.imaging.PdfPreviewSession
import com.example.scanpro.imaging.PdfRepairEngine
import com.example.scanpro.imaging.PdfToImagesMath
import com.example.scanpro.imaging.PdfToolsEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/*
 * The convert and info tools' pages (F211): PDF to images, Images to PDF, Office to PDF, Extract images and Repair.
 * Each says, before anything runs, exactly what will come out (how many files, their pixel or page size, an honest
 * "About" size worked out from a real sample) and turns the button off with the reason when the input makes no sense.
 * File reading, rendering and sampling run off the main thread; estimates wait for the choices to settle.
 */

// ---------------------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------------------

/** A picked PDF opened once for a tool page: its facts, its page sizes and the renderer for samples. */
internal class OpenedPdf(
    /** Null when the phone's renderer cannot open the file. */
    val session: PdfPreviewSession?,
    val facts: PdfFacts,
    /** Every page's size in points, when asked for; otherwise only the first page's. */
    val pageSizes: List<Pair<Int, Int>>,
)

/** Opens [uri] for the page's lifetime (one copy in the cache), off the main thread; null while opening. */
@Composable
internal fun rememberOpenedPdf(uri: Uri, allPageSizes: Boolean): State<OpenedPdf?> {
    val context = LocalContext.current
    return produceState<OpenedPdf?>(null, uri) {
        var session: PdfPreviewSession? = null
        val opened = withContext(Dispatchers.IO) {
            val bytes = sizeOf(context, uri)
            session = try {
                PdfPreviewSession.open(context, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            val open = session ?: return@withContext OpenedPdf(null, PdfFacts(null, bytes, null, 0, 0), emptyList())
            val count = if (allPageSizes) open.pageCount else minOf(1, open.pageCount)
            val sizes = ArrayList<Pair<Int, Int>>(count)
            for (index in 0 until count) {
                ensureActive()
                sizes += open.pageSizePoints(index) ?: (595 to 842)
            }
            val first = sizes.firstOrNull() ?: (595 to 842)
            OpenedPdf(open, PdfFacts(open.pageCount, bytes, open.render(0, 900), first.first, first.second), sizes)
        }
        value = opened
        awaitDispose {
            // Closing waits for a sample being drawn; that wait belongs on a background thread, not the main one.
            session?.let { open -> Thread { open.close() }.start() }
        }
    }
}

/** What a look at a picked PDF with PDFBox found: the value, or why it could not be read. */
internal sealed interface Survey<out T> {
    class Ready<T>(val value: T) : Survey<T>
    object Locked : Survey<Nothing>
    object Failed : Survey<Nothing>
}

/** Reads [uri] with PDFBox off the main thread and hands the open document to [read]; null while reading. */
@Composable
internal fun <T> rememberPdfSurvey(uri: Uri, read: (PDDocument) -> T): State<Survey<T>?> {
    val context = LocalContext.current
    return produceState<Survey<T>?>(null, uri) {
        value = withContext(Dispatchers.IO) { surveyPdf(context, uri, read) }
    }
}

internal fun <T> surveyPdf(context: Context, uri: Uri, read: (PDDocument) -> T): Survey<T> {
    val temp = try {
        PdfToolsEngine.copyUriToTemp(context, uri, "survey_${UUID.randomUUID()}.pdf")
    } catch (_: Exception) {
        return Survey.Failed
    }
    return try {
        PdfToolsEngine.openPdf(temp).use { Survey.Ready(read(it)) }
    } catch (_: InvalidPasswordException) {
        Survey.Locked
    } catch (_: Exception) {
        Survey.Failed
    } catch (_: OutOfMemoryError) {
        Survey.Failed
    } finally {
        temp.delete()
    }
}

/** Now → After for tools whose input is not one PDF (pictures, an Office file): two short columns and the size change. */
@Composable
internal fun ConvertOutcomeRow(
    nowTitle: String,
    nowDetail: String,
    afterTitle: String?,
    afterDetail: String,
    beforeBytes: Long = 0,
    afterBytes: Long? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SizeColumn(stringResource(R.string.img_tool_now), nowTitle, nowDetail, Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                SizeColumn(stringResource(R.string.img_tool_after), afterTitle ?: "…", afterDetail)
                if (afterBytes != null && beforeBytes > 0) {
                    val change = ((afterBytes - beforeBytes) * 100f / beforeBytes).roundToInt()
                    if (change != 0) {
                        Spacer(Modifier.height(6.dp))
                        ChangeBadge(change)
                    }
                }
            }
        }
    }
}

/** One fact as a label and a value on a line; the value wraps rather than clipping at large font sizes. */
@Composable
internal fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

/** Facts on a card. */
@Composable
internal fun FactsCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
    }
}

/** Encodes [bitmap] as the engine does for [format]. */
private fun encodeLike(bitmap: Bitmap, format: ImageExportFormat, out: OutputStream) {
    when (format) {
        ImageExportFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, PdfToImagesMath.JPEG_QUALITY, out)
        ImageExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, PdfToImagesMath.WEBP_QUALITY, out)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, PdfToImagesMath.WEBP_QUALITY, out)
        }
    }
}

private fun formatName(format: ImageExportFormat): String = when (format) {
    ImageExportFormat.JPEG -> "JPG"
    ImageExportFormat.PNG -> "PNG"
    else -> "WebP"
}

// ---------------------------------------------------------------------------------------------------------------
// PDF to images
// ---------------------------------------------------------------------------------------------------------------

/** Samples are drawn at most this wide; the size of the full picture is scaled up from them. */
private const val SAMPLE_MAX_WIDTH = 2000

/**
 * Bytes the export will take: up to three of the chosen pages (first, middle, last) are drawn at the chosen size
 * (or [SAMPLE_MAX_WIDTH] wide when larger) and encoded as the engine encodes them, and the bytes per pixel are
 * scaled to every chosen page's pixels. Null when no sample could be drawn.
 */
private fun estimatePdfImages(session: PdfPreviewSession, chosen: List<Int>, sizes: List<Pair<Int, Int>>, scale: Float, format: ImageExportFormat): Long? {
    if (chosen.isEmpty()) return null
    fun pixelsOf(page: Int): Pair<Int, Int> {
        val (w, h) = sizes.getOrNull(page - 1) ?: (595 to 842)
        return PdfToImagesMath.pixelSize(w, h, scale)
    }
    var sampleBytes = 0L
    var samplePixels = 0L
    for (page in listOf(chosen.first(), chosen[chosen.size / 2], chosen.last()).distinct()) {
        val (width, _) = pixelsOf(page)
        // A cached page belongs to the session: it is read here, never recycled.
        val bitmap = session.render(page - 1, minOf(width, SAMPLE_MAX_WIDTH)) ?: continue
        val out = ByteArrayOutputStream()
        encodeLike(bitmap, format, out)
        sampleBytes += out.size()
        samplePixels += bitmap.width.toLong() * bitmap.height
    }
    if (samplePixels <= 0) return null
    val total = chosen.sumOf { page -> pixelsOf(page).let { (w, h) -> w.toLong() * h } }
    return PdfToImagesMath.scaleBytes(sampleBytes, samplePixels, total)
}

/** A finished size estimate; [bytes] is null when no sample could be drawn. Null itself means still working it out. */
private class Estimate(val bytes: Long?)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PdfToImagesDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (format: ImageExportFormat, pages: List<Int>?, scale: Float) -> Unit
) {
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }
    var dpi by rememberSaveable { mutableIntStateOf(PdfToImagesMath.DEFAULT_DPI) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    val opened by rememberOpenedPdf(uri, allPageSizes = true)
    val pdf = opened
    val pageCount = pdf?.facts?.pageCount
    val scale = PdfToImagesMath.scaleFor(dpi)

    // The pages to export, from the choice and the typed ranges: null while there is nothing valid to export.
    val parsed: Result<List<Int>>? = when {
        pageCount == null || pageCount <= 0 -> null
        allPages -> Result.success((1..pageCount).toList())
        rangesText.isBlank() -> null
        else -> PageRanges.parse(rangesText, pageCount).map { ranges -> PdfToImagesMath.chosenPages(ranges.flatMap { it.toList() }, pageCount) }
    }
    val chosen = parsed?.getOrNull()
    val sizes = pdf?.pageSizes.orEmpty()
    val pixelSizes = remember(chosen, sizes, scale) {
        chosen?.map { page -> sizes.getOrNull(page - 1)?.let { (w, h) -> PdfToImagesMath.pixelSize(w, h, scale) } ?: (0 to 0) }
    }
    val capped = remember(chosen, sizes, scale) {
        chosen?.count { page -> sizes.getOrNull(page - 1)?.let { (w, h) -> PdfToImagesMath.isCapped(w, h, scale) } == true } ?: 0
    }
    val estimate by produceState<Estimate?>(null, pdf?.session, chosen, scale, format) {
        value = null
        val session = pdf?.session
        if (session == null || chosen.isNullOrEmpty()) return@produceState
        delay(300)
        value = Estimate(withContext(Dispatchers.Default) { runCatching { estimatePdfImages(session, chosen, sizes, scale, format) }.getOrNull() })
    }

    val count = chosen?.size ?: 0
    val largest = pixelSizes?.maxByOrNull { (w, h) -> w.toLong() * h }
    val sameSize = pixelSizes?.distinct()?.size == 1
    val dims = largest?.let { (w, h) -> stringResource(R.string.img_tool_dims, w, h) }
    val dimsLabel = dims?.let { if (sameSize) it else stringResource(R.string.cvt_up_to, it) }
    val name = formatName(format)
    val support = when {
        pdf == null -> stringResource(R.string.cvt_reading_pdf)
        pdf.session == null || pageCount == null -> stringResource(R.string.cvt_pdf_unreadable)
        !allPages && rangesText.isBlank() -> stringResource(R.string.cvt_pages_type)
        parsed?.isFailure == true -> parsed?.exceptionOrNull()?.localizedMessage ?: stringResource(R.string.cvt_pages_type)
        count > 1 -> pluralStringResource(R.plurals.cvt_pdf2img_zip, count, count, name)
        else -> stringResource(R.string.cvt_pdf2img_single, name)
    }

    ToolPage(
        title = stringResource(R.string.tools_pdf2img_dialog_title),
        onClose = onDismiss,
        actionLabel = if (count > 0) pluralStringResource(R.plurals.cvt_pdf2img_action, count, count) else stringResource(R.string.tools_pdf2img_export_button),
        onAction = { if (chosen != null) onConfirm(format, if (allPages) null else chosen, scale) },
        actionEnabled = !chosen.isNullOrEmpty(),
        actionSupport = support,
        demoToolId = "pdf2img"
    ) {
        PickedFileRow(uri)
        if (pdf != null) {
            val after = when {
                chosen.isNullOrEmpty() -> PdfOutcome(pages = null, bytes = null)
                estimate == null -> null
                else -> PdfOutcome(pages = count, bytes = estimate?.bytes, approximate = true, files = count, pagesLabel = dimsLabel)
            }
            PdfOutcomeRow(pdf.facts, after)
        }
        PdfResultPreview(pdf?.facts, height = 220.dp)
        if (capped > 0) {
            ImageToolNotice(pluralStringResource(R.plurals.cvt_pdf2img_capped, capped, capped, PdfToImagesMath.MAX_SIDE))
        }
        if (pixelSizes != null && !sameSize) ToolHint(stringResource(R.string.cvt_pdf2img_mixed))

        ToolSection(stringResource(R.string.tools_pdf2img_pages)) {
            SegmentedChoice(
                options = listOf(
                    true to (pageCount?.let { stringResource(R.string.tools_pdf2img_all_count, it) } ?: stringResource(R.string.tools_pdf2img_all)),
                    false to stringResource(R.string.tools_pdf2img_some)
                ),
                selected = allPages,
                onSelect = { allPages = it }
            )
            if (!allPages) {
                OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it.take(200) },
                    label = { Text(stringResource(R.string.tools_pdf2img_pages_label)) },
                    singleLine = true,
                    isError = parsed?.isFailure == true,
                    supportingText = chosen?.let { { Text(pluralStringResource(R.plurals.cvt_pages_chosen, it.size, it.size)) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ToolSection(stringResource(R.string.tools_pdf2img_resolution)) {
            SegmentedChoice(
                options = PdfToImagesMath.DPI_CHOICES.map { it to stringResource(R.string.cvt_dpi, it) },
                selected = dpi,
                onSelect = { dpi = it }
            )
            ToolHint(
                stringResource(
                    when (dpi) {
                        96 -> R.string.cvt_dpi_screen
                        150 -> R.string.cvt_dpi_standard
                        220 -> R.string.cvt_dpi_sharp
                        else -> R.string.cvt_dpi_print
                    }
                ) + (dims?.let { "\n" + stringResource(R.string.cvt_dpi_pixels, dpi, it) } ?: "")
            )
        }

        ToolSection(stringResource(R.string.tools_pdf2img_format)) {
            SegmentedChoice(
                options = listOf(ImageExportFormat.JPEG to "JPG", ImageExportFormat.PNG to "PNG", ImageExportFormat.WEBP to "WebP"),
                selected = format,
                onSelect = { format = it }
            )
            ToolHint(
                stringResource(
                    when (format) {
                        ImageExportFormat.JPEG -> R.string.cvt_format_jpg
                        ImageExportFormat.PNG -> R.string.cvt_format_png
                        else -> R.string.cvt_format_webp
                    }
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Images to PDF
// ---------------------------------------------------------------------------------------------------------------

/** A picked picture as it will be placed: its size as shown (EXIF rotation applied) and its file. */
private class PickedImage(val width: Int, val height: Int, val bytes: Long, val path: String?)

private class PickedImages(val items: List<PickedImage>, val first: Bitmap?) {
    val totalBytes: Long get() = items.sumOf { it.bytes }
}

private fun readPicked(context: Context, uri: Uri): PickedImage? = runCatching {
    val path = if (uri.scheme == "file") uri.path else null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val rotation: Int
    val bytes: Long
    if (path != null) {
        BitmapFactory.decodeFile(path, bounds)
        rotation = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
        bytes = File(path).length()
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        rotation = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } }.getOrNull() ?: 0
        bytes = sizeOf(context, uri)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val turned = rotation % 180 != 0
    PickedImage(if (turned) bounds.outHeight else bounds.outWidth, if (turned) bounds.outWidth else bounds.outHeight, bytes, path)
}.getOrNull()

/** The picture's pixels as the PDF stores them: no longer side than [quality]'s limit. */
private fun storedPixels(image: PickedImage, quality: PdfQuality): Pair<Int, Int> {
    val long = maxOf(image.width, image.height)
    val k = minOf(1f, quality.maxImageDimension.toFloat() / long)
    return (image.width * k).toInt().coerceAtLeast(1) to (image.height * k).toInt().coerceAtLeast(1)
}

/** The PDF's size: up to three pictures are encoded as the PDF stores them, and scaled to every picture's pixels. */
private fun estimateImagesPdf(images: List<PickedImage>, quality: PdfQuality): Long? {
    if (images.isEmpty()) return null
    var sampleBytes = 0L
    var samplePixels = 0L
    for (image in listOf(images.first(), images[images.size / 2], images.last()).distinct()) {
        val path = image.path ?: continue
        val bitmap = ImageProcessor.decodeOrientedScaled(path, 1200)?.let(PdfExporter::onWhite) ?: continue
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, PdfExporter.jpegQualityFor(quality), out)
            // A sample is never larger than the stored picture: count it at the size it really has.
            sampleBytes += out.size()
            samplePixels += bitmap.width.toLong() * bitmap.height
        } finally {
            bitmap.recycle()
        }
    }
    if (samplePixels <= 0) return null
    val total = images.sumOf { storedPixels(it, quality).let { (w, h) -> w.toLong() * h } }
    // About 2 kB a page for the page's own objects.
    return PdfToImagesMath.scaleBytes(sampleBytes, samplePixels, total) + images.size * 2_000L
}

/** Pictures with fewer dots per inch than this on the page look soft when printed. */
private const val SOFT_PRINT_DPI = 100

/** Page size, margin and quality for Images to PDF, with the first page drawn as it will come out. */
@Composable
internal fun ImagesToPdfDialog(
    images: List<Uri>,
    onDismiss: () -> Unit,
    onConfirm: (PdfPageSize, PdfQuality, ImageMargin) -> Unit
) {
    val context = LocalContext.current
    var size by rememberSaveable { mutableStateOf(PdfPageSize.AUTO) }
    var quality by rememberSaveable { mutableStateOf(PdfQuality.BALANCED) }
    var margin by rememberSaveable { mutableStateOf(ImageMargin.SMALL) }
    val picked by produceState<PickedImages?>(null, images) {
        value = withContext(Dispatchers.IO) {
            val items = images.mapNotNull { readPicked(context, it) }
            val first = items.firstOrNull()?.path?.let { ImageProcessor.decodeOrientedScaled(it, 720) }
            PickedImages(items, first)
        }
    }
    val loaded = picked
    val estimate by produceState<Estimate?>(null, loaded, quality) {
        value = null
        val items = loaded?.items ?: return@produceState
        delay(250)
        value = Estimate(withContext(Dispatchers.Default) { runCatching { estimateImagesPdf(items, quality) }.getOrNull() })
    }
    // Pictures that will be printed at too few dots per inch for the chosen page.
    val soft = remember(loaded, size, margin, quality) {
        loaded?.items?.count { image ->
            val (w, h) = storedPixels(image, quality)
            val layout = PdfExporter.layoutPage(size, null, w, h, pageNumbers = false, border = margin.points)
            layout.width > 0f && w / (layout.width / 72f) < SOFT_PRINT_DPI
        } ?: 0
    }
    val unreadable = loaded?.let { images.size - it.items.size } ?: 0
    val count = loaded?.items?.size ?: images.size

    ToolPage(
        title = pluralStringResource(R.plurals.tools_img2pdf_title, count, count),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_img2pdf_create),
        onAction = { onConfirm(size, quality, margin) },
        actionEnabled = loaded != null && loaded.items.isNotEmpty(),
        actionSupport = when {
            loaded == null -> stringResource(R.string.cvt_reading_images)
            loaded.items.isEmpty() -> stringResource(R.string.tools_err_no_images)
            else -> pluralStringResource(R.plurals.cvt_img2pdf_support, count, count)
        },
        demoToolId = "img2pdf"
    ) {
        if (loaded != null) {
            val after = estimate
            ConvertOutcomeRow(
                nowTitle = pluralStringResource(R.plurals.cvt_pictures, loaded.items.size, loaded.items.size),
                nowDetail = formatBytes(context, loaded.totalBytes),
                afterTitle = pluralStringResource(R.plurals.pdf_outcome_pages, loaded.items.size, loaded.items.size),
                afterDetail = when {
                    after == null -> stringResource(R.string.img_tool_estimating)
                    after.bytes == null -> stringResource(R.string.pdf_outcome_size_unknown)
                    else -> stringResource(R.string.img_tool_about, formatBytes(context, after.bytes))
                },
                beforeBytes = loaded.totalBytes,
                afterBytes = after?.bytes
            )
            loaded.items.firstOrNull()?.let { first -> PagePlacementPreview(first, loaded.first, size, margin, quality) }
        }
        if (unreadable > 0) ImageToolNotice(pluralStringResource(R.plurals.cvt_img2pdf_unreadable, unreadable, unreadable))
        if (soft > 0) ImageToolNotice(pluralStringResource(R.plurals.cvt_img2pdf_soft, soft, soft, SOFT_PRINT_DPI))

        ToolSection(stringResource(R.string.tools_img2pdf_page_size)) {
            SegmentedChoice(
                options = PdfPageSize.entries.map { it to stringResource(it.labelRes) },
                selected = size,
                onSelect = { size = it }
            )
            ToolHint(
                if (size == PdfPageSize.AUTO) stringResource(R.string.tools_img2pdf_auto_hint)
                else stringResource(R.string.tools_img2pdf_fitted_hint, stringResource(size.labelRes)) + "\n" + stringResource(R.string.cvt_img2pdf_orientation)
            )
        }
        ToolSection(stringResource(R.string.cvt_margin)) {
            SegmentedChoice(
                options = listOf(
                    ImageMargin.NONE to stringResource(R.string.cvt_margin_none),
                    ImageMargin.SMALL to stringResource(R.string.cvt_margin_small),
                    ImageMargin.LARGE to stringResource(R.string.cvt_margin_large)
                ),
                selected = margin,
                onSelect = { margin = it }
            )
            ToolHint(stringResource(R.string.cvt_margin_hint, PaperSizes.toMillimetres(margin.points).roundToInt()))
        }
        ToolSection(stringResource(R.string.tools_img2pdf_quality)) {
            SegmentedChoice(
                options = PdfQuality.entries.map { it to stringResource(it.labelRes) },
                selected = quality,
                onSelect = { quality = it }
            )
            ToolHint(stringResource(R.string.cvt_img2pdf_quality_hint, quality.maxImageDimension))
        }
    }
}

/** The first page as it will come out: the page, its margin and the picture placed exactly as the PDF places it. */
@Composable
private fun PagePlacementPreview(first: PickedImage, thumbnail: Bitmap?, pageSize: PdfPageSize, margin: ImageMargin, quality: PdfQuality) {
    val (w, h) = storedPixels(first, quality)
    val layout = PdfExporter.layoutPage(pageSize, null, w, h, pageNumbers = false, border = margin.points)
    val image = remember(thumbnail) { thumbnail?.asImageBitmap() }
    val paper = PaperSizes.name(layout.pageWidth.toFloat(), layout.pageHeight.toFloat())
    val mm = stringResource(
        R.string.cvt_size_mm,
        PaperSizes.toMillimetres(layout.pageWidth.toFloat()).roundToInt(),
        PaperSizes.toMillimetres(layout.pageHeight.toFloat()).roundToInt()
    )
    val orientation = stringResource(if (layout.pageWidth > layout.pageHeight) R.string.cvt_landscape else R.string.cvt_portrait)
    val caption = listOfNotNull(paper, mm, orientation).joinToString(" · ")
    val outline = MaterialTheme.colorScheme.outlineVariant
    val marginColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = caption }) {
            val k = minOf(size.width * 0.9f / layout.pageWidth, size.height * 0.94f / layout.pageHeight)
            val pw = layout.pageWidth * k
            val ph = layout.pageHeight * k
            val left = (size.width - pw) / 2f
            val top = (size.height - ph) / 2f
            drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(left, top + 6f), Size(pw, ph), CornerRadius(6f))
            drawRect(Color.White, Offset(left, top), Size(pw, ph))
            val m = margin.points * k
            if (m > 0f) drawRect(marginColour, Offset(left + m, top + m), Size(pw - 2 * m, ph - 2 * m), style = Stroke(1.5f))
            val px = left + layout.left * k
            val py = top + layout.top * k
            val iw = layout.width * k
            val ih = layout.height * k
            if (image != null) {
                drawImage(image, dstOffset = IntOffset(px.roundToInt(), py.roundToInt()), dstSize = IntSize(iw.roundToInt().coerceAtLeast(1), ih.roundToInt().coerceAtLeast(1)))
            } else {
                drawRect(placeholder, Offset(px, py), Size(iw, ih))
            }
            drawRect(outline, Offset(left, top), Size(pw, ph), style = Stroke(1f))
        }
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Office to PDF
// ---------------------------------------------------------------------------------------------------------------

private fun readOfficeSurvey(context: Context, uri: Uri): OfficeSurvey = try {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
    val kind = OfficeToHtml.kindOf(resolver.getType(uri), name)
    resolver.openInputStream(uri)?.use { OfficeSurvey.of(kind, it) } ?: OfficeSurvey(kind, problem = OfficeSurvey.Problem.UNREADABLE)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    OfficeSurvey(null, problem = OfficeSurvey.Problem.UNREADABLE)
}

/** Office to PDF's page: what the file is, what the PDF will hold and what it leaves out, before converting. */
@Composable
internal fun OfficeToPdfPage(uri: Uri, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val survey by produceState<OfficeSurvey?>(null, uri) { value = withContext(Dispatchers.IO) { readOfficeSurvey(context, uri) } }
    val bytes by produceState(0L, uri) { value = withContext(Dispatchers.IO) { sizeOf(context, uri) } }
    val found = survey
    val problem = found?.problem
    val kindLabel = found?.kind?.let { stringResource(officeKindLabel(it)) } ?: stringResource(R.string.cvt_office_kind_unknown)
    val pages: String = when {
        found == null -> "…"
        found.slides != null -> pluralStringResource(R.plurals.cvt_office_slides, found.slides, found.slides)
        found.sheets != null -> pluralStringResource(R.plurals.cvt_office_sheets, found.sheets, found.sheets)
        found.lines != null -> pluralStringResource(R.plurals.cvt_office_lines, found.lines, found.lines)
        else -> stringResource(R.string.cvt_office_pages_later)
    }

    ToolPage(
        title = stringResource(R.string.tools_card_office),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.cvt_office_action),
        onAction = onConfirm,
        actionEnabled = found != null && problem == null,
        actionSupport = when {
            found == null -> stringResource(R.string.cvt_office_reading)
            problem == OfficeSurvey.Problem.LEGACY_OR_LOCKED -> stringResource(R.string.cvt_office_legacy)
            problem == OfficeSurvey.Problem.UNSUPPORTED -> stringResource(R.string.tools_err_office_unsupported)
            problem == OfficeSurvey.Problem.UNREADABLE -> stringResource(R.string.tools_err_office_unreadable)
            else -> stringResource(R.string.cvt_office_support)
        },
        demoToolId = "office"
    ) {
        PickedFileRow(uri)
        ConvertOutcomeRow(
            nowTitle = kindLabel,
            nowDetail = formatBytes(context, bytes),
            afterTitle = if (found == null) null else stringResource(R.string.cvt_office_after_pdf),
            afterDetail = pages
        )
        if (found != null && problem == null) {
            FactsCard {
                found.slides?.let { FactRow(stringResource(R.string.cvt_office_fact_slides), it.toString()) }
                found.sheets?.let { FactRow(stringResource(R.string.cvt_office_fact_sheets), it.toString()) }
                found.lines?.let { FactRow(stringResource(R.string.cvt_office_fact_lines), it.toString()) }
                if (found.kind != OfficeToHtml.Kind.TEXT && found.kind != OfficeToHtml.Kind.CSV) {
                    FactRow(stringResource(R.string.cvt_office_fact_pictures), found.pictures.toString())
                }
                FactRow(stringResource(R.string.cvt_office_fact_paper), stringResource(R.string.cvt_office_paper_value))
            }
            found.kind?.let { ToolHint(stringResource(officeKeptHint(it))) }
            if (found.hiddenSheets > 0) ImageToolNotice(pluralStringResource(R.plurals.cvt_office_hidden_sheets, found.hiddenSheets, found.hiddenSheets))
            if ((found.slides ?: 0) > OFFICE_MAX_SLIDES) ImageToolNotice(stringResource(R.string.office_note_slides_truncated, OFFICE_MAX_SLIDES.toString()))
        }
    }
}

/** The converter's own slide limit (OfficeToHtml's Pptx.MAX_SLIDES). */
private const val OFFICE_MAX_SLIDES = 500

private fun officeKindLabel(kind: OfficeToHtml.Kind): Int = when (kind) {
    OfficeToHtml.Kind.DOCX -> R.string.cvt_office_kind_docx
    OfficeToHtml.Kind.XLSX -> R.string.cvt_office_kind_xlsx
    OfficeToHtml.Kind.PPTX -> R.string.cvt_office_kind_pptx
    OfficeToHtml.Kind.TEXT -> R.string.cvt_office_kind_text
    OfficeToHtml.Kind.CSV -> R.string.cvt_office_kind_csv
    OfficeToHtml.Kind.HTML -> R.string.cvt_office_kind_html
}

private fun officeKeptHint(kind: OfficeToHtml.Kind): Int = when (kind) {
    OfficeToHtml.Kind.DOCX -> R.string.cvt_office_kept_docx
    OfficeToHtml.Kind.XLSX -> R.string.cvt_office_kept_xlsx
    OfficeToHtml.Kind.PPTX -> R.string.cvt_office_kept_pptx
    OfficeToHtml.Kind.TEXT, OfficeToHtml.Kind.CSV -> R.string.cvt_office_kept_text
    OfficeToHtml.Kind.HTML -> R.string.cvt_office_kept_html
}

// ---------------------------------------------------------------------------------------------------------------
// Extract images
// ---------------------------------------------------------------------------------------------------------------

/** Extract images' page: how many pictures the PDF holds, in which formats and about how large, before saving them. */
@Composable
internal fun ExtractImagesPage(uri: Uri, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val facts by rememberPdfFacts(uri)
    val surveyed by rememberPdfSurvey(uri) { PdfImageSurvey.of(it) }
    val result = surveyed
    val survey = (result as? Survey.Ready<PdfImageSurvey>)?.value
    val pictures = survey?.pictures ?: 0
    val formats = survey?.let { s ->
        listOfNotNull(
            s.jpeg.takeIf { it > 0 }?.let { "$it JPG" },
            (s.pictures - s.jpeg).takeIf { it > 0 }?.let { "$it PNG" }
        ).joinToString(" · ")
    }

    ToolPage(
        title = stringResource(R.string.tools_card_extract_images),
        onClose = onDismiss,
        actionLabel = if (pictures > 0) pluralStringResource(R.plurals.cvt_ximg_action, pictures, pictures) else stringResource(R.string.tools_card_extract_images),
        onAction = onConfirm,
        actionEnabled = pictures > 0,
        actionSupport = when (result) {
            null -> stringResource(R.string.cvt_ximg_looking)
            Survey.Locked -> stringResource(R.string.cvt_pdf_locked)
            Survey.Failed -> stringResource(R.string.cvt_pdf_unreadable)
            is Survey.Ready -> if (pictures == 0) stringResource(R.string.tools_ximg_err_none)
                else if (pictures > 1) stringResource(R.string.cvt_ximg_zip) else null
        },
        demoToolId = "extract_images"
    ) {
        PickedFileRow(uri)
        facts?.let { before ->
            val after = when {
                result == null -> null
                survey == null || pictures == 0 -> PdfOutcome(pages = null, bytes = null)
                else -> PdfOutcome(pages = pictures, bytes = survey.storedBytes, approximate = true, files = pictures, pagesLabel = formats)
            }
            PdfOutcomeRow(before, after)
        }
        if (survey != null && pictures > 0) {
            FactsCard {
                FactRow(stringResource(R.string.cvt_ximg_fact_pictures), pictures.toString())
                FactRow(stringResource(R.string.cvt_ximg_fact_pages), survey.pagesWithPictures.toString())
                if (!formats.isNullOrEmpty()) FactRow(stringResource(R.string.cvt_ximg_fact_formats), formats)
            }
            ToolHint(stringResource(R.string.cvt_ximg_hint))
            if (survey.tiny > 0) ToolHint(pluralStringResource(R.plurals.cvt_ximg_tiny, survey.tiny, survey.tiny))
            if (survey.maybeUnreadable > 0) ImageToolNotice(pluralStringResource(R.plurals.cvt_ximg_unreadable, survey.maybeUnreadable, survey.maybeUnreadable))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Repair
// ---------------------------------------------------------------------------------------------------------------

/** What the damaged file looks like to the phone's viewer and to the forgiving parser. */
private class RepairCheck(val looksLikePdf: Boolean, val viewerPages: Int?, val parserPages: Int?, val locked: Boolean) {
    val expected: Int get() = maxOf(viewerPages ?: 0, parserPages ?: 0)
}

private fun checkForRepair(context: Context, uri: Uri): RepairCheck? {
    val temp = try {
        PdfToolsEngine.copyUriToTemp(context, uri, "repair_check_${UUID.randomUUID()}.pdf")
    } catch (_: Exception) {
        return null
    }
    try {
        val head = temp.inputStream().use { input -> ByteArray(1024).let { buffer -> String(buffer, 0, input.read(buffer).coerceAtLeast(0), Charsets.ISO_8859_1) } }
        val viewer = PdfRepairEngine.viewerPageCount(temp)
        var locked = false
        val parser = try {
            PdfToolsEngine.openPdf(temp).use { it.numberOfPages }
        } catch (_: InvalidPasswordException) {
            locked = true
            null
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        return RepairCheck(head.contains("%PDF-"), viewer, parser, locked)
    } finally {
        temp.delete()
    }
}

/** Repair's page: what is wrong with the file and how many pages will come back, before repairing. */
@Composable
internal fun RepairPage(uri: Uri, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val facts by rememberPdfFacts(uri)
    val check by produceState<RepairCheck?>(null, uri) { value = withContext(Dispatchers.IO) { checkForRepair(context, uri) } }
    val found = check
    val nothing = found != null && !found.locked && found.expected == 0
    val viewer = found?.viewerPages
    val parser = found?.parserPages

    ToolPage(
        title = stringResource(R.string.tools_card_repair),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.cvt_repair_action),
        onAction = onConfirm,
        actionEnabled = found != null && !found.locked && !nothing,
        actionSupport = when {
            found == null -> stringResource(R.string.cvt_repair_checking)
            found.locked -> stringResource(R.string.tools_err_repair_protected)
            nothing -> stringResource(if (found.looksLikePdf) R.string.cvt_repair_nothing else R.string.cvt_repair_not_pdf)
            else -> null
        },
        demoToolId = "repair"
    ) {
        PickedFileRow(uri)
        val before = facts
        if (before != null && found != null && !found.locked && !nothing) {
            PdfOutcomeRow(before, PdfOutcome(pages = found.expected, bytes = null))
        }
        if (found != null) {
            FactsCard {
                FactRow(
                    stringResource(R.string.cvt_repair_viewer),
                    if (viewer != null) pluralStringResource(R.plurals.cvt_repair_opens, viewer, viewer) else stringResource(R.string.cvt_repair_cannot_open)
                )
                FactRow(
                    stringResource(R.string.cvt_repair_structure),
                    when {
                        found.locked -> stringResource(R.string.cvt_repair_password)
                        parser != null && parser > 0 -> pluralStringResource(R.plurals.cvt_repair_reads, parser, parser)
                        else -> stringResource(R.string.cvt_repair_damaged)
                    }
                )
            }
            if (!found.locked && !nothing) {
                val plan = when {
                    parser != null && parser > 0 && (viewer == null || viewer == parser) -> pluralStringResource(R.plurals.cvt_repair_plan_clean, parser, parser)
                    parser != null && parser > 0 && viewer != null && viewer > parser -> stringResource(R.string.cvt_repair_plan_missing, parser, viewer)
                    viewer != null && viewer > 0 -> pluralStringResource(R.plurals.cvt_repair_plan_pictures, viewer, viewer)
                    else -> null
                }
                plan?.let { ToolHint(it) }
            }
        }
    }
}
