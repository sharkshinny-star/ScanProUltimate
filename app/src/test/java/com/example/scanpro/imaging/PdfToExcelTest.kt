package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PDF to Excel: a table in a PDF's text layer comes out as rows and columns of cells, one sheet per page. The table is
 * drawn at fixed positions, so the expected cells are known exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfToExcelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "pdf-to-excel-test").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun aTableInTheTextLayerBecomesRowsAndColumns() {
        val pdf = tablePdf(
            listOf(
                listOf("Item", "Qty", "Price"),
                listOf("Apples", "12", "4.50"),
                listOf("Pears", "3", "9.00")
            )
        )
        val output = File(dir, "out.xlsx")

        val pages = runBlocking {
            PDDocument.load(pdf).use { document ->
                PdfToTextEngine.readPagesDetailed(document, { fail("a page with text must not be sent to OCR"); throw AssertionError() }, withWords = true)
            }
        }
        PdfToTextEngine.write("table", pages, TextExportFormat.EXCEL, output)

        val rows = sheetRows(output, 1)
        assertEquals(3, rows.size)
        assertEquals(listOf("Item", "Qty", "Price"), rows[0])
        assertEquals("Apples", rows[1][0])
        assertEquals(12.0, rows[1][1].toDouble(), 0.0001)
        assertEquals(4.5, rows[1][2].toDouble(), 0.0001)
        assertEquals("Pears", rows[2][0])
        assertEquals(3.0, rows[2][1].toDouble(), 0.0001)
    }

    @Test
    fun aPageWithoutWordPositionsFallsBackToOneRowPerLine() {
        val rows = PdfToTextEngine.sheetRows(PdfTextPage("First line\n\nSecond line", PageTextSource.OCR))

        assertEquals(listOf(listOf("First line"), listOf("Second line")), rows)
    }

    @Test
    fun anEmptyPageStillGetsASheetWithANote() {
        val rows = PdfToTextEngine.sheetRows(PdfTextPage("", PageTextSource.NONE))

        assertEquals(1, rows.size)
        assertTrue(rows[0][0].isNotBlank())
    }

    /** One page with [table] drawn row by row, each column at a fixed x, so the columns are clearly apart. */
    private fun tablePdf(table: List<List<String>>): File {
        val file = File(dir, "table.pdf")
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                table.forEachIndexed { r, row ->
                    row.forEachIndexed { c, cell ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 12f)
                        stream.newLineAtOffset(60f + c * 160f, 700f - r * 24f)
                        stream.showText(cell)
                        stream.endText()
                    }
                }
            }
            document.save(file)
        }
        return file
    }

    /** The cell values of sheet [index], row by row, as text (inline strings and numbers alike). */
    private fun sheetRows(xlsx: File, index: Int): List<List<String>> = ZipFile(xlsx).use { zip ->
        val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet$index.xml")).bufferedReader().readText()
        Regex("<row[^>]*>(.*?)</row>").findAll(xml).map { row ->
            Regex("<c[^>]*>(.*?)</c>").findAll(row.groupValues[1]).map { cell ->
                Regex("<(?:t|v)[^>]*>(.*?)</(?:t|v)>").find(cell.groupValues[1])?.groupValues?.get(1).orEmpty()
            }.toList()
        }.toList()
    }
}
