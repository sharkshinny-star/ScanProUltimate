package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sign PDF stamps a picture onto chosen pages of a copy: same page count, an image on exactly those pages, and the
 * original file untouched. Protected files are opened with their password and written out unprotected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class SignPdfEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var stamp: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "sign-pdf-test").apply { deleteRecursively(); mkdirs() }
        stamp = File(dir, "stamp.png")
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.argb(255, 20, 40, 180))
        ImageProcessor.saveSignature(bitmap, stamp)
    }

    @Test
    fun theStampedCopyHasTheSamePagesAndAnImageOnTheSignedPage() = runBlocking {
        val source = pdf("three.pdf", pages = 3)
        val before = source.readBytes()
        val output = File(dir, "signed.pdf")

        val result = SignPdfEngine.stamp(source, listOf(SignPlacement(1, stamp, 0.5f, 0.8f, 0.3f)), output)

        assertEquals(3, result.pageCount)
        assertTrue(output.length() > 0)
        PDDocument.load(output).use { document ->
            assertEquals(3, document.numberOfPages)
            assertFalse(hasImage(document, 0))
            assertTrue(hasImage(document, 1))
            assertFalse(hasImage(document, 2))
        }
        // The file that was signed is left exactly as it was.
        assertTrue(before.contentEquals(source.readBytes()))
    }

    @Test
    fun severalStampsOnSeveralPagesAreAllDrawn() = runBlocking {
        val source = pdf("two.pdf", pages = 2)
        val output = File(dir, "signed.pdf")
        val placements = listOf(
            SignPlacement(0, stamp, 0.3f, 0.9f, 0.2f),
            SignPlacement(0, stamp, 0.7f, 0.9f, 0.2f, rotationDegrees = 15f),
            SignPlacement(1, stamp, 0.5f, 0.5f, 0.4f)
        )

        SignPdfEngine.stamp(source, placements, output)

        PDDocument.load(output).use { document ->
            assertTrue(hasImage(document, 0))
            assertTrue(hasImage(document, 1))
        }
    }

    @Test
    fun aRotatedPageWithAShiftedCropBoxIsStamped() = runBlocking {
        val source = File(dir, "rotated.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(600f, 800f))
            page.cropBox = PDRectangle(10f, 20f, 300f, 400f)
            page.rotation = 90
            document.addPage(page)
            document.save(source)
        }
        val output = File(dir, "signed.pdf")

        SignPdfEngine.stamp(source, listOf(SignPlacement(0, stamp, 0.5f, 0.5f, 0.5f)), output)

        PDDocument.load(output).use { document ->
            assertEquals(1, document.numberOfPages)
            assertEquals(90, document.getPage(0).rotation)
            assertTrue(hasImage(document, 0))
        }
    }

    @Test
    fun nothingPlacedAndAMissingPageAreRefusedWithoutLeavingAFile() {
        val source = pdf("one.pdf", pages = 1)
        val output = File(dir, "signed.pdf")

        assertRefused { SignPdfEngine.stamp(source, emptyList(), output) }
        assertRefused { SignPdfEngine.stamp(source, listOf(SignPlacement(5, stamp, 0.5f, 0.5f, 0.3f)), output) }
        assertFalse(output.exists())
    }

    @Test
    fun anUnprotectedFileIsCopiedAsItIs() = runBlocking {
        val uri = Uri.fromFile(pdf("plain.pdf", pages = 2))

        val copy = SignPdfEngine.prepare(context, uri, null)

        assertNotNull(copy)
        assertFalse(copy!!.wasProtected)
        PDDocument.load(copy.file).use { assertEquals(2, it.numberOfPages) }
    }

    @Test
    fun aPasswordProtectedFileAsksForItsPasswordThenOpensUnprotected() = runBlocking {
        val uri = Uri.fromFile(pdf("locked.pdf", pages = 2, userPassword = "open sesame"))

        assertNull(SignPdfEngine.prepare(context, uri, null))
        try {
            SignPdfEngine.prepare(context, uri, "not it")
            fail("A wrong password should be refused")
        } catch (_: InvalidPasswordException) {
            // expected
        }
        val copy = SignPdfEngine.prepare(context, uri, "open sesame")

        assertNotNull(copy)
        assertTrue(copy!!.wasProtected)
        PDDocument.load(copy.file).use {
            assertFalse(it.isEncrypted)
            assertEquals(2, it.numberOfPages)
        }
    }

    @Test
    fun aFileWithOnlyRestrictionsOpensWithoutAskingAndIsWrittenUnrestricted() = runBlocking {
        val uri = Uri.fromFile(pdf("restricted.pdf", pages = 1, userPassword = "", ownerPassword = "owner only"))

        val copy = SignPdfEngine.prepare(context, uri, null)

        assertNotNull(copy)
        assertTrue(copy!!.wasProtected)
        PDDocument.load(copy.file).use { assertFalse(it.isEncrypted) }
    }

    private fun assertRefused(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
        } catch (_: IllegalArgumentException) {
            return
        }
        fail("This should have been refused")
    }

    private fun hasImage(document: PDDocument, pageIndex: Int): Boolean {
        val resources = document.getPage(pageIndex).resources ?: return false
        return resources.xObjectNames.any { resources.isImageXObject(it) }
    }

    private fun pdf(name: String, pages: Int, userPassword: String? = null, ownerPassword: String? = null): File {
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
        return file
    }
}
