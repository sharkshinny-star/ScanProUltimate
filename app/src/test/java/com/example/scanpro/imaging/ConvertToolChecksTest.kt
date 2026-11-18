package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.data.PdfPageSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The checks behind the convert and info tools (F211): the sizes and counts their pages promise before running, and
 * the engine fixes found with them. PdfRenderer does not run under Robolectric, so the rules are tested directly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ConvertToolChecksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "convert-checks").apply { deleteRecursively(); mkdirs() }
    }

    // ---- PDF to images ----

    @Test
    fun pixelSizeIsPagePointsOver72TimesTheDpi() {
        // A4 is 595 × 842 points: 8.26 × 11.69 inches.
        assertEquals(1239 to 1754, PdfToImagesMath.pixelSize(595, 842, PdfToImagesMath.scaleFor(150)))
        assertEquals(2479 to 3508, PdfToImagesMath.pixelSize(595, 842, PdfToImagesMath.scaleFor(300)))
        assertEquals(793 to 1122, PdfToImagesMath.pixelSize(595, 842, PdfToImagesMath.scaleFor(96)))
        assertFalse(PdfToImagesMath.isCapped(595, 842, PdfToImagesMath.scaleFor(300)))
    }

    @Test
    fun aPosterSizedPageIsCappedAtTheLongestSideAndSaysSo() {
        // A0 at 300 dpi would be 9933 × 14043 px (560 MB of pixels).
        val (w, h) = PdfToImagesMath.pixelSize(2384, 3370, PdfToImagesMath.scaleFor(300))
        assertEquals(PdfToImagesMath.MAX_SIDE, h)
        assertEquals(2829, w)
        assertTrue(PdfToImagesMath.isCapped(2384, 3370, PdfToImagesMath.scaleFor(300)))
    }

    @Test
    fun chosenPagesAreInsideTheDocumentOnceEachAndInOrder() {
        assertEquals(listOf(2, 5), PdfToImagesMath.chosenPages(listOf(5, 2, 2, 99, 0), 10))
        assertEquals((1..4).toList(), PdfToImagesMath.chosenPages(null, 4))
    }

    @Test
    fun fileNamesSortInPageOrderPastPage999() {
        assertEquals("Report page 007.jpg", PdfToImagesMath.fileName("Report", 7, 12, "jpg"))
        assertEquals("Report page 0007.png", PdfToImagesMath.fileName("Report", 7, 1200, "png"))
        val names = listOf(101, 1000).map { PdfToImagesMath.fileName("R", it, 1000, "jpg") }
        assertEquals(names, names.sorted())
    }

    @Test
    fun bytesAreScaledFromTheSampleByPixels() {
        assertEquals(400_000L, PdfToImagesMath.scaleBytes(100_000, 1_000_000, 4_000_000))
        assertEquals(0L, PdfToImagesMath.scaleBytes(100_000, 0, 4_000_000))
    }

    // ---- Text layer, OCR ----

    @Test
    fun aPageNeedsOcrWithoutTextOrWithOnlyAStrayLineOverAPicture() {
        assertTrue(PageTextRules.needsOcr(0, hasImage = false))
        assertTrue(PageTextRules.needsOcr(12, hasImage = true))
        assertFalse(PageTextRules.needsOcr(12, hasImage = false))
        assertFalse(PageTextRules.needsOcr(400, hasImage = true))
    }

    @Test
    fun theSurveyCountsTextPagesAndPagesToReadByTheEnginesRule() {
        PDDocument().use { doc ->
            write(doc, page(doc), "A page of ordinary text, long enough to be the page's own words.")
            page(doc)
            // A scan with a scanning app's stamp: a picture and one short line.
            val scan = page(doc)
            val picture = LosslessFactory.createFromImage(doc, solid(40, 40, Color.GRAY))
            PDPageContentStream(doc, scan).use { it.drawImage(picture, 50f, 50f, 400f, 600f) }
            write(doc, scan, "Scanned")

            val chars = PageTextRules.visibleCharsPerPage(doc)
            assertEquals(3, chars.size)
            assertTrue(chars[0] > PageTextRules.SPARSE_TEXT_CHARS)
            assertEquals(0, chars[1])
            assertEquals(7, chars[2])

            val survey = PdfTextSurvey.of(doc)
            assertEquals(3, survey.pageCount)
            assertEquals(1, survey.textPages)
            assertEquals(2, survey.ocrPages)
        }
    }

    @Test
    fun aStrayLineOverAPictureIsReadWithOcrForPdfToText() = runBlocking {
        val file = File(dir, "stamped.pdf")
        PDDocument().use { doc ->
            val scan = page(doc)
            val picture = LosslessFactory.createFromImage(doc, solid(40, 40, Color.GRAY))
            PDPageContentStream(doc, scan).use { it.drawImage(picture, 50f, 50f, 400f, 600f) }
            write(doc, scan, "Scanned")
            doc.save(file)
        }
        PDDocument.load(file).use { doc ->
            val pages = PdfToTextEngine.readPages(doc, { "The words printed in the picture, read by OCR." })
            assertEquals(PageTextSource.OCR, pages.single().source)
            assertTrue(pages.single().text.startsWith("The words printed"))
        }
        // When OCR reads less than the layer holds, the layer stays.
        PDDocument.load(file).use { doc ->
            val pages = PdfToTextEngine.readPages(doc, { "" })
            assertEquals(PageTextSource.TEXT_LAYER, pages.single().source)
            assertEquals("Scanned", pages.single().text)
        }
    }

    // ---- Extract images ----

    @Test
    fun theImageSurveyCountsDistinctPicturesFormatsAndTinyOnes() {
        PDDocument().use { doc ->
            val photo = JPEGFactory.createFromImage(doc, solid(40, 30, Color.RED))
            val drawing = LosslessFactory.createFromImage(doc, solid(20, 20, Color.BLUE))
            val bullet = LosslessFactory.createFromImage(doc, solid(4, 4, Color.BLACK))
            PDPageContentStream(doc, page(doc)).use { it.drawImage(photo, 10f, 10f) }
            PDPageContentStream(doc, page(doc)).use {
                it.drawImage(photo, 10f, 10f)
                it.drawImage(drawing, 100f, 10f)
                it.drawImage(bullet, 200f, 10f)
            }
            page(doc)
            val file = File(dir, "pictures.pdf")
            doc.save(file)
            // Read back from the file, as the tool page does: every stream then carries its stored length.
            val survey = PDDocument.load(file).use { PdfImageSurvey.of(it) }
            assertEquals(2, survey.pictures)
            assertEquals(1, survey.jpeg)
            assertEquals(1, survey.tiny)
            assertEquals(2, survey.pagesWithPictures)
            assertTrue(survey.storedBytes > 0)
        }
    }

    @Test
    fun aJpegWithASeeThroughMaskIsNotCopiedAsStoredButAnOpaqueMaskIs() {
        PDDocument().use { doc ->
            val photo = JPEGFactory.createFromImage(doc, solid(40, 30, Color.RED))
            assertFalse(PdfImageSurvey.hasTransparency(photo))
            assertEquals(listOf(COSName.DCT_DECODE.name), PdfImageSurvey.filterNames(photo))
            // An all-white (opaque) soft mask: still copied as stored.
            photo.cosObject.setItem(COSName.SMASK, LosslessFactory.createFromImage(doc, solid(40, 30, Color.WHITE)).cosObject)
            assertTrue(PdfImageSurvey.hasMask(photo))
            assertFalse(PdfImageSurvey.hasTransparency(photo))
            // A black (see-through) one: decoded with its mask instead.
            photo.cosObject.setItem(COSName.SMASK, LosslessFactory.createFromImage(doc, solid(40, 30, Color.BLACK)).cosObject)
            assertTrue(PdfImageSurvey.hasTransparency(photo))
        }
    }

    // ---- Repair ----

    @Test
    fun repairKeepsTheRewrittenFileOnlyWhenNoPageWasLost() {
        assertEquals(RepairDecision.Pick.REWRITTEN, RepairDecision.pick(rewritten = 12, viewerPages = 12, rewrittenOpens = true))
        // The viewer could not judge (it could not open the damaged file): the rewrite stands.
        assertEquals(RepairDecision.Pick.REWRITTEN, RepairDecision.pick(rewritten = 12, viewerPages = null, rewrittenOpens = null))
        // The parser found 3 of the 12 pages the viewer still draws: rebuild as pictures rather than lose nine.
        assertEquals(RepairDecision.Pick.PICTURES, RepairDecision.pick(rewritten = 3, viewerPages = 12, rewrittenOpens = true))
        // The rewritten file does not open in the viewer.
        assertEquals(RepairDecision.Pick.PICTURES, RepairDecision.pick(rewritten = 12, viewerPages = 12, rewrittenOpens = false))
        assertEquals(RepairDecision.Pick.PICTURES, RepairDecision.pick(rewritten = 0, viewerPages = 5, rewrittenOpens = null))
        assertEquals(RepairDecision.Pick.NOTHING, RepairDecision.pick(rewritten = 0, viewerPages = null, rewrittenOpens = null))
        assertEquals(RepairDecision.Pick.NOTHING, RepairDecision.pick(rewritten = 0, viewerPages = 0, rewrittenOpens = null))
    }

    // ---- Office to PDF ----

    @Test
    fun aPresentationsSlidesAndPicturesAreCountedFromItsFileList() {
        val bytes = zip(
            "ppt/presentation.xml" to "<p:presentation/>",
            "ppt/slides/slide1.xml" to "<p:sld/>",
            "ppt/slides/slide2.xml" to "<p:sld/>",
            "ppt/slides/_rels/slide1.xml.rels" to "<Relationships/>",
            "ppt/media/image1.png" to "png",
        )
        val survey = OfficeSurvey.of(OfficeToHtml.Kind.PPTX, ByteArrayInputStream(bytes))
        assertNull(survey.problem)
        assertEquals(2, survey.slides)
        assertEquals(1, survey.pictures)
    }

    @Test
    fun hiddenSheetsAreCountedApart() {
        val workbook = """<workbook xmlns="x"><sheets><sheet name="A" sheetId="1"/><sheet name="B" sheetId="2" state="hidden"/><sheet name="C" sheetId="3"/></sheets></workbook>"""
        val survey = OfficeSurvey.of(OfficeToHtml.Kind.XLSX, ByteArrayInputStream(zip("xl/workbook.xml" to workbook)))
        assertEquals(2, survey.sheets)
        assertEquals(1, survey.hiddenSheets)
    }

    @Test
    fun anOldBinaryOrLockedOfficeFileIsNamedBeforeConverting() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0)
        assertEquals(OfficeSurvey.Problem.LEGACY_OR_LOCKED, OfficeSurvey.of(OfficeToHtml.Kind.DOCX, ByteArrayInputStream(ole)).problem)
        assertEquals(OfficeSurvey.Problem.LEGACY_OR_LOCKED, OfficeSurvey.of(null, ByteArrayInputStream(ole)).problem)
        assertEquals(OfficeSurvey.Problem.UNREADABLE, OfficeSurvey.of(OfficeToHtml.Kind.DOCX, ByteArrayInputStream(ByteArray(0))).problem)
        assertEquals(OfficeSurvey.Problem.UNREADABLE, OfficeSurvey.of(OfficeToHtml.Kind.DOCX, ByteArrayInputStream("not a zip".toByteArray())).problem)
        assertEquals(OfficeSurvey.Problem.UNSUPPORTED, OfficeSurvey.of(null, ByteArrayInputStream("hello".toByteArray())).problem)
    }

    @Test
    fun textLinesAreCountedWithOrWithoutAFinalNewline() {
        assertEquals(3, OfficeSurvey.of(OfficeToHtml.Kind.TEXT, ByteArrayInputStream("a\nb\nc".toByteArray())).lines)
        assertEquals(2, OfficeSurvey.of(OfficeToHtml.Kind.CSV, ByteArrayInputStream("a,b\n1,2\n".toByteArray())).lines)
    }

    @Test
    fun slidesPastTheLimitAreNamedInThePdfNotDroppedSilently() {
        val entries = mutableListOf("ppt/presentation.xml" to "<p:presentation xmlns:p=\"p\"/>")
        for (i in 1..501) entries += "ppt/slides/slide$i.xml" to "<p:sld xmlns:p=\"p\"/>"
        val html = OfficeToHtml.convert(OfficeToHtml.Kind.PPTX, ByteArrayInputStream(zip(*entries.toTypedArray())), "Deck")
        assertTrue(html.contains("Only the first 500 slides are shown"))
    }

    // ---- Images to PDF ----

    @Test
    fun theMarginChoiceSetsTheWhiteBorder() {
        val none = PdfExporter.layoutPage(PdfPageSize.A4, null, 1000, 1414, pageNumbers = false, border = ImageMargin.NONE.points)
        assertEquals(595f, none.width, 1f)
        val large = PdfExporter.layoutPage(PdfPageSize.A4, null, 1000, 1414, pageNumbers = false, border = ImageMargin.LARGE.points)
        assertEquals(595f - 72f, large.width, 1f)
        val default = PdfExporter.layoutPage(PdfPageSize.A4, null, 1000, 1414, pageNumbers = false)
        assertEquals(595f - 2 * ImageMargin.SMALL.points, default.width, 1f)
    }

    // ---- PDF info ----

    @Test
    fun paperNamesAndUnits() {
        assertEquals("A4", PaperSizes.name(595f, 842f))
        assertEquals("A4", PaperSizes.name(842f, 595f))
        assertEquals("Letter", PaperSizes.name(612f, 792f))
        assertNull(PaperSizes.name(500f, 500f))
        assertEquals(210f, PaperSizes.toMillimetres(595f), 0.5f)
        assertEquals(11f, PaperSizes.toInches(792f), 0.001f)
    }

    @Test
    fun infoReadsTheShownPageSizeMixedSizesAndFormFields() {
        PDDocument().use { doc ->
            val first = PDPage(PDRectangle.A4).apply { rotation = 90 }
            doc.addPage(first)
            doc.addPage(PDPage(PDRectangle.LETTER))
            val form = PDAcroForm(doc).also { doc.documentCatalog.acroForm = it }
            form.fields.add(PDTextField(form).apply { partialName = "name" })

            val info = PdfMetadataEngine.readInfo(doc, 1234)
            // Turned a quarter, the A4 page is shown landscape.
            assertEquals(842f, info.pageWidthPt, 1f)
            assertEquals(595f, info.pageHeightPt, 1f)
            assertTrue(info.mixedPageSizes)
            assertEquals(1, info.formFields)
            assertFalse(info.encrypted)
        }
    }

    // ---- Fill forms ----

    @Test
    fun aValueTheFormsFontCannotDrawIsKeptAndFlatteningItIsRefused() = runBlocking {
        val source = File(dir, "form.pdf")
        PDDocument().use { doc ->
            val page = page(doc)
            val form = PDAcroForm(doc).also { doc.documentCatalog.acroForm = it }
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val field = PDTextField(form).apply { partialName = "name"; defaultAppearance = "/Helv 12 Tf 0 g" }
            val widget = field.widgets.first().apply { rectangle = PDRectangle(50f, 700f, 200f, 20f); this.page = page }
            form.fields.add(field)
            page.annotations = listOf<PDAnnotation>(widget)
            doc.save(source)
        }
        val hindi = "नमस्ते"
        val out = File(dir, "filled.pdf")
        PdfFormEngine.fill(context, Uri.fromFile(source), mapOf("name" to hindi), flatten = false, outputFile = out)
        PDDocument.load(out).use { doc -> assertEquals(hindi, doc.documentCatalog.acroForm.getField("name").valueAsString) }

        try {
            PdfFormEngine.fill(context, Uri.fromFile(source), mapOf("name" to hindi), flatten = true, outputFile = File(dir, "flat.pdf"))
            fail("flattened a value the font cannot draw")
        } catch (_: UserFacingException) {
        }
    }

    // ---- helpers ----

    private fun page(doc: PDDocument) = PDPage(PDRectangle.A4).also { doc.addPage(it) }

    private fun write(doc: PDDocument, page: PDPage, text: String) {
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use {
            it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(72f, 760f); it.showText(text); it.endText()
        }
    }

    /** An opaque picture: without setHasAlpha(false) an ARGB bitmap counts as see-through and gets a mask of its own. */
    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color); setHasAlpha(false) }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
