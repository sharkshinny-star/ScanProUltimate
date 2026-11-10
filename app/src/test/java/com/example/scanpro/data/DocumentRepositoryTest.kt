package com.example.scanpro.data

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [DocumentRepository] on the JVM with a real Room database and real files: metadata, order, delete, duplicate,
 * undo and trash. Edits that redraw pages are in [DocumentRepositoryRedrawTest].
 */
class DocumentRepositoryTest : RepositoryTestBase() {

    @Test fun metadataEditsAreSavedWithoutTouchingPages() = runBlocking {
        val doc = newDocument(2)
        repository.rename(doc.id, "  New name ")
        repository.setTags(doc.id, "Tax, #receipts, tax, ")
        repository.setFolder(doc.id, "  Work ")
        repository.toggleFavorite(doc.id)
        val saved = load(doc.id)!!
        assertEquals("New name", saved.title)
        assertEquals(listOf("Tax", "receipts"), saved.tags)
        assertEquals("Work", saved.folderName)
        assertTrue(saved.isFavorite)
        assertEquals(doc.order(), saved.order())
    }

    @Test fun movePageReordersAndRenumbersAndMarksThePdfStale() = runBlocking {
        val doc = newDocument(3)
        val first = doc.order()
        repository.movePage(doc.id, first[0], delta = 2)
        val saved = load(doc.id)!!
        assertEquals(listOf(first[1], first[2], first[0]), saved.order())
        assertEquals(listOf(0, 1, 2), saved.pages.sortedBy { it.position }.map { it.position })
        assertFalse(saved.hasCurrentPdf())
    }

    @Test fun movePageBeyondTheEndStaysPutAndRecordsNothing() = runBlocking {
        val doc = newDocument(2)
        repository.movePage(doc.id, doc.order()[1], delta = 5)
        assertEquals(doc.order(), load(doc.id)!!.order())
        assertNull(repository.undoState.value[doc.id])
    }

    @Test fun undoAndRedoRestoreThePageOrder() = runBlocking {
        val doc = newDocument(3)
        val original = doc.order()
        repository.movePage(doc.id, original[0], delta = 1)
        val moved = load(doc.id)!!.order()
        assertEquals(1, repository.undoState.value[doc.id]?.undoSteps)

        assertTrue(repository.undo(doc.id))
        assertEquals(original, load(doc.id)!!.order())
        assertEquals(1, repository.undoState.value[doc.id]?.redoSteps)

        assertTrue(repository.redo(doc.id))
        assertEquals(moved, load(doc.id)!!.order())
        assertFalse(repository.redo(doc.id))
    }

    @Test fun undoWithNothingToUndoReturnsFalse() = runBlocking {
        assertFalse(repository.undo(newDocument(1).id))
    }

    @Test fun deletePageKeepsItsFilesUntilTheUndoWindowClosesAndClearsHistory() = runBlocking {
        val doc = newDocument(3)
        repository.movePage(doc.id, doc.order()[0], delta = 1)
        val target = load(doc.id)!!.pages.first { it.position == 1 }

        repository.deletePage(doc.id, target.id)

        val saved = load(doc.id)!!
        assertEquals(2, saved.pages.size)
        assertFalse(saved.pages.any { it.id == target.id })
        assertEquals(listOf(0, 1), saved.pages.sortedBy { it.position }.map { it.position })
        assertNull("history refers to a deleted page", repository.undoState.value[doc.id])
        // The files stay while the "Page removed" message can still undo the removal ...
        assertTrue(File(target.imagePath).exists())
        assertTrue(File(target.sourcePath).exists())
        // ... and go once the removal is purged (the message is dismissed or another page is removed).
        repository.purgeRemovedPage(doc.id, target.id)
        assertFalse(File(target.imagePath).exists())
        assertFalse(File(target.sourcePath).exists())
    }

