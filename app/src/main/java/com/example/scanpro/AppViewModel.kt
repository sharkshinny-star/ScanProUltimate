package com.example.scanpro

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.ExportArtifact
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.scanner.SESSION_ROOT
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-level state: theme, app lock, app-wide messages and exports, and the import entry
 * points used by share intents, the photo picker and the scanners.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner,
    library: DocumentLibrary
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.LIGHT)
    /** Null until the setting has loaded, so the library is never shown before the lock decision. */
    val appLockEnabled: StateFlow<Boolean?> = settings.appLock
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Lives in the ViewModel so rotating the screen doesn't lock the app again.
    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()
    private var stoppedAt = 0L

    // Imports that arrive from other apps while the library is locked wait here until it is unlocked.
    private val pendingExternalImports = mutableListOf<() -> Unit>()

    private val _scanRequested = MutableStateFlow(false)
    /** Set by the "Scan" launcher shortcut; the shell opens the camera once the app is unlocked. */
    val scanRequested: StateFlow<Boolean> = _scanRequested.asStateFlow()
    val documents: StateFlow<List<ScanDocument>> = library.documents
    val message: StateFlow<UiMessage?> = operations.message
    val exportArtifact: StateFlow<ExportArtifact?> = operations.exportArtifact
    val openDocumentRequest: StateFlow<String?> = operations.openDocumentRequest

    /**
     * Ask for a Play rating once, after first real use: the library holds a document, nothing is
     * still processing, and the prompt hasn't been answered before.
     */
    val showRatingPrompt: StateFlow<Boolean> = combine(
        library.documents, operations.busy, settings.ratingPromptDone
    ) { documents, busy, done -> documents.isNotEmpty() && !busy && !done }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onRatingPromptFinished() { viewModelScope.launch { settings.setRatingPromptDone() } }

    private val _externalPdfToView = MutableStateFlow<ExternalPdf?>(null)
    /** A PDF opened from another app, copied locally; the shell shows it in the viewer. */
    val externalPdfToView: StateFlow<ExternalPdf?> = _externalPdfToView.asStateFlow()

    fun showMessage(value: String) = operations.showMessage(value)
    fun clearMessage(shown: UiMessage) = operations.clearMessage(shown)
    fun clearExportArtifact() = operations.clearExportArtifact()
    fun clearOpenDocumentRequest() = operations.clearOpenDocumentRequest()
    fun clearExternalPdfToView() { _externalPdfToView.value = null }

    /**
     * Copies a PDF from another app into cache/exports/ (so it stays readable after the grant ends,
     * and can be shared from the viewer), then asks the shell to show it.
     */
    fun viewExternalPdf(uri: Uri) {
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(File(context.cacheDir, "exports"), "view_${System.currentTimeMillis()}.pdf")
                    file.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: error("Unreadable PDF")
                    file
                }.getOrNull()
            }
            if (local == null) {
                operations.showMessage("Could not open PDF")
                return@launch
            }
            val title = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "PDF Document"
            _externalPdfToView.value = ExternalPdf(Uri.fromFile(local), title)
        }
    }

    fun setAppLock(value: Boolean) { viewModelScope.launch { settings.setAppLock(value) } }

    init {
        viewModelScope.launch {
            combine(appLockEnabled, _sessionUnlocked) { lock, unlocked -> lock == false || unlocked }
                .collect { open -> if (open) flushExternalImports() }
        }
    }

    private fun isLibraryOpen() = appLockEnabled.value == false || _sessionUnlocked.value

    fun markUnlocked() { _sessionUnlocked.value = true }

    fun onAppStopped(nowMs: Long) { stoppedAt = nowMs }

    /** Re-locks after a real absence, but not for quick trips to the photo picker, camera or share sheet. */
    fun onAppStarted(nowMs: Long, timeoutMs: Long) {
        if (stoppedAt != 0L && nowMs - stoppedAt > timeoutMs) _sessionUnlocked.value = false
    }

    fun requestScan() { _scanRequested.value = true }
    fun clearScanRequest() { _scanRequested.value = false }

    /** Runs an import that came from another app now, or after unlock if the library is locked. */
    fun submitExternalImport(import: () -> Unit) {
        if (isLibraryOpen()) import() else synchronized(pendingExternalImports) { pendingExternalImports += import }
    }

    private fun flushExternalImports() {
        val ready = synchronized(pendingExternalImports) {
            pendingExternalImports.toList().also { pendingExternalImports.clear() }
        }
        ready.forEach { it() }
    }

    fun importScan(pdfUri: Uri?, imageUris: List<Uri>) {
        operations.enqueue("Enhancing pages, generating OCR, and building searchable PDF…", "Scan saved") {
            repository.importScan(pdfUri, imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        if (imageUris.size > MAX_IMPORT_IMAGES) {
            operations.showMessage("Import up to $MAX_IMPORT_IMAGES images at a time")
            return
        }
        operations.enqueue("Importing pages, enhancing them, and running OCR…", "Document saved") {
            repository.importImages(imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importPdf(uri: Uri) {
        operations.enqueue("Importing PDF pages and building OCR index…", "PDF imported") {
            repository.importPdf(uri, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importCameraSession(imageUris: List<Uri>, mode: ScanMode) {
        if (imageUris.isEmpty()) return
        operations.enqueue("Finishing ${mode.label.lowercase()} scan, enhancing pages, and running OCR…", "Scan saved") {
            try {
                val newDocument = repository.importImages(imageUris, settings.pdfQuality.first(), settings.autoTitle.first(), mode)
                operations.requestOpenDocument(newDocument.id)
            } finally {
                // The document now holds its own copies; drop the scanner session's temporary captures.
                imageUris.asSequence()
                    .filter { it.scheme == "file" }
                    .mapNotNull { uri -> uri.path?.let { File(it).parentFile } }
                    .filter { it.parentFile?.name == SESSION_ROOT }
                    .distinct()
                    .forEach { it.deleteRecursively() }
            }
        }
    }
}

private const val MAX_IMPORT_IMAGES = 100

data class ExternalPdf(val uri: Uri, val title: String)
