package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    private fun pages(filter: PageFilter) = listOf(ScanPage("p1", "d", 0, "s", "i", filter = filter))

    @Test
    fun undoAndRedoWalkThroughTheSteps() {
        val history = EditHistory()
        history.record("d", pages(PageFilter.ORIGINAL))   // ORIGINAL -> GREY
        history.record("d", pages(PageFilter.GRAY))       // GREY -> BW
        val current = pages(PageFilter.BW)

        val back1 = history.undo("d", current)
        assertEquals(PageFilter.GRAY, back1!!.single().filter)
        val back2 = history.undo("d", back1)
        assertEquals(PageFilter.ORIGINAL, back2!!.single().filter)
        assertNull(history.undo("d", back2))

        val forward = history.redo("d", back2)
        assertEquals(PageFilter.GRAY, forward!!.single().filter)
        assertEquals(UndoState(undoSteps = 1, redoSteps = 1), history.state.value["d"])
    }

    @Test
    fun aNewEditEndsTheRedoTrail() {
        val history = EditHistory()
        history.record("d", pages(PageFilter.ORIGINAL))
        history.undo("d", pages(PageFilter.GRAY))
        assertTrue(history.state.value["d"]!!.canRedo)
        history.record("d", pages(PageFilter.ORIGINAL))
        assertFalse(history.state.value["d"]!!.canRedo)
    }

    @Test
    fun historyIsCappedAtTheLimit() {
        val history = EditHistory(limit = 3)
        repeat(5) { history.record("d", pages(PageFilter.entries[it % PageFilter.entries.size])) }
        assertEquals(3, history.state.value["d"]!!.undoSteps)
    }

    @Test
    fun rollbackRestoresAStepThatFailedToApply() {
        val history = EditHistory()
        history.record("d", pages(PageFilter.ORIGINAL))
        val taken = history.undo("d", pages(PageFilter.GRAY))!!
        history.rollback("d", wasUndo = true, taken = taken)
        assertEquals(UndoState(undoSteps = 1, redoSteps = 0), history.state.value["d"])
    }

    @Test
    fun clearForgetsEverything() {
        val history = EditHistory()
        history.record("d", pages(PageFilter.ORIGINAL))
        history.clear("d")
        assertNull(history.state.value["d"])
        assertTrue(history.retained("d").isEmpty())
    }

    private fun wordy(filter: PageFilter, id: String = "p1", words: Int = 0) = ScanPage(
        id, "d", 0, "s", "i", filter = filter,
        ocrWords = List(words) { OcrWord("word$it", 0f, 0f, 1f, 1f) }
    )

    @Test
    fun unchangedPagesAreSharedBetweenSteps() {
        val history = EditHistory()
        val big = wordy(PageFilter.ORIGINAL, id = "big", words = 50)
        val first = listOf(big, wordy(PageFilter.ORIGINAL, id = "edited"))
        val second = listOf(big.copy(), wordy(PageFilter.GRAY, id = "edited"))
        history.record("d", first)
        history.record("d", second)

        val kept = history.retained("d")
        // The second step reuses the first step's copy of the unchanged page instead of holding its own.
        assertTrue(kept[0] === kept[2])
    }

    @Test
    fun theOldestStepsGoFirstWhenTheyHoldTooMuch() {
        val history = EditHistory(limit = 20, maxBytes = 20_000)
        repeat(10) { n ->
            // Each step is a different page, so nothing is shared and every step carries its own words.
            history.record("d", listOf(wordy(PageFilter.entries[n % PageFilter.entries.size], id = "p$n", words = 100)))
        }
        val steps = history.state.value.getValue("d").undoSteps
        assertTrue("kept $steps steps", steps in 1..9)
    }

    @Test
    fun theNewestStepIsKeptEvenWhenItAloneIsOverTheBudget() {
        val history = EditHistory(limit = 20, maxBytes = 100)
        history.record("d", listOf(wordy(PageFilter.ORIGINAL, words = 500)))
        assertEquals(1, history.state.value.getValue("d").undoSteps)
    }
}
