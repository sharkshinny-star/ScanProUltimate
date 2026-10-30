package com.example.scanpro.feature.library

import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFiltersTest {
    private fun doc(id: String, title: String, updatedAt: Long, size: Long, folder: String = "", favorite: Boolean = false) =
        DocumentSummary(
            id = id, title = title, createdAt = 0, updatedAt = updatedAt, pdfPath = "", pageCount = 1, coverPath = null,
            sizeBytes = size, isFavorite = favorite, folderName = folder
        )

    private val docs = listOf(
        doc("a", "Banana receipt", updatedAt = 30, size = 100, folder = "Receipts"),
        doc("b", "apple contract", updatedAt = 10, size = 900, folder = " receipts ", favorite = true),
        doc("c", "Cherry notes", updatedAt = 20, size = 500, folder = "Notes", favorite = true),
        doc("d", "Date ticket", updatedAt = 40, size = 300)
    )

    private fun ids(list: List<DocumentSummary>) = list.map { it.id }

    @Test
    fun `no filters keeps everything in the chosen order`() {
        assertEquals(listOf("d", "a", "c", "b"), ids(docs.filteredAndSorted("", false, LibrarySort.UPDATED)))
        assertEquals(listOf("b", "a", "c", "d"), ids(docs.filteredAndSorted("", false, LibrarySort.NAME)))
        assertEquals(listOf("b", "c", "d", "a"), ids(docs.filteredAndSorted("", false, LibrarySort.SIZE)))
    }

    @Test
    fun `names sort ignoring case`() {
        // "apple" (lower case) comes before "Banana" (upper case).
        assertEquals("b", docs.filteredAndSorted("", false, LibrarySort.NAME).first().id)
    }

    @Test
    fun `a folder matches ignoring case and surrounding spaces`() {
        assertEquals(listOf("a", "b"), ids(docs.filteredAndSorted("RECEIPTS", false, LibrarySort.UPDATED)))
        assertEquals(listOf("a", "b"), ids(docs.filteredAndSorted("  receipts ", false, LibrarySort.UPDATED)))
        assertEquals(listOf("c"), ids(docs.filteredAndSorted("Notes", false, LibrarySort.UPDATED)))
    }

    @Test
    fun `favorites only keeps starred documents`() {
        assertEquals(listOf("c", "b"), ids(docs.filteredAndSorted("", true, LibrarySort.UPDATED)))
    }

    @Test
    fun `folder and favorites combine`() {
        assertEquals(listOf("b"), ids(docs.filteredAndSorted("receipts", true, LibrarySort.UPDATED)))
    }

    @Test
    fun `a folder with nothing in it gives an empty list`() {
        assertEquals(emptyList<String>(), ids(docs.filteredAndSorted("Taxes", false, LibrarySort.UPDATED)))
    }
}
