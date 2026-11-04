package com.example.scanpro.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search against the real Room database, including the FTS4 `snippet()` query behind result snippets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocumentSearchSnippetTest {
    private val db = DocumentDatabase(ApplicationProvider.getApplicationContext())

    /** Room refuses the main thread, which is the test's thread under Robolectric. */
    private fun <T> io(block: () -> T): T {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            return executor.submit(java.util.concurrent.Callable(block)).get()
        } finally {
            executor.shutdown()
        }
    }

    private fun search(query: String) = io { db.search(query) }

    private fun save(id: String, title: String, text: String, updatedAt: Long = 1L) = io { db.saveDocument(
        ScanDocument(
            id = id, title = title, createdAt = 1L, updatedAt = updatedAt, pdfPath = "", sizeBytes = 0L,
            pages = listOf(ScanPage(id = "$id-p0", documentId = id, position = 0, sourcePath = "", imagePath = "", ocrText = text))
        )
    ) }

    @Test
    fun `a word found only in the page text comes with that text, the word marked`() {
        save("a", "Invoice 4471", "ACME SUPPLIES LTD\nBill to: Northwind Studio\nTotal due: 189.50")
        val hit = search("northwind").single()
        assertEquals("a", hit.id)
        assertNotNull(hit.matchSnippet)
        val snippet = hit.matchSnippet!!
        assertTrue(snippet, snippet.contains("${SNIPPET_MATCH_START}Northwind$SNIPPET_MATCH_END"))
        // Line breaks from OCR are folded into spaces, so the snippet reads as one line.
        assertTrue(snippet, '\n' !in snippet)
    }

    @Test
    fun `a title match needs no snippet`() {
        save("a", "Invoice 4471", "Bill to: Northwind Studio")
        assertNull(search("invoice").single().matchSnippet)
    }

    @Test
    fun `a prefix matches and every word must be found`() {
        save("a", "Lease agreement", "Tenant: Priya Raman, flat 3B")
        save("b", "Receipt", "Blue Bottle coffee", updatedAt = 2L)
        val hit = search("lease pri").single()
        assertEquals("a", hit.id)
        assertTrue(hit.matchSnippet!!.contains("${SNIPPET_MATCH_START}Priya$SNIPPET_MATCH_END"))
        assertTrue(search("lease coffee").isEmpty())
    }
}
