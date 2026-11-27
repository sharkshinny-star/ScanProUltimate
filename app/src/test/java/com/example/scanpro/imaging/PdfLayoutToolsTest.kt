package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** N-up and Header and footer (F173): the geometry and the written PDFs, without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfLayoutToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "layout-test").apply { deleteRecursively(); mkdirs() }
    }

    // ---- N-up geometry ----

    private fun apply(m: FloatArray, x: Float, y: Float) = (m[0] * x + m[2] * y + m[4]) to (m[1] * x + m[3] * y + m[5])

    @Test
    fun seenMatrixMapsTheBoxOntoTheUprightSeenPicture() {
        val box = PDRectangle(10f, 20f, 300f, 400f)
        for (rotation in listOf(0, 90, 180, 270)) {
            val m = PdfNupTools.seenMatrix(box, rotation)
            val (seenW, seenH) = PdfNupTools.seenSize(box, rotation)
            val corners = listOf(
                apply(m, 10f, 20f), apply(m, 310f, 20f), apply(m, 310f, 420f), apply(m, 10f, 420f)
            )
            val xs = corners.map { it.first }
            val ys = corners.map { it.second }
            assertEquals("rotation $rotation min x", 0f, xs.min(), 0.01f)
            assertEquals("rotation $rotation min y", 0f, ys.min(), 0.01f)
            assertEquals("rotation $rotation width", seenW, xs.max(), 0.01f)
            assertEquals("rotation $rotation height", seenH, ys.max(), 0.01f)
        }
        // 90 clockwise: the page's lower-left corner is seen at the top left, so it maps to (0, seenH).
        val m90 = PdfNupTools.seenMatrix(box, 90)
        val (_, seenH90) = PdfNupTools.seenSize(box, 90)
        assertEquals(0f to seenH90, apply(m90, 10f, 20f))
    }

    @Test
    fun cellsReadLeftToRightThenDown() {
        val two = PdfNupTools.cells(2, 800f, 600f)
        assertEquals(2, two.size)
        assertTrue(two[0].x < two[1].x)
        assertEquals(two[0].y, two[1].y, 0.01f)
        val four = PdfNupTools.cells(4, 600f, 800f)
        assertEquals(4, four.size)
        assertTrue(four[0].x < four[1].x)
        assertTrue("first row sits above the second", four[0].y > four[2].y)
        assertEquals(four[0].x, four[2].x, 0.01f)
        // Every cell fits on the sheet.
        four.forEach { c -> assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.width <= 600f && c.y + c.height <= 800f) }
    }

    // ---- N-up on a real document ----

    private fun pagesWithText(count: Int, size: PDRectangle = PDRectangle.A4): File {
        val file = File(dir, "src-$count.pdf")
        PDDocument().use { doc ->
            for (i in 1..count) {
                val page = PDPage(size)
                doc.addPage(page)
                PDPageContentStream(doc, page).use {
                    it.beginText(); it.setFont(PDType1Font.HELVETICA, 24f); it.newLineAtOffset(60f, size.height - 80f); it.showText("Original page $i"); it.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun fivePagesTwoUpMakeThreeLandscapeSheetsWithEveryPage() = runBlocking {
        val out = File(dir, "nup2.pdf")
        val result = PdfNupTools.nup(context, android.net.Uri.fromFile(pagesWithText(5)), 2, out)
        assertEquals(3, result.pageCount)
        PDDocument.load(out).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val sheet = doc.getPage(0).mediaBox
            assertTrue("landscape sheet", sheet.width > sheet.height)
            assertEquals(PDRectangle.A4.height, sheet.width, 0.5f)
            val text = PDFTextStripper().getText(doc)
            for (i in 1..5) assertTrue("page $i still readable", text.contains("Original page $i"))
            // Page 1 sits in the left half of sheet 1, page 2 in the right half.
            val positions = HashMap<String, Float>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(string: String, textPositions: List<TextPosition>) {
                    if (string.startsWith("Original page")) positions[string.trim()] = textPositions.first().xDirAdj
                }
            }
            stripper.startPage = 1; stripper.endPage = 1
            stripper.getText(doc)
            assertTrue(positions["Original page 1"]!! < sheet.width / 2)
            assertTrue(positions["Original page 2"]!! > sheet.width / 2)
        }
    }

    @Test
    fun fourUpKeepsThePaperAndFillsAGrid() = runBlocking {
        val out = File(dir, "nup4.pdf")
        val result = PdfNupTools.nup(context, android.net.Uri.fromFile(pagesWithText(5)), 4, out)
        assertEquals(2, result.pageCount)
        PDDocument.load(out).use { doc ->
            val sheet = doc.getPage(0).mediaBox
            assertEquals(PDRectangle.A4.width, sheet.width, 0.5f)
            assertEquals(PDRectangle.A4.height, sheet.height, 0.5f)
            val text = PDFTextStripper().getText(doc)
            for (i in 1..5) assertTrue(text.contains("Original page $i"))
        }
    }

    @Test
    fun aRotatedPageIsPlacedAsItIsSeen() = runBlocking {
        // Two A4 pages, the second stored sideways with /Rotate 90: both must land upright in their cells.
        val file = File(dir, "rot.pdf")
        PDDocument().use { doc ->
            val first = PDPage(PDRectangle.A4)
            doc.addPage(first)
            PDPageContentStream(doc, first).use { it.beginText(); it.setFont(PDType1Font.HELVETICA, 24f); it.newLineAtOffset(60f, 700f); it.showText("Plain"); it.endText() }
            val second = PDPage(PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width))
            second.rotation = 90
            doc.addPage(second)
            PDPageContentStream(doc, second).use { it.beginText(); it.setFont(PDType1Font.HELVETICA, 24f); it.newLineAtOffset(60f, 60f); it.showText("Turned"); it.endText() }
            doc.save(file)
        }
        val out = File(dir, "nup-rot.pdf")
        PdfNupTools.nup(context, android.net.Uri.fromFile(file), 2, out)
        PDDocument.load(out).use { doc ->
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("Plain"))
            assertTrue(text.contains("Turned"))
        }
    }

    @Test
    fun aSinglePageIsRefused(): Unit = runBlocking {
        try {
            PdfNupTools.nup(context, android.net.Uri.fromFile(pagesWithText(1)), 2, File(dir, "no.pdf"))
            assertTrue("should have refused", false)
        } catch (expected: com.example.scanpro.core.text.UserFacingException) {
        }
    }

    // ---- Header and footer ----

    @Test
    fun placeholdersAndEdges() {
        assertEquals("Draft 2 of 7", PdfHeaderFooterTools.substitute("Draft {page} of {total}", 2, 7))
        val header = PdfHeaderFooterTools.edgeStamp(true, PdfHeaderFooterTools.Align.LEFT, 100f, 10f, 24f, 595f, 842f, 0)
        assertEquals(24f, header.x, 0.01f)
        assertEquals(842f - 34f, header.y, 0.01f)
        val footer = PdfHeaderFooterTools.edgeStamp(false, PdfHeaderFooterTools.Align.RIGHT, 100f, 10f, 24f, 595f, 842f, 0)
        assertEquals(595f - 124f, footer.x, 0.01f)
        assertEquals(24f, footer.y, 0.01f)
        // On a page stored sideways (842 wide, seen 595 x 842), the seen bottom edge is the stored page's
        // x = width side, so the footer's page-space x is width minus the margin, centred along y.
        val turned = PdfHeaderFooterTools.edgeStamp(false, PdfHeaderFooterTools.Align.CENTER, 100f, 10f, 24f, 842f, 595f, 90)
        assertEquals(Math.toRadians(90.0), turned.angleRad, 0.001)
        assertEquals(842f - 24f, turned.x, 0.01f)
        assertEquals((595f - 100f) / 2f, turned.y, 0.01f)
    }

    @Test
    fun headerAndFooterLandOnTheRightPagesAndEdges() = runBlocking {
        val out = File(dir, "hf.pdf")
        PdfHeaderFooterTools.headerFooter(
            context,
            android.net.Uri.fromFile(pagesWithText(3)),
            PdfHeaderFooterTools.HeaderFooterConfig(
                headerText = "ACME report",
                footerText = "Sheet {page} of {total}",
                align = PdfHeaderFooterTools.Align.CENTER,
                skipFirstPage = true
            ),
            out
        )
        PDDocument.load(out).use { doc ->
            val perPage = (1..3).map { page ->
                val stripper = PDFTextStripper()
                stripper.startPage = page; stripper.endPage = page
                stripper.getText(doc)
            }
            assertFalse(perPage[0].contains("ACME report"))
            assertTrue(perPage[1].contains("ACME report"))
            assertTrue(perPage[1].contains("Sheet 2 of 3"))
            assertTrue(perPage[2].contains("Sheet 3 of 3"))
            assertTrue("the old content stays", perPage[1].contains("Original page 2"))
            // The header sits above the body text, the footer below it.
            var headerY = 0f
            var bodyY = 0f
            var footerY = 0f
            val stripper = object : PDFTextStripper() {
                override fun writeString(string: String, textPositions: List<TextPosition>) {
                    val y = textPositions.first().yDirAdj
                    when {
                        string.contains("ACME") -> headerY = y
                        string.contains("Original") -> bodyY = y
                        string.contains("Sheet") -> footerY = y
                    }
                }
            }
            stripper.startPage = 2; stripper.endPage = 2
            stripper.getText(doc)
            assertTrue("header $headerY above body $bodyY", headerY < bodyY)
            assertTrue("footer $footerY below body $bodyY", footerY > bodyY)
        }
    }

    @Test
    fun textHelveticaCannotWriteGoesOnAsAPicture() = runBlocking {
        val out = File(dir, "hf-hindi.pdf")
        PdfHeaderFooterTools.headerFooter(
            context,
            android.net.Uri.fromFile(pagesWithText(1)),
            PdfHeaderFooterTools.HeaderFooterConfig(headerText = "रिपोर्ट"),
            out
        )
        PDDocument.load(out).use { doc ->
            // The words are a picture now, so they are not in the text, but an image object is on the page.
            assertFalse(PDFTextStripper().getText(doc).contains("रिपोर्ट"))
            val resources = doc.getPage(0).resources
            assertTrue(resources.xObjectNames.any())
        }
    }
}
