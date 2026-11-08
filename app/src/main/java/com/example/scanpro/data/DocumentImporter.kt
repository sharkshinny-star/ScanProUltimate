package com.example.scanpro.data

import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfExporter
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SmartTitle
import com.example.scanpro.scanner.OpenCvScanProcessor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Creating documents from scans, photos, PDFs and encrypted backups (R9). */
internal class DocumentImporter(private val store: DocumentStore) {
    private val context get() = store.context
    private val db get() = store.db
    private val settings get() = store.settings
    private val rootDir get() = store.rootDir
    private fun regenerateAndSave(document: ScanDocument, touch: Boolean = true) = store.regenerateAndSave(document, touch)
    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false, script: OcrScript? = null) =
        store.renderAndOcr(page, runOcr, redactionChanged, script)
    private fun copyUri(source: Uri, destination: File) = store.copyUri(source, destination)
    private fun now() = System.currentTimeMillis()

    private val encryptedPackages = EncryptedDocumentPackage(context)

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
                val source = File(sourceDir, "source_${index.toString().padStart(3, '0')}_$pageId.jpg")
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
}
