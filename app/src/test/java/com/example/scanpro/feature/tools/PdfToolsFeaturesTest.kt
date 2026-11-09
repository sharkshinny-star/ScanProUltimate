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
    fun rotateImageFileProducesRotatedImageForPdf() {
        val dir = File(app.cacheDir, "test_rotate_${System.currentTimeMillis()}").apply { mkdirs() }
        val source = File(dir, "source.png")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(source).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val dest = File(dir, "rotated.jpg")
        ImageProcessor.rotateImageFile(source, dest, 90)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)

        val aspect = ImageProcessor.imageAspect(dest.absolutePath)
        assertNotNull(aspect)
        assertEquals(0.5f, aspect ?: 0f, 0.05f)
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
    fun organizePagesRejectsSinglePageDocument() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_organize_${System.currentTimeMillis()}").apply { mkdirs() }
        val singlePagePdf = File(dir, "one_page.pdf")
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
        document.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        document.save(singlePagePdf)
        document.close()

        val outFile = File(dir, "organized.pdf")
        var thrown = false
        try {
            com.example.scanpro.imaging.PdfToolsEngine.organizePages(
                context = app,
                inputUri = android.net.Uri.fromFile(singlePagePdf),
                pageSpecs = listOf(com.example.scanpro.imaging.PageSpec(0, 0)),
                outputFile = outFile
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue(e.message?.contains("1 page") == true || e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Organising a 1-page PDF must be rejected", thrown)
    }

    @Test
    fun extractPagesRejectsSinglePageDocument() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        val singlePagePdf = File(dir, "one_page.pdf")
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
        document.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        document.save(singlePagePdf)
        document.close()

        val outFile = File(dir, "extracted.pdf")
        var thrown = false
        try {
            com.example.scanpro.imaging.PdfToolsEngine.extractPages(
                context = app,
                inputUri = android.net.Uri.fromFile(singlePagePdf),
                selectedPageIndices = listOf(0),
                outputFile = outFile
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue(e.message?.contains("1 page") == true || e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Extracting from a 1-page PDF must be rejected", thrown)
    }

    @Test
    fun toolsCardStringsReflectRenamedMergeAndSplitPdf() {
        assertEquals("Merge PDF", app.getString(com.example.scanpro.R.string.tools_card_merge))
        assertEquals("Split PDF", app.getString(com.example.scanpro.R.string.tools_card_split))
        assertEquals("Organise PDF", app.getString(com.example.scanpro.R.string.tools_card_organize))
        assertEquals("Organise PDF", app.getString(com.example.scanpro.R.string.tools_tile_organize))
        assertEquals("Extract Pages", app.getString(com.example.scanpro.R.string.tools_card_extract))
        assertEquals("Extract Pages", app.getString(com.example.scanpro.R.string.tools_tile_extract))
        assertTrue(app.getString(com.example.scanpro.R.string.tools_split_single_page_desc).contains("1 page"))
        assertTrue(app.getString(com.example.scanpro.R.string.tools_organize_single_page_desc).contains("1 page"))
        assertTrue(app.getString(com.example.scanpro.R.string.tools_extract_single_page_desc).contains("1 page"))
    }

    @Test
    fun compressPdfRejectsTargetSizeLargerThanOriginal() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_compress_${System.currentTimeMillis()}").apply { mkdirs() }
        val testPdf = File(dir, "doc.pdf")
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
        document.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        document.save(testPdf)
        document.close()

        val fileSize = testPdf.length()
        val targetLarger = fileSize + 1024L
        val outFile = File(dir, "compressed.pdf")

        var thrown = false
        try {
            com.example.scanpro.imaging.CompressPdfEngine.compressPdf(
                context = app,
                inputUri = android.net.Uri.fromFile(testPdf),
                preset = com.example.scanpro.imaging.CompressionPreset.BALANCED,
                outputFile = outFile,
                customTargetBytes = targetLarger
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue(e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Target size larger than original must be rejected", thrown)
    }

    @Test
    fun imagesToPdfRejectsEmptyImageList() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_img2pdf_empty_${System.currentTimeMillis()}").apply { mkdirs() }
        val outFile = File(dir, "result.pdf")
        var thrown = false
        try {
            com.example.scanpro.imaging.PdfToolsEngine.imagesToPdf(
                context = app,
                imageUris = emptyList(),
                outputFile = outFile
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue(e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Empty image list must be rejected", thrown)
    }

    @Test
    fun pdfToImagesRejectsPasswordProtectedPdf() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_pdf2img_pw_${System.currentTimeMillis()}").apply { mkdirs() }
        val sourcePdf = File(dir, "unprotected.pdf")
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        doc.save(sourcePdf)
        doc.close()

        val protectedPdf = File(dir, "protected.pdf")
        com.example.scanpro.imaging.PdfSecurity.protect(sourcePdf, protectedPdf, "securepass123")
        assertTrue(protectedPdf.exists())

        val outDir = File(dir, "exported").apply { mkdirs() }
        var thrown = false
        try {
            com.example.scanpro.imaging.PdfToolsEngine.pdfToImages(
                context = app,
                inputUri = android.net.Uri.fromFile(protectedPdf),
                outputDir = outDir
            )
        } catch (e: Exception) {
            thrown = true
            assertTrue("Expected UserFacingException for password protected PDF, got $e", e is com.example.scanpro.core.text.UserFacingException)
        }
        assertTrue("Password protected PDF must be rejected by PDF to images", thrown)
    }

    @Test
    fun officeToPdfDetectsSupportedMimeTypes() {
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.DOCX, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "test.docx"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.DOCX, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/vnd.ms-word.document.macroenabled.12", "test.docm"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.XLSX, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data.xlsx"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.XLSX, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/vnd.ms-excel.sheet.macroenabled.12", "macro.xlsm"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.CSV, com.example.scanpro.imaging.OfficeToHtml.kindOf("text/csv", "report.csv"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.CSV, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/csv", "export.csv"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.TEXT, com.example.scanpro.imaging.OfficeToHtml.kindOf("text/plain", "notes.txt"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.TEXT, com.example.scanpro.imaging.OfficeToHtml.kindOf("text/markdown", "readme.md"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.HTML, com.example.scanpro.imaging.OfficeToHtml.kindOf("text/html", "page.html"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.HTML, com.example.scanpro.imaging.OfficeToHtml.kindOf("application/xhtml+xml", "page.xhtml"))
        assertEquals(com.example.scanpro.imaging.OfficeToHtml.Kind.HTML, com.example.scanpro.imaging.OfficeToHtml.kindOf("message/rfc822", "email.mhtml"))
    }

    @Test
    fun splitPdfNamesFilesWithSourceBaseNamePrefix() = kotlinx.coroutines.runBlocking {
        val dir = File(app.cacheDir, "test_split_names_${System.currentTimeMillis()}").apply { mkdirs() }
        val invoicePdf = File(dir, "Invoice.pdf")
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        doc.save(invoicePdf)
        doc.close()

        val outSingle = File(dir, "out_single").apply { mkdirs() }
        val singleFiles = com.example.scanpro.imaging.PdfToolsEngine.splitPdf(
            context = app,
            inputUri = android.net.Uri.fromFile(invoicePdf),
            mode = com.example.scanpro.imaging.SplitMode.SINGLE_PAGES,
            outputDir = outSingle
        )
        assertEquals(2, singleFiles.size)
        assertEquals("Invoice page 001.pdf", singleFiles[0].name)
        assertEquals("Invoice page 002.pdf", singleFiles[1].name)

        val outRanges = File(dir, "out_ranges").apply { mkdirs() }
        val rangeFiles = com.example.scanpro.imaging.PdfToolsEngine.splitPdf(
            context = app,
            inputUri = android.net.Uri.fromFile(invoicePdf),
            mode = com.example.scanpro.imaging.SplitMode.PAGE_RANGES,
            customRangesText = "1-2",
            outputDir = outRanges
        )
        assertEquals(1, rangeFiles.size)
        assertEquals("Invoice range 1 pages 1 to 2.pdf", rangeFiles[0].name)

        val outParts = File(dir, "out_parts").apply { mkdirs() }
        val partFiles = com.example.scanpro.imaging.PdfToolsEngine.splitPdf(
            context = app,
            inputUri = android.net.Uri.fromFile(invoicePdf),
            mode = com.example.scanpro.imaging.SplitMode.EVERY_N_PAGES,
            everyNPages = 1,
            outputDir = outParts
        )
        assertEquals(2, partFiles.size)
        assertEquals("Invoice part 1 pages 1 to 1.pdf", partFiles[0].name)
        assertEquals("Invoice part 2 pages 2 to 2.pdf", partFiles[1].name)
    }

    @Test
    fun sourceBaseNameGeneratesFileSafePrefixes() {
        val invoiceUri = android.net.Uri.parse("content://media/external/files/1234/Invoice_2026.pdf")
        val base1 = com.example.scanpro.imaging.PdfToolsEngine.sourceBaseName(app, invoiceUri)
        assertEquals("Invoice_2026", base1)

        val fileUri = android.net.Uri.fromFile(java.io.File("/tmp/My Document (Final).pdf"))
        val base2 = com.example.scanpro.imaging.PdfToolsEngine.sourceBaseName(app, fileUri)
        assertEquals("My Document (Final)", base2)

        val fallbackUri = android.net.Uri.parse("content://media")
        val base3 = com.example.scanpro.imaging.PdfToolsEngine.sourceBaseName(app, fallbackUri, fallback = "CustomFallback")
        assertEquals("CustomFallback", base3)
    }
}
