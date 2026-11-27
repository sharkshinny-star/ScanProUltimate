package com.example.scanpro.imaging

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes recognised text as an editable Word document (.docx): a title, then each page's text with one
 * paragraph per line and a page break between pages. A .docx is a zip of a few XML parts, so no library
 * is needed; Word, Google Docs, WPS and LibreOffice all open it.
 */
object DocxExporter {
    fun export(title: String, pages: List<String>, destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { write(title, pages, it) }
    }

    fun write(title: String, pages: List<String>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.part("[Content_Types].xml", CONTENT_TYPES)
            zip.part("_rels/.rels", ROOT_RELS)
            zip.part("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.part("word/styles.xml", STYLES)
            zip.part("word/document.xml", documentXml(title, pages))
        }
    }

    internal fun documentXml(title: String, pages: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        paragraph(title, style = "Title")
        pages.forEachIndexed { index, text ->
            if (index > 0) append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            val lines = text.trim().lines()
            if (lines.all { it.isBlank() }) paragraph("[No text recognised on page ${index + 1}]")
            else lines.forEach { paragraph(it.trimEnd()) }
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    private fun StringBuilder.paragraph(text: String, style: String? = null) {
        append("<w:p>")
        if (style != null) append("""<w:pPr><w:pStyle w:val="$style"/></w:pPr>""")
        if (text.isNotEmpty()) append("""<w:r><w:t xml:space="preserve">""").append(escape(text)).append("</w:t></w:r>")
        append("</w:p>")
    }

    /** XML-escapes [text] and drops characters XML 1.0 cannot hold (OCR occasionally yields control characters). */
    internal fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\t' -> append(' ')
                c < ' ' || c == '￾' || c == '￿' -> Unit
                else -> append(c)
            }
        }
    }

    private fun ZipOutputStream.part(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="80"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style></w:styles>"""
}
