package com.example.scanpro.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire

object PdfExporter {
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val LETTER_WIDTH = 612
    private const val LETTER_HEIGHT = 792
    private const val LEGAL_WIDTH = 612
    private const val LEGAL_HEIGHT = 1008
    private const val MARGIN = 18f
    private const val MAX_WORDS_PER_PAGE = 6000

    /** A page picture waiting to be placed under the page's text layer, as a JPEG file. */
    internal class PageImage(val index: Int, val file: File, val left: Float, val top: Float, val width: Float, val height: Float, val pageHeight: Int)

    /**
     * JPEG quality for the page pictures. Android's PdfDocument stores a drawn bitmap as lossless pixels, which made
     * one scanned page 15 MB; as JPEG it is a few hundred kilobytes and looks the same at these levels.
     */
    private fun jpegQualityFor(quality: PdfQuality): Int = when (quality) {
        PdfQuality.HIGH -> 90
        PdfQuality.BALANCED -> 80
        PdfQuality.SMALL -> 65
    }

    /**
     * Writes [pages] as a PDF. [decode] reads each page image at most `maxDimension` pixels on its longer side: scan
     * pages are already upright and opaque, while pictures picked for Images to PDF pass [decodePicture]. [beforePage]
     * runs before each page (1-based) is read, for progress, and may throw to stop a cancelled job between pages.
     * [pointSizes], one width-and-height pair in points per page, keeps each page at that size with no margin (Compress
     * PDF rebuilding a PDF's own pages); otherwise [pageSize] decides and the image sits inside a margin.
     */
    fun export(
        pages: List<ScanPage>,
        destination: File,
        quality: PdfQuality,
        pageSize: PdfPageSize,
        searchable: Boolean,
        pageNumbers: Boolean,
        decode: (path: String, maxDimension: Int) -> Bitmap? = ImageProcessor::decodeScaled,
        beforePage: (number: Int, total: Int) -> Unit = { _, _ -> },
        pointSizes: List<Pair<Int, Int>>? = null
    ) {
        userRequire(pages.isNotEmpty(), R.string.err_document_has_no_pages)
        destination.parentFile?.mkdirs()

        // The page pictures go in as JPEG under the text layer. If that step fails for any reason, the PDF is
        // written the old way (larger, but complete): a scan is never lost to a size optimisation.
        val scratch = File(destination.parentFile ?: File("."), ".pdf_jpeg_${UUID.randomUUID()}")
        try {
            scratch.mkdirs()
            val images = write(pages, destination, quality, pageSize, searchable, pageNumbers, decode, beforePage, scratch, pointSizes)
            runCatching { embedJpegs(destination, images) }.getOrElse { failure ->
                android.util.Log.w("PdfExporter", "JPEG embedding failed; wrote the larger lossless PDF instead", failure)
                write(pages, destination, quality, pageSize, searchable, pageNumbers, decode, beforePage, null, pointSizes)
            }
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** Places [images] under each page's text, as JPEG, and rewrites [pdf] in place. */
    internal fun embedJpegs(pdf: File, images: List<PageImage>) {
        val staged = File(pdf.parentFile, "${pdf.name}.staged")
        try {
            PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                images.forEach { item ->
                    val image = item.file.inputStream().use { JPEGFactory.createFromStream(doc, it) }
                    // PREPEND keeps the page picture behind the invisible text; the PDF's y axis runs upwards.
                    PDPageContentStream(doc, doc.getPage(item.index), PDPageContentStream.AppendMode.PREPEND, true, true).use { stream ->
                        stream.drawImage(image, item.left, item.pageHeight - item.top - item.height, item.width, item.height)
                    }
                }
                doc.save(staged)
            }
            staged.copyTo(pdf, overwrite = true)
        } finally {
            staged.delete()
        }
    }

    /**
     * Lays out every page. With [jpegDir] the page pictures are saved there as JPEG and returned for [embedJpegs]; without
     * it they are drawn straight into the PDF (lossless and large) and the result is empty.
     */
    private fun write(
        pages: List<ScanPage>,
        destination: File,
        quality: PdfQuality,
        pageSize: PdfPageSize,
        searchable: Boolean,
        pageNumbers: Boolean,
        decode: (path: String, maxDimension: Int) -> Bitmap?,
        beforePage: (number: Int, total: Int) -> Unit,
        jpegDir: File?,
        pointSizes: List<Pair<Int, Int>>?
    ): List<PageImage> {
        val placed = ArrayList<PageImage>()
        val pdf = PdfDocument()
        try {
            pages.sortedBy { it.position }.forEachIndexed { index, pageModel ->
                beforePage(index + 1, pages.size)
                // Fail loudly: silently skipping a page would produce a short PDF with wrong page numbers.
                val bitmap = decode(pageModel.imagePath, quality.maxImageDimension)
                    ?: userError(R.string.err_could_not_read_pdf_page, index + 1)
                val layout = layoutPage(pageSize, pointSizes?.getOrNull(index), bitmap.width, bitmap.height, pageNumbers)
                val pageWidth = layout.pageWidth
                val pageHeight = layout.pageHeight
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdf.startPage(info)
                val canvas = page.canvas
                // With JPEG pictures placed afterwards the page stays unpainted, so they are not covered; viewers show it white.
                if (jpegDir == null) canvas.drawColor(Color.WHITE)

                val drawWidth = layout.width
                val drawHeight = layout.height
                val left = layout.left
                val top = layout.top

                if (jpegDir == null) {
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, left + drawWidth, top + drawHeight),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    // The page is drawn; free the decoded bitmap now instead of holding every page until GC.
                    bitmap.recycle()
                } else {
                    // JPEG has no alpha: anything transparent is laid on white first, as the drawn page would show it.
                    val flat = onWhite(bitmap)
                    val file = File(jpegDir, "page_$index.jpg")
                    ImageProcessor.saveBitmapJpeg(flat, file, jpegQualityFor(quality))
                    flat.recycle()
                    placed += PageImage(index, file, left, top, drawWidth, drawHeight, pageHeight)
                }

                if (searchable && pageModel.ocrText.isNotBlank()) {
                    // Android PdfDocument records Canvas text as PDF text operators. The text is drawn in a colour
                    // that is practically transparent, so the scan looks unchanged while search and copy work.
                    val hiddenColor = Color.argb(2, 255, 255, 255)
                    val words = pageModel.ocrWords
                    if (words.isNotEmpty() && !pageModel.ocrEdited) {
                        // Each recognised word is placed under the printed word, so selection and highlights line up.
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hiddenColor }
                        words.asSequence().take(MAX_WORDS_PER_PAGE).forEach { word ->
                            val placed = WordPlacementMath.place(word, left, top, drawWidth, drawHeight) ?: return@forEach
                            val text = word.text.replace('\u0000', ' ')
                            paint.textSize = placed.textSize
                            paint.textScaleX = 1f
                            val natural = paint.measureText(text)
                            if (natural > 0f) paint.textScaleX = (placed.width / natural).coerceIn(0.2f, 5f)
                            canvas.drawText(text, placed.x, placed.baseline, paint)
                        }
                    } else {
                        // No word positions (older scans) or hand-corrected text: a small text strip along the page edge.
                        val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = hiddenColor
                            textSize = 1.4f
                        }
                        val lines = pageModel.ocrText
                            .replace('\u0000', ' ')
                            .lineSequence()
                            .flatMap { chunkText(it.trim(), 100).asSequence() }
                            .filter { it.isNotBlank() }
                            .take(160)
                            .toList()
                        // Top to bottom, so viewers that order text by position copy it in reading order.
                        var y = (pageHeight - 3f - 1.5f * (lines.size - 1)).coerceAtLeast(1f)
                        lines.forEach { line ->
                            canvas.drawText(line, 2f, y, hiddenPaint)
                            y += 1.5f
                        }
                    }
                }

                if (pageNumbers) {
                    val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.DKGRAY
                        textSize = 9f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(
                        "${index + 1} / ${pages.size}",
                        pageWidth / 2f,
                        pageHeight - 7f,
                        numberPaint
                    )
                }
                pdf.finishPage(page)
            }
            FileOutputStream(destination).use { output -> pdf.writeTo(output) }
        } finally {
            pdf.close()
        }
        return placed
    }

    /**
     * A picture picked for Images to PDF, read once and straight from the picked file: scaled, turned upright from its
     * EXIF rotation, and laid on white. Saving it as a JPEG first cost a second round of compression and turned
     * transparency (a PNG logo, signature or screenshot) black, because JPEG has no alpha channel.
     */
    fun decodePicture(path: String, maxDimension: Int): Bitmap? =
        ImageProcessor.decodeOrientedScaled(path, maxDimension)?.let(::onWhite)

    /**
     * [bitmap] composited onto white and marked opaque, as it looks on a white page, so the PDF holds a plain picture
     * rather than one with a separate transparency mask. A bitmap without alpha is returned as it is; otherwise the
     * result is [bitmap] itself when it can be drawn on, or a copy (and [bitmap] is recycled).
     */
    internal fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val target = if (bitmap.isMutable) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, true)?.also { bitmap.recycle() } ?: return bitmap
        // DST_OVER paints the white behind what is already there: transparent pixels turn white, half-transparent
        // ones blend with it, opaque ones keep their colour.
        Canvas(target).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
        target.setHasAlpha(false)
        return target
    }

    /** Backwards-compatible image-only export used by special tools. */
    fun export(pageFiles: List<File>, destination: File, quality: PdfQuality) {
        val pages = pageFiles.mapIndexed { index, file ->
            ScanPage(
                id = "export-$index",
                documentId = "export",
                position = index,
                sourcePath = file.absolutePath,
                imagePath = file.absolutePath
            )
        }
        export(pages, destination, quality, PdfPageSize.A4, searchable = false, pageNumbers = false)
    }

    /** A page's size in points and where its picture goes on it (top-left origin, as the Canvas draws). */
    internal class PageLayout(val pageWidth: Int, val pageHeight: Int, val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * The page for one picture: [pointSize] keeps that size with the picture edge to edge (Compress PDF rebuilding a
     * PDF's own pages); otherwise [pageSize] decides and the picture is centred inside the margin.
     */
    internal fun layoutPage(pageSize: PdfPageSize, pointSize: Pair<Int, Int>?, imageWidth: Int, imageHeight: Int, pageNumbers: Boolean): PageLayout {
        val (pageWidth, pageHeight) = pointSize ?: pageDimensions(pageSize, imageWidth, imageHeight)
        val margin = if (pointSize != null) 0f else MARGIN
        val bottomReserve = if (pageNumbers) 18f else 0f
        val scale = min(
            (pageWidth - margin * 2) / imageWidth.toFloat(),
            (pageHeight - margin * 2 - bottomReserve) / imageHeight.toFloat()
        )
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        return PageLayout(pageWidth, pageHeight, (pageWidth - drawWidth) / 2f, (pageHeight - bottomReserve - drawHeight) / 2f, drawWidth, drawHeight)
    }

    private fun pageDimensions(pageSize: PdfPageSize, imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        return when (pageSize) {
            PdfPageSize.A4 -> if (imageWidth > imageHeight) A4_HEIGHT to A4_WIDTH else A4_WIDTH to A4_HEIGHT
            PdfPageSize.LETTER -> if (imageWidth > imageHeight) LETTER_HEIGHT to LETTER_WIDTH else LETTER_WIDTH to LETTER_HEIGHT
            PdfPageSize.LEGAL -> if (imageWidth > imageHeight) LEGAL_HEIGHT to LEGAL_WIDTH else LEGAL_WIDTH to LEGAL_HEIGHT
            PdfPageSize.AUTO -> {
                val longSide = 842
                val aspect = imageWidth.toFloat() / imageHeight.toFloat()
                if (aspect >= 1f) longSide to max(200, (longSide / aspect).toInt())
                else max(200, (longSide * aspect).toInt()) to longSide
            }
        }
    }

    private fun chunkText(text: String, size: Int): List<String> {
        if (text.length <= size) return listOf(text)
        val output = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = min(text.length, start + size)
            if (end < text.length) {
                // Break at the last space so a word is never split across two text runs (which breaks search).
                val space = text.lastIndexOf(' ', end)
                if (space > start) end = space
            }
            output += text.substring(start, end).trim()
            start = if (end < text.length && text[end] == ' ') end + 1 else end
        }
        return output
    }
}
