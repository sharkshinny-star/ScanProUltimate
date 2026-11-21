package com.example.scanpro.imaging

import com.example.scanpro.data.OcrWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** A point in PDF user space (origin bottom-left, y up). */
internal data class PagePoint(val x: Float, val y: Float)

/**
 * Maps positions on a page as it is shown (what PdfRenderer draws, with the page's /Rotate applied; origin
 * top-left, y down, as fractions of the shown width and height) back to PDF user space.
 */
internal object PdfTextLayerMath {
    /** The page's shown size in points: width and height swap for a quarter turn. */
    fun shownSize(boxWidth: Float, boxHeight: Float, rotation: Int): Pair<Float, Float> =
        if (normalized(rotation) % 180 == 0) boxWidth to boxHeight else boxHeight to boxWidth

    fun normalized(rotation: Int): Int = ((rotation % 360) + 360) % 360 / 90 * 90

    /** [u], [v]: fractions across and down the shown page. The box is the page's crop box in user space. */
    fun toPageSpace(u: Float, v: Float, rotation: Int, x0: Float, y0: Float, width: Float, height: Float): PagePoint =
        when (normalized(rotation)) {
            90 -> PagePoint(x0 + v * width, y0 + u * height)
            180 -> PagePoint(x0 + (1 - u) * width, y0 + v * height)
            270 -> PagePoint(x0 + (1 - v) * width, y0 + (1 - u) * height)
            else -> PagePoint(x0 + u * width, y0 + (1 - v) * height)
        }
}

/**
 * Adds recognised words to an existing PDF page as invisible text, under the printed words, so the page can be
 * searched and copied while it looks exactly as before. The page's own content is left untouched.
 */
internal object PdfTextLayer {
    private const val MAX_WORDS_PER_PAGE = 6000
    private val SYSTEM_FONTS = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    )

    /**
     * A font that covers more than Western European text when the device has one; Helvetica otherwise. Only
     * the glyphs used are embedded, so the file grows little.
     */
    fun loadFont(document: PDDocument): PDFont {
        for (path in SYSTEM_FONTS) {
            val file = File(path)
            if (!file.canRead()) continue
            val font = runCatching { PDType0Font.load(document, file) }.getOrNull()
            if (font != null) return font
        }
        return PDType1Font.HELVETICA
    }

    /** Returns how many words were written. */
    fun addInvisibleWords(document: PDDocument, page: PDPage, words: List<OcrWord>, font: PDFont): Int {
        if (words.isEmpty()) return 0
        val box = page.cropBox
        val rotation = PdfTextLayerMath.normalized(page.rotation)
        val (shownWidth, shownHeight) = PdfTextLayerMath.shownSize(box.width, box.height, rotation)
        val angle = Math.toRadians(rotation.toDouble())
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        var written = 0
        // Append after the existing content, wrapped so the page's own graphics state can't affect the text.
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setRenderingMode(RenderingMode.NEITHER)
            words.asSequence().take(MAX_WORDS_PER_PAGE).forEach { word ->
                val placed = WordPlacementMath.place(word, 0f, 0f, shownWidth, shownHeight) ?: return@forEach
                val text = encodable(font, word.text.replace('\u0000', ' ').trim()) ?: return@forEach
                val natural = runCatching { font.getStringWidth(text) / 1000f * placed.textSize }.getOrNull() ?: return@forEach
                if (natural <= 0f) return@forEach
                val stretch = (placed.width / natural).coerceIn(0.2f, 5f)
                val origin = PdfTextLayerMath.toPageSpace(
                    placed.x / shownWidth, placed.baseline / shownHeight, rotation, box.lowerLeftX, box.lowerLeftY, box.width, box.height
                )
                stream.setFont(font, placed.textSize)
                // Rotated with the page so the text runs along the printed line, and stretched to the word's width.
                stream.setTextMatrix(Matrix(cosA * stretch, sinA * stretch, -sinA, cosA, origin.x, origin.y))
                stream.showText(text)
                written++
            }
            stream.endText()
        }
        return written
    }

    /** The word with any characters the font can't write removed, or null when nothing is left. */
    private fun encodable(font: PDFont, text: String): String? {
        if (text.isEmpty()) return null
        if (runCatching { font.encode(text) }.isSuccess) return text
        val kept = buildString {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val ch = String(Character.toChars(cp))
                if (runCatching { font.encode(ch) }.isSuccess) append(ch)
                i += Character.charCount(cp)
            }
        }
        return kept.ifBlank { null }
    }
}
