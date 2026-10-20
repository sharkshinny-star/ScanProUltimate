package com.example.scanpro

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
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-level state: theme, app lock, app-wide messages and exports, and the import entry
 * points used by share intents, the photo picker and the scanners.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner,
    library: DocumentLibrary
) : ViewModel() {
    /** Null until the saved theme has loaded; the launch screen stays up until then, so it never flashes. */
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode
        .map<ThemeMode, ThemeMode?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    fun showMessage(value: String) = operations.showMessage(value)
    fun clearMessage(shown: UiMessage) = operations.clearMessage(shown)
    fun clearExportArtifact() = operations.clearExportArtifact()
    fun clearOpenDocumentRequest() = operations.clearOpenDocumentRequest()

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
