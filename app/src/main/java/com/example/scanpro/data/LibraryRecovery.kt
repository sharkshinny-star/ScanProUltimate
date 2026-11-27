package com.example.scanpro.data

import com.example.scanpro.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A hidden file in a new document's folder from the moment an import starts until its record is saved. A folder that
 * still has it and no record is an import that was cut short (the process was killed, the phone died): library
 * recovery brings it back, since its pages are the user's, but says in the title that it is incomplete.
 */
internal object ImportMarker {
    const val NAME = ".importing"
    fun start(folder: File) { runCatching { File(folder, NAME).createNewFile() } }
    fun finish(folder: File) { File(folder, NAME).delete() }
    fun isUnfinished(folder: File) = File(folder, NAME).exists()
}

/**
 * Brings back documents whose files are still on the device but whose records are gone, for example after the
 * database was moved aside because an older build opened a newer one (see [setAsideNewerDatabase]) or was cleared.
 *
 * Each document lives in its own folder under `documents/<id>`. A folder with no record is rebuilt from what is in
 * it: one page per image in `rendered/`, matched to its photo in `source/` by the page id in the file name.
 * Edit settings and recognised text are not stored in the files, so recovered pages start with no edits and without
 * text; the PDF is rebuilt the next time it is needed, and "Run OCR again" makes the pages searchable.
 *
 * The page's source is a copy of its rendered picture, not the original photo. The rendered picture is what the user
 * saw and what is in the PDF: it already has the crop, the filter and any redaction. Starting from the original photo
 * with no edits, the first rotate or filter would render the page again from the unredacted photo and quietly bring
 * back what had been blacked out. The price is that a recovered page's crop cannot be widened again.
 */
internal class LibraryRecovery(private val store: DocumentStore) {
    private class Found(val file: File, val index: Int?, val pageId: String)

    /** Recovers every folder that has no record. Returns how many documents were brought back. */
    suspend fun recoverOrphanedDocuments(): Int = withContext(Dispatchers.IO) {
        var recovered = 0
        for (folder in store.rootDir.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }) {
            coroutineContext.ensureActive()
            // Documents in the recycle bin have a record too, so they are not touched.
            if (store.db.loadDocument(folder.name) != null) continue
            val document = rebuild(folder) ?: continue
            try {
                store.saveWithStalePdf(document)
                recovered++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // One folder that can't be saved must not stop the others.
            }
        }
        recovered
    }

    private fun rebuild(folder: File): ScanDocument? {
        val found = File(folder, "rendered").listFiles { file -> file.isFile }.orEmpty().mapNotNull { file ->
            val match = RENDERED_NAME.matchEntire(file.name) ?: return@mapNotNull null
            // The position is group 1 for "page_003_<id>", group 2 for "book_003_<id>"; older "book_<id>" files have none.
            Found(file, match.groupValues[1].ifEmpty { match.groupValues[2] }.toIntOrNull(), match.groupValues[3])
        }.sortedWith(compareBy<Found>({ it.index ?: Int.MAX_VALUE }, { it.file.lastModified() }, { it.file.name }))
        if (found.isEmpty()) return null

        val sourceDir = File(folder, "source").apply { mkdirs() }
        val pages = found.mapIndexed { position, page ->
            // A copy of the rendered picture (see the class note), so edits start from what the user saw, redactions included,
            // and a render never reads and overwrites the same file. Only if the copy cannot be made does the page keep its
            // original photo (or, with none, its own picture) as the source.
            val copy = File(sourceDir, "recovered_${page.pageId}.jpg")
            val copied = runCatching { page.file.copyTo(copy, overwrite = true) }.isSuccess && copy.length() > 0
            val source = if (copied) copy else {
                copy.delete()
                File(folder, "source").listFiles { file -> file.isFile }.orEmpty().firstOrNull { it.name.contains(page.pageId) } ?: page.file
            }
            ScanPage(
                id = page.pageId,
                documentId = folder.name,
                position = position,
                sourcePath = source.absolutePath,
                imagePath = page.file.absolutePath
            )
        }
        val created = found.minOf { it.file.lastModified() }.takeIf { it > 0 } ?: System.currentTimeMillis()
        val pdf = File(folder, "document.pdf")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date(created))
        val title = store.context.getString(R.string.doc_title_scan, stamp)
        return ScanDocument(
            id = folder.name,
            // An import that was cut short may have only some of its pages: say so instead of passing it off as whole.
            title = if (ImportMarker.isUnfinished(folder)) store.context.getString(R.string.doc_title_import_incomplete, title) else title,
            createdAt = created,
            updatedAt = created,
            pdfPath = pdf.absolutePath,
            pages = pages,
            sizeBytes = if (pdf.isFile) pdf.length() else 0L
        )
    }

    private companion object {
        /**
         * "page_003_<id>.jpg" (an imported or scanned page), "book_003_<id>.jpg" (half of a split spread) or the older
         * "book_<id>.jpg" with no position. Ids are UUIDs, which hold no underscore, so a leading "digits_" is always a position.
         */
        val RENDERED_NAME = Regex("""(?:page_(\d+)_|book_(?:(\d+)_)?)(.+)\.jpg""")
    }
}
