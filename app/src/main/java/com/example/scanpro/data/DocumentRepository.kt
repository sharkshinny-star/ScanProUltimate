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

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DocumentDatabase,
    private val ocr: OcrEngine,
) {
    private val rootDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    private val encryptedPackages = EncryptedDocumentPackage(context)

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
    ): ScanDocument = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Choose at least one image" }
        createDocument(null, imageUris, quality, autoTitle, mode)
    }

    suspend fun importPdf(
        pdfUri: Uri,
        quality: PdfQuality,
        autoTitle: Boolean,
    ): ScanDocument = withContext(Dispatchers.IO) {
        val created = now()
        val id = UUID.randomUUID().toString()
        val folder = File(rootDir, id).apply { mkdirs() }
        val sourceDir = File(folder, "source").apply { mkdirs() }
        val renderedDir = File(folder, "rendered").apply { mkdirs() }
        val tempPdf = File(context.cacheDir, "pdf_import_${UUID.randomUUID()}.pdf")
        try {
            copyUri(pdfUri, tempPdf)
            val pages = mutableListOf<ScanPage>()
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
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
        db.loadAll()
            .filter { doc -> doc.folderName.equals(source, ignoreCase = true) }
            .forEach { doc -> db.saveDocument(doc.copy(folderName = target, updatedAt = now())) }
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

    suspend fun resetPage(documentId: String, pageId: String) =
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

    suspend fun removeLastAnnotation(documentId: String, pageId: String) =
        editSinglePage(documentId, pageId) { page ->
            val updated = page.copy(annotations = page.annotations.dropLast(1))
            renderAndOcr(updated)
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
            renderAndOcr(page.copy(annotations = page.annotations + annotation))
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
            renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = false)
        }
        regenerateAndSave(doc.copy(pages = pages, updatedAt = now()))
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
        regenerateAndSave(doc.copy(pages = normalize(pages), updatedAt = now()))
    }

    suspend fun duplicatePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val pages = doc.pages.asSequence().sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Page not found" }
        val folder = File(doc.pdfPath).parentFile ?: error("Invalid document folder")
        val copy = copyFlattenedPage(pages[index], doc.id, folder, index + 1)
        pages.add(index + 1, copy)
        regenerateAndSave(doc.copy(pages = normalize(pages), updatedAt = now()))
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

        val left = makeHalf(bitmap, left = true, doc.id, index, sourceDir, renderedDir)
        val right = makeHalf(bitmap, left = false, doc.id, index, sourceDir, renderedDir)
        File(page.sourcePath).delete()
        File(page.imagePath).delete()
        pages.removeAt(index)
        pages.add(index, right)
        pages.add(index, left)
        regenerateAndSave(doc.copy(pages = normalize(pages), scanMode = ScanMode.BOOK, updatedAt = now()))
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
        regenerateAndSave(doc.copy(pages = doc.pages + newPages, updatedAt = now()))
    }

    suspend fun deletePage(documentId: String, pageId: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        if (doc.pages.size == 1) {
            moveToTrash(documentId)
            return@withContext
        }
        File(target.sourcePath).delete()
        File(target.imagePath).delete()
        target.annotations.filter { it.type == AnnotationType.SIGNATURE }.forEach { File(it.assetPath).delete() }
        val pages = normalize(doc.pages.filterNot { it.id == pageId })
        regenerateAndSave(doc.copy(pages = pages, updatedAt = now()))
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
        PdfSecurity.protect(File(doc.pdfPath), file, password)
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
                restoredPage.file.copyTo(sourceFile, overwrite = true)
                restoredPage.file.copyTo(renderedFile, overwrite = true)
                ScanPage(
                    id = pageId,
                    documentId = id,
                    position = index,
                    sourcePath = sourceFile.absolutePath,
                    imagePath = renderedFile.absolutePath,
                    ocrText = restoredPage.ocrText
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
        regenerateAndSave(doc.copy(pages = pages, updatedAt = now()))
    }

    suspend fun reOcrDocument(id: String, script: OcrScript? = null) = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val selected = script ?: doc.ocrScript
        val pages = doc.pages.map { page ->
            page.copy(ocrText = runCatching { ocr.recognize(File(page.imagePath), selected) }.getOrDefault(page.ocrText))
        }
        regenerateAndSave(doc.copy(pages = pages, ocrScript = selected, updatedAt = now()))
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
        mode: ScanMode
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
                        imagePath = rendered.absolutePath
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
        regenerateAndSave(doc.copy(pages = pages, updatedAt = now()))
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
        return if (runOcr) {
            val script = db.loadDocument(page.documentId)?.ocrScript ?: OcrScript.LATIN
            page.copy(ocrText = runCatching { ocr.recognize(File(page.imagePath), script) }.getOrDefault(page.ocrText))
        } else page
    }

    private suspend fun mutateDocument(id: String, block: suspend (ScanDocument) -> ScanDocument) =
        withContext(Dispatchers.IO) {
            db.saveDocument(block(requireDocument(id)))
        }

    private fun regenerateAndSave(document: ScanDocument): ScanDocument {
        val sorted = document.pages.sortedBy { it.position }
        require(sorted.isNotEmpty()) { "Document has no pages" }
        val pdf = File(document.pdfPath)
        PdfExporter.export(
            pages = sorted,
            destination = pdf,
            quality = document.pdfQuality,
            pageSize = document.pdfPageSize,
            searchable = document.searchablePdf,
            pageNumbers = document.pageNumbers
        )
        val final = document.copy(
            pages = sorted,
            sizeBytes = pdf.length(),
            updatedAt = now()
        )
        db.saveDocument(final)
        return final
    }

    private suspend fun makeHalf(
        bitmap: Bitmap,
        left: Boolean,
        docId: String,
        index: Int,
        sourceDir: File,
        renderedDir: File
    ): ScanPage {
        val halfWidth = bitmap.width / 2
        val x = if (left) 0 else halfWidth
        val width = if (left) halfWidth else bitmap.width - halfWidth
        val half = Bitmap.createBitmap(bitmap, x, 0, width, bitmap.height)
        val id = UUID.randomUUID().toString()
        val source = File(sourceDir, "book_$id.jpg")
        val rendered = File(renderedDir, "book_$id.jpg")
        ImageProcessor.saveBitmapJpeg(half, source, 98)
        ImageProcessor.saveBitmapJpeg(half, rendered, 96)
        return ScanPage(
            id = id,
            documentId = docId,
            position = index,
            sourcePath = source.absolutePath,
            imagePath = rendered.absolutePath,
            ocrText = runCatching { ocr.recognize(rendered) }.getOrDefault("")
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

    private fun requireDocument(id: String) = db.loadDocument(id) ?: error("Document not found")

    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) {
        File(doc.pdfPath).parentFile?.deleteRecursively()
        db.deleteDocument(doc.id)
    }

    private fun copyUri(source: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open $source" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun freshExportFile(base: String, extension: String): File {
        exportDir.listFiles()?.filter { it.lastModified() < now() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        return File(exportDir, "${base}_${now()}.$extension")
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
    private fun now() = System.currentTimeMillis()
}
