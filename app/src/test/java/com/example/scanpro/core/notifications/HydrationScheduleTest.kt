package com.example.scanpro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HydrationScheduleTest {

    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        set(2026, Calendar.NOVEMBER, 16, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `slots are one, two and three hours apart from the day start`() {
        // 09:00 start -> 10:00, 12:00, 15:00.
        assertEquals(listOf(10 * 60, 12 * 60, 15 * 60), HydrationSchedule.slotMinutes)
    }

    @Test
    fun `before the first slot the next reminder is at ten`() {
        val next = HydrationSchedule.next(at(7, 30), zone)
        assertEquals(at(10, 0), next.atMs)
        assertEquals(0, next.slot)
    }

    @Test
    fun `between slots the next reminder is the following slot`() {
        val next = HydrationSchedule.next(at(10, 0), zone)
        assertEquals(at(12, 0), next.atMs)
        assertEquals(1, next.slot)
    }

    @Test
    fun `after the last slot the next reminder is tomorrow morning`() {
        val next = HydrationSchedule.next(at(16, 0), zone)
        assertEquals(1, Calendar.getInstance(zone).apply { timeInMillis = next.atMs }.get(Calendar.DAY_OF_MONTH) - 16)
        assertEquals(0, next.slot)
        assertEquals(10, Calendar.getInstance(zone).apply { timeInMillis = next.atMs }.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `a reminder is shown on time and within the grace period, not after it`() {
        val due = at(12, 0)
        assertTrue(HydrationSchedule.isDue(due, due))
        assertTrue(HydrationSchedule.isDue(due, due + 20 * 60 * 1000))
        assertFalse("too late", HydrationSchedule.isDue(due, due + HydrationSchedule.GRACE_MS))
        assertFalse("not yet due", HydrationSchedule.isDue(due, due - 1))
        assertFalse("never scheduled", HydrationSchedule.isDue(0, due))
    }
}
