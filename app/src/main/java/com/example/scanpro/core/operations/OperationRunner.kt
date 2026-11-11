package com.example.scanpro.core.operations

import android.content.Context
import com.example.scanpro.R
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.data.DocumentLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A snackbar message, optionally with one action such as Undo. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Runs when the message goes away without its action being taken, e.g. to purge what Undo would have restored. */
    val onDismiss: (() -> Unit)? = null
)

/** A file produced by an export that the UI should hand to the share sheet. */
data class ExportArtifact(
    val path: String,
    val mimeType: String,
    val title: String,
    /** Further files of the same type shared together with [path], such as the other pages of a PNG export. */
    val extraPaths: List<String> = emptyList()
)

/**
 * Runs long document operations one at a time on the application scope, so they finish even if
 * the screen that started them is closed, and publishes progress, messages and exports app-wide.
 */
@Singleton
class OperationRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: DocumentLibrary,
    @ApplicationScope private val scope: CoroutineScope,
    private val notifications: AppNotifications
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

    private val _openWhenIdleRequest = MutableStateFlow<String?>(null)
    /** A just-saved scan. The UI opens it only from a top-level screen; anywhere else it announces the scan instead. */
    val openWhenIdleRequest: StateFlow<String?> = _openWhenIdleRequest.asStateFlow()

    private val _scanSaved = MutableStateFlow<String?>(null)
    /** Id of a just-scanned document whose screen should offer the "Scan saved" share and save sheet. */
    val scanSaved: StateFlow<String?> = _scanSaved.asStateFlow()

    fun showMessage(value: String) { _message.value = UiMessage(value) }
    fun showMessage(value: UiMessage) { _message.value = value }
    /** Clears [shown] only if it is still the current message, so a newer one isn't lost unseen. */
    fun clearMessage(shown: UiMessage) { _message.compareAndSet(shown, null) }
    fun publishExport(artifact: ExportArtifact) { _exportArtifact.value = artifact }
    fun clearExportArtifact() { _exportArtifact.value = null }
    fun requestOpenDocument(id: String) { _openDocumentRequest.value = id }
    fun clearOpenDocumentRequest() { _openDocumentRequest.value = null }
    fun requestOpenWhenIdle(id: String) { _openWhenIdleRequest.value = id }
    fun clearOpenWhenIdleRequest() { _openWhenIdleRequest.value = null }
    fun offerScanSaved(id: String) { _scanSaved.value = id }
    fun clearScanSaved(id: String) { _scanSaved.compareAndSet(id, null) }

    private val mutex = Mutex()
    @Volatile private var current: kotlinx.coroutines.Job? = null

    /**
     * Stops the operation that is running now. Operations that loop over pages check for cancellation
     * between pages; the document is left as it was before the operation.
     */
    fun cancelCurrent() {
        current?.cancel()
    }
    // Guards the pending count together with the busy flags: start() runs on Main and the finish runs
    // on a background thread, and without one lock a finishing job could clear busy just after a
    // new job set it.
    private val stateLock = Any()
    private var pending = 0
    // Jobs outside the queue (the PDF tools) that also need the process kept alive while they run.
    private var keptAlive = 0

    /**
     * Runs an edit the user triggered from a screen, then reloads the library. Requests made while
     * another operation is running are queued, never dropped: each action reads the document afresh
     * when its turn comes, so running after the earlier edit is the same as the user tapping later.
     */
    fun run(label: String?, successMessage: String?, action: suspend () -> Unit) {
        start(label, successMessage, action)
    }

    /**
     * Queues an operation that must not be lost, such as an import or an Undo; it runs as soon as the
     * current one finishes. Operations still run one at a time.
     */
    fun enqueue(label: String?, successMessage: String?, action: suspend () -> Unit) =
        start(label, successMessage, action)

    /**
     * Background housekeeping that must not overlap an edit (it waits its turn like any operation) but shows no
     * progress, message or library refresh. Failures are ignored.
     */
    fun enqueueQuiet(action: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock { withContext(Dispatchers.Default) { runCatching { action() } } }
        }
    }

    /**
     * Runs [action] in the caller's coroutine (a WorkManager worker, for instance) while holding the same
     * lock and busy state as the queued operations, so it never overlaps an edit and the screens show its
     * label. The caller handles failures.
     */
    suspend fun <T> perform(label: String?, action: suspend () -> T): T {
        synchronized(stateLock) {
            pending++
            _busy.value = true
        }
        try {
            return mutex.withLock {
                synchronized(stateLock) { _busyLabel.value = label }
                try {
                    action()
                } finally {
                    runCatching { library.refresh() }
                }
            }
        } finally {
            synchronized(stateLock) {
                pending--
                if (pending == 0) {
                    _busyLabel.value = null
                    _busy.value = false
                    // A queued job started the service while this one waited; whichever finishes last stops it.
                    if (keptAlive == 0) OperationForegroundService.stop(context)
                }
            }
        }
    }

    /**
     * Keeps the process alive (the foreground service) while [block] runs, without queueing it behind document
     * edits: the PDF tools work on their own files and show their own progress, but would otherwise be lost if the
     * user switched apps during a long compression or merge.
     */
    suspend fun <T> keepAlive(label: String?, block: suspend () -> T): T {
        synchronized(stateLock) {
            keptAlive++
            OperationForegroundService.show(context, label ?: _busyLabel.value)
        }
        try {
            return block()
        } finally {
            synchronized(stateLock) {
                keptAlive--
                if (keptAlive == 0 && pending == 0) OperationForegroundService.stop(context)
            }
        }
    }

    private fun start(label: String?, successMessage: String?, action: suspend () -> Unit) {
        synchronized(stateLock) {
            pending++
            _busy.value = true
            // Keeps the process alive if the user leaves the app before the work is done. Start and stop
            // requests are sent under the same lock that counts running jobs, so they reach the service in
            // the order of the count: a job finishing as another starts can't switch the service off after
            // the new job asked for it.
            OperationForegroundService.show(context, label ?: _busyLabel.value)
        }
        // Undispatched, so the job joins the lock's first-come-first-served queue on the caller's thread before
        // run() returns: two quick requests always run in the order they were made. The work itself then moves
        // to a background thread.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                mutex.withLock { withContext(Dispatchers.Default) {
                    synchronized(stateLock) {
                        _busyLabel.value = label
                        OperationForegroundService.show(context, label)
                    }
                    current = coroutineContext[kotlinx.coroutines.Job]
                    val messageBefore = _message.value
                    try {
                        action()
                        if (successMessage != null) _message.value = UiMessage(successMessage)
                        // Only jobs long enough to show progress are worth a notice if the user left meanwhile.
                        if (label != null) notifications.taskFinished(_message.value.takeIf { it !== messageBefore }?.text)
                    } catch (skipped: OperationSkipped) {
                        skipped.message?.let { _message.value = UiMessage(it) }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        _message.value = UiMessage(context.getString(R.string.common_stopped))
                    } catch (error: Throwable) {
                        val text = error.message ?: context.getString(R.string.common_something_went_wrong)
                        _message.value = UiMessage(text)
                        if (label != null) runCatching { notifications.taskFailed(text) }
                    } finally {
                        current = null
                    }
                    runCatching { library.refresh() }
                } }
            } finally {
                synchronized(stateLock) {
                    pending--
                    if (pending == 0) {
                        _busyLabel.value = null
                        _busy.value = false
                        if (keptAlive == 0) OperationForegroundService.stop(context)
                    }
                }
            }
        }
    }
}

/** Thrown by an action that found nothing to do: the runner shows [message] (if any) instead of the success message. */
class OperationSkipped(message: String?) : Exception(message)
