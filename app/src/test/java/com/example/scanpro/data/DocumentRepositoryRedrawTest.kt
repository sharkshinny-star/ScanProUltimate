package com.example.scanpro.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Edits that redraw pages, with the native image pipeline and text recognition replaced by recorders (see
 * [RepositoryTestBase]). They check what the repository asks for and keeps, not the pixels: filters and
 * crops are covered by the imaging tests.
 */
class DocumentRepositoryRedrawTest : RepositoryTestBase() {

    private val tilted = CropQuad(topLeft = NormalizedPoint(0.1f, 0.1f), topRight = NormalizedPoint(0.9f, 0.05f))

    @Test fun applyFilterRedrawsOnlyThatPageAndReadsItsTextAgain() = runBlocking {
        val doc = newDocument(3)
        val target = doc.page(1)

        repository.applyFilter(doc.id, target.id, PageFilter.BW)

        assertEquals(1, renders.size)
        assertEquals(File(target.imagePath), renders.single().file)
        assertEquals(PageFilter.BW, renders.single().filter)
        val saved = load(doc.id)!!
        assertEquals(PageFilter.BW, saved.page(1).filter)
        assertEquals("fresh text", saved.page(1).ocrText)
        assertEquals("text 0", saved.page(0).ocrText)
        assertEquals(PageFilter.ORIGINAL, saved.page(2).filter)
        assertFalse(saved.hasCurrentPdf())
    }

    @Test fun rotatingFourTimesComesBackToUpright() = runBlocking {
        val doc = newDocument(1)
        val id = doc.pages.single().id
        val seen = (1..4).map {
            repository.rotatePage(doc.id, id)
            load(doc.id)!!.pages.single().rotationDegrees
        }
        assertEquals(listOf(90, 180, 270, 0), seen)
        assertEquals(listOf(90, 180, 270, 0), renders.map { it.rotation })
    }

    @Test fun applyCropStoresTheOutlineAndDrawsWithIt() = runBlocking {
        val doc = newDocument(2)
        repository.applyCrop(doc.id, doc.page(0).id, tilted)
        assertEquals(tilted, load(doc.id)!!.page(0).crop)
        assertEquals(tilted, renders.single().crop)
        assertEquals(CropQuad.DEFAULT, load(doc.id)!!.page(1).crop)
    }

    @Test fun applyAdjustmentsClampsOutOfRangeValues() = runBlocking {
        val doc = newDocument(1)
        val wild = PageAdjustments(brightness = 5f, contrast = 0.1f, saturation = 9f)
        repository.applyAdjustments(doc.id, doc.pages.single().id, wild)
        val saved = load(doc.id)!!.pages.single().adjustments
        assertEquals(wild.normalized(), saved)
        assertEquals(1f, saved.brightness, 0f)
        assertEquals(0.6f, saved.contrast, 0f)
        assertEquals(wild.normalized(), renders.single().adjustments)
    }

    @Test fun adjustmentsForAllPagesShareOnlyTheCleanUpSwitches() = runBlocking {
        val doc = newDocument(3)
        val chosen = PageAdjustments(brightness = 0.3f, removeShadows = true, removeFingers = true)

        repository.applyAdjustmentsToAll(doc.id, doc.page(1).id, chosen)

        val saved = load(doc.id)!!
        assertEquals(chosen, saved.page(1).adjustments)
        for (other in listOf(0, 2)) {
            assertEquals(0f, saved.page(other).adjustments.brightness, 0f)
            assertTrue(saved.page(other).adjustments.removeShadows)
            assertTrue(saved.page(other).adjustments.removeFingers)
        }
        assertEquals(3, renders.size)

        // Nothing differs now, so nothing is drawn again and no undo step is added.
        renders.clear()
        repository.applyAdjustmentsToAll(doc.id, doc.page(1).id, chosen)
        assertTrue(renders.isEmpty())
        assertEquals(1, repository.undoState.value[doc.id]?.undoSteps)
    }

