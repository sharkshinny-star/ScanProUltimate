package com.example.scanpro.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.scanpro.R
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.DocxExporter
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SpecialExporters
import com.example.scanpro.imaging.XlsxExporter
import com.example.scanpro.intelligence.OcrLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything that turns a stored document into a file to share or keep: PNG, PDF selections, ID copy, long
 * image, text, Word, Excel, password-protected PDF and encrypted backups (R9: split out of [DocumentRepository]).
 * It only reads documents; [ensurePdf] returns the document with an up-to-date PDF on disk.
 */
internal class DocumentExporter(
    private val context: Context,
    private val db: DocumentDatabase,
    private val ensurePdf: (String) -> ScanDocument,
) {
    private val exportDir: File by lazy { File(context.cacheDir, "exports").apply { mkdirs() } }
    private val encryptedPackages = EncryptedDocumentPackage(context)

    /** Every page as a lossless PNG, for apps and forms that reject JPG. */
    suspend fun exportPng(id: String): List<File> = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val pages = doc.pages.sortedBy { it.position }
        val base = safeName(doc.title)
        pages.mapIndexed { index, page ->
            val bitmap = ImageProcessor.decodeScaled(page.imagePath, 4000) ?: userError(R.string.err_page_could_not_be_read, index + 1)
            try {
                val file = freshExportFile(if (pages.size == 1) base else "${base}_p${(index + 1).toString().padStart(2, '0')}", "png")
                file.outputStream().buffered().use { out ->
                    userRequire(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out), R.string.err_png_not_written)
                }
                file
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Share PDF card (R28): estimated size per quality for [pageIds] (all pages when empty), from a trial render. */
    suspend fun estimatePdfSizes(id: String, pageIds: Set<String>): Map<PdfQuality, Long> = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val pages = doc.pages.sortedBy { it.position }.filter { pageIds.isEmpty() || it.id in pageIds }
        PdfQuality.entries.associateWith { quality ->
            val scratch = freshExportFile("estimate", "pdf")
            com.example.scanpro.imaging.PdfShare.estimate(pages, quality, doc.pdfPageSize, doc.searchablePdf, doc.pageNumbers, scratch)
        }
    }

    /** Share PDF card (R28): [pageIds] (all when empty) at [quality], shrunk to fit under [maxBytes] when set. */
    suspend fun exportPdfSelection(id: String, pageIds: Set<String>, quality: PdfQuality, maxBytes: Long?): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val pages = doc.pages.sortedBy { it.position }.filter { pageIds.isEmpty() || it.id in pageIds }
        val file = freshExportFile(safeName(doc.title), "pdf")
        com.example.scanpro.imaging.PdfShare.build(pages, file, quality, doc.pdfPageSize, doc.searchablePdf, doc.pageNumbers, maxBytes).file
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
        userRequire(doc.ocrText.isNotBlank(), R.string.err_no_text_to_export)
        val file = freshExportFile(safeName(doc.title), "txt")
        file.writeText(doc.ocrText, Charsets.UTF_8)
        file
    }

    /** The recognised text as an editable Word document, one Word page per scanned page. */
    suspend fun exportWord(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        userRequire(doc.ocrText.isNotBlank(), R.string.err_no_text_to_export)
        val file = freshExportFile(safeName(doc.title), "docx")
        DocxExporter.export(doc.title, doc.pages.sortedBy { it.position }.map { it.ocrText }, file)
        file
    }

    /**
     * Tables in the scan as an Excel workbook, one sheet per page. Columns are rebuilt from where each word sits
     * on the page; pages whose text was corrected by hand use that text, one row per line.
     */
    suspend fun exportExcel(id: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        userRequire(doc.ocrText.isNotBlank(), R.string.err_no_text_to_export)
        val sheets = doc.pages.sortedBy { it.position }.mapIndexed { index, page ->
            val rows = if (!page.ocrEdited && page.ocrWords.isNotEmpty()) OcrLayout.table(page.ocrWords)
            else page.ocrText.lines().filter { it.isNotBlank() }.map { listOf(it.trim()) }
            XlsxExporter.Sheet("Page ${index + 1}", rows)
        }
        val file = freshExportFile(safeName(doc.title), "xlsx")
        XlsxExporter.export(sheets, file)
        file
    }

    suspend fun exportPasswordProtectedPdf(id: String, password: String): File = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val file = freshExportFile("${safeName(doc.title)}_protected", "pdf")
        PdfSecurity.protect(File(ensurePdf(id).pdfPath), file, password)
        file
    }

    suspend fun exportEncryptedBackup(id: String, password: String): File = withContext(Dispatchers.IO) {
        userRequire(
            password.length >= EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH,
            R.string.err_backup_password_too_short, EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH
        )
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
        userRequire(
            password.length >= EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH,
            R.string.err_backup_password_too_short, EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH
        )
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
                    ?: userError(R.string.err_could_not_create_file_in_folder)
                try {
                    context.contentResolver.openOutputStream(target, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                        ?: userError(R.string.err_could_not_write_to_folder)
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

    private fun freshExportFile(base: String, extension: String): File {
        // "Clear cache" or the system can remove the folder while the app runs; the text export writes straight into it.
        exportDir.mkdirs()
        exportDir.listFiles()?.filter { it.lastModified() < now() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val plain = File(exportDir, "$base.$extension")
        if (!plain.exists()) return plain
        var n = 2
        while (File(exportDir, "$base ($n).$extension").exists()) n++
        return File(exportDir, "$base ($n).$extension")
    }

    private fun requireDocument(id: String) = db.loadDocument(id) ?: userError(R.string.err_document_not_found)
    private fun safeName(value: String): String = fileSafeName(value)
}
