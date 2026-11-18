package com.example.scanpro.imaging

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Edit PDF text (F193): change the words on a PDF's pages and keep its look.
 *
 * The page is read the way text extraction reads it, and its letters are gathered into [Span]s: runs on one line in one
 * font and size. An edit really takes the old letters out of the page (they are cut from the page's own drawing
 * instructions, with a matching gap left so everything after them stays where it was), so the old words can no longer
 * be found, copied or uncovered. The new words are then written in the same place, size, colour and slant:
 *  - in the PDF's own font when it holds every letter needed (fonts in PDFs are often cut down to the letters the page
 *    used, so a new letter may be missing);
 *  - else in another weight of that font already on the page (the bold one, say, when bold was asked for);
 *  - else in the closest standard font (sans, serif or monospaced, bold and italic as asked);
 *  - else in a font from the phone that covers the script.
 * Letters drawn inside a nested drawing (a form XObject) can't be cut out; those are covered with a white box instead,
 * and the outcome says so.
 *
 * Pure PDFBox and Kotlin, no Android, so it is unit tested on the desktop JVM.
 */
internal object PdfTextEditor {
    enum class Family { ORIGINAL, SANS, SERIF, MONO }

    /**
     * Text on one line in one font and size. [left], [top], [right] and [bottom] are display points from the page's
     * top-left corner (text extraction's adjusted coordinates, as the Redact tool uses); [colour] is 0xRRGGBB.
     */
    data class Span(
        val page: Int,
        val index: Int,
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val fontName: String,
        val fontSize: Float,
        val bold: Boolean,
        val italic: Boolean,
        val colour: Int
    )

    /** Replace span [index] on [page] with [text] (empty removes it). [colour] null keeps the span's own. */
    data class Edit(
        val page: Int,
        val index: Int,
        val text: String,
        val family: Family = Family.ORIGINAL,
        val bold: Boolean,
        val italic: Boolean,
        val size: Float,
        val colour: Int? = null
    )

    /**
     * [sameFont]: edits written in the PDF's own font (or its other weight); [similarFont]: in a substitute; [skipped]:
     * edits left out and their line left as it was (no font on the phone can draw the new text, or the line is gone).
     */
    data class Outcome(val applied: Int, val sameFont: Int, val similarFont: Int, val covered: Int, val skipped: Int = 0)

    /** Every span on page [pageIndex] (0-based), in reading order. */
    fun spans(doc: PDDocument, pageIndex: Int): List<Span> = analyse(doc, pageIndex).spans.map { it.span }

