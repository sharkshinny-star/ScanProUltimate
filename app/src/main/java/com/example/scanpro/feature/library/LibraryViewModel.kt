package com.example.scanpro.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<DocumentSummary>> = combine(_query.debounce(180), library.documents) { query, all -> query to all }
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

    fun moveManyToTrash(ids: Set<String>) {
        if (ids.isEmpty()) return
        val label = if (ids.size == 1) context.getString(R.string.msg_moved_to_recycle_bin) else context.getString(R.string.msg_documents_moved_to_recycle_bin, ids.size)
        operations.run(context.getString(R.string.msg_moving_documents_to_recycle_bin, ids.size), null) {
            ids.forEach { repository.moveToTrash(it) }
            operations.showMessage(UiMessage(label, actionLabel = context.getString(R.string.common_undo)) { restore(ids) })
        }
    }

    private fun restore(ids: Set<String>) =
        operations.enqueue(null, if (ids.size == 1) context.getString(R.string.msg_document_restored) else context.getString(R.string.msg_documents_restored, ids.size)) {
            ids.forEach { repository.restore(it) }
        }

    /** Hands [onReady] the selected documents with current PDFs, rebuilding stale ones first. */
    fun withFreshPdfs(ids: Set<String>, onReady: (List<ScanDocument>) -> Unit) {
        operations.run(context.getString(R.string.msg_preparing_pdfs), null) {
            val fresh = library.withFreshPdfs(ids)
            withContext(Dispatchers.Main) { onReady(fresh) }
        }
    }

    fun renameFolder(oldName: String, newName: String) =
        operations.run(context.getString(R.string.msg_renaming_folder), context.getString(R.string.msg_folder_renamed)) { repository.renameFolder(oldName, newName) }
}
