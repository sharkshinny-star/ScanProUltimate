package com.example.scanpro.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.scanpro.imaging.OcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The PDF is password-protected; [wrongPassword] is true when a password was given and did not work. */
class PdfPasswordRequiredException(val wrongPassword: Boolean) :
    Exception(if (wrongPassword) "That password didn't work" else "This PDF is password-protected")

/**
 * The one entry point the screens and workers use for documents. The work lives in [DocumentStore] and the
 * classes built on it ([DocumentCatalog], [DocumentTrash], [DocumentImporter], [PageEditor], [PageOrganiser],
 * [DocumentExporter]); this class only delegates, so its public surface is unchanged (R9).
 */
@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext context: Context,
    db: DocumentDatabase,
    ocr: OcrEngine,
    settings: SettingsStore,
) {
    private val store = DocumentStore(context, db, ocr, settings)
    private val catalog = DocumentCatalog(store)
    private val trash = DocumentTrash(store)
    private val importer = DocumentImporter(store)
    private val recovery = LibraryRecovery(store)
    private val editor = PageEditor(store)
    private val organiser = PageOrganiser(store, trash)
    private val exporter = DocumentExporter(context, db) { store.ensurePdfNow(it) }

    /** Draws a page from its photo; tests replace it to stand in for the native image pipeline. */
    internal var pageRenderer: PageRenderer
        get() = store.pageRenderer
        set(value) { store.pageRenderer = value }

    /** Undo and redo steps available per document, for this session. */
    val undoState: kotlinx.coroutines.flow.StateFlow<Map<String, UndoState>> = store.history.state

    // ---- DocumentCatalog
    suspend fun activeDocumentCount(): Int = catalog.activeDocumentCount()
    fun pagedSummaries(
        folder: String,
        favoritesOnly: Boolean,
        sort: LibrarySort,
        ascending: Boolean = sort.defaultAscending,
        tag: String = ""
    ) =
        catalog.pagedSummaries(folder, favoritesOnly, sort, ascending, tag)
    suspend fun loadOverview(recentLimit: Int): LibraryOverview = catalog.loadOverview(recentLimit)
    suspend fun loadOtherSummaries(excludeId: String, limit: Int): List<DocumentSummary> = catalog.loadOtherSummaries(excludeId, limit)
    suspend fun activeStamp(id: String): DocumentStamp? = catalog.activeStamp(id)
    suspend fun loadDocument(id: String): ScanDocument? = catalog.loadDocument(id)
    suspend fun search(query: String): List<DocumentSummary> = catalog.search(query)
    suspend fun storageBytes(): Long = catalog.storageBytes()
    suspend fun rename(id: String, title: String): Boolean = catalog.rename(id, title)
    suspend fun deleteFolder(name: String) = catalog.deleteFolder(name)
    suspend fun folderExists(name: String): Boolean = catalog.folderExists(name)
    suspend fun setTags(id: String, tagsText: String) = catalog.setTags(id, tagsText)
    suspend fun setFolder(id: String, folder: String) = catalog.setFolder(id, folder)
    suspend fun renameFolder(oldName: String, newName: String) = catalog.renameFolder(oldName, newName)
    suspend fun toggleFavorite(id: String) = catalog.toggleFavorite(id)
    suspend fun setPdfQuality(id: String, quality: PdfQuality) = catalog.setPdfQuality(id, quality)
    suspend fun setPdfPageSize(id: String, size: PdfPageSize) = catalog.setPdfPageSize(id, size)
    suspend fun setSearchablePdf(id: String, enabled: Boolean) = catalog.setSearchablePdf(id, enabled)
    suspend fun setPageNumbers(id: String, enabled: Boolean) = catalog.setPageNumbers(id, enabled)
    suspend fun setScanMode(id: String, mode: ScanMode) = catalog.setScanMode(id, mode)
    suspend fun ensurePdf(id: String): ScanDocument = catalog.ensurePdf(id)
    suspend fun documentsNeedingPdf(): List<String> = catalog.documentsNeedingPdf()

    // ---- DocumentTrash
    suspend fun trashExpiringWithin(withinMs: Long): Int = trash.trashExpiringWithin(withinMs)
    suspend fun loadTrashSummaries(): List<DocumentSummary> = trash.loadTrashSummaries()
    suspend fun moveToTrash(id: String) = trash.moveToTrash(id)
    suspend fun restore(id: String) = trash.restore(id)
    suspend fun deleteForever(id: String) = trash.deleteForever(id)
    suspend fun emptyTrash() = trash.emptyTrash()
    suspend fun purgeExpiredTrash(): Int = trash.purgeExpiredTrash()

    // ---- DocumentImporter
    suspend fun importScan(
        pdfUri: Uri?,
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode = ScanMode.DOCUMENT
    ): ScanDocument =
        importer.importScan(pdfUri, imageUris, quality, autoTitle, mode)
    suspend fun importImages(
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode = ScanMode.DOCUMENT,
        crops: List<CropQuad> = emptyList(),
        filters: List<PageFilter> = emptyList(),
        adjustments: List<PageAdjustments?> = emptyList(),
        title: String? = null,
        folder: String? = null
    ): ScanDocument =
        importer.importImages(imageUris, quality, autoTitle, mode, crops, filters, adjustments, title, folder)
    suspend fun importPdf(
        pdfUri: Uri,
        quality: PdfQuality,
        autoTitle: Boolean,
        password: String? = null
    ): ScanDocument =
        importer.importPdf(pdfUri, quality, autoTitle, password)
    suspend fun importEncryptedBackup(source: Uri, password: String): ScanDocument = importer.importEncryptedBackup(source, password)

    /** Rebuilds documents whose files are on the device but whose records are gone; returns how many came back. */
    suspend fun recoverOrphanedDocuments(): Int = recovery.recoverOrphanedDocuments()

    // ---- PageEditor
    suspend fun applyFilter(documentId: String, pageId: String, filter: PageFilter) = editor.applyFilter(documentId, pageId, filter)
    suspend fun rotatePage(documentId: String, pageId: String) = editor.rotatePage(documentId, pageId)
    suspend fun applyCrop(documentId: String, pageId: String, crop: CropQuad) = editor.applyCrop(documentId, pageId, crop)
    suspend fun applyAdjustments(
        documentId: String,
        pageId: String,
        adjustments: PageAdjustments
    ) =
        editor.applyAdjustments(documentId, pageId, adjustments)
    suspend fun applyAdjustmentsToAll(
        documentId: String,
        pageId: String,
        adjustments: PageAdjustments
    ) =
        editor.applyAdjustmentsToAll(documentId, pageId, adjustments)
    suspend fun resetPage(documentId: String, pageId: String) = editor.resetPage(documentId, pageId)
    suspend fun markupBase(documentId: String, pageId: String): File = editor.markupBase(documentId, pageId)
    suspend fun replaceAnnotations(
        documentId: String,
        pageId: String,
        annotations: List<PageAnnotation>
    ) =
        editor.replaceAnnotations(documentId, pageId, annotations)
    suspend fun addTextAnnotation(documentId: String, pageId: String, text: String) = editor.addTextAnnotation(documentId, pageId, text)
    suspend fun addHighlight(documentId: String, pageId: String) = editor.addHighlight(documentId, pageId)
    suspend fun addRedaction(documentId: String, pageId: String) = editor.addRedaction(documentId, pageId)
    suspend fun removeLastAnnotation(documentId: String, pageId: String) = editor.removeLastAnnotation(documentId, pageId)
    suspend fun replaceText(
        documentId: String,
        pageId: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        text: String
    ) =
        editor.replaceText(documentId, pageId, left, top, right, bottom, text)
    suspend fun erase(
        documentId: String,
        pageId: String,
        strokesByBrush: Map<Float, List<List<NormalizedPoint>>>
    ) =
        editor.erase(documentId, pageId, strokesByBrush)
    suspend fun stampSignature(
        documentId: String,
        pageId: String,
        signature: Bitmap,
        placement: com.example.scanpro.feature.document.SignaturePlacement? = null
    ) =
        editor.stampSignature(documentId, pageId, signature, placement)
    suspend fun addWatermark(documentId: String, style: WatermarkStyle) = editor.addWatermark(documentId, style)
    suspend fun updatePageOcrText(documentId: String, pageId: String, text: String) = editor.updatePageOcrText(documentId, pageId, text)
    suspend fun reOcrDocument(id: String, script: OcrScript? = null): Int = editor.reOcrDocument(id, script)
    suspend fun undo(documentId: String): Boolean = editor.undo(documentId)
    suspend fun redo(documentId: String): Boolean = editor.redo(documentId)
    suspend fun cleanUnusedSignatures(documentId: String) = editor.cleanUnusedSignatures(documentId)

    // ---- PageOrganiser
    suspend fun movePage(documentId: String, pageId: String, delta: Int) = organiser.movePage(documentId, pageId, delta)
    suspend fun duplicatePage(documentId: String, pageId: String) = organiser.duplicatePage(documentId, pageId)
    suspend fun extractPage(documentId: String, pageId: String): ScanDocument = organiser.extractPage(documentId, pageId)
    suspend fun mergeDocuments(primaryId: String, secondaryId: String): ScanDocument = organiser.mergeDocuments(primaryId, secondaryId)
    suspend fun splitBookSpread(
        documentId: String,
        pageId: String,
        splitFraction: Float = 0.5f
    ) =
        organiser.splitBookSpread(documentId, pageId, splitFraction)
    suspend fun appendImages(
        documentId: String,
        imageUris: List<Uri>,
        crops: List<CropQuad> = emptyList(),
        filters: List<PageFilter> = emptyList(),
        adjustments: List<PageAdjustments?> = emptyList()
    ) =
        organiser.appendImages(documentId, imageUris, crops, filters, adjustments)
    suspend fun replacePage(
        documentId: String,
        pageId: String,
        imageUri: Uri,
        crop: CropQuad,
        filter: PageFilter,
        adjustments: PageAdjustments? = null
    ) =
        organiser.replacePage(documentId, pageId, imageUri, crop, filter, adjustments)
    suspend fun deletePage(documentId: String, pageId: String) = organiser.deletePage(documentId, pageId)
    suspend fun restoreRemovedPage(documentId: String): Boolean = organiser.restoreRemovedPage(documentId)
    fun purgeRemovedPage(documentId: String, pageId: String? = null) = organiser.purgeRemovedPage(documentId, pageId)
    suspend fun duplicateDocument(documentId: String): ScanDocument = organiser.duplicateDocument(documentId)
    suspend fun splitIntoPageDocuments(id: String): Int = organiser.splitIntoPageDocuments(id)

    // Exports live in [DocumentExporter]; these keep the repository's public surface unchanged.
    suspend fun exportPng(id: String): List<File> = exporter.exportPng(id)

    suspend fun estimatePdfSizes(id: String, pageIds: Set<String>): Map<PdfQuality, Long> = exporter.estimatePdfSizes(id, pageIds)

    suspend fun exportPdfSelection(id: String, pageIds: Set<String>, quality: PdfQuality, maxBytes: Long?): File =
        exporter.exportPdfSelection(id, pageIds, quality, maxBytes)

    suspend fun exportIdCopy(id: String): File = exporter.exportIdCopy(id)

    suspend fun exportLongImage(id: String): File = exporter.exportLongImage(id)

    suspend fun exportOcrText(id: String): File = exporter.exportOcrText(id)

    suspend fun exportWord(id: String): File = exporter.exportWord(id)

    suspend fun exportExcel(id: String): File = exporter.exportExcel(id)

    suspend fun exportPasswordProtectedPdf(id: String, password: String): File = exporter.exportPasswordProtectedPdf(id, password)

    suspend fun exportEncryptedBackup(id: String, password: String): File = exporter.exportEncryptedBackup(id, password)

    suspend fun backupAll(treeUri: Uri, password: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int =
        exporter.backupAll(treeUri, password, onProgress)
}

/**
 * A title as a file name: only the characters file systems and share targets reject are replaced, so
 * titles in any script survive the trip.
 */
fun fileSafeName(value: String): String {
    val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim().trim('.').take(80)
    return cleaned.ifBlank { "Document" }
}
