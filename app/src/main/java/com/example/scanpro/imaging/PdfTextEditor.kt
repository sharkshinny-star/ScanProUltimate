package com.example.scanpro.imaging

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
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
     * Where the new words sit in the place the old ones had: starting where they started, centred on it, or ending
     * where they ended (a column of amounts stays lined up on the right). For new text: starting at, centred on or
     * ending at the point tapped. With wrapping, within the width the lines are broken to.
     */
    enum class Align { LEFT, CENTRE, RIGHT, JUSTIFY }

    /** A marker before each paragraph of the text: a dot, its number, or an empty tick box. */
    enum class ListStyle { NONE, BULLET, NUMBER, CHECK }

    /** Small text set below the line (the 2 in H2O) or above it (the 2 in m2). */
    enum class Script { NONE, SUB, SUPER }

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

    /**
     * Replace span [index] on [page] with [text] (empty removes it). [colour] null keeps the span's own (0xRRGGBB).
     * An [index] of [NEW] adds [text] as new words starting at [x], [y]: the baseline's left end in display points from
     * the shown page's top-left; Same as PDF then means the font of the nearest text on the page.
     */
    data class Edit(
        val page: Int,
        val index: Int,
        val text: String,
        val family: Family = Family.ORIGINAL,
        val bold: Boolean,
        val italic: Boolean,
        val size: Float,
        val colour: Int? = null,
        val x: Float = 0f,
        val y: Float = 0f,
        val align: Align = Align.LEFT,
        /**
         * Break a line too long for the page onto further lines below, at spaces: up to the right edge of the page's
         * text (never narrower than the old words were). The lines below are not moved, so wrapped text can run over
         * them. A line break typed in [text] always starts a new line.
         */
        val wrap: Boolean = false,
        /** Moved from where it would sit by this much, in display points: right and down are positive. */
        val dx: Float = 0f,
        val dy: Float = 0f,
        val underline: Boolean = false,
        val strike: Boolean = false,
        /** A marker colour behind the words (0xRRGGBB), or none. */
        val highlight: Int? = null,
        /** Baseline to baseline, in font sizes, when the text takes more than one line. */
        val lineSpacing: Float = LINE_SPACING,
        /** Extra room after every letter, in points (negative brings them closer). */
        val letterSpacing: Float = 0f,
        /**
         * When the new text of a line takes more room downwards than the one line it had, the lines of text below it in
         * the same column move down to make room, as far as the first gap that takes it up (pictures and ruled lines
         * are not moved, and nothing is pushed off the page).
         */
        val pushBelow: Boolean = true,
        val list: ListStyle = ListStyle.NONE,
        val script: Script = Script.NONE,
        /** Room kept clear at the start and end of each line, in points. */
        val indentLeft: Float = 0f,
        val indentRight: Float = 0f,
        /** Room above the first line and below each paragraph (a typed line break ends one), in points. */
        val spaceBefore: Float = 0f,
        val spaceAfter: Float = 0f,
        /** Tells pieces of added text on one page apart; 0 for a changed line. */
        val id: Int = 0
    ) {
        val isNew: Boolean get() = index == NEW
    }

    /** The [Edit.index] of new text added to a page. */
    const val NEW = -1

    /**
     * [sameFont]: edits written in the PDF's own font (or its other weight); [similarFont]: in a substitute; [skipped]:
     * edits left out and their line left as it was (no font on the phone can draw the new text, or the line is gone).
     */
    data class Outcome(
        val applied: Int,
        val sameFont: Int,
        val similarFont: Int,
        val covered: Int,
        val skipped: Int = 0,
        /** Where each edit's words now are, for an editor to mark and to open again. */
        val placed: List<Placed> = emptyList(),
        /** Lines that were not edited but moved down to make room: page and span index to the points moved. */
        val moved: Map<Pair<Int, Int>, Float> = emptyMap(),
        /** Edits drawn as shapes by a [Shaper] (they look right but are no longer text in the PDF). */
        val shaped: Int = 0
    )

    /** The box of [edit]'s words as drawn, in display points from the shown page's top-left. */
    data class Placed(val edit: Edit, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * Draws scripts whose letters join and reorder (Hindi, Kannada, Malayalam, Tamil, Telugu, Arabic and others), which
     * PDFBox cannot set properly: the phone shapes the words and hands back their outlines. Sizes are for text of size 1.
     */
    interface Shaper {
        fun width(text: String, family: Family, bold: Boolean, italic: Boolean): Float

        /** Closed outlines as x0, y0, x1, y1…: x along the baseline from the start of [text], y upwards. */
        fun outline(text: String, family: Family, bold: Boolean, italic: Boolean): List<FloatArray>
    }

    /** True when [text] holds letters of a script that needs a [Shaper] to look right. */
    fun needsShaping(text: String): Boolean = text.any { it.code in 0x0590..0x109F || it.code in 0x1780..0x17FF }

    /** Every span on page [pageIndex] (0-based), in reading order. */
    fun spans(doc: PDDocument, pageIndex: Int): List<Span> = analyse(doc, pageIndex).spans.map { it.span }

    /**
     * Applies [edits] to [doc] in place. Edits naming a span that is no longer there are skipped. [unicodeFont] gives a
     * font for text no standard font can show (null when there is none).
     */
    fun apply(doc: PDDocument, edits: List<Edit>, unicodeFont: (PDDocument) -> PDFont? = { null }, shaper: Shaper? = null): Outcome {
        var applied = 0
        var same = 0
        var similar = 0
        var covered = 0
        var skipped = 0
        var shaped = 0
        val placed = ArrayList<Placed>()
        val moved = HashMap<Pair<Int, Int>, Float>()
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
            val chosen = ArrayList<Planned>()
            val additions = pageEdits.filter { it.isNew && it.text.isNotBlank() }
            for (edit in pageEdits.filterNot { it.isNew }.distinctBy { it.index }) {
                val data = analysis.spans.getOrNull(edit.index)
                // The font is chosen before anything is cut: a line whose new text no font can draw is left as it was,
                // rather than cut out and not written back.
                val choice = if (data == null || edit.text.isEmpty()) null else chooseFont(page, data, edit, unicodeOnce, shaper)
                if (data == null || (edit.text.isNotEmpty() && choice == null)) { skipped++; continue }
                chosen += Planned(edit, data, choice, analysis)
                val ops = opsOfEdit.getOrPut(edit) { HashSet() }
                for (glyph in glyphsOf(analysis, data)) {
                    val ref = glyph.ref
                    if (ref == null) { needsCover += edit; continue }
                    removal.getOrPut(ref.op) { HashSet() } += ref.glyph
                    ops += ref.op
                }
            }
            if (chosen.isEmpty() && additions.isEmpty()) continue
            // Lines pushed down by text that now takes more room, top of the page first so pushes add up.
            val push = HashMap<Int, Float>()
            for (plan in chosen.sortedBy { it.data.span.top }) {
                val block = plan.block ?: continue
                val extra = block.advance - plan.edit.size.coerceIn(1f, 400f) * plan.edit.lineSpacing
                if (!plan.edit.pushBelow || extra < 0.5f || !plan.across) continue
                for (below in linesBelow(analysis, plan.data, maxOf(plan.box, plan.data.span.right - plan.data.span.left), extra)) {
                    push[below.span.index] = (push[below.span.index] ?: 0f) + extra
                }
            }
            val edited = chosen.map { it.data.span.index }.toSet()
            val rises = HashMap<Int, MutableMap<Int, Float>>()
            val opsOfPushed = HashMap<Int, MutableSet<Int>>()
            for ((index, amount) in push) {
                if (index in edited) continue
                val data = analysis.spans[index]
                val m = data.positions.first().textMatrix
                val up = hypot(m.shearX, m.scaleY)
                val firstSize = data.positions.firstNotNullOfOrNull { analysis.glyphOf[it]?.ref }?.let { analysis.ops[it.op]?.fontSize } ?: 0f
                if (up < 1e-6f || firstSize == 0f) continue
                for (glyph in glyphsOf(analysis, data)) {
                    val ref = glyph.ref ?: continue
                    val size = analysis.ops[ref.op]?.fontSize ?: continue
                    // Text rise is measured in the text's own units: the points wanted, over how far one unit reaches.
                    rises.getOrPut(ref.op) { HashMap() }[ref.glyph] = -amount * (if (size < 0f) -1f else 1f) * abs(firstSize) / up
                    opsOfPushed.getOrPut(index) { HashSet() } += ref.op
                }
            }
            val failed = if (removal.isEmpty() && rises.isEmpty()) emptySet() else rewrite(doc, page, removal, rises, analysis.ops)
            for ((edit, ops) in opsOfEdit) if (ops.any { it in failed }) needsCover += edit
            for ((index, ops) in opsOfPushed) if (ops.none { it in failed }) moved[pageIndex to index] = push.getValue(index)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                for (plan in chosen) {
                    val (edit, data, choice) = plan
                    if (edit in needsCover) { cover(stream, data); covered++ }
                    val block = plan.block
                    if (choice == null || block == null) { applied++; continue }
                    draw(stream, plan, block, push[data.span.index] ?: 0f)?.let { placed += placedOf(edit, it, analysis.shape) }
                    applied++
                    if (choice.shaper != null) shaped++
                    if (choice.same) same++ else similar++
                }
                for (addition in additions) {
                    // The nearest line's font stands for "the PDF's font" here, and its colour when none was chosen.
                    val near = analysis.spans.minByOrNull { distance(it.span, addition.x, addition.y) }
                    val choice = if (near != null) chooseFont(page, near, addition, unicodeOnce, shaper)
                    else chooseStandard(null, addition, unicodeOnce, shaper)
                    if (choice == null) { skipped++; continue }
                    val (ux, uy, vx, vy) = analysis.shape.upright()
                    val (ox, oy) = analysis.shape.toPage(addition.x + addition.dx, addition.y + addition.dy)
                    val wrapWidth = if (addition.wrap) (rightEdge(analysis) - addition.x).coerceAtLeast(MIN_WRAP_WIDTH) else null
                    val block = plan(addition, choice, box = wrapWidth ?: 0f, wrapWidth = wrapWidth)
                    drawBlock(stream, block, addition, choice, addition.colour ?: near?.span?.colour ?: 0, ux, uy, vx, vy, ox, oy)
                        ?.let { placed += placedOf(addition, it, analysis.shape) }
                    applied++
                    if (choice.shaper != null) shaped++
                    if (choice.same) same++ else similar++
                }
            }
        }
        return Outcome(applied, same, similar, covered, skipped, placed, moved, shaped)
    }

    /** One edit of a line already on the page, with its font and the lines its new words make. */
    private class Planned(val edit: Edit, val data: SpanData, val choice: FontChoice?, analysis: PageAnalysis) {
        operator fun component1() = edit
        operator fun component2() = data
        operator fun component3() = choice
        private val first = data.positions.first()
        private val last = data.positions.last()
        val matrix: Matrix = first.textMatrix
        val along = unit(matrix.scaleX, matrix.shearY)
        val up = unit(matrix.shearX, matrix.scaleY)
        /** How long the old words were along their baseline: the place the new ones are aligned in. */
        val old: Float = along?.let { (ux, uy) ->
            ((last.textMatrix.translateX - matrix.translateX) * ux + (last.textMatrix.translateY - matrix.translateY) * uy + last.widthDirAdj).coerceAtLeast(0f)
        } ?: 0f
        /** True when the line reads across the page as shown (not up the side or at an angle). */
        val across: Boolean = along?.let { (ux, uy) ->
            val (ax, ay) = analysis.shape.toDisplay(0f, 0f)
            val (bx, by) = analysis.shape.toDisplay(ux, uy)
            bx - ax > 0.99f && abs(by - ay) < 0.1f
        } ?: false
        // Wrapping goes up to the right edge of the page's text; a line at an angle keeps to the length it had.
        val wrapWidth: Float? = if (!edit.wrap) null
        else if (across) maxOf(old, rightEdge(analysis) - data.span.left, MIN_WRAP_WIDTH) else maxOf(old, MIN_WRAP_WIDTH)
        val box: Float = wrapWidth ?: old
        val shape = analysis.shape
        val block: Block? = if (choice == null || along == null || up == null) null else plan(edit, choice, box, wrapWidth)
    }

    /** The letters of a span as drawn: its own and any copy drawn over them (fake bold). */
    private fun glyphsOf(analysis: PageAnalysis, data: SpanData): List<Glyph> =
        (analysis.glyphs.filter { data.covers(it) } + data.positions.mapNotNull { analysis.glyphOf[it] }).distinct()

    /**
     * The lines under [data] that have to move down by [extra] points: those below it that share its column
     * ([width] from its left end) and read the same way, row by row, until a gap between rows that takes the extra up
     * or a row that would leave the page.
     */
    private fun linesBelow(analysis: PageAnalysis, data: SpanData, width: Float, extra: Float): List<SpanData> {
        val span = data.span
        val shown = if (analysis.shape.rotation == 90 || analysis.shape.rotation == 270) analysis.shape.width else analysis.shape.height
        val m = data.positions.first().textMatrix
        val direction = unit(m.scaleX, m.shearY) ?: return emptyList()
        val below = analysis.spans.filter { other ->
            if (other === data) return@filter false
            val o = other.positions.first().textMatrix
            val d = unit(o.scaleX, o.shearY) ?: return@filter false
            d.first * direction.first + d.second * direction.second > 0.99f &&
                other.span.top >= span.top + (span.bottom - span.top) * 0.5f &&
                other.span.right > span.left - 1f && other.span.left < span.left + width + 1f
        }.sortedBy { it.span.top }
        val out = ArrayList<SpanData>()
        var previousBottom = span.bottom
        var i = 0
        while (i < below.size) {
            val top = below[i].span.top
            var j = i
            while (j < below.size && below[j].span.top - top < below[i].span.fontSize * 0.5f) j++
            val row = below.subList(i, j)
            if (top - previousBottom >= extra) break
            if (row.maxOf { it.span.bottom } + extra > shown - 6f) break
            out += row
            previousBottom = row.maxOf { it.span.bottom }
            i = j
        }
        return out
    }

    /** [bounds] (min x, min y, max x, max y in the page's own coordinates) as a box on the shown page. */
    private fun placedOf(edit: Edit, bounds: FloatArray, shape: PageShape): Placed {
        val (ax, ay) = shape.toDisplay(bounds[0] - shape.x, bounds[1] - shape.y)
        val (bx, by) = shape.toDisplay(bounds[2] - shape.x, bounds[3] - shape.y)
        return Placed(edit, minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by))
    }

    // ---- reading the page ---------------------------------------------------------------------------------------

    /** Glyph [glyph] (0-based, across all its strings) of the [op]-th instruction of the page's own content. */
    private data class GlyphRef(val op: Int, val glyph: Int)

    /** A drawn letter: where it is (display points, baseline) and, when it is in the page's own content, its [ref]. */
    private class Glyph(val ref: GlyphRef?, val x: Float, val baseline: Float, val width: Float)

    /** The text state a show-text instruction ran with, to work out each letter's advance when cutting it out. */
    private class OpState(val font: PDFont?, val fontSize: Float, val charSpacing: Float, val wordSpacing: Float, val rise: Float)

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
        val shape: PageShape,
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
        return PageAnalysis(shape, buildSpans(pageIndex, reader.lines, reader.colours, shape), reader.glyphs, reader.glyphOf, reader.ops)
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

        init {
            // Text extraction leaves the fill colour alone unless asked: without these every line read as black, so an
            // edited line came back black whatever its colour.
            addOperator(SetNonStrokingColorSpace())
            addOperator(SetNonStrokingColor())
            addOperator(SetNonStrokingColorN())
            addOperator(SetNonStrokingDeviceRGBColor())
            addOperator(SetNonStrokingDeviceGrayColor())
            addOperator(SetNonStrokingDeviceCMYKColor())
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth == 0) {
                opIndex++
                glyphInOp = 0
                if (operator.name in SHOW_TEXT) {
                    val state = graphicsState.textState
                    ops[opIndex] = OpState(state.font, state.fontSize, state.characterSpacing, state.wordSpacing, state.rise)
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

        /** The other way: a display point to the page's own coordinates (crop box corner added back). */
        fun toPage(displayX: Float, displayY: Float): Pair<Float, Float> {
            val (rx, ry) = when (rotation) {
                90 -> displayY to displayX
                180 -> (width - displayX) to displayY
                270 -> (width - displayY) to (height - displayX)
                else -> displayX to (height - displayY)
            }
            return (x + rx) to (y + ry)
        }

        /** Rightwards and upwards on the shown page, as directions in the page's own coordinates. */
        fun upright(): FloatArray = when (rotation) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f)
            270 -> floatArrayOf(0f, -1f, 1f, 0f)
            else -> floatArrayOf(1f, 0f, 0f, 1f)
        }
    }

    /** How far a display point is from a span's box (0 inside it). */
    private fun distance(span: Span, x: Float, y: Float): Float {
        val dx = maxOf(span.left - x, 0f, x - span.right)
        val dy = maxOf(span.top - y, 0f, y - span.bottom)
        return hypot(dx, dy)
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
     * Rewrites the page's content without the letters in [removal] (instruction index to glyph indexes), and with the
     * letters in [rises] lifted by that much text rise (negative lowers them: how a line is moved down without
     * touching its font). Each cut letter leaves a gap of its own width, so nothing after it moves. Returns the
     * instructions that could not be rewritten (an unknown font, vertical writing); their letters stay as they were
     * and the caller covers those it meant to cut.
     */
    private fun rewrite(
        doc: PDDocument, page: PDPage, removal: Map<Int, Set<Int>>, rises: Map<Int, Map<Int, Float>>, ops: Map<Int, OpState>
    ): Set<Int> {
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
                val rise = rises[opIndex]
                val state = ops[opIndex]
                val touched = remove != null || rise != null
                val replaced = if (touched && state != null) {
                    runCatching { withoutGlyphs(token, operands, remove.orEmpty(), rise.orEmpty(), state) }.getOrNull()
                } else null
                if (touched && replaced == null) failed += opIndex
                if (replaced != null) out.addAll(replaced) else { out.addAll(operands); out.add(token) }
                operands.clear()
            } else {
                operands += token
            }
        }
        out.addAll(operands)
        // Nothing could be cut: leave the page's content exactly as it was.
        if (failed.containsAll(removal.keys + rises.keys)) return failed
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
    private fun withoutGlyphs(operator: Operator, operands: List<Any>, remove: Set<Int>, rise: Map<Int, Float>, state: OpState): List<Any>? {
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
        // The letters go out as one TJ, or as several with a text rise set between them where some are lifted.
        val out = ArrayList<Any>(prefix)
        var array = COSArray()
        var lifted = 0f
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
        fun flushArray() {
            flushString()
            flushNumber()
            if (array.size() > 0) { out.add(array); out.add(Operator.getOperator("TJ")) }
            array = COSArray()
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
                            val wanted = rise[glyph] ?: 0f
                            if (wanted != lifted) {
                                flushArray()
                                out.add(COSFloat(state.rise + wanted))
                                out.add(Operator.getOperator("Ts"))
                                lifted = wanted
                            }
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
        flushArray()
        if (lifted != 0f) { out.add(COSFloat(state.rise)); out.add(Operator.getOperator("Ts")) }
        return out
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

    /**
     * The font to write with; [synthBold] and [synthItalic] mean the font lacks the weight or slant and it is drawn on.
     * With a [shaper] the words are drawn as its outlines instead and [font] is not used.
     */
    private class FontChoice(
        val font: PDFont?, val same: Boolean, val synthBold: Boolean, val synthItalic: Boolean,
        val shaper: Shaper? = null, val family: Family = Family.SANS, val bold: Boolean = false, val italic: Boolean = false
    ) {
        fun width(text: String, size: Float): Float = runCatching {
            if (shaper != null) shaper.width(text, family, bold, italic) * size else font!!.getStringWidth(text) / 1000f * size
        }.getOrDefault(0f)
    }

    private fun chooseFont(page: PDPage, data: SpanData, edit: Edit, unicode: () -> PDFont?, shaper: Shaper?): FontChoice? {
        val text = edit.text
        val original = data.positions.first().font
        if (shaper != null && needsShaping(text)) return chooseStandard(original, edit, unicode, shaper)
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
        return chooseStandard(original, edit, unicode, shaper)
    }

    /** A standard font of the family asked for (or like [original]), else the phone's wide font. */
    private fun chooseStandard(original: PDFont?, edit: Edit, unicode: () -> PDFont?, shaper: Shaper?): FontChoice? {
        val text = edit.text
        val family = if (edit.family == Family.ORIGINAL) guessFamily(original) else edit.family
        if (shaper != null && needsShaping(text)) {
            return FontChoice(null, same = false, synthBold = false, synthItalic = false, shaper = shaper, family = family, bold = edit.bold, italic = edit.italic)
        }
        val standard = standardFont(family, edit.bold, edit.italic)
        if (canShow(standard, text)) return FontChoice(standard, same = false, synthBold = false, synthItalic = false)
        val wide = unicode() ?: return null
        if (!canShow(wide, text)) return null
        return FontChoice(wide, same = false, synthBold = edit.bold, synthItalic = edit.italic)
    }

    /** Draws [plan]'s new words where the old ones were, [pushed] points lower when a line above made it move. */
    private fun draw(stream: PDPageContentStream, plan: Planned, block: Block, pushed: Float): FloatArray? {
        val (ux, uy) = plan.along ?: return null
        val (vx, vy) = plan.up ?: return null
        val edit = plan.edit
        val m = plan.matrix
        // A move asked for, from display points to the page's own directions.
        val (zeroX, zeroY) = plan.shape.toPage(0f, 0f)
        val (movedX, movedY) = plan.shape.toPage(edit.dx, edit.dy)
        return drawBlock(
            stream, block, edit, plan.choice!!, edit.colour ?: plan.data.span.colour, ux, uy, vx, vy,
            plan.data.offsetX + m.translateX + (movedX - zeroX) - vx * pushed,
            plan.data.offsetY + m.translateY + (movedY - zeroY) - vy * pushed
        )
    }

    /** Baseline to baseline, in font sizes, for text that runs onto further lines, unless an edit says otherwise. */
    const val LINE_SPACING = 1.2f

    /**
     * [text] with every [query] in it replaced by [replacement], or null when it holds none. The query is taken as
     * plain text (not a pattern); capital and small letters count as the same unless [matchCase].
     */
    fun replaceIn(text: String, query: String, replacement: String, matchCase: Boolean): String? {
        if (query.isEmpty()) return null
        val pattern = Regex(Regex.escape(query), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        if (!pattern.containsMatchIn(text)) return null
        return pattern.replace(text, Regex.escapeReplacement(replacement))
    }

    /** Lines are never broken narrower than this (points): a sliver beside the page edge would give a word a line. */
    private const val MIN_WRAP_WIDTH = 60f

    /**
     * The right edge of the page's text, in display points: as far right as any line reaches, or the page's width less
     * the margin its text keeps on the left when the lines are all short; never past the page.
     */
    private fun rightEdge(analysis: PageAnalysis): Float {
        val shape = analysis.shape
        val shown = if (shape.rotation == 90 || shape.rotation == 270) shape.height else shape.width
        val left = (analysis.spans.minOfOrNull { it.span.left } ?: 36f).coerceIn(18f, shown / 3f)
        val reached = analysis.spans.maxOfOrNull { it.span.right } ?: 0f
        return maxOf(reached, shown - left).coerceAtMost(shown - 12f)
    }

    /**
     * [text] as the lines to draw: a new line at each line break in it and, when [wrapWidth] is given, at a space
     * wherever the next word would pass it. A word longer than a whole line is broken where it fills one.
     */
    internal fun layout(text: String, wrapWidth: Float?, width: (String) -> Float): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            if (wrapWidth == null || width(paragraph) <= wrapWidth) { lines += paragraph; continue }
            var line = ""
            for (word in paragraph.split(' ')) {
                val joined = if (line.isEmpty()) word else "$line $word"
                if (width(joined) <= wrapWidth) { line = joined; continue }
                if (line.isNotEmpty()) lines += line
                line = word
                while (width(line) > wrapWidth && line.length > 1) {
                    var cut = line.length - 1
                    while (cut > 1 && width(line.substring(0, cut)) > wrapWidth) cut--
                    lines += line.substring(0, cut)
                    line = line.substring(cut)
                }
            }
            lines += line
        }
        return lines
    }

    /** One line as it will be drawn: [along] and [down] from the block's starting point, in points. */
    private class Row(
        val text: String, val along: Float, val down: Float, val width: Float,
        /** Extra room at each space, for a line stretched to both edges. */
        val gap: Float,
        /** The paragraph's number (from 1) on its first line, for a list marker; 0 on the other lines. */
        val marker: Int
    )

    /** An edit's lines, the size they are drawn at, and how far down the block reaches in all ([advance]). */
    private class Block(val rows: List<Row>, val size: Float, val raise: Float, val advance: Float)

    /**
     * Lays [edit]'s text out from its starting point: a line is placed in [box] (a length along the baseline) by the
     * edit's alignment, after its indents and the room a list marker takes; lines are broken at [wrapWidth] when given.
     */
    private fun plan(edit: Edit, choice: FontChoice, box: Float, wrapWidth: Float?): Block {
        val full = edit.size.coerceIn(1f, 400f)
        val size = if (edit.script == Script.NONE) full else full * 0.62f
        val raise = when (edit.script) { Script.SUPER -> full * 0.36f; Script.SUB -> -full * 0.14f; Script.NONE -> 0f }
        val spacing = if (choice.shaper == null) edit.letterSpacing else 0f
        val width: (String) -> Float = { s -> choice.width(s, size) + spacing * s.length }
        val hang = if (edit.list == ListStyle.NONE) 0f else full * 1.5f
        val start = edit.indentLeft.coerceAtLeast(0f) + hang
        val inner = wrapWidth?.let { (it - start - edit.indentRight.coerceAtLeast(0f)).coerceAtLeast(MIN_WRAP_WIDTH / 2f) }
        val room = inner ?: (box - start - edit.indentRight.coerceAtLeast(0f)).coerceAtLeast(0f)
        val rows = ArrayList<Row>()
        var down = edit.spaceBefore.coerceAtLeast(0f)
        var number = 0
        for (paragraph in edit.text.split('\n')) {
            val lines = layout(paragraph, inner, width)
            if (paragraph.isNotBlank()) number++
            lines.forEachIndexed { i, line ->
                val w = width(line)
                val spaces = line.count { it == ' ' }
                val stretch = edit.align == Align.JUSTIFY && inner != null && i < lines.lastIndex && spaces > 0 && w < room
                val shift = when (edit.align) {
                    Align.LEFT, Align.JUSTIFY -> 0f
                    Align.CENTRE -> (room - w) / 2f
                    Align.RIGHT -> room - w
                }
                rows += Row(line, start + shift, down, if (stretch) room else w, if (stretch) (room - w) / spaces else 0f, if (i == 0 && paragraph.isNotBlank()) number else 0)
                down += full * edit.lineSpacing.coerceIn(0.8f, 4f)
            }
            down += edit.spaceAfter.coerceAtLeast(0f)
        }
        return Block(rows, size, raise, down)
    }

    /**
     * Draws [block] from the point ([ox], [oy]), its lines running along ([ux], [uy]) with up ([vx], [vy]): marker
     * colour behind, list markers, the words (or their outlines), then underline and strike-through. Returns the box
     * it takes in the page's own coordinates (min x, min y, max x, max y), or null when nothing was drawn.
     */
    private fun drawBlock(
        stream: PDPageContentStream, block: Block, edit: Edit, choice: FontChoice, rgb: Int,
        ux: Float, uy: Float, vx: Float, vy: Float, ox: Float, oy: Float
    ): FloatArray? {
        val size = block.size
        val full = edit.size.coerceIn(1f, 400f)
        val rows = block.rows.filter { it.text.isNotBlank() }
        if (rows.isEmpty()) return null
        fun colourOf(value: Int) = PDColor(
            floatArrayOf(((value shr 16) and 0xFF) / 255f, ((value shr 8) and 0xFF) / 255f, (value and 0xFF) / 255f),
            PDDeviceRGB.INSTANCE
        )
        val colour = colourOf(rgb)
        // A point [along] the baseline and [up] from it, for the row [down] from the start.
        fun px(along: Float, up: Float, down: Float) = ox + ux * along + vx * (up - down)
        fun py(along: Float, up: Float, down: Float) = oy + uy * along + vy * (up - down)
        val bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        fun quad(a0: Float, a1: Float, u0: Float, u1: Float, down: Float, fill: Boolean) {
            val xs = floatArrayOf(px(a0, u0, down), px(a1, u0, down), px(a1, u1, down), px(a0, u1, down))
            val ys = floatArrayOf(py(a0, u0, down), py(a1, u0, down), py(a1, u1, down), py(a0, u1, down))
            if (!fill) {
                bounds[0] = minOf(bounds[0], xs.min()); bounds[1] = minOf(bounds[1], ys.min())
                bounds[2] = maxOf(bounds[2], xs.max()); bounds[3] = maxOf(bounds[3], ys.max())
                return
            }
            stream.moveTo(xs[0], ys[0])
            for (i in 1..3) stream.lineTo(xs[i], ys[i])
            stream.closePath()
            stream.fill()
        }
        val spacing = if (choice.shaper == null) edit.letterSpacing else 0f
        val width: (String) -> Float = { s -> choice.width(s, size) + spacing * s.length }
        // Each row as the pieces shown one after another: the whole line, or its words when it is stretched.
        fun pieces(row: Row): List<Pair<String, Float>> {
            if (row.gap == 0f) return listOf(row.text to row.along)
            var at = row.along
            return row.text.split(' ').map { word -> (word to at).also { at += width("$word ") + row.gap } }
        }
        val markerStart = edit.indentLeft.coerceAtLeast(0f)
        stream.saveGraphicsState()
        for (row in rows) {
            val from = if (row.marker > 0 && edit.list != ListStyle.NONE) markerStart else row.along
            quad(from, row.along + row.width, block.raise - full * 0.25f, block.raise + full * 0.9f, row.down, fill = false)
        }
        edit.highlight?.let { marker ->
            stream.setNonStrokingColor(colourOf(marker))
            for (row in rows) quad(row.along, row.along + row.width, block.raise - size * 0.24f, block.raise + size * 0.9f, row.down, fill = true)
        }
        stream.setNonStrokingColor(colour)
        if (edit.list == ListStyle.BULLET || edit.list == ListStyle.CHECK) {
            for (row in rows.filter { it.marker > 0 }) {
                if (edit.list == ListStyle.BULLET) {
                    // A round dot: four curves about its centre, a third of the way up the letters.
                    val r = full * 0.15f
                    val k = r * 0.5523f
                    val ca = markerStart + full * 0.45f
                    val cu = full * 0.33f
                    fun to(a: Float, u: Float) = px(ca + a, cu + u, row.down) to py(ca + a, cu + u, row.down)
                    fun curve(a1: Float, u1: Float, a2: Float, u2: Float, a3: Float, u3: Float) {
                        val (x1, y1) = to(a1, u1); val (x2, y2) = to(a2, u2); val (x3, y3) = to(a3, u3)
                        stream.curveTo(x1, y1, x2, y2, x3, y3)
                    }
                    to(r, 0f).let { (x, y) -> stream.moveTo(x, y) }
                    curve(r, k, k, r, 0f, r)
                    curve(-k, r, -r, k, -r, 0f)
                    curve(-r, -k, -k, -r, 0f, -r)
                    curve(k, -r, r, -k, r, 0f)
                    stream.closePath()
                    stream.fill()
                } else {
                    // An empty tick box: a square with a smaller one taken out of it.
                    val a0 = markerStart + full * 0.15f
                    val side = full * 0.62f
                    val line = full * 0.06f
                    val xs = floatArrayOf(a0, a0 + side, a0 + side, a0)
                    val us = floatArrayOf(0f, 0f, side, side)
                    for (inset in listOf(0f, line)) {
                        val sx = floatArrayOf(xs[0] + inset, xs[1] - inset, xs[2] - inset, xs[3] + inset)
                        val su = floatArrayOf(us[0] + inset, us[1] + inset, us[2] - inset, us[3] - inset)
                        stream.moveTo(px(sx[0], su[0], row.down), py(sx[0], su[0], row.down))
                        for (i in 1..3) stream.lineTo(px(sx[i], su[i], row.down), py(sx[i], su[i], row.down))
                        stream.closePath()
                    }
                    stream.fillEvenOdd()
                }
            }
        }
        // A slant drawn on for an italic the font doesn't have: the up direction leans forward by about 12 degrees.
        val lean = if (choice.synthItalic) 0.21f else 0f
        if (edit.list == ListStyle.NUMBER) {
            val numbers = choice.font?.takeIf { canShow(it, "0123456789.") } ?: standardFont(Family.SANS, edit.bold, false)
            stream.beginText()
            stream.setFont(numbers, full)
            for (row in rows.filter { it.marker > 0 }) {
                stream.setTextMatrix(Matrix(ux, uy, vx, vy, px(markerStart, 0f, row.down), py(markerStart, 0f, row.down)))
                stream.showText("${row.marker}.")
            }
            stream.endText()
        }
        val shaper = choice.shaper
        if (shaper != null) {
            for (row in rows) for ((piece, at) in pieces(row)) {
                val contours = runCatching { shaper.outline(piece, choice.family, choice.bold, choice.italic) }.getOrDefault(emptyList())
                var any = false
                for (contour in contours) {
                    if (contour.size < 6) continue
                    for (i in contour.indices step 2) {
                        val x = px(at + contour[i] * size, block.raise + contour[i + 1] * size, row.down)
                        val y = py(at + contour[i] * size, block.raise + contour[i + 1] * size, row.down)
                        if (i == 0) stream.moveTo(x, y) else stream.lineTo(x, y)
                    }
                    stream.closePath()
                    any = true
                }
                if (any) stream.fill()
            }
        } else {
            // Colours and line width before the text object: PDFBox refuses some graphics settings inside one.
            if (choice.synthBold) {
                stream.setStrokingColor(colour)
                stream.setLineWidth(size * 0.035f)
            }
            stream.beginText()
            stream.setFont(choice.font, size)
            if (choice.synthBold) stream.setRenderingMode(RenderingMode.FILL_STROKE)
            if (spacing != 0f) stream.setCharacterSpacing(spacing)
            for (row in rows) for ((piece, at) in pieces(row)) {
                if (piece.isEmpty()) continue
                stream.setTextMatrix(Matrix(ux, uy, vx + ux * lean, vy + uy * lean, px(at, block.raise, row.down), py(at, block.raise, row.down)))
                stream.showText(piece)
            }
            if (choice.synthBold) stream.setRenderingMode(RenderingMode.FILL)
            stream.endText()
        }
        if (edit.underline) for (row in rows) quad(row.along, row.along + row.width, block.raise - size * 0.14f, block.raise - size * 0.08f, row.down, fill = true)
        if (edit.strike) for (row in rows) quad(row.along, row.along + row.width, block.raise + size * 0.25f, block.raise + size * 0.31f, row.down, fill = true)
        stream.restoreGraphicsState()
        return bounds
    }

    private fun unit(x: Float, y: Float): Pair<Float, Float>? {
        val length = hypot(x, y)
        return if (length < 1e-6f) null else (x / length) to (y / length)
    }

    /** True when [font] can draw every character of [text]: it encodes them, and an embedded font has their glyphs. */
    internal fun canShow(font: PDFont, text: String): Boolean = runCatching {
        if (font is PDType3Font) return@runCatching false
        // Line breaks are places to start a new line, not letters to draw.
        val bytes = font.encode(text.replace("\n", ""))
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
