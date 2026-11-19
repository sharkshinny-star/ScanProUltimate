package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.UserFacingException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
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

/** Remove blank pages, Greyscale, Flatten and Extract images: the decisions and the PDF writing, without PdfRenderer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfCleanupToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "cleanup-test").apply { deleteRecursively(); mkdirs() }
    }

    // ---- Remove blank pages ----

    @Test
    fun onlyPagesWithoutContentAndWithBlankPixelsAreBlank() = runBlocking {
        val paper = ByteArray(64 * 64) { 245.toByte() }
        val inked = paper.copyOf().also { for (i in 0 until 400) it[i] = 20 }
        val blank = PdfCleanupTools.blankPages(
            pageCount = 5,
            hasContent = { it == 1 },
            luma = { i -> when (i) { 2 -> inked; 4 -> null; else -> paper } }
        )
        // Page 1 has text, page 2 ink, page 4 could not be drawn: only 0 and 3 are blank.
        assertEquals(listOf(0, 3), blank)
    }

    @Test
    fun brightnessFollowsRec601() {
        assertEquals(255, PdfCleanupTools.greyLevel(Color.WHITE))
        assertEquals(0, PdfCleanupTools.greyLevel(Color.BLACK))
        assertEquals(76, PdfCleanupTools.greyLevel(Color.RED))
    }

    @Test
    fun textAndVisibleAnnotationsCountAsContentButALinkDoesNot() {
        PDDocument().use { doc ->
            val text = page(doc).also { write(doc, it, "Hello") }
            val empty = page(doc)
            val linkOnly = page(doc).also { it.annotations = listOf(PDAnnotationLink().apply { rectangle = PDRectangle(10f, 10f, 50f, 20f) }) }
            val stamped = page(doc).also { it.annotations = listOf(square(doc)) }
            val stripper = PDFTextStripper()
            assertTrue(PdfCleanupTools.pageHasContent(doc, doc.pages.indexOf(text), stripper))
            assertFalse(PdfCleanupTools.pageHasContent(doc, doc.pages.indexOf(empty), stripper))
            assertFalse(PdfCleanupTools.pageHasContent(doc, doc.pages.indexOf(linkOnly), stripper))
            assertTrue(PdfCleanupTools.pageHasContent(doc, doc.pages.indexOf(stamped), stripper))
        }
    }

    @Test
    fun removingPagesKeepsTheRestInOrder() = runBlocking {
        val source = pdf("four.pdf") { doc -> listOf("A", "", "C", "").forEach { t -> page(doc).also { if (t.isNotEmpty()) write(doc, it, t) } } }
        val out = File(dir, "out.pdf")

        val result = PdfCleanupTools.removePages(context, source, setOf(1, 3), out)

        assertEquals(2, result.pageCount)
        PDDocument.load(out).use { assertEquals(listOf("A", "C"), (1..2).map { p -> textOf(it, p) }) }
    }

    @Test
    fun everyPageCannotBeRemoved() = runBlocking {
        val source = pdf("two.pdf") { doc -> repeat(2) { page(doc) } }
        val out = File(dir, "out.pdf")
        try {
            PdfCleanupTools.removePages(context, source, setOf(0, 1), out)
            fail("removed every page")
        } catch (_: UserFacingException) {
            assertFalse(out.exists())
        }
    }

    // ---- Greyscale ----

    @Test
    fun greyscaleKeepsPageSizesAndDrawsGrey() = runBlocking {
        val out = File(dir, "grey.pdf")
        val sizes = listOf(200f to 300f, 300f to 200f)
        PdfCleanupTools.writeGreyscale(
            pageCount = 2,
            pageSize = { sizes[it] },
            draw = { _, target -> target.eraseColor(Color.rgb(200, 30, 30)) },
            outputFile = out
        )
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            sizes.forEachIndexed { i, (w, h) ->
                val box = doc.getPage(i).mediaBox
                assertEquals(w, box.width, 0.01f)
                assertEquals(h, box.height, 0.01f)
            }
            val image = doc.getPage(0).resources.let { res -> res.getXObject(res.xObjectNames.first()) as PDImageXObject }
            // At 150 dpi a 200 pt wide page is 416 pixels wide (416.7, rounded down).
            assertEquals(416, image.width)
            val pixel = image.image.getPixel(image.width / 2, image.height / 2)
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            assertTrue("not grey: $r $g $b", maxOf(r, g, b) - minOf(r, g, b) <= 6)
        }
    }

    // ---- Flatten ----

    @Test
    fun appearanceIsScaledOntoTheAnnotationRectangle() {
        PDDocument().use { doc ->
            val annotation = square(doc, PDRectangle(100f, 100f, 100f, 50f))
            val m = PdfCleanupTools.appearanceTransform(annotation)!!
            assertEquals(2f, m.scaleX, 0.001f)
            assertEquals(1f, m.scaleY, 0.001f)
            assertEquals(100f, m.translateX, 0.001f)
            assertEquals(100f, m.translateY, 0.001f)
        }
    }

    @Test
    fun flattenDrawsFieldsAndAnnotationsInAndKeepsLinks() {
        PDDocument().use { doc ->
            val page = page(doc)
            val form = PDAcroForm(doc).also { doc.documentCatalog.acroForm = it }
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val field = PDTextField(form).apply { partialName = "name"; defaultAppearance = "/Helv 12 Tf 0 g" }
            val widget = field.widgets.first().apply { rectangle = PDRectangle(50f, 700f, 200f, 20f); this.page = page }
            form.fields.add(field)
            val link = PDAnnotationLink().apply { rectangle = PDRectangle(10f, 10f, 50f, 20f) }
            page.annotations = listOf<PDAnnotation>(widget, square(doc), link)
            field.value = "Ada Lovelace"

            val count = PdfCleanupTools.flattenDocument(doc)

            assertEquals(1, count.fields)
            assertEquals(1, count.annotations)
            assertNull(doc.documentCatalog.acroForm)
            assertEquals(listOf("Link"), page.annotations.map { it.subtype })
            val saved = File(dir, "flat.pdf").also { doc.save(it) }
            PDDocument.load(saved).use { assertTrue(textOf(it, 1).contains("Ada Lovelace")) }
        }
    }

    @Test
    fun aPdfWithNothingToFlattenSaysSo() = runBlocking {
        val source = pdf("plain.pdf") { doc -> write(doc, page(doc), "Text") }
        try {
            PdfCleanupTools.flatten(context, source, File(dir, "flat.pdf"))
            fail("flattened nothing")
        } catch (_: UserFacingException) {
        }
    }

    // ---- Extract images ----

    @Test
    fun imagesAreSavedOnceEachJpegAsStoredAndTinyOnesSkipped() = runBlocking {
        val source = pdf("images.pdf") { doc ->
            val photo = JPEGFactory.createFromImage(doc, solid(40, 30, Color.RED))
            val drawing = LosslessFactory.createFromImage(doc, solid(20, 20, Color.BLUE))
            val bullet = LosslessFactory.createFromImage(doc, solid(4, 4, Color.BLACK))
            PDPageContentStream(doc, page(doc)).use { it.drawImage(photo, 10f, 10f) }
            PDPageContentStream(doc, page(doc)).use {
                it.drawImage(photo, 10f, 10f)
                it.drawImage(drawing, 100f, 10f)
                it.drawImage(bullet, 200f, 10f)
            }
        }
        val out = File(dir, "images").apply { mkdirs() }

        val files = PdfCleanupTools.extractImages(context, source, out)

        assertEquals(listOf("jpg", "png"), files.map { it.extension })
        val head = files[0].readBytes()
        assertEquals(0xFF, head[0].toInt() and 0xFF)
        assertEquals(0xD8, head[1].toInt() and 0xFF)
        val png = android.graphics.BitmapFactory.decodeFile(files[1].path)
        assertEquals(20, png.width)
    }

    @Test
    fun aPdfWithoutImagesSaysSo() = runBlocking {
        val source = pdf("text.pdf") { doc -> write(doc, page(doc), "No pictures") }
        try {
            PdfCleanupTools.extractImages(context, source, File(dir, "none").apply { mkdirs() })
            fail("extracted from a PDF with no images")
        } catch (_: UserFacingException) {
        }
    }

    @Test
    fun imagesInsideAFormAreFoundAndASharedImageOnlyOnce() {
        PDDocument().use { doc ->
            val photo = LosslessFactory.createFromImage(doc, solid(30, 30, Color.GREEN))
            // An appearance stream is a form XObject; PDFBox only offers a content stream on that subclass.
            val form = PDAppearanceStream(doc).apply {
                bBox = PDRectangle(0f, 0f, 100f, 100f)
                resources = PDResources()
            }
            PDPageContentStream(doc, form).use { it.drawImage(photo, 0f, 0f) }
            val page = page(doc)
            PDPageContentStream(doc, page).use { it.drawForm(form); it.drawImage(photo, 200f, 200f) }
            val found = mutableListOf<PDImageXObject>()
            PdfCleanupTools.pageImages(page, HashSet<COSBase>(), found)
            assertEquals(1, found.size)
        }
    }

    @Test
    fun toInvertedColorsInvertsRgbValues() {
        val bitmap = solid(10, 10, Color.rgb(255, 255, 255))
        PdfCleanupTools.toInvertedColors(bitmap)
        val pixel = bitmap.getPixel(0, 0)
        assertEquals(0, Color.red(pixel))
        assertEquals(0, Color.green(pixel))
        assertEquals(0, Color.blue(pixel))
    }

    // ---- helpers ----

    private fun page(doc: PDDocument) = PDPage(PDRectangle.A4).also { doc.addPage(it) }

    private fun write(doc: PDDocument, page: PDPage, text: String) {
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use {
            it.beginText(); it.setFont(PDType1Font.HELVETICA, 14f); it.newLineAtOffset(72f, 700f); it.showText(text); it.endText()
        }
    }

    private fun square(doc: PDDocument, rect: PDRectangle = PDRectangle(300f, 300f, 50f, 50f)): PDAnnotationSquareCircle {
        val appearance = PDAppearanceStream(doc).apply { bBox = PDRectangle(0f, 0f, 50f, 50f); resources = PDResources() }
        PDPageContentStream(doc, appearance).use { it.addRect(0f, 0f, 50f, 50f); it.fill() }
        return PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
            rectangle = rect
            this.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
        }
    }

    private fun solid(w: Int, h: Int, color: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun textOf(doc: PDDocument, page: Int): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(doc).trim()

    private fun pdf(name: String, build: (PDDocument) -> Unit): Uri {
        val file = File(dir, name)
        PDDocument().use { build(it); it.save(file) }
        return Uri.fromFile(file)
    }
}
