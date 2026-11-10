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
 * Brings back documents whose files are still on the device but whose records are gone, for example after the
 * database was moved aside because an older build opened a newer one (see [setAsideNewerDatabase]) or was cleared.
 *
 * Each document lives in its own folder under `documents/<id>`. A folder with no record is rebuilt from what is in
 * it: one page per image in `rendered/`, matched to its photo in `source/` by the page id in the file name.
 * Edit settings and recognised text are not stored in the files, so recovered pages start unedited and without
 * text; the PDF is rebuilt the next time it is needed, and "Run OCR again" makes the pages searchable.
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
            Found(file, match.groupValues[1].toIntOrNull(), match.groupValues[2])
        }.sortedWith(compareBy<Found>({ it.index ?: Int.MAX_VALUE }, { it.file.lastModified() }, { it.file.name }))
        if (found.isEmpty()) return null

        val sources = File(folder, "source").listFiles { file -> file.isFile }.orEmpty()
        val pages = found.mapIndexed { position, page ->
            // Without its photo the rendered page stands in as the source, so the page can still be opened and edited.
            val source = sources.firstOrNull { it.name.contains(page.pageId) } ?: page.file
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
        return ScanDocument(
            id = folder.name,
            title = store.context.getString(R.string.doc_title_scan, stamp),
            createdAt = created,
            updatedAt = created,
            pdfPath = pdf.absolutePath,
            pages = pages,
            sizeBytes = if (pdf.isFile) pdf.length() else 0L
        )
    }

    private companion object {
        /** "page_003_<id>.jpg" (an imported or scanned page) or "book_<id>.jpg" (half of a split spread). */
        val RENDERED_NAME = Regex("""(?:page_(\d+)_|book_)(.+)\.jpg""")
    }
}
