package com.example.scanpro.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingPluralException
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.OcrResult
import com.example.scanpro.scanner.OpenCvScanProcessor
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Changes to which pages a document has and their order: move, copy, extract, merge, split, add, replace, remove (R9). */
internal class PageOrganiser(private val store: DocumentStore, private val trash: DocumentTrash) {
    private val ocr get() = store.ocr
    private val history get() = store.history
    private fun requireDocument(id: String) = store.requireDocument(id)
    private fun saveWithStalePdf(document: ScanDocument) = store.saveWithStalePdf(document)
    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false, script: OcrScript? = null) =
        store.renderAndOcr(page, runOcr, redactionChanged, script)
    private fun deletePageFiles(page: ScanPage) = store.deletePageFiles(page)
    private fun deleteSignatureAssets(annotations: List<PageAnnotation>) = store.deleteSignatureAssets(annotations)
    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) = store.deleteDocumentFilesAndRecord(doc)
    private fun normalize(pages: List<ScanPage>) = store.normalize(pages)
    private fun copyFlattenedPage(source: ScanPage, documentId: String, folder: File, position: Int) =
        store.copyFlattenedPage(source, documentId, folder, position)
    private fun createFromPages(
        sourcePages: List<ScanPage>, title: String, quality: PdfQuality, pageSize: PdfPageSize, searchable: Boolean,
        pageNumbers: Boolean, mode: ScanMode, like: ScanDocument? = null
    ) = store.createFromPages(sourcePages, title, quality, pageSize, searchable, pageNumbers, mode, like)
    private fun copyUri(source: Uri, destination: File) = store.copyUri(source, destination)

    suspend fun movePage(documentId: String, pageId: String, delta: Int) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val from = pages.indexOfFirst { it.id == pageId }
        if (from == -1) userError(R.string.err_page_not_found)
        val to = (from + delta).coerceIn(0, pages.lastIndex)
        if (to == from) return@withContext
        val moved = pages.removeAt(from)
        pages.add(to, moved)
        saveWithStalePdf(doc.copy(pages = normalize(pages), updatedAt = now())).also { history.record(doc.id, doc.pages) }
    }

    suspend fun duplicatePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        userRequire(index >= 0, R.string.err_page_not_found)
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val copy = copyFlattenedPage(pages[index], doc.id, folder, index + 1)
        pages.add(index + 1, copy)
        try {
            saveWithStalePdf(doc.copy(pages = normalize(pages), updatedAt = now())).also { history.clear(doc.id) }
        } catch (error: Throwable) {
            // The copy was never recorded, so its files would stay behind unused.
            deletePageFiles(copy)
            throw error
        }
    }

    suspend fun extractPage(documentId: String, pageId: String): ScanDocument = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val page = doc.pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
        createFromPages(
            sourcePages = listOf(page),
            title = "${doc.title} - Page ${page.position + 1}",
            quality = doc.pdfQuality,
            pageSize = doc.pdfPageSize,
            searchable = doc.searchablePdf,
            pageNumbers = false,
            mode = doc.scanMode,
            like = doc
        )
    }

    suspend fun mergeDocuments(primaryId: String, secondaryId: String): ScanDocument = withContext(Dispatchers.IO) {
        userRequire(primaryId != secondaryId, R.string.err_choose_different_document)
        val first = requireDocument(primaryId)
        val second = requireDocument(secondaryId)
        createFromPages(
            sourcePages = first.pages.sortedBy { it.position } + second.pages.sortedBy { it.position },
            title = "${first.title} + ${second.title}".take(120),
            quality = first.pdfQuality,
            pageSize = first.pdfPageSize,
            searchable = first.searchablePdf,
            pageNumbers = first.pageNumbers,
            mode = first.scanMode,
            like = first
        )
    }

    /** Splits a two-page spread at [splitFraction] of its width (0.5 is the centre; move it for off-centre books). */
    suspend fun splitBookSpread(documentId: String, pageId: String, splitFraction: Float = 0.5f) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        userRequire(index >= 0, R.string.err_page_not_found)
        val page = pages[index]
        val bitmap = ImageProcessor.decodeScaled(page.imagePath, 3800) ?: userError(R.string.err_could_not_decode_page)
        if (bitmap.width < 500) {
            bitmap.recycle()
            userError(R.string.err_page_too_narrow_to_split)
        }
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }

        // Kept outside the try so halves written before a failure can be removed again.
        var left: ScanPage? = null
        var right: ScanPage? = null
        val saved = try {
            val (leftHalf, rightHalf) = try {
                makeHalf(bitmap, left = true, splitFraction, doc.id, index, sourceDir, renderedDir, doc.ocrScript).also { left = it } to
                    makeHalf(bitmap, left = false, splitFraction, doc.id, index, sourceDir, renderedDir, doc.ocrScript).also { right = it }
            } finally {
                bitmap.recycle()
            }
            pages.removeAt(index)
            pages.add(index, rightHalf)
            pages.add(index, leftHalf)
            saveWithStalePdf(doc.copy(pages = normalize(pages), scanMode = ScanMode.BOOK, updatedAt = now()))
        } catch (error: Throwable) {
            // Halves that were never recorded would stay behind unused.
            left?.let(::deletePageFiles)
            right?.let(::deletePageFiles)
            throw error
        }
        // Steps from before the split refer to a page that no longer exists.
        history.clear(doc.id)
        // Delete the original spread only once both halves are saved.
        File(page.sourcePath).delete()
        File(page.imagePath).delete()
        deleteSignatureAssets(page.annotations)
        saved
    }

    suspend fun appendImages(
        documentId: String,
        imageUris: List<Uri>,
        /** From the camera's review screen, one per photo; missing entries are detected or left as they are. */
        crops: List<CropQuad> = emptyList(),
        filters: List<PageFilter> = emptyList(),
        adjustments: List<PageAdjustments?> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext
        val doc = requireDocument(documentId)
        if (doc.pages.size + imageUris.size > MAX_PAGES) throw UserFacingPluralException(R.plurals.err_document_page_limit, MAX_PAGES, MAX_PAGES)
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val start = doc.pages.size
        val newPages = mutableListOf<ScanPage>()
        try {
            imageUris.forEachIndexed { offset, uri ->
                coroutineContext.ensureActive()
                val position = start + offset
                val pageId = UUID.randomUUID().toString()
                val source = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$pageId.jpg")
                val rendered = File(renderedDir, "page_${position.toString().padStart(3, '0')}_$pageId.jpg")
                val page = ScanPage(
                    id = pageId,
                    documentId = doc.id,
                    position = position,
                    sourcePath = source.absolutePath,
                    imagePath = rendered.absolutePath
                )
                // Listed before copying, so a photo that fails half-way is cleaned up with the rest.
                newPages += page
                copyUri(uri, source)
                // The outline confirmed on the camera's review screen, or one found here, so a photo of a page on a
                // desk comes in cropped and straight.
                val crop = crops.getOrNull(offset) ?: runCatching { OpenCvScanProcessor.detectCrop(source) }.getOrDefault(CropQuad.DEFAULT)
                newPages[newPages.lastIndex] = renderAndOcr(
                    page.copy(
                        crop = crop,
                        filter = filters.getOrNull(offset) ?: page.filter,
                        adjustments = adjustments.getOrNull(offset)?.normalized() ?: page.adjustments
                    )
                )
            }
            saveWithStalePdf(doc.copy(pages = doc.pages + newPages, updatedAt = now())).also { history.clear(doc.id) }
        } catch (error: Throwable) {
            // One photo that can't be read must not leave the others' files behind, recorded nowhere.
            newPages.forEach(::deletePageFiles)
            throw error
        }
    }

    /**
     * Swaps a page's photo for a new one taken in the camera. The page keeps its place; its mark-up, text
     * corrections and adjustments belonged to the old photo and are dropped, and OCR runs again.
     */
    suspend fun replacePage(documentId: String, pageId: String, imageUri: Uri, crop: CropQuad, filter: PageFilter, adjustments: PageAdjustments? = null) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val old = doc.pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val newId = UUID.randomUUID().toString()
        val position = old.position
        val source = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$newId.jpg")
        val rendered = File(renderedDir, "page_${position.toString().padStart(3, '0')}_$newId.jpg")
        copyUri(imageUri, source)
        val page = try {
            renderAndOcr(
                ScanPage(
                    id = newId,
                    documentId = doc.id,
                    position = position,
                    sourcePath = source.absolutePath,
                    imagePath = rendered.absolutePath,
                    crop = crop,
                    filter = filter,
                    adjustments = adjustments?.normalized() ?: PageAdjustments.DEFAULT
                )
            )
        } catch (error: Throwable) {
            source.delete()
            rendered.delete()
            throw error
        }
        val pages = doc.pages.map { if (it.id == pageId) page else it }
        saveWithStalePdf(doc.copy(pages = normalize(pages.sortedBy { it.position }), updatedAt = now()))
        history.clear(doc.id)
        purgeRemovedPage(documentId)
        deletePageFiles(old)
        deleteSignatureAssets(old.annotations)
    }

    /** A page removed from a document whose files are still on disk, so the removal can be undone. */
    private class RemovedPage(val documentId: String, val page: ScanPage, val index: Int)

    // At most one removed page per document is kept; removing another page, or the "Page removed"
    // message going away, purges the earlier one.
    private val removedPages = java.util.concurrent.ConcurrentHashMap<String, RemovedPage>()

    /**
     * Removes a page but keeps its files until [purgeRemovedPage] (or the next removal) drops them, so
     * [restoreRemovedPage] can put it back. A document's last page sends the whole document to the
     * recycle bin instead, which has its own restore.
     */
    suspend fun deletePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val sorted = doc.pages.sortedBy { it.position }
        val index = sorted.indexOfFirst { it.id == pageId }
        if (index == -1) userError(R.string.err_page_not_found)
        if (doc.pages.size == 1) {
            trash.moveToTrash(documentId)
            return@withContext
        }
        val pages = normalize(sorted.filterNot { it.id == pageId })
        val saved = saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        // The document's page set changed, so earlier steps no longer fit it.
        history.clear(doc.id)
        // The page's files stay until the undo window closes; see purgeRemovedPage.
        purgeRemovedPage(documentId)
        removedPages[documentId] = RemovedPage(documentId, sorted[index], index)
        saved
    }

    /** Puts the most recently removed page back where it was; false when there is nothing to restore. */
    suspend fun restoreRemovedPage(documentId: String): Boolean = withContext(Dispatchers.IO) {
        val removed = removedPages.remove(documentId) ?: return@withContext false
        if (!File(removed.page.imagePath).isFile) return@withContext false
        val doc = requireDocument(documentId)
        val pages = doc.pages.sortedBy { it.position }.toMutableList()
        pages.add(removed.index.coerceIn(0, pages.size), removed.page)
        saveWithStalePdf(doc.copy(pages = normalize(pages), updatedAt = now()))
        history.clear(doc.id)
        true
    }

    /**
     * Deletes the files of a removed page once it can no longer be restored. With [pageId], only that
     * page is purged, so a message going away late cannot drop a page removed after it.
     */
    fun purgeRemovedPage(documentId: String, pageId: String? = null) {
        val current = removedPages[documentId] ?: return
        if (pageId != null && current.page.id != pageId) return
        val removed = removedPages.remove(documentId) ?: return
        deletePageFiles(removed.page)
        deleteSignatureAssets(removed.page.annotations)
    }

    /** A new document with copies of every page, for editing one version while keeping the other. */
    suspend fun duplicateDocument(documentId: String): ScanDocument = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        createFromPages(
            sourcePages = doc.pages.sortedBy { it.position },
            title = "${doc.title} copy".take(120),
            quality = doc.pdfQuality,
            pageSize = doc.pdfPageSize,
            searchable = doc.searchablePdf,
            pageNumbers = doc.pageNumbers,
            mode = doc.scanMode,
            like = doc
        )
    }

    suspend fun splitIntoPageDocuments(id: String): Int = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val created = mutableListOf<ScanDocument>()
        try {
            doc.pages.sortedBy { it.position }.forEachIndexed { index, page ->
                coroutineContext.ensureActive()
                created += createFromPages(
                    sourcePages = listOf(page),
                    title = "${doc.title} - ${index + 1}",
                    quality = doc.pdfQuality,
                    pageSize = doc.pdfPageSize,
                    searchable = doc.searchablePdf,
                    pageNumbers = false,
                    mode = doc.scanMode,
                    like = doc
                )
            }
        } catch (error: Throwable) {
            // All or nothing: a failure or a cancel part-way leaves no half set of page documents behind.
            created.forEach { partial -> runCatching { deleteDocumentFilesAndRecord(partial) } }
            throw error
        }
        doc.pages.size
    }

    private suspend fun makeHalf(
        bitmap: Bitmap,
        left: Boolean,
        splitFraction: Float,
        docId: String,
        index: Int,
        sourceDir: File,
        renderedDir: File,
        script: OcrScript
    ): ScanPage {
        val halfWidth = (bitmap.width * splitFraction).toInt().coerceIn(1, bitmap.width - 1)
        val x = if (left) 0 else halfWidth
        val width = if (left) halfWidth else bitmap.width - halfWidth
        val half = Bitmap.createBitmap(bitmap, x, 0, width, bitmap.height)
        val id = UUID.randomUUID().toString()
        val source = File(sourceDir, "book_$id.jpg")
        val rendered = File(renderedDir, "book_$id.jpg")
        try {
            ImageProcessor.saveBitmapJpeg(half, source, 98)
            ImageProcessor.saveBitmapJpeg(half, rendered, 96)
        } finally {
            if (half !== bitmap) half.recycle()
        }
        val read = runCatching { ocr.recognizeDetailed(rendered, script) }.getOrNull() ?: OcrResult("", emptyList())
        return ScanPage(
            id = id,
            documentId = docId,
            position = index,
            sourcePath = source.absolutePath,
            imagePath = rendered.absolutePath,
            ocrText = read.text,
            ocrWords = read.words
        )
    }
}
