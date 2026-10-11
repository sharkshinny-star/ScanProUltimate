package com.example.scanpro

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


data class ExportArtifact(
    val path: String,
    val mimeType: String,
    val title: String
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val settings: SettingsStore
) : ViewModel() {
    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    private val _trash = MutableStateFlow<List<ScanDocument>>(emptyList())
    val trash: StateFlow<List<ScanDocument>> = _trash.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportArtifact = MutableStateFlow<ExportArtifact?>(null)
    val exportArtifact: StateFlow<ExportArtifact?> = _exportArtifact.asStateFlow()

    private val _newScanId = MutableStateFlow<String?>(null)
    val newScanId: StateFlow<String?> = _newScanId.asStateFlow()
    fun clearNewScanId() { _newScanId.value = null }

    val defaultPdfQuality = settings.pdfQuality.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PdfQuality.BALANCED
    )
    val autoTitle = settings.autoTitle.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val appLockEnabled = settings.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _documents.value = if (_query.value.isBlank()) repository.loadAll() else repository.search(_query.value)
            _trash.value = repository.loadTrash()
            _storageBytes.value = repository.storageBytes()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180)
            _documents.value = if (value.isBlank()) repository.loadAll() else repository.search(value)
        }
    }

    fun clearMessage() { _message.value = null }
    fun showMessage(value: String) { _message.value = value }
    fun clearExportArtifact() { _exportArtifact.value = null }

    fun importScan(pdfUri: Uri?, imageUris: List<Uri>) {
        launchBusy("Enhancing pages, generating OCR, and building searchable PDF…", "Scan saved") {
            repository.importScan(pdfUri, imageUris, defaultPdfQuality.value, autoTitle.value)
        }
    }

    fun importImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        launchBusy("Importing pages, enhancing them, and running OCR…", "Document saved") {
            repository.importImages(imageUris, defaultPdfQuality.value, autoTitle.value)
        }
    }

    fun importPdf(uri: Uri) {
        launchBusy("Importing PDF pages and building OCR index…", "PDF imported") {
            repository.importPdf(uri, defaultPdfQuality.value, autoTitle.value)
        }
    }

    fun importCameraSession(imageUris: List<Uri>, mode: ScanMode) {
        if (imageUris.isEmpty()) return
        launchBusy("Finishing ${mode.label.lowercase()} scan, enhancing pages, and running OCR…", "Scan saved") {
            val newDocument = repository.importImages(imageUris, defaultPdfQuality.value, autoTitle.value, mode)
            _newScanId.value = newDocument.id
        }
    }

    fun appendImages(documentId: String, imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        launchBusy("Adding pages and rebuilding searchable PDF…", "Pages added") {
            repository.appendImages(documentId, imageUris)
        }
    }

    fun rename(id: String, title: String) = launchBusy(null, null) { repository.rename(id, title) }
    fun setTags(id: String, tags: String) = launchBusy(null, "Tags updated") { repository.setTags(id, tags) }
    fun setFolder(id: String, folder: String) = launchBusy(null, "Folder updated") { repository.setFolder(id, folder) }
    fun renameFolder(oldName: String, newName: String) =
        launchBusy("Renaming folder…", "Folder renamed") { repository.renameFolder(oldName, newName) }
    fun toggleFavorite(id: String) = launchBusy(null, null) { repository.toggleFavorite(id) }

    fun setDocumentPdfQuality(id: String, quality: PdfQuality) =
        launchBusy("Rebuilding PDF at ${quality.label} quality…", "PDF quality updated") {
            repository.setPdfQuality(id, quality)
        }

    fun setDocumentPdfPageSize(id: String, size: PdfPageSize) =
        launchBusy("Rebuilding PDF for ${size.label} pages…", "PDF page size updated") {
            repository.setPdfPageSize(id, size)
        }

    fun setSearchablePdf(id: String, enabled: Boolean) =
        launchBusy("Rebuilding OCR PDF layer…", if (enabled) "Searchable PDF enabled" else "Searchable layer disabled") {
            repository.setSearchablePdf(id, enabled)
        }

    fun setPageNumbers(id: String, enabled: Boolean) =
        launchBusy("Updating PDF page numbers…", null) { repository.setPageNumbers(id, enabled) }

    fun setScanMode(id: String, mode: ScanMode) =
        launchBusy(null, "Mode set to ${mode.label}") { repository.setScanMode(id, mode) }

    fun applyFilter(documentId: String, pageId: String, filter: PageFilter) =
        launchBusy("Applying ${filter.label}…", null) { repository.applyFilter(documentId, pageId, filter) }

    fun rotatePage(documentId: String, pageId: String) =
        launchBusy("Rotating page and rebuilding PDF…", null) { repository.rotatePage(documentId, pageId) }

    fun applyCrop(documentId: String, pageId: String, crop: CropQuad) =
        launchBusy("Perspective-correcting page…", "Crop applied") { repository.applyCrop(documentId, pageId, crop) }

    fun applyAdjustments(documentId: String, pageId: String, adjustments: PageAdjustments) =
        launchBusy("Applying image adjustments…", "Adjustments applied") { repository.applyAdjustments(documentId, pageId, adjustments) }

    fun resetPage(documentId: String, pageId: String) =
        launchBusy("Restoring original page…", "Page reset") { repository.resetPage(documentId, pageId) }

    fun movePage(documentId: String, pageId: String, delta: Int) =
        launchBusy("Reordering pages…", null) { repository.movePage(documentId, pageId, delta) }

    fun duplicatePage(documentId: String, pageId: String) =
        launchBusy("Duplicating page…", "Page duplicated") { repository.duplicatePage(documentId, pageId) }

    fun extractPage(documentId: String, pageId: String) =
        launchBusy("Extracting page into a new document…", "Page extracted") { repository.extractPage(documentId, pageId) }

    fun splitBookSpread(documentId: String, pageId: String) =
        launchBusy("Splitting book spread into two pages…", "Book spread split") {
            repository.splitBookSpread(documentId, pageId)
        }

    fun deletePage(documentId: String, pageId: String) =
        launchBusy("Removing page…", "Page removed") { repository.deletePage(documentId, pageId) }

    fun stampSignature(documentId: String, pageId: String, signature: Bitmap) =
        launchBusy("Adding signature overlay…", "Signature added") {
            repository.stampSignature(documentId, pageId, signature)
        }

    fun addTextAnnotation(documentId: String, pageId: String, text: String) =
        launchBusy("Adding text annotation…", "Text added") { repository.addTextAnnotation(documentId, pageId, text) }

    fun addHighlight(documentId: String, pageId: String) =
        launchBusy("Adding highlight…", "Highlight added") { repository.addHighlight(documentId, pageId) }

    fun addRedaction(documentId: String, pageId: String) =
        launchBusy("Applying redaction…", "Redaction added") { repository.addRedaction(documentId, pageId) }

    fun undoLastAnnotation(documentId: String, pageId: String) =
        launchBusy("Removing last annotation…", "Annotation removed") { repository.removeLastAnnotation(documentId, pageId) }

    fun addWatermark(documentId: String, text: String) =
        launchBusy("Adding watermark to all pages…", "Watermark added") { repository.addWatermark(documentId, text) }

    fun mergeDocuments(primaryId: String, secondaryId: String) =
        launchBusy("Merging documents…", "Merged copy created") { repository.mergeDocuments(primaryId, secondaryId) }

    fun updatePageOcrText(documentId: String, pageId: String, text: String) =
        launchBusy("Updating OCR text and searchable PDF…", "OCR text updated") {
            repository.updatePageOcrText(documentId, pageId, text)
        }

    fun reOcrDocument(id: String) =
        launchBusy("Refreshing OCR on every page…", "OCR refreshed") { repository.reOcrDocument(id) }

    fun reOcrDocument(id: String, script: OcrScript) =
        launchBusy("Running ${script.label} OCR on every page…", "${script.label} OCR applied") { repository.reOcrDocument(id, script) }

    fun moveManyToTrash(ids: Set<String>) {
        if (ids.isEmpty()) return
        launchBusy("Moving ${ids.size} documents to recycle bin…", "${ids.size} documents moved to recycle bin") {
            ids.forEach { repository.moveToTrash(it) }
        }
    }

    fun moveToTrash(id: String) = launchBusy(null, "Moved to recycle bin") { repository.moveToTrash(id) }
    fun restore(id: String) = launchBusy(null, "Document restored") { repository.restore(id) }
    fun deleteForever(id: String) = launchBusy(null, "Document permanently deleted") { repository.deleteForever(id) }
    fun emptyTrash() = launchBusy("Emptying recycle bin…", "Recycle bin emptied") { repository.emptyTrash() }

    fun exportIdCopy(id: String) {
        launchBusy("Creating printable ID copy…", null) {
            val file = repository.exportIdCopy(id)
            _exportArtifact.value = ExportArtifact(file.absolutePath, "application/pdf", "Share ID copy")
        }
    }

    fun exportLongImage(id: String) {
        launchBusy("Creating long image…", null) {
            val file = repository.exportLongImage(id)
            _exportArtifact.value = ExportArtifact(file.absolutePath, "image/jpeg", "Share long image")
        }
    }

    fun exportOcrTextFile(id: String) {
        launchBusy("Creating text export…", null) {
            val file = repository.exportOcrText(id)
            _exportArtifact.value = ExportArtifact(file.absolutePath, "text/plain", "Share OCR text file")
        }
    }

    fun splitDocument(id: String) {
        launchBusy("Splitting document into individual page documents…", null) {
            val count = repository.splitIntoPageDocuments(id)
            _message.value = "$count page documents created"
        }
    }

    fun exportPasswordProtectedPdf(id: String, password: String) {
        launchBusy("Encrypting PDF…", null) {
            val file = repository.exportPasswordProtectedPdf(id, password)
            _exportArtifact.value = ExportArtifact(file.absolutePath, "application/pdf", "Share protected PDF")
        }
    }

    fun exportEncryptedBackup(id: String, password: String) {
        launchBusy("Encrypting document backup…", null) {
            val file = repository.exportEncryptedBackup(id, password)
            _exportArtifact.value = ExportArtifact(file.absolutePath, "application/octet-stream", "Share encrypted ScanPro backup")
        }
    }

    fun importEncryptedBackup(uri: Uri, password: String) {
        launchBusy("Decrypting and restoring backup…", "Encrypted backup restored") {
            repository.importEncryptedBackup(uri, password)
        }
    }

    fun setDefaultPdfQuality(value: PdfQuality) { viewModelScope.launch { settings.setPdfQuality(value) } }
    fun setAutoTitle(value: Boolean) { viewModelScope.launch { settings.setAutoTitle(value) } }
    fun setAppLock(value: Boolean) { viewModelScope.launch { settings.setAppLock(value) } }

    private fun launchBusy(label: String?, successMessage: String?, action: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _busyLabel.value = label
            runCatching { action() }
                .onSuccess {
                    _documents.value = if (_query.value.isBlank()) repository.loadAll() else repository.search(_query.value)
                    _trash.value = repository.loadTrash()
                    _storageBytes.value = repository.storageBytes()
                    if (successMessage != null) _message.value = successMessage
                }
                .onFailure { error -> _message.value = error.message ?: "Something went wrong" }
            _busyLabel.value = null
            _busy.value = false
        }
    }
}
