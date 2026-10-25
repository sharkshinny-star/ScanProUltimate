package com.example.scanpro.imaging

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxExporterTest {
    private fun parts(title: String, pages: List<String>): Map<String, String> {
        val bytes = ByteArrayOutputStream().also { DocxExporter.write(title, pages, it) }.toByteArray()
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return result
    }

    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

    @Test
    fun packageHasEveryRequiredPartAndAllAreWellFormed() {
        val parts = parts("Invoice <A&B>", listOf("Line 1\nTotal: 5 < 6 & \"ok\"", "Page two"))
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "word/_rels/document.xml.rels", "word/styles.xml", "word/document.xml"),
            parts.keys
        )
        parts.values.forEach { parse(it) }
    }

    @Test
    fun pagesAreSeparatedByPageBreaksAndLinesBecomeParagraphs() {
        val doc = parse(DocxExporter.documentXml("T", listOf("a\nb", "c", "")))
        val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val breaks = doc.getElementsByTagNameNS(ns, "br")
        assertEquals(2, breaks.length)
        val texts = doc.getElementsByTagNameNS(ns, "t")
        val all = (0 until texts.length).map { texts.item(it).textContent }
        assertEquals(listOf("T", "a", "b", "c", "[No text recognised on page 3]"), all)
    }

    @Test
    fun controlCharactersAreDroppedSoTheXmlStaysValid() {
        val escaped = DocxExporter.escape("ok\u0001\u0008\tdone￿")
        assertEquals("ok done", escaped)
        assertTrue(DocxExporter.escape("<&>").contains("&lt;&amp;&gt;"))
    }
}
