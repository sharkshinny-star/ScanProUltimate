package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.AppStrings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real Watermark engine, end to end: text the built-in Helvetica cannot write (Hindi, Tamil) must produce a PDF
 * with the watermark as a picture on every page, where it used to throw; Latin text must stay real PDF text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatermarkUnicodeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        AppStrings.init(context)
    }

    private fun blankPdf(pages: Int): File {
        val file = File(context.cacheDir, "wm_source_${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage(PDRectangle.A4)) }
            doc.save(file)
        }
        return file
    }

    private fun stamp(text: String, pages: Int = 2): File {
        val output = File(context.cacheDir, "wm_out_${System.nanoTime()}.pdf")
        runBlocking {
            PdfToolsEngine.watermarkPdf(context, Uri.fromFile(blankPdf(pages)), WatermarkConfig(text = text), output)
        }
        return output
    }

    private fun imagesOnEachPage(file: File): List<Int> = PDDocument.load(file).use { doc ->
        doc.pages.map { page -> page.resources.xObjectNames.count { page.resources.getXObject(it) is PDImageXObject } }
    }

    @Test
    fun `a Hindi watermark is stamped as a picture on every page`() {
        val output = stamp("गोपनीय")
        assertTrue("output written", output.length() > 0)
        assertEquals(listOf(1, 1), imagesOnEachPage(output))
    }

    @Test
    fun `a Tamil watermark is stamped as a picture`() {
        assertEquals(listOf(1), imagesOnEachPage(stamp("ரகசியம்", pages = 1)))
    }

    @Test
    fun `a Latin watermark stays real text`() {
        val output = stamp("CONFIDENTIAL", pages = 1)
        assertEquals(listOf(0), imagesOnEachPage(output))
        val text = PDDocument.load(output).use { com.tom_roush.pdfbox.text.PDFTextStripper().getText(it) }
        // Turned 45 degrees, the extractor splits the word over several lines ("CONFID / ENTIA / L"): compare without breaks.
        assertTrue("watermark text is selectable: '$text'", text.replace(Regex("\\s"), "").contains("CONFIDENTIAL"))
    }
}
