package com.example.scanpro.feature.trash

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    settings: com.example.scanpro.data.SettingsStore
) : ViewModel() {
    val retentionDays: StateFlow<Int> = settings.trashRetentionDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS)
    val documents: StateFlow<List<ScanDocument>> = library.trash
    val busy: StateFlow<Boolean> = operations.busy

    fun restore(id: String) = operations.run(null, "File restored successfully.") { repository.restore(id) }
    fun deleteForever(id: String) = operations.run(null, "Document permanently deleted") { repository.deleteForever(id) }
    fun emptyTrash() = operations.run("Emptying recycle bin…", "Recycle bin emptied") { repository.emptyTrash() }
    fun restoreAll() = operations.run("Restoring everything…", "All documents restored") {
        library.trash.value.forEach { repository.restore(it.id) }
    }
}
