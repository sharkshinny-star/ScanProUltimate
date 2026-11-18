package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userRequire
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * N-up (F173): two, four, six or nine pages drawn on each sheet, for reading drafts and slides on paper. Pages are imported as
 * form objects ([LayerUtility]), so text, vectors and images stay as they are, merely moved and scaled; nothing is
 * rasterised. Each page is placed as it is displayed (its /Rotate applied), scaled to fit its cell and centred, in
 * reading order (left to right, then down).
 */
object PdfNupTools {
    /** Sheet margin and the gap between cells, in points. */
    private const val MARGIN_PT = 18f
    private const val GUTTER_PT = 12f

    /** Pages per sheet the tool offers (F194: 6 and 9 added). */
    val PER_SHEET_CHOICES = listOf(2, 4, 6, 9)

    /** A cell on the sheet: lower-left corner and size, in sheet points. */
    internal class Cell(val x: Float, val y: Float, val width: Float, val height: Float)

    /** Sheets needed for [pages] pages at [perSheet] a sheet: the last one may be part empty. */
    fun sheetCount(pages: Int, perSheet: Int): Int = if (pages <= 0 || perSheet <= 0) 0 else (pages + perSheet - 1) / perSheet

    /**
     * The sheet for pages whose first one is [firstWidth] by [firstHeight] as seen: the same paper, turned sideways for
     * 2 and 6 a sheet (an odd number of rows and columns would otherwise squash the pages), kept as it is for 4 and 9.
     */
    internal fun sheetSize(perSheet: Int, firstWidth: Float, firstHeight: Float): Pair<Float, Float> =
        if (perSheet == 2 || perSheet == 6) firstHeight to firstWidth else firstWidth to firstHeight

    /**
     * Columns and rows: the longer side of the sheet gets more of them. A landscape page used to be put two side by
     * side on a landscape sheet, each at under half its size; on a portrait sheet, one above the other, it is two
     * thirds bigger.
     */
    internal fun grid(perSheet: Int, sheetWidth: Float, sheetHeight: Float): Pair<Int, Int> {
        val wide = sheetWidth > sheetHeight
        return when (perSheet) {
            2 -> if (wide) 2 to 1 else 1 to 2
            4 -> 2 to 2
            6 -> if (wide) 3 to 2 else 2 to 3
            9 -> 3 to 3
            else -> throw IllegalArgumentException("perSheet must be one of $PER_SHEET_CHOICES")
        }
    }

