package com.example.scanpro.data

import androidx.annotation.StringRes
import com.example.scanpro.R

/**
 * What the library, trash and pickers need to show a document: everything in [ScanDocument] except the
 * pages. Pages carry OCR text and annotations, so a library of a few thousand pages held as full documents
 * costs tens of megabytes and a slow load; summaries come from one query and stay small.
 */
data class DocumentSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pdfPath: String,
    val pageCount: Int,
    /** The first page's rendered image, for thumbnails; null for a document without pages. */
    val coverPath: String?,
    val sizeBytes: Long,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val folderName: String = "",
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L,
    /**
     * Search results only: a few words of page text around a word that matched there and nowhere in the title,
     * folder or tags, with each matched word between [SNIPPET_MATCH_START] and [SNIPPET_MATCH_END]. Null otherwise.
     */
    val matchSnippet: String? = null
)

/** Start and end of a matched word in [DocumentSummary.matchSnippet]: private-use characters, never in OCR text. */
const val SNIPPET_MATCH_START = "\uE000"
const val SNIPPET_MATCH_END = "\uE001"

/**
 * How the Files list is ordered; each value is one SQL ordering of the paged query. [defaultAscending] is the
 * direction people expect for the key (A to Z, newest first, largest first); the menu offers the other one too, with
 * [ascendingLabel] / [descendingLabel] naming them in the key's own words (R35).
 */
enum class LibrarySort(
    @StringRes val label: Int,
    val defaultAscending: Boolean,
    @StringRes val ascendingLabel: Int,
    @StringRes val descendingLabel: Int
) {
    UPDATED(R.string.lib_sort_recent, false, R.string.lib_sort_oldest_first, R.string.lib_sort_newest_first),
    CREATED(R.string.lib_sort_created, false, R.string.lib_sort_oldest_first, R.string.lib_sort_newest_first),
    NAME(R.string.lib_sort_name, true, R.string.lib_sort_a_to_z, R.string.lib_sort_z_to_a),
    SIZE(R.string.lib_sort_size, false, R.string.lib_sort_smallest_first, R.string.lib_sort_largest_first)
}

/**
 * The small facts the library screens need without loading the whole list: totals, the folders in use and
 * the newest documents for Home. The list itself is paged straight from the database.
 * [revision] changes on every refresh, so observers that re-read other state (the open document's pages, say)
 * run after every library change even when these facts happen to be equal.
 */
data class LibraryOverview(
    val documentCount: Int = 0,
    val favoriteCount: Int = 0,
    val folders: List<String> = emptyList(),
    /** Every tag in use on an active document, one entry per spelling ignoring case, sorted (R35). */
    val tags: List<String> = emptyList(),
    val recent: List<DocumentSummary> = emptyList(),
    val revision: Long = 0L
)

/** What changes when a document is edited, saved or rebuilt: enough to know whether to reload its pages. */
data class DocumentStamp(val updatedAt: Long, val sizeBytes: Long)
