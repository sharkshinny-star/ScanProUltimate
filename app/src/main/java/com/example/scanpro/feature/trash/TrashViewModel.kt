package com.example.scanpro.feature.trash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TrashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    private val folderLock: com.example.scanpro.core.security.FolderLock,
    settings: SettingsStore
) : ViewModel() {
    val retentionDays: StateFlow<Int> = settings.trashRetentionDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_TRASH_RETENTION_DAYS)
    /** Trashed documents, without those from closed locked folders (they show again once the folder is opened). */
    val documents: StateFlow<List<DocumentSummary>> = kotlinx.coroutines.flow.combine(library.trash, folderLock.hidden) { trash, hidden ->
        trash.filterNot { com.example.scanpro.core.security.FolderLock.hides(hidden, it.folderName) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val busy: StateFlow<Boolean> = operations.busy

    fun restore(id: String) = operations.run(null, context.getString(R.string.msg_file_restored)) { repository.restore(id) }
    fun deleteForever(id: String) = operations.run(null, context.getString(R.string.msg_document_permanently_deleted)) { repository.deleteForever(id) }
    // Empty and Restore all act on what the user can see: hidden documents of locked folders are never deleted unseen.
    fun emptyTrash() = operations.run(context.getString(R.string.msg_emptying_recycle_bin), context.getString(R.string.msg_recycle_bin_emptied)) {
        val visible = documents.value
        if (visible.size == library.trash.value.size) repository.emptyTrash() else visible.forEach { repository.deleteForever(it.id) }
    }
    fun restoreAll() = operations.run(context.getString(R.string.trash_msg_restoring_all), context.getString(R.string.trash_msg_all_restored)) {
        documents.value.forEach { repository.restore(it.id) }
    }
}
