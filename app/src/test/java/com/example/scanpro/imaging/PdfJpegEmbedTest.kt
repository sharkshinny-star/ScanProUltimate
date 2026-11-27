package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A scanned page used to cost megabytes because Android's PdfDocument stores a drawn bitmap as lossless pixels. The
 * exporter now writes the page layout and the invisible text with PdfDocument and puts each page's picture in as a
 * JPEG underneath ([PdfExporter.embedJpegs]). PdfDocument itself does not run under Robolectric, so this checks the
 * embedding step on a PDF made with PDFBox: the picture goes in as JPEG, behind the existing text, on pages with and
 * without content, and the file stays about the size of the JPEGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfJpegEmbedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "pdf-jpeg-embed-test").apply { deleteRecursively(); mkdirs() }
    }

    /** A noisy photo-like picture, so the JPEG is not trivially tiny. */
    private fun jpeg(name: String): File {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val random = Random(7)
        val pixels = IntArray(600 * 800) { (0xFF shl 24) or random.nextInt(0x1000000) }
        bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 800)
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun pictureGoesInAsJpegBehindTheTextAndOnEmptyPages() {
        val pdf = File(dir, "layout.pdf")
        PDDocument().use { doc ->
            val withText = PDPage(PDRectangle.A4).also(doc::addPage)
            doc.addPage(PDPage(PDRectangle.A4)) // a page with no content at all
            PDPageContentStream(doc, withText).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("searchable words")
                cs.endText()
            }
            doc.save(pdf)
        }
        val first = jpeg("p0.jpg")
        val second = jpeg("p1.jpg")
        val images = listOf(
            PdfExporter.PageImage(0, first, 18f, 18f, 559f, 806f, 842),
            PdfExporter.PageImage(1, second, 18f, 18f, 559f, 806f, 842)
        )

        PdfExporter.embedJpegs(pdf, images)

        PDDocument.load(pdf).use { doc ->
            assertEquals(2, doc.numberOfPages)
            for (index in 0 until 2) {
                val resources = requireNotNull(doc.getPage(index).resources)
                val pictures = resources.xObjectNames.map { resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
                assertEquals("page $index should hold one picture", 1, pictures.size)
                assertEquals("jpg", pictures.single().suffix)
            }
            val stripper = PDFTextStripper().apply { startPage = 1; endPage = 1 }
            assertTrue("the text layer must survive", stripper.getText(doc).contains("searchable words"))
        }
        // The pictures are copied in as they are, not decoded to pixels: the PDF is about as big as the two JPEGs.
        val jpegs = first.length() + second.length()
        assertTrue("pdf ${pdf.length()} vs jpegs $jpegs", pdf.length() < jpegs + 20_000)
    }
}
