package com.example.scanpro.feature.library

import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibrarySort

/**
 * Applies the Files filters to an in-memory list, with the same meaning the paged database query gives them:
 * a folder matches ignoring case and surrounding spaces, "favourites only" keeps starred documents, and the
 * sort orders the rest. Used for search results, which are a small list rather than a paged query.
 */
internal fun List<DocumentSummary>.filteredAndSorted(
    folder: String,
    favoritesOnly: Boolean,
    sort: LibrarySort,
    ascending: Boolean = sort.defaultAscending,
    tag: String = ""
): List<DocumentSummary> {
    val wanted = folder.trim()
    val wantedTag = tag.trim()
    val kept = filter { wanted.isEmpty() || it.folderName.trim().equals(wanted, ignoreCase = true) }
        .filter { !favoritesOnly || it.isFavorite }
        .filter { wantedTag.isEmpty() || it.tags.any { t -> t.trim().equals(wantedTag, ignoreCase = true) } }
    val ordered = when (sort) {
        LibrarySort.UPDATED -> kept.sortedBy { it.updatedAt }
        LibrarySort.CREATED -> kept.sortedBy { it.createdAt }
        LibrarySort.NAME -> kept.sortedBy { it.title.lowercase() }
        LibrarySort.SIZE -> kept.sortedBy { it.sizeBytes }
    }
    return if (ascending) ordered else ordered.reversed()
}
