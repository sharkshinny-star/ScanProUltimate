package com.example.scanpro.data

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Regression tests for the 2026-11-11 bug hunt (F134, F135). */
class RepositoryBugFixesTest : RepositoryTestBase() {

    @Test fun stoppingAnEditWhileItsTextIsReadDoesNotSaveIt() = runBlocking {
        val doc = newDocument(2)
        val page = doc.page(0)
        ocr.readFailure = CancellationException("Stop")

        try {
            repository.applyFilter(doc.id, page.id, PageFilter.BW)
            fail("the cancellation must reach the operation runner")
        } catch (expected: CancellationException) {
            // runCatching around recognition used to turn this into "no text" and save the edit anyway.
        }

        val after = load(doc.id)!!.page(0)
        assertEquals(page.filter, after.filter)
        assertEquals("text 0", after.ocrText)
    }

    @Test fun aFailedReadStillSavesTheEditWithoutNewText() = runBlocking {
        val doc = newDocument(1)
        ocr.failing = true

        repository.applyFilter(doc.id, doc.page(0).id, PageFilter.BW)

        val after = load(doc.id)!!.page(0)
        assertEquals(PageFilter.BW, after.filter)
        assertEquals("text 0", after.ocrText)
    }

    @Test fun aRemovedPageWaitingForUndoKeepsItsSignature() = runBlocking {
        val doc = newDocument(2)
        val folder = File(doc.pdfPath).parentFile!!
        val signature = File(folder, "signatures/sig_1.png").apply { parentFile!!.mkdirs(); writeText("ink") }
        val signed = doc.page(1).copy(
            annotations = listOf(
                PageAnnotation(type = AnnotationType.SIGNATURE, x = 0.1f, y = 0.1f, width = 0.2f, height = 0.1f, assetPath = signature.absolutePath)
            )
        )
        withContext(Dispatchers.IO) { db.saveDocument(doc.copy(pages = listOf(doc.page(0), signed))) }

        repository.deletePage(doc.id, signed.id)
        // Leaving the document while "Page removed · Undo" is still showing.
        repository.cleanUnusedSignatures(doc.id)
        assertTrue("the Undo page's signature must survive", signature.exists())

        assertTrue(repository.restoreRemovedPage(doc.id))
        assertEquals(signature.absolutePath, load(doc.id)!!.page(1).annotations.single().assetPath)
    }

    @Test fun aSignatureNobodyUsesIsStillCleanedUp() = runBlocking {
        val doc = newDocument(1)
        val folder = File(doc.pdfPath).parentFile!!
        val orphan = File(folder, "signatures/sig_old.png").apply { parentFile!!.mkdirs(); writeText("ink") }

        repository.cleanUnusedSignatures(doc.id)

        assertFalse(orphan.exists())
    }
}
