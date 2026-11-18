package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.AppStrings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page tools' upgrade (F194): N-up sheets for 2, 4, 6 and 9 pages, page numbers sized to the page, Remove blank
 * pages cutting links to removed pages, and Compress keeping a text PDF as it is and estimating sizes honestly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PageToolsUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        AppStrings.init(context)
        dir = File(context.cacheDir, "page-tools-test").apply { deleteRecursively(); mkdirs() }
    }

    private fun pagesWithText(count: Int, size: PDRectangle = PDRectangle.A4, name: String = "src"): File {
        val file = File(dir, "$name-$count.pdf")
        PDDocument().use { doc ->
            for (i in 1..count) {
                val page = PDPage(size)
                doc.addPage(page)
                PDPageContentStream(doc, page).use {
                    it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(20f, size.height - 40f); it.showText("Original page $i"); it.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    // ---- N-up ----

    @Test
    fun sheetCountRoundsUp() {
        assertEquals(3, PdfNupTools.sheetCount(5, 2))
        assertEquals(1, PdfNupTools.sheetCount(4, 4))
        assertEquals(2, PdfNupTools.sheetCount(7, 6))
        assertEquals(2, PdfNupTools.sheetCount(10, 9))
        assertEquals(0, PdfNupTools.sheetCount(0, 4))
    }

    @Test
    fun theLongerSideOfTheSheetGetsMoreColumnsOrRows() {
        assertEquals(2 to 1, PdfNupTools.grid(2, 842f, 595f))
        assertEquals(1 to 2, PdfNupTools.grid(2, 595f, 842f))
        assertEquals(3 to 2, PdfNupTools.grid(6, 842f, 595f))
        assertEquals(2 to 3, PdfNupTools.grid(6, 595f, 842f))
        assertEquals(3 to 3, PdfNupTools.grid(9, 595f, 842f))
        // Portrait pages: 2 and 6 a sheet turn the paper sideways, 4 and 9 keep it.
        assertEquals(842f to 595f, PdfNupTools.sheetSize(2, 595f, 842f))
        assertEquals(595f to 842f, PdfNupTools.sheetSize(9, 595f, 842f))
    }

    @Test
    fun everyCellFitsOnTheSheetEvenOnSmallPaper() {
        for (perSheet in PdfNupTools.PER_SHEET_CHOICES) for ((w, h) in listOf(595f to 842f, 842f to 595f, 120f to 200f)) {
            val cells = PdfNupTools.cells(perSheet, w, h)
            assertEquals(perSheet, cells.size)
            cells.forEach { c ->
                assertTrue("$perSheet on $w x $h: positive cell", c.width > 0f && c.height > 0f)
                assertTrue("$perSheet on $w x $h: inside", c.x >= 0f && c.y >= 0f && c.x + c.width <= w + 0.01f && c.y + c.height <= h + 0.01f)
            }
            // Reading order: the first cell is top left.
            assertTrue(cells.first().y >= cells.last().y)
            assertTrue(cells.first().x <= cells.last().x)
        }
    }

    @Test
    fun twoLandscapePagesGoOneAboveTheOtherAndBigger() {
        val landscape = PDRectangle(842f, 595f)
        val sheet = PdfNupTools.sheetSize(2, landscape.width, landscape.height)
        assertTrue("portrait sheet", sheet.second > sheet.first)
        val cell = PdfNupTools.cells(2, sheet.first, sheet.second).first()
        val newScale = PdfNupTools.fitScale(cell, landscape.width, landscape.height)
        // The old layout: two columns on a landscape sheet.
        val oldCell = PdfNupTools.cells(2, 842f, 595f).first()
        val oldScale = PdfNupTools.fitScale(oldCell, landscape.width, landscape.height)
        assertTrue("$newScale > $oldScale", newScale > oldScale * 1.3f)
    }

    @Test
    fun tenPagesNineUpMakeTwoSheetsWithEveryPage() = runBlocking {
        val out = File(dir, "nup9.pdf")
        val result = PdfNupTools.nup(context, Uri.fromFile(pagesWithText(10)), 9, out)
        assertEquals(2, result.pageCount)
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            for (i in 1..10) assertTrue("page $i kept", text.contains("Original page $i"))
        }
    }

    @Test
    fun sixUpOfLandscapePagesUsesAPortraitSheet() = runBlocking {
        val out = File(dir, "nup6.pdf")
        PdfNupTools.nup(context, Uri.fromFile(pagesWithText(6, PDRectangle(842f, 595f), "wide")), 6, out)
        PDDocument.load(out).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val box = doc.getPage(0).mediaBox
            assertTrue(box.height > box.width)
        }
    }

    // ---- Page numbers ----

    @Test
    fun pageNumbersGrowWithLargePagesAndShrinkOnSmallOnes() {
        assertEquals(1f, PageNumberGeometry.sizeScale(595f, 842f), 0.001f)
        assertEquals(1f, PageNumberGeometry.sizeScale(842f, 595f), 0.001f)
        assertEquals(5f, PageNumberGeometry.sizeScale(3500f, 4600f), 0.001f)
        assertEquals(2000f / 595f, PageNumberGeometry.sizeScale(2000f, 3000f), 0.001f)
        assertEquals(0.5f, PageNumberGeometry.sizeScale(150f, 400f), 0.001f)
        // A number too wide for the page between its margins is made smaller; one that fits is left alone.
        assertEquals(11f, PageNumberGeometry.fittedFontSize(11f, 4f, 36f, 595f), 0.001f)
        assertEquals((200f - 2 * 18f) / 20f, PageNumberGeometry.fittedFontSize(11f, 20f, 18f, 200f), 0.001f)
    }

    @Test
    fun pageNumberTextAndTheTopLeftCorner() {
        assertEquals("Page 3 of 12", PageNumberGeometry.text(PageNumberFormat.PAGE_N_OF_M, 3, 12))
        assertEquals("3 / 12", PageNumberGeometry.text(PageNumberFormat.N_SLASH_M, 3, 12))
        assertEquals("- 3 -", PageNumberGeometry.text(PageNumberFormat.DASH, 3, 12))
        assertEquals("3", PageNumberGeometry.text(PageNumberFormat.SIMPLE, 3, 12))
        val spot = PageNumberGeometry.place(PageNumberPosition.TOP_LEFT, 40f, 11f, 36f, 612f, 792f, 0)
        assertEquals(36f, spot.x, 0.01f)
        assertEquals(792f - 36f - 11f, spot.y, 0.01f)
    }

    @Test
    fun aNumberOnAPhotoSizedPageIsWrittenLargeEnoughToRead() = runBlocking {
        val big = PDRectangle(3000f, 4000f)
        val out = File(dir, "numbers.pdf")
        PdfToolsEngine.addPageNumbers(context, Uri.fromFile(pagesWithText(2, big, "big")), PageNumbersConfig(), out)
        var size = 0f
        val stripper = object : PDFTextStripper() {
            override fun writeString(string: String, textPositions: List<TextPosition>) {
                if (string.startsWith("Page 1 of 2")) size = textPositions.first().fontSizeInPt
            }
        }
        PDDocument.load(out).use { doc -> stripper.startPage = 1; stripper.endPage = 1; stripper.getText(doc) }
        assertTrue("font $size", size > 40f)
    }

    // ---- Remove blank pages ----

    @Test
    fun aLinkToARemovedPageIsCut() = runBlocking {
        val file = File(dir, "linked.pdf")
        PDDocument().use { doc ->
            val first = PDPage(PDRectangle.A4)
            val blank = PDPage(PDRectangle.A4)
            val last = PDPage(PDRectangle.A4)
            doc.addPage(first); doc.addPage(blank); doc.addPage(last)
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(50f, 50f, 100f, 20f)
                destination = PDPageFitDestination().apply { page = blank }
            }
            first.annotations = listOf(link)
            doc.save(file)
        }
        val out = File(dir, "no-blank.pdf")
        val result = PdfCleanupTools.removePages(context, Uri.fromFile(file), setOf(1), out)
        assertEquals(2, result.pageCount)
        PDDocument.load(out).use { doc ->
            val kept = doc.getPage(0).annotations.filterIsInstance<PDAnnotationLink>().single()
            val destination = kept.destination as PDPageFitDestination
            assertNull("link no longer holds the removed page", destination.page)
        }
    }

    // ---- Compress ----

    @Test
    fun estimateMaths() {
        // 800 of 1000 bytes are pictures; the sample shrank to a quarter: 200 + 200.
        assertEquals(400L, CompressPdfEngine.imagePassBytes(1000L, 800L, 400L, 100L))
        // No pictures: nothing to gain.
        assertEquals(1000L, CompressPdfEngine.imagePassBytes(1000L, 0L, 0L, 0L))
        // A sample that would grow is capped at its own size.
        assertEquals(1000L, CompressPdfEngine.imagePassBytes(1000L, 800L, 400L, 900L))
        assertEquals(CompressPdfEngine.RASTER_FILE_OVERHEAD + 3 * (5000L + CompressPdfEngine.RASTER_PAGE_OVERHEAD), CompressPdfEngine.rasterPassBytes(3, 5000L))
        assertEquals(400L, CompressPdfEngine.scaleByArea(100L, 1000L, 4000L))
    }

    @Test
    fun aTextPdfIsNotTurnedIntoPictures() = runBlocking {
        val input = pagesWithText(3, name = "letter")
        val output = File(context.cacheDir, "exports/compressed_text_${System.nanoTime()}.pdf")
        val result = CompressPdfEngine.compressPdf(context, Uri.fromFile(input), CompressionPreset.MAXIMUM, output)
        assertTrue(result.isSearchablePreserved)
        // Kept as it is (or re-saved a few bytes smaller): never rebuilt from pictures, which would drop the text.
        assertTrue(result.compressedSizeBytes <= result.originalSizeBytes)
        PDDocument.load(output).use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertTrue(PDFTextStripper().getText(doc).contains("Original page 3"))
        }
    }

    @Test
    fun theEstimateForATextPdfPromisesNoSaving() = runBlocking {
        val input = pagesWithText(2, name = "estimate")
        val estimate = CompressPdfEngine.estimate(context, Uri.fromFile(input))
        assertTrue(estimate.hasText)
        assertEquals(2, estimate.pageCount)
        assertEquals(0L, estimate.imageBytes)
        CompressionPreset.entries.forEach { assertEquals(estimate.originalBytes, estimate.bytes.getValue(it)) }
    }
}
