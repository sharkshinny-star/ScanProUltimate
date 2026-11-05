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
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Writes [pages] as a PDF. [decode] reads each page image at most `maxDimension` pixels on its longer side: scan
     * pages are already upright and opaque, while pictures picked for Images to PDF pass [decodePicture]. [beforePage]
     * runs before each page (1-based) is read, for progress, and may throw to stop a cancelled job between pages.
     */
    fun export(
        pages: List<ScanPage>,
        destination: File,
        quality: PdfQuality,
        pageSize: PdfPageSize,
        searchable: Boolean,
        pageNumbers: Boolean,
        decode: (path: String, maxDimension: Int) -> Bitmap? = ImageProcessor::decodeScaled,
        beforePage: (number: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        userRequire(pages.isNotEmpty(), R.string.err_document_has_no_pages)
        destination.parentFile?.mkdirs()

        val pdf = PdfDocument()
        try {
            pages.sortedBy { it.position }.forEachIndexed { index, pageModel ->
                beforePage(index + 1, pages.size)
                // Fail loudly: silently skipping a page would produce a short PDF with wrong page numbers.
                val bitmap = decode(pageModel.imagePath, quality.maxImageDimension)
                    ?: userError(R.string.err_could_not_read_pdf_page, index + 1)
                val (pageWidth, pageHeight) = pageDimensions(pageSize, bitmap.width, bitmap.height)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdf.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val bottomReserve = if (pageNumbers) 18f else 0f
                val availableWidth = pageWidth - MARGIN * 2
                val availableHeight = pageHeight - MARGIN * 2 - bottomReserve
                val scale = min(
                    availableWidth / bitmap.width.toFloat(),
                    availableHeight / bitmap.height.toFloat()
                )
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val left = (pageWidth - drawWidth) / 2f
                val top = (pageHeight - bottomReserve - drawHeight) / 2f

                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(left, top, left + drawWidth, top + drawHeight),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                // The page is drawn; free the decoded bitmap now instead of holding every page until GC.
                bitmap.recycle()

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
