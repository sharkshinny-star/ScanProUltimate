package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.UserFacingException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
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
 * Repair PDF's first step: re-reading a damaged file with the forgiving parser and writing it out clean. Each page's
 * width tells which page it is (100, 200 and 300 wide), so a repaired file can be checked page by page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfRepairEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var output: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "repair-test").apply { deleteRecursively(); mkdirs() }
        output = File(dir, "out.pdf")
    }

    @Test
    fun aHealthyPdfKeepsEveryPage() {
        val source = pdf("healthy.pdf")

        assertEquals(3, PdfRepairEngine.rewriteStructure(source, output))
        assertEquals(listOf(100, 200, 300), widths(output))
    }

    @Test
    fun aPdfWithItsEndCutOffIsRecoveredWithAllItsPages() {
        // The cross-reference table, the trailer and the end marker live at the end of the file: cutting them off is
        // what an interrupted download or a full disk leaves behind.
        val healthy = pdf("healthy.pdf")
        val damaged = File(dir, "damaged.pdf").apply { writeBytes(healthy.readBytes().copyOf(healthy.length().toInt() - 160)) }
        assertTrue("the test file must really be cut short", damaged.length() < healthy.length())

        assertEquals(3, PdfRepairEngine.rewriteStructure(damaged, output))
        assertEquals(listOf(100, 200, 300), widths(output))
    }

    @Test
    fun bytesThatAreNotAPdfGiveNoPagesAndLeaveNoOutput() {
        val garbage = File(dir, "garbage.pdf").apply { writeText("this was never a PDF file") }

        assertEquals(0, PdfRepairEngine.rewriteStructure(garbage, output))
        assertFalse("no half-written file may be left", output.exists())
    }

    @Test
    fun aPasswordProtectedPdfIsRefusedWithAnExplanation() {
        val locked = File(dir, "locked.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(100f, 100f)))
            document.protect(StandardProtectionPolicy("owner", "secret", AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(locked)
        }

        try {
            PdfRepairEngine.rewriteStructure(locked, output)
            fail("a protected PDF cannot be read without its password")
        } catch (expected: UserFacingException) {
            assertFalse(output.exists())
        }
    }

    private fun pdf(name: String): File = File(dir, name).also { file ->
        PDDocument().use { document ->
            listOf(100f, 200f, 300f).forEach { width -> document.addPage(PDPage(PDRectangle(width, 100f))) }
            document.save(file)
        }
    }

    private fun widths(file: File): List<Int> = PDDocument.load(file).use { document ->
        document.pages.map { it.mediaBox.width.toInt() }
    }
}
