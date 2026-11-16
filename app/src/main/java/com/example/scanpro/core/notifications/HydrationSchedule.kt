package com.example.scanpro.core.notifications

import java.util.Calendar
import java.util.TimeZone

/**
 * When the drink-water reminders fall. Three a day, with widening gaps: one hour after the day starts, then two
 * hours later, then three hours later (10:00, 12:00 and 15:00 with the day starting at 09:00). Worked out from the
 * wall clock each time, so a late run, a new time zone or a clock change never shifts the following reminders.
 */
object HydrationSchedule {
    /** Minute of the day the count starts from: 09:00. */
    const val DAY_START_MINUTE = 9 * 60

    /** Hours between one reminder and the next, in order. */
    val GAP_HOURS = listOf(1, 2, 3)

    /** Minute of the day of each reminder: 600, 720 and 900. */
    val slotMinutes: List<Int> = GAP_HOURS.runningFold(DAY_START_MINUTE) { at, gap -> at + gap * 60 }.drop(1)

    /** A reminder that ran this long after its time (phone off, battery saver) is skipped rather than shown late. */
    const val GRACE_MS = 45L * 60 * 1000

    data class Reminder(val atMs: Long, val slot: Int)

    /** The first reminder strictly after [nowMs], today or tomorrow. */
    fun next(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Reminder {
        for (dayOffset in 0..1) {
            slotMinutes.forEachIndexed { slot, minute ->
                val at = Calendar.getInstance(zone).apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, minute / 60)
                    set(Calendar.MINUTE, minute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (at > nowMs) return Reminder(at, slot)
            }
        }
        // Unreachable: tomorrow's first reminder is always in the future.
        return Reminder(nowMs + 24L * 60 * 60 * 1000, 0)
    }

    /** Whether a reminder due at [dueAtMs] should still be shown at [nowMs]. */
    fun isDue(dueAtMs: Long, nowMs: Long): Boolean = dueAtMs > 0 && nowMs - dueAtMs in 0 until GRACE_MS
}
