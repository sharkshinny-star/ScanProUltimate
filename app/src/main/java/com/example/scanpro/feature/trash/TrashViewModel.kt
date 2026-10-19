package com.example.scanpro.feature.trash

import androidx.lifecycle.ViewModel
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
    library: DocumentLibrary
) : ViewModel() {
    val documents: StateFlow<List<ScanDocument>> = library.trash
    val busy: StateFlow<Boolean> = operations.busy

    fun restore(id: String) = operations.run(null, "File restored successfully.") { repository.restore(id) }
    fun deleteForever(id: String) = operations.run(null, "Document permanently deleted") { repository.deleteForever(id) }
    fun emptyTrash() = operations.run("Emptying recycle bin…", "Recycle bin emptied") { repository.emptyTrash() }
}
