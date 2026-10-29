package com.example.scanpro.feature.document

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.operations.ExportArtifact
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.hasCurrentPdf
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    private val signatures: com.example.scanpro.data.SignatureLibrary
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle[ScanProDestinations.DOCUMENT_ID_ARG])

    val document: StateFlow<ScanDocument?> = library.documents
        .map { all -> all.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value.firstOrNull { it.id == id })

    val otherDocuments: StateFlow<List<ScanDocument>> = library.documents
        .map { all -> all.filterNot { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value.filterNot { it.id == id })

    /** True right after this document was scanned: its screen offers share, save and rename at once. */
    val scanSaved: StateFlow<Boolean> = operations.scanSaved
        .map { it == id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), operations.scanSaved.value == id)

    fun dismissScanSaved() = operations.clearScanSaved(id)

    /** Whether this document has edits to undo or redo in this session. */
    val undoState: StateFlow<com.example.scanpro.data.UndoState> = repository.undoState
        .map { it[id] ?: com.example.scanpro.data.UndoState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.undoState.value[id] ?: com.example.scanpro.data.UndoState())

    fun undo() = run("Undoing…", null) { repository.undo(id) }
    fun redo() = run("Redoing…", null) { repository.redo(id) }

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    private fun run(label: String?, successMessage: String?, action: suspend () -> Unit) =
        operations.run(label, successMessage, action)

    fun rename(title: String) = run(null, null) { repository.rename(id, title) }
    fun setTags(tags: String) = run(null, "Tags updated") { repository.setTags(id, tags) }
    fun setFolder(folder: String) = run(null, "Folder updated") { repository.setFolder(id, folder) }
    fun toggleFavorite() = run(null, null) { repository.toggleFavorite(id) }

    fun setPdfQuality(quality: PdfQuality) =
        run("Rebuilding PDF at ${quality.label} quality…", "PDF quality updated") { repository.setPdfQuality(id, quality) }

    fun setPdfPageSize(size: PdfPageSize) =
        run("Rebuilding PDF for ${size.label} pages…", "PDF page size updated") { repository.setPdfPageSize(id, size) }

    fun setSearchablePdf(enabled: Boolean) =
        run("Rebuilding OCR PDF layer…", if (enabled) "Searchable PDF enabled" else "Searchable layer disabled") {
            repository.setSearchablePdf(id, enabled)
        }

    fun setPageNumbers(enabled: Boolean) =
        run("Updating PDF page numbers…", null) { repository.setPageNumbers(id, enabled) }

    fun setScanMode(mode: ScanMode) =
        run(null, "Mode set to ${mode.label}") { repository.setScanMode(id, mode) }

    fun applyFilter(pageId: String, filter: PageFilter) =
        run("Applying ${filter.label}…", null) { repository.applyFilter(id, pageId, filter) }

    fun rotatePage(pageId: String) =
        run("Rotating page and rebuilding PDF…", null) { repository.rotatePage(id, pageId) }

    fun applyCrop(pageId: String, crop: CropQuad) =
        run("Perspective-correcting page…", "Crop applied") { repository.applyCrop(id, pageId, crop) }

    fun applyAdjustments(pageId: String, adjustments: PageAdjustments, allPages: Boolean = false) =
        if (allPages) run("Cleaning up every page…", "Clean-up applied to all pages") { repository.applyAdjustmentsToAll(id, pageId, adjustments) }
        else run("Applying image adjustments…", "Adjustments applied") { repository.applyAdjustments(id, pageId, adjustments) }

    fun resetPage(pageId: String) =
        run("Restoring original page…", "Page reset") { repository.resetPage(id, pageId) }

    fun movePage(pageId: String, delta: Int) =
        run("Reordering pages…", null) { repository.movePage(id, pageId, delta) }

    fun duplicatePage(pageId: String) =
        run("Duplicating page…", "Page duplicated") { repository.duplicatePage(id, pageId) }

    fun extractPage(pageId: String) =
        run("Extracting page into a new document…", "Page extracted") { repository.extractPage(id, pageId) }

    fun splitBookSpread(pageId: String, splitFraction: Float) =
        run("Splitting book spread into two pages…", "Book spread split") { repository.splitBookSpread(id, pageId, splitFraction) }

    /** Removes the page and offers Undo; the page's files go only once the message is gone. */
    fun deletePage(pageId: String) = run("Removing page…", null) {
        repository.deletePage(id, pageId)
        operations.showMessage(
            UiMessage(
                "Page removed",
                actionLabel = "Undo",
                onAction = { undoDeletePage() },
                onDismiss = { repository.purgeRemovedPage(id, pageId) }
            )
        )
    }

    private fun undoDeletePage() = operations.enqueue("Restoring page…", null) {
        if (!repository.restoreRemovedPage(id)) operations.showMessage("The page could not be restored")
    }

    fun duplicateDocument() = run("Duplicating document…", null) {
        val copy = repository.duplicateDocument(id)
        operations.showMessage(UiMessage("Copy saved as \"${copy.title}\"", actionLabel = "Open") { operations.requestOpenDocument(copy.id) })
    }

    fun exportPng() = run("Converting pages to PNG…", null) {
        val files = repository.exportPng(id)
        operations.publishExport(ExportArtifact(files.first().absolutePath, "image/png", "Share PNG", files.drop(1).map { it.absolutePath }))
    }

    /** Saved signatures, newest first, offered in the signature dialog. */
    val savedSignatures: StateFlow<List<java.io.File>> = signatures.items

    fun stampSignature(pageId: String, signature: Bitmap, keep: Boolean = false) =
        run("Adding signature overlay…", "Signature added") {
            if (keep) runCatching { signatures.save(signature) }
            repository.stampSignature(id, pageId, signature)
        }

    fun stampSavedSignature(pageId: String, file: java.io.File) =
        run("Adding signature overlay…", "Signature added") {
            val bitmap = ImageProcessor.decodeScaled(file.absolutePath, 1600) ?: error("Saved signature could not be read")
            try {
                repository.stampSignature(id, pageId, bitmap)
            } finally {
                bitmap.recycle()
            }
        }

    fun deleteSavedSignature(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) { signatures.delete(file) }
    }

    fun addTextAnnotation(pageId: String, text: String) =
        run("Adding text annotation…", "Text added") { repository.addTextAnnotation(id, pageId, text) }

    fun addHighlight(pageId: String) =
        run("Adding highlight…", "Highlight added") { repository.addHighlight(id, pageId) }

    fun addRedaction(pageId: String) =
        run("Applying redaction…", "Redaction added") { repository.addRedaction(id, pageId) }

    fun undoLastAnnotation(pageId: String) =
        run("Removing last annotation…", "Annotation removed") { repository.removeLastAnnotation(id, pageId) }

    suspend fun markupBase(pageId: String) = repository.markupBase(id, pageId)

    fun applyMarkup(pageId: String, annotations: List<com.example.scanpro.data.PageAnnotation>) =
        run("Updating markup…", "Markup updated") { repository.replaceAnnotations(id, pageId, annotations) }

    fun addWatermark(style: com.example.scanpro.data.WatermarkStyle) =
        run("Adding watermark to all pages…", "Watermark added") { repository.addWatermark(id, style) }

    fun appendImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        run("Adding pages and rebuilding searchable PDF…", "Pages added") { repository.appendImages(id, imageUris) }
    }

    fun mergeWith(otherId: String) =
        run("Merging documents…", "Merged copy created") { repository.mergeDocuments(id, otherId) }

    fun updatePageOcrText(pageId: String, text: String) =
        run("Updating OCR text and searchable PDF…", "OCR text updated") { repository.updatePageOcrText(id, pageId, text) }

    fun reOcr() = run("Refreshing OCR on every page…", "OCR refreshed") { repository.reOcrDocument(id) }

    fun reOcr(script: OcrScript) =
        run("Running ${script.label} OCR on every page…", "${script.label} OCR applied") { repository.reOcrDocument(id, script) }

    fun splitDocument() = run("Splitting document into individual page documents…", null) {
        val count = repository.splitIntoPageDocuments(id)
        operations.showMessage("$count page documents created")
    }

    fun exportIdCopy() = run("Creating printable ID copy…", null) {
        val file = repository.exportIdCopy(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/pdf", "Share ID copy"))
    }

    fun exportLongImage() = run("Creating long image…", null) {
        val file = repository.exportLongImage(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "image/jpeg", "Share long image"))
    }

    fun exportOcrTextFile() = run("Creating text export…", null) {
        val file = repository.exportOcrText(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "text/plain", "Share OCR text file"))
    }

    fun exportWordFile() = run("Creating Word document…", null) {
        val file = repository.exportWord(id)
        operations.publishExport(ExportArtifact(file.absolutePath, DOCX_MIME, "Share Word document"))
    }

    fun exportExcelFile() = run("Creating Excel workbook…", null) {
        val file = repository.exportExcel(id)
        operations.publishExport(ExportArtifact(file.absolutePath, XLSX_MIME, "Share Excel workbook"))
    }

    fun replaceText(pageId: String, edit: TextEdit) =
        run("Replacing text…", if (edit.text.isBlank()) "Text removed" else "Text replaced") {
            repository.replaceText(id, pageId, edit.left, edit.top, edit.right, edit.bottom, edit.text)
        }

    fun erase(pageId: String, strokesByBrush: Map<Float, List<List<com.example.scanpro.data.NormalizedPoint>>>) =
        run("Erasing…", "Erased") { repository.erase(id, pageId, strokesByBrush) }

    fun exportPasswordProtectedPdf(password: String) = run("Encrypting PDF…", null) {
        val file = repository.exportPasswordProtectedPdf(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/pdf", "Share protected PDF"))
    }

    fun exportEncryptedBackup(password: String) = run("Encrypting document backup…", null) {
        val file = repository.exportEncryptedBackup(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/octet-stream", "Share encrypted ScanPro backup"))
    }

    fun moveToTrash() = run(null, null) {
        repository.moveToTrash(id)
        operations.showMessage(
            UiMessage("Moved to recycle bin", actionLabel = "Undo") {
                operations.enqueue(null, "Document restored") { repository.restore(id) }
            }
        )
    }

    /**
     * Runs [onReady] with a document whose PDF is current. Page edits defer the PDF rebuild, so share,
     * open, print and save go through here; when the PDF is already current this is immediate.
     */
    fun withFreshPdf(onFailed: () -> Unit = {}, onReady: (ScanDocument) -> Unit) {
        val current = document.value
        if (current == null) {
            onFailed()
            return
        }
        if (current.hasCurrentPdf()) {
            onReady(current)
            return
        }
        // Queued rather than rejected while busy: by now the user has already asked for the file
        // (and for Save, already created the destination), so dropping the request would lose it.
        operations.enqueue("Preparing PDF…", null) {
            val fresh = try {
                repository.ensurePdf(id)
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) { onFailed() }
                throw error
            }
            withContext(Dispatchers.Main) { onReady(fresh) }
        }
    }

    override fun onCleared() {
        // Leaving without closing the "Scan saved" sheet: don't offer it again next time this document opens.
        operations.clearScanSaved(id)
        // Signatures removed while editing were kept for Undo; drop the ones no step can bring back.
        operations.enqueueQuiet { repository.cleanUnusedSignatures(id) }
        // Leaving the editor: bring the PDF up to date in the background so it's ready next time.
        library.buildPdfInBackground(id)
    }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
