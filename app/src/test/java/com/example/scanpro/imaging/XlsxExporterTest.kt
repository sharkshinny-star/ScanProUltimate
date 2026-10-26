package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxExporterTest {
    @Test
    fun `column names run A to Z then AA`() {
        assertEquals("A", XlsxExporter.columnName(0))
        assertEquals("Z", XlsxExporter.columnName(25))
        assertEquals("AA", XlsxExporter.columnName(26))
        assertEquals("AZ", XlsxExporter.columnName(51))
    }

    @Test
    fun `plain numbers become numbers, everything else stays text`() {
        assertEquals("1234.50", XlsxExporter.numberOrNull("1,234.50"))
        assertEquals("-3", XlsxExporter.numberOrNull("-3"))
        assertEquals("0.62", XlsxExporter.numberOrNull("0.62"))
        assertNull(XlsxExporter.numberOrNull("$8.37"))
        assertNull(XlsxExporter.numberOrNull("007"))
        assertNull(XlsxExporter.numberOrNull("03/10/2026"))
        assertNull(XlsxExporter.numberOrNull("1234567890123456"))
    }

    @Test
    fun `cells are written with references, escaped text and numbers`() {
        val xml = XlsxExporter.sheetXml(listOf(listOf("Item & co", "", "4.50")))
        assertTrue(xml.contains("""<c r="A1" t="inlineStr"><is><t xml:space="preserve">Item &amp; co</t></is></c>"""))
        assertFalse(xml.contains("""r="B1""""))
        assertTrue(xml.contains("""<c r="C1"><v>4.50</v></c>"""))
    }

    @Test
    fun `sheet names are made valid and unique`() {
        assertEquals(listOf("Page 1", "Page 1 (2)", "a b", "Sheet 4"), XlsxExporter.uniqueNames(listOf("Page 1", "Page 1", "a/b", "  ")))
    }

    @Test
    fun `the workbook has every part Excel needs`() {
        val out = ByteArrayOutputStream()
        XlsxExporter.write(listOf(XlsxExporter.Sheet("Page 1", listOf(listOf("a"))), XlsxExporter.Sheet("Page 2", emptyList())), out)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        assertEquals(
            listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            names
        )
    }
}
