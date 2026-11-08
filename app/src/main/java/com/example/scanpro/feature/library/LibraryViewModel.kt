package com.example.scanpro.feature.library

import androidx.paging.filter
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
import com.example.scanpro.work.KeptScanStore
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
    private val savedState: SavedStateHandle,
    private val folderLock: com.example.scanpro.core.security.FolderLock,
    keptScans: KeptScanStore
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Folder and favourites filters live here (saved with the screen's state) because they decide which pages
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
    /** Files tag filter (R35): blank for none. */
    val tag: StateFlow<String> = savedState.getStateFlow(KEY_TAG, "")
    fun setTag(value: String) { savedState[KEY_TAG] = value }
    fun setSort(value: LibrarySort) { viewModelScope.launch { settings.setLibrarySort(value.name); settings.setLibrarySortAscending(null) } }

    /** Sort direction (R35): the saved choice, or the key's own default when none was made. */
    val sortAscending: StateFlow<Boolean> = combine(sort, settings.librarySortAscending) { key, saved -> saved ?: key.defaultAscending }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.UPDATED.defaultAscending)
    fun setSortAscending(value: Boolean) { viewModelScope.launch { settings.setLibrarySortAscending(value) } }

    /** Recent search terms for the Search screen (R35), newest first. */
    val recentSearches: StateFlow<List<String>> = settings.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun recordSearch(term: String) { viewModelScope.launch { settings.recordSearch(term) } }
    fun clearRecentSearches() { viewModelScope.launch { settings.clearRecentSearches() } }
    fun setLibraryGrid(value: Boolean) { viewModelScope.launch { settings.setLibraryGrid(value) } }

    /** Locked folders (lower-cased) and the ones still closed this session; see FolderLock. */
    val lockedFolders: StateFlow<Set<String>> = folderLock.locked
    val hiddenFolders: StateFlow<Set<String>> = folderLock.hidden

    /** The library overview with documents in closed locked folders left out of Recent. */
    val overview: StateFlow<LibraryOverview> = combine(library.overview, folderLock.hidden) { o, hidden ->
        if (hidden.isEmpty()) o else o.copy(recent = o.recent.filterNot { it.inHidden(hidden) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, library.overview.value)

    fun setFolderLocked(name: String, locked: Boolean) = folderLock.setLocked(name, locked)

    init {
        // Leaving the app re-locks folders: a locked folder left selected in Files goes back to All.
        viewModelScope.launch {
            folderLock.hidden.collect { hidden ->
                if (com.example.scanpro.core.security.FolderLock.hides(hidden, folder.value)) savedState[KEY_FOLDER] = ""
            }
        }
    }
    fun unlockFolder(name: String) = folderLock.unlockForSession(name)

    /** The "Did you know?" card on Home; off until loaded so it never flashes for people who closed it. */
    val homeTips: StateFlow<Boolean> = settings.homeTips.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun hideHomeTips() { viewModelScope.launch { settings.setHomeTips(false) } }

    /**
     * The documents to list. Without a search this is the paged database query for the current folder,
     * favourites and sort. A search returns its (small) result set, filtered and ordered the same way.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val documents: Flow<PagingData<DocumentSummary>> =
        combine(_query.debounce(180.milliseconds), folder, favoritesOnly, combine(sort, sortAscending, tag) { k, a, t -> Triple(k, a, t) }, folderLock.hidden) { query, folder, favorites, (sort, ascending, tag), hidden -> Filters(query, folder, favorites, sort, ascending, tag) to hidden }
            .flatMapLatest { (f, hidden) ->
                // A closed locked folder shows nothing, and its documents stay out of All, favourites and search.
                if (com.example.scanpro.core.security.FolderLock.hides(hidden, f.folder)) {
                    flow { emit(PagingData.empty()) }
                } else if (f.query.isBlank()) {
                    repository.pagedSummaries(f.folder, f.favoritesOnly, f.sort, f.ascending, f.tag)
                        .map { page -> if (hidden.isEmpty()) page else page.filter { !it.inHidden(hidden) } }
                } else {
                    flow { emit(PagingData.from(searchResults(f).filterNot { it.inHidden(hidden) })) }
                }
            }
            .cachedIn(viewModelScope)

    private data class Filters(val query: String, val folder: String, val favoritesOnly: Boolean, val sort: LibrarySort, val ascending: Boolean, val tag: String)

    private suspend fun searchResults(f: Filters): List<DocumentSummary> {
        val found = try {
            library.search(f.query)
        } catch (cancelled: CancellationException) {
            // A newer query superseded this one; don't emit an empty result for it.
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        return found.filteredAndSorted(f.folder, f.favoritesOnly, f.sort, f.ascending, f.tag)
    }

    val trashCount: StateFlow<Int> = library.trash
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)

    /** Camera scans that couldn't be saved; Home points to them while there are any. */
    val keptScanCount: StateFlow<Int> = keptScans.scans
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
    ) {
        repository.deleteFolder(name)
        folderLock.setLocked(name, false)
    }

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

    /**
     * Combines the documents into one temporary PDF, in the order given, for sharing (Genius Scan and Adobe Scan can
     * share a selection as one file). The originals are untouched; the file lives in the exports cache.
     */
    fun combinePdfs(ids: List<String>, onReady: (java.io.File) -> Unit) {
        operations.run(context.getString(R.string.msg_preparing_pdfs), null) {
            val fresh = library.freshPdfs(ids)
            if (fresh.failed > 0) {
                operations.showMessage(
                    if (fresh.documents.isEmpty()) context.getString(R.string.lib_msg_pdf_not_prepared)
                    else context.resources.getQuantityString(R.plurals.lib_msg_pdfs_left_out, ids.size, fresh.failed, ids.size)
                )
            }
            val docs = fresh.documents
            if (docs.isEmpty()) return@run
            val name = com.example.scanpro.data.fileSafeName(
                if (docs.size == 1) docs.single().title else "${docs.first().title} (+${docs.size - 1})"
            )
            val out = java.io.File(java.io.File(context.cacheDir, "exports").apply { mkdirs() }, "$name.pdf")
            if (docs.size == 1) java.io.File(docs.single().pdfPath).copyTo(out, overwrite = true)
            else com.example.scanpro.imaging.PdfToolsEngine.mergePdfs(context, docs.map { android.net.Uri.fromFile(java.io.File(it.pdfPath)) }, out)
            withContext(Dispatchers.Main) { onReady(out) }
        }
    }

    /** Renames the folder; queued if another job is running. */
    fun renameFolder(oldName: String, newName: String) =
        operations.run(context.getString(R.string.msg_renaming_folder), context.getString(R.string.msg_folder_renamed)) {
            repository.renameFolder(oldName, newName)
            folderLock.rename(oldName, newName)
        }

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_FAVORITES = "favoritesOnly"
        const val KEY_TAG = "tag"
    }
}

private fun DocumentSummary.inHidden(hidden: Set<String>) = com.example.scanpro.core.security.FolderLock.hides(hidden, folderName)
