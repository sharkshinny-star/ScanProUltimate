package com.example.scanpro.imaging

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF to PowerPoint's writer: a valid package (every part well-formed, every part PowerPoint requires present, every
 * relationship pointing at a part that exists), one slide per page in order, pictures never stretched, and a file
 * ScanPro's own PowerPoint reader can open again.
 */
class PptxExporterTest {
    // A JPEG only needs its first bytes to be recognised; the picture itself is not decoded here.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)

    private fun pptx(vararg sizes: Pair<Int, Int>): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        PptxExporter.write(sizes.map { (w, h) -> PptxExporter.Slide(jpeg, w, h) }, out)
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; parts[e.name] = zip.readBytes() }
        }
        return parts
    }

    @Test
    fun everyRequiredPartIsThereAndEveryXmlPartIsWellFormed() {
        val parts = pptx(1240 to 1754, 1240 to 1754)
        listOf(
            "[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels",
            "ppt/slideMasters/slideMaster1.xml", "ppt/slideLayouts/slideLayout1.xml", "ppt/theme/theme1.xml",
            "ppt/slides/slide1.xml", "ppt/slides/slide2.xml", "ppt/media/image1.jpeg", "ppt/media/image2.jpeg"
        ).forEach { assertTrue("missing $it", parts.containsKey(it)) }
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, bytes) ->
            builder.parse(ByteArrayInputStream(bytes)) // throws on malformed XML
            assertTrue("$name is empty", bytes.isNotEmpty())
        }
    }

    @Test
    fun everyRelationshipPointsAtAPartThatExists() {
        val parts = pptx(1000 to 1000, 1000 to 1000, 1000 to 1000)
        parts.filterKeys { it.endsWith(".rels") }.forEach { (relsName, bytes) ->
            val baseDir = relsName.substringBefore("_rels/")
            Regex("Target=\"([^\"]+)\"").findAll(String(bytes)).forEach { match ->
                val target = resolve(baseDir, match.groupValues[1])
                assertTrue("$relsName points at missing $target", parts.containsKey(target))
            }
        }
        // Three slides, listed in order in the presentation.
        val presentation = String(parts.getValue("ppt/presentation.xml"))
        assertEquals(3, Regex("<p:sldId ").findAll(presentation).count())
    }

    @Test
    fun slidesTakeTheFirstPagesShapeAndPicturesAreNeverStretched() {
        val (cx, cy) = PptxExporter.slideSize(PptxExporter.Slide(jpeg, 1240, 1754))
        assertEquals(PptxExporter.SLIDE_WIDTH_EMU, cx)
        assertEquals(1754.0 / 1240.0, cy.toDouble() / cx, 0.001)

        // A landscape page on a portrait slide: full width, centred vertically, same shape as the page.
        val (x, y, w, h) = PptxExporter.fit(PptxExporter.Slide(jpeg, 2000, 1000), cx, cy).toList()
        assertEquals(0L, x)
        assertEquals(cx, w)
        assertEquals(2.0, w.toDouble() / h, 0.001)
        assertEquals((cy - h) / 2, y)
    }

    @Test
    fun scanProsOwnPowerPointReaderOpensItAgain() {
        val out = ByteArrayOutputStream()
        PptxExporter.write(listOf(PptxExporter.Slide(jpeg, 100, 141), PptxExporter.Slide(jpeg, 100, 141)), out)

        val html = OfficeToHtml.convert(OfficeToHtml.Kind.PPTX, ByteArrayInputStream(out.toByteArray()), "Deck")

        assertEquals("one picture per slide", 2, Regex("<img src=\"data:image/jpeg;base64,").findAll(html).count())
        assertEquals("a page break between the two slides", 1, Regex("class=\"pb\"").findAll(html).count())
    }

    private fun resolve(baseDir: String, target: String): String {
        val parts = ArrayList<String>()
        for (p in (baseDir + target).split('/')) when (p) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(p)
        }
        return parts.joinToString("/")
    }
}
