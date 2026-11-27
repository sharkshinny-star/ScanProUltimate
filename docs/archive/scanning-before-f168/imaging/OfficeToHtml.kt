package com.example.scanpro.imaging

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** The single error type [OfficeToHtml.convert] throws for corrupt, unsupported or unexpected input. */
class OfficeConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Converts office documents (DOCX, XLSX), plain text, CSV and HTML/MHTML into one self-contained, print-friendly
 * HTML document: inline CSS, images as data: URIs, no scripts and a Content-Security-Policy that blocks every
 * external load. Pure Kotlin/JDK (no Android classes) so it runs in JVM unit tests.
 */
object OfficeToHtml {
    enum class Kind { DOCX, XLSX, PPTX, TEXT, CSV, HTML }

    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
    private const val MAX_ZIP_TOTAL_BYTES = 200L * 1024 * 1024
    private const val MAX_TEXT_BYTES = 20 * 1024 * 1024
    private const val MAX_HTML_BYTES = 50 * 1024 * 1024
    private const val MAX_CELLS = 20_000
    private const val EMU_PER_PX = 9525.0 // 914400 EMU per inch / 96 dpi

    /** Which converter a file needs, from its MIME type or file name; null when unsupported. */
    fun kindOf(mimeType: String?, fileName: String?): Kind? {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        val byExt = when (ext) {
            "docx", "docm", "dotx" -> Kind.DOCX
            "xlsx", "xlsm", "xltx" -> Kind.XLSX
            "pptx", "pptm", "ppsx", "potx" -> Kind.PPTX
            "txt", "text", "log", "md" -> Kind.TEXT
            "csv" -> Kind.CSV
            "htm", "html", "xhtml", "mht", "mhtml" -> Kind.HTML
            else -> null
        }
        if (byExt != null) return byExt
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return when (mime) {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-word.document.macroenabled.12" -> Kind.DOCX
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroenabled.12" -> Kind.XLSX
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> Kind.PPTX
            "text/csv", "text/comma-separated-values", "application/csv" -> Kind.CSV
            "text/html", "application/xhtml+xml", "multipart/related", "message/rfc822",
            "application/x-mimearchive" -> Kind.HTML
            else -> if (mime == "text/plain" || mime == "text/markdown") Kind.TEXT else null
        }
    }

    /**
     * The few sentences the converter writes into the page itself. The caller passes them in the user's language;
     * [ENGLISH][Notes.ENGLISH] is the default (used by the unit tests).
     */
    class Notes(
        val imageNotShown: String,
        val workbookEmpty: String,
        val fileEmpty: String,
        val fileTooLong: String,
        /** Takes the cell limit already formatted, for example "20,000". */
        val workbookTruncated: (cells: String) -> String,
        val fileTruncated: (cells: String) -> String
    ) {
        companion object {
            val ENGLISH = Notes(
                imageNotShown = "[Image not shown]",
                workbookEmpty = "The workbook has no data.",
                fileEmpty = "The file has no data.",
                fileTooLong = "The file was too long; only the first 20 MB are shown.",
                workbookTruncated = { "Only the first $it cells are shown; the rest of the workbook was not converted." },
                fileTruncated = { "The file was too large; only the first $it cells are shown." }
            )
        }
    }

    /** The notes for the conversion running on this thread; conversions never share a thread at the same time. */
    private val activeNotes = object : ThreadLocal<Notes>() {
        override fun initialValue(): Notes = Notes.ENGLISH
    }

    private fun currentNotes(): Notes = activeNotes.get() ?: Notes.ENGLISH

    private fun note(text: String): String = esc(text)

    /** A complete, self-contained HTML document (inline CSS, images as data: URIs, no scripts, no external links loaded). */
    fun convert(kind: Kind, input: InputStream, title: String, notes: Notes = Notes.ENGLISH): String {
        val previous = activeNotes.get()
        activeNotes.set(notes)
        try {
            return convertChecked(kind, input, title)
        } finally {
            activeNotes.set(previous)
        }
    }

    private fun convertChecked(kind: Kind, input: InputStream, title: String): String = try {
        when (kind) {
            Kind.DOCX -> Docx.convert(input, title)
            Kind.XLSX -> Xlsx.convert(input, title)
            Kind.PPTX -> Pptx.convert(input, title)
            Kind.TEXT -> textToHtml(input, title)
            Kind.CSV -> csvToHtml(input, title)
            Kind.HTML -> Html.convert(input, title)
        }
    } catch (e: OfficeConversionException) {
        throw e
    } catch (e: StackOverflowError) {
        throw OfficeConversionException("The file is too complex to convert", e)
    } catch (e: OutOfMemoryError) {
        throw OfficeConversionException("The file is too large to convert", e)
    } catch (e: Exception) {
        throw OfficeConversionException("The file could not be read (${e.javaClass.simpleName})", e)
    }

    // ---------------------------------------------------------------- shared helpers

    private const val CSP = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:; " +
        "form-action 'none'; base-uri 'none'"

    private const val CSS = "@page { margin: 16mm; }\n" +
        "body { font-family: sans-serif; font-size: 11pt; line-height: 1.35; color: #000; background: #fff; " +
        "margin: 0; padding: 8px; overflow-wrap: break-word; }\n" +
        "@media print { body { padding: 0; } }\n" +
        "table { border-collapse: collapse; page-break-inside: auto; }\n" +
        "table.grid { margin: 6pt 0; }\n" +
        "table.grid td, table.grid th { border: 1px solid #888; padding: 2pt 4pt; vertical-align: top; }\n" +
        "table.grid th { background: #eee; text-align: left; }\n" +
        "tr { page-break-inside: avoid; }\n" +
        "img { max-width: 100%; height: auto; }\n" +
        "p { margin: 0 0 6pt; }\n" +
        "pre { white-space: pre-wrap; font-family: monospace; font-size: 10pt; margin: 0; }\n" +
        ".pb { page-break-after: always; break-after: page; height: 0; }\n" +
        ".link { color: #1a4fa0; text-decoration: underline; }\n" +
        ".note { color: #555; font-style: italic; margin-top: 12pt; }\n" +
        ".slide-no { color: #777; font-size: 9pt; margin: 0 0 8pt; }\n" +
        "h2.slide-title { font-size: 18pt; margin: 0 0 10pt; }\n"

    private fun headTags(title: String): String =
        "<meta charset=\"utf-8\">\n<meta http-equiv=\"Content-Security-Policy\" content=\"$CSP\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "<title>${esc(title)}</title>\n<style>\n$CSS</style>\n"

