package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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
 * Merge asks for the password of a protected PDF instead of failing with "unlock it first" (F49): the engine stops
 * with [MergePasswordNeededException] naming the file, and merges once it is given the password.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MergePasswordTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var output: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "merge-password-test").apply { deleteRecursively(); mkdirs() }
        output = File(dir, "merged.pdf")
    }

    @Test
    fun aProtectedFileStopsTheMergeToAskForItsPassword() {
        val plain = pdf("plain.pdf", pages = 1)
        val locked = pdf("locked.pdf", pages = 2, userPassword = USER)

        val asked = passwordNeeded(listOf(plain, locked))

        assertEquals(1, asked.index)
        assertEquals(locked, asked.uri)
        assertFalse(asked.wrongPassword)
        assertFalse(output.exists())
        // The copies made for the merge are gone, as after any failed merge.
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("merge_in_") })
    }

    @Test
    fun aWrongPasswordIsReportedAsWrong() {
        val plain = pdf("plain.pdf", pages = 1)
        val locked = pdf("locked.pdf", pages = 2, userPassword = USER)

        val asked = passwordNeeded(listOf(plain, locked), mapOf(locked to "not it"))

        assertEquals(1, asked.index)
        assertTrue(asked.wrongPassword)
    }

    @Test
    fun withItsPasswordTheFileIsMergedAndTheResultOpensWithoutOne() = runBlocking {
        val plain = pdf("plain.pdf", pages = 1)
        val locked = pdf("locked.pdf", pages = 2, userPassword = USER)

        val result = PdfToolsEngine.mergePdfs(context, listOf(plain, locked), output, passwords = mapOf(locked to USER))

        assertEquals(3, result.pageCount)
        PDDocument.load(output).use {
            assertFalse(it.isEncrypted)
            assertEquals(3, it.numberOfPages)
        }
    }

    @Test
    fun theOwnerPasswordOpensItToo() = runBlocking {
        val locked = pdf("locked.pdf", pages = 2, userPassword = USER, ownerPassword = OWNER)
        val plain = pdf("plain.pdf", pages = 1)

        val result = PdfToolsEngine.mergePdfs(context, listOf(locked, plain), output, passwords = mapOf(locked to OWNER))

        assertEquals(3, result.pageCount)
    }

    @Test
    fun twoProtectedFilesAreAskedForInTurn() {
        val first = pdf("first.pdf", pages = 1, userPassword = USER)
        val second = pdf("second.pdf", pages = 1, userPassword = "another one")

        assertEquals(0, passwordNeeded(listOf(first, second)).index)
        val next = passwordNeeded(listOf(first, second), mapOf(first to USER))
        assertEquals(1, next.index)
        assertFalse(next.wrongPassword)
    }

    @Test
    fun aFileWithOnlyEditingRestrictionsIsMergedWithoutAsking() = runBlocking {
        // An owner password alone restricts editing and copying but opens freely; Merge never needed to ask for it.
        val restricted = pdf("restricted.pdf", pages = 2, userPassword = "", ownerPassword = OWNER)
        val plain = pdf("plain.pdf", pages = 1)

        val result = PdfToolsEngine.mergePdfs(context, listOf(restricted, plain), output)

        assertEquals(3, result.pageCount)
    }

    private fun passwordNeeded(uris: List<Uri>, passwords: Map<Uri, String> = emptyMap()): MergePasswordNeededException {
        try {
            runBlocking { PdfToolsEngine.mergePdfs(context, uris, output, passwords = passwords) }
        } catch (asked: MergePasswordNeededException) {
            return asked
        }
        fail("The merge should have asked for a password")
        throw AssertionError()
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
        const val OWNER = "owner only"
    }
}
