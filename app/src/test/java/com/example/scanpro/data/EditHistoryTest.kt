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
}
