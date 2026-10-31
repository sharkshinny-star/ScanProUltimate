package com.example.scanpro.imaging

import com.example.scanpro.core.text.userRequire
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import androidx.core.graphics.createBitmap

object SpecialExporters {
    /** Creates a printable one-page ID copy using the first two pages as front/back. */
    fun exportIdCopy(pages: List<ScanPage>, destination: File, quality: PdfQuality) {
        userRequire(pages.isNotEmpty(), R.string.export_err_id_copy_no_pages)
        val selected = pages.sortedBy { it.position }.take(2)
        val pdf = PdfDocument()
        try {
            val width = 595
            val height = 842
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            page.canvas.drawColor(Color.WHITE)
            val slots = listOf(
                RectF(62f, 120f, 533f, 370f),
                RectF(62f, 430f, 533f, 680f)
            )
            selected.forEachIndexed { index, model ->
                val bitmap = ImageProcessor.decodeScaled(model.imagePath, quality.maxImageDimension)
                    ?: userError(R.string.export_err_page_unreadable, index + 1)
                drawFit(page.canvas, bitmap, slots[index])
                bitmap.recycle()
            }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            page.canvas.drawText(AppStrings.get(R.string.export_id_copy_footer), width / 2f, 805f, label)
            pdf.finishPage(page)
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /**
     * Stacks every page into one tall JPEG. Pages are measured first and then decoded and drawn one at a
     * time, so memory holds the output plus a single page instead of every page twice. The output is
     * 16-bit (fine for a JPEG) and its width shrinks if needed to stay under [MAX_LONG_IMAGE_PIXELS].
     */
    fun exportLongImage(pages: List<ScanPage>, destination: File, maxWidth: Int = 1800) {
        userRequire(pages.isNotEmpty(), R.string.export_err_no_pages)
        val measured: List<Triple<ScanPage, Int, Int>> = pages.sortedBy { it.position }.mapNotNull { page ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(page.imagePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) Triple(page, bounds.outWidth, bounds.outHeight) else null
        }
        userRequire(measured.isNotEmpty(), R.string.export_err_pages_undecodable)
        val gap = 12
        var targetWidth = measured.maxOf { it.second }.coerceAtMost(maxWidth)
        fun heightsAt(width: Int) = measured.map { (_, w, h) -> (h * (width.toFloat() / w)).toInt().coerceAtLeast(1) }
        var heights = heightsAt(targetWidth)
        var totalHeight = heights.sum() + gap * (heights.size - 1)
        if (targetWidth.toLong() * totalHeight > MAX_LONG_IMAGE_PIXELS) {
            val shrink = kotlin.math.sqrt(MAX_LONG_IMAGE_PIXELS.toDouble() / (targetWidth.toLong() * totalHeight))
            targetWidth = (targetWidth * shrink).toInt().coerceAtLeast(1)
            heights = heightsAt(targetWidth)
            totalHeight = heights.sum() + gap * (heights.size - 1)
        }
        userRequire(targetWidth >= MIN_LONG_IMAGE_WIDTH && totalHeight <= MAX_JPEG_DIMENSION, R.string.export_err_too_long)

        val output = createBitmap(targetWidth, totalHeight, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var y = 0f
            measured.forEachIndexed { index, (page, _, _) ->
                val height = heights[index]
                val bitmap = ImageProcessor.decodeScaled(page.imagePath, maxOf(targetWidth, height))
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, RectF(0f, y, targetWidth.toFloat(), y + height), paint)
                    bitmap.recycle()
                }
                y += height + gap
            }
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { stream ->
                userRequire(output.compress(Bitmap.CompressFormat.JPEG, 90, stream), R.string.export_err_long_image_encode)
            }
        } finally {
            output.recycle()
        }
    }

    private const val MAX_LONG_IMAGE_PIXELS = 24_000_000L
    private const val MAX_JPEG_DIMENSION = 65_000
    private const val MIN_LONG_IMAGE_WIDTH = 320

    private fun drawFit(canvas: Canvas, bitmap: Bitmap, slot: RectF) {
        val scale = min(slot.width() / bitmap.width, slot.height() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = slot.left + (slot.width() - w) / 2f
        val top = slot.top + (slot.height() - h) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
