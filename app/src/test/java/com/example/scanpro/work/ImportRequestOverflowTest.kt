package com.example.scanpro.work

import androidx.work.workDataOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A large batch's request is too big for WorkManager; it is kept in the scan folder and read back whole. */
class ImportRequestOverflowTest {
    @get:Rule val temp = TemporaryFolder()

    private fun request(pages: Int): ImportRequest = ImportRequest.Builder()
        .putStringArray(ImportScanWorker.KEY_URIS, Array(pages) { "file:///data/user/0/com.example.scanpro/files/pro_scanner_session/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0/raw_${it}_9f8e7d6c-5b4a-3928-1716-051423324150.jpg" })
        .putString(ImportScanWorker.KEY_MODE, "DOCUMENT")
        .putStringArray(ImportScanWorker.KEY_CROPS, Array(pages) { """{"tl":[0.1,0.1],"tr":[0.9,0.1],"br":[0.9,0.9],"bl":[0.1,0.9]}""" })
        .putStringArray(ImportScanWorker.KEY_FILTERS, Array(pages) { "AUTO" })
        .putStringArray(ImportScanWorker.KEY_ADJUSTMENTS, Array(pages) { "" })
        .build()

    @Test
    fun `a hundred pages don't fit WorkManager, a few do`() {
        assertNull(request(100).toWorkData())
        assertNotNull(request(3).toWorkData())
    }

    @Test
    fun `the saved request reads back with every page`() {
        val input = request(100)
        val file = File(temp.root, "request").apply { writeText(input.toJson()) }
        val back = ImportRequest.readFile(file)!!
        assertArrayEquals(input.getStringArray(ImportScanWorker.KEY_URIS), back.getStringArray(ImportScanWorker.KEY_URIS))
        assertArrayEquals(input.getStringArray(ImportScanWorker.KEY_CROPS), back.getStringArray(ImportScanWorker.KEY_CROPS))
        assertEquals("DOCUMENT", back.getString(ImportScanWorker.KEY_MODE))
        assertEquals(100, back.getStringArray(ImportScanWorker.KEY_ADJUSTMENTS)!!.size)
    }

    @Test
    fun `requests saved by the previous version still read`() {
        val small = request(3)
        val file = File(temp.root, "old").apply { writeBytes(small.toWorkData()!!.toByteArray()) }
        assertArrayEquals(small.getStringArray(ImportScanWorker.KEY_URIS), ImportRequest.readFile(file)!!.getStringArray(ImportScanWorker.KEY_URIS))
    }

    @Test
    fun `the worker reads the request from the file it was given`() {
        val input = request(100)
        val file = File(temp.root, "request").apply { writeText(input.toJson()) }
        val expanded = ImportScanWorker.expand(workDataOf(ImportScanWorker.KEY_REQUEST_FILE to file.path))
        assertEquals(100, expanded.getStringArray(ImportScanWorker.KEY_URIS)!!.size)
    }
}
