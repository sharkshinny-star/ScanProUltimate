package com.example.scanpro.imaging

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.io.Writer
import java.util.zip.ZipInputStream

/*
 * The arithmetic and checks behind the convert and info tools (F211): what a tool will produce, worked out before it
 * runs, with the same rules the engines use, so the page never promises something the engine then does differently.
 * Kept free of Android rendering so the rules can be unit-tested.
 */

/** PDF to images: pixel sizes, the memory cap and file names. */
object PdfToImagesMath {
    /** The longest side a page picture may have: 4000 × 4000 ARGB is 64 MB, about the most a phone can hold safely. */
    const val MAX_SIDE = 4000

    /** Resolutions offered, in dots per inch; a PDF point is 1/72 inch, so the render scale is dpi / 72. */
    val DPI_CHOICES = listOf(96, 150, 220, 300)
    const val DEFAULT_DPI = 150

    /** JPEG and WebP qualities the engine writes with (PNG is lossless). */
    const val JPEG_QUALITY = 92
    const val WEBP_QUALITY = 90

    fun scaleFor(dpi: Int): Float = dpi / 72f

    /** The scale really used for a page of [widthPt] × [heightPt] points: [scale], lowered so no side passes [MAX_SIDE]. */
    fun effectiveScale(widthPt: Int, heightPt: Int, scale: Float): Float =
        minOf(scale, MAX_SIDE.toFloat() / maxOf(widthPt, heightPt).coerceAtLeast(1))

    /** Width and height in pixels of the picture of a [widthPt] × [heightPt] page at [scale]. */
    fun pixelSize(widthPt: Int, heightPt: Int, scale: Float): Pair<Int, Int> {
        val s = effectiveScale(widthPt, heightPt, scale)
        return (widthPt * s).toInt().coerceAtLeast(1) to (heightPt * s).toInt().coerceAtLeast(1)
    }

    /** True when the page is too large for [scale] and comes out smaller (fewer dots per inch) than asked. */
    fun isCapped(widthPt: Int, heightPt: Int, scale: Float): Boolean = effectiveScale(widthPt, heightPt, scale) < scale - 1e-4f

    /** The 1-based pages that will be exported: [pages] inside the document, once each, in page order; null is all. */
    fun chosenPages(pages: List<Int>?, total: Int): List<Int> =
        pages?.asSequence()?.filter { it in 1..total }?.distinct()?.sorted()?.toList() ?: (1..total).toList()

    /**
     * "Report page 007.jpg": numbered with as many digits as the last page needs (at least three), so the files sort
     * in page order everywhere; "page 1000" used to sort before "page 101" in a 1000-page export.
     */
    fun fileName(base: String, pageNumber: Int, totalPages: Int, extension: String): String {
        val digits = maxOf(3, totalPages.toString().length)
        return "$base page ${pageNumber.toString().padStart(digits, '0')}.$extension"
    }

    /** Bytes for [totalPixels], from a sample that took [sampleBytes] for [samplePixels]. */
    fun scaleBytes(sampleBytes: Long, samplePixels: Long, totalPixels: Long): Long =
        if (samplePixels <= 0) 0 else (sampleBytes.toDouble() * totalPixels / samplePixels).toLong()
}

/** When a page's text layer is enough, and when the page has to be read with OCR. */
object PageTextRules {
    /**
     * Fewer visible characters than this, on a page that also shows a picture, is a scan with a stray text layer: a
     * "Scanned with …" stamp or a page number added by the scanning app. Such a page used to count as "has text", so
     * the words in the picture were never read and PDF to Word came out with one line per page.
     */
    const val SPARSE_TEXT_CHARS = 40

    fun countVisible(text: String): Int = text.count { !it.isWhitespace() }

    fun needsOcr(visibleChars: Int, hasImage: Boolean): Boolean = visibleChars == 0 || (hasImage && visibleChars < SPARSE_TEXT_CHARS)

