package com.example.scanpro.core.operations

import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.data.DocumentLibrary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A file produced by an export that the UI should hand to the share sheet. */
data class ExportArtifact(
    val path: String,
    val mimeType: String,
    val title: String
)

/**
 * Runs long document operations one at a time on the application scope, so they finish even if
 * the screen that started them is closed, and publishes progress, messages and exports app-wide.
 */
@Singleton
class OperationRunner @Inject constructor(
    private val library: DocumentLibrary,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportArtifact = MutableStateFlow<ExportArtifact?>(null)
    val exportArtifact: StateFlow<ExportArtifact?> = _exportArtifact.asStateFlow()

    private val _openDocumentRequest = MutableStateFlow<String?>(null)
    /** Id of a newly created document the UI should open once it appears in the library. */
    val openDocumentRequest: StateFlow<String?> = _openDocumentRequest.asStateFlow()

    fun showMessage(value: String) { _message.value = value }
    fun clearMessage() { _message.value = null }
    fun publishExport(artifact: ExportArtifact) { _exportArtifact.value = artifact }
    fun clearExportArtifact() { _exportArtifact.value = null }
    fun requestOpenDocument(id: String) { _openDocumentRequest.value = id }
    fun clearOpenDocumentRequest() { _openDocumentRequest.value = null }

    /**
     * Runs [action], then reloads the library. Only one operation runs at a time; a second request
     * while busy is rejected with a message rather than queued.
     */
    fun run(label: String?, successMessage: String?, action: suspend () -> Unit) {
        if (_busy.value) {
            _message.value = "Still working on the previous task. Try again in a moment."
            return
        }
        _busy.value = true
        _busyLabel.value = label
        scope.launch {
            try {
                runCatching { action() }
                    .onSuccess { if (successMessage != null) _message.value = successMessage }
                    .onFailure { error -> _message.value = error.message ?: "Something went wrong" }
                runCatching { library.refresh() }
            } finally {
                _busyLabel.value = null
                _busy.value = false
            }
        }
    }
}
