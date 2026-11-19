package com.example.scanpro.imaging

import kotlin.math.abs

import com.example.scanpro.core.concurrent.BackgroundWork
import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

/** What a PDF says about itself: the document information dictionary, plus facts read from the file. */
data class PdfInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    /** Milliseconds since the epoch, or null when the PDF does not say. */
    val created: Long? = null,
    val modified: Long? = null,
    val pageCount: Int = 0,
    /** "1.7", as the file header (or the catalogue's /Version) states it. */
    val version: String = "",
    val sizeBytes: Long = 0,
    /** The PDF also carries an XMP metadata stream, a second copy of these details that readers may prefer. */
    val hasXmp: Boolean = false,
    /** The first page as it is shown (crop box, turned by its rotation), in points (1/72 inch); 0 when unknown. */
    val pageWidthPt: Float = 0f,
    val pageHeightPt: Float = 0f,
    /** Some pages differ in size from the first one. */
    val mixedPageSizes: Boolean = false,
    /** Fillable form fields. */
    val formFields: Int = 0,
    /** The file carries encryption: a password to open it, or print and copy restrictions. */
    val encrypted: Boolean = false
)

/** The details the user can change. Blank means "not set". */
data class PdfMetadataEdit(val title: String, val author: String, val subject: String, val keywords: String)

/**
 * PDF info & metadata: reads the details, writes edited ones, or removes them all for privacy. Always to a new copy.
 * Edited details are written to the information dictionary and the XMP stream is dropped, because readers that
 * prefer XMP would otherwise go on showing the old title and author.
 */
object PdfMetadataEngine {

    internal fun readInfo(doc: PDDocument, sizeBytes: Long): PdfInfo {
        val info = doc.documentInformation
        val first = if (doc.numberOfPages > 0) runCatching { shownSize(doc.getPage(0)) }.getOrNull() else null
        return PdfInfo(
            title = info.title.orEmpty().trim(),
            author = info.author.orEmpty().trim(),
            subject = info.subject.orEmpty().trim(),
            keywords = info.keywords.orEmpty().trim(),
            creator = info.creator.orEmpty().trim(),
            producer = info.producer.orEmpty().trim(),
            created = runCatching { info.creationDate?.timeInMillis }.getOrNull(),
            modified = runCatching { info.modificationDate?.timeInMillis }.getOrNull(),
            pageCount = doc.numberOfPages,
            version = String.format(java.util.Locale.US, "%.1f", doc.version),
            sizeBytes = sizeBytes,
            hasXmp = doc.documentCatalog.metadata != null,
            pageWidthPt = first?.first ?: 0f,
            pageHeightPt = first?.second ?: 0f,
            mixedPageSizes = first != null && doc.pages.any { page ->
                val size = shownSize(page)
                abs(size.first - first.first) > 1f || abs(size.second - first.second) > 1f
            },
            formFields = runCatching {
                doc.documentCatalog.acroForm?.fieldTree?.count { it is com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField } ?: 0
            }.getOrDefault(0),
            encrypted = doc.isEncrypted
        )
    }

    /** A page's size as a viewer shows it: the crop box, with width and height swapped for a quarter turn. */
    internal fun shownSize(page: com.tom_roush.pdfbox.pdmodel.PDPage): Pair<Float, Float> {
        val box = page.cropBox
        val turned = ((page.rotation % 360) + 360) % 180 == 90
        return if (turned) box.height to box.width else box.width to box.height
    }

    /** Applies [edit], or with [removeAll] clears every detail (information dictionary and XMP, page XMP too). */
    internal fun apply(doc: PDDocument, edit: PdfMetadataEdit?, removeAll: Boolean, now: Calendar = Calendar.getInstance()) {
        if (removeAll) {
            doc.documentInformation = PDDocumentInformation()
            doc.documentCatalog.metadata = null
            for (page in doc.pages) page.metadata = null
            return
        }
        if (edit == null) return
        val info = doc.documentInformation
        info.title = edit.title.trim().ifEmpty { null }
        info.author = edit.author.trim().ifEmpty { null }
        info.subject = edit.subject.trim().ifEmpty { null }
        info.keywords = edit.keywords.trim().ifEmpty { null }
        info.modificationDate = now
        doc.documentInformation = info
        doc.documentCatalog.metadata = null
    }

    suspend fun read(context: Context, inputUri: Uri): PdfInfo = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "info_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(temp).use { readInfo(it, temp.length()) }
        } finally {
            temp.delete()
        }
    }

    suspend fun write(
        context: Context,
        inputUri: Uri,
        edit: PdfMetadataEdit?,
        removeAll: Boolean,
        outputFile: File
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "info_out_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(temp).use { doc ->
                apply(doc, edit, removeAll)
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = doc.numberOfPages,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.get(if (removeAll) R.string.tools_info_removed else R.string.tools_info_saved)
                )
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }
}
