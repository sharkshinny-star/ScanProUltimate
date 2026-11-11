package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.core.text.UserFacingPluralException
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.data.MAX_PAGES
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Pages of a second PDF to put into the one being organised. [rangeText] is the pages to take, such as "1-3, 5"
 * (page numbers start at 1), or null for every page. [afterPosition] is how many of the organised PDF's pages come
 * first: 0 puts them at the start, and the number of pages kept puts them at the end.
 */
data class PageInsertion(val uri: Uri, val rangeText: String?, val afterPosition: Int)

/**
 * Organise's "insert pages from another PDF". Works on the pages as arranged in the Organise sheet ([PageSpec]s), so
 * deletes, moves and rotations made there carry over. Always writes a new file; the PDFs it reads are untouched.
 */
object InsertPagesEngine {
    /** The 0-based page numbers [rangeText] names in a PDF of [pageCount] pages, in the order given; all pages for null. */
    internal fun pagesToInsert(rangeText: String?, pageCount: Int): List<Int> {
        if (rangeText == null) return (0 until pageCount).toList()
        // A bad entry throws a UserFacingException that says what is wrong.
        val pages = PageRanges.parse(rangeText, pageCount).getOrThrow().flatMap { range -> range.map { it - 1 } }
        // A page can sit in a PDF only once as the same object, so a page named twice is refused rather than dropped.
        userRequire(pages.size == pages.toSet().size, R.string.tools_err_insert_duplicate)
        return pages
    }

    /** Throws when [inserted] pages added to [kept] would be more than the app handles elsewhere. */
    internal fun requireWithinLimit(kept: Int, inserted: Int) {
        if (kept + inserted > MAX_PAGES) throw UserFacingPluralException(R.plurals.err_document_page_limit, MAX_PAGES, MAX_PAGES)
    }

    /** The order of the result: [baseCount] pages of the organised PDF, with [insertedCount] new ones after the first [after]. */
    internal sealed interface Slot {
        data class Base(val position: Int) : Slot
        data class Inserted(val position: Int) : Slot
    }

    internal fun layout(baseCount: Int, insertedCount: Int, after: Int): List<Slot> = buildList {
        for (i in 0 until after) add(Slot.Base(i))
        for (j in 0 until insertedCount) add(Slot.Inserted(j))
        for (i in after until baseCount) add(Slot.Base(i))
    }

    /**
     * Writes [baseSpecs] of [baseUri] with the pages of [insertion] put in. A protected second PDF is opened with
     * [password]; when it has none, or it is wrong, this stops with [PdfPasswordNeededException] so the user can be asked.
     * The result has no password.
     */
    suspend fun insertPages(
        context: Context,
        baseUri: Uri,
        baseSpecs: List<PageSpec>,
        insertion: PageInsertion,
        outputFile: File,
        password: String? = null,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(baseSpecs.isNotEmpty(), R.string.tools_err_keep_one_page)
        userRequire(insertion.afterPosition in 0..baseSpecs.size, R.string.tools_err_insert_position)
        outputFile.parentFile?.mkdirs()

        val baseFile = PdfToolsEngine.copyUriToTemp(context, baseUri, "insert_base_${UUID.randomUUID()}.pdf")
        var extraFile: File? = null
        // Pages read from a source document are written from it when the result is saved, so both sources stay open
        // until after the save.
        var base: PDDocument? = null
        var extra: PDDocument? = null
        val result = PDDocument()
        try {
            val opened = PdfToolsEngine.loadPdf(baseFile)
            base = opened
            val secondFile = PdfToolsEngine.copyUriToTemp(context, insertion.uri, "insert_extra_${UUID.randomUUID()}.pdf")
            extraFile = secondFile
            val second = try {
                PdfToolsEngine.openPdf(secondFile, password.orEmpty())
            } catch (locked: InvalidPasswordException) {
                throw PdfPasswordNeededException(insertion.uri, wrongPassword = password != null, cause = locked)
            } catch (error: Exception) {
                throw UserFacingException(R.string.tools_err_insert_unreadable, cause = error)
            }
            extra = second
            userRequire(second.numberOfPages > 0, R.string.tools_err_no_pages)

            val chosen = pagesToInsert(insertion.rangeText, second.numberOfPages)
            requireWithinLimit(baseSpecs.size, chosen.size)

            val slots = layout(baseSpecs.size, chosen.size, insertion.afterPosition)
            with(PdfToolsEngine) {
                slots.forEachIndexed { step, slot ->
                    currentCoroutineContext().ensureActive()
                    onProgress(step + 1, slots.size, AppStrings.get(R.string.tools_progress_processing_page, step + 1, slots.size))
                    when (slot) {
                        is Slot.Base -> {
                            val spec = baseSpecs[slot.position]
                            if (spec.originalIndex in 0 until opened.numberOfPages) {
                                val page = opened.getPage(spec.originalIndex)
                                if (spec.rotationDegrees != 0) page.rotation = Math.floorMod(page.rotation + spec.rotationDegrees, 360)
                                result.addImportedPage(page)
                            }
                        }
                        is Slot.Inserted -> result.addImportedPage(second.getPage(chosen[slot.position]))
                    }
                }
            }
            userRequire(result.numberOfPages > 0, R.string.tools_err_keep_one_page)
            with(PdfToolsEngine) { result.dropLinksToMissingPages() }
            result.save(outputFile)

            val inserted = chosen.size
            val total = result.numberOfPages
            var message = AppStrings.plural(R.plurals.tools_result_inserted, inserted, inserted, total)
            // A protected second PDF gives up its protection here: say so, the way Merge does.
            if (password != null) message += " " + AppStrings.get(R.string.tools_result_insert_unprotected)
            return@withContext PdfToolResult(
                outputFile = outputFile,
                pageCount = total,
                originalSizeBytes = baseFile.length(),
                outputSizeBytes = outputFile.length(),
                message = message
            )
        } catch (error: Throwable) {
            // Nothing half-written is left behind.
            outputFile.delete()
            throw error
        } finally {
            runCatching { result.close() }
            base?.let { runCatching { it.close() } }
            extra?.let { runCatching { it.close() } }
            baseFile.delete()
            extraFile?.delete()
        }
    }
}
