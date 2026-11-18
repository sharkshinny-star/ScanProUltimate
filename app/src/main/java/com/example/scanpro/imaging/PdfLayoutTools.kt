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
 * N-up (F173): two or four pages drawn on each sheet, for reading drafts and slides on paper. Pages are imported as
 * form objects ([LayerUtility]), so text, vectors and images stay as they are, merely moved and scaled; nothing is
 * rasterised. Each page is placed as it is displayed (its /Rotate applied), scaled to fit its cell and centred, in
 * reading order (left to right, then down).
 */
object PdfNupTools {
    /** Sheet margin and the gap between cells, in points. */
    private const val MARGIN_PT = 18f
    private const val GUTTER_PT = 12f

    /** A cell on the sheet: lower-left corner and size, in sheet points. */
    internal class Cell(val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * The cells of one sheet in reading order: 2-up is two columns side by side, 4-up a 2 by 2 grid read left to
     * right then down (so the first row is the TOP row, although PDF y runs up).
     */
    internal fun cells(perSheet: Int, sheetWidth: Float, sheetHeight: Float): List<Cell> {
        val columns = 2
        val rows = if (perSheet == 4) 2 else 1
        val cellW = (sheetWidth - 2 * MARGIN_PT - (columns - 1) * GUTTER_PT) / columns
        val cellH = (sheetHeight - 2 * MARGIN_PT - (rows - 1) * GUTTER_PT) / rows
        return buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val x = MARGIN_PT + column * (cellW + GUTTER_PT)
                    val y = sheetHeight - MARGIN_PT - cellH - row * (cellH + GUTTER_PT)
                    add(Cell(x, y, cellW, cellH))
                }
            }
        }
    }

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
        require(perSheet == 2 || perSheet == 4) { "perSheet must be 2 or 4" }
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "nup_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.openPdf(temp, password).use { source ->
                val total = source.numberOfPages
                userRequire(total > 1, R.string.tools_nup_err_one_page)
                // The sheet is the first page's paper: the same size for a 2 by 2 grid, turned sideways for 2-up,
                // so two portrait pages sit next to each other on a landscape sheet of the same paper.
                val first = source.getPage(0)
                val (firstW, firstH) = seenSize(first.cropBox, first.rotation)
                val (sheetW, sheetH) = if (perSheet == 2) maxOf(firstW, firstH) to minOf(firstW, firstH) else firstW to firstH
                val sheets = (total + perSheet - 1) / perSheet
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
                                val scale = min(cell.width / seenW, cell.height / seenH)
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

    /** The smallest size a header or footer is shrunk to so that it fits between the page's side margins. */
    internal const val MIN_FONT_SIZE_PT = 5f

    /** [fontSize], or smaller so that a line [widthAtSize] wide at [fontSize] fits in [available] points. */
    internal fun fittedFontSize(fontSize: Float, widthAtSize: Float, available: Float): Float =
        if (widthAtSize <= 0f || widthAtSize <= available) fontSize
        else (fontSize * available / widthAtSize).coerceIn(MIN_FONT_SIZE_PT, fontSize)

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
                            val encodable = PdfTextImage.canEncode(font, text)
                            val widthAtSize = if (encodable) (font.getStringWidth(text) / 1000f) * fontSize else PdfTextImage.widthPt(text, fontSize, bold = false)
                            // A line longer than the page is between its margins gets smaller rather than running off the edge.
                            val seenWidth = WatermarkGeometry.seenSize(box.width, box.height, page.rotation).first
                            val size = fittedFontSize(fontSize, widthAtSize, seenWidth - 2f * margin)
                            val picture = if (encodable) null else PdfTextImage.create(doc, text, size, PICTURE_COLOUR, bold = false)
                            val textWidth = picture?.width ?: ((font.getStringWidth(text) / 1000f) * size)
                            val spot = edgeStamp(top, config.align, textWidth, size, margin, box.width, box.height, page.rotation)
                            val cos = Math.cos(spot.angleRad).toFloat()
                            val sin = Math.sin(spot.angleRad).toFloat()
                            if (picture == null) {
                                cs.beginText()
                                cs.setFont(font, size)
                                cs.setTextMatrix(Matrix(cos, sin, -sin, cos, box.lowerLeftX + spot.x, box.lowerLeftY + spot.y))
                                cs.showText(text)
                                cs.endText()
                            } else {
                                // The picture is placed by its centre: half a width along the baseline and half a cap
                                // height up from it (the inverse of the Watermark tool's centre-to-baseline shift).
                                val halfCap = size * 0.36f
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
