package com.example.scanpro.data

import android.net.Uri
import com.example.scanpro.core.text.UserFacingPluralException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** reOcrDocument and appendImages, on a real database and real files with the fake OCR and renderer. */
class RepositoryEditsTest : RepositoryTestBase() {
    private fun filesIn(doc: ScanDocument, sub: String) =
        File(File(doc.pdfPath).parentFile, sub).listFiles().orEmpty().map { it.name }.toSet()

    private fun photo(name: String, content: String = "photo"): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeText(content) })

    // ---- reOcrDocument

    @Test fun reOcrReplacesEveryPagesTextAndTheScript() = runBlocking {
        val doc = newDocument(3)
        ocr.text = "new words"
        val failed = repository.reOcrDocument(doc.id, OcrScript.DEVANAGARI)
        val after = load(doc.id)!!
        assertEquals(0, failed)
        assertEquals(listOf(OcrScript.DEVANAGARI), ocr.prepared)
        assertEquals(OcrScript.DEVANAGARI, after.ocrScript)
        assertEquals(listOf("new words", "new words", "new words"), after.pages.sortedBy { it.position }.map { it.ocrText })
    }

    @Test fun reOcrWithoutAScriptUsesTheDocumentsOwn() = runBlocking {
        val doc = newDocument(1)
        repository.reOcrDocument(doc.id)
        assertEquals(listOf(doc.ocrScript), ocr.prepared)
        assertEquals(doc.ocrScript, load(doc.id)!!.ocrScript)
    }

    @Test fun reOcrKeepsTheOldTextOfAPageThatFailsAndCountsIt() = runBlocking {
        val doc = newDocument(3)
        val bad = doc.page(1)
        ocr.failFor = { it.path == bad.imagePath }
        val failed = repository.reOcrDocument(doc.id)
        val after = load(doc.id)!!
        assertEquals(1, failed)
        assertEquals("text 1", after.pages.first { it.id == bad.id }.ocrText)
        assertEquals("fresh text", after.pages.first { it.id == doc.page(0).id }.ocrText)
    }

    @Test fun reOcrReplacesAUsersCorrectionsAndClearsTheEditedFlag() = runBlocking {
        val doc = newDocument(1)
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.saveDocument(doc.copy(pages = doc.pages.map { it.copy(ocrText = "my fix", ocrEdited = true) })) }
        repository.reOcrDocument(doc.id)
        val page = load(doc.id)!!.pages.single()
        assertEquals("fresh text", page.ocrText)
        assertFalse(page.ocrEdited)
    }

    @Test fun reOcrWhenTheModelCannotBeFetchedChangesNothing() = runBlocking {
        val doc = newDocument(2)
        ocr.prepareFailure = IllegalStateException("no model")
        try {
            repository.reOcrDocument(doc.id, OcrScript.CHINESE)
            fail("expected the model error")
        } catch (expected: IllegalStateException) {
        }
        val after = load(doc.id)!!
        assertEquals(doc.ocrScript, after.ocrScript)
        assertEquals(listOf("text 0", "text 1"), after.pages.sortedBy { it.position }.map { it.ocrText })
        assertEquals(0, ocr.calls)
    }

    @Test fun reOcrIsOneUndoStep() = runBlocking {
        val doc = newDocument(2)
        repository.reOcrDocument(doc.id)
        assertTrue(repository.undo(doc.id))
        assertEquals(listOf("text 0", "text 1"), load(doc.id)!!.pages.sortedBy { it.position }.map { it.ocrText })
    }

    // ---- appendImages

    @Test fun appendImagesAddsPagesAfterTheExistingOnesInOrder() = runBlocking {
        val doc = newDocument(2)
        ocr.text = "appended"
        repository.appendImages(doc.id, listOf(photo("a.jpg", "A"), photo("b.jpg", "B")), crops = listOf(CropQuad.DEFAULT, CropQuad.DEFAULT))
        val after = load(doc.id)!!
        val pages = after.pages.sortedBy { it.position }
        assertEquals(4, pages.size)
        assertEquals(doc.order(), pages.take(2).map { it.id })
        assertEquals(listOf(2, 3), pages.drop(2).map { it.position })
        assertEquals("A", File(pages[2].sourcePath).readText())
        assertEquals("B", File(pages[3].sourcePath).readText())
        assertEquals(listOf("appended", "appended"), pages.drop(2).map { it.ocrText })
        assertTrue(File(pages[2].imagePath).exists())
    }

    @Test fun appendImagesUsesTheReviewsFilterAndCropForEachPhoto() = runBlocking {
        val doc = newDocument(1)
        val crop = CropQuad.DEFAULT
        repository.appendImages(doc.id, listOf(photo("a.jpg"), photo("b.jpg")), crops = listOf(crop, crop), filters = listOf(PageFilter.ORIGINAL, PageFilter.values().last()))
        val added = load(doc.id)!!.pages.sortedBy { it.position }.drop(1)
        assertEquals(listOf(PageFilter.ORIGINAL, PageFilter.values().last()), added.map { it.filter })
        assertEquals(listOf(PageFilter.ORIGINAL, PageFilter.values().last()), renders.takeLast(2).map { it.filter })
    }

    @Test fun appendingNothingChangesNothing() = runBlocking {
        val doc = newDocument(2)
        repository.appendImages(doc.id, emptyList())
        assertEquals(doc.order(), load(doc.id)!!.order())
    }

    @Test fun appendImagesRefusesToGoPastThePageLimit() = runBlocking {
        val doc = newDocument(MAX_PAGES)
        try {
            repository.appendImages(doc.id, listOf(photo("a.jpg")), crops = listOf(CropQuad.DEFAULT))
            fail("expected the page limit")
        } catch (expected: UserFacingPluralException) {
        }
        assertEquals(MAX_PAGES, load(doc.id)!!.pages.size)
    }

    @Test fun aPhotoThatCannotBeReadLeavesNoFilesBehindAndTheDocumentUnchanged() = runBlocking {
        val doc = newDocument(1)
        val sourcesBefore = filesIn(doc, "source")
        val renderedBefore = filesIn(doc, "rendered")
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.jpg"))
        try {
            repository.appendImages(doc.id, listOf(photo("a.jpg"), missing), crops = listOf(CropQuad.DEFAULT, CropQuad.DEFAULT))
            fail("expected a read error")
        } catch (expected: Exception) {
        }
        assertEquals(1, load(doc.id)!!.pages.size)
        assertEquals(sourcesBefore, filesIn(doc, "source"))
        assertEquals(renderedBefore, filesIn(doc, "rendered"))
    }
}
