package com.example.scanpro.imaging

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Text the PDF's built-in fonts cannot write (Hindi, Tamil, Telugu, Kannada, Malayalam, emoji...), drawn with the
 * phone's own fonts into a transparent picture that is placed on the page instead. The built-in Helvetica only
 * covers Latin letters, so a watermark typed in an Indian script used to fail. Latin text keeps using real PDF text.
 * (Technique from the 2026-11 all-in-one patch's stamp engine.)
 */
internal object PdfTextImage {
    /** Pixels drawn per PDF point: sharp when printed, still small (a 40 pt word is about 160 px tall). */
    private const val PX_PER_PT = 4f

    /** True when [font] can write every character of [text] as PDF text. */
    fun canEncode(font: PDFont, text: String): Boolean = runCatching { font.encode(text); true }.getOrDefault(false)

    /** A picture of some text, [width] x [height] in PDF points, to be centred on the point it stamps. */
    class Stamp(val image: PDImageXObject, val width: Float, val height: Float)

    private fun paint(fontSizePt: Float, colourArgb: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = colourArgb
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = fontSizePt * PX_PER_PT
    }

    /**
     * How wide the picture of [text] will be, in PDF points. The Watermark editor's preview sizes its text with this, so
     * what is shown for Hindi or Tamil matches what is stamped.
     */
    fun widthPt(text: String, fontSizePt: Float, bold: Boolean = true): Float {
        val paint = paint(fontSizePt, 0xFF000000.toInt(), bold)
        return ceil(paint.measureText(text) + 2 * paint.textSize * 0.1f) / PX_PER_PT
    }

    /** Draws [text] at [fontSizePt] in [colourArgb] (bold), once per document; reuse the result on every page. */
    fun create(doc: PDDocument, text: String, fontSizePt: Float, colourArgb: Int, bold: Boolean = true): Stamp {
        val paint = paint(fontSizePt, colourArgb, bold)
        val metrics = paint.fontMetrics
        val pad = paint.textSize * 0.1f
        val widthPx = ceil(paint.measureText(text) + 2 * pad).toInt().coerceIn(1, 8192)
        val heightPx = ceil(metrics.descent - metrics.ascent + 2 * pad).toInt().coerceIn(1, 4096)
        val bitmap = createBitmap(widthPx, heightPx)
        try {
            Canvas(bitmap).drawText(text, pad, pad - metrics.ascent, paint)
            val image = LosslessFactory.createFromImage(doc, bitmap)
            return Stamp(image, widthPx / PX_PER_PT, heightPx / PX_PER_PT)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The placement matrix that maps the picture's unit square to a [stamp]-sized box centred on ([cx], [cy]) and
     * turned by [angleRad] about that centre (the same centring the vector watermark uses).
     */
    fun placement(stamp: Stamp, cx: Float, cy: Float, angleRad: Double): Matrix =
        placementOf(stamp.width, stamp.height, cx, cy, angleRad)

    internal fun placementOf(w: Float, h: Float, cx: Float, cy: Float, angleRad: Double): Matrix {
        val c = cos(angleRad).toFloat()
        val s = sin(angleRad).toFloat()
        return Matrix(w * c, w * s, -h * s, h * c, cx - w / 2f * c + h / 2f * s, cy - w / 2f * s - h / 2f * c)
    }
}