    /**
     * Applies [edits] to [doc] in place. Edits naming a span that is no longer there are skipped. [unicodeFont] gives a
     * font for text no standard font can show (null when there is none).
     */
    fun apply(doc: PDDocument, edits: List<Edit>, unicodeFont: (PDDocument) -> PDFont? = { null }): Outcome {
        var applied = 0
        var same = 0
        var similar = 0
        var covered = 0
        var skipped = 0
        var unicode: PDFont? = null
        var unicodeLoaded = false
        val unicodeOnce: () -> PDFont? = {
            if (!unicodeLoaded) { unicode = runCatching { unicodeFont(doc) }.getOrNull(); unicodeLoaded = true }
            unicode
        }
        for ((pageIndex, pageEdits) in edits.groupBy { it.page }.toSortedMap()) {
            if (pageIndex !in 0 until doc.numberOfPages) continue
            val page = doc.getPage(pageIndex)
            val analysis = analyse(doc, pageIndex)
            val removal = HashMap<Int, MutableSet<Int>>()
            val opsOfEdit = HashMap<Edit, MutableSet<Int>>()
            val needsCover = HashSet<Edit>()
            val chosen = ArrayList<Triple<Edit, SpanData, FontChoice?>>()
            for (edit in pageEdits.distinctBy { it.index }) {
                val data = analysis.spans.getOrNull(edit.index)
                // The font is chosen before anything is cut: a line whose new text no font can draw is left as it was,
                // rather than cut out and not written back.
                val choice = if (data == null || edit.text.isEmpty()) null else chooseFont(page, data, edit, unicodeOnce)
                if (data == null || (edit.text.isNotEmpty() && choice == null)) { skipped++; continue }
                chosen += Triple(edit, data, choice)
                val ops = opsOfEdit.getOrPut(edit) { HashSet() }
                val glyphs = analysis.glyphs.filter { data.covers(it) } + data.positions.mapNotNull { analysis.glyphOf[it] }
                for (glyph in glyphs) {
                    val ref = glyph.ref
                    if (ref == null) { needsCover += edit; continue }
                    removal.getOrPut(ref.op) { HashSet() } += ref.glyph
                    ops += ref.op
                }
            }
            if (chosen.isEmpty()) continue
            val failed = if (removal.isEmpty()) emptySet() else rewrite(doc, page, removal, analysis.ops)
            for ((edit, ops) in opsOfEdit) if (ops.any { it in failed }) needsCover += edit
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                for ((edit, data, choice) in chosen) {
                    if (edit in needsCover) { cover(stream, data); covered++ }
                    if (choice == null) { applied++; continue }
                    draw(stream, data, edit, choice)
                    applied++
                    if (choice.same) same++ else similar++
                }
            }
        }
        return Outcome(applied, same, similar, covered, skipped)
    }

    // ---- reading the page ---------------------------------------------------------------------------------------

    /** Glyph [glyph] (0-based, across all its strings) of the [op]-th instruction of the page's own content. */
    private data class GlyphRef(val op: Int, val glyph: Int)

    /** A drawn letter: where it is (display points, baseline) and, when it is in the page's own content, its [ref]. */
    private class Glyph(val ref: GlyphRef?, val x: Float, val baseline: Float, val width: Float)

    /** The text state a show-text instruction ran with, to work out each letter's advance when cutting it out. */
    private class OpState(val font: PDFont?, val fontSize: Float, val charSpacing: Float, val wordSpacing: Float)

    /**
     * [offsetX] and [offsetY]: the crop box's lower-left corner. Text extraction gives each letter's matrix relative to
     * it, so it is added back to draw in the page's own coordinates.
     */
    private class SpanData(val span: Span, val positions: List<TextPosition>, val offsetX: Float, val offsetY: Float) {
        /** True for a letter on this span's line and inside its extent: the span's own, or a copy drawn over it. */
        fun covers(glyph: Glyph): Boolean {
            val size = span.fontSize.coerceAtLeast(1f)
            val baseline = positions.first().yDirAdj
            if (abs(glyph.baseline - baseline) > size * 0.35f) return false
            // Only where one of the span's own letters is: a copy drawn over it (fake bold) goes too, a neighbouring
            // run that overlaps the line (a superscript, a label) stays.
            return positions.any { abs(it.xDirAdj - glyph.x) < size * 0.25f }
        }
    }

    private class PageAnalysis(
        val spans: List<SpanData>,
        val glyphs: List<Glyph>,
        val glyphOf: Map<TextPosition, Glyph>,
        val ops: Map<Int, OpState>
    )

    private fun analyse(doc: PDDocument, pageIndex: Int): PageAnalysis {
        val reader = Reader()
        reader.startPage = pageIndex + 1
        reader.endPage = pageIndex + 1
        reader.sortByPosition = true
        reader.getText(doc)
        reader.endLine()
        val page = doc.getPage(pageIndex)
        val shape = PageShape(page.cropBox.lowerLeftX, page.cropBox.lowerLeftY, page.cropBox.width, page.cropBox.height, ((page.rotation % 360) + 360) % 360)
        return PageAnalysis(buildSpans(pageIndex, reader.lines, reader.colours, shape), reader.glyphs, reader.glyphOf, reader.ops)
    }

    /**
     * Text extraction, listening in: which instruction of the page's own content (not of a nested drawing) each letter
     * came from, the text state it was drawn with, its colour, and the letters of each line in reading order.
     */
    private class Reader : PDFTextStripper() {
        var depth = 0
        var opIndex = -1
        var glyphInOp = 0
        var current: GlyphRef? = null
        val ops = HashMap<Int, OpState>()
        val glyphs = ArrayList<Glyph>()
        val glyphOf = IdentityHashMap<TextPosition, Glyph>()
        val colours = IdentityHashMap<TextPosition, Int>()
        val lines = ArrayList<List<Pair<TextPosition, Boolean>>>()
        private var line = ArrayList<Pair<TextPosition, Boolean>>()
        private var wordBreak = false

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth == 0) {
                opIndex++
                glyphInOp = 0
                if (operator.name in SHOW_TEXT) {
                    val state = graphicsState.textState
                    ops[opIndex] = OpState(state.font, state.fontSize, state.characterSpacing, state.wordSpacing)
                }
            }
            super.processOperator(operator, operands)
        }

        override fun showForm(form: PDFormXObject) {
            depth++
            try { super.showForm(form) } finally { depth-- }
        }

        override fun showTransparencyGroup(form: PDTransparencyGroup) {
            depth++
            try { super.showTransparencyGroup(form) } finally { depth-- }
        }

        override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) {
            current = if (depth == 0) GlyphRef(opIndex, glyphInOp++) else null
            try {
                super.showGlyph(textRenderingMatrix, font, code, unicode, displacement)
            } finally {
                current = null
            }
        }

        override fun processTextPosition(text: TextPosition) {
            val glyph = Glyph(current, text.xDirAdj, text.yDirAdj, text.widthDirAdj)
            glyphs += glyph
            glyphOf[text] = glyph
            colours[text] = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrDefault(0)
            super.processTextPosition(text)
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            textPositions.forEachIndexed { i, position -> line += position to (i == 0 && wordBreak) }
            wordBreak = false
        }

        override fun writeWordSeparator() { wordBreak = true }
        override fun writeLineSeparator() = endLine()
        override fun writeParagraphSeparator() = endLine()

        fun endLine() {
            if (line.isNotEmpty()) lines += line
            line = ArrayList()
            wordBreak = false
        }
    }

    /**
     * Spans from each line's letters: a new span starts where the font or size changes, the line steps up or down, or
     * a gap is wider than about two spaces. A word break inside a span becomes a space.
     */
    /** The crop box (lower-left corner and size, in the page's own coordinates) and the page's turn for display. */
    private class PageShape(val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Int) {
        /** A point relative to the crop box's lower-left corner, as display points from the shown page's top-left. */
        fun toDisplay(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
            90 -> y to x
            180 -> (width - x) to y
            270 -> (height - y) to (width - x)
            else -> x to (height - y)
        }
    }

    private fun buildSpans(page: Int, lines: List<List<Pair<TextPosition, Boolean>>>, colours: Map<TextPosition, Int>, shape: PageShape): List<SpanData> {
        val spans = ArrayList<SpanData>()
        for (line in lines) {
            var run = ArrayList<TextPosition>()
            val text = StringBuilder()
            fun emit() {
                if (run.isEmpty() || text.isBlank()) { run = ArrayList(); text.clear(); return }
                spans += makeSpan(page, spans.size, run, text.toString().trim(), colours, shape)
                run = ArrayList()
                text.clear()
            }
            for ((position, spaceBefore) in line) {
                val previous = run.lastOrNull()
                if (previous != null) {
                    val size = previous.fontSizeInPt.coerceAtLeast(1f)
                    val gap = position.xDirAdj - (previous.xDirAdj + previous.widthDirAdj)
                    val sameStyle = previous.font?.name == position.font?.name && abs(previous.fontSizeInPt - position.fontSizeInPt) < 0.5f
                    val sameLine = abs(previous.yDirAdj - position.yDirAdj) < size * 0.3f
                    if (!sameStyle || !sameLine || gap > size * 2f || gap < -size) emit()
                }
                if (run.isNotEmpty() && spaceBefore) text.append(' ')
                run += position
                text.append(position.unicode.orEmpty())
            }
            emit()
        }
        return spans
    }

    private fun makeSpan(page: Int, index: Int, run: List<TextPosition>, text: String, colours: Map<TextPosition, Int>, shape: PageShape): SpanData {
        val first = run.first()
        val size = first.fontSizeInPt.takeIf { it > 0f } ?: (first.heightDir * 1.4f).coerceAtLeast(1f)
        val font = first.font
        val name = baseName(font?.name)
        // The box as the page is shown: each letter's corners (from its baseline origin, along and across the text),
        // turned with the page. Text-direction coordinates would put it in the wrong place on a turned page.
        val xs = ArrayList<Float>(run.size * 4)
        val ys = ArrayList<Float>(run.size * 4)
        for (position in run) {
            val m = position.textMatrix
            val (ux, uy) = unit(m.scaleX, m.shearY) ?: (1f to 0f)
            val (vx, vy) = unit(m.shearX, m.scaleY) ?: (0f to 1f)
            val advance = position.widthDirAdj
            for ((along, across) in listOf(0f to -0.25f, advance to -0.25f, 0f to 0.9f, advance to 0.9f)) {
                val (dx, dy) = shape.toDisplay(m.translateX + ux * along + vx * across * size, m.translateY + uy * along + vy * across * size)
                xs += dx
                ys += dy
            }
        }
        val span = Span(
            page = page,
            index = index,
            text = text,
            left = xs.min(),
            top = ys.min(),
            right = xs.max(),
            bottom = ys.max(),
            fontName = name,
            fontSize = size,
            bold = isBold(font),
            italic = isItalic(font),
            colour = colours[first] ?: 0
        )
        return SpanData(span, run, shape.x, shape.y)
    }

    // ---- cutting the old letters out ----------------------------------------------------------------------------

    /**
     * Rewrites the page's content without the letters in [removal] (instruction index to glyph indexes). Each cut letter
     * leaves a gap of its own width, so nothing after it moves. Returns the instructions that could not be rewritten
     * (an unknown font, vertical writing); their letters stay and the caller covers them.
     */
    private fun rewrite(doc: PDDocument, page: PDPage, removal: Map<Int, Set<Int>>, ops: Map<Int, OpState>): Set<Int> {
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens
        val out = ArrayList<Any>(tokens.size + removal.size * 4)
        val operands = ArrayList<Any>()
        val failed = HashSet<Int>()
        var opIndex = -1
        for (token in tokens) {
            if (token is Operator) {
                opIndex++
                val remove = removal[opIndex]
                val state = ops[opIndex]
                val replaced = if (remove != null && state != null) runCatching { withoutGlyphs(token, operands, remove, state) }.getOrNull() else null
                if (remove != null && replaced == null) failed += opIndex
                if (replaced != null) out.addAll(replaced) else { out.addAll(operands); out.add(token) }
                operands.clear()
            } else {
                operands += token
            }
        }
        out.addAll(operands)
        // Nothing could be cut: leave the page's content exactly as it was.
        if (failed.size == removal.size) return failed
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(out) }
        page.setContents(stream)
        return failed
    }

    /**
     * One show-text instruction rewritten as TJ without the glyphs in [remove]; null when it can't be (vertical
     * writing, a font that can't be read). In a TJ array a number n moves the pen back by n/1000 of the font size, so a
     * cut glyph becomes -(its width x 1000 + (character spacing + word spacing) x 1000 / font size).
     */
    private fun withoutGlyphs(operator: Operator, operands: List<Any>, remove: Set<Int>, state: OpState): List<Any>? {
        val font = state.font ?: return null
        if (font.isVertical || state.fontSize == 0f) return null
        val prefix = ArrayList<Any>()
        var charSpacing = state.charSpacing
        var wordSpacing = state.wordSpacing
        val elements: List<COSBase> = when (operator.name) {
            "Tj" -> listOf(operands.lastOrNull() as? COSString ?: return null)
            "'" -> {
                prefix.add(Operator.getOperator("T*"))
                listOf(operands.lastOrNull() as? COSString ?: return null)
            }
            "\"" -> {
                if (operands.size < 3) return null
                val aw = operands[0] as? COSNumber ?: return null
                val ac = operands[1] as? COSNumber ?: return null
                wordSpacing = aw.floatValue()
                charSpacing = ac.floatValue()
                prefix.addAll(listOf(aw, Operator.getOperator("Tw"), ac, Operator.getOperator("Tc"), Operator.getOperator("T*")))
                listOf(operands[2] as? COSString ?: return null)
            }
            "TJ" -> (operands.lastOrNull() as? COSArray)?.toList() ?: return null
            else -> return null
        }
        val array = COSArray()
        var pending = 0f
        val buffer = ByteArrayOutputStream()
        fun flushNumber() {
            if (pending != 0f) array.add(COSFloat(pending))
            pending = 0f
        }
        fun flushString() {
            if (buffer.size() > 0) {
                array.add(COSString(buffer.toByteArray()))
                buffer.reset()
            }
        }
        var glyph = 0
        for (element in elements) {
            when (element) {
                is COSString -> {
                    val bytes = element.bytes
                    val input = ByteArrayInputStream(bytes)
                    while (input.available() > 0) {
                        val before = input.available()
                        val code = font.readCode(input)
                        val length = before - input.available()
                        val start = bytes.size - before
                        if (glyph in remove) {
                            flushString()
                            val width = font.getDisplacement(code).x
                            val space = if (length == 1 && code == 32) wordSpacing else 0f
                            pending -= width * 1000f + (charSpacing + space) * 1000f / state.fontSize
                        } else {
                            flushNumber()
                            buffer.write(bytes, start, length)
                        }
                        glyph++
                    }
                }
                is COSNumber -> {
                    flushString()
                    pending += element.floatValue()
                }
                else -> return null
            }
        }
        flushString()
        flushNumber()
        return prefix + listOf(array, Operator.getOperator("TJ"))
    }

    /** A white box over a span whose letters could not be cut out. */
    private fun cover(stream: PDPageContentStream, data: SpanData) {
        val first = data.positions.first()
        val last = data.positions.last()
        val m = first.textMatrix
        val (ux, uy) = unit(m.scaleX, m.shearY) ?: return
        val (vx, vy) = unit(m.shearX, m.scaleY) ?: return
        val size = data.span.fontSize
        val length = hypot(last.textMatrix.translateX - m.translateX, last.textMatrix.translateY - m.translateY) + last.widthDirAdj
        val x0 = data.offsetX + m.translateX - ux * size * 0.05f - vx * size * 0.3f
        val y0 = data.offsetY + m.translateY - uy * size * 0.05f - vy * size * 0.3f
        val along = length + size * 0.1f
        val up = size * 1.3f
        stream.saveGraphicsState()
        stream.setNonStrokingColor(PDColor(floatArrayOf(1f, 1f, 1f), PDDeviceRGB.INSTANCE))
        stream.moveTo(x0, y0)
        stream.lineTo(x0 + ux * along, y0 + uy * along)
        stream.lineTo(x0 + ux * along + vx * up, y0 + uy * along + vy * up)
        stream.lineTo(x0 + vx * up, y0 + vy * up)
        stream.closePath()
        stream.fill()
        stream.restoreGraphicsState()
    }

    // ---- writing the new words ----------------------------------------------------------------------------------

    /** The font to write with; [synthBold] and [synthItalic] mean the font lacks the weight or slant and it is drawn on. */
    private class FontChoice(val font: PDFont, val same: Boolean, val synthBold: Boolean, val synthItalic: Boolean)

    private fun chooseFont(page: PDPage, data: SpanData, edit: Edit, unicode: () -> PDFont?): FontChoice? {
        val text = edit.text
        val original = data.positions.first().font
        if (edit.family == Family.ORIGINAL && original != null) {
            if (edit.bold == data.span.bold && edit.italic == data.span.italic && canShow(original, text)) {
                return FontChoice(original, same = true, synthBold = false, synthItalic = false)
            }
            val family = familyKey(original.name)
            val resources = page.resources
            val sibling = resources?.fontNames?.asSequence()
                ?.mapNotNull { name -> runCatching { resources.getFont(name) }.getOrNull() }
                ?.firstOrNull { font ->
                    familyKey(font.name) == family && isBold(font) == edit.bold && isItalic(font) == edit.italic && canShow(font, text)
                }
            if (sibling != null) return FontChoice(sibling, same = true, synthBold = false, synthItalic = false)
        }
        val family = if (edit.family == Family.ORIGINAL) guessFamily(original) else edit.family
        val standard = standardFont(family, edit.bold, edit.italic)
        if (canShow(standard, text)) return FontChoice(standard, same = false, synthBold = false, synthItalic = false)
        val wide = unicode() ?: return null
        if (!canShow(wide, text)) return null
        return FontChoice(wide, same = false, synthBold = edit.bold, synthItalic = edit.italic)
    }

    private fun draw(stream: PDPageContentStream, data: SpanData, edit: Edit, choice: FontChoice) {
        val m = data.positions.first().textMatrix
        val (ux, uy) = unit(m.scaleX, m.shearY) ?: return
        val (vx, vy) = unit(m.shearX, m.scaleY) ?: return
        val size = edit.size.coerceIn(1f, 400f)
        val rgb = edit.colour ?: data.span.colour
        val colour = PDColor(
            floatArrayOf(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f),
            PDDeviceRGB.INSTANCE
        )
        // A slant drawn on for an italic the font doesn't have: the up direction leans forward by about 12 degrees.
        val lean = if (choice.synthItalic) 0.21f else 0f
        // Colours and line width before the text object: PDFBox refuses some graphics settings inside one.
        stream.saveGraphicsState()
        stream.setNonStrokingColor(colour)
        if (choice.synthBold) {
            stream.setStrokingColor(colour)
            stream.setLineWidth(size * 0.035f)
        }
        stream.beginText()
        stream.setFont(choice.font, size)
        if (choice.synthBold) stream.setRenderingMode(RenderingMode.FILL_STROKE)
        stream.setTextMatrix(Matrix(ux, uy, vx + ux * lean, vy + uy * lean, data.offsetX + m.translateX, data.offsetY + m.translateY))
        stream.showText(edit.text)
        if (choice.synthBold) stream.setRenderingMode(RenderingMode.FILL)
        stream.endText()
        stream.restoreGraphicsState()
    }

    private fun unit(x: Float, y: Float): Pair<Float, Float>? {
        val length = hypot(x, y)
        return if (length < 1e-6f) null else (x / length) to (y / length)
    }

    /** True when [font] can draw every character of [text]: it encodes them, and an embedded font has their glyphs. */
    internal fun canShow(font: PDFont, text: String): Boolean = runCatching {
        if (font is PDType3Font) return@runCatching false
        val bytes = font.encode(text)
        if (font.isEmbedded && font is PDVectorFont) {
            val input = ByteArrayInputStream(bytes)
            while (input.available() > 0) if (!font.hasGlyph(font.readCode(input))) return@runCatching false
        }
        true
    }.getOrDefault(false)

    private fun standardFont(family: Family, bold: Boolean, italic: Boolean): PDFont = when (family) {
        Family.SERIF -> when {
            bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
            bold -> PDType1Font.TIMES_BOLD
            italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        Family.MONO -> when {
            bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            bold -> PDType1Font.COURIER_BOLD
            italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
        else -> when {
            bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            bold -> PDType1Font.HELVETICA_BOLD
            italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }

    // ---- font names ---------------------------------------------------------------------------------------------

    private val SHOW_TEXT = setOf("Tj", "TJ", "'", "\"")
    private val WEIGHT_WORDS = Regex("(?i)(bold|black|heavy|semibold|demibold|extrabold|ultrabold|medium|light|thin|regular|book|roman|italic|oblique|it|mt|ps)")

    /** The font's name without the six-letter subset tag ("ABCDEF+Arial-BoldMT" gives "Arial-BoldMT"). */
    internal fun baseName(name: String?): String = name.orEmpty().substringAfter('+', name.orEmpty())

    /** The family part of a font name, for finding its other weights: "Arial-BoldMT" and "ArialMT" both give "arial". */
    internal fun familyKey(name: String?): String =
        baseName(name).split('-', ',', '_', ' ').first().replace(WEIGHT_WORDS, "").lowercase()

    internal fun isBold(font: PDFont?): Boolean {
        if (font == null) return false
        val descriptor = font.fontDescriptor
        if (descriptor != null && (descriptor.isForceBold || descriptor.fontWeight >= 600f)) return true
        val name = baseName(font.name).lowercase()
        return listOf("bold", "black", "heavy", "semibold", "demibold").any { it in name }
    }

    internal fun isItalic(font: PDFont?): Boolean {
        if (font == null) return false
        val descriptor = font.fontDescriptor
        if (descriptor != null && (descriptor.isItalic || abs(descriptor.italicAngle) > 0.5f)) return true
        val name = baseName(font.name).lowercase()
        return "italic" in name || "oblique" in name
    }

    /** A standard family that looks like [font]: serif for Times-like names, monospaced for Courier-like ones. */
    internal fun guessFamily(font: PDFont?): Family {
        val name = baseName(font?.name).lowercase()
        val descriptor = font?.fontDescriptor
        return when {
            listOf("courier", "mono", "consol", "menlo", "typewriter").any { it in name } || descriptor?.isFixedPitch == true -> Family.MONO
            listOf("times", "serif", "roman", "georgia", "garamond", "cambria", "book", "minion", "palatino").any { it in name && "sans" !in name } ||
                descriptor?.isSerif == true -> Family.SERIF
            else -> Family.SANS
        }
    }
}

/** The unicode font helper as the app uses it: a phone font that covers more than Western text, or none. */
internal fun phoneUnicodeFont(document: PDDocument): PDFont? =
    PdfTextLayer.loadFont(document).takeIf { it is PDType0Font }
