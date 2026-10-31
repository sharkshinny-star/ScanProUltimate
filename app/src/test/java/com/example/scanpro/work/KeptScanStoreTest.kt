package com.example.scanpro.work

import androidx.work.Data
import com.example.scanpro.data.ScanMode
import com.example.scanpro.scanner.SESSION_IMPORT_FAILED
import com.example.scanpro.scanner.SESSION_IMPORT_REQUEST
import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KeptScanStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private fun session(root: File, name: String, photos: Int, pending: Boolean = true): File =
        File(root, name).apply {
            mkdirs()
            repeat(photos) { File(this, "raw_$it.jpg").writeText("photo $it") }
            if (pending) File(this, SESSION_PENDING_IMPORT).createNewFile()
        }

    private fun uri(file: File) = "file://${file.absolutePath}"

    private fun request(session: File, photos: List<File>, mode: ScanMode, crops: List<String>, filters: List<String>) {
        val data = Data.Builder()
            .putStringArray(ImportScanWorker.KEY_URIS, photos.map(::uri).toTypedArray())
            .putString(ImportScanWorker.KEY_MODE, mode.name)
            .putStringArray(ImportScanWorker.KEY_CROPS, crops.toTypedArray())
            .putStringArray(ImportScanWorker.KEY_FILTERS, filters.toTypedArray())
            .build()
        File(session, SESSION_IMPORT_REQUEST).writeBytes(data.toByteArray())
    }

    @Test
    fun `only sessions waiting for an import that is not running are listed`() {
        val root = temp.newFolder("sessions")
        session(root, "abandoned", photos = 2, pending = false)
        session(root, "running", photos = 2)
        session(root, "kept", photos = 2)
        session(root, "empty", photos = 0)
        val kept = KeptScanStore.collect(root, activeSessions = setOf("running"))
        assertEquals(listOf("kept"), kept.map { it.id })
        assertEquals(2, kept.single().photos.size)
        assertEquals(ScanMode.DOCUMENT, kept.single().mode)
        assertNull(kept.single().failure)
    }

    @Test
    fun `the recorded request gives the mode, photo order and last error`() {
        val root = temp.newFolder("sessions")
        val dir = session(root, "s", photos = 3)
        val photos = listOf(File(dir, "raw_2.jpg"), File(dir, "raw_0.jpg"), File(dir, "raw_1.jpg"))
        request(dir, photos, ScanMode.RECEIPT, listOf("c2", "c0", "c1"), listOf("f2", "f0", "f1"))
        File(dir, SESSION_IMPORT_FAILED).writeText("Out of memory\n")
        val scan = KeptScanStore.collect(root, emptySet()).single()
        assertEquals(ScanMode.RECEIPT, scan.mode)
        assertEquals(photos.map { it.name }, scan.photos.map { it.name })
        assertEquals("Out of memory", scan.failure)
        assertTrue(scan.expiresAt > scan.keptAt)
    }

    @Test
    fun `a retry keeps each remaining photo's outline and filter`() {
        val dir = session(temp.newFolder("sessions"), "s", photos = 3)
        val photos = (0..2).map { File(dir, "raw_$it.jpg") }
        request(dir, photos, ScanMode.BOOK, listOf("c0", "c1", "c2"), listOf("f0", "f1", "f2"))
        photos[1].delete()
        val input = KeptScanStore.retryInput(dir, listOf(photos[0], photos[2]))
        assertArrayEquals(arrayOf(uri(photos[0]), uri(photos[2])), input.getStringArray(ImportScanWorker.KEY_URIS))
        assertArrayEquals(arrayOf("c0", "c2"), input.getStringArray(ImportScanWorker.KEY_CROPS))
        assertArrayEquals(arrayOf("f0", "f2"), input.getStringArray(ImportScanWorker.KEY_FILTERS))
        assertEquals(ScanMode.BOOK.name, input.getString(ImportScanWorker.KEY_MODE))
    }

    @Test
    fun `without a recorded request every photo is sent for the importer to outline`() {
        val dir = session(temp.newFolder("sessions"), "s", photos = 2)
        val photos = (0..1).map { File(dir, "raw_$it.jpg") }
        val input = KeptScanStore.retryInput(dir, photos)
        assertArrayEquals(photos.map(::uri).toTypedArray(), input.getStringArray(ImportScanWorker.KEY_URIS))
        assertNull(input.getStringArray(ImportScanWorker.KEY_CROPS))
        assertEquals(ScanMode.DOCUMENT.name, input.getString(ImportScanWorker.KEY_MODE))
    }
}
