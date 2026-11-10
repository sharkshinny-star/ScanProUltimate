package com.example.scanpro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.imaging.AnnotationRemap
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.ThumbnailStore
import com.example.scanpro.imaging.PaperColors
import com.example.scanpro.imaging.XlsxExporter
import com.example.scanpro.intelligence.OcrLayout
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.OcrResult
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SmartTitle
import com.example.scanpro.imaging.SpecialExporters
import com.example.scanpro.scanner.detectCrop
import com.example.scanpro.scanner.OpenCvScanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.core.graphics.createBitmap

/** The PDF is password-protected; [wrongPassword] is true when a password was given and did not work. */
class PdfPasswordRequiredException(val wrongPassword: Boolean) :
    Exception(if (wrongPassword) "That password didn't work" else "This PDF is password-protected")

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DocumentDatabase,
    private val ocr: OcrEngine,
) {
    private val rootDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    private val encryptedPackages = EncryptedDocumentPackage(context)
    /**
     * Per-document locks that serialise page saves with PDF rebuilds, so a rebuild never publishes a PDF
     * older than the pages, and a long rebuild of one document doesn't block edits to another.
     */
    private val pdfLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val history = EditHistory()
    /** Undo and redo steps available per document, for this session. */
    val undoState: kotlinx.coroutines.flow.StateFlow<Map<String, UndoState>> = history.state
    private fun pdfLock(documentId: String): Any = pdfLocks.computeIfAbsent(documentId) { Any() }

    suspend fun loadTrashSummaries(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        purgeExpiredTrash(maxAgeDays = 7)
        db.loadTrashSummaries()
    }
    /** The Files list as pages; see [DocumentDatabase.pagedSummaries]. */
    fun pagedSummaries(folder: String, favoritesOnly: Boolean, sort: LibrarySort) = db.pagedSummaries(folder, favoritesOnly, sort)
    suspend fun loadOverview(recentLimit: Int): LibraryOverview = withContext(Dispatchers.IO) { db.loadOverview(recentLimit) }
    suspend fun loadOtherSummaries(excludeId: String, limit: Int): List<DocumentSummary> =
        withContext(Dispatchers.IO) { db.loadOtherSummaries(excludeId, limit) }
    /** Null when the document is missing or in the recycle bin. */
    suspend fun activeStamp(id: String): DocumentStamp? = withContext(Dispatchers.IO) { db.loadActiveStamp(id) }
    /** The full document with its pages, or null when it no longer exists. */
    suspend fun loadDocument(id: String): ScanDocument? = withContext(Dispatchers.IO) { db.loadDocument(id) }
    suspend fun search(query: String): List<DocumentSummary> = withContext(Dispatchers.IO) { db.search(query) }
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun importScan(
        pdfUri: Uri?,
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode = ScanMode.DOCUMENT,
    ): ScanDocument = withContext(Dispatchers.IO) {
        createDocument(pdfUri, imageUris, quality, autoTitle, mode)
    }

    suspend fun importImages(
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode = ScanMode.DOCUMENT,
        crops: List<CropQuad> = emptyList(),
        filters: List<PageFilter> = emptyList(),
    ): ScanDocument = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Choose at least one image" }
        // Camera scans arrive with the outline the user confirmed; gallery and shared photos are outlined here.
        createDocument(null, imageUris, quality, autoTitle, mode, crops, filters, detectCrops = crops.isEmpty())
    }

    suspend fun importPdf(
        pdfUri: Uri,
        quality: PdfQuality,
        autoTitle: Boolean,
        password: String? = null,
    ): ScanDocument = withContext(Dispatchers.IO) {
        val created = now()
        val id = UUID.randomUUID().toString()
        val folder = File(rootDir, id).apply { mkdirs() }
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val tempPdf = File(context.cacheDir, "pdf_import_${UUID.randomUUID()}.pdf")
        val unlockedPdf = File(context.cacheDir, "pdf_unlocked_${UUID.randomUUID()}.pdf")
        try {
            copyUri(pdfUri, tempPdf)
            // The system PDF renderer cannot open protected files, so decrypt a copy with the password first.
            val readable = if (password != null) {
                try {
                    PdfSecurity.unlock(tempPdf, unlockedPdf, password)
                } catch (error: Exception) {
                    throw PdfPasswordRequiredException(wrongPassword = true)
                }
                unlockedPdf
            } else when (runCatching { PdfSecurity.protection(tempPdf) }.getOrDefault(PdfProtection.NONE)) {
                PdfProtection.PASSWORD_REQUIRED -> throw PdfPasswordRequiredException(wrongPassword = false)
                PdfProtection.RESTRICTED_ONLY -> {
                    // Opens freely but with restrictions the system renderer may refuse.
                    PdfSecurity.unlock(tempPdf, unlockedPdf, "")
                    unlockedPdf
                }
                PdfProtection.NONE -> tempPdf
            }
            val pages = mutableListOf<ScanPage>()
            ParcelFileDescriptor.open(readable, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val opened = try {
                    PdfRenderer(descriptor)
                } catch (error: SecurityException) {
                    throw PdfPasswordRequiredException(wrongPassword = false)
                }
                opened.use { renderer ->
                    require(renderer.pageCount in (1..300)) { "PDF must contain between 1 and 300 pages" }
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val maxDimension = 2400f
                            val scale = minOf(maxDimension / page.width.toFloat(), maxDimension / page.height.toFloat(), 2.5f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pageId = UUID.randomUUID().toString()
                            val source = File(sourceDir, "source_${index.toString().padStart(3, '0')}_$pageId.jpg")
                            val rendered = File(renderedDir, "page_${index.toString().padStart(3, '0')}_$pageId.jpg")
                            ImageProcessor.saveBitmapJpeg(bitmap, source, 98)
                            bitmap.recycle()
                            pages += renderAndOcr(
                                ScanPage(
                                    id = pageId,
                                    documentId = id,
                                    position = index,
                                    sourcePath = source.absolutePath,
                                    imagePath = rendered.absolutePath
                                )
                            )
                        }
                    }
                }
            }
            val fallbackTitle = "Imported " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date(created))
            val allText = pages.joinToString("\n", transform = ScanPage::ocrText)
            val title = if (autoTitle) SmartTitle.fromOcr(allText, fallbackTitle) else fallbackTitle
            val pdf = File(folder, "document.pdf")
            val doc = ScanDocument(
                id = id,
                title = title,
                createdAt = created,
                updatedAt = created,
                pdfPath = pdf.absolutePath,
                pages = pages,
                sizeBytes = 0L,
                pdfQuality = quality,
                pdfPageSize = PdfPageSize.AUTO,
                searchablePdf = true
            )
            regenerateAndSave(doc)
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        } finally {
            tempPdf.delete()
            unlockedPdf.delete()
        }
    }

    suspend fun rename(id: String, title: String) = mutateDocument(id) { doc ->
        doc.copy(title = title.trim().ifBlank { doc.title }, updatedAt = now())
    }

    suspend fun setTags(id: String, tagsText: String) = mutateDocument(id) { doc ->
        val tags = tagsText.splitToSequence(',', '#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(20)
            .toList()
        doc.copy(tags = tags, updatedAt = now())
    }

    suspend fun setFolder(id: String, folder: String) = mutateDocument(id) { doc ->
        doc.copy(folderName = folder.trim().take(80), updatedAt = now())
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val source = oldName.trim()
        val target = newName.trim().take(80)
        require(source.isNotBlank()) { "Folder name is required" }
        require(target.isNotBlank()) { "New folder name is required" }
        db.loadAll(includeTrashed = true)
            .filter { doc -> doc.folderName.equals(source, ignoreCase = true) }
            .forEach { doc -> db.saveDocumentMetadata(doc.copy(folderName = target, updatedAt = now())) }
    }

    suspend fun toggleFavorite(id: String) = mutateDocument(id) { doc ->
        doc.copy(isFavorite = !doc.isFavorite, updatedAt = now())
    }

    suspend fun setPdfQuality(id: String, quality: PdfQuality) = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        regenerateAndSave(doc.copy(pdfQuality = quality, updatedAt = now()))
    }

    suspend fun setPdfPageSize(id: String, size: PdfPageSize) = withContext(Dispatchers.IO) {
        regenerateAndSave(requireDocument(id).copy(pdfPageSize = size, updatedAt = now()))
    }

    suspend fun setSearchablePdf(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        regenerateAndSave(requireDocument(id).copy(searchablePdf = enabled, updatedAt = now()))
    }

    suspend fun setPageNumbers(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        regenerateAndSave(requireDocument(id).copy(pageNumbers = enabled, updatedAt = now()))
    }

    suspend fun setScanMode(id: String, mode: ScanMode) = mutateDocument(id) { doc ->
        doc.copy(scanMode = mode, updatedAt = now())
    }

    suspend fun applyFilter(documentId: String, pageId: String, filter: PageFilter) =
        editSinglePage(documentId, pageId) { page ->
            val updated = page.copy(filter = filter)
            renderAndOcr(updated)
        }

    suspend fun rotatePage(documentId: String, pageId: String) =
        editSinglePage(documentId, pageId) { page ->
            // Markup is placed on the finished page, so it turns with it; otherwise a redaction would slide off its text.
            // The eraser brush is a fraction of the page width, which changes with the turn.
            val aspect = ImageProcessor.imageAspect(page.imagePath) ?: 1f
            val updated = page.copy(
                rotationDegrees = (page.rotationDegrees + 90) % 360,
                annotations = page.annotations.map { it.rotatedClockwise(pageAspect = aspect) }
            )
            renderAndOcr(updated)
        }

    suspend fun applyCrop(documentId: String, pageId: String, crop: CropQuad) =
        editSinglePage(documentId, pageId) { page ->
            // Mark-up is placed on the cropped page, so move it with its content; otherwise a redaction would
            // slide off the text it hides when the crop changes.
            val annotations = ImageProcessor.orientedAspect(page.sourcePath)?.let { aspect ->
                AnnotationRemap.remap(page.annotations, page.crop, crop, page.rotationDegrees, aspect)
            } ?: page.annotations
            renderAndOcr(page.copy(crop = crop, annotations = annotations))
        }

    suspend fun applyAdjustments(documentId: String, pageId: String, adjustments: PageAdjustments) =
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(adjustments = adjustments.normalized()))
        }

    /** [adjustments] on [pageId]; every other page gets only its shadow and finger clean-up switches. */
    suspend fun applyAdjustmentsToAll(documentId: String, pageId: String, adjustments: PageAdjustments) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val clean = adjustments.normalized()
        allOrNothing { touched ->
            val pages = doc.pages.map { page ->
                val updated = if (page.id == pageId) clean
                else page.adjustments.copy(removeShadows = clean.removeShadows, removeFingers = clean.removeFingers)
                if (updated == page.adjustments) page else {
                    touched += page
                    renderAndOcr(page.copy(adjustments = updated))
                }
            }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    suspend fun resetPage(documentId: String, pageId: String) {
        val removed = annotationsOf(documentId, pageId)
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(
                page.copy(
                    filter = PageFilter.ORIGINAL,
                    rotationDegrees = 0,
                    crop = CropQuad.DEFAULT,
                    adjustments = PageAdjustments.DEFAULT,
                    annotations = emptyList()
                ),
                redactionChanged = removed.any { it.hidesContent }
            )
        }
        // Signature images stay until the document is closed, so Undo can bring the signature back.
    }

    private suspend fun annotationsOf(documentId: String, pageId: String): List<PageAnnotation> =
        withContext(Dispatchers.IO) { requireDocument(documentId).pages.firstOrNull { it.id == pageId }?.annotations.orEmpty() }

    /** The page as it looks without its markup, so the markup editor can show boxes over a clean image. */
    suspend fun markupBase(documentId: String, pageId: String): File = withContext(Dispatchers.IO) {
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        val out = File(context.cacheDir, "markup_${page.id}.jpg")
        ImageProcessor.renderPage(File(page.sourcePath), out, page.filter, page.rotationDegrees, page.crop, page.adjustments, emptyList())
        out
    }

    /** Replaces a page's markup with an edited list (moved, resized or removed boxes). */
    suspend fun replaceAnnotations(documentId: String, pageId: String, annotations: List<PageAnnotation>) {
        val before = annotationsOf(documentId, pageId)
        editSinglePage(documentId, pageId) { page ->
            // Only a redaction that was added, moved, resized or removed changes which text is visible.
            val changed = redactions(before) != redactions(annotations)
            renderAndOcr(page.copy(annotations = annotations), runOcr = changed, redactionChanged = changed)
        }
    }

    suspend fun addTextAnnotation(documentId: String, pageId: String, text: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.TEXT,
                x = 0.08f,
                y = 0.08f,
                width = 0.58f,
                height = 0.12f,
                text = text.trim().take(400)
            )
        )

    suspend fun addHighlight(documentId: String, pageId: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.HIGHLIGHT,
                x = 0.12f,
                y = 0.40f,
                width = 0.70f,
                height = 0.055f,
                opacity = 0.55f
            )
        )

    suspend fun addRedaction(documentId: String, pageId: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.REDACT,
                x = 0.12f,
                y = 0.48f,
                width = 0.70f,
                height = 0.07f
            )
        )

    suspend fun removeLastAnnotation(documentId: String, pageId: String) {
        val removed = annotationsOf(documentId, pageId).takeLast(1)
        val redaction = removed.any { it.hidesContent }
        editSinglePage(documentId, pageId) { page ->
            // Removing a highlight, text or signature leaves the page's words as they were; a redaction uncovers some.
            renderAndOcr(page.copy(annotations = page.annotations.dropLast(1)), runOcr = redaction, redactionChanged = redaction)
        }
    }

    /**
     * Replaces a line of printed words: the box ([left]..[bottom], fractions of the page) is painted in the paper
     * colour sampled around it and [text] is written there in the ink colour of the original words. Blank text
     * just removes the words.
     */
    suspend fun replaceText(documentId: String, pageId: String, left: Float, top: Float, right: Float, bottom: Float, text: String) {
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        val colours = withContext(Dispatchers.IO) { sampleColours(File(page.imagePath), left, top, right, bottom) }
        val padX = (bottom - top) * 0.15f
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.TEXT,
                x = (left - padX).coerceIn(0f, 1f),
                y = top.coerceIn(0f, 1f),
                width = (right - left + 2 * padX).coerceIn(0.01f, 1f),
                height = (bottom - top).coerceIn(0.005f, 1f),
                text = text.trim().take(400),
                color = colours.ink,
                background = colours.paper
            )
        )
    }

    /** Paper and ink colours of a box on the rendered page, read from a reduced copy of it. */
    private fun sampleColours(image: File, left: Float, top: Float, right: Float, bottom: Float): PaperColors.Estimate {
        val bitmap = ImageProcessor.decodeOrientedScaled(image.absolutePath, 1600) ?: return PaperColors.estimate(IntArray(0))
        try {
            val margin = (bottom - top) * 0.2f
            val x0 = ((left - margin) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y0 = ((top - margin) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val x1 = ((right + margin) * bitmap.width).toInt().coerceIn(x0 + 1, bitmap.width)
            val y1 = ((bottom + margin) * bitmap.height).toInt().coerceIn(y0 + 1, bitmap.height)
            val pixels = IntArray((x1 - x0) * (y1 - y0))
            bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0)
            return PaperColors.estimate(pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Erases brush strokes (points as fractions of the page), grouped by brush size (a fraction of the page
     * width): the marks under them are filled in from the surrounding paper. One undo step for all of them.
     */
    suspend fun erase(documentId: String, pageId: String, strokesByBrush: Map<Float, List<List<NormalizedPoint>>>) {
        val erasers = strokesByBrush.entries.sortedBy { it.key }.mapNotNull { (size, strokes) -> eraser(strokes.filter { it.isNotEmpty() }, size) }
        require(erasers.isNotEmpty()) { "Brush over what you want to erase" }
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(annotations = page.annotations + erasers), runOcr = true, redactionChanged = true)
        }
    }

    private fun eraser(strokes: List<List<NormalizedPoint>>, brushSize: Float): PageAnnotation? {
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        val brush = brushSize.coerceIn(0.005f, 0.2f)
        val left = (points.minOf { it.x } - brush).coerceIn(0f, 1f)
        val top = (points.minOf { it.y } - brush).coerceIn(0f, 1f)
        val width = ((points.maxOf { it.x } + brush).coerceIn(0f, 1f) - left).coerceAtLeast(0.001f)
        val height = ((points.maxOf { it.y } + brush).coerceIn(0f, 1f) - top).coerceAtLeast(0.001f)
        return PageAnnotation(
            type = AnnotationType.ERASE,
            x = left,
            y = top,
            width = width,
            height = height,
            // Stored relative to the box, so moving or resizing it in the markup editor carries the strokes.
            strokes = strokes.map { stroke -> stroke.map { NormalizedPoint((it.x - left) / width, (it.y - top) / height).clamped() } },
            brushSize = brush
        )
    }

    suspend fun stampSignature(documentId: String, pageId: String, signature: Bitmap) =
        editSinglePage(documentId, pageId) { page ->
            val folder = File(page.imagePath).parentFile?.parentFile ?: error("Invalid page folder")
            val signatureDir = File(folder, "signatures").apply { mkdirs() }
            val asset = File(signatureDir, "sig_${UUID.randomUUID()}.png")
            ImageProcessor.saveSignature(signature, asset)
            val annotation = PageAnnotation(
                type = AnnotationType.SIGNATURE,
                x = 0.56f,
                y = 0.78f,
                width = 0.34f,
                height = 0.12f,
                assetPath = asset.absolutePath
            )
            try {
                renderAndOcr(page.copy(annotations = page.annotations + annotation))
            } catch (error: Throwable) {
                asset.delete()
                throw error
            }
        }

    suspend fun addWatermark(documentId: String, style: WatermarkStyle) = withContext(Dispatchers.IO) {
        val clean = style.text.trim()
        require(clean.isNotBlank()) { "Watermark cannot be blank" }
        val doc = requireDocument(documentId)
        val height = style.size.height
        val (y, rotation) = when (style.position) {
            WatermarkPosition.DIAGONAL -> (0.5f - height / 2f) to -28f
            WatermarkPosition.CENTER -> (0.5f - height / 2f) to 0f
            WatermarkPosition.BOTTOM -> (0.95f - height) to 0f
        }
        allOrNothing { touched ->
            val pages = doc.pages.map { page ->
                touched += page
                val annotation = PageAnnotation(
                    type = AnnotationType.TEXT,
                    x = 0.12f,
                    y = y,
                    width = 0.76f,
                    height = height,
                    rotationDegrees = rotation,
                    text = clean.take(80),
                    opacity = style.opacity.coerceIn(0.1f, 1f),
                    color = style.color
                )
                // The watermark is drawn over the page; the text underneath stays readable, so OCR is kept.
                renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = false)
            }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    suspend fun movePage(documentId: String, pageId: String, delta: Int) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val from = pages.indexOfFirst { it.id == pageId }
        if (from == -1) error("Page not found")
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
        require(index >= 0) { "Page not found" }
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
        val page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        createFromPages(
            sourcePages = listOf(page),
            title = "${doc.title} - Page ${page.position + 1}",
            quality = doc.pdfQuality,
            pageSize = doc.pdfPageSize,
            searchable = doc.searchablePdf,
            pageNumbers = false,
            mode = doc.scanMode
        )
    }

    suspend fun mergeDocuments(primaryId: String, secondaryId: String): ScanDocument = withContext(Dispatchers.IO) {
        require(primaryId != secondaryId) { "Choose a different document" }
        val first = requireDocument(primaryId)
        val second = requireDocument(secondaryId)
        createFromPages(
            sourcePages = first.pages.sortedBy { it.position } + second.pages.sortedBy { it.position },
            title = "${first.title} + ${second.title}".take(120),
            quality = first.pdfQuality,
            pageSize = first.pdfPageSize,
            searchable = first.searchablePdf,
            pageNumbers = first.pageNumbers,
            mode = first.scanMode
        )
    }

    /** Splits a two-page spread at [splitFraction] of its width (0.5 is the centre; move it for off-centre books). */
    suspend fun splitBookSpread(documentId: String, pageId: String, splitFraction: Float = 0.5f) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Page not found" }
        val page = pages[index]
        val bitmap = ImageProcessor.decodeScaled(page.imagePath, 3800) ?: error("Could not decode page")
        if (bitmap.width < 500) {
            bitmap.recycle()
            throw IllegalArgumentException("Page is too narrow to split")
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

    suspend fun appendImages(documentId: String, imageUris: List<Uri>) = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext
        val doc = requireDocument(documentId)
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val start = doc.pages.size
        val newPages = mutableListOf<ScanPage>()
        try {
            imageUris.forEachIndexed { offset, uri ->
                val position = start + offset
                val pageId = UUID.randomUUID().toString()
                val source = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$pageId")
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
                newPages[newPages.lastIndex] = renderAndOcr(page)
            }
            saveWithStalePdf(doc.copy(pages = doc.pages + newPages, updatedAt = now())).also { history.clear(doc.id) }
        } catch (error: Throwable) {
            // One photo that can't be read must not leave the others' files behind, recorded nowhere.
            newPages.forEach(::deletePageFiles)
            throw error
        }
    }

    suspend fun deletePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        if (doc.pages.size == 1) {
            moveToTrash(documentId)
            return@withContext
        }
        val pages = normalize(doc.pages.sortedBy { it.position }.filterNot { it.id == pageId })
        val saved = saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        // The deleted page's files are about to go, so earlier steps could not be restored.
        history.clear(doc.id)
        // Delete the files only after the page is gone from the database, so a failed save loses nothing.
        deletePageFiles(target)
        deleteSignatureAssets(target.annotations)
        saved
    }

    suspend fun moveToTrash(id: String) = mutateDocument(id) { doc ->
        doc.copy(isTrashed = true, trashedAt = now(), updatedAt = now())
    }

    suspend fun restore(id: String) = mutateDocument(id) { doc ->
        doc.copy(isTrashed = false, trashedAt = 0L, updatedAt = now())
    }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        val doc = db.loadDocument(id) ?: return@withContext
        deleteDocumentFilesAndRecord(doc)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        db.loadTrash().forEach { deleteDocumentFilesAndRecord(it) }
    }

    suspend fun purgeExpiredTrash(maxAgeDays: Int = 7): Int = withContext(Dispatchers.IO) {
        val cutoff = now() - maxAgeDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val expired = db.loadTrash().filter { it.trashedAt in 1 until cutoff }
        expired.forEach { deleteDocumentFilesAndRecord(it) }
        expired.size
    }

    suspend fun exportIdCopy(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val file = freshExportFile("${safeName(doc.title)}_ID", "pdf")
        SpecialExporters.exportIdCopy(doc.pages, file, doc.pdfQuality)
        file
    }

    suspend fun exportLongImage(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val file = freshExportFile("${safeName(doc.title)}_long", "jpg")
        SpecialExporters.exportLongImage(doc.pages, file)
        file
    }

    suspend fun exportOcrText(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        require(doc.ocrText.isNotBlank()) { "No recognized text to export" }
        val file = freshExportFile(safeName(doc.title), "txt")
        file.writeText(doc.ocrText, Charsets.UTF_8)
        file
    }

    /** The recognised text as an editable Word document, one Word page per scanned page. */
    suspend fun exportWord(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        require(doc.ocrText.isNotBlank()) { "No recognized text to export" }
        val file = freshExportFile(safeName(doc.title), "docx")
        com.example.scanpro.imaging.DocxExporter.export(doc.title, doc.pages.sortedBy { it.position }.map { it.ocrText }, file)
        file
    }

    /**
     * Tables in the scan as an Excel workbook, one sheet per page. Columns are rebuilt from where each word sits
     * on the page; pages whose text was corrected by hand use that text, one row per line.
     */
    suspend fun exportExcel(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        require(doc.ocrText.isNotBlank()) { "No recognized text to export" }
        val sheets = doc.pages.sortedBy { it.position }.mapIndexed { index, page ->
            val rows = if (!page.ocrEdited && page.ocrWords.isNotEmpty()) OcrLayout.table(page.ocrWords)
            else page.ocrText.lines().filter { it.isNotBlank() }.map { listOf(it.trim()) }
            XlsxExporter.Sheet("Page ${index + 1}", rows)
        }
        val file = freshExportFile(safeName(doc.title), "xlsx")
        XlsxExporter.export(sheets, file)
        file
    }

    suspend fun splitIntoPageDocuments(id: String): Int = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        doc.pages.sortedBy { it.position }.forEachIndexed { index, page ->
            createFromPages(
                sourcePages = listOf(page),
                title = "${doc.title} - ${index + 1}",
                quality = doc.pdfQuality,
                pageSize = doc.pdfPageSize,
                searchable = doc.searchablePdf,
                pageNumbers = false,
                mode = doc.scanMode
            )
        }
        doc.pages.size
    }

    suspend fun exportPasswordProtectedPdf(id: String, password: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val file = freshExportFile("${safeName(doc.title)}_protected", "pdf")
        PdfSecurity.protect(File(ensurePdfNow(id).pdfPath), file, password)
        file
    }

    suspend fun exportEncryptedBackup(id: String, password: String): File = withContext(Dispatchers.IO) {
        require(password.length >= 6) { "Use a backup password with at least 6 characters" }
        val doc = requireDocument(id)
        val file = freshExportFile("${safeName(doc.title)}_backup", "scanpro")
        encryptedPackages.export(doc, password.toCharArray(), file)
        file
    }

    /**
     * Writes every document (recycle bin excluded) as its own encrypted ".scanpro" package into the folder the
     * user picked, one at a time, so a library larger than the free space on the device never has to be
     * staged whole. [onProgress] gets (done, total) after each document. Returns how many were written.
     */
    suspend fun backupAll(treeUri: Uri, password: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        require(password.length >= 6) { "Use a backup password with at least 6 characters" }
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val documents = db.loadAll()
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now()))
        var written = 0
        documents.forEachIndexed { index, doc ->
            val temp = File(context.cacheDir, "backup_${UUID.randomUUID()}.scanpro")
            try {
                encryptedPackages.export(doc, password.toCharArray(), temp)
                val name = "${safeName(doc.title)}_$stamp.scanpro"
                val target = DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", name)
                    ?: error("Could not create a file in the chosen folder")
                try {
                    context.contentResolver.openOutputStream(target, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                        ?: error("Could not write to the chosen folder")
                } catch (error: Throwable) {
                    // Don't leave an empty or partial package in the user's folder.
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
                    throw error
                }
                written++
            } finally {
                temp.delete()
            }
            onProgress(index + 1, documents.size)
        }
        written
    }

    suspend fun importEncryptedBackup(source: Uri, password: String): ScanDocument = withContext(Dispatchers.IO) {
        require(password.length >= 6) { "Backup password must be at least 6 characters" }
        val restored = encryptedPackages.import(source, password.toCharArray())
        val created = now()
        val id = UUID.randomUUID().toString()
        val folder = File(rootDir, id).apply { mkdirs() }
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        try {
            val pages = restored.pages.mapIndexed { index, restoredPage ->
                val pageId = UUID.randomUUID().toString()
                val sourceFile = File(sourceDir, "source_${index.toString().padStart(3, '0')}_$pageId.jpg")
                val renderedFile = File(renderedDir, "page_${index.toString().padStart(3, '0')}_$pageId.jpg")
                // Keep the original image and edit settings when the backup has them, so the restored page
                // stays editable. Older backups only hold the rendered page, which becomes the source.
                val original = restoredPage.sourceFile
                (original ?: restoredPage.file).copyTo(sourceFile, overwrite = true)
                restoredPage.file.copyTo(renderedFile, overwrite = true)
                val editable = original != null
                val annotations = if (editable) {
                    restoredPage.annotations.map { annotation ->
                        if (annotation.type != AnnotationType.SIGNATURE) annotation else {
                            val signatureDir = File(folder, "signatures").apply { mkdirs() }
                            val asset = File(signatureDir, "sig_${UUID.randomUUID()}.png")
                            File(annotation.assetPath).copyTo(asset, overwrite = true)
                            annotation.copy(assetPath = asset.absolutePath)
                        }
                    }
                } else emptyList()
                ScanPage(
                    id = pageId,
                    documentId = id,
                    position = index,
                    sourcePath = sourceFile.absolutePath,
                    imagePath = renderedFile.absolutePath,
                    ocrText = restoredPage.ocrText,
                    filter = if (editable) restoredPage.filter else PageFilter.ORIGINAL,
                    rotationDegrees = if (editable) restoredPage.rotationDegrees else 0,
                    crop = if (editable) restoredPage.crop else CropQuad.DEFAULT,
                    adjustments = if (editable) restoredPage.adjustments else PageAdjustments.DEFAULT,
                    annotations = annotations
                )
            }
            val pdf = File(folder, "document.pdf")
            val doc = ScanDocument(
                id = id,
                title = restored.title.ifBlank { "Restored scan" },
                createdAt = created,
                updatedAt = created,
                pdfPath = pdf.absolutePath,
                pages = pages,
                sizeBytes = 0L,
                tags = restored.tags,
                folderName = restored.folderName,
                pdfQuality = restored.quality,
                pdfPageSize = restored.pageSize,
                searchablePdf = restored.searchablePdf,
                pageNumbers = restored.pageNumbers,
                scanMode = restored.scanMode,
                ocrScript = restored.ocrScript
            )
            regenerateAndSave(doc)
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        } finally {
            restored.tempDir.deleteRecursively()
        }
    }

    suspend fun updatePageOcrText(documentId: String, pageId: String, text: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        // Marked as hand-corrected so filters, crops and rotations afterwards do not read the page again and replace it.
        val pages = doc.pages.map { page -> if (page.id == pageId) page.copy(ocrText = text.trim(), ocrEdited = true) else page }
        require(pages.any { it.id == pageId }) { "Page not found" }
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now())).also { history.record(doc.id, doc.pages) }
    }

    suspend fun reOcrDocument(id: String, script: OcrScript? = null) = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val selected = script ?: doc.ocrScript
        // Fails with a clear message when the script's model can't be fetched, instead of silently blanking every page.
        ocr.prepare(selected)
        val pages = doc.pages.map { page ->
            // Re-running recognition is an explicit request to read the pages again, so it replaces corrections.
            try {
                ocr.recognizeDetailed(File(page.imagePath), selected)
                    .let { page.copy(ocrText = it.text, ocrWords = it.words, ocrEdited = false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                page
            }
        }
        saveWithStalePdf(doc.copy(pages = pages, ocrScript = selected, updatedAt = now())).also { history.record(doc.id, doc.pages) }
    }

    private suspend fun addAnnotation(documentId: String, pageId: String, annotation: PageAnnotation) =
        editSinglePage(documentId, pageId) { page ->
            // Replaced words may be blank (the words are just removed); added text may not.
            require(annotation.type != AnnotationType.TEXT || annotation.text.isNotBlank() || annotation.background != null) { "Text cannot be blank" }
            // A redaction re-reads the flattened page: the text it covers must not stay in search, copy or text export.
            val redacting = annotation.hidesContent
            renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = redacting, redactionChanged = redacting)
        }

    private suspend fun createDocument(
        fallbackPdfUri: Uri?,
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode,
        crops: List<CropQuad> = emptyList(),
        /** The look chosen for each page on the review screen; pages without one stay as photographed. */
        filters: List<PageFilter> = emptyList(),
        /** Find the page in each photo, as the camera does, when no outline was given. */
        detectCrops: Boolean = false
    ): ScanDocument {
        val created = now()
        val id = UUID.randomUUID().toString()
        val folder = File(rootDir, id).apply { mkdirs() }
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val pdf = File(folder, "document.pdf")

        try {
            val pages = imageUris.mapIndexed { index, uri ->
                val pageId = UUID.randomUUID().toString()
                val source = File(sourceDir, "source_${index.toString().padStart(3, '0')}_$pageId")
                val rendered = File(renderedDir, "page_${index.toString().padStart(3, '0')}_$pageId.jpg")
                copyUri(uri, source)
                val crop = crops.getOrNull(index)
                    ?: if (detectCrops) runCatching { OpenCvScanProcessor.detectCrop(source) }.getOrDefault(CropQuad.DEFAULT)
                    else CropQuad.DEFAULT
                renderAndOcr(
                    ScanPage(
                        id = pageId,
                        documentId = id,
                        position = index,
                        sourcePath = source.absolutePath,
                        imagePath = rendered.absolutePath,
                        // The original stays untouched; the crop is an instruction that can be widened later.
                        crop = crop,
                        filter = filters.getOrNull(index) ?: PageFilter.ORIGINAL
                    )
                )
            }

            if (pages.isEmpty()) {
                // A document without pages can't be opened or edited. When the fallback scanner returned only a
                // PDF, import that instead: it renders a page per PDF page like any other PDF import.
                folder.deleteRecursively()
                if (fallbackPdfUri != null) return importPdf(fallbackPdfUri, quality, autoTitle)
                error("No scan pages were returned")
            }
            PdfExporter.export(pages, pdf, quality, PdfPageSize.A4, searchable = true, pageNumbers = false)

            val fallbackTitle = "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date(created))
            val allText = pages.joinToString("\n", transform = ScanPage::ocrText)
            val title = if (autoTitle) SmartTitle.fromOcr(allText, fallbackTitle) else fallbackTitle
            val doc = ScanDocument(
                id = id,
                title = title,
                createdAt = created,
                updatedAt = created,
                pdfPath = pdf.absolutePath,
                pages = pages,
                sizeBytes = pdf.length(),
                pdfQuality = quality,
                searchablePdf = pages.isNotEmpty(),
                scanMode = mode
            )
            db.saveDocument(doc)
            return doc
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        }
    }

    private fun createFromPages(
        sourcePages: List<ScanPage>,
        title: String,
        quality: PdfQuality,
        pageSize: PdfPageSize,
        searchable: Boolean,
        pageNumbers: Boolean,
        mode: ScanMode
    ): ScanDocument {
        require(sourcePages.isNotEmpty()) { "No pages" }
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
                scanMode = mode
            )
            return regenerateAndSave(doc)
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        }
    }

    /** Goes back one edit; false when there is nothing to undo. */
    suspend fun undo(documentId: String): Boolean = step(documentId, back = true)

    /** Re-applies an undone edit; false when there is nothing to redo. */
    suspend fun redo(documentId: String): Boolean = step(documentId, back = false)

    private suspend fun step(documentId: String, back: Boolean): Boolean = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = (if (back) history.undo(documentId, doc.pages) else history.redo(documentId, doc.pages))
            ?: return@withContext false
        val current = doc.pages.associateBy { it.id }
        if (target.size != doc.pages.size || target.any { it.id !in current }) {
            // Pages were added or removed outside the history; its steps no longer fit this document.
            history.clear(documentId)
            error("This change can no longer be undone")
        }
        try {
            // Only pages whose look differs are drawn again; text, corrections and order come back as they were.
            allOrNothing { touched ->
                val restored = target.map { page ->
                    val now = current.getValue(page.id)
                    if (!sameRendering(now, page)) {
                        touched += now
                        ImageProcessor.renderPage(
                            File(page.sourcePath), File(page.imagePath), page.filter, page.rotationDegrees,
                            page.crop, page.adjustments, page.annotations
                        )
                    }
                    page
                }
                saveWithStalePdf(doc.copy(pages = restored, updatedAt = now()))
            }
            true
        } catch (error: Throwable) {
            history.rollback(documentId, back, target)
            throw error
        }
    }

    /**
     * Runs a change that redraws pages and then saves them. Each page is added to `touched` before it is drawn;
     * if any step fails, the save included, those pages are drawn again as they are saved, so no image is left
     * showing an edit the database never recorded.
     */
    private suspend fun <T> allOrNothing(block: suspend (touched: MutableList<ScanPage>) -> T): T {
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

    private fun sameRendering(a: ScanPage, b: ScanPage) =
        a.sourcePath == b.sourcePath && a.filter == b.filter && a.rotationDegrees == b.rotationDegrees &&
            a.crop == b.crop && a.adjustments == b.adjustments && a.annotations == b.annotations

    /**
     * Deletes signature images that neither the document nor any undo step uses. Removing a signature keeps its
     * image so Undo can bring it back; this runs when the document is closed.
     */
    suspend fun cleanUnusedSignatures(documentId: String) = withContext(Dispatchers.IO) {
        val doc = db.loadDocument(documentId) ?: return@withContext
        val folder = File(doc.pdfPath).parentFile ?: return@withContext
        val inUse = (doc.pages + history.retained(documentId))
            .flatMap { it.annotations }
            .mapNotNull { it.assetPath.takeIf(String::isNotBlank)?.let { path -> File(path).canonicalPath } }
            .toSet()
        File(folder, "signatures").listFiles()?.filter { it.isFile && it.canonicalPath !in inUse }?.forEach { it.delete() }
    }

    private suspend fun editSinglePage(
        documentId: String,
        pageId: String,
        block: suspend (ScanPage) -> ScanPage
    ) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        allOrNothing { touched ->
            touched += target
            val updatedPage = block(target)
            val pages = doc.pages.map { if (it.id == pageId) updatedPage else it }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    /**
     * Renders the page and, when [runOcr], reads its text again. [redactionChanged] means a redaction was added,
     * moved or removed: the page is then always read again, even over hand-corrected text, because the old text
     * may include words that are now covered.
     */
    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false): ScanPage {
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
            val script = db.loadOcrScript(page.documentId) ?: OcrScript.LATIN
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

    private suspend fun mutateDocument(id: String, block: suspend (ScanDocument) -> ScanDocument) =
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
    private fun saveWithStalePdf(document: ScanDocument): ScanDocument {
        val sorted = document.pages.sortedBy { it.position }
        require(sorted.isNotEmpty()) { "Document has no pages" }
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

    /** Returns the document with an up-to-date PDF, rebuilding it first if page edits made it stale. */
    suspend fun ensurePdf(id: String): ScanDocument = withContext(Dispatchers.IO) { ensurePdfNow(id) }

    /** Ids of active documents whose PDF still needs rebuilding. */
    suspend fun documentsNeedingPdf(): List<String> = withContext(Dispatchers.IO) {
        db.loadAll().filterNot { it.hasCurrentPdf() }.map { it.id }
    }

    private fun ensurePdfNow(id: String): ScanDocument = synchronized(pdfLock(id)) {
        // Reload inside the lock so the rebuild always uses the latest saved pages.
        val doc = requireDocument(id)
        if (doc.hasCurrentPdf()) return@synchronized doc
        val size = writePdf(doc)
        // Only the size column changes. Writing the whole row would undo a rename, favorite or trash
        // made while this rebuild ran, and a deferred rebuild keeps the document's place in Recent.
        db.updateSize(doc.id, size)
        doc.copy(sizeBytes = size)
    }

    /** Builds the PDF beside the real file and renames it into place; returns its size. */
    private fun writePdf(document: ScanDocument): Long {
        val sorted = document.pages.sortedBy { it.position }
        require(sorted.isNotEmpty()) { "Document has no pages" }
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
            check(partial.renameTo(pdf)) { "Could not save the PDF" }
            document.stalePdfMarker.delete()
        } finally {
            partial.delete()
        }
        return pdf.length()
    }

    private fun regenerateAndSave(document: ScanDocument, touch: Boolean = true): ScanDocument = synchronized(pdfLock(document.id)) {
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

    private fun copyFlattenedPage(source: ScanPage, documentId: String, folder: File, position: Int): ScanPage {
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

    private fun redactions(list: List<PageAnnotation>) =
        list.filter { it.hidesContent }.map { listOf(it.id, it.x, it.y, it.width, it.height, it.rotationDegrees) }

    private fun normalize(pages: List<ScanPage>) = pages.mapIndexed { index, page -> page.copy(position = index) }

    /** Deletes a page's own image files (not its signature assets, which may be shared with history). */
    private fun deletePageFiles(page: ScanPage) {
        File(page.sourcePath).delete()
        File(page.imagePath).delete()
        ThumbnailStore.forget(context, page.imagePath)
    }

    /** Deletes the image files behind signature annotations that are being removed. */
    private fun deleteSignatureAssets(annotations: List<PageAnnotation>) {
        annotations.filter { it.type == AnnotationType.SIGNATURE && it.assetPath.isNotBlank() }
            .forEach { File(it.assetPath).delete() }
    }

    private fun requireDocument(id: String) = db.loadDocument(id) ?: error("Document not found")

    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) {
        File(doc.pdfPath).parentFile?.deleteRecursively()
        doc.pages.forEach { ThumbnailStore.forget(context, it.imagePath) }
        db.deleteDocument(doc.id)
    }

    /** Copies an imported file into app storage, refusing anything over [MAX_IMPORT_BYTES] so a huge share can't fill the device. */
    private fun copyUri(source: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open the selected file" }
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
                        throw IllegalArgumentException("File is too large to import (limit ${MAX_IMPORT_BYTES / (1024 * 1024)} MB)")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun freshExportFile(base: String, extension: String): File {
        // "Clear cache" or the system can remove the folder while the app runs; the text export writes straight into it.
        exportDir.mkdirs()
        exportDir.listFiles()?.filter { it.lastModified() < now() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        return File(exportDir, "${base}_${now()}.$extension")
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 300L * 1024 * 1024
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
    private fun now() = System.currentTimeMillis()
}
