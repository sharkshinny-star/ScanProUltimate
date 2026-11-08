package com.example.scanpro.feature.tools

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.navigation.ScanProDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfToolsFeaturesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun cameraRouteMatchesDestinationContract() {
        val retake = ScanProDestinations.cameraRetake("doc1", "p1", 0)
        assertEquals("camera?retake=doc1/p1/0", retake)
        val parsed = ScanProDestinations.parseRetake("doc1/p1/0")
        assertNotNull(parsed)
        assertEquals("doc1", parsed?.documentId)
        assertEquals("p1", parsed?.pageId)
        assertEquals(0, parsed?.position)
    }

    @Test
    fun cropSourceToNewFileProducesValidImageForPdf() {
        val dir = File(app.cacheDir, "test_crop_${System.currentTimeMillis()}").apply { mkdirs() }
        val source = File(dir, "source.png")
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        FileOutputStream(source).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val dest = File(dir, "cropped.jpg")
        val quad = CropQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.9f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f)
        )
        ImageProcessor.cropSourceToNewFile(source, dest, quad)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)

        val aspect = ImageProcessor.imageAspect(dest.absolutePath)
        assertNotNull(aspect)
    }

    @Test
    fun splitPdfRejectsSinglePageDocument() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_split_${System.currentTimeMillis()}").apply { mkdirs() }
        val singlePagePdf = File(dir, "one_page.pdf")
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
        document.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        document.save(singlePagePdf)
        document.close()

        val outDir = File(dir, "out").apply { mkdirs() }
        var thrown = false
        try {
            com.example.scanpro.imaging.PdfToolsEngine.splitPdf(
                context = app,
                inputUri = android.net.Uri.fromFile(singlePagePdf),
                mode = com.example.scanpro.imaging.SplitMode.SINGLE_PAGES,
                outputDir = outDir
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue(e.message?.contains("1 page") == true || e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Splitting a 1-page PDF must be rejected", thrown)
    }

    @Test
    fun toolsCardStringsReflectRenamedMergeAndSplitPdf() {
        assertEquals("Merge PDF", app.getString(com.example.scanpro.R.string.tools_card_merge))
        assertEquals("Split PDF", app.getString(com.example.scanpro.R.string.tools_card_split))
        assertTrue(app.getString(com.example.scanpro.R.string.tools_split_single_page_desc).contains("1 page"))
    }
}
