package com.example.scanpro.feature.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.ExportArtifact
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.DocumentStamp
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SignatureLibrary
import com.example.scanpro.data.UndoState
import com.example.scanpro.data.WatermarkStyle
import com.example.scanpro.data.hasCurrentPdf
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.navigation.ScanProDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val folderLock: com.example.scanpro.core.security.FolderLock,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    private val signatures: SignatureLibrary,
    private val editor: DocumentEditor
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle[ScanProDestinations.DOCUMENT_ID_ARG])

    // Changes whenever the document is edited, saved or rebuilt, so the pages are reloaded only then.
    // UNKNOWN until the first lookup; null once the document is gone or in the recycle bin.
    private val stamp: StateFlow<DocumentStamp?> = library.overview
        .map { repository.activeStamp(id) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UNKNOWN)

    /** True once the library no longer lists this document (trashed or deleted elsewhere). */
    val missing: StateFlow<Boolean> = stamp
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // The library holds summaries without pages; the full document is read here, and again whenever the
    // stamp changes (every edit bumps updatedAt).
    val document: StateFlow<ScanDocument?> = stamp
        .map { current -> if (current == null || current === UNKNOWN) null else repository.loadDocument(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The library's summary of this document when Home already holds it (its Recent list), so the screen can
     * show the title and cover the moment it opens, while the pages are still being read.
     */
    val preview: StateFlow<DocumentSummary?> = library.overview
        .map { overview -> overview.recent.firstOrNull { it.id == id } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.overview.value.recent.firstOrNull { it.id == id })

    val otherDocuments: StateFlow<List<DocumentSummary>> = library.overview
        .map { repository.loadOtherSummaries(id, OTHER_DOCUMENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True right after this document was scanned: its screen offers share, save and rename at once. */
    val scanSaved: StateFlow<Boolean> = operations.scanSaved
        .map { it == id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), operations.scanSaved.value == id)

    fun dismissScanSaved() = operations.clearScanSaved(id)

    /** Whether this document has edits to undo or redo in this session. */
    val undoState: StateFlow<UndoState> = repository.undoState
        .map { it[id] ?: UndoState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.undoState.value[id] ?: UndoState())

    fun undo() = run(R.string.msg_undoing, null) { repository.undo(id) }
    fun redo() = run(R.string.msg_redoing, null) { repository.redo(id) }

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    private fun run(@StringRes label: Int?, @StringRes successMessage: Int?, action: suspend () -> Unit) =
        operations.run(label?.let { context.getString(it) }, successMessage?.let { context.getString(it) }, action)

    fun rename(title: String) = editor.submit(id, DocumentEdit.Rename(title))

    fun stopCurrent() = operations.cancelCurrent()
    fun setTags(tags: String) = editor.submit(id, DocumentEdit.SetTags(tags))
    fun setFolder(folder: String) = editor.submit(id, DocumentEdit.SetFolder(folder))
    fun toggleFavorite() = editor.submit(id, DocumentEdit.ToggleFavorite)

    fun setPdfQuality(quality: PdfQuality) = editor.submit(id, DocumentEdit.SetPdfQuality(quality))

    fun setPdfPageSize(size: PdfPageSize) = editor.submit(id, DocumentEdit.SetPdfPageSize(size))

    fun setSearchablePdf(enabled: Boolean) = editor.submit(id, DocumentEdit.SetSearchablePdf(enabled))

    fun setPageNumbers(enabled: Boolean) = editor.submit(id, DocumentEdit.SetPageNumbers(enabled))

    fun setScanMode(mode: ScanMode) = editor.submit(id, DocumentEdit.SetScanMode(mode))

    fun applyFilter(pageId: String, filter: PageFilter) = editor.submit(id, DocumentEdit.ApplyFilter(pageId, filter))

    fun rotatePage(pageId: String) = editor.submit(id, DocumentEdit.RotatePage(pageId))

    fun applyCrop(pageId: String, crop: CropQuad) = editor.submit(id, DocumentEdit.ApplyCrop(pageId, crop))

    fun applyAdjustments(pageId: String, adjustments: PageAdjustments, allPages: Boolean = false) =
        editor.submit(id, DocumentEdit.ApplyAdjustments(pageId, adjustments, allPages))

    fun resetPage(pageId: String) = editor.submit(id, DocumentEdit.ResetPage(pageId))

    fun movePage(pageId: String, delta: Int) = editor.submit(id, DocumentEdit.MovePage(pageId, delta))

    fun duplicatePage(pageId: String) = editor.submit(id, DocumentEdit.DuplicatePage(pageId))

    fun extractPage(pageId: String) = editor.submit(id, DocumentEdit.ExtractPage(pageId))

    fun splitBookSpread(pageId: String, splitFraction: Float) = editor.submit(id, DocumentEdit.SplitBookSpread(pageId, splitFraction))

    /** Removes the page and offers Undo; the page's files go only once the message is gone. */
    fun deletePage(pageId: String) = editor.submit(id, DocumentEdit.DeletePage(pageId))

    fun duplicateDocument() = editor.submit(id, DocumentEdit.DuplicateDocument)

    fun exportPng() = run(R.string.msg_converting_to_png, null) {
        val files = repository.exportPng(id)
        operations.publishExport(
            ExportArtifact(files.first().absolutePath, "image/png", context.getString(R.string.msg_share_png), files.drop(1).map { it.absolutePath })
        )
    }

    /** Saved signatures, newest first, offered in the signature dialog. */
    /** Locked folders not opened this session (lower-cased names). */
    val hiddenFolders: StateFlow<Set<String>> = folderLock.hidden
    fun unlockFolder(name: String) = folderLock.unlockForSession(name)

    val savedSignatures: StateFlow<List<java.io.File>> = signatures.items

    fun stampSignature(pageId: String, signature: Bitmap, keep: Boolean = false, placement: SignaturePlacement? = null) =
        run(R.string.msg_adding_signature, R.string.msg_signature_added) {
            if (keep) runCatching { signatures.save(signature) }
            repository.stampSignature(id, pageId, signature, placement)
        }

    fun stampSavedSignature(pageId: String, file: java.io.File, placement: SignaturePlacement? = null) =
        run(R.string.msg_adding_signature, R.string.msg_signature_added) {
            val bitmap = ImageProcessor.decodeScaled(file.absolutePath, 1600) ?: error(context.getString(R.string.msg_saved_signature_unreadable))
            try {
                repository.stampSignature(id, pageId, bitmap, placement)
            } finally {
                bitmap.recycle()
            }
        }

    fun deleteSavedSignature(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) { signatures.delete(file) }
    }

    fun addTextAnnotation(pageId: String, text: String) = editor.submit(id, DocumentEdit.AddTextAnnotation(pageId, text))

    fun addHighlight(pageId: String) = editor.submit(id, DocumentEdit.AddHighlight(pageId))

    fun addRedaction(pageId: String) = editor.submit(id, DocumentEdit.AddRedaction(pageId))

    fun undoLastAnnotation(pageId: String) = editor.submit(id, DocumentEdit.RemoveLastAnnotation(pageId))

    suspend fun markupBase(pageId: String) = repository.markupBase(id, pageId)

    fun applyMarkup(pageId: String, annotations: List<PageAnnotation>) = editor.submit(id, DocumentEdit.ReplaceAnnotations(pageId, annotations))

    fun addWatermark(style: WatermarkStyle) = editor.submit(id, DocumentEdit.AddWatermark(style))

    fun appendImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        run(R.string.msg_adding_pages, R.string.msg_pages_added) { repository.appendImages(id, imageUris) }
    }

    fun mergeWith(otherId: String) = editor.submit(id, DocumentEdit.MergeWith(otherId))

    fun updatePageOcrText(pageId: String, text: String) = editor.submit(id, DocumentEdit.UpdatePageOcrText(pageId, text))

    fun reOcr() = editor.submit(id, DocumentEdit.ReOcr(null))

    fun reOcr(script: OcrScript) = editor.submit(id, DocumentEdit.ReOcr(script))

    fun splitDocument() = editor.submit(id, DocumentEdit.SplitDocument)

    fun exportIdCopy() = run(R.string.msg_creating_id_copy, null) {
        val file = repository.exportIdCopy(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/pdf", context.getString(R.string.msg_share_id_copy)))
    }

    fun exportLongImage() = run(R.string.msg_creating_long_image, null) {
        val file = repository.exportLongImage(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "image/jpeg", context.getString(R.string.msg_share_long_image)))
    }

    fun exportOcrTextFile() = run(R.string.msg_creating_text_export, null) {
        val file = repository.exportOcrText(id)
        operations.publishExport(ExportArtifact(file.absolutePath, "text/plain", context.getString(R.string.msg_share_ocr_text_file)))
    }

    fun exportWordFile() = run(R.string.msg_creating_word_document, null) {
        val file = repository.exportWord(id)
        operations.publishExport(ExportArtifact(file.absolutePath, DOCX_MIME, context.getString(R.string.msg_share_word_document)))
    }

    fun exportExcelFile() = run(R.string.msg_creating_excel_workbook, null) {
        val file = repository.exportExcel(id)
        operations.publishExport(ExportArtifact(file.absolutePath, XLSX_MIME, context.getString(R.string.msg_share_excel_workbook)))
    }

    fun replaceText(pageId: String, edit: TextEdit) = editor.submit(id, DocumentEdit.ReplaceText(pageId, edit))

    fun erase(pageId: String, strokesByBrush: Map<Float, List<List<NormalizedPoint>>>) = editor.submit(id, DocumentEdit.Erase(pageId, strokesByBrush))

    fun exportPasswordProtectedPdf(password: String) = run(R.string.msg_encrypting_pdf, null) {
        val file = repository.exportPasswordProtectedPdf(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/pdf", context.getString(R.string.msg_share_protected_pdf)))
    }

    fun exportEncryptedBackup(password: String) = run(R.string.msg_encrypting_backup, null) {
        val file = repository.exportEncryptedBackup(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/octet-stream", context.getString(R.string.msg_share_encrypted_backup)))
    }

    // Queued, not rejected while busy: the screen closes as soon as this is tapped, so a dropped request would
    // leave the document in the library with nothing but a "still working" message to show for it, so the screen
    // can close straight away.
    fun moveToTrash() = moveToTrashQueued()

    private fun moveToTrashQueued() = operations.enqueue(null, null) {
        repository.moveToTrash(id)
        operations.showMessage(
            UiMessage(context.getString(R.string.msg_moved_to_recycle_bin), actionLabel = context.getString(R.string.common_undo), onAction = {
                operations.enqueue(null, context.getString(R.string.msg_document_restored)) { repository.restore(id) }
            })
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
        operations.enqueue(context.getString(R.string.msg_preparing_pdf), null) {
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

    private companion object {
        /** Stands for "not looked up yet", as opposed to null, which means the document is gone. */
        val UNKNOWN = DocumentStamp(updatedAt = -1L, sizeBytes = -1L)
        /** The merge picker lists this many other documents. */
        const val OTHER_DOCUMENT_LIMIT = 12
    }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
