package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.AppStrings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compress PDF on a document with selectable text and a large photo: the photo is shrunk, the text and the page size
 * survive. Real image encoding, so native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompressPdfEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        AppStrings.init(context)
    }

    /** A Letter page with a line of text and a 1600x1200 noisy photo stored losslessly (large, as a camera image is). */
    private fun source(): File {
        val file = File(context.cacheDir, "compress_src_${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER).also(doc::addPage)
            val photo = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
            val random = java.util.Random(7)
            val row = IntArray(1600)
            for (y in 0 until 1200) {
                for (x in 0 until 1600) {
                    val base = (x / 6 + y / 5) % 255
                    val n = random.nextInt(40)
                    row[x] = android.graphics.Color.rgb((base + n) % 256, (base * 2 + n) % 256, (255 - base + n) % 256)
                }
                photo.setPixels(row, 0, 1600, 0, y, 1600, 1)
            }
            val image = LosslessFactory.createFromImage(doc, photo)
            photo.recycle()
            PDPageContentStream(doc, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 14f)
                content.newLineAtOffset(72f, 740f)
                content.showText("Quarterly report 2026")
                content.endText()
                content.drawImage(image, 72f, 200f, 468f, 351f)
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `a photo is shrunk while the text and the page size survive`() {
        val input = source()
        val output = File(context.cacheDir, "exports/compressed_${System.nanoTime()}.pdf")
        val result = runBlocking {
            CompressPdfEngine.compressPdf(context, Uri.fromFile(input), CompressionPreset.BALANCED, output)
        }
        assertTrue("smaller: ${result.compressedSizeBytes} vs ${result.originalSizeBytes}", result.compressedSizeBytes < result.originalSizeBytes / 2)
        assertTrue("text kept", result.isSearchablePreserved)
        PDDocument.load(output).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertEquals(PDRectangle.LETTER.width, doc.getPage(0).mediaBox.width, 0.5f)
            assertEquals(PDRectangle.LETTER.height, doc.getPage(0).mediaBox.height, 0.5f)
            assertTrue(PDFTextStripper().getText(doc).contains("Quarterly report 2026"))
        }
    }

    @Test
    fun `images about to be shrunk are decoded smaller`() {
        assertEquals(1, CompressPdfEngine.subsamplingFor(0.85f))
        assertEquals(1, CompressPdfEngine.subsamplingFor(0.65f))
        assertEquals(2, CompressPdfEngine.subsamplingFor(0.45f))
        assertEquals(4, CompressPdfEngine.subsamplingFor(0.25f))
        assertEquals(8, CompressPdfEngine.subsamplingFor(0.01f))
    }
}
