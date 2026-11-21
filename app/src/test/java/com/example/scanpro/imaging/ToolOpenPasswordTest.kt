package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.UserFacingException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every single-file tool works on a protected PDF once its password is given (F155, pushed as F148): the engine tells whether a file
 * needs one, writes an unlocked working copy with the right password and leaves nothing behind with a wrong one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ToolOpenPasswordTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "tool-password-test").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun onlyAFileWithAnOpenPasswordNeedsOne() = runBlocking<Unit> {
        assertTrue(PdfToolsEngine.needsOpenPassword(context, pdf("locked.pdf", 2, userPassword = USER)))
        assertFalse(PdfToolsEngine.needsOpenPassword(context, pdf("plain.pdf", 2)))
        // Editing restrictions alone don't stop a file from opening.
        assertFalse(PdfToolsEngine.needsOpenPassword(context, pdf("restricted.pdf", 2, userPassword = "", ownerPassword = USER)))
    }

    @Test
    fun aFileThatIsNotAPdfIsLeftToTheTool() = runBlocking<Unit> {
        val junk = File(dir, "junk.pdf").apply { writeText("not a PDF") }

        assertFalse(PdfToolsEngine.needsOpenPassword(context, Uri.fromFile(junk)))
    }

    @Test
    fun theRightPasswordWritesAnUnlockedCopy() = runBlocking<Unit> {
        val copy = File(dir, "copy.pdf")

        assertTrue(PdfToolsEngine.decryptCopy(context, pdf("locked.pdf", 3, userPassword = USER), USER, copy))

        PDDocument.load(copy).use {
            assertFalse(it.isEncrypted)
            assertEquals(3, it.numberOfPages)
        }
    }

    @Test
    fun aWrongPasswordLeavesNothingBehind() = runBlocking<Unit> {
        val copy = File(dir, "copy.pdf")

        assertFalse(PdfToolsEngine.decryptCopy(context, pdf("locked.pdf", 1, userPassword = USER), "not it", copy))

        assertFalse(copy.exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("open_in_") })
    }

    @Test
    fun theUnlockedCopyWorksWithAPageTool() = runBlocking<Unit> {
        val copy = File(dir, "Invoice.pdf")
        PdfToolsEngine.decryptCopy(context, pdf("locked.pdf", 3, userPassword = USER), USER, copy)

        val result = PdfToolsEngine.extractPages(context, Uri.fromFile(copy), listOf(0, 2), File(dir, "out.pdf"))

        assertEquals(2, result.pageCount)
        assertEquals("Invoice", PdfToolsEngine.sourceBaseName(context, Uri.fromFile(copy)))
    }

    @Test
    fun extractCountsOnlyPagesThatExist() = runBlocking<Unit> {
        val result = PdfToolsEngine.extractPages(context, pdf("in.pdf", 2), listOf(0, 5), File(dir, "extracted.pdf"))

        assertEquals(1, result.pageCount)
    }

    @Test
    fun extractWithNoExistingPageMakesNoEmptyPdf() {
        try {
            runBlocking { PdfToolsEngine.extractPages(context, pdf("in.pdf", 2), listOf(7, 9), File(dir, "empty.pdf")) }
            fail("An extract with no existing page should stop")
        } catch (expected: UserFacingException) {
            assertFalse(File(dir, "empty.pdf").exists())
        }
    }

    @Test
    fun aSecondFileOfTheSameNameIsNumbered() {
        File(dir, "a.pdf").writeText("x")

        assertEquals("a (2).pdf", PdfToolsEngine.uniqueFile(dir, "a.pdf").name)
        assertEquals("b.pdf", PdfToolsEngine.uniqueFile(dir, "b.pdf").name)
    }

    private fun pdf(name: String, pages: Int, userPassword: String? = null, ownerPassword: String? = null): Uri {
        val file = File(dir, name)
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            if (userPassword != null) {
                document.protect(
                    StandardProtectionPolicy(ownerPassword ?: userPassword, userPassword, AccessPermission()).apply {
                        encryptionKeyLength = 128
                    }
                )
            }
            document.save(file)
        }
        return Uri.fromFile(file)
    }

    private companion object {
        const val USER = "open sesame"
    }
}
