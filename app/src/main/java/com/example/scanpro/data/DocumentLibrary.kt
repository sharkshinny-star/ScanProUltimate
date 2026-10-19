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
    @ApplicationScope scope: CoroutineScope
) {
    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    private val _trash = MutableStateFlow<List<ScanDocument>>(emptyList())
    val trash: StateFlow<List<ScanDocument>> = _trash.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    init {
        scope.launch { runCatching { refresh() } }
    }

    suspend fun refresh() {
        _documents.value = repository.loadAll()
        _trash.value = repository.loadTrash()
        _storageBytes.value = repository.storageBytes()
    }

    suspend fun search(query: String): List<ScanDocument> = repository.search(query)
}