    /**
     * The cells of one sheet in reading order: left to right, then down (so the first row is the TOP row, although
     * PDF y runs up). The margin and gutter shrink on small paper so a receipt-sized sheet still has room for pages.
     */
    internal fun cells(perSheet: Int, sheetWidth: Float, sheetHeight: Float): List<Cell> {
        val (columns, rows) = grid(perSheet, sheetWidth, sheetHeight)
        val short = minOf(sheetWidth, sheetHeight)
        val margin = minOf(MARGIN_PT, short * 0.04f)
        val gutter = minOf(GUTTER_PT, short * 0.025f)
        val cellW = (sheetWidth - 2 * margin - (columns - 1) * gutter) / columns
        val cellH = (sheetHeight - 2 * margin - (rows - 1) * gutter) / rows
        return buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val x = margin + column * (cellW + gutter)
                    val y = sheetHeight - margin - cellH - row * (cellH + gutter)
                    add(Cell(x, y, cellW, cellH))
                }
            }
        }
    }

    /** How much a page [pageWidth] by [pageHeight] (as seen) shrinks to fit [cell]: 0.5 is half size. */
    internal fun fitScale(cell: Cell, pageWidth: Float, pageHeight: Float): Float =
        if (pageWidth <= 0f || pageHeight <= 0f) 0f else min(cell.width / pageWidth, cell.height / pageHeight)

    /**
     * The matrix (a, b, c, d, e, f) that maps content in [box]'s coordinates to how the page is SEEN after
     * [rotation]: the seen picture stands upright with its lower-left corner at the origin, [seenSize] big.
     * The inverse of the mapping `PdfCropTools.displayRectToUserSpace` uses, with y measured up instead of down.
     */
    internal fun seenMatrix(box: PDRectangle, rotation: Int): FloatArray {
        val x0 = box.lowerLeftX
        val y0 = box.lowerLeftY
        return when (((rotation % 360) + 360) % 360) {
            90 -> floatArrayOf(0f, -1f, 1f, 0f, -y0, box.width + x0)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, box.width + x0, box.height + y0)
            270 -> floatArrayOf(0f, 1f, -1f, 0f, box.height + y0, -x0)
            else -> floatArrayOf(1f, 0f, 0f, 1f, -x0, -y0)
        }
    }

    internal fun seenSize(box: PDRectangle, rotation: Int): Pair<Float, Float> =
        WatermarkGeometry.seenSize(box.width, box.height, rotation)

    suspend fun nup(
        context: Context,
        inputUri: Uri,
        perSheet: Int,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        password: String = ""
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        require(perSheet in PER_SHEET_CHOICES) { "perSheet must be one of $PER_SHEET_CHOICES" }
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "nup_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.openPdf(temp, password).use { source ->
                val total = source.numberOfPages
                userRequire(total > 1, R.string.tools_nup_err_one_page)
                // The sheet is the first page's paper: the same way up for 4 and 9, turned sideways for 2 and 6, so
                // two portrait pages sit next to each other on a landscape sheet and two landscape pages one above the
                // other on a portrait one.
                val first = source.getPage(0)
                val (firstW, firstH) = seenSize(first.cropBox, first.rotation)
                val (sheetW, sheetH) = sheetSize(perSheet, firstW, firstH)
                val sheets = sheetCount(total, perSheet)
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
                    val layer = LayerUtility(out)
                    val grid = cells(perSheet, sheetW, sheetH)
                    for (sheet in 0 until sheets) {
                        if (!coroutineContext.isActive) throw InterruptedException("N-up cancelled")
                        val page = PDPage(PDRectangle(sheetW, sheetH))
                        out.addPage(page)
                        PDPageContentStream(out, page).use { cs ->
                            for (slot in 0 until perSheet) {
                                val index = sheet * perSheet + slot
                                if (index >= total) break
                                onProgress(index + 1, total, AppStrings.get(R.string.tools_new_progress_page, index + 1, total))
                                val form = layer.importPageAsForm(source, index)
                                val sourcePage = source.getPage(index)
                                // The form's own box, so whatever origin LayerUtility kept is mapped, not assumed.
                                val seen = seenMatrix(form.bBox, sourcePage.rotation)
                                val (seenW, seenH) = seenSize(form.bBox, sourcePage.rotation)
                                val cell = grid[slot]
                                val scale = fitScale(cell, seenW, seenH)
                                val tx = cell.x + (cell.width - seenW * scale) / 2
                                val ty = cell.y + (cell.height - seenH * scale) / 2
                                cs.saveGraphicsState()
                                cs.transform(Matrix(
                                    seen[0] * scale, seen[1] * scale, seen[2] * scale, seen[3] * scale,
                                    seen[4] * scale + tx, seen[5] * scale + ty
                                ))
                                cs.drawForm(form)
                                cs.restoreGraphicsState()
                            }
                        }
                    }
                    outputFile.parentFile?.mkdirs()
                    out.save(outputFile)
                }
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = sheets,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.get(R.string.tools_nup_done, total, sheets)
                )
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }
}

/**
 * Header and footer text (F173): a line of text along the top and/or bottom edge of every page, placed on the page as
 * seen (like page numbers and watermarks), appended to the content so everything else stays untouched. `{page}` and
 * `{total}` in either text become the page number and the page count.
 */
object PdfHeaderFooterTools {
    /** Dark grey, like the drawn text's 0.25 grey. */
    private const val PICTURE_COLOUR = 0xFF404040.toInt()

    enum class Align { LEFT, CENTER, RIGHT }

    data class HeaderFooterConfig(
        val headerText: String = "",
        val footerText: String = "",
        val align: Align = Align.CENTER,
        val fontSizePt: Float = 10f,
        val marginPt: Float = 24f,
        val skipFirstPage: Boolean = false
    ) {
        val hasAny: Boolean get() = headerText.isNotBlank() || footerText.isNotBlank()
    }

