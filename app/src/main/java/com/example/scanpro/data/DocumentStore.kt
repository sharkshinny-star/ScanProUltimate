package com.example.scanpro.data

import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.ThumbnailStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal const val MAX_TITLE_LENGTH = 120
internal const val MAX_PAGES = 300
internal const val MAX_IMPORT_BYTES = 300L * 1024 * 1024

/**
 * What every document operation shares: the database, the per-document PDF locks, the undo history, saving
 * pages without rebuilding the PDF, rebuilding it, and drawing and reading a page again (R9). The other
 * classes in this package (DocumentCatalog, DocumentTrash, DocumentImporter, PageEditor, PageOrganiser) are built on it.
 */
internal class DocumentStore(
    val context: Context,
    val db: DocumentDatabase,
    val ocr: OcrEngine,
    val settings: SettingsStore,
) {
    val rootDir = File(context.filesDir, "documents").apply { mkdirs() }
    private fun now() = System.currentTimeMillis()


    /**
     * Per-document locks that serialise page saves with PDF rebuilds, so a rebuild never publishes a PDF
     * older than the pages, and a long rebuild of one document doesn't block edits to another.
     */
    internal val pdfLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    internal val history = EditHistory()

    internal fun pdfLock(documentId: String): Any = pdfLocks.computeIfAbsent(documentId) { Any() }

    internal fun createFromPages(
        sourcePages: List<ScanPage>,
        title: String,
        quality: PdfQuality,
        pageSize: PdfPageSize,
        searchable: Boolean,
        pageNumbers: Boolean,
        mode: ScanMode,
        /** The document the pages come from: its folder, tags, favourite mark and OCR script carry over. */
        like: ScanDocument? = null
    ): ScanDocument {
        userRequire(sourcePages.isNotEmpty(), R.string.err_document_has_no_pages)
        val created = now()
        val id = UUID.randomUUID().toString()
        val folder = File(rootDir, id).apply { mkdirs() }
        try {
            val pages = sourcePages.mapIndexed { index, source -> copyFlattenedPage(source, id, folder, index) }
            val pdf = File(folder, "document.pdf")
            val doc = ScanDocument(
                id = id,
                title = title,
                createdAt = created,
                updatedAt = created,
                pdfPath = pdf.absolutePath,
                pages = pages,
                sizeBytes = 0,
                pdfQuality = quality,
                pdfPageSize = pageSize,
                searchablePdf = searchable,
                pageNumbers = pageNumbers,
                scanMode = mode,
                folderName = like?.folderName.orEmpty(),
                tags = like?.tags.orEmpty(),
                isFavorite = like?.isFavorite ?: false,
                ocrScript = like?.ocrScript ?: OcrScript.LATIN
            )
            return regenerateAndSave(doc)
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        }
    }

    /**
     * Runs a change that redraws pages and then saves them. Each page is added to `touched` before it is drawn;
     * if any step fails, the save included, those pages are drawn again as they are saved, so no image is left
     * showing an edit the database never recorded.
     */
    internal suspend fun <T> allOrNothing(block: suspend (touched: MutableList<ScanPage>) -> T): T {
        val touched = mutableListOf<ScanPage>()
        try {
            return block(touched)
        } catch (error: Throwable) {
            touched.forEach { page ->
                runCatching {
                    ImageProcessor.renderPage(
                        File(page.sourcePath), File(page.imagePath), page.filter, page.rotationDegrees,
                        page.crop, page.adjustments, page.annotations
                    )
                }
            }
            throw error
        }
    }

    /**
     * Renders the page and, when [runOcr], reads its text again. [redactionChanged] means a redaction was added,
     * moved or removed: the page is then always read again, even over hand-corrected text, because the old text
     * may include words that are now covered.
     */
    internal suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false, script: OcrScript? = null): ScanPage {
        ImageProcessor.renderPage(
            source = File(page.sourcePath),
            destination = File(page.imagePath),
            filter = page.filter,
            manualRotationDegrees = page.rotationDegrees,
            crop = page.crop,
            adjustments = page.adjustments,
            annotations = page.annotations
        )
        val hasRedaction = page.annotations.any { it.hidesContent }
        // Corrected text is the user's work: keep it through edits, unless a redaction changed.
        if (page.ocrEdited && !redactionChanged) return page
        return if (runOcr || redactionChanged) {
            // A document being created has no row yet; its builder passes the script it will be saved with.
            val script = script ?: db.loadOcrScript(page.documentId) ?: OcrScript.LATIN
            // Recognition runs on the flattened image, where covered text is gone. If it fails, a redacted page
            // must end up with no text rather than keep the old text that includes what was covered.
            val read = runCatching { ocr.recognizeDetailed(File(page.imagePath), script) }.getOrNull()
            when {
                read != null -> page.copy(ocrEdited = false, ocrText = read.text, ocrWords = read.words)
                hasRedaction -> page.copy(ocrEdited = false, ocrText = "", ocrWords = emptyList())
                else -> page.copy(ocrEdited = false)
            }
        } else page
    }

    internal suspend fun mutateDocument(id: String, block: suspend (ScanDocument) -> ScanDocument) =
        withContext(Dispatchers.IO) {
            // Metadata-only change: update the document row without rewriting every page and search row.
            db.saveDocumentMetadata(block(requireDocument(id)))
        }

    /**
     * Saves page edits without rebuilding the PDF. Rebuilding re-decodes every page, so doing it on each
     * filter, rotate or markup made edits slow on long documents. The PDF is marked stale (the old file is
     * kept as a fallback) and rebuilt once by [ensurePdf] when it's next needed: share, open, print, save,
     * or leaving the document.
     */
    internal fun saveWithStalePdf(document: ScanDocument): ScanDocument {
        val sorted = document.pages.sortedBy { it.position }
        userRequire(sorted.isNotEmpty(), R.string.err_document_has_no_pages)
        val final = document.copy(pages = sorted, updatedAt = now())
        // Under the document's lock so a concurrent rebuild can't finish after this and look current.
        synchronized(pdfLock(final.id)) {
            // Marker first: if the process dies between the two steps, the PDF is rebuilt needlessly
            // rather than an outdated PDF being treated as current.
            final.stalePdfMarker.createNewFile()
            db.saveDocument(final)
        }
        return final
    }

    internal fun ensurePdfNow(id: String): ScanDocument = synchronized(pdfLock(id)) {
        // Reload inside the lock so the rebuild always uses the latest saved pages.
        val doc = requireDocument(id)
        if (doc.hasCurrentPdf()) return@synchronized doc
        val size = writePdf(doc)
        // Only the size column changes. Writing the whole row would undo a rename, favourite or trash
        // made while this rebuild ran, and a deferred rebuild keeps the document's place in Recent.
        db.updateSize(doc.id, size)
        doc.copy(sizeBytes = size)
    }

    /** Builds the PDF beside the real file and renames it into place; returns its size. */
    internal fun writePdf(document: ScanDocument): Long {
        val sorted = document.pages.sortedBy { it.position }
        userRequire(sorted.isNotEmpty(), R.string.err_document_has_no_pages)
        val pdf = File(document.pdfPath)
        // Write beside the real file and rename, so an interrupted build never leaves a partial
        // PDF that would later be mistaken for an up-to-date one.
        val partial = File(pdf.parentFile, "${pdf.name}.partial")
        try {
            PdfExporter.export(
                pages = sorted,
                destination = partial,
                quality = document.pdfQuality,
                pageSize = document.pdfPageSize,
                searchable = document.searchablePdf,
                pageNumbers = document.pageNumbers
            )
            // rename() replaces the old file atomically, so there is never a moment with no PDF.
            userRequire(partial.renameTo(pdf), R.string.err_could_not_save_pdf)
            document.stalePdfMarker.delete()
        } finally {
            partial.delete()
        }
        return pdf.length()
    }

    /**
     * Rebuilds [id]'s PDF with the compact exporter and keeps the new file only when it is smaller, so a PDF written
     * the old, lossless way (several times larger than needed) shrinks and nothing ever grows. Returns the bytes saved.
     * A document with edits waiting is rebuilt by [ensurePdfNow] instead, which also clears its stale marker.
     */
    internal fun compactPdf(id: String): Long = synchronized(pdfLock(id)) {
        val doc = requireDocument(id)
        val pdf = File(doc.pdfPath)
        if (!pdf.isFile) return@synchronized 0L
        val before = pdf.length()
        if (doc.stalePdfMarker.exists()) {
            ensurePdfNow(id)
            return@synchronized (before - File(doc.pdfPath).length()).coerceAtLeast(0L)
        }
        val partial = File(pdf.parentFile, "${pdf.name}.partial")
        try {
            PdfExporter.export(
                pages = doc.pages.sortedBy { it.position },
                destination = partial,
                quality = doc.pdfQuality,
                pageSize = doc.pdfPageSize,
                searchable = doc.searchablePdf,
                pageNumbers = doc.pageNumbers
            )
            val after = partial.length()
            // rename() replaces the old file atomically; only the size column changes, like a deferred rebuild.
            if (after in 1 until before && partial.renameTo(pdf)) {
                db.updateSize(id, after)
                before - after
            } else {
                0L
            }
        } finally {
            partial.delete()
        }
    }

    internal fun regenerateAndSave(document: ScanDocument, touch: Boolean = true): ScanDocument = synchronized(pdfLock(document.id)) {
        val sorted = document.pages.sortedBy { it.position }
        val size = writePdf(document)
        val final = document.copy(
            pages = sorted,
            sizeBytes = size,
            updatedAt = if (touch) now() else document.updatedAt
        )
        db.saveDocument(final)
        final
    }

    internal fun copyFlattenedPage(source: ScanPage, documentId: String, folder: File, position: Int): ScanPage {
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val newSource = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$id.jpg")
        val newRendered = File(renderedDir, "page_${position.toString().padStart(3, '0')}_$id.jpg")
        File(source.imagePath).copyTo(newSource, overwrite = true)
        File(source.imagePath).copyTo(newRendered, overwrite = true)
        return ScanPage(
            id = id,
            documentId = documentId,
            position = position,
            sourcePath = newSource.absolutePath,
            imagePath = newRendered.absolutePath,
            // The copy is the same image, so its text, word positions and "corrected by hand" mark still apply.
            ocrText = source.ocrText,
            ocrEdited = source.ocrEdited,
            ocrWords = source.ocrWords
        )
    }

    internal fun normalize(pages: List<ScanPage>) = pages.mapIndexed { index, page -> page.copy(position = index) }

    /** Deletes a page's own image files (not its signature assets, which may be shared with history). */
    internal fun deletePageFiles(page: ScanPage) {
        File(page.sourcePath).delete()
        File(page.imagePath).delete()
        ThumbnailStore.forget(context, page.imagePath)
    }

    /** Deletes the image files behind signature annotations that are being removed. */
    internal fun deleteSignatureAssets(annotations: List<PageAnnotation>) {
        annotations.filter { it.type == AnnotationType.SIGNATURE && it.assetPath.isNotBlank() }
            .forEach { File(it.assetPath).delete() }
    }

    internal fun requireDocument(id: String) = db.loadDocument(id) ?: userError(R.string.err_document_not_found)

    internal fun deleteDocumentFilesAndRecord(doc: ScanDocument) {
        File(doc.pdfPath).parentFile?.deleteRecursively()
        doc.pages.forEach { ThumbnailStore.forget(context, it.imagePath) }
        db.deleteDocument(doc.id)
    }

    /** Copies an imported file into app storage, refusing anything over [MAX_IMPORT_BYTES] so a huge share can't fill the device. */
    internal fun copyUri(source: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source).use { input ->
            if (input == null) userError(R.string.err_could_not_open_selected_file)
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) {
                        output.close()
                        destination.delete()
                        userError(R.string.err_file_too_large_to_import, (MAX_IMPORT_BYTES / (1024 * 1024)).toInt())
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

}
