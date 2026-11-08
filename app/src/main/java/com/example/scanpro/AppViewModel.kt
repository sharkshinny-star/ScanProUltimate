package com.example.scanpro

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.ExportArtifact
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPasswordRequiredException
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.data.WHATS_NEW_VERSION
import com.example.scanpro.navigation.RetakeTarget
import com.example.scanpro.scanner.SESSION_ROOT
import com.example.scanpro.work.ImportScanWorker
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
    private val folderLock: com.example.scanpro.core.security.FolderLock,
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
    /** The one-time rating sheet, due after the first saved scan (the owner's "New Look" design). */
    val ratingDue: StateFlow<Boolean> = combine(settings.completedScans, settings.feedbackPromptShown) { scans, shown ->
        scans >= RATING_AFTER_SCANS && !shown
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markRatingShown() { viewModelScope.launch { settings.setFeedbackPromptShown() } }

    /** Wallpaper-derived colours (Android 12+); applied as soon as it is known, false until then. */
    /** Camera defaults from Settings: the mode it opens in and the filter new scans start with. */
    val defaultScanMode: StateFlow<String> = settings.defaultScanMode.stateIn(viewModelScope, SharingStarted.Eagerly, ScanMode.DOCUMENT.name)
    val defaultFilter: StateFlow<PageFilter?> = settings.defaultFilter.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val reviewMode: StateFlow<com.example.scanpro.data.ReviewMode> = settings.reviewMode.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.scanpro.data.ReviewMode.WHEN_NEEDED)

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** Null until the setting has loaded, so the library is never shown before the lock decision. */
    val appLockEnabled: StateFlow<Boolean?> = settings.appLock
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Folders a new scan can be saved into: every folder except locked ones not opened this session. */
    val saveFolders: StateFlow<List<String>> = combine(library.overview, folderLock.hidden) { overview, hidden ->
        overview.folders.filterNot { com.example.scanpro.core.security.FolderLock.hides(hidden, it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Null until known; false shows the first-run welcome once. */
    val welcomeSeen: StateFlow<Boolean?> = settings.welcomeSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * True once everything the first frame depends on has loaded: the theme and colour theme, the lock decision and whether the
     * welcome is due (otherwise a first launch could flash the library before the welcome covers it).
     */
    val launchStateReady: Boolean
        get() = themeMode.value != null && accentPalette.value != null && appLockEnabled.value != null && welcomeSeen.value != null

    /** Closing the welcome also counts as having seen the current "What's new": it covers the same ground. */
    fun markWelcomeSeen() {
        viewModelScope.launch {
            settings.setWhatsNewSeen(WHATS_NEW_VERSION)
            settings.setWelcomeSeen()
        }
    }

    /** Null until the saved colour theme has loaded; like [themeMode], it holds the launch screen until then. */
    val accentPalette: StateFlow<AccentPalette?> = settings.accentPalette
        .map<AccentPalette, AccentPalette?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * True once for people updating from a release before [WHATS_NEW_VERSION], after the welcome is
     * known to have been seen (so a new install never gets both).
     */
    val whatsNewDue: StateFlow<Boolean> = combine(settings.welcomeSeen, settings.whatsNewSeen) { welcome, seen ->
        welcome && seen < WHATS_NEW_VERSION
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markWhatsNewSeen() { viewModelScope.launch { settings.setWhatsNewSeen(WHATS_NEW_VERSION) } }

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
    val openWhenIdleRequest: StateFlow<String?> = operations.openWhenIdleRequest

    fun showMessage(value: String) = operations.showMessage(value)
    fun clearMessage(shown: UiMessage) = operations.clearMessage(shown)
    fun clearExportArtifact() = operations.clearExportArtifact()
    fun clearOpenDocumentRequest() = operations.clearOpenDocumentRequest()
    fun clearOpenWhenIdleRequest() = operations.clearOpenWhenIdleRequest()

    /** A scan finished while the user was busy elsewhere: say so, with a way to open it, instead of pulling them away. */
    fun announceScanSaved(id: String) {
        operations.showMessage(
            UiMessage(context.getString(R.string.doc_scan_saved), context.getString(R.string.msg_open), onAction = { operations.requestOpenDocument(id) })
        )
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
        if (stoppedAt != 0L && nowMs - stoppedAt > timeoutMs) {
            _sessionUnlocked.value = false
            folderLock.relockAll()
        }
    }

    fun requestScan() { _scanRequested.value = true }
    fun clearScanRequest() { _scanRequested.value = false }

    private val _destinationRequest = MutableStateFlow<String?>(null)
    /** A screen a notification asked to open; shown once the library is unlocked. */
    val destinationRequest: StateFlow<String?> = _destinationRequest.asStateFlow()
    fun requestDestination(route: String) { _destinationRequest.value = route }
    fun clearDestinationRequest() { _destinationRequest.value = null }
    /** A notification asked to open this document; it opens below the lock gate like a finished scan does. */
    fun requestOpenDocument(id: String) = operations.requestOpenDocument(id)

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
            operations.showMessage(context.resources.getQuantityString(R.plurals.msg_import_images_limit, MAX_IMPORT_IMAGES, MAX_IMPORT_IMAGES))
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
        crops: List<CropQuad> = emptyList(),
        filters: List<PageFilter> = emptyList(),
        adjustments: List<PageAdjustments?> = emptyList(),
        /** Typed on the review screen; null names the document automatically. */
        title: String? = null,
        /** Chosen on the review screen; null saves the document outside any folder. */
        folder: String? = null
    ) {
        if (imageUris.isEmpty()) return
        // As WorkManager work, so the scan is finished even if the app is swiped away meanwhile.
        ImportScanWorker.enqueue(context, ImportScanWorker.inputFor(imageUris, mode, crops, filters, adjustments, title, folder))
    }

    /** Pages shot from a document's Add page, added at its end with the review screen's outlines, filters and sliders. */
    fun appendPagesFromCamera(
        documentId: String,
        imageUris: List<Uri>,
        crops: List<CropQuad>,
        filters: List<PageFilter>,
        adjustments: List<PageAdjustments?>
    ) {
        if (imageUris.isEmpty()) return
        operations.enqueue(context.getString(R.string.msg_adding_pages), context.getString(R.string.msg_pages_added)) {
            try {
                repository.appendImages(documentId, imageUris, crops, filters, adjustments)
            } finally {
                imageUris.firstOrNull()?.takeIf { it.scheme == "file" }?.path?.let { File(it).parentFile }
                    ?.takeIf { it.parentFile?.name == SESSION_ROOT }?.deleteRecursively()
            }
        }
    }

    /** A page shot again from the editor: the new photo takes the old page's place, then the session is cleaned up. */
    fun replacePageFromCamera(
        target: RetakeTarget,
        imageUri: Uri,
        crop: CropQuad,
        filter: PageFilter,
        adjustments: PageAdjustments? = null
    ) {
        val number = target.position + 1
        operations.enqueue(context.getString(R.string.msg_replacing_page, number), context.getString(R.string.msg_page_retaken, number)) {
            try {
                repository.replacePage(target.documentId, target.pageId, imageUri, crop, filter, adjustments)
            } finally {
                if (imageUri.scheme == "file") imageUri.path?.let { File(it).parentFile }
                    ?.takeIf { it.parentFile?.name == SESSION_ROOT }?.deleteRecursively()
            }
        }
    }

}

private const val MAX_IMPORT_IMAGES = 100


/** Saved camera scans before the rating sheet appears (once). */
const val RATING_AFTER_SCANS = 1
