package com.example.scanpro.feature.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Backs the Home, Files and Search tabs. Each tab gets its own instance, so queries don't leak between them. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    private val settings: SettingsStore,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Folder and favorites filters live here (saved with the screen's state) because they decide which pages
    // the database returns.
    val folder: StateFlow<String> = savedState.getStateFlow(KEY_FOLDER, "")
    val favoritesOnly: StateFlow<Boolean> = savedState.getStateFlow(KEY_FAVORITES, false)

    /** Sort and layout of the Files tab, kept across restarts. */
    val sort: StateFlow<LibrarySort> = settings.librarySort
        .map { name -> runCatching { LibrarySort.valueOf(name) }.getOrDefault(LibrarySort.UPDATED) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.UPDATED)
    val libraryGrid: StateFlow<Boolean> = settings.libraryGrid.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setFolder(value: String) { savedState[KEY_FOLDER] = value }
    fun setFavoritesOnly(value: Boolean) { savedState[KEY_FAVORITES] = value }
    fun setSort(value: LibrarySort) { viewModelScope.launch { settings.setLibrarySort(value.name) } }
    fun setLibraryGrid(value: Boolean) { viewModelScope.launch { settings.setLibraryGrid(value) } }

    val overview: StateFlow<LibraryOverview> = library.overview

    /**
     * The documents to list. Without a search this is the paged database query for the current folder,
     * favorites and sort. A search returns its (small) result set, filtered and ordered the same way.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val documents: Flow<PagingData<DocumentSummary>> =
        combine(_query.debounce(180.milliseconds), folder, favoritesOnly, sort) { query, folder, favorites, sort -> Filters(query, folder, favorites, sort) }
            .flatMapLatest { f ->
                if (f.query.isBlank()) {
                    repository.pagedSummaries(f.folder, f.favoritesOnly, f.sort)
                } else {
                    flow { emit(PagingData.from(searchResults(f))) }
                }
            }
            .cachedIn(viewModelScope)

    private data class Filters(val query: String, val folder: String, val favoritesOnly: Boolean, val sort: LibrarySort)

    private suspend fun searchResults(f: Filters): List<DocumentSummary> {
        val found = try {
            library.search(f.query)
        } catch (cancelled: CancellationException) {
            // A newer query superseded this one; don't emit an empty result for it.
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        return found.filteredAndSorted(f.folder, f.favoritesOnly, f.sort)
    }

    val trashCount: StateFlow<Int> = library.trash
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    fun setQuery(value: String) { _query.value = value }

    fun stopCurrent() = operations.cancelCurrent()

    /** Moves the selected documents into [folder] (blank for the top level). Queued if another job is running. */
    fun moveToFolder(ids: Set<String>, folder: String) = operations.run(
        context.resources.getQuantityString(R.plurals.lib_msg_moving_documents, ids.size, ids.size),
        if (folder.isBlank()) context.getString(R.string.lib_msg_moved_out_of_folder)
        else context.getString(R.string.lib_msg_moved_to_folder, folder.trim())
    ) { ids.forEach { repository.setFolder(it, folder) } }

    /** Stars or unstars the selected documents. Queued if another job is running. */
    fun setFavorite(ids: Set<String>, favorite: Boolean) = operations.run(
        null,
        context.getString(if (favorite) R.string.lib_msg_added_to_favorites else R.string.lib_msg_removed_from_favorites)
    ) {
        ids.forEach { id -> if (repository.loadDocument(id)?.isFavorite == !favorite) repository.toggleFavorite(id) }
    }

    /** Takes every document out of the folder; the folder is gone once it holds nothing. */
    fun deleteFolder(name: String) = operations.run(
        context.getString(R.string.lib_msg_removing_folder),
        context.getString(R.string.lib_msg_folder_removed)
    ) { repository.deleteFolder(name) }

    suspend fun folderExists(name: String): Boolean = repository.folderExists(name)

    /** False when nothing was selected, so the caller keeps its selection. */
    fun moveManyToTrash(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        val label = if (ids.size == 1) context.getString(R.string.msg_moved_to_recycle_bin) else context.resources.getQuantityString(R.plurals.msg_documents_moved_to_recycle_bin, ids.size, ids.size)
        operations.run(context.resources.getQuantityString(R.plurals.msg_moving_documents_to_recycle_bin, ids.size, ids.size), null) {
            ids.forEach { repository.moveToTrash(it) }
            operations.showMessage(UiMessage(label, actionLabel = context.getString(R.string.common_undo), onAction = { restore(ids) }))
        }
        return true
    }

    private fun restore(ids: Set<String>) =
        operations.enqueue(null, if (ids.size == 1) context.getString(R.string.msg_document_restored) else context.resources.getQuantityString(R.plurals.msg_documents_restored, ids.size, ids.size)) {
            ids.forEach { repository.restore(it) }
        }

    /** Hands [onReady] the selected documents with current PDFs, rebuilding stale ones first. */
    fun withFreshPdfs(ids: Set<String>, onReady: (List<ScanDocument>) -> Unit) {
        operations.run(context.getString(R.string.msg_preparing_pdfs), null) {
            val fresh = library.freshPdfs(ids)
            if (fresh.failed > 0) {
                operations.showMessage(
                    if (fresh.documents.isEmpty()) context.getString(R.string.lib_msg_pdf_not_prepared)
                    else context.resources.getQuantityString(R.plurals.lib_msg_pdfs_left_out, ids.size, fresh.failed, ids.size)
                )
            }
            if (fresh.documents.isNotEmpty()) withContext(Dispatchers.Main) { onReady(fresh.documents) }
        }
    }

    /** Renames the folder; queued if another job is running. */
    fun renameFolder(oldName: String, newName: String) =
        operations.run(context.getString(R.string.msg_renaming_folder), context.getString(R.string.msg_folder_renamed)) { repository.renameFolder(oldName, newName) }

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_FAVORITES = "favoritesOnly"
    }
}
