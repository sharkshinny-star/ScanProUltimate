package com.example.scanpro.imaging

import com.example.scanpro.imaging.OfficeToHtml.Kind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PowerPoint to PDF (the HTML step): slides come out in the presentation's own order, one page each, with titles as
 * headings, body text as paragraphs and pictures embedded. The test presentation is built by hand so every part is known.
 */
class PptxToHtmlTest {

    @Test
    fun pptxFilesAreRecognisedByNameAndType() {
        assertEquals(Kind.PPTX, OfficeToHtml.kindOf(null, "Deck.pptx"))
        assertEquals(Kind.PPTX, OfficeToHtml.kindOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "deck"))
    }

    @Test
    fun slidesFollowThePresentationOrderNotTheFileNames() {
        // slide2.xml is listed first: a slide moved to the front keeps its old file name.
        val html = convert(listOf("rId3", "rId2"))

        val second = html.indexOf("Second slide title")
        val first = html.indexOf("First slide title")
        assertTrue("both titles are present", second >= 0 && first >= 0)
        assertTrue("the slide listed first comes first", second < first)
        assertEquals("one page break between two slides", 1, Regex("class=\"pb\"").findAll(html).count())
    }

    @Test
    fun titlesBodyTextLineBreaksAndPicturesComeThrough() {
        val html = convert(listOf("rId2", "rId3"))

        assertTrue(html.contains("<h2 class=\"slide-title\">First slide title</h2>"))
        assertTrue("body text kept, line break kept", html.contains("<p>Point one<br>continued</p>"))
        assertTrue("text inside a group is found", html.contains("Grouped note"))
        assertTrue("the picture is embedded", html.contains("<img src=\"data:image/png;base64,"))
        assertTrue("special characters are escaped", html.contains("Q&amp;A"))
    }

    private fun convert(order: List<String>): String = OfficeToHtml.convert(Kind.PPTX, ByteArrayInputStream(pptx(order)), "Deck")

    private fun pptx(order: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun part(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            fun bytes(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            part(
                "ppt/presentation.xml",
                "<p:presentation xmlns:p=\"$P\" xmlns:r=\"$R\"><p:sldIdLst>" +
                    order.mapIndexed { i, rel -> "<p:sldId id=\"${256 + i}\" r:id=\"$rel\"/>" }.joinToString("") +
                    "</p:sldIdLst></p:presentation>"
            )
            part(
                "ppt/_rels/presentation.xml.rels",
                "<Relationships xmlns=\"$PKG\"><Relationship Id=\"rId2\" Type=\"slide\" Target=\"slides/slide1.xml\"/>" +
                    "<Relationship Id=\"rId3\" Type=\"slide\" Target=\"slides/slide2.xml\"/></Relationships>"
            )
            part(
                "ppt/slides/slide1.xml",
                slide(
                    title = "First slide title",
                    body = "<a:p><a:r><a:t>Point one</a:t></a:r><a:br/><a:r><a:t>continued</a:t></a:r></a:p><a:p><a:r><a:t>Q&amp;A</a:t></a:r></a:p>",
                    extra = "<p:grpSp><p:sp><p:spPr><a:xfrm><a:off x=\"10\" y=\"900\"/></a:xfrm></p:spPr><p:txBody><a:p><a:r><a:t>Grouped note</a:t></a:r></a:p></p:txBody></p:sp></p:grpSp>" +
                        "<p:pic><p:blipFill><a:blip r:embed=\"rIdImg\"/></p:blipFill><p:spPr><a:xfrm><a:off x=\"10\" y=\"1200\"/></a:xfrm></p:spPr></p:pic>"
                )
            )
            part("ppt/slides/_rels/slide1.xml.rels", "<Relationships xmlns=\"$PKG\"><Relationship Id=\"rIdImg\" Type=\"image\" Target=\"../media/image1.png\"/></Relationships>")
            part("ppt/slides/slide2.xml", slide(title = "Second slide title", body = "<a:p><a:r><a:t>Only text</a:t></a:r></a:p>", extra = ""))
            // The 8-byte PNG signature is enough for the picture to be recognised as a PNG.
            bytes("ppt/media/image1.png", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        }
        return out.toByteArray()
    }

    private fun slide(title: String, body: String, extra: String): String =
        "<p:sld xmlns:p=\"$P\" xmlns:a=\"$A\" xmlns:r=\"$R\"><p:cSld><p:spTree>" +
            "<p:sp><p:nvSpPr><p:nvPr><p:ph type=\"title\"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"10\" y=\"100\"/></a:xfrm></p:spPr>" +
            "<p:txBody><a:p><a:r><a:t>$title</a:t></a:r></a:p></p:txBody></p:sp>" +
            "<p:sp><p:spPr><a:xfrm><a:off x=\"10\" y=\"500\"/></a:xfrm></p:spPr><p:txBody>$body</p:txBody></p:sp>" +
            extra +
            "</p:spTree></p:cSld></p:sld>"

    private companion object {
        const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
    }
}
