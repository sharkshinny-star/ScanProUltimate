package com.example.scanpro.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Backs the Home, Files and Search tabs. Each tab gets its own instance, so queries don't leak between them. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<ScanDocument>> = combine(_query.debounce(180), library.documents) { query, all -> query to all }
        .mapLatest { (query, all) -> if (query.isBlank()) all else runCatching { library.search(query) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value)

    val trashCount: StateFlow<Int> = library.trash
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    fun setQuery(value: String) { _query.value = value }

    fun moveManyToTrash(ids: Set<String>) {
        if (ids.isEmpty()) return
        operations.run("Moving ${ids.size} documents to recycle bin…", "${ids.size} documents moved to recycle bin") {
            ids.forEach { repository.moveToTrash(it) }
        }
    }

    /** Hands [onReady] the selected documents with current PDFs, rebuilding stale ones first. */
    fun withFreshPdfs(ids: Set<String>, onReady: (List<ScanDocument>) -> Unit) {
        operations.run("Preparing PDFs…", null) {
            val fresh = library.withFreshPdfs(ids)
            withContext(Dispatchers.Main) { onReady(fresh) }
        }
    }

    fun renameFolder(oldName: String, newName: String) =
        operations.run("Renaming folder…", "Folder renamed") { repository.renameFolder(oldName, newName) }
}
