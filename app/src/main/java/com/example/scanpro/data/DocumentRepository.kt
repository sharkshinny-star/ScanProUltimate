package com.example.scanpro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SmartTitle
import com.example.scanpro.imaging.SpecialExporters
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
    private fun pdfLock(documentId: String): Any = pdfLocks.computeIfAbsent(documentId) { Any() }

    suspend fun loadAll(): List<ScanDocument> = withContext(Dispatchers.IO) { db.loadAll() }
    suspend fun loadTrash(): List<ScanDocument> = withContext(Dispatchers.IO) {
        purgeExpiredTrash(maxAgeDays = 7)
        db.loadTrash()
    }
    suspend fun search(query: String): List<ScanDocument> = withContext(Dispatchers.IO) { db.search(query) }
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
    ): ScanDocument = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Choose at least one image" }
        createDocument(null, imageUris, quality, autoTitle, mode, crops)
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
            val updated = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
            renderAndOcr(updated)
        }

    suspend fun applyCrop(documentId: String, pageId: String, crop: CropQuad) =
        editSinglePage(documentId, pageId) { page -> renderAndOcr(page.copy(crop = crop)) }

    suspend fun applyAdjustments(documentId: String, pageId: String, adjustments: PageAdjustments) =
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(adjustments = adjustments.normalized()))
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
                )
            )
        }
        // Signature images go only after the page is saved without them.
        deleteSignatureAssets(removed)
    }

    private suspend fun annotationsOf(documentId: String, pageId: String): List<PageAnnotation> =
        withContext(Dispatchers.IO) { requireDocument(documentId).pages.firstOrNull { it.id == pageId }?.annotations.orEmpty() }

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
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(annotations = page.annotations.dropLast(1)))
        }
        deleteSignatureAssets(removed)
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

    suspend fun addWatermark(documentId: String, watermark: String) = withContext(Dispatchers.IO) {
        val clean = watermark.trim()
        require(clean.isNotBlank()) { "Watermark cannot be blank" }
        val doc = requireDocument(documentId)
        val pages = doc.pages.map { page ->
            val annotation = PageAnnotation(
                type = AnnotationType.TEXT,
                x = 0.16f,
                y = 0.45f,
                width = 0.68f,
                height = 0.12f,
                rotationDegrees = -28f,
                text = clean.take(80),
                opacity = 0.35f
            )
            // A redaction re-reads the flattened page: the text it covers must not stay in search, copy or text export.
            renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = annotation.type == AnnotationType.REDACT)
        }
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
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
        saveWithStalePdf(doc.copy(pages = normalize(pages), updatedAt = now()))
    }

    suspend fun duplicatePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Page not found" }
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val copy = copyFlattenedPage(pages[index], doc.id, folder, index + 1)
        pages.add(index + 1, copy)
        saveWithStalePdf(doc.copy(pages = normalize(pages), updatedAt = now()))
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

    suspend fun splitBookSpread(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Page not found" }
        val page = pages[index]
        val bitmap = ImageProcessor.decodeScaled(page.imagePath, 3800) ?: error("Could not decode page")
        require(bitmap.width >= 500) { "Page is too narrow to split" }
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }

        val (left, right) = try {
            makeHalf(bitmap, left = true, doc.id, index, sourceDir, renderedDir, doc.ocrScript) to
                makeHalf(bitmap, left = false, doc.id, index, sourceDir, renderedDir, doc.ocrScript)
        } finally {
            bitmap.recycle()
        }
        pages.removeAt(index)
        pages.add(index, right)
        pages.add(index, left)
        val saved = saveWithStalePdf(doc.copy(pages = normalize(pages), scanMode = ScanMode.BOOK, updatedAt = now()))
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
        val newPages = imageUris.mapIndexed { offset, uri ->
            val position = start + offset
            val pageId = UUID.randomUUID().toString()
            val source = File(sourceDir, "source_${position.toString().padStart(3, '0')}_$pageId")
            val rendered = File(renderedDir, "page_${position.toString().padStart(3, '0')}_$pageId.jpg")
            copyUri(uri, source)
            val page = ScanPage(
                id = pageId,
                documentId = doc.id,
                position = position,
                sourcePath = source.absolutePath,
                imagePath = rendered.absolutePath
            )
            renderAndOcr(page)
        }
        saveWithStalePdf(doc.copy(pages = doc.pages + newPages, updatedAt = now()))
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
        // Delete the files only after the page is gone from the database, so a failed save loses nothing.
        File(target.sourcePath).delete()
        File(target.imagePath).delete()
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
        val pages = doc.pages.map { page -> if (page.id == pageId) page.copy(ocrText = text.trim()) else page }
        require(pages.any { it.id == pageId }) { "Page not found" }
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
    }

    suspend fun reOcrDocument(id: String, script: OcrScript? = null) = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val selected = script ?: doc.ocrScript
        val pages = doc.pages.map { page ->
            page.copy(ocrText = runCatching { ocr.recognize(File(page.imagePath), selected) }.getOrDefault(page.ocrText))
        }
        saveWithStalePdf(doc.copy(pages = pages, ocrScript = selected, updatedAt = now()))
    }

    private suspend fun addAnnotation(documentId: String, pageId: String, annotation: PageAnnotation) =
        editSinglePage(documentId, pageId) { page ->
            require(annotation.type != AnnotationType.TEXT || annotation.text.isNotBlank()) { "Text cannot be blank" }
            renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = false)
        }

    private suspend fun createDocument(
        fallbackPdfUri: Uri?,
        imageUris: List<Uri>,
        quality: PdfQuality,
        autoTitle: Boolean,
        mode: ScanMode,
        crops: List<CropQuad> = emptyList()
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
                renderAndOcr(
                    ScanPage(
                        id = pageId,
                        documentId = id,
                        position = index,
                        sourcePath = source.absolutePath,
                        imagePath = rendered.absolutePath,
                        // The original stays untouched; the crop is an instruction that can be widened later.
                        crop = crops.getOrNull(index) ?: CropQuad.DEFAULT
                    )
                )
            }

            if (pages.isNotEmpty()) {
                PdfExporter.export(pages, pdf, quality, PdfPageSize.A4, searchable = true, pageNumbers = false)
            } else if (fallbackPdfUri != null) {
                copyUri(fallbackPdfUri, pdf)
            } else {
                error("No scan pages were returned")
            }

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

    private suspend fun editSinglePage(
        documentId: String,
        pageId: String,
        block: suspend (ScanPage) -> ScanPage
    ) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        val updatedPage = block(target)
        val pages = doc.pages.map { if (it.id == pageId) updatedPage else it }
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
    }

    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true): ScanPage {
        ImageProcessor.renderPage(
            source = File(page.sourcePath),
            destination = File(page.imagePath),
            filter = page.filter,
            manualRotationDegrees = page.rotationDegrees,
            crop = page.crop,
            adjustments = page.adjustments,
            annotations = page.annotations
        )
        val hasRedaction = page.annotations.any { it.type == AnnotationType.REDACT }
        return if (runOcr) {
            val script = db.loadOcrScript(page.documentId) ?: OcrScript.LATIN
            // Recognition runs on the flattened image, where covered text is gone. If it fails, a redacted page
            // must end up with no text rather than keep the old text that includes what was covered.
            page.copy(
                ocrText = runCatching { ocr.recognize(File(page.imagePath), script) }
                    .getOrDefault(if (hasRedaction) "" else page.ocrText)
            )
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
        docId: String,
        index: Int,
        sourceDir: File,
        renderedDir: File,
        script: OcrScript
    ): ScanPage {
        val halfWidth = bitmap.width / 2
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
        return ScanPage(
            id = id,
            documentId = docId,
            position = index,
            sourcePath = source.absolutePath,
            imagePath = rendered.absolutePath,
            ocrText = runCatching { ocr.recognize(rendered, script) }.getOrDefault("")
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
            ocrText = source.ocrText
        )
    }

    private fun normalize(pages: List<ScanPage>) = pages.mapIndexed { index, page -> page.copy(position = index) }

    /** Deletes the image files behind signature annotations that are being removed. */
    private fun deleteSignatureAssets(annotations: List<PageAnnotation>) {
        annotations.filter { it.type == AnnotationType.SIGNATURE && it.assetPath.isNotBlank() }
            .forEach { File(it.assetPath).delete() }
    }

    private fun requireDocument(id: String) = db.loadDocument(id) ?: error("Document not found")

    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) {
        File(doc.pdfPath).parentFile?.deleteRecursively()
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
        exportDir.listFiles()?.filter { it.lastModified() < now() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        return File(exportDir, "${base}_${now()}.$extension")
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 300L * 1024 * 1024
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
    private fun now() = System.currentTimeMillis()
}
