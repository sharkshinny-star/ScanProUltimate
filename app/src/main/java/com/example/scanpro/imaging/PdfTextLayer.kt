package com.example.scanpro.imaging

import com.example.scanpro.data.OcrWord
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.Locale
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
 * Text the page font cannot write (Devanagari, Chinese, Japanese, Korean, and anything else the phone's fonts lack),
 * kept as characters instead of being dropped. The words are shown with an invisible Type 0 font that has no glyphs
 * of its own: each distinct character gets a two-byte code, and a ToUnicode map says which character a code is, which
 * is all a viewer needs to search or copy the text. Nothing is drawn (the text is invisible), so no font file is
 * needed and it works the same on every phone.
 */
internal class UnicodeTextFont {
    /** Code point to its two-byte code (1, 2, 3, ...), in the order first seen. */
    private val codes = LinkedHashMap<Int, Int>()

    /** How many characters [text] is (a character outside the basic plane counts once). */
    fun length(text: String): Int = text.codePointCount(0, text.length)

    /** [text] as the hexadecimal string operand of a show-text operator: four digits per character. */
    fun hex(text: String): String {
        val out = StringBuilder("<")
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            i += Character.charCount(codePoint)
            val code = codes.getOrPut(codePoint) { codes.size + 1 }
            out.append(hex4(code))
        }
        return out.append('>').toString()
    }

    /** The ToUnicode CMap for every code handed out so far. */
    fun toUnicodeCMap(): String = buildString {
        append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
        append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
        append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
        append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")
        // A CMap allows at most 100 entries in one bfchar block.
        codes.entries.chunked(100).forEach { chunk ->
            append(chunk.size).append(" beginbfchar\n")
            chunk.forEach { (codePoint, code) ->
                append('<').append(hex4(code)).append("> <")
                String(Character.toChars(codePoint)).forEach { append(hex4(it.code)) }
                append(">\n")
            }
            append("endbfchar\n")
        }
        append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
    }

    /** Adds the font to [page]'s resources under [name], once all its words are written. */
    fun install(document: PDDocument, page: PDPage, name: COSName) {
        val toUnicode = PDStream(document).also { stream ->
            stream.createOutputStream().use { it.write(toUnicodeCMap().toByteArray(Charsets.US_ASCII)) }
        }
        val base = COSName.getPDFName(FONT_NAME)
        val descriptor = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.getPDFName("FontDescriptor"))
            setItem(COSName.getPDFName("FontName"), base)
            // Symbolic: its characters are not in any standard encoding.
            setInt(COSName.getPDFName("Flags"), 4)
            setItem(COSName.getPDFName("FontBBox"), COSArray().apply { listOf(0, -200, 1000, 900).forEach { add(com.tom_roush.pdfbox.cos.COSInteger.get(it.toLong())) } })
            setInt(COSName.getPDFName("ItalicAngle"), 0)
            setInt(COSName.getPDFName("Ascent"), 800)
            setInt(COSName.getPDFName("Descent"), -200)
            setInt(COSName.getPDFName("CapHeight"), 700)
            setInt(COSName.getPDFName("StemV"), 80)
        }
        val descendant = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.getPDFName("CIDFontType2"))
            setItem(COSName.BASE_FONT, base)
            setItem(COSName.getPDFName("CIDSystemInfo"), COSDictionary().apply {
                setString(COSName.getPDFName("Registry"), "Adobe")
                setString(COSName.getPDFName("Ordering"), "Identity")
                setInt(COSName.getPDFName("Supplement"), 0)
            })
            setItem(COSName.getPDFName("FontDescriptor"), descriptor)
            setInt(COSName.getPDFName("DW"), 1000)
        }
        val font = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.getPDFName("Type0"))
            setItem(COSName.BASE_FONT, base)
            setItem(COSName.getPDFName("Encoding"), COSName.getPDFName("Identity-H"))
            setItem(COSName.getPDFName("DescendantFonts"), COSArray().apply { add(descendant) })
            setItem(COSName.getPDFName("ToUnicode"), toUnicode.cosObject)
        }
        fontDictionary(page).setItem(name, font)
    }

    companion object {
        private const val FONT_NAME = "ScanProUnicode"

        private fun hex4(value: Int): String = Integer.toHexString(value).padStart(4, '0').uppercase(Locale.ROOT)

        /** The page's /Font resource dictionary, created if the page has none. */
        private fun fontDictionary(page: PDPage): COSDictionary {
            val resources = page.resources ?: PDResources().also { page.resources = it }
            val dictionary = resources.cosObject
            return dictionary.getDictionaryObject(COSName.FONT) as? COSDictionary
                ?: COSDictionary().also { dictionary.setItem(COSName.FONT, it) }
        }

        /**
         * A font name no page resource uses yet. Pages can share one resource dictionary, so every page that needs
         * the font takes its own name and never overwrites another page's character table.
         */
        fun freeName(page: PDPage): COSName {
            val fonts = fontDictionary(page)
            var n = 1
            while (fonts.containsKey(COSName.getPDFName("ScanProU$n"))) n++
            return COSName.getPDFName("ScanProU$n")
        }
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
        // Words the main font cannot write are kept, as characters, in a font of their own (see UnicodeTextFont).
        val unicodeFont = UnicodeTextFont()
        var unicodeName: COSName? = null
        // Append after the existing content, wrapped so the page's own graphics state can't affect the text.
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setRenderingMode(RenderingMode.NEITHER)
            words.asSequence().take(MAX_WORDS_PER_PAGE).forEach { word ->
                val placed = WordPlacementMath.place(word, 0f, 0f, shownWidth, shownHeight) ?: return@forEach
                val text = word.text.replace('\u0000', ' ').trim()
                if (text.isEmpty()) return@forEach
                val origin = PdfTextLayerMath.toPageSpace(
                    placed.x / shownWidth, placed.baseline / shownHeight, rotation, box.lowerLeftX, box.lowerLeftY, box.width, box.height
                )
                if (runCatching { font.encode(text) }.isSuccess) {
                    val natural = runCatching { font.getStringWidth(text) / 1000f * placed.textSize }.getOrNull() ?: return@forEach
                    if (natural <= 0f) return@forEach
                    val stretch = (placed.width / natural).coerceIn(0.2f, 5f)
                    stream.setFont(font, placed.textSize)
                    // Rotated with the page so the text runs along the printed line, and stretched to the word's width.
                    stream.setTextMatrix(Matrix(cosA * stretch, sinA * stretch, -sinA, cosA, origin.x, origin.y))
                    stream.showText(text)
                } else {
                    // Every character takes the same advance (the font's default width, 1000), so the word is
                    // stretched to its box evenly.
                    val natural = unicodeFont.length(text) * placed.textSize
                    if (natural <= 0f) return@forEach
                    val stretch = (placed.width / natural).coerceIn(0.05f, 5f)
                    val name = unicodeName ?: UnicodeTextFont.freeName(page).also { unicodeName = it }
                    stream.appendRawCommands("/${name.name} ${placed.textSize} Tf\n")
                    stream.setTextMatrix(Matrix(cosA * stretch, sinA * stretch, -sinA, cosA, origin.x, origin.y))
                    stream.appendRawCommands("${unicodeFont.hex(text)} Tj\n")
                }
                written++
            }
            stream.endText()
        }
        unicodeName?.let { unicodeFont.install(document, page, it) }
        return written
    }
}