    @Test fun aDeletedPageCanBeBroughtBackInPlace() = runBlocking {
        val doc = newDocument(3)
        val before = doc.order()

        repository.deletePage(doc.id, before[1])
        assertEquals(listOf(before[0], before[2]), load(doc.id)!!.order())

        assertTrue(repository.restoreRemovedPage(doc.id))
        assertEquals(before, load(doc.id)!!.order())
        assertFalse("nothing is left to restore", repository.restoreRemovedPage(doc.id))
    }

    @Test fun deletingTheOnlyPageMovesTheDocumentToTheTrash() = runBlocking {
        val doc = newDocument(1)
        repository.deletePage(doc.id, doc.pages.single().id)
        val saved = load(doc.id)!!
        assertTrue(saved.isTrashed)
        assertEquals(1, saved.pages.size)
        assertTrue(File(saved.pages.single().imagePath).exists())
    }

    @Test fun deletingAnUnknownPageFailsAndChangesNothing() = runBlocking {
        val doc = newDocument(2)
        try {
            repository.deletePage(doc.id, "nope")
            fail("expected an error")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(doc.order(), load(doc.id)!!.order())
    }

    @Test fun duplicatePageInsertsACopyWithItsOwnFiles() = runBlocking {
        val doc = newDocument(2)
        val original = doc.order()
        repository.duplicatePage(doc.id, original[0])
        val saved = load(doc.id)!!
        assertEquals(3, saved.pages.size)
        val ordered = saved.pages.sortedBy { it.position }
        assertEquals(original[0], ordered[0].id)
        assertEquals(original[1], ordered[2].id)
        val copy = ordered[1]
        assertTrue(copy.id !in original)
        assertEquals("text 0", copy.ocrText)
        assertEquals("page 0", File(copy.imagePath).readText())
        assertTrue(copy.imagePath != ordered[0].imagePath)
    }

    @Test fun trashRestoreRoundTrip() = runBlocking {
        val doc = newDocument(1)
        repository.moveToTrash(doc.id)
        assertEquals(listOf(doc.id), repository.loadTrashSummaries().map { it.id })
        repository.restore(doc.id)
        assertFalse(load(doc.id)!!.isTrashed)
        assertTrue(repository.loadTrashSummaries().isEmpty())
    }

    @Test fun purgeDeletesOnlyExpiredTrashAndItsFiles() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val old = newDocument(1, trashedAt = now - 10 * day)
        val recent = newDocument(1, trashedAt = now - 1 * day)
        val active = newDocument(1)

        assertEquals(1, repository.purgeExpiredTrash())

        assertNull(load(old.id))
        assertFalse(File(old.pdfPath).parentFile!!.exists())
        assertNotNull(load(recent.id))
        assertNotNull(load(active.id))
    }

    @Test fun aDocumentRestoredDuringAPurgeIsNeverDeleted() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val docs = (1..24).map { newDocument(1, trashedAt = now - 10 * day) }
        val outcome = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val purge = pool.submit { start.await(); runBlocking { repository.purgeExpiredTrash() } }
            val restore = pool.submit {
                start.await()
                runBlocking {
                    docs.forEach { doc ->
                        // A restore that loses the race fails with "Document not found"; one that wins must stick.
                        outcome[doc.id] = runCatching { repository.restore(doc.id) }.isSuccess
                    }
                }
            }
            start.countDown()
            purge.get(); restore.get()
        } finally {
            pool.shutdownNow()
        }
        docs.forEach { doc ->
            if (outcome.getValue(doc.id)) assertNotNull("restored ${doc.id} was purged", load(doc.id))
        }
    }

    @Test fun emptyTrashRemovesTrashedDocumentsOnly() = runBlocking {
        val trashed = newDocument(1, trashedAt = System.currentTimeMillis())
        val active = newDocument(1)
        repository.emptyTrash()
        assertNull(load(trashed.id))
        assertNotNull(load(active.id))
        withContext(Dispatchers.IO) { assertEquals(listOf(active.id), db.loadAll().map { it.id }) }
    }
}