    /** True when [page] draws at least one picture (its own resources, or a form it uses). Nothing is decoded. */
    fun pageHasImage(page: PDPage): Boolean = runCatching {
        val found = mutableListOf<PDImageXObject>()
        PdfCleanupTools.pageImages(page, HashSet<COSBase>(), found)
        found.isNotEmpty()
    }.getOrDefault(false)

    /**
     * Visible characters of the text layer on each page of [document]. Pages are read one at a time, so a page PDFBox
     * cannot read counts as having no text and leaves every other page's count alone (read in one pass, a failure
     * partway left all the later pages at zero, and Make searchable then read pages that already had text).
     */
    fun visibleCharsPerPage(document: PDDocument): IntArray {
        val counts = IntArray(document.numberOfPages)
        var page = 0
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                counts[page] += countVisible(text)
            }
        }
        for (index in counts.indices) {
            page = index
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            runCatching { stripper.writeText(document, DiscardingWriter) }
        }
        return counts
    }

    private object DiscardingWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }
}

/** What PDF to text and Make searchable will find, page by page, before they run. */
class PdfTextSurvey(
    val pageCount: Int,
    /** Pages whose text layer is used as it is. */
    val textPages: Int,
    /** Pages that will be read with OCR (no text layer, or only a stray line over a picture). */
    val ocrPages: Int,
) {
    companion object {
        fun of(document: PDDocument): PdfTextSurvey {
            val chars = PageTextRules.visibleCharsPerPage(document)
            var ocr = 0
            chars.forEachIndexed { index, visible ->
                val hasImage = visible in 1 until PageTextRules.SPARSE_TEXT_CHARS &&
                    PageTextRules.pageHasImage(document.getPage(index))
                if (PageTextRules.needsOcr(visible, hasImage)) ocr++
            }
            return PdfTextSurvey(chars.size, chars.size - ocr, ocr)
        }
    }
}

/** Extract images: what the PDF holds, before anything is written. */
class PdfImageSurvey(
    /** Distinct pictures large enough to be saved. */
    val pictures: Int,
    /** Of those, how many are stored as JPEG and come out exactly as stored. */
    val jpeg: Int,
    /** Tiny images (rules, bullets, spacer pixels) that are left out. */
    val tiny: Int,
    /** Pictures in formats the phone may not be able to decode (JPEG 2000, JBIG2, CMYK JPEG). */
    val maybeUnreadable: Int,
    /** Bytes the pictures take inside the PDF: close to the JPEG files, a lower bound for the PNG ones. */
    val storedBytes: Long,
    /** Pages that show at least one picture. */
    val pagesWithPictures: Int,
) {
    companion object {
        fun of(document: PDDocument): PdfImageSurvey {
            val seen = HashSet<COSBase>()
            var pictures = 0
            var jpeg = 0
            var tiny = 0
            var unreadable = 0
            var bytes = 0L
            var pages = 0
            for (page in document.pages) {
                val found = mutableListOf<PDImageXObject>()
                runCatching { PdfCleanupTools.pageImages(page, seen, found) }
                if (found.isNotEmpty()) pages++
                for (image in found) {
                    if (image.width < PdfCleanupTools.MIN_IMAGE_SIDE || image.height < PdfCleanupTools.MIN_IMAGE_SIDE) {
                        tiny++
                        continue
                    }
                    pictures++
                    val filters = filterNames(image)
                    if (filters == listOf(COSName.DCT_DECODE.name) && !hasTransparency(image)) jpeg++
                    if (filters.any { it == COSName.JPX_DECODE.name || it == COSName.JBIG2_DECODE.name }) unreadable++
                    bytes += image.cosObject.getLong(COSName.LENGTH).coerceAtLeast(0)
                }
            }
            return PdfImageSurvey(pictures, jpeg, tiny, unreadable, bytes, pages)
        }

        internal fun filterNames(image: PDImageXObject): List<String> = when (val filters = image.cosObject.filters) {
            is COSName -> listOf(filters.name)
            is com.tom_roush.pdfbox.cos.COSArray -> (0 until filters.size()).mapNotNull { (filters.getObject(it) as? COSName)?.name }
            else -> emptyList()
        }

        /** The picture carries a mask of any kind (stencil, colour key or soft mask). */
        internal fun hasMask(image: PDImageXObject): Boolean =
            image.cosObject.containsKey(COSName.SMASK) || image.cosObject.containsKey(COSName.MASK)

        /**
         * True when [image] has see-through parts that a JPEG copied as stored would lose (a logo would come out on a
         * black box): a stencil or colour-key mask, or a soft mask that is not fully opaque. Some PDF writers give
         * every JPEG an all-opaque soft mask; those are still copied exactly as stored. A mask that cannot be read
         * counts as see-through, so the picture is decoded with it rather than shown wrong.
         */
        internal fun hasTransparency(image: PDImageXObject): Boolean {
            if (image.cosObject.containsKey(COSName.MASK)) return true
            if (!image.cosObject.containsKey(COSName.SMASK)) return false
            val mask = runCatching { image.softMask?.image }.getOrNull() ?: return true
            try {
                val alphaOnly = mask.config == android.graphics.Bitmap.Config.ALPHA_8
                val row = IntArray(mask.width)
                for (y in 0 until mask.height) {
                    mask.getPixels(row, 0, mask.width, 0, y, mask.width, 1)
                    for (pixel in row) {
                        val value = if (alphaOnly) android.graphics.Color.alpha(pixel) else android.graphics.Color.red(pixel)
                        if (value < 250) return true
                    }
                }
                return false
            } finally {
                mask.recycle()
            }
        }
    }
}

