package com.example.scanpro.feature.document

import android.content.Context
import com.example.scanpro.R
import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.core.operations.EditJournal
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.OperationSkipped
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [DocumentEdit]s through [OperationRunner]'s queue and keeps each one in an [EditJournal] until it is done, so
 * edits still waiting their turn survive the app being stopped. The next time the app opens, [resumeInterrupted]
 * queues them again in their original order. An edit that had already started is never run twice, since it may be
 * half applied; the user is told to check the document instead.
 */
@Singleton
class DocumentEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val journal = EditJournal(File(context.filesDir, JOURNAL_DIR))
    // One thread for every journal write, in call order, so a record is always on disk before its edit marks it started.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val journalThread = Dispatchers.IO.limitedParallelism(1)
    private val resumed = AtomicBoolean(false)

    /** Queues [edit] on [documentId], recording it first. */
    fun submit(documentId: String, edit: DocumentEdit) {
        val seq = CompletableSeq()
        scope.launch(journalThread) {
            seq.value = runCatching { journal.add(DocumentEdit.encode(documentId, edit)) }.getOrNull()
        }
        queue(documentId, edit) { seq.value }
    }

    /** Queues the edits a previous run of the app left waiting. Runs once per process; later calls do nothing. */
    fun resumeInterrupted() {
        if (!resumed.compareAndSet(false, true)) return
        scope.launch(journalThread) {
            val recovered = runCatching { journal.recover() }.getOrNull() ?: return@launch
            if (recovered.interrupted > 0) {
                operations.showMessage(
                    context.resources.getQuantityString(R.plurals.edits_msg_interrupted, recovered.interrupted, recovered.interrupted)
                )
            }
            recovered.queued.forEach { (seq, payload) ->
                val decoded = DocumentEdit.decode(payload)
                if (decoded == null) {
                    journal.remove(seq)
                } else {
                    queue(decoded.first, decoded.second) { seq }
                }
            }
        }
    }

    private fun queue(documentId: String, edit: DocumentEdit, seq: () -> Long?) {
        val (label, success) = messages(edit)
        operations.run(label, success) {
            // Same thread as the write, so this waits for the record to exist. NonCancellable: a Stop that lands
            // here must not leave the record unmarked or, in finally, undeleted (it would be reported as interrupted).
            var number: Long? = null
            try {
                withContext(NonCancellable + journalThread) { number = seq()?.also { runCatching { journal.markStarted(it) } } }
                // A document removed meanwhile (or before a restart) has nothing left to edit.
                if (repository.loadDocument(documentId) == null) {
                    throw OperationSkipped(context.getString(R.string.edits_msg_document_gone))
                }
                execute(documentId, edit)
            } finally {
                number?.let { n -> withContext(NonCancellable + journalThread) { runCatching { journal.remove(n) } } }
            }
        }
    }

    private fun messages(edit: DocumentEdit): Pair<String?, String?> {
        fun s(id: Int) = context.getString(id)
        return when (edit) {
            is DocumentEdit.Rename, DocumentEdit.ToggleFavorite -> null to null
            is DocumentEdit.SetTags -> null to s(R.string.msg_tags_updated)
            is DocumentEdit.SetFolder -> null to s(R.string.msg_folder_updated)
            is DocumentEdit.SetPdfQuality -> context.getString(R.string.msg_rebuilding_pdf_quality, s(edit.quality.labelRes)) to s(R.string.msg_pdf_quality_updated)
            is DocumentEdit.SetPdfPageSize -> context.getString(R.string.msg_rebuilding_pdf_page_size, s(edit.size.labelRes)) to s(R.string.msg_pdf_page_size_updated)
            is DocumentEdit.SetSearchablePdf -> s(R.string.msg_rebuilding_ocr_layer) to
                s(if (edit.enabled) R.string.msg_searchable_pdf_enabled else R.string.msg_searchable_layer_disabled)
            is DocumentEdit.SetPageNumbers -> s(R.string.msg_updating_page_numbers) to null
            is DocumentEdit.SetScanMode -> null to context.getString(R.string.msg_mode_set_to, s(edit.mode.labelRes))
            is DocumentEdit.ApplyFilter -> context.getString(R.string.msg_applying_filter, s(edit.filter.labelRes)) to null
            is DocumentEdit.RotatePage -> s(R.string.msg_rotating_page) to null
            is DocumentEdit.ApplyCrop -> s(R.string.msg_perspective_correcting) to s(R.string.msg_crop_applied)
            is DocumentEdit.ApplyAdjustments ->
                if (edit.allPages) s(R.string.msg_cleaning_up_every_page) to s(R.string.msg_cleanup_applied_all)
                else s(R.string.msg_applying_adjustments) to s(R.string.msg_adjustments_applied)
            is DocumentEdit.ResetPage -> s(R.string.msg_restoring_original_page) to s(R.string.msg_page_reset)
            is DocumentEdit.MovePage -> s(R.string.msg_reordering_pages) to null
            is DocumentEdit.DuplicatePage -> s(R.string.msg_duplicating_page) to s(R.string.msg_page_duplicated)
            is DocumentEdit.ExtractPage -> s(R.string.msg_extracting_page) to s(R.string.msg_page_extracted)
            is DocumentEdit.SplitBookSpread -> s(R.string.msg_splitting_book_spread) to s(R.string.msg_book_spread_split)
            is DocumentEdit.DeletePage -> s(R.string.msg_removing_page) to null
            DocumentEdit.DuplicateDocument -> s(R.string.msg_duplicating_document) to null
            is DocumentEdit.AddTextAnnotation -> s(R.string.msg_adding_text_annotation) to s(R.string.msg_text_added)
            is DocumentEdit.AddHighlight -> s(R.string.msg_adding_highlight) to s(R.string.msg_highlight_added)
            is DocumentEdit.AddRedaction -> s(R.string.msg_applying_redaction) to s(R.string.msg_redaction_added)
            is DocumentEdit.RemoveLastAnnotation -> s(R.string.msg_removing_last_annotation) to s(R.string.msg_annotation_removed)
            is DocumentEdit.ReplaceAnnotations -> s(R.string.msg_updating_markup) to s(R.string.msg_markup_updated)
            is DocumentEdit.AddWatermark -> s(R.string.msg_adding_watermark) to s(R.string.msg_watermark_added)
            is DocumentEdit.MergeWith -> s(R.string.msg_merging_documents) to s(R.string.msg_merged_copy_created)
            is DocumentEdit.UpdatePageOcrText -> s(R.string.msg_updating_ocr_text) to s(R.string.msg_ocr_text_updated)
            is DocumentEdit.ReOcr ->
                (edit.script?.let { context.getString(R.string.msg_running_script_ocr, s(it.labelRes)) } ?: s(R.string.msg_refreshing_ocr)) to null
            DocumentEdit.SplitDocument -> s(R.string.msg_splitting_document) to null
            is DocumentEdit.ReplaceText -> s(R.string.msg_replacing_text) to
                s(if (edit.edit.text.isBlank()) R.string.msg_text_removed else R.string.msg_text_replaced)
            is DocumentEdit.Erase -> s(R.string.msg_erasing) to s(R.string.msg_erased)
        }
    }

    private suspend fun execute(id: String, edit: DocumentEdit) {
        when (edit) {
            is DocumentEdit.Rename ->
                if (repository.rename(id, edit.title)) operations.showMessage(context.getString(R.string.msg_renamed_duplicate_name))
            is DocumentEdit.SetTags -> repository.setTags(id, edit.tags)
            is DocumentEdit.SetFolder -> repository.setFolder(id, edit.folder)
            DocumentEdit.ToggleFavorite -> repository.toggleFavorite(id)
            is DocumentEdit.SetPdfQuality -> repository.setPdfQuality(id, edit.quality)
            is DocumentEdit.SetPdfPageSize -> repository.setPdfPageSize(id, edit.size)
            is DocumentEdit.SetSearchablePdf -> repository.setSearchablePdf(id, edit.enabled)
            is DocumentEdit.SetPageNumbers -> repository.setPageNumbers(id, edit.enabled)
            is DocumentEdit.SetScanMode -> repository.setScanMode(id, edit.mode)
            is DocumentEdit.ApplyFilter -> repository.applyFilter(id, edit.pageId, edit.filter)
            is DocumentEdit.RotatePage -> repository.rotatePage(id, edit.pageId)
            is DocumentEdit.ApplyCrop -> repository.applyCrop(id, edit.pageId, edit.crop)
            is DocumentEdit.ApplyAdjustments ->
                if (edit.allPages) repository.applyAdjustmentsToAll(id, edit.pageId, edit.adjustments)
                else repository.applyAdjustments(id, edit.pageId, edit.adjustments)
            is DocumentEdit.ResetPage -> repository.resetPage(id, edit.pageId)
            is DocumentEdit.MovePage -> repository.movePage(id, edit.pageId, edit.delta)
            is DocumentEdit.DuplicatePage -> repository.duplicatePage(id, edit.pageId)
            is DocumentEdit.ExtractPage -> repository.extractPage(id, edit.pageId)
            is DocumentEdit.SplitBookSpread -> repository.splitBookSpread(id, edit.pageId, edit.fraction)
            is DocumentEdit.DeletePage -> {
                // Removes the page and offers Undo; the page's files go only once the message is gone.
                repository.deletePage(id, edit.pageId)
                operations.showMessage(
                    UiMessage(
                        context.getString(R.string.msg_page_removed),
                        actionLabel = context.getString(R.string.common_undo),
                        onAction = { undoDeletePage(id) },
                        onDismiss = { repository.purgeRemovedPage(id, edit.pageId) }
                    )
                )
            }
            DocumentEdit.DuplicateDocument -> {
                val copy = repository.duplicateDocument(id)
                operations.showMessage(
                    UiMessage(context.getString(R.string.msg_copy_saved_as, copy.title), actionLabel = context.getString(R.string.msg_open), onAction = {
                        operations.requestOpenDocument(copy.id)
                    })
                )
            }
            is DocumentEdit.AddTextAnnotation -> repository.addTextAnnotation(id, edit.pageId, edit.text)
            is DocumentEdit.AddHighlight -> repository.addHighlight(id, edit.pageId)
            is DocumentEdit.AddRedaction -> repository.addRedaction(id, edit.pageId)
            is DocumentEdit.RemoveLastAnnotation -> repository.removeLastAnnotation(id, edit.pageId)
            is DocumentEdit.ReplaceAnnotations -> repository.replaceAnnotations(id, edit.pageId, edit.annotations)
            is DocumentEdit.AddWatermark -> repository.addWatermark(id, edit.style)
            is DocumentEdit.MergeWith -> repository.mergeDocuments(id, edit.otherId)
            is DocumentEdit.UpdatePageOcrText -> repository.updatePageOcrText(id, edit.pageId, edit.text)
            is DocumentEdit.ReOcr -> {
                val failed = repository.reOcrDocument(id, edit.script)
                val total = repository.loadDocument(id)?.pages?.size ?: 0
                val success = edit.script?.let { context.getString(R.string.msg_script_ocr_applied, context.getString(it.labelRes)) }
                    ?: context.getString(R.string.msg_ocr_refreshed)
                operations.showMessage(
                    if (failed == 0) success
                    else context.resources.getQuantityString(R.plurals.msg_ocr_partial, total, total - failed, total)
                )
            }
            DocumentEdit.SplitDocument -> {
                val count = repository.splitIntoPageDocuments(id)
                operations.showMessage(context.resources.getQuantityString(R.plurals.msg_page_documents_created, count, count))
            }
            is DocumentEdit.ReplaceText ->
                repository.replaceText(id, edit.pageId, edit.edit.left, edit.edit.top, edit.edit.right, edit.edit.bottom, edit.edit.text)
            is DocumentEdit.Erase -> repository.erase(id, edit.pageId, edit.strokesByBrush)
        }
    }

    // Undo works only within this run: the removed page is held in memory, so it isn't journaled.
    private fun undoDeletePage(id: String) = operations.enqueue(context.getString(R.string.msg_restoring_page), null) {
        if (!repository.restoreRemovedPage(id)) operations.showMessage(context.getString(R.string.msg_page_restore_failed))
    }

    /** The journal number, filled in on the journal thread before anything reads it there. */
    private class CompletableSeq {
        @Volatile var value: Long? = null
    }

    private companion object {
        const val JOURNAL_DIR = "edit_journal"
    }
}
