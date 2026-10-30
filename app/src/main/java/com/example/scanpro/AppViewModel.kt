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
import com.example.scanpro.data.PdfPasswordRequiredException
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.scanner.SESSION_ROOT
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner,
    private val library: DocumentLibrary
) : ViewModel() {
    /** Null until the saved theme has loaded; the launch screen stays up until then, so it never flashes. */
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode
        .map<ThemeMode, ThemeMode?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /**
     * The Play in-app review flow is due once, after the third saved scan, when the app is in a calm place
     * (the document screen). Google decides whether a card is shown; nothing in the app promises one.
     */
    val reviewDue: StateFlow<Boolean> = combine(settings.completedScans, settings.reviewRequested) { scans, requested ->
        scans >= REVIEW_AFTER_SCANS && !requested
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The one-time "How was your scanning experience?" prompt, due after the fifth saved scan. */
    val feedbackPromptDue: StateFlow<Boolean> = combine(settings.completedScans, settings.feedbackPromptShown) { scans, shown ->
        scans >= FEEDBACK_PROMPT_AFTER_SCANS && !shown
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markReviewRequested() { viewModelScope.launch { settings.setReviewRequested() } }
    fun markFeedbackPromptShown() { viewModelScope.launch { settings.setFeedbackPromptShown() } }

    /** Wallpaper-derived colours (Android 12+); applied as soon as it is known, false until then. */
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** Null until the setting has loaded, so the library is never shown before the lock decision. */
    val appLockEnabled: StateFlow<Boolean?> = settings.appLock
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until known; false shows the first-run welcome once. */
    val welcomeSeen: StateFlow<Boolean?> = settings.welcomeSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markWelcomeSeen() { viewModelScope.launch { settings.setWelcomeSeen() } }

    // Lives in the ViewModel so rotating the screen doesn't lock the app again.
    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()
    private var stoppedAt = 0L

    // Imports that arrive from other apps while the library is locked wait here until it is unlocked.
    private val pendingExternalImports = mutableListOf<() -> Unit>()

    private val _scanRequested = MutableStateFlow(false)
    /** Set by the "Scan" launcher shortcut; the shell opens the camera once the app is unlocked. */
    val scanRequested: StateFlow<Boolean> = _scanRequested.asStateFlow()
    /** Changes after every library refresh; the shell re-checks pending "open this document" requests on it. */
    val libraryRevision: StateFlow<Long> = library.overview
        .map { it.revision }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    suspend fun documentExists(id: String): Boolean = library.exists(id)
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
        operations.enqueue(context.getString(R.string.msg_import_scan_progress), null) {
            val document = repository.importScan(pdfUri, imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
            operations.offerScanSaved(document.id)
            operations.requestOpenDocument(document.id)
        }
    }

    fun importImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        if (imageUris.size > MAX_IMPORT_IMAGES) {
            operations.showMessage(context.getString(R.string.msg_import_images_limit, MAX_IMPORT_IMAGES))
            return
        }
        operations.enqueue(context.getString(R.string.msg_import_images_progress), context.getString(R.string.msg_document_saved)) {
            repository.importImages(imageUris, settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }

    /** A protected PDF waiting for its password; the shell shows a prompt for it. */
    data class PdfPasswordRequest(val uri: Uri, val wrongPassword: Boolean)

    private val _pdfPasswordRequest = MutableStateFlow<PdfPasswordRequest?>(null)
    val pdfPasswordRequest: StateFlow<PdfPasswordRequest?> = _pdfPasswordRequest.asStateFlow()
    fun cancelPdfPassword() { _pdfPasswordRequest.value = null }

    fun importPdf(uri: Uri, password: String? = null) {
        _pdfPasswordRequest.value = null
        operations.enqueue(context.getString(R.string.msg_import_pdf_progress), null) {
            try {
                repository.importPdf(uri, settings.pdfQuality.first(), settings.autoTitle.first(), password)
                operations.showMessage(context.getString(R.string.msg_pdf_imported))
            } catch (needsPassword: PdfPasswordRequiredException) {
                _pdfPasswordRequest.value = PdfPasswordRequest(uri, needsPassword.wrongPassword)
            }
        }
    }

    fun importCameraSession(
        imageUris: List<Uri>,
        mode: ScanMode,
        crops: List<com.example.scanpro.data.CropQuad> = emptyList(),
        filters: List<com.example.scanpro.data.PageFilter> = emptyList()
    ) {
        if (imageUris.isEmpty()) return
        // As WorkManager work, so the scan is finished even if the app is swiped away meanwhile.
        com.example.scanpro.work.ImportScanWorker.enqueue(context, com.example.scanpro.work.ImportScanWorker.inputFor(imageUris, mode, crops, filters))
    }

    /** A page shot again from the editor: the new photo takes the old page's place, then the session is cleaned up. */
    fun replacePageFromCamera(
        target: com.example.scanpro.navigation.RetakeTarget,
        imageUri: Uri,
        crop: com.example.scanpro.data.CropQuad,
        filter: com.example.scanpro.data.PageFilter
    ) {
        val number = target.position + 1
        operations.enqueue(context.getString(R.string.msg_replacing_page, number), context.getString(R.string.msg_page_retaken, number)) {
            try {
                repository.replacePage(target.documentId, target.pageId, imageUri, crop, filter)
            } finally {
                if (imageUri.scheme == "file") imageUri.path?.let { File(it).parentFile }
                    ?.takeIf { it.parentFile?.name == SESSION_ROOT }?.deleteRecursively()
            }
        }
    }

}

private const val MAX_IMPORT_IMAGES = 100


/** Saved scans before the Play in-app review flow is requested, once. */
const val REVIEW_AFTER_SCANS = 3
/** Saved scans before the one-time feedback prompt; later than the review so the two never meet. */
const val FEEDBACK_PROMPT_AFTER_SCANS = 5