/** Repair: whether to keep the rewritten file, or the page pictures, and how many pages came back. */
object RepairDecision {
    enum class Pick { REWRITTEN, PICTURES, NOTHING }

    /**
     * [rewritten] pages came back with their text from the forgiving parser (0 when it failed); [viewerPages] is how
     * many pages the phone's own renderer sees in the damaged file (null when it cannot open it); [rewrittenOpens] is
     * whether the renderer can open the rewritten file. The rewritten file is used unless it lost pages the viewer can
     * still see, or the viewer cannot open it: claiming "Repaired" for a file that dropped pages was the bug.
     */
    fun pick(rewritten: Int, viewerPages: Int?, rewrittenOpens: Boolean?): Pick = when {
        rewritten > 0 && rewrittenOpens != false && (viewerPages == null || viewerPages <= rewritten) -> Pick.REWRITTEN
        viewerPages != null && viewerPages > 0 -> Pick.PICTURES
        rewritten > 0 -> Pick.REWRITTEN
        else -> Pick.NOTHING
    }
}

/** Office to PDF: what is in the picked file, read from the zip's file list (no conversion), and why it cannot be read. */
class OfficeSurvey(
    val kind: OfficeToHtml.Kind?,
    /** Slides of a presentation, or null when not one. */
    val slides: Int? = null,
    /** Visible sheets of a workbook, or null. */
    val sheets: Int? = null,
    /** Sheets left out because the workbook hides them. */
    val hiddenSheets: Int = 0,
    /** Pictures stored in the document. */
    val pictures: Int = 0,
    /** Lines of a text or CSV file (counted up to a limit). */
    val lines: Int? = null,
    val problem: Problem? = null,
) {
    enum class Problem {
        /** An older binary Office file (.doc, .xls, .ppt) or an Office file locked with a password. */
        LEGACY_OR_LOCKED,
        /** Not a document the converter knows. */
        UNSUPPORTED,
        /** Zero bytes, or the zip is broken. */
        UNREADABLE,
    }

    companion object {
        private val SLIDE = Regex("ppt/slides/slide\\d+\\.xml")
        private val MEDIA = Regex("(word|ppt|xl)/media/[^/]+")
        private val SHEET_TAG = Regex("<(?:\\w+:)?sheet\\b[^>]*>")
        private val HIDDEN = Regex("state=\"(hidden|veryHidden)\"")

        /** Reads [input] as a file of [kind] (null: unsupported). Text files are counted up to [lineLimit] lines. */
        fun of(kind: OfficeToHtml.Kind?, input: InputStream, lineLimit: Int = 200_000): OfficeSurvey {
            val head = ByteArray(8)
            val buffered = input.buffered()
            buffered.mark(16)
            val read = buffered.read(head)
            buffered.reset()
            if (read <= 0) return OfficeSurvey(kind, problem = Problem.UNREADABLE)
            // D0 CF 11 E0: an OLE compound file, which is what .doc/.xls/.ppt and password-protected .docx/.xlsx are.
            val ole = read >= 4 && head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() && head[2] == 0x11.toByte() && head[3] == 0xE0.toByte()
            if (ole) return OfficeSurvey(kind, problem = Problem.LEGACY_OR_LOCKED)
            if (kind == null) return OfficeSurvey(null, problem = Problem.UNSUPPORTED)
            return when (kind) {
                OfficeToHtml.Kind.DOCX, OfficeToHtml.Kind.XLSX, OfficeToHtml.Kind.PPTX -> zip(kind, buffered)
                OfficeToHtml.Kind.TEXT, OfficeToHtml.Kind.CSV -> {
                    var lines = 0
                    var last = -1
                    while (lines < lineLimit) {
                        val b = buffered.read()
                        if (b < 0) break
                        if (b == '\n'.code) lines++
                        last = b
                    }
                    if (last != -1 && last != '\n'.code) lines++
                    OfficeSurvey(kind, lines = lines)
                }
                OfficeToHtml.Kind.HTML -> OfficeSurvey(kind)
            }
        }

        private fun zip(kind: OfficeToHtml.Kind, input: InputStream): OfficeSurvey {
            var slides = 0
            var pictures = 0
            var entries = 0
            var workbook: String? = null
            runCatching {
                val zip = ZipInputStream(input)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    val name = entry.name.trimStart('/')
                    when {
                        SLIDE.matches(name) -> slides++
                        MEDIA.matches(name) -> pictures++
                        // The workbook part is small: read it for the sheet list (hidden sheets are not printed).
                        name == "xl/workbook.xml" -> workbook = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
            if (entries == 0) return OfficeSurvey(kind, problem = Problem.UNREADABLE)
            return when (kind) {
                OfficeToHtml.Kind.PPTX -> OfficeSurvey(kind, slides = slides, pictures = pictures)
                OfficeToHtml.Kind.XLSX -> {
                    val tags = workbook?.let { SHEET_TAG.findAll(it).map { m -> m.value }.toList() }.orEmpty()
                    val hidden = tags.count { HIDDEN.containsMatchIn(it) }
                    OfficeSurvey(kind, sheets = tags.size - hidden, hiddenSheets = hidden, pictures = pictures)
                }
                else -> OfficeSurvey(kind, pictures = pictures)
            }
        }
    }
}

/** Images to PDF: the white border around each picture, in points (1/72 inch). */
enum class ImageMargin(val points: Float) {
    NONE(0f),
    SMALL(PdfExporter.MARGIN),
    LARGE(36f)
}

/** PDF info: page sizes in the units people know, and the paper name when it is a standard one. */
object PaperSizes {
    /** Portrait sizes in points (1/72 inch). */
    private val KNOWN = listOf(
        "A3" to (842f to 1191f),
        "A4" to (595f to 842f),
        "A5" to (420f to 595f),
        "B5" to (499f to 709f),
        "Letter" to (612f to 792f),
        "Legal" to (612f to 1008f),
        "Tabloid" to (792f to 1224f),
    )

    /** "A4" for a 595 × 842 point page either way round (within [tolerance] points), or null for any other size. */
    fun name(widthPt: Float, heightPt: Float, tolerance: Float = 3f): String? {
        val short = minOf(widthPt, heightPt)
        val long = maxOf(widthPt, heightPt)
        return KNOWN.firstOrNull { (_, size) ->
            kotlin.math.abs(size.first - short) <= tolerance && kotlin.math.abs(size.second - long) <= tolerance
        }?.first
    }

    fun toMillimetres(points: Float): Float = points * 25.4f / 72f

    fun toInches(points: Float): Float = points / 72f
}
