package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.data.MAX_PAGES
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
 * Organise's "insert pages from another PDF": the result's page order, page ranges, refusals, a protected second PDF,
 * and that the PDFs read are left as they were. Each page's width tells which page it is: the PDF being organised
 * has pages 100, 200 and 300 wide, the one inserted from 1000, 1100 and 1200 wide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InsertPagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var base: Uri
    private lateinit var extra: Uri
    private lateinit var output: File
    private val all = listOf(PageSpec(0), PageSpec(1), PageSpec(2))

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "insert-pages-test").apply { deleteRecursively(); mkdirs() }
        base = pdf("base.pdf", listOf(100f, 200f, 300f))
        extra = pdf("extra.pdf", listOf(1000f, 1100f, 1200f))
        output = File(dir, "out.pdf")
    }

    @Test
    fun allPagesGoAfterTheChosenPage() {
        val result = insert(PageInsertion(extra, null, afterPosition = 1))

        assertEquals(listOf(100, 1000, 1100, 1200, 200, 300), widths(output))
        assertEquals(6, result.pageCount)
    }

    @Test
    fun theyCanGoAtTheStartOrTheEnd() {
        insert(PageInsertion(extra, null, afterPosition = 0))
        assertEquals(listOf(1000, 1100, 1200, 100, 200, 300), widths(output))

        output.delete()
        insert(PageInsertion(extra, null, afterPosition = 3))
        assertEquals(listOf(100, 200, 300, 1000, 1100, 1200), widths(output))
    }

    @Test
    fun aPageRangeTakesOnlyThosePagesInTheOrderGiven() {
        insert(PageInsertion(extra, "3, 1", afterPosition = 2))
        assertEquals(listOf(100, 200, 1200, 1000, 300), widths(output))

        output.delete()
        insert(PageInsertion(extra, "2-3", afterPosition = 0))
        assertEquals(listOf(1100, 1200, 100, 200, 300), widths(output))
    }

    @Test
    fun theOrganisedArrangementCarriesOver() {
        // Page 2 deleted, page 3 first and turned a quarter: the new pages go after the first page of that arrangement.
        val arranged = listOf(PageSpec(2, 90), PageSpec(0))

        insert(PageInsertion(extra, "1", afterPosition = 1), specs = arranged)

        assertEquals(listOf(300, 1000, 100), widths(output))
        PDDocument.load(output).use { assertEquals(90, it.getPage(0).rotation) }
    }

    @Test
    fun theFilesReadAreLeftUntouched() {
        val before = File(base.path!!).readBytes()
        val extraBefore = File(extra.path!!).readBytes()

        insert(PageInsertion(extra, null, afterPosition = 2))

        assertArrayEquals(before, File(base.path!!).readBytes())
        assertArrayEquals(extraBefore, File(extra.path!!).readBytes())
        // The copies made for the work are gone.
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("insert_") })
    }

    @Test
    fun badRangesAndPositionsAreRefusedAndLeaveNoFile() {
        refused(PageInsertion(extra, "4", afterPosition = 1))
        refused(PageInsertion(extra, "0-2", afterPosition = 1))
        refused(PageInsertion(extra, "2-1", afterPosition = 1))
        refused(PageInsertion(extra, "abc", afterPosition = 1))
        refused(PageInsertion(extra, "", afterPosition = 1))
        refused(PageInsertion(extra, "1, 1", afterPosition = 1))
        refused(PageInsertion(extra, "1-2, 2", afterPosition = 1))
        refused(PageInsertion(extra, null, afterPosition = 4))
        refused(PageInsertion(extra, null, afterPosition = -1))
        assertFalse(output.exists())
    }

    @Test
    fun moreThanTheAppHoldsIsRefused() {
        InsertPagesEngine.requireWithinLimit(MAX_PAGES - 3, 3)
        try {
            InsertPagesEngine.requireWithinLimit(MAX_PAGES - 3, 4)
            fail("More than $MAX_PAGES pages should be refused")
        } catch (expected: IllegalArgumentException) {
            // UserFacingPluralException: "A document can hold up to 300 pages".
        }
    }

    @Test
    fun theLayoutPutsTheNewPagesAfterTheChosenOne() {
        val slots = InsertPagesEngine.layout(baseCount = 2, insertedCount = 2, after = 1)

        assertEquals(
            listOf(
                InsertPagesEngine.Slot.Base(0),
                InsertPagesEngine.Slot.Inserted(0),
                InsertPagesEngine.Slot.Inserted(1),
                InsertPagesEngine.Slot.Base(1)
            ),
            slots
        )
    }

    @Test
    fun pagesToInsertReadsRangesOneBasedAndAllWhenNull() {
        assertEquals(listOf(0, 1, 2), InsertPagesEngine.pagesToInsert(null, 3))
        assertEquals(listOf(2, 0, 1), InsertPagesEngine.pagesToInsert("3, 1-2", 3))
    }

    @Test
    fun aProtectedSecondPdfAsksForItsPasswordThenInsertsAndSaysNothingIsProtected() {
        val locked = pdf("locked.pdf", listOf(1000f, 1100f), userPassword = "open sesame")

        val none = passwordNeeded(PageInsertion(locked, null, 1), null)
        assertEquals(locked, none.uri)
        assertFalse(none.wrongPassword)
        assertTrue(passwordNeeded(PageInsertion(locked, null, 1), "not it").wrongPassword)
        assertFalse(output.exists())

        val result = insert(PageInsertion(locked, null, afterPosition = 1), password = "open sesame")

        assertEquals(listOf(100, 1000, 1100, 200, 300), widths(output))
        PDDocument.load(output).use { assertFalse(it.isEncrypted) }
        assertEquals(5, result.pageCount)
    }

    private fun insert(
        insertion: PageInsertion,
        specs: List<PageSpec> = all,
        password: String? = null
    ): PdfToolResult = runBlocking { InsertPagesEngine.insertPages(context, base, specs, insertion, output, password) }

    private fun refused(insertion: PageInsertion) {
        try {
            insert(insertion)
        } catch (expected: IllegalArgumentException) {
            // UserFacingException or UserFacingPluralException: both are IllegalArgumentExceptions.
            return
        }
        fail("Inserting $insertion should have been refused")
    }

    private fun passwordNeeded(insertion: PageInsertion, password: String?): PdfPasswordNeededException {
        try {
            insert(insertion, password = password)
        } catch (asked: PdfPasswordNeededException) {
            return asked
        }
        fail("The tool should have asked for a password")
        throw AssertionError()
    }

    private fun widths(file: File): List<Int> =
        PDDocument.load(file).use { document -> (0 until document.numberOfPages).map { document.getPage(it).mediaBox.width.toInt() } }

    private fun pdf(name: String, pageWidths: List<Float>, userPassword: String? = null): Uri {
        val file = File(dir, name)
        PDDocument().use { document ->
            pageWidths.forEach { document.addPage(PDPage(PDRectangle(it, 100f))) }
            if (userPassword != null) {
                document.protect(
                    StandardProtectionPolicy(userPassword, userPassword, AccessPermission()).apply { encryptionKeyLength = 128 }
                )
            }
            document.save(file)
        }
        return Uri.fromFile(file)
    }
}
