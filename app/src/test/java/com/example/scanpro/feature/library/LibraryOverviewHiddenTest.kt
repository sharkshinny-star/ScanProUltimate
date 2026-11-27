package com.example.scanpro.feature.library

import com.example.scanpro.data.DocumentFacet
import com.example.scanpro.data.LibraryOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** What a closed locked folder must not give away through the library's tags and counts. */
class LibraryOverviewHiddenTest {
    private val overview = LibraryOverview(
        documentCount = 4,
        favoriteCount = 2,
        folders = listOf("Private", "Work"),
        tags = listOf("divorce", "invoice", "tax"),
        facets = listOf(
            DocumentFacet("Private", favorite = true, tags = listOf("divorce")),
            DocumentFacet("Work", favorite = false, tags = listOf("invoice", "tax")),
            DocumentFacet("", favorite = true, tags = listOf("Tax")),
            DocumentFacet("Work", favorite = false, tags = emptyList())
        )
    )

    @Test
    fun nothingIsChangedWhenNoFolderIsClosed() {
        assertSame(overview, overview.withoutHidden(emptySet()))
    }

    @Test
    fun aClosedFolderLosesItsTagsAndItsShareOfTheCounts() {
        val shown = overview.withoutHidden(setOf("private"))
        assertEquals(listOf("invoice", "tax"), shown.tags)
        assertEquals(3, shown.documentCount)
        assertEquals(1, shown.favoriteCount)
        // The chips stay: a locked folder is shown as locked on purpose.
        assertEquals(listOf("Private", "Work"), shown.folders)
    }

    @Test
    fun aTagUsedOutsideTheFolderStaysOnceWhateverItsSpelling() {
        // "tax" on a Work document and "Tax" on an unfiled one are one tag.
        val shown = overview.withoutHidden(setOf("work"))
        assertEquals(listOf("divorce", "Tax"), shown.tags.sortedBy { it.lowercase() })
        assertEquals(2, shown.documentCount)
    }

    @Test
    fun beforeTheLockedListHasLoadedNoFolderedDocumentCounts() {
        // The "everything hidden" marker FolderLock uses until its saved list is read: only unfiled documents remain.
        val shown = overview.withoutHidden(setOf("\u0000all"))
        assertEquals(1, shown.documentCount)
        assertEquals(listOf("Tax"), shown.tags)
    }
}
