package com.example.scanpro.core.notifications

import com.example.scanpro.core.notifications.HydrationSchedule.Timing
import com.example.scanpro.core.notifications.HydrationSchedule.Wording
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
    fun `out of the box the reminders are three hours apart through the day`() {
        // 09:00, 12:00, 15:00 and 18:00.
        assertEquals(listOf(9 * 60, 12 * 60, 15 * 60, 18 * 60), Timing().slotMinutes)
    }

    @Test
    fun `the user's timings set the first reminder, the gap and the cut-off`() {
        // 08:00 to 17:00 every two hours: 17:00 itself is not a multiple of the gap, so 16:00 is the last.
        assertEquals(listOf(8 * 60, 10 * 60, 12 * 60, 14 * 60, 16 * 60), Timing(8 * 60, 17 * 60, 2 * 60).slotMinutes)
        assertEquals(listOf(7 * 60), Timing(7 * 60, 7 * 60, 60).slotMinutes)
    }

    @Test
    fun `damaged timings still give a sane day`() {
        // A cut-off before the first reminder leaves the first one only.
        assertEquals(listOf(14 * 60), Timing(14 * 60, 9 * 60, 3 * 60).slotMinutes)
        // A gap of zero or less never becomes a reminder a minute.
        assertEquals(10, Timing(9 * 60, 18 * 60, 0).slotMinutes.size)
        assertEquals(listOf(0), Timing(-5, -1, -1).slotMinutes)
        assertEquals(listOf(24 * 60 - 1), Timing(99_999, 99_999, 99_999).slotMinutes)
    }

    @Test
    fun `before the first slot the next reminder is the first of the day`() {
        val next = HydrationSchedule.next(at(7, 30), Timing(), zone)
        assertEquals(at(9, 0), next.atMs)
        assertEquals(9 * 60, next.minute)
    }

    @Test
    fun `on a slot the next reminder is the following slot`() {
        val next = HydrationSchedule.next(at(12, 0), Timing(), zone)
        assertEquals(at(15, 0), next.atMs)
        assertEquals(15 * 60, next.minute)
    }

    @Test
    fun `after the last slot the next reminder is tomorrow morning`() {
        val next = HydrationSchedule.next(at(18, 0), Timing(), zone)
        assertEquals(at(9, 0) + 24L * 60 * 60 * 1000, next.atMs)
        assertEquals(9 * 60, next.minute)
    }

    @Test
    fun `the next reminder follows the user's timings`() {
        val next = HydrationSchedule.next(at(10, 30), Timing(8 * 60, 20 * 60, 2 * 60), zone)
        assertEquals(at(12, 0), next.atMs)
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

    @Test
    fun `work queued under other timings is no longer a slot`() {
        assertTrue(HydrationSchedule.isSlot(at(12, 0), Timing(), zone))
        // 10:00 was a reminder before the timings became the user's; it is not one now.
        assertFalse(HydrationSchedule.isSlot(at(10, 0), Timing(), zone))
        assertTrue(HydrationSchedule.isSlot(at(10, 0), Timing(8 * 60, 20 * 60, 2 * 60), zone))
    }

    @Test
    fun `the wording follows the time of day and marks the last reminder`() {
        val timing = Timing()
        assertEquals(Wording.MORNING, HydrationSchedule.wording(9 * 60, timing))
        assertEquals(Wording.MIDDAY, HydrationSchedule.wording(12 * 60, timing))
        assertEquals(Wording.LATER, HydrationSchedule.wording(15 * 60, timing))
        assertEquals(Wording.LAST, HydrationSchedule.wording(18 * 60, timing))
        // A single reminder a day is not a "last call".
        assertEquals(Wording.MORNING, HydrationSchedule.wording(7 * 60, Timing(7 * 60, 7 * 60, 60)))
    }
}
