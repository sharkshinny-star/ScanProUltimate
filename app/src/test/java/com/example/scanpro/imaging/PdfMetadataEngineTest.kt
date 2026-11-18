package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PDF info and metadata: reading the details, editing them and removing them all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfMetadataEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "metadata-test").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun detailsAreReadWithTheFileFacts() = runBlocking {
        val info = PdfMetadataEngine.read(context, sample())

        assertEquals("Quarterly report", info.title)
        assertEquals("A. Author", info.author)
        assertEquals("Writer 7", info.creator)
        assertEquals(3, info.pageCount)
        assertEquals("1.7", info.version)
        assertTrue(info.sizeBytes > 0)
        assertTrue(info.hasXmp)
        assertEquals(CREATED, info.created)
        assertNull(info.modified)
    }

    @Test
    fun editedDetailsAreWrittenAndTheStaleXmpCopyDropped() = runBlocking {
        val out = File(dir, "edited.pdf")

        PdfMetadataEngine.write(context, sample(), PdfMetadataEdit("New title", " ", "Budget", "q4, money"), removeAll = false, outputFile = out)

        PDDocument.load(out).use { doc ->
            val info = PdfMetadataEngine.readInfo(doc, out.length())
            assertEquals("New title", info.title)
            assertEquals("", info.author)
            assertEquals("Budget", info.subject)
            assertEquals("q4, money", info.keywords)
            // Untouched details stay, the change is dated, and no XMP copy contradicts the new title.
            assertEquals("Writer 7", info.creator)
            assertTrue(info.modified != null)
            assertFalse(info.hasXmp)
        }
    }

    @Test
    fun removeAllLeavesNoDetailsBehind() = runBlocking {
        val out = File(dir, "clean.pdf")

        PdfMetadataEngine.write(context, sample(), null, removeAll = true, outputFile = out)

        PDDocument.load(out).use { doc ->
            // Only the details this test is about; page size, forms and encryption (F211) are checked elsewhere.
            val info = PdfMetadataEngine.readInfo(doc, 1)
            assertEquals(
                PdfInfo(pageCount = 3, version = "1.7", sizeBytes = 1),
                info.copy(pageWidthPt = 0f, pageHeightPt = 0f, mixedPageSizes = false, formFields = 0, encrypted = false)
            )
            assertNull(doc.getPage(0).metadata)
            assertEquals(3, doc.numberOfPages)
        }
    }

    private fun sample(): Uri {
        val file = File(dir, "sample.pdf")
        PDDocument().use { doc ->
            repeat(3) { doc.addPage(PDPage()) }
            doc.version = 1.7f
            doc.documentInformation.apply {
                title = "Quarterly report"
                author = "A. Author"
                creator = "Writer 7"
                producer = "Some Producer"
                creationDate = Calendar.getInstance().apply { timeInMillis = CREATED }
            }
            val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>".toByteArray()
            doc.documentCatalog.metadata = PDMetadata(doc).apply { importXMPMetadata(xmp) }
            doc.getPage(0).metadata = PDMetadata(doc).apply { importXMPMetadata(xmp) }
            doc.save(file)
        }
        return Uri.fromFile(file)
    }

    private companion object {
        // Whole seconds: PDF dates keep no milliseconds.
        const val CREATED = 1_700_000_000_000L
    }
}