    internal fun substitute(text: String, page: Int, total: Int): String =
        text.replace("{page}", page.toString()).replace("{total}", total.toString())

    /** Where the text's baseline starts, in page coordinates, upright for the reader whatever the page's /Rotate. */
    internal fun edgeStamp(
        top: Boolean,
        align: Align,
        textWidth: Float,
        fontSize: Float,
        margin: Float,
        width: Float,
        height: Float,
        rotation: Int
    ): WatermarkGeometry.Stamp {
        val (seenW, seenH) = WatermarkGeometry.seenSize(width, height, rotation)
        val seenX = when (align) {
            Align.LEFT -> margin
            Align.CENTER -> (seenW - textWidth) / 2f
            Align.RIGHT -> seenW - margin - textWidth
        }
        // Seen y runs down from the top; this is the text baseline.
        val seenY = if (top) margin + fontSize else seenH - margin
        val (px, py) = WatermarkGeometry.toPage(seenX, seenY, width, height, rotation)
        return WatermarkGeometry.Stamp(px, py, Math.toRadians((((rotation % 360) + 360) % 360).toDouble()))
    }

    suspend fun headerFooter(
        context: Context,
        inputUri: Uri,
        config: HeaderFooterConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        password: String = ""
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "hf_in_${UUID.randomUUID()}.pdf")
        try {
            val stamped: Int
            val total: Int
            PdfToolsEngine.openPdf(temp, password).use { doc ->
                total = doc.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                val font = PDType1Font.HELVETICA
                val fontSize = config.fontSizePt.coerceIn(7f, 24f)
                val margin = config.marginPt.coerceIn(12f, 100f)
                var count = 0
                for (i in 0 until total) {
                    if (!coroutineContext.isActive) throw InterruptedException("Header and footer cancelled")
                    onProgress(i + 1, total, AppStrings.get(R.string.tools_new_progress_page, i + 1, total))
                    if (config.skipFirstPage && i == 0) continue
                    val page = doc.getPage(i)
                    val box = page.cropBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.setNonStrokingColor(0.25f, 0.25f, 0.25f)
                        for (top in listOf(true, false)) {
                            val raw = if (top) config.headerText else config.footerText
                            if (raw.isBlank()) continue
                            val text = substitute(raw.trim(), i + 1, total)
                            // Text Helvetica cannot write (Hindi, Tamil, ...) goes on as a picture of the text, the
                            // way the Watermark tool does. Drawn per page because {page} changes the words.
                            val picture = if (PdfTextImage.canEncode(font, text)) null else PdfTextImage.create(doc, text, fontSize, PICTURE_COLOUR, bold = false)
                            val textWidth = picture?.width ?: ((font.getStringWidth(text) / 1000f) * fontSize)
                            val spot = edgeStamp(top, config.align, textWidth, fontSize, margin, box.width, box.height, page.rotation)
                            val cos = Math.cos(spot.angleRad).toFloat()
                            val sin = Math.sin(spot.angleRad).toFloat()
                            if (picture == null) {
                                cs.beginText()
                                cs.setFont(font, fontSize)
                                cs.setTextMatrix(Matrix(cos, sin, -sin, cos, box.lowerLeftX + spot.x, box.lowerLeftY + spot.y))
                                cs.showText(text)
                                cs.endText()
                            } else {
                                // The picture is placed by its centre: half a width along the baseline and half a cap
                                // height up from it (the inverse of the Watermark tool's centre-to-baseline shift).
                                val halfCap = fontSize * 0.36f
                                val cx = box.lowerLeftX + spot.x + (textWidth / 2f * cos) - (halfCap * sin)
                                val cy = box.lowerLeftY + spot.y + (textWidth / 2f * sin) + (halfCap * cos)
                                cs.drawImage(picture.image, PdfTextImage.placement(picture, cx, cy, spot.angleRad))
                            }
                        }
                    }
                    count++
                }
                stamped = count
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            PdfToolResult(
                outputFile = outputFile,
                pageCount = total,
                originalSizeBytes = temp.length(),
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_hf_done, stamped, stamped)
            )
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }
}
