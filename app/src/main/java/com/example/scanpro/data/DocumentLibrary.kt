package com.example.scanpro.data

import com.example.scanpro.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the document lists every screen observes.
 * Screens read these flows; [refresh] is called after anything changes the library.
 */
@Singleton
class DocumentLibrary @Inject constructor(
    private val repository: DocumentRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _overview = MutableStateFlow(LibraryOverview())
    /** Totals, folders and the newest documents; the full list is paged from the database (see DocumentRepository). */
    val overview: StateFlow<LibraryOverview> = _overview.asStateFlow()

    private val _trash = MutableStateFlow<List<DocumentSummary>>(emptyList())
    val trash: StateFlow<List<DocumentSummary>> = _trash.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    init {
        scope.launch {
            runCatching { refresh() }
            // Catch up on PDFs left stale when the app was closed before they were rebuilt.
            runCatching { repository.documentsNeedingPdf() }.getOrDefault(emptyList()).forEach { id ->
                runCatching { repository.ensurePdf(id) }
            }
            runCatching { refresh() }
        }
    }

    /** Rebuilds a document's PDF off the UI path, e.g. after leaving the editor. */
    fun buildPdfInBackground(id: String) {
        scope.launch {
            runCatching { repository.ensurePdf(id) }
            runCatching { refresh() }
        }
    }

    /** Returns the documents with current PDFs, rebuilding any that page edits made stale. */
    suspend fun withFreshPdfs(ids: Collection<String>): List<ScanDocument> =
        ids.mapNotNull { id -> runCatching { repository.ensurePdf(id) }.getOrNull() }

    suspend fun refresh() {
        val next = repository.loadOverview(RECENT_LIMIT)
        _overview.value = next.copy(revision = _overview.value.revision + 1)
        _trash.value = repository.loadTrashSummaries()
        _storageBytes.value = repository.storageBytes()
    }

    suspend fun search(query: String): List<DocumentSummary> = repository.search(query)

    /** True when [id] is a document in the library (not in the recycle bin). */
    suspend fun exists(id: String): Boolean = repository.activeStamp(id) != null

    private companion object {
        /** How many recent documents Home shows. */
        const val RECENT_LIMIT = 8
    }
}
