package com.example.scanpro.core.operations

import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.data.DocumentLibrary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A snackbar message, optionally with one action such as Undo. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

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

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _exportArtifact = MutableStateFlow<ExportArtifact?>(null)
    val exportArtifact: StateFlow<ExportArtifact?> = _exportArtifact.asStateFlow()

    private val _openDocumentRequest = MutableStateFlow<String?>(null)
    /** Id of a newly created document the UI should open once it appears in the library. */
    val openDocumentRequest: StateFlow<String?> = _openDocumentRequest.asStateFlow()

    fun showMessage(value: String) { _message.value = UiMessage(value) }
    fun showMessage(value: UiMessage) { _message.value = value }
    /** Clears [shown] only if it is still the current message, so a newer one isn't lost unseen. */
    fun clearMessage(shown: UiMessage) { _message.compareAndSet(shown, null) }
    fun publishExport(artifact: ExportArtifact) { _exportArtifact.value = artifact }
    fun clearExportArtifact() { _exportArtifact.value = null }
    fun requestOpenDocument(id: String) { _openDocumentRequest.value = id }
    fun clearOpenDocumentRequest() { _openDocumentRequest.value = null }

    private val mutex = Mutex()
    private val pending = AtomicInteger(0)

    /**
     * Runs an edit the user triggered from a screen, then reloads the library. While another operation
     * is running the request is rejected with a message, because it was made against state that is
     * about to change.
     */
    fun run(label: String?, successMessage: String?, action: suspend () -> Unit) {
        if (_busy.value) {
            _message.value = UiMessage("Still working on the previous task. Try again in a moment.")
            return
        }
        start(label, successMessage, action)
    }

    /**
     * Queues an operation that must not be lost, such as an import or an Undo; it runs as soon as the
     * current one finishes. Operations still run one at a time.
     */
    fun enqueue(label: String?, successMessage: String?, action: suspend () -> Unit) =
        start(label, successMessage, action)

    private fun start(label: String?, successMessage: String?, action: suspend () -> Unit) {
        pending.incrementAndGet()
        _busy.value = true
        scope.launch {
            try {
                mutex.withLock {
                    _busyLabel.value = label
                    runCatching { action() }
                        .onSuccess { if (successMessage != null) _message.value = UiMessage(successMessage) }
                        .onFailure { error -> _message.value = UiMessage(error.message ?: "Something went wrong") }
                    runCatching { library.refresh() }
                }
            } finally {
                if (pending.decrementAndGet() == 0) {
                    _busyLabel.value = null
                    _busy.value = false
                }
            }
        }
    }
}
