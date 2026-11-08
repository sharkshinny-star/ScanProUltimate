package com.example.scanpro.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import kotlin.math.roundToInt

/**
 * Both sides of an ID card on one page, the way CamScanner and Adobe Scan save them: the front at the top, the back
 * below it, both the same width, centred on a white portrait page, each with a hairline border so a white card still
 * reads as a card. The page is an A4 shape, so it prints and shares like any other scan.
 */
object IdCardComposer {
    /** A rectangle in page pixels (plain ints, so the layout is testable without Android). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun toRect() = Rect(left, top, right, bottom)
    }

    /** Where each side goes on a page of [pageWidth] x [pageHeight] px. */
    data class Layout(val pageWidth: Int, val pageHeight: Int, val front: Box, val back: Box)

    private const val A4 = 1.4142f
    /** Side margin as a fraction of the card width; the gap between the cards is the same. */
    private const val MARGIN = 0.07f
    /** Both sides are scaled to this width at most, so the page stays a sensible size. */
    private const val MAX_CARD_WIDTH = 2000

    /** The page for a front of [frontWidth] x [frontHeight] and a back of [backWidth] x [backHeight] px. */
    fun arrange(frontWidth: Int, frontHeight: Int, backWidth: Int, backHeight: Int): Layout {
        val cardW = minOf(frontWidth, backWidth, MAX_CARD_WIDTH).coerceAtLeast(1)
        val frontH = (cardW * frontHeight.toFloat() / frontWidth.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
        val backH = (cardW * backHeight.toFloat() / backWidth.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
        val margin = (cardW * MARGIN).roundToInt()
        val pageW = cardW + 2 * margin
        // Tall enough for both cards and the gap even when a side was cropped unusually tall; A4 otherwise.
        val pageH = maxOf((pageW * A4).roundToInt(), frontH + backH + 3 * margin)
        // The pair sits centred on the page.
        val top = (pageH - (frontH + margin + backH)) / 2
        val front = Box(margin, top, margin + cardW, top + frontH)
        val back = Box(margin, front.bottom + margin, margin + cardW, front.bottom + margin + backH)
        return Layout(pageW, pageH, front, back)
    }

    /**
     * Writes the page to [destination]: each side is cut out of its photo along [frontQuad] / [backQuad] (the whole
     * photo when null) and straightened, then placed by [arrange]. Throws when a photo cannot be read.
     */
    fun compose(front: File, frontQuad: CropQuad?, back: File, backQuad: CropQuad?, destination: File) {
        val sides = listOf(front to frontQuad, back to backQuad).map { (file, quad) ->
            // The same crop as a normal page, straight to a bitmap: one decode per side, no temporary file.
            ImageProcessor.cropSourceToBitmap(file, quad ?: CropQuad.DEFAULT, MAX_CARD_WIDTH * 12 / 10)
        }
        val (frontBitmap, backBitmap) = sides
        try {
            val layout = arrange(frontBitmap.width, frontBitmap.height, backBitmap.width, backBitmap.height)
            val page = createBitmap(layout.pageWidth, layout.pageHeight)
            try {
                val canvas = Canvas(page)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = 0xFFD8DCE6.toInt()
                    strokeWidth = (layout.pageWidth / 600f).coerceAtLeast(1f)
                }
                listOf(frontBitmap to layout.front, backBitmap to layout.back).forEach { (bitmap, box) ->
                    val rect = box.toRect()
                    canvas.drawBitmap(bitmap, null, rect, paint)
                    canvas.drawRect(RectF(rect), border)
                }
                destination.parentFile?.mkdirs()
                destination.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            } finally {
                page.recycle()
            }
        } finally {
            sides.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
