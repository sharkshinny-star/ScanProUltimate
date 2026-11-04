package com.example.scanpro.imaging

import com.example.scanpro.imaging.OfficeToHtml.Kind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeToHtmlTest {
    private val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
        "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
        "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
    private val pngBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3)

    private fun zip(vararg parts: Pair<String, Any>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, content) in parts) {
                z.putNextEntry(ZipEntry(name))
                z.write(if (content is ByteArray) content else content.toString().toByteArray())
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun convert(kind: Kind, bytes: ByteArray, title: String = "T") =
        OfficeToHtml.convert(kind, ByteArrayInputStream(bytes), title)

    private fun convert(kind: Kind, text: String) = convert(kind, text.toByteArray())

    private fun docx(body: String): String = convert(
        Kind.DOCX,
        zip(
            "word/document.xml" to "<?xml version=\"1.0\"?><w:document $w><w:body>$body</w:body></w:document>",
            "word/styles.xml" to "<w:styles $w><w:style w:type=\"paragraph\" w:styleId=\"berschrift2\">" +
                "<w:name w:val=\"heading 2\"/></w:style></w:styles>",
            "word/numbering.xml" to "<w:numbering $w>" +
                "<w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/></w:lvl></w:abstractNum>" +
                "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/></w:lvl></w:abstractNum>" +
                "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>" +
                "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>",
            "word/_rels/document.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId5\" Type=\"image\" Target=\"media/image1.png\"/>" +
                "<Relationship Id=\"rId6\" Type=\"hyperlink\" Target=\"https://example.com/?a=1&amp;b=2\" TargetMode=\"External\"/>" +
                "</Relationships>",
            "word/media/image1.png" to pngBytes,
        ),
    )

    private fun p(text: String, pPr: String = "", rPr: String = "") =
        "<w:p><w:pPr>$pPr</w:pPr><w:r><w:rPr>$rPr</w:rPr><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"

    @Test fun kindDetection() {
        assertEquals(Kind.DOCX, OfficeToHtml.kindOf(null, "Report.DOCX"))
        assertEquals(Kind.XLSX, OfficeToHtml.kindOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null))
        assertEquals(Kind.CSV, OfficeToHtml.kindOf("text/plain", "data.csv"))
        assertEquals(Kind.TEXT, OfficeToHtml.kindOf("text/plain; charset=utf-8", "notes"))
        assertEquals(Kind.HTML, OfficeToHtml.kindOf(null, "page.mhtml"))
        assertNull(OfficeToHtml.kindOf("application/octet-stream", "x.bin"))
        assertNull(OfficeToHtml.kindOf(null, null))
    }

    @Test fun docxHeadingsFormattingAndAlignment() {
        val html = docx(
            p("Big title", "<w:pStyle w:val=\"Title\"/>") +
                p("Section", "<w:pStyle w:val=\"Heading3\"/>") +
                p("Lokal", "<w:pStyle w:val=\"berschrift2\"/>") +
                p("Bold &lt;b&gt;", "<w:jc w:val=\"center\"/>", "<w:b/><w:i/>") +
                p("Not bold", "", "<w:b w:val=\"0\"/>") +
                "<w:p><w:r><w:t>A</w:t><w:tab/><w:t>B</w:t><w:br/><w:t>C</w:t><w:br w:type=\"page\"/><w:t>D</w:t></w:r></w:p>" +
                "<w:p><w:hyperlink r:id=\"rId6\"><w:r><w:t>link</w:t></w:r></w:hyperlink></w:p>",
        )
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<meta charset=\"utf-8\">"))
        assertTrue(html.contains("@page { margin: 16mm; }"))
        assertTrue(html.contains("<h1>Big title</h1>"))
        assertTrue(html.contains("<h3>Section</h3>"))
        assertTrue(html.contains("<h2>Lokal</h2>"))
        assertTrue(html.contains("<p style=\"text-align:center\"><strong><em>Bold &lt;b&gt;</em></strong></p>"))
        assertTrue(html.contains("<p>Not bold</p>"))
        assertTrue(html.contains("<p>A&emsp;B<br>C</p>\n<div class=\"pb\"></div>\n<p>D</p>"))
        assertTrue(html.contains("<span class=\"link\" title=\"https://example.com/?a=1&amp;b=2\">link</span>"))
        assertFalse(html.contains("href="))
        assertFalse(html.contains("<script"))
    }

    @Test fun docxListsTablesAndImages() {
        val num = { id: Int -> "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"$id\"/></w:numPr>" }
        val drawing = "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"952500\" cy=\"476250\"/>" +
            "<a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed=\"rId5\"/></pic:blipFill></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val tc = { t: String, pr: String -> "<w:tc><w:tcPr>$pr</w:tcPr>${p(t)}</w:tc>" }
        val html = docx(
            p("one", num(1)) + p("two", num(1)) + p("dot", num(2)) +
                "<w:tbl><w:tr>${tc("wide", "<w:gridSpan w:val=\"2\"/>")}</w:tr>" +
                "<w:tr>${tc("x", "")}${tc("y", "")}</w:tr></w:tbl>" + drawing,
        )
        assertTrue(html, html.contains("<ol>\n<li>one</li>\n<li>two</li></ol>\n<ul>\n<li>dot</li></ul>"))
        assertTrue(html.contains("<table class=\"grid\">"))
        assertTrue(html.contains("<td colspan=\"2\"><p>wide</p>\n</td>"))
        assertTrue(html.contains("<td><p>x</p>\n</td><td><p>y</p>\n</td>"))
        val b64 = OfficeToHtml.base64(pngBytes)
        assertTrue(html.contains("<img alt=\"\" src=\"data:image/png;base64,$b64\" style=\"width:100px;max-width:100%;height:auto\">"))
        assertEquals(pngBytes.toList(), OfficeToHtml.unbase64(b64).toList())
    }

    @Test fun xlsxSharedStringsNumbersAndWideColumns() {
        val ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
        val bytes = zip(
            "xl/workbook.xml" to "<workbook $ns><sheets><sheet name=\"Empty\" sheetId=\"1\" r:id=\"rId2\"/>" +
                "<sheet name=\"Data &amp; more\" sheetId=\"2\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Target=\"/xl/worksheets/sheet2.xml\"/></Relationships>",
            "xl/sharedStrings.xml" to "<sst $ns><si><t>Name</t></si><si><r><t>Ri</t></r><r><t>ch</t></r></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet $ns><sheetData>" +
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"s\"><v>1</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\"><v>42.0</v></c><c r=\"B2\"><v>3.25</v></c><c r=\"C2\" t=\"b\"><v>1</v></c></row>" +
                "<row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t>&lt;x&gt;</t></is></c><c r=\"AA3\" t=\"str\"><v>far</v></c></row>" +
                "</sheetData></worksheet>",
            "xl/worksheets/sheet2.xml" to "<worksheet $ns><sheetData><row r=\"1\"><c r=\"A1\" s=\"1\"/></row></sheetData></worksheet>",
        )
        val html = convert(Kind.XLSX, bytes)
        assertFalse(html.contains("<h2>Empty</h2>"))
        assertTrue(html.contains("<h2>Data &amp; more</h2>"))
        assertTrue(html.contains("<tr><td>Name</td><td></td><td>Rich</td>"))
        assertTrue(html.contains("<tr><td>42</td><td>3.25</td><td>TRUE</td>"))
        val row3 = html.substringAfter("<tr><td>&lt;x&gt;</td>").substringBefore("</tr>")
        assertEquals(25, Regex("<td></td>").findAll(row3).count())
        assertTrue(row3.endsWith("<td>far</td>"))
    }

    @Test fun xlsxCellCapAddsNote() {
        val ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
        val rows = StringBuilder()
        for (r in 1..2100) {
            rows.append("<row r=\"$r\">")
            for (c in 0 until 10) rows.append("<c r=\"${'A' + c}$r\"><v>$c</v></c>")
            rows.append("</row>")
        }
        val html = convert(
            Kind.XLSX,
            zip(
                "xl/workbook.xml" to "<workbook $ns><sheets><sheet name=\"S\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
                "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
                "xl/worksheets/sheet1.xml" to "<worksheet $ns><sheetData>$rows</sheetData></worksheet>",
            ),
        )
        assertTrue(html.contains("Only the first 20,000 cells are shown"))
        assertTrue(Regex("<td>").findAll(html).count() <= 20_000)
    }

    @Test fun csvQuotingAndSemicolons() {
        val html = convert(Kind.CSV, "name,note\n\"Smith, J\",\"said \"\"hi\"\"\nthen left\"\nx<y,\n")
        assertTrue(html.contains("<thead><tr><th>name</th><th>note</th></tr></thead>"))
        assertTrue(html.contains("<tr><td>Smith, J</td><td>said &quot;hi&quot;\nthen left</td></tr>"))
        assertTrue(html.contains("<tr><td>x&lt;y</td><td></td></tr>"))
        val (rows, _) = OfficeToHtml.parseCsv("a;b;c\n1,5;2;3")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1,5", "2", "3")), rows)
    }

    @Test fun textIsEscapedAndBomsDetected() {
        val html = convert(Kind.TEXT, "if a < b && c > d </pre><script>x</script>")
        assertTrue(html.contains("<pre>if a &lt; b &amp;&amp; c &gt; d &lt;/pre&gt;&lt;script&gt;x&lt;/script&gt;</pre>"))
        assertTrue(html.contains("white-space: pre-wrap"))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Grüße".toByteArray(Charsets.UTF_16LE)
        assertTrue(convert(Kind.TEXT, utf16).contains("<pre>Grüße</pre>"))
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "café".toByteArray()
        assertTrue(convert(Kind.TEXT, utf8).contains("<pre>café</pre>"))
        assertTrue(convert(Kind.TEXT, "My <title>").contains("<title>T</title>"))
        assertTrue(convert(Kind.TEXT, "x".toByteArray(), "A & B").contains("<title>A &amp; B</title>"))
    }

    @Test fun htmlScriptsAndHandlersStripped() {
        val html = convert(
            Kind.HTML,
            "<html><head><title>Mine</title><script src=\"x.js\"></script></head><body onload=\"evil()\">" +
                "<p ONCLICK='x()' class=\"k\">Hi</p><a href=\" javascript:alert(1)\">a</a><a href=\"https://e.org\">b</a>" +
                "<iframe src=\"https://e.org\"></iframe><embed src=\"x.swf\"><object data=\"y\">z</object>" +
                "<img src=x onerror=alert(1)><SCRIPT>bad()</SCRIPT></body></html>",
        )
        assertFalse(html.lowercase().contains("<script"))
        assertFalse(html.lowercase().contains("onload"))
        assertFalse(html.lowercase().contains("onclick"))
        assertFalse(html.contains("onerror"))
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("<iframe"))
        assertFalse(html.contains("<embed"))
        assertFalse(html.contains("<object"))
        assertTrue(html.contains("<p class=\"k\">Hi</p>"))
        assertTrue(html.contains("<a href=\"https://e.org\">b</a>"))
        assertTrue(html.contains("<title>Mine</title>"))
        assertEquals(1, Regex("<title>").findAll(html).count())
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("Content-Security-Policy"))
        val malformed = convert(Kind.HTML, "<p>x</p><img src=x onerror=alert(1)//'>")
        assertFalse(Regex("\\son[a-z]+\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(malformed.substringAfter("<body>")))
    }

    @Test fun mhtmlInlinesImages() {
        val b64 = OfficeToHtml.base64(pngBytes)
        val mhtml = "From: <Saved by Blink>\r\nMIME-Version: 1.0\r\n" +
            "Content-Type: multipart/related;\r\n\ttype=\"text/html\";\r\n\tboundary=\"----B\"\r\n\r\n" +
            "------B\r\nContent-Type: text/html; charset=\"utf-8\"\r\nContent-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Location: https://e.org/page\r\n\r\n" +
            "<html><body><p>Caf=C3=A9 long=\r\n line</p><img src=3D\"https://e.org/pic.png\"><img src=3D\"https://e.org/x.js\">" +
            "<script>bad()</script></body></html>\r\n" +
            "------B\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: https://e.org/pic.png\r\n\r\n" +
            b64.chunked(4).joinToString("\r\n") + "\r\n" +
            "------B\r\nContent-Type: text/javascript\r\nContent-Location: https://e.org/x.js\r\n\r\nalert(1)\r\n------B--\r\n"
        val html = convert(Kind.HTML, mhtml)
        assertTrue(html, html.contains("<p>Café long line</p>"))
        assertTrue(html.contains("<img src=\"data:image/png;base64,$b64\">"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("alert(1)"))
    }

    @Test fun garbageThrows() {
        for (kind in listOf(Kind.DOCX, Kind.XLSX)) {
            try {
                convert(kind, ByteArray(4096) { (it * 31 + 7).toByte() })
                throw AssertionError("expected OfficeConversionException for $kind")
            } catch (_: OfficeConversionException) {
            }
        }
        try {
            convert(Kind.DOCX, zip("word/document.xml" to "<w:document><unclosed"))
            throw AssertionError("expected OfficeConversionException for malformed XML")
        } catch (_: OfficeConversionException) {
        }
        try {
            convert(Kind.HTML, "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"q\"\r\n\r\n--q\r\n\r\nno html\r\n--q--")
            throw AssertionError("expected OfficeConversionException for MHTML without HTML")
        } catch (_: OfficeConversionException) {
        }
    }
    @Test
    fun `notes come in the language the caller passes, escaped, and the default stays English`() {
        val notes = OfficeToHtml.Notes(
            imageNotShown = "img",
            workbookEmpty = "wb",
            fileEmpty = "खाली <फ़ाइल>",
            fileTooLong = "long",
            workbookTruncated = { "wb $it" },
            fileTruncated = { "file $it" }
        )
        val localised = OfficeToHtml.convert(Kind.CSV, ByteArrayInputStream(ByteArray(0)), "T", notes)
        assertTrue(localised.contains("खाली &lt;फ़ाइल&gt;"))
        assertFalse(localised.contains("The file has no data."))
        // The next conversion on the same thread is back to the default.
        assertTrue(convert(Kind.CSV, "").contains("The file has no data."))
    }
}
