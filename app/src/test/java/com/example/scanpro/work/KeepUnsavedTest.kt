package com.example.scanpro.work

import android.app.Application
import android.net.Uri
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanMode
import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import com.example.scanpro.scanner.SESSION_ROOT
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Add page and Retake photos that could not be added to their document stay under "Unsaved scans". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KeepUnsavedTest {
    @get:Rule val temp = TemporaryFolder()

    private fun cameraSession(photos: Int): Pair<File, List<Uri>> {
        val root = File(temp.root, SESSION_ROOT).apply { mkdirs() }
        val session = File(root, "add-page").apply { mkdirs() }
        val uris = (0 until photos).map { i -> Uri.fromFile(File(session, "raw_$i.jpg").apply { writeText("photo $i") }) }
        return root to uris
    }

    @Test
    fun `photos whose save failed are listed with the reason`() {
        val (root, uris) = cameraSession(photos = 3)
        val input = ImportScanWorker.inputFor(uris, ScanMode.DOCUMENT, List(3) { CropQuad.DEFAULT }, List(3) { PageFilter.BW })

        ImportScanWorker.keepUnsaved(input, IllegalStateException("Out of memory"))

        val kept = KeptScanStore.collect(root, activeSessions = emptySet()).single()
        assertEquals(listOf("raw_0.jpg", "raw_1.jpg", "raw_2.jpg"), kept.photos.map { it.name })
        assertEquals("Out of memory", kept.failure)
    }

    @Test
    fun `photos from a stopped job are kept without a failure`() {
        val (root, uris) = cameraSession(photos = 1)
        val input = ImportScanWorker.inputFor(uris, ScanMode.DOCUMENT, listOf(CropQuad.DEFAULT), listOf(PageFilter.ORIGINAL))

        ImportScanWorker.keepUnsaved(input, error = null)

        val kept = KeptScanStore.collect(root, activeSessions = emptySet()).single()
        assertEquals(1, kept.photos.size)
        assertNull(kept.failure)
    }

    @Test
    fun `a folder outside the scanner sessions is left alone`() {
        val elsewhere = temp.newFolder("Pictures")
        val photo = Uri.fromFile(File(elsewhere, "a.jpg").apply { writeText("x") })
        val input = ImportScanWorker.inputFor(listOf(photo), ScanMode.DOCUMENT, listOf(CropQuad.DEFAULT), listOf(PageFilter.ORIGINAL))

        ImportScanWorker.keepUnsaved(input, IllegalStateException("boom"))

        assertFalse(File(elsewhere, SESSION_PENDING_IMPORT).exists())
        assertTrue(File(elsewhere, "a.jpg").exists())
    }
}
