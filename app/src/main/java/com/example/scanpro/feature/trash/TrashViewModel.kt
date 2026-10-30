package com.example.scanpro.feature.trash

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.DocumentSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class TrashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    library: DocumentLibrary
) : ViewModel() {
    val documents: StateFlow<List<DocumentSummary>> = library.trash
    val busy: StateFlow<Boolean> = operations.busy

    fun restore(id: String) = operations.run(null, context.getString(R.string.msg_file_restored)) { repository.restore(id) }
    fun deleteForever(id: String) = operations.run(null, context.getString(R.string.msg_document_permanently_deleted)) { repository.deleteForever(id) }
    fun emptyTrash() = operations.run(context.getString(R.string.msg_emptying_recycle_bin), context.getString(R.string.msg_recycle_bin_emptied)) { repository.emptyTrash() }
}
