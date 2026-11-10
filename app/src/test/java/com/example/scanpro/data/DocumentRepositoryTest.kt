package com.example.scanpro.data

import android.app.Application
import android.content.Context
import com.example.scanpro.imaging.OcrEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [DocumentRepository] on the JVM with a real Room database and real files. Only edits that do not redraw pages are
 * covered here (order, delete, duplicate, undo, trash), because redrawing needs the native image pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DocumentRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: DocumentDatabase
    private lateinit var repository: DocumentRepository

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = DocumentDatabase(context)
        repository = DocumentRepository(context, db, OcrEngine(context))
    }

    @After fun tearDown() {
        File(context.filesDir, "documents").deleteRecursively()
    }

    /** Saves a document of [pageCount] pages whose image files really exist. */
    private fun newDocument(pageCount: Int, title: String = "Doc", trashedAt: Long = 0L): ScanDocument {
        val id = UUID.randomUUID().toString()
        val folder = File(context.filesDir, "documents/$id").apply { mkdirs() }
        File(folder, "source").mkdirs()
        File(folder, "rendered").mkdirs()
        val pages = (0 until pageCount).map { index ->
            val pageId = UUID.randomUUID().toString()
            val source = File(folder, "source/s_$pageId.jpg").apply { writeText("source $index") }
            val image = File(folder, "rendered/p_$pageId.jpg").apply { writeText("page $index") }
            ScanPage(pageId, id, index, source.absolutePath, image.absolutePath, ocrText = "text $index")
        }
        val pdf = File(folder, "document.pdf").apply { writeText("%PDF") }
        val doc = ScanDocument(
            id = id, title = title, createdAt = 1L, updatedAt = 1L, pdfPath = pdf.absolutePath,
            pages = pages, sizeBytes = pdf.length(), isTrashed = trashedAt > 0, trashedAt = trashedAt
        )
        runBlocking(Dispatchers.IO) { db.saveDocument(doc) }
        return doc
    }

    private fun load(id: String): ScanDocument? = runBlocking { repository.loadDocument(id) }
    private fun ScanDocument.order() = pages.sortedBy { it.position }.map { it.id }

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

    @Test fun deletePageRemovesItsFilesAndClearsHistory() = runBlocking {
        val doc = newDocument(3)
        repository.movePage(doc.id, doc.order()[0], delta = 1)
        val target = load(doc.id)!!.pages.first { it.position == 1 }

        repository.deletePage(doc.id, target.id)

        val saved = load(doc.id)!!
        assertEquals(2, saved.pages.size)
        assertFalse(saved.pages.any { it.id == target.id })
        assertEquals(listOf(0, 1), saved.pages.sortedBy { it.position }.map { it.position })
        assertFalse(File(target.imagePath).exists())
        assertFalse(File(target.sourcePath).exists())
        assertNull("history refers to a deleted page", repository.undoState.value[doc.id])
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
        } catch (expected: IllegalStateException) {
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

        assertEquals(1, repository.purgeExpiredTrash(maxAgeDays = 7))

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
            val purge = pool.submit { start.await(); runBlocking { repository.purgeExpiredTrash(7) } }
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
