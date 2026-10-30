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
import com.example.scanpro.data.DocumentStamp
import com.example.scanpro.data.DocumentSummary
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    private val library: DocumentLibrary,
    private val signatures: com.example.scanpro.data.SignatureLibrary
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

    val otherDocuments: StateFlow<List<DocumentSummary>> = library.overview
        .map { repository.loadOtherSummaries(id, OTHER_DOCUMENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True right after this document was scanned: its screen offers share, save and rename at once. */
    val scanSaved: StateFlow<Boolean> = operations.scanSaved
        .map { it == id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), operations.scanSaved.value == id)

    fun dismissScanSaved() = operations.clearScanSaved(id)

    /** Whether this document has edits to undo or redo in this session. */
    val undoState: StateFlow<com.example.scanpro.data.UndoState> = repository.undoState
        .map { it[id] ?: com.example.scanpro.data.UndoState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.undoState.value[id] ?: com.example.scanpro.data.UndoState())

    fun undo() = run(R.string.msg_undoing, null) { repository.undo(id) }
    fun redo() = run(R.string.msg_redoing, null) { repository.redo(id) }

    val busy: StateFlow<Boolean> = operations.busy
    val busyLabel: StateFlow<String?> = operations.busyLabel

    private fun run(@StringRes label: Int?, @StringRes successMessage: Int?, action: suspend () -> Unit) =
        operations.run(label?.let { context.getString(it) }, successMessage?.let { context.getString(it) }, action)

    fun rename(title: String) = run(null, null) {
        if (repository.rename(id, title)) operations.showMessage(context.getString(R.string.msg_renamed_duplicate_name))
    }

    fun stopCurrent() = operations.cancelCurrent()
    fun setTags(tags: String) = run(null, R.string.msg_tags_updated) { repository.setTags(id, tags) }
    fun setFolder(folder: String) = run(null, R.string.msg_folder_updated) { repository.setFolder(id, folder) }
    fun toggleFavorite() = run(null, null) { repository.toggleFavorite(id) }

    fun setPdfQuality(quality: PdfQuality) =
        operations.run(context.getString(R.string.msg_rebuilding_pdf_quality, context.getString(quality.labelRes)), context.getString(R.string.msg_pdf_quality_updated)) { repository.setPdfQuality(id, quality) }

    fun setPdfPageSize(size: PdfPageSize) =
        operations.run(context.getString(R.string.msg_rebuilding_pdf_page_size, context.getString(size.labelRes)), context.getString(R.string.msg_pdf_page_size_updated)) { repository.setPdfPageSize(id, size) }

    fun setSearchablePdf(enabled: Boolean) =
        run(R.string.msg_rebuilding_ocr_layer, if (enabled) R.string.msg_searchable_pdf_enabled else R.string.msg_searchable_layer_disabled) {
            repository.setSearchablePdf(id, enabled)
        }

    fun setPageNumbers(enabled: Boolean) =
        run(R.string.msg_updating_page_numbers, null) { repository.setPageNumbers(id, enabled) }

    fun setScanMode(mode: ScanMode) =
        operations.run(null, context.getString(R.string.msg_mode_set_to, context.getString(mode.labelRes))) { repository.setScanMode(id, mode) }

    fun applyFilter(pageId: String, filter: PageFilter) =
        operations.run(context.getString(R.string.msg_applying_filter, context.getString(filter.labelRes)), null) { repository.applyFilter(id, pageId, filter) }

    fun rotatePage(pageId: String) =
        run(R.string.msg_rotating_page, null) { repository.rotatePage(id, pageId) }

    fun applyCrop(pageId: String, crop: CropQuad) =
        run(R.string.msg_perspective_correcting, R.string.msg_crop_applied) { repository.applyCrop(id, pageId, crop) }

    fun applyAdjustments(pageId: String, adjustments: PageAdjustments, allPages: Boolean = false) =
        if (allPages) run(R.string.msg_cleaning_up_every_page, R.string.msg_cleanup_applied_all) { repository.applyAdjustmentsToAll(id, pageId, adjustments) }
        else run(R.string.msg_applying_adjustments, R.string.msg_adjustments_applied) { repository.applyAdjustments(id, pageId, adjustments) }

    fun resetPage(pageId: String) =
        run(R.string.msg_restoring_original_page, R.string.msg_page_reset) { repository.resetPage(id, pageId) }

    fun movePage(pageId: String, delta: Int) =
        run(R.string.msg_reordering_pages, null) { repository.movePage(id, pageId, delta) }

    fun duplicatePage(pageId: String) =
        run(R.string.msg_duplicating_page, R.string.msg_page_duplicated) { repository.duplicatePage(id, pageId) }

    fun extractPage(pageId: String) =
        run(R.string.msg_extracting_page, R.string.msg_page_extracted) { repository.extractPage(id, pageId) }

    fun splitBookSpread(pageId: String, splitFraction: Float) =
        run(R.string.msg_splitting_book_spread, R.string.msg_book_spread_split) { repository.splitBookSpread(id, pageId, splitFraction) }

    /** Removes the page and offers Undo; the page's files go only once the message is gone. */
    fun deletePage(pageId: String) = run(R.string.msg_removing_page, null) {
        repository.deletePage(id, pageId)
        operations.showMessage(
            UiMessage(
                context.getString(R.string.msg_page_removed),
                actionLabel = context.getString(R.string.common_undo),
                onAction = { undoDeletePage() },
                onDismiss = { repository.purgeRemovedPage(id, pageId) }
            )
        )
    }

    private fun undoDeletePage() = operations.enqueue(context.getString(R.string.msg_restoring_page), null) {
        if (!repository.restoreRemovedPage(id)) operations.showMessage(context.getString(R.string.msg_page_restore_failed))
    }

    fun duplicateDocument() = run(R.string.msg_duplicating_document, null) {
        val copy = repository.duplicateDocument(id)
        operations.showMessage(
            UiMessage(context.getString(R.string.msg_copy_saved_as, copy.title), actionLabel = context.getString(R.string.msg_open), onAction = {
                operations.requestOpenDocument(copy.id)
            })
        )
    }

    fun exportPng() = run(R.string.msg_converting_to_png, null) {
        val files = repository.exportPng(id)
        operations.publishExport(
            ExportArtifact(files.first().absolutePath, "image/png", context.getString(R.string.msg_share_png), files.drop(1).map { it.absolutePath })
        )
    }

    /** Saved signatures, newest first, offered in the signature dialog. */
    val savedSignatures: StateFlow<List<java.io.File>> = signatures.items

    fun stampSignature(pageId: String, signature: Bitmap, keep: Boolean = false) =
        run(R.string.msg_adding_signature, R.string.msg_signature_added) {
            if (keep) runCatching { signatures.save(signature) }
            repository.stampSignature(id, pageId, signature)
        }

    fun stampSavedSignature(pageId: String, file: java.io.File) =
        run(R.string.msg_adding_signature, R.string.msg_signature_added) {
            val bitmap = ImageProcessor.decodeScaled(file.absolutePath, 1600) ?: error(context.getString(R.string.msg_saved_signature_unreadable))
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
        run(R.string.msg_adding_text_annotation, R.string.msg_text_added) { repository.addTextAnnotation(id, pageId, text) }

    fun addHighlight(pageId: String) =
        run(R.string.msg_adding_highlight, R.string.msg_highlight_added) { repository.addHighlight(id, pageId) }

    fun addRedaction(pageId: String) =
        run(R.string.msg_applying_redaction, R.string.msg_redaction_added) { repository.addRedaction(id, pageId) }

    fun undoLastAnnotation(pageId: String) =
        run(R.string.msg_removing_last_annotation, R.string.msg_annotation_removed) { repository.removeLastAnnotation(id, pageId) }

    suspend fun markupBase(pageId: String) = repository.markupBase(id, pageId)

    fun applyMarkup(pageId: String, annotations: List<com.example.scanpro.data.PageAnnotation>) =
        run(R.string.msg_updating_markup, R.string.msg_markup_updated) { repository.replaceAnnotations(id, pageId, annotations) }

    fun addWatermark(style: com.example.scanpro.data.WatermarkStyle) =
        run(R.string.msg_adding_watermark, R.string.msg_watermark_added) { repository.addWatermark(id, style) }

    fun appendImages(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        run(R.string.msg_adding_pages, R.string.msg_pages_added) { repository.appendImages(id, imageUris) }
    }

    fun mergeWith(otherId: String) =
        run(R.string.msg_merging_documents, R.string.msg_merged_copy_created) { repository.mergeDocuments(id, otherId) }

    fun updatePageOcrText(pageId: String, text: String) =
        run(R.string.msg_updating_ocr_text, R.string.msg_ocr_text_updated) { repository.updatePageOcrText(id, pageId, text) }

    fun reOcr() = run(R.string.msg_refreshing_ocr, null) { reportOcr(repository.reOcrDocument(id), context.getString(R.string.msg_ocr_refreshed)) }

    fun reOcr(script: OcrScript) =
        operations.run(context.getString(R.string.msg_running_script_ocr, context.getString(script.labelRes)), null) {
            reportOcr(repository.reOcrDocument(id, script), context.getString(R.string.msg_script_ocr_applied, context.getString(script.labelRes)))
        }

    private fun reportOcr(failed: Int, success: String) {
        val total = document.value?.pages?.size ?: 0
        operations.showMessage(if (failed == 0) success else context.getString(R.string.msg_ocr_partial, total - failed, total))
    }

    fun splitDocument() = run(R.string.msg_splitting_document, null) {
        val count = repository.splitIntoPageDocuments(id)
        operations.showMessage(context.getString(R.string.msg_page_documents_created, count))
    }

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

    fun replaceText(pageId: String, edit: TextEdit) =
        run(R.string.msg_replacing_text, if (edit.text.isBlank()) R.string.msg_text_removed else R.string.msg_text_replaced) {
            repository.replaceText(id, pageId, edit.left, edit.top, edit.right, edit.bottom, edit.text)
        }

    fun erase(pageId: String, strokesByBrush: Map<Float, List<List<com.example.scanpro.data.NormalizedPoint>>>) =
        run(R.string.msg_erasing, R.string.msg_erased) { repository.erase(id, pageId, strokesByBrush) }

    fun exportPasswordProtectedPdf(password: String) = run(R.string.msg_encrypting_pdf, null) {
        val file = repository.exportPasswordProtectedPdf(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/pdf", context.getString(R.string.msg_share_protected_pdf)))
    }

    fun exportEncryptedBackup(password: String) = run(R.string.msg_encrypting_backup, null) {
        val file = repository.exportEncryptedBackup(id, password)
        operations.publishExport(ExportArtifact(file.absolutePath, "application/octet-stream", context.getString(R.string.msg_share_encrypted_backup)))
    }

    // Queued, not rejected while busy: the screen closes as soon as this is tapped, so a dropped request would
    // leave the document in the library with nothing but a "still working" message to show for it. Always true,
    // so the screen can close straight away.
    fun moveToTrash(): Boolean {
        moveToTrashQueued()
        return true
    }

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