    @Test fun aHighlightKeepsTheTextAndDoesNotReadThePageAgain() = runBlocking {
        val doc = newDocument(1)
        repository.addHighlight(doc.id, doc.pages.single().id)
        val page = load(doc.id)!!.pages.single()
        assertEquals(listOf(AnnotationType.HIGHLIGHT), page.annotations.map { it.type })
        assertEquals("text 0", page.ocrText)
        assertEquals(0, ocr.calls)
        assertEquals(1, renders.single().annotations.size)
    }

    @Test fun aRedactionReadsThePageAgain() = runBlocking {
        val doc = newDocument(1)
        repository.addRedaction(doc.id, doc.pages.single().id)
        assertEquals("fresh text", load(doc.id)!!.pages.single().ocrText)
        assertEquals(1, ocr.calls)
    }

    @Test fun aRedactionNeverLeavesTheOldTextWhenReadingFails() = runBlocking {
        val doc = newDocument(1)
        ocr.failing = true
        repository.addRedaction(doc.id, doc.pages.single().id)
        val page = load(doc.id)!!.pages.single()
        assertEquals("", page.ocrText)
        assertTrue(page.ocrWords.isEmpty())
    }

    @Test fun anOrdinaryEditKeepsTheOldTextWhenReadingFails() = runBlocking {
        val doc = newDocument(1)
        ocr.failing = true
        repository.applyFilter(doc.id, doc.pages.single().id, PageFilter.BW)
        assertEquals("text 0", load(doc.id)!!.pages.single().ocrText)
    }

    @Test fun handCorrectedTextSurvivesFiltersButNotRedactions() = runBlocking {
        val doc = newDocument(1)
        val id = doc.pages.single().id
        repository.updatePageOcrText(doc.id, id, "  my correction ")
        repository.applyFilter(doc.id, id, PageFilter.BW)
        load(doc.id)!!.pages.single().let {
            assertEquals("my correction", it.ocrText)
            assertTrue(it.ocrEdited)
        }
        assertEquals(0, ocr.calls)

        repository.addRedaction(doc.id, id)
        load(doc.id)!!.pages.single().let {
            assertEquals("fresh text", it.ocrText)
            assertFalse(it.ocrEdited)
        }
    }

    @Test fun removingARedactionReadsThePageAgainButRemovingAHighlightDoesNot() = runBlocking {
        val doc = newDocument(1)
        val id = doc.pages.single().id
        repository.addHighlight(doc.id, id)
        repository.removeLastAnnotation(doc.id, id)
        assertEquals(0, ocr.calls)

        repository.addRedaction(doc.id, id)
        val afterAdd = ocr.calls
        repository.removeLastAnnotation(doc.id, id)
        assertEquals(afterAdd + 1, ocr.calls)
        assertTrue(load(doc.id)!!.pages.single().annotations.isEmpty())
    }

    @Test fun watermarkStampsEveryPageAndKeepsItsText() = runBlocking {
        val doc = newDocument(3)
        repository.addWatermark(doc.id, WatermarkStyle(text = "  DRAFT  "))
        val saved = load(doc.id)!!
        saved.pages.forEach { page ->
            val mark = page.annotations.single()
            assertEquals(AnnotationType.TEXT, mark.type)
            assertEquals("DRAFT", mark.text)
        }
        assertEquals(listOf("text 0", "text 1", "text 2"), saved.pages.sortedBy { it.position }.map { it.ocrText })
        assertEquals(3, renders.size)
        assertEquals(0, ocr.calls)
    }

