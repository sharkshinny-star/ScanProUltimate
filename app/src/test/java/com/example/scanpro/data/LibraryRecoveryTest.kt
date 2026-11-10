package com.example.scanpro.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Documents whose files are on the device but whose records are gone come back from their folders. */
class LibraryRecoveryTest : RepositoryTestBase() {
    private val root get() = File(context.filesDir, "documents")

    /** A document folder as the app writes it, with no database record. */
    private fun orphan(
        pages: List<Pair<String, String>>,
        id: String = UUID.randomUUID().toString(),
        withSources: Boolean = true
    ): String {
        val folder = File(root, id).apply { mkdirs() }
        File(folder, "rendered").mkdirs()
        File(folder, "source").mkdirs()
        pages.forEach { (renderedName, sourceName) ->
            File(folder, "rendered/$renderedName").writeText("rendered $renderedName")
            if (withSources) File(folder, "source/$sourceName").writeText("source $sourceName")
        }
        File(folder, "document.pdf").writeText("%PDF")
        return id
    }

    private fun pageId() = UUID.randomUUID().toString()

    @Test fun aFolderWithoutARecordComesBackWithItsPagesInOrder() = runBlocking {
        val a = pageId(); val b = pageId(); val c = pageId()
        // Written out of order on purpose: the number in the name decides.
        val id = orphan(
            listOf(
                "page_002_$c.jpg" to "source_002_$c",
                "page_000_$a.jpg" to "source_000_$a.jpg",
                "page_001_$b.jpg" to "source_001_$b"
            )
        )

        assertEquals(1, repository.recoverOrphanedDocuments())

        val doc = load(id)!!
        assertEquals(listOf(a, b, c), doc.order())
        assertEquals(listOf(0, 1, 2), doc.pages.sortedBy { it.position }.map { it.position })
        assertTrue(doc.page(1).sourcePath.endsWith("source_001_$b"))
        assertTrue(File(doc.page(1).imagePath).isFile)
        assertFalse(doc.isTrashed)
        assertEquals("", doc.ocrText)
        // The PDF is rebuilt when it is next needed.
        assertFalse(doc.hasCurrentPdf())
        assertTrue(repository.documentsNeedingPdf().contains(id))
    }

    @Test fun aPageWithoutItsPhotoUsesTheRenderedImageAsItsSource() = runBlocking {
        val a = pageId()
        val id = orphan(listOf("page_000_$a.jpg" to "unused"), withSources = false)
        repository.recoverOrphanedDocuments()
        val page = load(id)!!.pages.single()
        assertEquals(page.imagePath, page.sourcePath)
    }

    @Test fun splitHalvesAreRecoveredToo() = runBlocking {
        val left = pageId(); val right = pageId()
        val id = orphan(listOf("book_$left.jpg" to "book_$left.jpg", "book_$right.jpg" to "book_$right.jpg"))
        repository.recoverOrphanedDocuments()
        assertEquals(setOf(left, right), load(id)!!.pages.map { it.id }.toSet())
    }

    @Test fun documentsThatAlreadyHaveARecordAreLeftAlone() = runBlocking {
        val known = newDocument(2, title = "Mine")
        orphan(listOf("page_000_${pageId()}.jpg" to "s"))

        assertEquals(1, repository.recoverOrphanedDocuments())

        val after = load(known.id)!!
        assertEquals("Mine", after.title)
        assertEquals(known.order(), after.order())
    }

    @Test fun aSecondRunFindsNothingMore() = runBlocking {
        orphan(listOf("page_000_${pageId()}.jpg" to "s"))
        assertEquals(1, repository.recoverOrphanedDocuments())
        assertEquals(0, repository.recoverOrphanedDocuments())
    }

    @Test fun foldersWithNoPageImagesAreSkipped() = runBlocking {
        val empty = UUID.randomUUID().toString()
        File(root, empty).mkdirs()
        val junk = orphan(emptyList())
        File(root, "$junk/rendered/notes.txt").writeText("not a page")
        assertEquals(0, repository.recoverOrphanedDocuments())
        assertNull(load(empty))
        assertNull(load(junk))
    }

    @Test fun recoveredDocumentsAppearInTheLibrary() = runBlocking {
        val id = orphan(listOf("page_000_${pageId()}.jpg" to "s"))
        repository.recoverOrphanedDocuments()
        assertNotNull(repository.search("").firstOrNull { it.id == id })
    }
}