    private fun page(title: String, body: CharSequence): String =
        "<!doctype html>\n<html><head>\n${headTags(title)}</head>\n<body>\n$body\n</body></html>\n"

    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        escInto(sb, s)
        return sb.toString()
    }

    private fun escInto(sb: StringBuilder, s: CharSequence) {
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> if (c < ' ' && c != '\n' && c != '\t' && c != '\r') Unit else sb.append(c)
        }
    }

    /** Reads at most [limit] bytes; the flag says whether the input was longer. */
    private fun readCapped(input: InputStream, limit: Int): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) return out.toByteArray() to false
            out.write(buf, 0, n)
        }
        return out.toByteArray() to (input.read() >= 0)
    }

    /** Decodes using a BOM when present, else strict UTF-8, else Windows-1252. */
    private fun decodeText(bytes: ByteArray, declared: Charset? = null): String {
        fun b(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        if (b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (b(0) == 0xFE && b(1) == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        if (b(0) == 0xFF && b(1) == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (declared != null && declared != Charsets.UTF_8) return String(bytes, declared)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charsetOr("windows-1252", Charsets.ISO_8859_1)!!)
        }
    }

    private fun charsetOr(name: String?, fallback: Charset?): Charset? =
        try { if (name.isNullOrBlank()) fallback else Charset.forName(name.trim()) } catch (_: Exception) { fallback }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** java.util.Base64 needs API 26; minSdk is 24. */
    internal fun base64(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8) or (data[i + 2].toInt() and 0xFF)
            sb.append(B64[n ushr 18]).append(B64[n ushr 12 and 63]).append(B64[n ushr 6 and 63]).append(B64[n and 63])
            i += 3
        }
        val rem = data.size - i
        if (rem > 0) {
            val n = (data[i].toInt() and 0xFF shl 16) or (if (rem == 2) data[i + 1].toInt() and 0xFF shl 8 else 0)
            sb.append(B64[n ushr 18]).append(B64[n ushr 12 and 63])
            sb.append(if (rem == 2) B64[n ushr 6 and 63] else '=').append('=')
        }
        return sb.toString()
    }

    internal fun unbase64(s: CharSequence): ByteArray {
        val out = ByteArrayOutputStream(s.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (c in s) {
            val v = B64.indexOf(c).takeIf { it >= 0 } ?: when (c) { '-' -> 62; '_' -> 63; else -> -1 }
            if (v < 0) { if (c == '=') break else continue }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) { bits -= 8; out.write(acc ushr bits and 0xFF) }
        }
        return out.toByteArray()
    }

    private fun imageMime(name: String, bytes: ByteArray): String? {
        fun b(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        return when {
            b(0) == 0x89 && b(1) == 'P'.code && b(2) == 'N'.code -> "image/png"
            b(0) == 0xFF && b(1) == 0xD8 -> "image/jpeg"
            b(0) == 'G'.code && b(1) == 'I'.code && b(2) == 'F'.code -> "image/gif"
            b(0) == 'B'.code && b(1) == 'M'.code -> "image/bmp"
            b(0) == 'R'.code && b(8) == 'W'.code && b(9) == 'E'.code && b(10) == 'B'.code -> "image/webp"
            else -> when (name.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg", "jpe" -> "image/jpeg"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                else -> null // EMF/WMF/TIFF: a WebView can't show them
            }
        }
    }

    private fun dataUri(mime: String, bytes: ByteArray) = "data:$mime;base64,${base64(bytes)}"

    // ---------------------------------------------------------------- ZIP + XML

    /** Streams the ZIP and keeps only entries [want] accepts, skipping any over 50 MB uncompressed. */
    private fun readZip(input: InputStream, label: String, want: (String) -> Boolean): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        var total = 0L
        var sawEntry = false
        val zip = ZipInputStream(input)
        val buf = ByteArray(64 * 1024)
        while (true) {
            val entry = zip.nextEntry ?: break
            sawEntry = true
            val name = entry.name.trimStart('/')
            if (entry.isDirectory || !want(name) || entry.size > MAX_ENTRY_BYTES) continue
            val out = ByteArrayOutputStream(if (entry.size in 1..MAX_ENTRY_BYTES) entry.size.toInt() else 8192)
            var tooBig = false
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                if (out.size() + n > MAX_ENTRY_BYTES || total + out.size() + n > MAX_ZIP_TOTAL_BYTES) { tooBig = true; break }
                out.write(buf, 0, n)
            }
            if (!tooBig) { result[name] = out.toByteArray(); total += out.size() }
        }
        if (!sawEntry) throw OfficeConversionException("This is not a valid $label file")
        return result
    }

    private fun newDom(bytes: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        for ((k, v) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )) try { f.setFeature(k, v) } catch (_: Exception) { }
        return f.newDocumentBuilder().parse(InputSource(bytes.inputStream()))
    }

    private fun sax(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        for ((k, v) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) try { f.setFeature(k, v) } catch (_: Exception) { }
        try {
            f.newSAXParser().parse(InputSource(bytes.inputStream()), handler)
        } catch (_: StopParsing) {
        }
    }

    private class StopParsing : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun Element.children(): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) { if (n is Element) out.add(n); n = n.nextSibling }
        return out
    }

    private val Element.local: String get() = localName ?: tagName.substringAfter(':')

    private fun Element.child(name: String): Element? = children().firstOrNull { it.local == name }

    /** Attribute by local name, whatever its namespace prefix. */
    private fun Element.attr(name: String): String? {
        val attrs = attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if ((a.localName ?: a.nodeName.substringAfter(':')) == name) return a.nodeValue
        }
        return null
    }

    private fun Element.firstDescendant(name: String): Element? {
        for (c in children()) {
            if (c.local == name) return c
            c.firstDescendant(name)?.let { return it }
        }
        return null
    }

    private fun Attributes.byLocal(name: String): String? {
        for (i in 0 until length) if ((getLocalName(i).ifEmpty { getQName(i).substringAfter(':') }) == name) return getValue(i)
        return null
    }

    /** OPC relationships: Id → resolved part name (or raw target when external). */
    private fun readRels(bytes: ByteArray?, baseDir: String): Map<String, Pair<String, Boolean>> {
        if (bytes == null) return emptyMap()
        val out = HashMap<String, Pair<String, Boolean>>()
        for (r in newDom(bytes).documentElement.children()) {
            if (r.local != "Relationship") continue
            val id = r.attr("Id") ?: continue
            val target = r.attr("Target") ?: continue
            val external = r.attr("TargetMode").equals("External", ignoreCase = true)
            out[id] = (if (external) target else resolvePart(baseDir, target)) to external
        }
        return out
    }

    private fun resolvePart(baseDir: String, target: String): String {
        val raw = if (target.startsWith("/")) target.substring(1) else baseDir + target
        val parts = ArrayList<String>()
        for (p in raw.split('/')) when (p) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(p)
        }
        return parts.joinToString("/")
    }

    // ---------------------------------------------------------------- PPTX

    /**
     * PowerPoint (iLovePDF's PowerPoint to PDF): one printed page per slide, in the presentation's slide order. Text
     * boxes become paragraphs (title placeholders become headings) and pictures become images, in reading order (top
     * to bottom, then left to right). Themes, backgrounds, charts and animations are not drawn: the PDF carries the
     * slides' content, readable and searchable, not their design.
     */
    private object Pptx {
        private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val MAX_SLIDES = 500

        fun convert(input: InputStream, title: String): String {
            val zip = readZip(input, "PowerPoint") {
                it == "ppt/presentation.xml" || it == "ppt/_rels/presentation.xml.rels" ||
                    it.startsWith("ppt/slides/") || it.startsWith("ppt/media/")
            }
            val presentation = zip["ppt/presentation.xml"] ?: throw OfficeConversionException("This is not a valid PowerPoint file")
            val rels = readRels(zip["ppt/_rels/presentation.xml.rels"], "ppt/")
            // Slide order is the presentation's list, not the file names (a moved slide keeps its old name).
            val listed = newDom(presentation).documentElement.firstDescendant("sldIdLst")?.children().orEmpty()
                .mapNotNull { slideId -> slideId.getAttributeNS(REL_NS, "id").takeIf { it.isNotEmpty() }?.let { rels[it]?.first } }
                .filter { zip.containsKey(it) }
            val slides = listed.ifEmpty {
                zip.keys.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }
                    .sortedBy { it.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0 }
            }.take(MAX_SLIDES)
            if (slides.isEmpty()) return page(title, "<p class=\"note\">${note(currentNotes().fileEmpty)}</p>")

            val sb = StringBuilder()
            slides.forEachIndexed { index, path ->
                if (index > 0) sb.append("<div class=\"pb\"></div>\n")
                sb.append("<section class=\"slide\">\n<p class=\"slide-no\">").append(index + 1).append(" / ").append(slides.size).append("</p>\n")
                val slideRels = readRels(zip["ppt/slides/_rels/${path.substringAfterLast('/')}.rels"], "ppt/slides/")
                val tree = zip[path]?.let { newDom(it).documentElement.firstDescendant("spTree") }
                if (tree != null) items(tree).sortedWith(compareBy({ it.y }, { it.x })).forEach { item ->
                    when (item) {
                        is Item.Text -> item.paragraphs.forEach { para ->
                            if (item.title) sb.append("<h2 class=\"slide-title\">").append(esc(para)).append("</h2>\n")
                            else sb.append("<p>").append(esc(para).replace("\n", "<br>")).append("</p>\n")
                        }
                        is Item.Picture -> {
                            val part = slideRels[item.relId]?.takeIf { !it.second }?.first
                            val bytes = part?.let { zip[it] }
                            val mime = if (part != null && bytes != null) imageMime(part, bytes) else null
                            if (bytes != null && mime != null) sb.append("<p><img src=\"").append(dataUri(mime, bytes)).append("\" alt=\"\"></p>\n")
                            else sb.append("<p class=\"note\">").append(note(currentNotes().imageNotShown)).append("</p>\n")
                        }
                    }
                }
                sb.append("</section>\n")
            }
            return page(title, sb)
        }

        private sealed class Item(val x: Long, val y: Long) {
            class Text(x: Long, y: Long, val title: Boolean, val paragraphs: List<String>) : Item(x, y)
            class Picture(x: Long, y: Long, val relId: String) : Item(x, y)
        }

        /** Text boxes and pictures anywhere in the shape tree, groups included. */
        private fun items(tree: Element): List<Item> {
            val out = ArrayList<Item>()
            fun position(shape: Element): Pair<Long, Long> {
                val off = shape.firstDescendant("off")
                return (off?.attr("x")?.toLongOrNull() ?: 0L) to (off?.attr("y")?.toLongOrNull() ?: 0L)
            }
            fun walk(parent: Element) {
                for (child in parent.children()) when (child.local) {
                    "sp" -> {
                        val body = child.child("txBody") ?: continue
                        val paragraphs = body.children().filter { it.local == "p" }.map { paragraph(it) }.filter { it.isNotBlank() }
                        if (paragraphs.isEmpty()) continue
                        val type = child.firstDescendant("ph")?.attr("type")
                        val (x, y) = position(child)
                        out += Item.Text(x, y, type == "title" || type == "ctrTitle", paragraphs)
                    }
                    "pic" -> {
                        val rel = child.firstDescendant("blip")?.getAttributeNS(REL_NS, "embed")?.takeIf { it.isNotEmpty() } ?: continue
                        val (x, y) = position(child)
                        out += Item.Picture(x, y, rel)
                    }
                    "grpSp" -> walk(child)
                }
            }
            walk(tree)
            return out
        }

        /** One paragraph's text: its runs and fields in order, line breaks kept. */
        private fun paragraph(p: Element): String = buildString {
            for (part in p.children()) when (part.local) {
                "r", "fld" -> append(part.child("t")?.textContent.orEmpty())
                "br" -> append('\n')
            }
        }.trim()
    }

    // ---------------------------------------------------------------- DOCX

    private object Docx {
        fun convert(input: InputStream, title: String): String {
            val zip = readZip(input, "Word") {
                it == "word/document.xml" || it == "word/styles.xml" || it == "word/numbering.xml" ||
                    it == "word/_rels/document.xml.rels" || it.startsWith("word/media/") || it == "[Content_Types].xml"
            }
            val docBytes = zip["word/document.xml"] ?: throw OfficeConversionException("This is not a valid Word document")
            val ctx = Ctx(
                zip = zip,
                rels = readRels(zip["word/_rels/document.xml.rels"], "word/"),
                styles = readStyles(zip["word/styles.xml"]),
                numbering = readNumbering(zip["word/numbering.xml"]),
            )
            val body = newDom(docBytes).documentElement.child("body")
                ?: throw OfficeConversionException("The Word document has no body")
            val sb = StringBuilder()
            ctx.blocks(body, sb)
            return page(title, sb)
        }

        /** Heading level per style id (0 = not a heading). */
        private fun readStyles(bytes: ByteArray?): Map<String, Int> {
            if (bytes == null) return emptyMap()
            val out = HashMap<String, Int>()
            for (s in newDom(bytes).documentElement.children()) {
                if (s.local != "style" || s.attr("type") != "paragraph") continue
                val id = s.attr("styleId") ?: continue
                val name = s.child("name")?.attr("val").orEmpty()
                var level = headingLevel(name)
                if (level == 0) {
                    s.child("pPr")?.child("outlineLvl")?.attr("val")?.toIntOrNull()?.let { if (it in 0..5) level = it + 1 }
                }
                if (level > 0) out[id] = level
            }
            return out
        }

        fun headingLevel(name: String): Int {
            val n = name.lowercase().replace(" ", "")
            if (n == "title") return 1
            val m = Regex("^heading([1-9])$").find(n) ?: return 0
            return minOf(6, m.groupValues[1].toInt())
        }

        /** numId → (ilvl → ordered?). */
        private fun readNumbering(bytes: ByteArray?): Map<String, Map<Int, Boolean>> {
            if (bytes == null) return emptyMap()
            val root = newDom(bytes).documentElement
            val abstract = HashMap<String, Map<Int, Boolean>>()
            val nums = HashMap<String, String>()
            for (e in root.children()) when (e.local) {
                "abstractNum" -> {
                    val levels = HashMap<Int, Boolean>()
                    for (l in e.children()) if (l.local == "lvl") {
                        val ilvl = l.attr("ilvl")?.toIntOrNull() ?: continue
                        val fmt = l.child("numFmt")?.attr("val") ?: "bullet"
                        levels[ilvl] = fmt != "bullet" && fmt != "none"
                    }
                    abstract[e.attr("abstractNumId") ?: continue] = levels
                }
                "num" -> nums[e.attr("numId") ?: continue] = e.child("abstractNumId")?.attr("val") ?: continue
            }
            return nums.mapValues { abstract[it.value].orEmpty() }
        }

        private class Ctx(
            val zip: Map<String, ByteArray>,
            val rels: Map<String, Pair<String, Boolean>>,
            val styles: Map<String, Int>,
            val numbering: Map<String, Map<Int, Boolean>>,
        ) {
            fun blocks(container: Element, out: StringBuilder) {
                val lists = ArrayList<String>()
                fun closeTo(depth: Int) {
                    while (lists.size > depth) out.append("</li></").append(lists.removeAt(lists.size - 1)).append(">\n")
                }
                fun walk(parent: Element) {
                    for (e in parent.children()) when (e.local) {
                        "p" -> paragraph(e, out, lists, ::closeTo)
                        "tbl" -> { closeTo(0); table(e, out) }
                        "sdt" -> e.child("sdtContent")?.let { walk(it) }
                        "customXml", "ins", "smartTag" -> walk(e)
                        "AlternateContent" -> (e.child("Choice") ?: e.child("Fallback"))?.let { walk(it) }
                        else -> Unit
                    }
                }
                walk(container)
                closeTo(0)
            }

            private fun paragraph(p: Element, out: StringBuilder, lists: ArrayList<String>, closeTo: (Int) -> Unit) {
                val pPr = p.child("pPr")
                val styleId = pPr?.child("pStyle")?.attr("val")
                val level = styleId?.let { styles[it] ?: headingLevel(it) } ?: 0
                val align = when (pPr?.child("jc")?.attr("val")) {
                    "center" -> "center"
                    "right", "end" -> "right"
                    "both", "distribute", "lowKashida", "mediumKashida", "highKashida", "thaiDistribute" -> "justify"
                    else -> null
                }
                val numPr = pPr?.child("numPr")
                val numId = numPr?.child("numId")?.attr("val")
                val ilvl = (numPr?.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0).coerceIn(0, 8)
                val isList = level == 0 && numId != null && numId != "0"
                val tag = if (level > 0) "h$level" else if (isList) "li" else "p"
                val attrs = if (align != null) " style=\"text-align:$align\"" else ""

                if (pPr?.child("pageBreakBefore")?.let { on(it) } == true) {
                    closeTo(0); out.append("<div class=\"pb\"></div>\n")
                }
                val pieces = ArrayList<StringBuilder>().apply { add(StringBuilder()) }
                inline(p, pieces)

                if (isList) {
                    val ordered = numbering[numId]?.get(ilvl) ?: false
                    val lt = if (ordered) "ol" else "ul"
                    closeTo(ilvl + 1)
                    if (lists.size == ilvl + 1 && lists[ilvl] != lt) closeTo(ilvl)
                    if (lists.size == ilvl + 1) out.append("</li>\n")
                    while (lists.size < ilvl + 1) {
                        out.append('<').append(lt).append(">\n")
                        lists.add(lt)
                        if (lists.size < ilvl + 1) out.append("<li style=\"list-style:none\">")
                    }
                    out.append("<li").append(attrs).append('>')
                    pieces.forEachIndexed { i, s ->
                        if (i > 0) out.append("<div class=\"pb\"></div>")
                        out.append(s)
                    }
                    return
                }
                closeTo(0)
                pieces.forEachIndexed { i, s ->
                    if (i > 0) out.append("<div class=\"pb\"></div>\n")
                    if (s.isEmpty() && i > 0) return@forEachIndexed
                    out.append('<').append(tag).append(attrs).append('>')
                    if (s.isBlank()) out.append("&nbsp;") else out.append(s)
                    out.append("</").append(tag).append(">\n")
                }
            }

            private fun on(e: Element): Boolean = when (e.attr("val")?.lowercase()) {
                null, "true", "1", "on" -> true
                else -> false
            }

            /** Paragraph content; a page break starts a new piece. */
            private fun inline(parent: Element, pieces: ArrayList<StringBuilder>) {
                for (e in parent.children()) {
                    val cur = pieces.last()
                    when (e.local) {
                        "r" -> run(e, pieces)
                        "hyperlink" -> {
                            val href = e.attr("id")?.let { rels[it]?.first } ?: e.attr("anchor")?.let { "#$it" }
                            val inner = ArrayList<StringBuilder>().apply { add(StringBuilder()) }
                            inline(e, inner)
                            cur.append("<span class=\"link\"")
                            if (href != null) cur.append(" title=\"").append(esc(href)).append('"')
                            cur.append('>').append(inner.joinToString("")).append("</span>")
                        }
                        "ins", "smartTag", "customXml", "fldSimple", "bdo", "dir" -> inline(e, pieces)
                        "sdt" -> e.child("sdtContent")?.let { inline(it, pieces) }
                        "AlternateContent" -> (e.child("Choice") ?: e.child("Fallback"))?.let { inline(it, pieces) }
                        else -> Unit // del, bookmarks, proofing marks, comments …
                    }
                }
            }

            private fun run(r: Element, pieces: ArrayList<StringBuilder>) {
                val rPr = r.child("rPr")
                fun flag(name: String) = rPr?.child(name)?.let { on(it) } ?: false
                val bold = flag("b")
                val italic = flag("i")
                val underline = rPr?.child("u")?.attr("val").let { it != null && it != "none" }
                val strike = flag("strike") || flag("dstrike")
                val va = rPr?.child("vertAlign")?.attr("val")
                val open = StringBuilder()
                val close = StringBuilder()
                fun wrap(t: String) { open.append('<').append(t).append('>'); close.insert(0, "</$t>") }
                if (bold) wrap("strong")
                if (italic) wrap("em")
                if (underline) wrap("u")
                if (strike) wrap("s")
                if (va == "superscript") wrap("sup") else if (va == "subscript") wrap("sub")

                var buf = StringBuilder()
                fun flush() {
                    if (buf.isNotEmpty()) pieces.last().append(open).append(buf).append(close)
                    buf = StringBuilder()
                }
                for (c in r.children()) when (c.local) {
                    "t" -> escInto(buf, c.textContent)
                    "tab", "ptab" -> buf.append("&emsp;")
                    "br" -> if (c.attr("type") == "page") { flush(); pieces.add(StringBuilder()) } else buf.append("<br>")
                    "cr" -> buf.append("<br>")
                    "noBreakHyphen" -> buf.append('-')
                    "drawing", "pict", "object" -> { flush(); image(c)?.let { pieces.last().append(it) } }
                    "AlternateContent" -> {
                        flush()
                        val alt = c.child("Choice") ?: c.child("Fallback")
                        alt?.let { a -> a.children().firstOrNull { it.local == "drawing" || it.local == "pict" } }
                            ?.let { image(it) }?.let { pieces.last().append(it) }
                    }
                    else -> Unit
                }
                flush()
            }

            private fun image(holder: Element): String? {
                val blip = holder.firstDescendant("blip")
                val relId = blip?.attr("embed") ?: holder.firstDescendant("imagedata")?.attr("id") ?: return null
                val (part, external) = rels[relId] ?: return null
                if (external) return null
                val bytes = zip[part] ?: return null
                val mime = imageMime(part, bytes) ?: return "<span class=\"note\">${note(currentNotes().imageNotShown)}</span>"
                val cx = holder.firstDescendant("extent")?.attr("cx")?.toLongOrNull()
                val width = cx?.let { (it / EMU_PER_PX).toInt() }?.takeIf { it > 0 }
                val style = if (width != null) " style=\"width:${width}px;max-width:100%;height:auto\"" else ""
                return "<img alt=\"\" src=\"${dataUri(mime, bytes)}\"$style>"
            }

            private class Cell(val html: String, val colspan: Int, var rowspan: Int, val col: Int)

            private fun table(tbl: Element, out: StringBuilder) {
                val rows = ArrayList<List<Cell>>()
                val openMerges = HashMap<Int, Cell>()
                for (tr in tbl.children()) {
                    if (tr.local != "tr") continue
                    val cells = ArrayList<Cell>()
                    var col = tr.child("trPr")?.child("gridBefore")?.attr("val")?.toIntOrNull() ?: 0
                    for (tc in tcs(tr)) {
                        val tcPr = tc.child("tcPr")
                        val span = (tcPr?.child("gridSpan")?.attr("val")?.toIntOrNull() ?: 1).coerceIn(1, 1000)
                        val vMerge = tcPr?.child("vMerge")
                        if (vMerge != null && vMerge.attr("val") != "restart" && openMerges[col] != null) {
                            openMerges[col]!!.rowspan++
                        } else {
                            val sb = StringBuilder()
                            blocks(tc, sb)
                            val cell = Cell(sb.toString(), span, 1, col)
                            cells.add(cell)
                            if (vMerge != null) openMerges[col] = cell else openMerges.remove(col)
                        }
                        col += span
                    }
                    rows.add(cells)
                }
                out.append("<table class=\"grid\">\n")
                for (row in rows) {
                    out.append("<tr>")
                    for (c in row) {
                        out.append("<td")
                        if (c.colspan > 1) out.append(" colspan=\"").append(c.colspan).append('"')
                        if (c.rowspan > 1) out.append(" rowspan=\"").append(c.rowspan).append('"')
                        out.append('>').append(c.html).append("</td>")
                    }
                    out.append("</tr>\n")
                }
                out.append("</table>\n")
            }

            private fun tcs(tr: Element): List<Element> = tr.children().flatMap {
                when (it.local) {
                    "tc" -> listOf(it)
                    "sdt" -> it.child("sdtContent")?.let { c -> tcs(c) } ?: emptyList()
                    "customXml" -> tcs(it)
                    else -> emptyList()
                }
            }
        }
    }

    // ---------------------------------------------------------------- XLSX

    private object Xlsx {
        fun convert(input: InputStream, title: String): String {
            val zip = readZip(input, "Excel") {
                it == "xl/workbook.xml" || it == "xl/_rels/workbook.xml.rels" || it == "xl/sharedStrings.xml" ||
                    it.startsWith("xl/worksheets/")
            }
            val wb = zip["xl/workbook.xml"] ?: throw OfficeConversionException("This is not a valid Excel workbook")
            val rels = readRels(zip["xl/_rels/workbook.xml.rels"], "xl/")
            val shared = sharedStrings(zip["xl/sharedStrings.xml"])
            val sb = StringBuilder()
            var budget = MAX_CELLS
            var truncated = false
            val sheetsEl = newDom(wb).documentElement.child("sheets")
            for (s in sheetsEl?.children().orEmpty()) {
                if (s.local != "sheet") continue
                if (s.attr("state")?.let { it == "hidden" || it == "veryHidden" } == true) continue
                val part = rels[s.attr("id") ?: continue]?.first ?: continue
                val bytes = zip[part] ?: continue
                if (budget <= 0) { truncated = true; break }
                val sheet = readSheet(bytes, shared, budget)
                if (sheet.cells.isEmpty()) { truncated = truncated || sheet.truncated; continue }
                val used = render(s.attr("name") ?: "Sheet", sheet, budget, sb)
                budget -= used.first
                truncated = truncated || sheet.truncated || used.second
            }
            if (truncated) {
                val cells = String.format(java.util.Locale.ROOT, "%,d", MAX_CELLS)
                sb.append("<p class=\"note\">").append(note(currentNotes().workbookTruncated(cells))).append("</p>\n")
            }
            if (sb.isEmpty()) sb.append("<p class=\"note\">").append(note(currentNotes().workbookEmpty)).append("</p>")
            return page(title, sb)
        }

        private fun sharedStrings(bytes: ByteArray?): List<String> {
            if (bytes == null) return emptyList()
            val out = ArrayList<String>()
            sax(bytes, object : DefaultHandler() {
                var cur: StringBuilder? = null
                var inT = false
                var phonetic = 0
                override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                    when (local.ifEmpty { q.substringAfter(':') }) {
                        "si" -> cur = StringBuilder()
                        "rPh" -> phonetic++
                        "t" -> inT = phonetic == 0
                    }
                }
                override fun endElement(uri: String, local: String, q: String) {
                    when (local.ifEmpty { q.substringAfter(':') }) {
                        "si" -> { out.add(cur?.toString().orEmpty()); cur = null }
                        "rPh" -> phonetic--
                        "t" -> inT = false
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inT) cur?.appendRange(ch, start, start + length)
                }
            })
            return out
        }

        class Sheet(val cells: Map<Int, Map<Int, String>>, val merges: List<IntArray>, val truncated: Boolean)

        /** Column letters of a cell reference ("AA7") → 0-based index; -1 when absent. */
        fun columnIndex(ref: String): Int {
            var n = 0
            var seen = false
            for (c in ref) {
                val u = c.uppercaseChar()
                if (u in 'A'..'Z') { n = n * 26 + (u - 'A' + 1); seen = true; if (n > 1_000_000) return -1 } else break
            }
            return if (seen) n - 1 else -1
        }

        private fun rowIndex(ref: String): Int = ref.dropWhile { it.isLetter() }.toIntOrNull()?.minus(1) ?: -1

        private fun readSheet(bytes: ByteArray, shared: List<String>, budget: Int): Sheet {
            val cells = java.util.TreeMap<Int, java.util.TreeMap<Int, String>>()
            val merges = ArrayList<IntArray>()
            var count = 0
            var truncated = false
            sax(bytes, object : DefaultHandler() {
                var row = -1
                var col = -1
                var type: String? = null
                var text: StringBuilder? = null
                var value: String? = null
                var inline = StringBuilder()
                var inIs = false
                var phonetic = 0
                override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                    when (local.ifEmpty { q.substringAfter(':') }) {
                        "row" -> { row = a.byLocal("r")?.toIntOrNull()?.minus(1) ?: (row + 1); col = -1 }
                        "c" -> {
                            val ref = a.byLocal("r")
                            col = ref?.let { columnIndex(it) }?.takeIf { it >= 0 } ?: (col + 1)
                            if (ref != null) rowIndex(ref).takeIf { it >= 0 }?.let { row = it }
                            type = a.byLocal("t"); value = null; inline = StringBuilder(); inIs = false
                        }
                        "v" -> text = StringBuilder()
                        "is" -> inIs = true
                        "rPh" -> phonetic++
                        "t" -> if (inIs && phonetic == 0) text = StringBuilder()
                        "mergeCell" -> a.byLocal("ref")?.let { ref ->
                            val parts = ref.split(':')
                            if (parts.size == 2) {
                                val r0 = rowIndex(parts[0]); val c0 = columnIndex(parts[0])
                                val r1 = rowIndex(parts[1]); val c1 = columnIndex(parts[1])
                                if (r0 >= 0 && c0 >= 0 && r1 >= r0 && c1 >= c0) merges.add(intArrayOf(r0, c0, r1, c1))
                            }
                        }
                    }
                }
                override fun endElement(uri: String, local: String, q: String) {
                    when (local.ifEmpty { q.substringAfter(':') }) {
                        "v" -> { value = text?.toString(); text = null }
                        "t" -> if (inIs && text != null) { inline.append(text); text = null }
                        "rPh" -> phonetic--
                        "c" -> {
                            val shown = when (type) {
                                "s" -> value?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }
                                "inlineStr" -> inline.toString()
                                "b" -> when (value?.trim()) { "1" -> "TRUE"; "0" -> "FALSE"; else -> value }
                                "str", "e", "d" -> value
                                else -> value?.trim()?.let { if (it.endsWith(".0")) it.dropLast(2) else it }
                            }
                            if (!shown.isNullOrEmpty() && row >= 0 && col >= 0) {
                                if (count >= budget) { truncated = true; throw StopParsing() }
                                cells.getOrPut(row) { java.util.TreeMap() }[col] = shown
                                count++
                            }
                        }
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    text?.appendRange(ch, start, start + length)
                }
            })
            return Sheet(cells, merges, truncated)
        }

        /** Returns (cells rendered, truncated). */
        private fun render(name: String, sheet: Sheet, budget: Int, sb: StringBuilder): Pair<Int, Boolean> {
            val rows = sheet.cells.keys
            val minRow = rows.first()
            val maxRow = rows.last()
            val minCol = sheet.cells.values.minOf { it.keys.first() }
            val maxCol = sheet.cells.values.maxOf { it.keys.last() }
            val width = maxCol - minCol + 1
            val covered = HashSet<Long>()
            val spans = HashMap<Long, IntArray>()
            fun key(r: Int, c: Int) = r.toLong() shl 32 or c.toLong()
            for (m in sheet.merges) {
                val r0 = maxOf(m[0], minRow); val c0 = maxOf(m[1], minCol)
                val r1 = minOf(m[2], maxRow); val c1 = minOf(m[3], maxCol)
                if (r0 != m[0] || c0 != m[1] || r1 < r0 || c1 < c0) continue
                if ((r1 - r0 + 1).toLong() * (c1 - c0 + 1) > MAX_CELLS) continue
                spans[key(r0, c0)] = intArrayOf(r1 - r0 + 1, c1 - c0 + 1)
                for (r in r0..r1) for (c in c0..c1) if (r != r0 || c != c0) covered.add(key(r, c))
            }
            sb.append("<h2>").append(esc(name)).append("</h2>\n<table class=\"grid\">\n")
            var used = 0
            var truncated = false
            for (r in minRow..maxRow) {
                if (used + width > budget) { truncated = true; break }
                val row = sheet.cells[r]
                sb.append("<tr>")
                for (c in minCol..maxCol) {
                    if (key(r, c) in covered) continue
                    sb.append("<td")
                    spans[key(r, c)]?.let {
                        if (it[0] > 1) sb.append(" rowspan=\"").append(it[0]).append('"')
                        if (it[1] > 1) sb.append(" colspan=\"").append(it[1]).append('"')
                    }
                    sb.append('>')
                    row?.get(c)?.let { escInto(sb, it) }
                    sb.append("</td>")
                }
                sb.append("</tr>\n")
                used += width
            }
            sb.append("</table>\n")
            return used to truncated
        }
    }

    // ---------------------------------------------------------------- TEXT and CSV

    private fun textToHtml(input: InputStream, title: String): String {
        val (bytes, more) = readCapped(input, MAX_TEXT_BYTES)
        val sb = StringBuilder("<pre>")
        escInto(sb, decodeText(bytes))
        sb.append("</pre>")
        if (more) sb.append("\n<p class=\"note\">").append(note(currentNotes().fileTooLong)).append("</p>")
        return page(title, sb)
    }

    internal fun parseCsv(text: String, maxCells: Int = Int.MAX_VALUE): Pair<List<List<String>>, Boolean> {
        val firstLine = text.substringBefore('\n')
        val commas = firstLine.count { it == ',' }
        val semis = firstLine.count { it == ';' }
        val tabs = firstLine.count { it == '\t' }
        val sep = when {
            tabs > commas && tabs > semis -> '\t'
            semis > commas -> ';'
            else -> ','
        }
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var count = 0
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        var quoted = false
        var fieldStarted = false
        fun endField() { row.add(field.toString()); field.setLength(0); fieldStarted = false; count++ }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = ArrayList()
        }
        while (i < text.length) {
            if (count >= maxCells) return rows to true
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> if (field.isEmpty() && !fieldStarted) { quoted = true; fieldStarted = true } else field.append(c)
                sep -> endField()
                '\r' -> { endRow(); if (i + 1 < text.length && text[i + 1] == '\n') i++ }
                '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) endRow()
        return rows to false
    }

    private fun csvToHtml(input: InputStream, title: String): String {
        val (bytes, more) = readCapped(input, MAX_TEXT_BYTES)
        val (rows, capped) = parseCsv(decodeText(bytes), MAX_CELLS)
        val width = rows.maxOfOrNull { it.size } ?: 0
        val sb = StringBuilder()
        if (rows.isEmpty()) sb.append("<p class=\"note\">").append(note(currentNotes().fileEmpty)).append("</p>")
        else {
            sb.append("<table class=\"grid\">\n")
            rows.forEachIndexed { r, row ->
                val cell = if (r == 0) "th" else "td"
                if (r == 0) sb.append("<thead>")
                if (r == 1) sb.append("<tbody>")
                sb.append("<tr>")
                for (c in 0 until width) {
                    sb.append('<').append(cell).append('>')
                    row.getOrNull(c)?.let { escInto(sb, it) }
                    sb.append("</").append(cell).append('>')
                }
                sb.append("</tr>")
                if (r == 0) sb.append("</thead>")
                sb.append('\n')
            }
            if (rows.size > 1) sb.append("</tbody>")
            sb.append("</table>\n")
        }
        if (capped || more) {
            val cells = String.format(java.util.Locale.ROOT, "%,d", MAX_CELLS)
            sb.append("<p class=\"note\">").append(note(currentNotes().fileTruncated(cells))).append("</p>")
        }
        return page(title, sb)
    }

    // ---------------------------------------------------------------- HTML and MHTML

    private object Html {
        private val OPT = setOf(RegexOption.IGNORE_CASE)
        private val dangerousBlocks = listOf("script", "iframe", "object", "embed", "applet", "frameset", "frame", "noscript", "template")
            .map { Regex("<$it\\b(?:\"[^\"]*\"|'[^']*'|[^'\">]++)*+>[\\s\\S]*?</$it\\s*>", OPT) }
        private val dangerousTags = Regex(
            "</?(?:script|iframe|object|embed|applet|frameset|frame|base|link|portal)\\b(?:\"[^\"]*\"|'[^']*'|[^'\">]++)*+>", OPT,
        )
        private val unclosedScript = Regex("<script\\b[\\s\\S]*$", OPT)
        private val metaTag = Regex("<meta\\b(?:\"[^\"]*\"|'[^']*'|[^'\">]++)*+>", OPT)
        private val doctype = Regex("<!doctype[^>]*>", OPT)
        private val tag = Regex("<([a-zA-Z][a-zA-Z0-9:_-]*)((?:\"[^\"]*\"|'[^']*'|[^'\">]++)*+)>")
        private val attr = Regex("([^\\s\"'>/=]+)(\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?")
        private val looseTag = Regex("<[a-zA-Z][^>]*+>")
        private val looseOn = Regex("([\\s/\"'])on[a-zA-Z_-]+\\s*=", OPT)
        private val urlAttrs = setOf("href", "src", "action", "formaction", "xlink:href", "background", "poster", "data", "srcdoc", "lowsrc", "dynsrc")
        private val headOpen = Regex("<head\\b[^>]*>", OPT)
        private val htmlOpen = Regex("<html\\b[^>]*>", OPT)
        private val titleTag = Regex("<title\\b", OPT)

        fun convert(input: InputStream, title: String): String {
            val (bytes, _) = readCapped(input, MAX_HTML_BYTES)
            val latin = String(bytes, 0, minOf(bytes.size, 8192), Charsets.ISO_8859_1)
            val html = if (isMhtml(latin)) Mhtml.html(String(bytes, Charsets.ISO_8859_1))
            else {
                val declared = Regex("<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", OPT).find(latin)?.groupValues?.get(1)
                decodeText(bytes, charsetOr(declared, null))
            }
            return wrap(sanitise(html), title)
        }

        private fun isMhtml(head: String): Boolean {
            val headers = head.substringBefore("\r\n\r\n").substringBefore("\n\n")
            return !headers.trimStart().startsWith("<") &&
                Regex("^content-type:\\s*multipart/related", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(headers)
        }

        fun sanitise(src: String): String {
            var s = src.replace(Regex("<!--[\\s\\S]*?-->"), "")
            for (r in dangerousBlocks) s = r.replace(s, "")
            s = dangerousTags.replace(s, "")
            s = unclosedScript.replace(s, "")
            s = metaTag.replace(s) { m ->
                val t = m.value.lowercase()
                if ("http-equiv" in t || "charset" in t) "" else m.value
            }
            s = doctype.replace(s, "")
            s = tag.replace(s) { m -> cleanTag(m.groupValues[1], m.groupValues[2]) }
            // Fallback for malformed tags the strict pattern could not parse (e.g. an unbalanced quote).
            return looseTag.replace(s) { m -> looseOn.replace(m.value, "$1data-x-on=") }
        }

        private fun cleanTag(name: String, attrs: String): String {
            val sb = StringBuilder("<").append(name)
            for (a in attr.findAll(attrs)) {
                val an = a.groupValues[1].lowercase()
                if (an.startsWith("on") || an == "/" || an == "formaction" || an == "srcdoc") continue
                val rawVal = a.groupValues[2].substringAfter('=', "").trim().trim('"', '\'')
                if (an in urlAttrs || an == "style") {
                    val v = decodeEntities(rawVal).filter { it > ' ' }.lowercase()
                    if (Regex("(javascript|vbscript|livescript|data:text/html|expression\\()").containsMatchIn(v) &&
                        !(an == "src" && v.startsWith("data:image/"))
                    ) continue
                }
                sb.append(' ').append(a.value.trim())
            }
            if (attrs.trimEnd().endsWith("/")) sb.append(" /")
            return sb.append('>').toString()
        }

        private fun decodeEntities(s: String): String =
            Regex("&#(x[0-9a-fA-F]+|[0-9]+);?|&(colon|tab|newline);", OPT).replace(s) { m ->
                val g = m.groupValues[1]
                when {
                    g.isEmpty() -> when (m.groupValues[2].lowercase()) { "colon" -> ":"; else -> "" }
                    g[0] == 'x' || g[0] == 'X' -> g.substring(1).toIntOrNull(16)?.let { String(Character.toChars(it.coerceIn(0, 0x10FFFF))) } ?: ""
                    else -> g.toIntOrNull()?.let { String(Character.toChars(it.coerceIn(0, 0x10FFFF))) } ?: ""
                }
            }

        private fun wrap(html: String, title: String): String {
            val headExtra = headTags(title).let { if (titleTag.containsMatchIn(html)) it.replace(Regex("<title>[\\s\\S]*?</title>\n"), "") else it }
            headOpen.find(html)?.let { m ->
                return "<!doctype html>\n" + html.substring(0, m.range.last + 1) + "\n" + headExtra + html.substring(m.range.last + 1)
            }
            htmlOpen.find(html)?.let { m ->
                return "<!doctype html>\n" + html.substring(0, m.range.last + 1) + "\n<head>\n" + headExtra + "</head>" +
                    html.substring(m.range.last + 1)
            }
            return "<!doctype html>\n<html><head>\n$headExtra</head>\n<body>\n$html\n</body></html>\n"
        }
    }

    private object Mhtml {
        private class Part(val headers: Map<String, String>, val body: String)

        fun html(raw: String): String {
            val (headers, body) = splitHeaders(raw)
            val boundary = param(headers["content-type"].orEmpty(), "boundary")
                ?: throw OfficeConversionException("The web archive has no MIME boundary")
            val parts = body.split("--$boundary").drop(1).filterNot { it.startsWith("--") }.map {
                val (h, b) = splitHeaders(it.removePrefix("\r\n").removePrefix("\n"))
                Part(h, b)
            }
            val htmlPart = parts.firstOrNull { it.headers["content-type"].orEmpty().lowercase().startsWith("text/html") }
                ?: throw OfficeConversionException("The web archive has no HTML page")
            val charset = charsetOr(param(htmlPart.headers["content-type"].orEmpty(), "charset"), null)
            var html = decodeText(decode(htmlPart), charset)

            val replacements = HashMap<String, String>()
            for (p in parts) {
                if (p === htmlPart) continue
                val ct = p.headers["content-type"].orEmpty().substringBefore(';').trim().lowercase()
                if (!ct.startsWith("image/") || ct.contains("svg")) continue
                val uri = dataUri(ct, decode(p))
                p.headers["content-location"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    replacements[it] = uri
                    replacements[it.replace("&", "&amp;")] = uri
                }
                p.headers["content-id"]?.trim()?.removeSurrounding("<", ">")?.takeIf { it.isNotEmpty() }?.let {
                    replacements["cid:$it"] = uri
                }
            }
            if (replacements.isNotEmpty()) {
                val pattern = Regex(replacements.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })
                html = pattern.replace(html) { replacements[it.value] ?: it.value }
            }
            return html
        }

        private fun splitHeaders(s: String): Pair<Map<String, String>, String> {
            val idx = Regex("\r?\n\r?\n").find(s) ?: return emptyMap<String, String>() to s
            val headerText = s.substring(0, idx.range.first).replace(Regex("\r?\n[ \t]+"), " ")
            val map = HashMap<String, String>()
            for (line in headerText.lines()) {
                val c = line.indexOf(':')
                if (c > 0) map.putIfAbsent(line.substring(0, c).trim().lowercase(), line.substring(c + 1).trim())
            }
            return map to s.substring(idx.range.last + 1)
        }

        private fun param(header: String, name: String): String? =
            Regex("(?:^|;)\\s*$name\\s*=\\s*(\"([^\"]*)\"|[^;\\s]+)", RegexOption.IGNORE_CASE).find(header)?.let {
                it.groupValues[2].ifEmpty { it.groupValues[1] }
            }

        /** Part body (held as ISO-8859-1 so chars = bytes) → raw bytes. */
        private fun decode(p: Part): ByteArray = when (p.headers["content-transfer-encoding"]?.trim()?.lowercase()) {
            "base64" -> unbase64(p.body)
            "quoted-printable" -> {
                val out = ByteArrayOutputStream(p.body.length)
                val s = p.body
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    if (c == '=') {
                        when {
                            s.startsWith("=\r\n", i) -> { i += 3; continue }
                            s.startsWith("=\n", i) -> { i += 2; continue }
                            i + 2 < s.length && s.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                                out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3; continue
                            }
                        }
                    }
                    out.write(c.code and 0xFF)
                    i++
                }
                out.toByteArray()
            }
            else -> p.body.toByteArray(Charsets.ISO_8859_1)
        }
    }
}
