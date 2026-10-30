package com.example.scanpro.data

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
    val trashedAt: Long = 0L
)
