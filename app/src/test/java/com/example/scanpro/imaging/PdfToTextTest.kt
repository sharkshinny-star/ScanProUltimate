package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.data.OcrScript
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PDF to Word or text: pages with a text layer are read with PDFBox, pages without one are handed to the OCR (faked
 * here), and the result is plain text or a .docx.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfToTextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "pdf-to-text-test").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun pagesWithTextAreReadFromTheirTextLayerAndNeverSentToOcr() {
        val file = pdf("text.pdf", listOf("First page words", "Second page words"))
        val asked = mutableListOf<Int>()

        val pages = read(file) { index -> asked += index; "should not be used" }

        assertEquals(2, pages.size)
        assertTrue(pages.all { it.source == PageTextSource.TEXT_LAYER })
        assertTrue(pages[0].text.contains("First page words"))
        assertTrue(pages[1].text.contains("Second page words"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aPageWithoutTextFallsBackToOcrAndOnlyThatPage() {
        val file = pdf("mixed.pdf", listOf("Typed page", null, "Another typed page"))
        val asked = mutableListOf<Int>()

        val pages = read(file) { index -> asked += index; "  words from the scan \n" }

        assertEquals(listOf(1), asked)
        assertEquals(listOf(PageTextSource.TEXT_LAYER, PageTextSource.OCR, PageTextSource.TEXT_LAYER), pages.map { it.source })
        assertEquals("words from the scan", pages[1].text)
    }

    @Test
    fun aPageThatOcrCannotReadEitherIsMarkedAsHavingNoText() {
        val file = pdf("blank.pdf", listOf("Typed page", null))

        val pages = read(file) { "   " }

        assertEquals(PageTextSource.NONE, pages[1].source)
        assertEquals("", pages[1].text)
    }

    @Test
    fun plainTextHasAHeadingAndTheTextOfEveryPage() {
        val pages = listOf(
            PdfTextPage("Alpha line", PageTextSource.TEXT_LAYER),
            PdfTextPage("", PageTextSource.NONE),
            PdfTextPage("Gamma line", PageTextSource.OCR)
        )

        val text = PdfToTextEngine.plainText(pages)

        assertTrue(text.contains("Alpha line"))
        assertTrue(text.contains("Gamma line"))
        assertTrue(text.indexOf("Alpha line") < text.indexOf("Gamma line"))
        // One block per page, a blank line apart.
        assertEquals(3, text.trim().split("\n\n").size)
    }

    @Test
    fun theWordDocumentIsAValidZipThatHoldsTheTextOnePageAfterAnother() {
        val pages = listOf(
            PdfTextPage("Alpha line\nSecond line", PageTextSource.TEXT_LAYER),
            PdfTextPage("Gamma & <delta>", PageTextSource.OCR)
        )
        val output = File(dir, "out.docx")

        PdfToTextEngine.write("My title", pages, TextExportFormat.WORD, output)

        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("[Content_Types].xml"))
            assertNotNull(zip.getEntry("word/styles.xml"))
            val entry = zip.getEntry("word/document.xml")
            assertNotNull(entry)
            val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue(xml.contains("My title"))
            assertTrue(xml.contains("Alpha line"))
            assertTrue(xml.contains("Second line"))
            // Markup in the text is escaped, and the pages are apart by a page break.
            assertTrue(xml.contains("Gamma &amp; &lt;delta&gt;"))
            assertTrue(xml.contains("w:type=\"page\""))
        }
    }

    @Test
    fun pageListIsCutShortWhenLong() {
        assertEquals("3, 4, 9", PdfToTextEngine.pageList(listOf(3, 4, 9)))
        assertEquals("1, 2…", PdfToTextEngine.pageList(listOf(1, 2, 3), limit = 2))
    }

    @Test
    fun convertWritesATextFileFromATextPdfWithoutNeedingOcr() = runBlocking {
        val file = pdf("source.pdf", listOf("Readable words on page one"))
        val output = File(dir, "result.txt")

        val outcome = PdfToTextEngine.convert(
            context, Uri.fromFile(file), TextExportFormat.TEXT, OcrScript.LATIN, OcrEngine(context), "source", output
        )

        assertEquals(1, outcome.pageCount)
        assertEquals(1, outcome.textLayerPages)
        assertTrue(outcome.ocrPages.isEmpty())
        assertTrue(outcome.emptyPages.isEmpty())
        assertTrue(output.readText().contains("Readable words on page one"))
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("pdftext_") })
    }

    @Test
    fun aProtectedPdfAsksForItsPasswordAndThenGivesAnUnprotectedResult() {
        val locked = pdf("locked.pdf", listOf("Secret words"), userPassword = "open sesame")
        val output = File(dir, "locked.txt")
        val uri = Uri.fromFile(locked)

        val none = passwordNeeded { convert(uri, output, null) }
        assertFalse(none.wrongPassword)
        val wrong = passwordNeeded { convert(uri, output, "not it") }
        assertTrue(wrong.wrongPassword)
        assertFalse(output.exists())

        val outcome = runBlocking { convert(uri, output, "open sesame") }
        assertEquals(1, outcome.pageCount)
        assertTrue(output.readText().contains("Secret words"))
    }

    private suspend fun convert(uri: Uri, output: File, password: String?) =
        PdfToTextEngine.convert(context, uri, TextExportFormat.TEXT, OcrScript.LATIN, OcrEngine(context), "locked", output, password)

    private fun passwordNeeded(block: suspend () -> Unit): PdfPasswordNeededException {
        try {
            runBlocking { block() }
        } catch (asked: PdfPasswordNeededException) {
            return asked
        }
        fail("The tool should have asked for a password")
        throw AssertionError()
    }

    private fun read(file: File, ocr: suspend (Int) -> String): List<PdfTextPage> = runBlocking {
        PDDocument.load(file).use { document -> PdfToTextEngine.readPages(document, ocr) }
    }

    /** A PDF with one page per entry; a null entry is a page with nothing on it, like a scan with no text layer. */
    private fun pdf(name: String, pageTexts: List<String?>, userPassword: String? = null): File {
        val file = File(dir, name)
        PDDocument().use { document ->
            pageTexts.forEach { text ->
                val page = PDPage()
                document.addPage(page)
                if (text != null) {
                    PDPageContentStream(document, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 12f)
                        stream.newLineAtOffset(50f, 700f)
                        stream.showText(text)
                        stream.endText()
                    }
                }
            }
            if (userPassword != null) {
                document.protect(
                    StandardProtectionPolicy(userPassword, userPassword, AccessPermission()).apply { encryptionKeyLength = 128 }
                )
            }
            document.save(file)
        }
        return file
    }
}