    @Test fun aBlankWatermarkIsRefused() = runBlocking {
        val doc = newDocument(1)
        try {
            repository.addWatermark(doc.id, WatermarkStyle(text = "   "))
            fail("expected an error")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(renders.isEmpty())
    }

    @Test fun undoRedrawsOnlyThePagesThatDiffer() = runBlocking {
        val doc = newDocument(3)
        repository.applyFilter(doc.id, doc.page(1).id, PageFilter.BW)
        renders.clear()

        assertTrue(repository.undo(doc.id))
        assertEquals(1, renders.size)
        assertEquals(PageFilter.ORIGINAL, renders.single().filter)
        assertEquals(PageFilter.ORIGINAL, load(doc.id)!!.page(1).filter)

        renders.clear()
        assertTrue(repository.redo(doc.id))
        assertEquals(PageFilter.BW, renders.single().filter)
        assertEquals(PageFilter.BW, load(doc.id)!!.page(1).filter)
    }

    @Test fun undoingAWatermarkTakesItOffEveryPage() = runBlocking {
        val doc = newDocument(2)
        repository.addWatermark(doc.id, WatermarkStyle(text = "COPY"))
        renders.clear()
        assertTrue(repository.undo(doc.id))
        assertEquals(2, renders.size)
        assertTrue(renders.all { it.annotations.isEmpty() })
        assertTrue(load(doc.id)!!.pages.all { it.annotations.isEmpty() })
    }

    @Test fun resetPageRestoresDefaultsInOneUndoableStep() = runBlocking {
        val doc = newDocument(1)
        val id = doc.pages.single().id
        repository.applyFilter(doc.id, id, PageFilter.BW)
        repository.rotatePage(doc.id, id)
        repository.addHighlight(doc.id, id)
        renders.clear()

        repository.resetPage(doc.id, id)

        load(doc.id)!!.pages.single().let {
            assertEquals(PageFilter.ORIGINAL, it.filter)
            assertEquals(0, it.rotationDegrees)
            assertEquals(CropQuad.DEFAULT, it.crop)
            assertTrue(it.annotations.isEmpty())
        }
        assertEquals(1, renders.size)

        assertTrue(repository.undo(doc.id))
        load(doc.id)!!.pages.single().let {
            assertEquals(PageFilter.BW, it.filter)
            assertEquals(90, it.rotationDegrees)
            assertEquals(1, it.annotations.size)
        }
    }

    @Test fun aFailedRenderChangesNothingAndRecordsNoUndoStep() = runBlocking {
        val doc = newDocument(2)
        val target = doc.page(0)
        renderFailure = { render -> if (render.filter == PageFilter.BW) IllegalStateException("draw failed") else null }

        try {
            repository.applyFilter(doc.id, target.id, PageFilter.BW)
            fail("expected the render error")
        } catch (expected: IllegalStateException) {
            assertEquals("draw failed", expected.message)
        }

        assertEquals(PageFilter.ORIGINAL, load(doc.id)!!.page(0).filter)
        assertNull(repository.undoState.value[doc.id])
        // The page is drawn again with the settings it still has, so the image never shows the failed edit.
        assertEquals(PageFilter.ORIGINAL, renders.last().filter)
        assertEquals(File(target.imagePath), renders.last().file)
    }

    @Test fun aWatermarkThatFailsHalfWayRestoresTheDrawnPages() = runBlocking {
        val doc = newDocument(3)
        // The second page to be drawn with a watermark fails; the rollback draws without one, so it is not hit.
        renderFailure = { render ->
            if (render.annotations.isNotEmpty() && renders.count { it.annotations.isNotEmpty() } == 2) {
                IllegalStateException("disk full")
            } else null
        }

        try {
            repository.addWatermark(doc.id, WatermarkStyle(text = "DRAFT"))
            fail("expected the render error")
        } catch (expected: IllegalStateException) {
        }

        val saved = load(doc.id)!!
        assertTrue(saved.pages.all { it.annotations.isEmpty() })
        assertNull(repository.undoState.value[doc.id])
        // Both pages that were touched are drawn again as they were saved.
        val redrawn = renders.filter { it.annotations.isEmpty() }.map { it.file }.toSet()
        assertEquals(setOf(File(doc.page(0).imagePath), File(doc.page(1).imagePath)), redrawn)
    }

    @Test fun markupBaseDrawsThePageWithoutItsMarkup() = runBlocking {
        val doc = newDocument(1)
        val id = doc.pages.single().id
        repository.addHighlight(doc.id, id)
        renders.clear()

        val base = repository.markupBase(doc.id, id)

        assertEquals("markup_$id.jpg", base.name)
        assertEquals(1, renders.size)
        assertTrue(renders.single().annotations.isEmpty())
        assertTrue(base.exists())
    }
}
