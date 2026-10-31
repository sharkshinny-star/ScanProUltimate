package com.example.scanpro.feature.library

import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibrarySort

/**
 * Applies the Files filters to an in-memory list, with the same meaning the paged database query gives them:
 * a folder matches ignoring case and surrounding spaces, "favourites only" keeps starred documents, and the
 * sort orders the rest. Used for search results, which are a small list rather than a paged query.
 */
internal fun List<DocumentSummary>.filteredAndSorted(folder: String, favoritesOnly: Boolean, sort: LibrarySort): List<DocumentSummary> {
    val wanted = folder.trim()
    val kept = filter { wanted.isEmpty() || it.folderName.trim().equals(wanted, ignoreCase = true) }
        .filter { !favoritesOnly || it.isFavorite }
    return when (sort) {
        LibrarySort.UPDATED -> kept.sortedByDescending { it.updatedAt }
        LibrarySort.NAME -> kept.sortedBy { it.title.lowercase() }
        LibrarySort.SIZE -> kept.sortedByDescending { it.sizeBytes }
    }
}
