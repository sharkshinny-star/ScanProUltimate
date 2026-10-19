package com.example.scanpro

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.ExportArtifact
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.LIGHT)
    val appLockEnabled = settings.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val documents: StateFlow<List<ScanDocument>> = library.documents
    val message: StateFlow<String?> = operations.message
    val exportArtifact: StateFlow<ExportArtifact?> = operations.exportArtifact
    val openDocumentRequest: StateFlow<String?> = operations.openDocumentRequest

    fun showMessage(value: String) = operations.showMessage(value)
    fun clearMessage() = operations.clearMessage()
    fun clearExportArtifact() = operations.clearExportArtifact()
    fun clearOpenDocumentRequest() = operations.clearOpenDocumentRequest()

    fun setAppLock(value: Boolean) { viewModelScope.launch { settings.setAppLock(value) } }

    fun importScan(pdfUri: Uri?, imageUris: List<Uri>) {
        operations.run("Enhancing pages, generating OCR, and building searchable PDF…", "Scan saved") {
            repository.importScan(pdfUri, imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        operations.run("Importing pages, enhancing them, and running OCR…", "Document saved") {
            repository.importImages(imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importPdf(uri: Uri) {
        operations.run("Importing PDF pages and building OCR index…", "PDF imported") {
            repository.importPdf(uri, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    fun importCameraSession(imageUris: List<Uri>, mode: ScanMode) {
        if (imageUris.isEmpty()) return
        operations.run("Finishing ${mode.label.lowercase()} scan, enhancing pages, and running OCR…", "Scan saved") {
            val newDocument = repository.importImages(imageUris, settings.pdfQuality.first(), settings.autoTitle.first(), mode)
            operations.requestOpenDocument(newDocument.id)
        }
    }
}
