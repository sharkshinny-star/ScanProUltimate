package com.example.scanpro.data

import com.example.scanpro.R
import com.example.scanpro.core.text.userRequire
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads and light edits of the library: queries, names, folders, tags, favourites, per-document PDF settings (R9). */
internal class DocumentCatalog(private val store: DocumentStore) {
    private val db get() = store.db
    private val settings get() = store.settings
    private val rootDir get() = store.rootDir
    private fun requireDocument(id: String) = store.requireDocument(id)
    private suspend fun mutateDocument(id: String, block: suspend (ScanDocument) -> ScanDocument) = store.mutateDocument(id, block)
    private fun saveWithStalePdf(document: ScanDocument) = store.saveWithStalePdf(document)
    private fun regenerateAndSave(document: ScanDocument, touch: Boolean = true) = store.regenerateAndSave(document, touch)
    private fun ensurePdfNow(id: String) = store.ensurePdfNow(id)

    /** Documents in the library (not in the recycle bin). */
    suspend fun activeDocumentCount(): Int = withContext(Dispatchers.IO) { db.loadOverview(recentLimit = 0).documentCount }

    /** The Files list as pages; see [DocumentDatabase.pagedSummaries]. */
    fun pagedSummaries(folder: String, favoritesOnly: Boolean, sort: LibrarySort, ascending: Boolean = sort.defaultAscending, tag: String = "") = db.pagedSummaries(folder, favoritesOnly, sort, ascending, tag)

    suspend fun loadOverview(recentLimit: Int): LibraryOverview = withContext(Dispatchers.IO) { db.loadOverview(recentLimit) }

    suspend fun loadOtherSummaries(excludeId: String, limit: Int): List<DocumentSummary> =
        withContext(Dispatchers.IO) { db.loadOtherSummaries(excludeId, limit) }

    /** Null when the document is missing or in the recycle bin. */
    suspend fun activeStamp(id: String): DocumentStamp? = withContext(Dispatchers.IO) { db.loadActiveStamp(id) }

    /** The full document with its pages, or null when it no longer exists. */
    suspend fun loadDocument(id: String): ScanDocument? = withContext(Dispatchers.IO) { db.loadDocument(id) }

    suspend fun search(query: String): List<DocumentSummary> = withContext(Dispatchers.IO) { db.search(query) }

    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Renames the document. Returns true when another document already has this name, so the caller can say so. */
    suspend fun rename(id: String, title: String): Boolean = withContext(Dispatchers.IO) {
        val clean = title.trim().take(MAX_TITLE_LENGTH)
        userRequire(clean.isNotBlank(), R.string.err_name_required)
        val clash = db.loadAll().any { it.id != id && it.title.equals(clean, ignoreCase = true) }
        mutateDocument(id) { doc -> doc.copy(title = clean, updatedAt = now()) }
        clash
    }

    /** Every document in the folder goes back to the top level; the folder then no longer exists. */
    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        val target = name.trim()
        userRequire(target.isNotBlank(), R.string.err_folder_name_required)
        db.loadAll(includeTrashed = true)
            .filter { doc -> doc.folderName.equals(target, ignoreCase = true) }
            .forEach { doc -> db.saveDocumentMetadata(doc.copy(folderName = "", updatedAt = now())) }
    }

    /** True when a folder of this name (in any case) already exists, for the rename dialog's merge warning. */
    suspend fun folderExists(name: String): Boolean = withContext(Dispatchers.IO) {
        val target = name.trim()
        target.isNotBlank() && db.loadAll().any { it.folderName.equals(target, ignoreCase = true) }
    }

    suspend fun setTags(id: String, tagsText: String) = mutateDocument(id) { doc ->
        val tags = tagsText.splitToSequence(',', '#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(20)
            .toList()
        doc.copy(tags = tags, updatedAt = now())
    }

    suspend fun setFolder(id: String, folder: String) = withContext(Dispatchers.IO) {
        val wanted = folder.trim().take(80)
        // "bills" joins an existing "Bills" rather than starting a second folder that differs only in case.
        val existing = if (wanted.isBlank()) "" else db.loadAll(includeTrashed = true)
            .map { it.folderName }
            .firstOrNull { it.isNotBlank() && it.equals(wanted, ignoreCase = true) } ?: wanted
        mutateDocument(id) { doc -> doc.copy(folderName = existing, updatedAt = now()) }
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val source = oldName.trim()
        val target = newName.trim().take(80)
        userRequire(source.isNotBlank(), R.string.err_folder_name_required)
        userRequire(target.isNotBlank(), R.string.err_new_folder_name_required)
        db.loadAll(includeTrashed = true)
            .filter { doc -> doc.folderName.equals(source, ignoreCase = true) }
            .forEach { doc -> db.saveDocumentMetadata(doc.copy(folderName = target, updatedAt = now())) }
    }

    suspend fun toggleFavorite(id: String) = mutateDocument(id) { doc ->
        doc.copy(isFavorite = !doc.isFavorite, updatedAt = now())
    }

    // PDF settings mark the PDF stale and rebuild it on the next export, like page edits do, so changing
    // three settings in a row costs one rebuild rather than three blocking ones.
    suspend fun setPdfQuality(id: String, quality: PdfQuality) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(pdfQuality = quality))
    }

    suspend fun setPdfPageSize(id: String, size: PdfPageSize) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(pdfPageSize = size))
    }

    suspend fun setSearchablePdf(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(searchablePdf = enabled))
    }

    suspend fun setPageNumbers(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        regenerateAndSave(requireDocument(id).copy(pageNumbers = enabled, updatedAt = now()))
    }

    suspend fun setScanMode(id: String, mode: ScanMode) = mutateDocument(id) { doc ->
        doc.copy(scanMode = mode, updatedAt = now())
    }

    /** Returns the document with an up-to-date PDF, rebuilding it first if page edits made it stale. */
    suspend fun ensurePdf(id: String): ScanDocument = withContext(Dispatchers.IO) { ensurePdfNow(id) }

    /** Ids of active documents whose PDF still needs rebuilding. */
    suspend fun documentsNeedingPdf(): List<String> = withContext(Dispatchers.IO) {
        db.loadAll().filterNot { it.hasCurrentPdf() }.map { it.id }
    }

    /** A PDF holding more than this per page was written the old, lossless way; a JPEG page is a few hundred kB. */
    private val ScanDocument.isOversized: Boolean
        get() = pageCount > 0 && File(pdfPath).let { it.isFile && it.length() > pageCount * OVERSIZED_BYTES_PER_PAGE }

    /** How many PDFs are far larger than needed, and a cautious estimate of what rebuilding them would free. */
    suspend fun oversizedPdfs(): OversizedPdfs = withContext(Dispatchers.IO) {
        val oversized = db.loadAll().filter { it.isOversized }
        val freeable = oversized.sumOf { (File(it.pdfPath).length() - it.pageCount * EXPECTED_BYTES_PER_PAGE).coerceAtLeast(0L) }
        OversizedPdfs(oversized.size, freeable)
    }

    /** Rebuilds every oversized PDF compactly (never making one larger); returns the bytes freed. */
    suspend fun compactPdfs(): Long = withContext(Dispatchers.IO) {
        db.loadAll().filter { it.isOversized }.sumOf { store.compactPdf(it.id) }
    }

    private companion object {
        const val OVERSIZED_BYTES_PER_PAGE = 2_000_000L
        const val EXPECTED_BYTES_PER_PAGE = 800_000L
    }
}

/** PDFs that rebuilding would shrink: how many, and roughly how many bytes would be freed. */
data class OversizedPdfs(val count: Int, val estimatedBytes: Long)
