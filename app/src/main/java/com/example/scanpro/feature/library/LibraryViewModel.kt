package com.example.scanpro.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
        .mapLatest { (query, all) ->
            if (query.isBlank()) all
            else try {
                library.search(query)
            } catch (cancelled: CancellationException) {
                // A newer query superseded this one; don't emit an empty result for it.
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value)

    val trashCount: StateFlow<Int> = library.trash
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    fun setQuery(value: String) { _query.value = value }

    /** False when the app was busy and nothing was done, so the caller keeps its selection. */
    fun moveManyToTrash(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        val label = if (ids.size == 1) "Moved to recycle bin" else "${ids.size} documents moved to recycle bin"
        return operations.run("Moving ${ids.size} documents to recycle bin…", null) {
            ids.forEach { repository.moveToTrash(it) }
            operations.showMessage(UiMessage(label, actionLabel = "Undo") { restore(ids) })
        }
    }

    private fun restore(ids: Set<String>) =
        operations.enqueue(null, if (ids.size == 1) "Document restored" else "${ids.size} documents restored") {
            ids.forEach { repository.restore(it) }
        }

    /** Hands [onReady] the selected documents with current PDFs, rebuilding stale ones first. */
    fun withFreshPdfs(ids: Set<String>, onReady: (List<ScanDocument>) -> Unit) {
        operations.run("Preparing PDFs…", null) {
            val fresh = library.freshPdfs(ids)
            if (fresh.failed > 0) {
                operations.showMessage(
                    if (fresh.documents.isEmpty()) "The PDF could not be prepared"
                    else "${fresh.failed} of ${ids.size} documents could not be prepared and were left out"
                )
            }
            if (fresh.documents.isNotEmpty()) withContext(Dispatchers.Main) { onReady(fresh.documents) }
        }
    }

    /** False when the app was busy and the folder was not renamed. */
    fun renameFolder(oldName: String, newName: String): Boolean =
        operations.run("Renaming folder…", "Folder renamed") { repository.renameFolder(oldName, newName) }
}
