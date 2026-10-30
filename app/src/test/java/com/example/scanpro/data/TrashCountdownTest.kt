package com.example.scanpro.data

import com.example.scanpro.core.ui.TrashCountdown
import com.example.scanpro.core.ui.trashCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashCountdownTest {

    @Test
    fun `trashCountdown formats days remaining correctly`() {
        val now = 1000000000000L
        val dayMs = 24L * 60L * 60L * 1000L

        // Trashed 1 hour ago -> 7 days remaining
        val text7 = trashCountdown(now - 1L * 60L * 60L * 1000L, now)
        assertEquals(TrashCountdown.Days(7), text7)
        assertFalse(text7.isUrgent)

        // Trashed 2 days ago -> 5 days remaining
        val text5 = trashCountdown(now - 2L * dayMs, now)
        assertEquals(TrashCountdown.Days(5), text5)
        assertFalse(text5.isUrgent)

        // Trashed 6 days ago -> 1 day remaining
        val text1 = trashCountdown(now - 6L * dayMs, now)
        assertEquals(TrashCountdown.Days(1), text1)
        assertFalse(text1.isUrgent)
    }

    @Test
    fun `trashCountdown formats hours and minutes when urgent`() {
        val now = 1000000000000L
        val hourMs = 60L * 60L * 1000L
        val dayMs = 24L * hourMs

        // 12 hours remaining
        val text12h = trashCountdown(now - (7L * dayMs - 12L * hourMs), now)
        assertEquals(TrashCountdown.Hours(12), text12h)
        assertTrue(text12h.isUrgent)

        // 30 minutes remaining
        val text30m = trashCountdown(now - (7L * dayMs - 30L * 60L * 1000L), now)
        assertEquals(TrashCountdown.Minutes(30), text30m)
        assertTrue(text30m.isUrgent)
    }

    @Test
    fun `trashCountdown handles expired trashed documents`() {
        val now = 1000000000000L
        val dayMs = 24L * 60L * 60L * 1000L

        val textExpired = trashCountdown(now - 8L * dayMs, now)
        assertEquals(TrashCountdown.Soon, textExpired)
        assertTrue(textExpired.isUrgent)
    }
}
