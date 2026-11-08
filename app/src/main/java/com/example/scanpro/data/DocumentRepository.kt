package com.example.scanpro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.imaging.AnnotationRemap
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.OcrResult
import com.example.scanpro.imaging.PaperColors
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SmartTitle
import com.example.scanpro.imaging.ThumbnailStore
import com.example.scanpro.scanner.OpenCvScanProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.scanpro.core.text.UserFacingPluralException
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.R

/** The PDF is password-protected; [wrongPassword] is true when a password was given and did not work. */
class PdfPasswordRequiredException(val wrongPassword: Boolean) :
    Exception(if (wrongPassword) "That password didn't work" else "This PDF is password-protected")

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DocumentDatabase,
    private val ocr: OcrEngine,
    private val settings: SettingsStore,
) {
    private val rootDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val exporter = DocumentExporter(context, db) { ensurePdfNow(it) }
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

    /** How many recycle-bin documents are deleted for good within the next [withinMs], under the retention setting. */
    suspend fun trashExpiringWithin(withinMs: Long): Int = withContext(Dispatchers.IO) {
        val retentionMs = settings.trashRetentionDays.first().coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val now = now()
        db.loadTrashSummaries().count { it.trashedAt > 0 && it.trashedAt + retentionMs - now in 1..withinMs }
    }

    /** Documents in the library (not in the recycle bin). */
    suspend fun activeDocumentCount(): Int = withContext(Dispatchers.IO) { db.loadOverview(recentLimit = 0).documentCount }

    suspend fun loadTrashSummaries(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        purgeExpiredTrash()
        db.loadTrashSummaries()
    }
    /** The Files list as pages; see [DocumentDatabase.pagedSummaries]. */
    fun pagedSummaries(folder: String, favoritesOnly: Boolean, sort: LibrarySort, ascending: Boolean = sort.defaultAscending, tag: String = "") = db.pagedSummaries(folder, favoritesOnly, sort, ascending, tag)
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
        /** The review screen's brightness, contrast and saturation for each page; null or missing keeps the page as is. */
        adjustments: List<PageAdjustments?> = emptyList(),
        /** A name the user typed before saving; null or blank uses the automatic title. */
        title: String? = null,
        /** The folder chosen before saving; null or blank leaves the document outside any folder. */
        folder: String? = null,
    ): ScanDocument = withContext(Dispatchers.IO) {
        userRequire(imageUris.isNotEmpty(), R.string.err_choose_at_least_one_image)
        // Camera scans arrive with the outline the user confirmed; gallery and shared photos are outlined here.
        createDocument(null, imageUris, quality, autoTitle, mode, crops, filters, adjustments, detectCrops = crops.isEmpty(), userTitle = title, userFolder = folder)
    }

    suspend fun importPdf(
        pdfUri: Uri,
        quality: PdfQuality,
        autoTitle: Boolean,
        password: String? = null,
    ): ScanDocument = withContext(Dispatchers.IO) {
        val defaultScript = settings.defaultOcrScript.first()
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
                    userRequire(renderer.pageCount in (1..300), R.string.err_pdf_page_count_range, 1, 300)
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
                                script = defaultScript,
                                page = ScanPage(
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
            val fallbackTitle = context.getString(R.string.doc_title_imported, SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date(created)))
            val allText = pages.joinToString("\n", transform = ScanPage::ocrText)
            val smart = if (autoTitle) SmartTitle.fromOcr(allText, "").takeIf { it.isNotBlank() } else null
            val title = com.example.scanpro.imaging.ScanTitle.build(settings.namePattern.first(), smart, context.getString(ScanMode.DOCUMENT.labelRes), created, fallbackTitle)
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
                searchablePdf = true,
                ocrScript = defaultScript
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

    /** Renames the document. Returns true when another document already has this name, so the caller can say so. */
    suspend fun rename(id: String, title: String): Boolean = withContext(Dispatchers.IO) {
        val clean = title.trim().take(MAX_TITLE_LENGTH)
        userRequire(clean.isNotBlank(), R.string.err_name_required)
        val clash = db.loadAll().any { it.id != id && it.title.equals(clean, ignoreCase = true) }
        mutateDocument(id) { doc -> doc.copy(title = clean, updatedAt = now()) }
        clash
    }

    /** Every document in the folder goes back to the top level; the folder then no longer exists. */
    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        val target = name.trim()
        userRequire(target.isNotBlank(), R.string.err_folder_name_required)
        db.loadAll(includeTrashed = true)
            .filter { doc -> doc.folderName.equals(target, ignoreCase = true) }
            .forEach { doc -> db.saveDocumentMetadata(doc.copy(folderName = "", updatedAt = now())) }
    }

    /** True when a folder of this name (in any case) already exists, for the rename dialog's merge warning. */
    suspend fun folderExists(name: String): Boolean = withContext(Dispatchers.IO) {
        val target = name.trim()
        target.isNotBlank() && db.loadAll().any { it.folderName.equals(target, ignoreCase = true) }
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

    suspend fun setFolder(id: String, folder: String) = withContext(Dispatchers.IO) {
        val wanted = folder.trim().take(80)
        // "bills" joins an existing "Bills" rather than starting a second folder that differs only in case.
        val existing = if (wanted.isBlank()) "" else db.loadAll(includeTrashed = true)
            .map { it.folderName }
            .firstOrNull { it.isNotBlank() && it.equals(wanted, ignoreCase = true) } ?: wanted
        mutateDocument(id) { doc -> doc.copy(folderName = existing, updatedAt = now()) }
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val source = oldName.trim()
        val target = newName.trim().take(80)
        userRequire(source.isNotBlank(), R.string.err_folder_name_required)
        userRequire(target.isNotBlank(), R.string.err_new_folder_name_required)
        db.loadAll(includeTrashed = true)
            .filter { doc -> doc.folderName.equals(source, ignoreCase = true) }
            .forEach { doc -> db.saveDocumentMetadata(doc.copy(folderName = target, updatedAt = now())) }
    }

    suspend fun toggleFavorite(id: String) = mutateDocument(id) { doc ->
        doc.copy(isFavorite = !doc.isFavorite, updatedAt = now())
    }

    // PDF settings mark the PDF stale and rebuild it on the next export, like page edits do, so changing
    // three settings in a row costs one rebuild rather than three blocking ones.
    suspend fun setPdfQuality(id: String, quality: PdfQuality) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(pdfQuality = quality))
    }

    suspend fun setPdfPageSize(id: String, size: PdfPageSize) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(pdfPageSize = size))
    }

    suspend fun setSearchablePdf(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        saveWithStalePdf(requireDocument(id).copy(searchablePdf = enabled))
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
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
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
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
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
        userRequire(erasers.isNotEmpty(), R.string.err_erase_nothing_selected)
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

    /** Stamps [signature] on the page, fitted into [placement] (fractions of the page) or, without one, the lower right. */
    suspend fun stampSignature(documentId: String, pageId: String, signature: Bitmap, placement: com.example.scanpro.feature.document.SignaturePlacement? = null) =
        editSinglePage(documentId, pageId) { page ->
            val folder = File(page.imagePath).parentFile?.parentFile ?: error("Invalid page folder")
            val signatureDir = File(folder, "signatures").apply { mkdirs() }
            val asset = File(signatureDir, "sig_${UUID.randomUUID()}.png")
            ImageProcessor.saveSignature(signature, asset)
            val annotation = PageAnnotation(
                type = AnnotationType.SIGNATURE,
                x = placement?.x?.coerceIn(0f, 0.99f) ?: 0.56f,
                y = placement?.y?.coerceIn(0f, 0.99f) ?: 0.78f,
                width = placement?.width?.coerceIn(0.01f, 1f) ?: 0.34f,
                height = placement?.height?.coerceIn(0.01f, 1f) ?: 0.12f,
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
        userRequire(clean.isNotBlank(), R.string.err_watermark_blank)
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
        userRequire(bitmap.width >= 500, R.string.err_page_too_narrow_to_split)
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
        val source = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$newId")
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
            moveToTrash(documentId)
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

    /** Deletes recycle-bin documents older than the user's retention setting. */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val maxAgeDays = settings.trashRetentionDays.first()
        val cutoff = now() - maxAgeDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val expired = db.loadTrash().filter { it.trashedAt in 1 until cutoff }
        expired.forEach { deleteDocumentFilesAndRecord(it) }
        expired.size
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

    suspend fun importEncryptedBackup(source: Uri, password: String): ScanDocument = withContext(Dispatchers.IO) {
        userRequire(
            password.length >= EncryptedDocumentPackage.MIN_RESTORE_PASSWORD_LENGTH,
            R.string.err_backup_password_too_short, EncryptedDocumentPackage.MIN_RESTORE_PASSWORD_LENGTH
        )
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
        userRequire(pages.any { it.id == pageId }, R.string.err_page_not_found)
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now())).also { history.record(doc.id, doc.pages) }
    }

    /** Reads every page again. Returns how many pages could not be read (they keep their old text). */
    suspend fun reOcrDocument(id: String, script: OcrScript? = null): Int = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val selected = script ?: doc.ocrScript
        // Fails with a clear message when the script's model can't be fetched, instead of silently blanking every page.
        ocr.prepare(selected)
        var failed = 0
        val pages = doc.pages.map { page ->
            coroutineContext.ensureActive()
            // Re-running recognition is an explicit request to read the pages again, so it replaces corrections.
            runCatching { ocr.recognizeDetailed(File(page.imagePath), selected) }
                .map { page.copy(ocrText = it.text, ocrWords = it.words, ocrEdited = false) }
                .getOrElse { failed++; page }
        }
        saveWithStalePdf(doc.copy(pages = pages, ocrScript = selected, updatedAt = now())).also { history.record(doc.id, doc.pages) }
        failed
    }

    private suspend fun addAnnotation(documentId: String, pageId: String, annotation: PageAnnotation) =
        editSinglePage(documentId, pageId) { page ->
            // Replaced words may be blank (the words are just removed); added text may not.
            userRequire(annotation.type != AnnotationType.TEXT || annotation.text.isNotBlank() || annotation.background != null, R.string.err_text_blank)
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
        /** The review sliders for each page, when they were moved. */
        adjustments: List<PageAdjustments?> = emptyList(),
        /** Find the page in each photo, as the camera does, when no outline was given. */
        detectCrops: Boolean = false,
        /** Typed by the user; wins over the automatic title when it has any text. */
        userTitle: String? = null,
        /** Chosen by the user; the document is filed there (blank: no folder). */
        userFolder: String? = null
    ): ScanDocument {
        val defaultScript = settings.defaultOcrScript.first()
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
                    script = defaultScript,
                    page = ScanPage(
                        id = pageId,
                        documentId = id,
                        position = index,
                        sourcePath = source.absolutePath,
                        imagePath = rendered.absolutePath,
                        // The original stays untouched; the crop is an instruction that can be widened later.
                        crop = crop,
                        filter = filters.getOrNull(index) ?: PageFilter.ORIGINAL,
                        adjustments = adjustments.getOrNull(index)?.normalized() ?: PageAdjustments.DEFAULT
                    )
                )
            }

            if (pages.isEmpty()) {
                // A document without pages can't be opened or edited. When the fallback scanner returned only a
                // PDF, import that instead: it renders a page per PDF page like any other PDF import.
                folder.deleteRecursively()
                if (fallbackPdfUri != null) return importPdf(fallbackPdfUri, quality, autoTitle)
                userError(R.string.err_no_scan_pages_returned)
            }
            PdfExporter.export(pages, pdf, quality, PdfPageSize.A4, searchable = true, pageNumbers = false)

            // Named after the mode when it says something ("Receipt 2026-11-05 10-31", "ID card …"), as Adobe Scan does;
            // used only when no title comes from the page text.
            val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date(created))
            val fallbackTitle = when (mode) {
                ScanMode.DOCUMENT, ScanMode.TEXT -> context.getString(R.string.doc_title_scan, stamp)
                else -> "${context.getString(mode.labelRes)} $stamp"
            }
            val allText = pages.joinToString("\n", transform = ScanPage::ocrText)
            // The typed name wins; otherwise the smart title (when on) goes through the name pattern (R34), which
            // with no pattern set gives the smart title or the mode-and-date fallback as before.
            val smart = if (autoTitle) SmartTitle.fromOcr(allText, "").takeIf { it.isNotBlank() } else null
            val title = userTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE_LENGTH)
                ?: com.example.scanpro.imaging.ScanTitle.build(settings.namePattern.first(), smart, context.getString(mode.labelRes), created, fallbackTitle)
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
                scanMode = mode,
                ocrScript = defaultScript,
                folderName = userFolder?.trim().orEmpty().take(80)
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
            userError(R.string.err_change_cannot_be_undone)
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
        val target = doc.pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
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
    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false, script: OcrScript? = null): ScanPage {
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
        // Only the size column changes. Writing the whole row would undo a rename, favourite or trash
        // made while this rebuild ran, and a deferred rebuild keeps the document's place in Recent.
        db.updateSize(doc.id, size)
        doc.copy(sizeBytes = size)
    }

    /** Builds the PDF beside the real file and renames it into place; returns its size. */
    private fun writePdf(document: ScanDocument): Long {
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

    private fun requireDocument(id: String) = db.loadDocument(id) ?: userError(R.string.err_document_not_found)

    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) {
        File(doc.pdfPath).parentFile?.deleteRecursively()
        doc.pages.forEach { ThumbnailStore.forget(context, it.imagePath) }
        db.deleteDocument(doc.id)
    }

    /** Copies an imported file into app storage, refusing anything over [MAX_IMPORT_BYTES] so a huge share can't fill the device. */
    private fun copyUri(source: Uri, destination: File) {
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

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_PAGES = 300
        const val MAX_IMPORT_BYTES = 300L * 1024 * 1024
    }

    private fun safeName(value: String): String = fileSafeName(value)
    private fun now() = System.currentTimeMillis()
}

/**
 * A title as a file name: only the characters file systems and share targets reject are replaced, so
 * titles in any script survive the trip.
 */
fun fileSafeName(value: String): String {
    val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim().trim('.').take(80)
    return cleaned.ifBlank { "Document" }
}
