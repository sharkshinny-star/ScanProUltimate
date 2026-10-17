package com.example.scanpro.data

import com.example.scanpro.ui.formatTrashCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashCountdownTest {

    @Test
    fun `formatTrashCountdown formats days remaining correctly`() {
        val now = 1000000000000L
        val dayMs = 24L * 60L * 60L * 1000L

        // Trashed 1 hour ago -> 7 days remaining
        val (text7, urgent7) = formatTrashCountdown(now - 1L * 60L * 60L * 1000L, now)
        assertEquals("Auto-deletes in 7 days", text7)
        assertFalse(urgent7)

        // Trashed 2 days ago -> 5 days remaining
        val (text5, urgent5) = formatTrashCountdown(now - 2L * dayMs, now)
        assertEquals("Auto-deletes in 5 days", text5)
        assertFalse(urgent5)

        // Trashed 6 days ago -> 1 day remaining
        val (text1, urgent1) = formatTrashCountdown(now - 6L * dayMs, now)
        assertEquals("Auto-deletes in 1 day", text1)
        assertFalse(urgent1)
    }

    @Test
    fun `formatTrashCountdown formats hours and minutes when urgent`() {
        val now = 1000000000000L
        val hourMs = 60L * 60L * 1000L
        val dayMs = 24L * hourMs

        // 12 hours remaining
        val (text12h, urgent12h) = formatTrashCountdown(now - (7L * dayMs - 12L * hourMs), now)
        assertEquals("Auto-deletes in 12 hours", text12h)
        assertTrue(urgent12h)

        // 30 minutes remaining
        val (text30m, urgent30m) = formatTrashCountdown(now - (7L * dayMs - 30L * 60L * 1000L), now)
        assertEquals("Auto-deletes in 30 minutes", text30m)
        assertTrue(urgent30m)
    }

    @Test
    fun `formatTrashCountdown handles expired trashed documents`() {
        val now = 1000000000000L
        val dayMs = 24L * 60L * 60L * 1000L

        val (textExpired, urgentExpired) = formatTrashCountdown(now - 8L * dayMs, now)
        assertEquals("Auto-deletes soon", textExpired)
        assertTrue(urgentExpired)
    }
}
