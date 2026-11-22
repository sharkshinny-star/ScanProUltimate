package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Crop PDF pages and Redact text (F172): the geometry, the decisions and the PDF writing, without PdfRenderer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfCropRedactToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "crop-redact-test").apply { deleteRecursively(); mkdirs() }
    }

    // ---- Crop: display rectangle to page coordinates ----

    private fun box(r: PDRectangle) = listOf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY).map { Math.round(it) }

    @Test
    fun unrotatedPageMapsTopDownToBottomUp() {
        val shape = PdfCropTools.PageShape(PDRectangle(0f, 0f, 300f, 400f), 0)
        // 10 from the left, 20 from the top, to 290 and 380: in page space y runs up from the bottom.
        assertEquals(listOf(10, 20, 290, 380), box(PdfCropTools.displayRectToUserSpace(shape, 10f, 20f, 290f, 380f)))
    }

    @Test
    fun rotatedPagesMapBackOntoTheirOwnAxes() {
        val crop = PDRectangle(0f, 0f, 300f, 400f)
        // Turned 90 clockwise the page shows 400 wide and 300 high; its left edge runs along the display's top.
        assertEquals(listOf(20, 10, 280, 390), box(PdfCropTools.displayRectToUserSpace(PdfCropTools.PageShape(crop, 90), 10f, 20f, 390f, 280f)))
        // Upside down: left-right and top-bottom both flip.
        assertEquals(listOf(10, 20, 290, 380), box(PdfCropTools.displayRectToUserSpace(PdfCropTools.PageShape(crop, 180), 10f, 20f, 290f, 380f)))
        // Turned 270: the display's top is the page's right edge.
        assertEquals(listOf(20, 10, 280, 390), box(PdfCropTools.displayRectToUserSpace(PdfCropTools.PageShape(crop, 270), 10f, 20f, 390f, 280f)))
    }

    @Test
    fun anOffsetCropBoxKeepsItsOrigin() {
        val shape = PdfCropTools.PageShape(PDRectangle(50f, 60f, 300f, 400f), 0)
        assertEquals(listOf(60, 80, 340, 440), box(PdfCropTools.displayRectToUserSpace(shape, 10f, 20f, 290f, 380f)))
    }

    // ---- Crop: finding the content ----

    @Test
    fun autoTrimFindsTheInkAndPadsIt() = runBlocking {
        val shapes = listOf(PdfCropTools.PageShape(PDRectangle(0f, 0f, 300f, 600f), 0))
        val boxes = PdfCropTools.cropBoxes(
            pageCount = 1,
            shape = { shapes[it] },
            draw = { _, target ->
                // The page is 300 x 600 pt drawn at 600 px tall, so 1 pt = 1 px: ink from (60,100) to (240,500).
                Canvas(target).drawRect(60f, 100f, 240f, 500f, Paint().apply { color = Color.BLACK })
            },
            options = PdfCropTools.CropOptions(PdfCropTools.Mode.AUTO, paddingMm = 0f)
        )
        val b = boxes[0]
        assertNotNull(b)
        // In page space the ink's top at 100 from the display's top is 500 from the page's bottom.
        assertEquals(listOf(60, 100, 240, 500), box(b!!))
    }

    @Test
    fun blankAndAlreadyTightPagesAreLeftAlone() = runBlocking {
        val shapes = listOf(PdfCropTools.PageShape(PDRectangle(0f, 0f, 300f, 600f), 0))
        val blank = PdfCropTools.cropBoxes(1, { shapes[it] }, { _, _ -> }, PdfCropTools.CropOptions(PdfCropTools.Mode.AUTO))
        assertNull(blank[0])
        val noMargins = PdfCropTools.cropBoxes(1, { shapes[it] }, { _, _ -> }, PdfCropTools.CropOptions(PdfCropTools.Mode.MARGINS))
        assertNull(noMargins[0])
    }

    @Test
    fun marginsCutFromTheDisplayedSidesAndTooMuchIsRefused() = runBlocking {
        val shapes = listOf(PdfCropTools.PageShape(PDRectangle(0f, 0f, 300f, 600f), 90))
        val mm = PdfCropTools.PT_PER_MM
        val boxes = PdfCropTools.cropBoxes(
            1, { shapes[it] }, { _, _ -> },
            PdfCropTools.CropOptions(PdfCropTools.Mode.MARGINS, topMm = 10f, bottomMm = 20f, leftMm = 5f, rightMm = 15f)
        )
        // Displayed 600 wide and 300 high: left 5 mm and right 15 mm come off the page's y axis, top and bottom off its x axis.
        val b = boxes[0]!!
        assertEquals(Math.round(10 * mm), Math.round(b.lowerLeftX))
        assertEquals(Math.round(300 - 20 * mm), Math.round(b.upperRightX))
        assertEquals(Math.round(5 * mm), Math.round(b.lowerLeftY))
        assertEquals(Math.round(600 - 15 * mm), Math.round(b.upperRightY))
        val tooMuch = PdfCropTools.cropBoxes(
            1, { shapes[it] }, { _, _ -> }, PdfCropTools.CropOptions(PdfCropTools.Mode.MARGINS, leftMm = 100f, rightMm = 110f)
        )
        assertNull(tooMuch[0])
    }

    @Test
    fun aPlanCropsAllPagesOrEachPageItsOwnWay() {
        // F241: two A4 pages (595 x 842 pt) and one turned a quarter; the plan keeps the left half of every page,
        // keeps page 2 whole, and gives page 3 the top half of its own (as shown, which is its turned side).
        val a4 = PDRectangle(0f, 0f, 595f, 842f)
        val shapes = listOf(PdfCropTools.PageShape(a4, 0), PdfCropTools.PageShape(a4, 0), PdfCropTools.PageShape(a4, 90))
        val plan = CropPlan(all = CropFraction(0f, 0f, 0.5f, 1f))
            .withPage(1, CropFraction.WHOLE)
            .withPage(2, CropFraction(0f, 0f, 1f, 0.5f))

        val boxes = PdfCropTools.planBoxes(shapes, plan)

        assertEquals(listOf(0, 0, 298, 842), box(boxes[0]!!))
        assertNull("a page kept whole is left alone", boxes[1])
        // Shown turned, the page is 842 wide and 595 high; its top half is the page's own left half in user space.
        assertEquals(listOf(0, 0, 298, 842), box(boxes[2]!!))
    }

    @Test
    fun aPlanNeverMakesAPageSmallerThanTheToolAccepts() {
        val shapes = listOf(PdfCropTools.PageShape(PDRectangle(0f, 0f, 595f, 842f), 0))
        assertNull(PdfCropTools.planBoxes(shapes, CropPlan(all = CropFraction(0f, 0f, 0.01f, 1f)))[0])
        assertNull(PdfCropTools.planBoxes(shapes, CropPlan())[0])
    }

    @Test
    fun cropBoxesAreWrittenAndTheContentStays() {
        val file = File(dir, "crop.pdf")
        PDDocument().use { doc ->
            repeat(2) { doc.addPage(PDPage(PDRectangle.A4)) }
            PDPageContentStream(doc, doc.getPage(0)).use { it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(100f, 700f); it.showText("Hello"); it.endText() }
            doc.save(file)
        }
        PDDocument.load(file).use { doc ->
            val changed = PdfCropTools.applyCropBoxes(doc, listOf(PDRectangle(50f, 50f, 300f, 400f), null))
            assertEquals(1, changed)
            doc.save(file)
        }
        PDDocument.load(file).use { doc ->
            assertEquals(listOf(50, 50, 350, 450), box(doc.getPage(0).cropBox))
            assertEquals(Math.round(PDRectangle.A4.width), Math.round(doc.getPage(1).cropBox.width))
            assertTrue(PDFTextStripper().getText(doc).contains("Hello"))
        }
    }

    // ---- Redact ----

    private fun twoPageDocument(): File {
        val file = File(dir, "source.pdf")
        PDDocument().use { doc ->
            listOf("Invoice for Alice Smith, account 12345.", "Nothing secret on this page.").forEach { line ->
                val page = PDPage(PDRectangle(400f, 300f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use {
                    it.beginText(); it.setFont(PDType1Font.HELVETICA, 14f); it.newLineAtOffset(40f, 200f); it.showText(line); it.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun matchesAreFoundWhereTheWordsAreDrawn() {
        PDDocument.load(twoPageDocument()).use { doc ->
            val matches = PdfRedactTools.findMatches(doc, listOf("alice smith", "12345"), matchCase = false)
            assertEquals(2, matches.size)
            assertTrue(matches.all { it.page == 0 })
            val name = matches.first { it.rect.width() > 50f }
            // Text starts 40 pt in; "Invoice for " comes first, so the name sits to the right of it and left of the
            // account number, on the line drawn 100 pt from the top of the 300 pt page.
            assertTrue("left ${name.rect.left}", name.rect.left in 100f..130f)
            assertTrue("top ${name.rect.top}", name.rect.top in 80f..100f)
            assertTrue("bottom ${name.rect.bottom}", name.rect.bottom in 100f..110f)
            assertTrue(PdfRedactTools.findMatches(doc, listOf("ALICE"), matchCase = true).isEmpty())
            assertTrue(PdfRedactTools.findMatches(doc, listOf("  ", ""), matchCase = false).isEmpty())
        }
    }

    // ---- Redact by drawn boxes (F174) ----

    @Test
    fun drawnBoxesBecomeMatchesInPagePoints() {
        val boxes = listOf(
            // Drawn upwards and leftwards: still a box. Page 1 is 400 x 300 points.
            PdfRedactTools.DrawnBox(1, 0.8f, 0.9f, 0.2f, 0.1f),
            // Too thin to mean anything, and a page that does not exist: both dropped.
            PdfRedactTools.DrawnBox(0, 0.5f, 0.1f, 0.502f, 0.9f),
            PdfRedactTools.DrawnBox(7, 0.1f, 0.1f, 0.9f, 0.9f),
            // Hanging over the edge: clamped.
            PdfRedactTools.DrawnBox(0, -0.2f, 0.25f, 0.5f, 0.75f)
        )
        val matches = PdfRedactTools.boxesToMatches(boxes, pageCount = 2) { i -> if (i == 0) 200f to 100f else 400f to 300f }
        assertEquals(2, matches.size)
        val turned = matches.first { it.page == 1 }
        assertEquals(0.2f * 400f, turned.rect.left, 0.01f)
        assertEquals(0.1f * 300f, turned.rect.top, 0.01f)
        assertEquals(0.8f * 400f, turned.rect.right, 0.01f)
        assertEquals(0.9f * 300f, turned.rect.bottom, 0.01f)
        val clamped = matches.first { it.page == 0 }
        assertEquals(0f, clamped.rect.left, 0.01f)
        assertEquals(0.5f * 200f, clamped.rect.right, 0.01f)
    }

    @Test
    fun drawnBoxesBlackOutTheirPages() = runBlocking {
        val out = File(dir, "boxes.pdf")
        PDDocument.load(twoPageDocument()).use { doc ->
            val matches = PdfRedactTools.boxesToMatches(
                listOf(PdfRedactTools.DrawnBox(0, 0.05f, 0.2f, 0.95f, 0.5f)),
                pageCount = 2
            ) { 400f to 300f }
            val pages = PdfRedactTools.writeRedacted(
                source = doc,
                matches = matches,
                pageSize = { 400f to 300f },
                draw = { _, target -> target.eraseColor(Color.WHITE) },
                outputFile = out
            )
            assertEquals(1, pages)
        }
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val stripper = PDFTextStripper()
            stripper.startPage = 1; stripper.endPage = 1
            assertFalse(stripper.getText(doc).contains("Alice"))
            stripper.startPage = 2; stripper.endPage = 2
            assertTrue(stripper.getText(doc).contains("Nothing secret"))
        }
    }

    @Test
    fun redactedPagesLoseTheirTextAndOthersKeepIt() = runBlocking {
        val out = File(dir, "redacted.pdf")
        PDDocument.load(twoPageDocument()).use { doc ->
            val matches = PdfRedactTools.findMatches(doc, listOf("12345"), matchCase = false)
            val pages = PdfRedactTools.writeRedacted(
                source = doc,
                matches = matches,
                pageSize = { 400f to 300f },
                draw = { _, target -> target.eraseColor(Color.WHITE) },
                outputFile = out
            )
            assertEquals(1, pages)
        }
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val stripper = PDFTextStripper()
            stripper.startPage = 1; stripper.endPage = 1
            assertFalse("the number must be gone", stripper.getText(doc).contains("12345"))
            assertFalse("the whole page is a picture now", stripper.getText(doc).contains("Alice"))
            stripper.startPage = 2; stripper.endPage = 2
            assertTrue(stripper.getText(doc).contains("Nothing secret"))
            assertEquals(400, Math.round(doc.getPage(0).mediaBox.width))
        }
    }
}
