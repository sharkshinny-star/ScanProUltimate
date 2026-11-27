package com.example.scanpro.feature.document

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Save PDF: the picker has already created the destination, so every way of not saving must delete it again. */
class SavePdfCopyTest {
    private var deleted = 0
    private val delete: suspend () -> Unit = { deleted++ }

    @Test
    fun `a saved copy keeps the destination`() = runBlocking {
        var copiedFrom = ""
        val saved = savePdfCopy({ "/docs/a/document.pdf" }, { path -> copiedFrom = path; true }, delete)
        assertTrue(saved)
        assertEquals("/docs/a/document.pdf", copiedFrom)
        assertEquals(0, deleted)
    }

    @Test
    fun `a PDF that cannot be rebuilt deletes the empty destination`() = runBlocking {
        val saved = savePdfCopy({ throw IllegalStateException("render failed") }, { true }, delete)
        assertFalse(saved)
        assertEquals(1, deleted)
    }

    @Test
    fun `a copy that fails deletes the destination`() = runBlocking {
        assertFalse(savePdfCopy({ "/docs/a/document.pdf" }, { false }, delete))
        assertEquals(1, deleted)
        assertFalse(savePdfCopy({ "/docs/a/document.pdf" }, { throw java.io.IOException("disk full") }, delete))
        assertEquals(2, deleted)
    }

    @Test
    fun `stopping the job deletes the destination and still stops`() = runBlocking {
        try {
            savePdfCopy({ throw CancellationException("Stop") }, { true }, delete)
            fail("the cancellation must not be swallowed")
        } catch (expected: CancellationException) {
            assertEquals(1, deleted)
        }
    }
}
