package com.example.scanpro.core.notifications

import java.util.Calendar
import java.util.TimeZone

/**
 * When the drink-water reminders fall. The user sets the first reminder of the day, the time after which there are
 * none, and the gap between them ([Timing]); out of the box that is every three hours from 09:00 to 18:00 (09:00,
 * 12:00, 15:00 and 18:00). Worked out from the wall clock each time, so a late run, a new time zone or a clock change
 * never shifts the following reminders.
 */
object HydrationSchedule {
    const val DEFAULT_FIRST_MINUTE = 9 * 60
    const val DEFAULT_LAST_MINUTE = 18 * 60
    const val DEFAULT_EVERY_MINUTES = 3 * 60

    /** The shortest gap the schedule accepts; anything shorter would be a nuisance, not a nudge. */
    const val MIN_EVERY_MINUTES = 60

    private const val DAY_MINUTES = 24 * 60

    /** A reminder that ran this long after its time (phone off, battery saver) is skipped rather than shown late. */
    const val GRACE_MS = 45L * 60 * 1000

    /**
     * The user's timings, as minutes of the day. Stored values are never trusted as they are: [slotMinutes] clamps
     * them, so a damaged preference still gives at least one reminder a day and never one a minute.
     */
    data class Timing(
        val firstMinute: Int = DEFAULT_FIRST_MINUTE,
        val lastMinute: Int = DEFAULT_LAST_MINUTE,
        val everyMinutes: Int = DEFAULT_EVERY_MINUTES
    ) {
        /** Minute of the day of each reminder, in order: the first, then one every gap up to and including the last. */
        val slotMinutes: List<Int>
            get() {
                val first = firstMinute.coerceIn(0, DAY_MINUTES - 1)
                val last = lastMinute.coerceIn(first, DAY_MINUTES - 1)
                val every = everyMinutes.coerceIn(MIN_EVERY_MINUTES, DAY_MINUTES)
                return generateSequence(first) { it + every }.takeWhile { it <= last }.toList()
            }
    }

    data class Reminder(val atMs: Long, val minute: Int)

    /** Which of the notice's wordings suits a reminder. */
    enum class Wording { MORNING, MIDDAY, LATER, LAST }

    /** The first reminder strictly after [nowMs], today or tomorrow. */
    fun next(nowMs: Long, timing: Timing = Timing(), zone: TimeZone = TimeZone.getDefault()): Reminder {
        val slots = timing.slotMinutes
        for (dayOffset in 0..1) {
            for (minute in slots) {
                val at = Calendar.getInstance(zone).apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, minute / 60)
                    set(Calendar.MINUTE, minute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (at > nowMs) return Reminder(at, minute)
            }
        }
        // Unreachable: tomorrow's first reminder is always in the future.
        return Reminder(nowMs + 24L * 60 * 60 * 1000, slots.first())
    }

    /** Whether a reminder due at [dueAtMs] should still be shown at [nowMs]. */
    fun isDue(dueAtMs: Long, nowMs: Long): Boolean = dueAtMs > 0 && nowMs - dueAtMs in 0 until GRACE_MS

    /**
     * Whether [dueAtMs] is still one of the day's reminders. False for work queued under timings the user has since
     * changed (or by a version with other timings), which must not show a notice at the old time.
     */
    fun isSlot(dueAtMs: Long, timing: Timing, zone: TimeZone = TimeZone.getDefault()): Boolean =
        minuteOfDay(dueAtMs, zone) in timing.slotMinutes

    fun minuteOfDay(atMs: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        Calendar.getInstance(zone).run {
            timeInMillis = atMs
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }

    /** The day's final reminder says so (when there is more than one); the others go by the time of day. */
    fun wording(minute: Int, timing: Timing): Wording {
        val slots = timing.slotMinutes
        return when {
            slots.size > 1 && minute >= slots.last() -> Wording.LAST
            minute < 11 * 60 -> Wording.MORNING
            minute < 14 * 60 -> Wording.MIDDAY
            else -> Wording.LATER
        }
    }
}
