package com.example.scanpro.data

import android.content.Context
import androidx.core.content.edit

/**
 * When the "Rate your experience" sheet may ask: after the first and the second completed scan, then at most once
 * every [REPEAT_AFTER_MS] (at a scan completion), and never twice within [MIN_GAP_MS]. Pure, so it is unit-tested;
 * [FeedbackPrompts] keeps it in SharedPreferences.
 *
 * @param completedScans scans saved as documents, in any camera mode.
 * @param promptsShown how many times the sheet has been shown (submitted or dismissed both count).
 * @param lastShownAt when it was last shown, in epoch milliseconds; 0 if never.
 */
data class FeedbackSchedule(val completedScans: Int = 0, val promptsShown: Int = 0, val lastShownAt: Long = 0L) {

    /** Whether the sheet should show now, right after a scan was completed and its "Scan saved" sheet closed. */
    fun due(now: Long): Boolean {
        if (completedScans <= 0) return false
        // A clock set backwards counts as "just shown", so the user is never asked twice in a row.
        if (lastShownAt != 0L && now - lastShownAt < MIN_GAP_MS) return false
        return if (promptsShown < EARLY_PROMPTS) completedScans > promptsShown else now - lastShownAt >= REPEAT_AFTER_MS
    }

    fun withCompletedScan(): FeedbackSchedule = copy(completedScans = completedScans + 1)

    fun withShown(now: Long): FeedbackSchedule = copy(promptsShown = promptsShown + 1, lastShownAt = now)

    companion object {
        /** The first and the second completed scan each get one prompt. */
        const val EARLY_PROMPTS = 2
        const val MIN_GAP_MS = 24L * 60 * 60 * 1000
        const val REPEAT_AFTER_MS = 30L * MIN_GAP_MS
    }
}

/** [FeedbackSchedule] kept in SharedPreferences and read synchronously, like [FirstRunHints]. */
object FeedbackPrompts {
    private const val PREFS = "feedback_prompts"
    private const val KEY_SCANS = "completed_scans"
    private const val KEY_SHOWN = "prompts_shown"
    private const val KEY_LAST = "last_shown_at"

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): FeedbackSchedule = prefs(context).let {
        FeedbackSchedule(it.getInt(KEY_SCANS, 0), it.getInt(KEY_SHOWN, 0), it.getLong(KEY_LAST, 0L))
    }

    private fun save(context: Context, schedule: FeedbackSchedule) = prefs(context).edit {
        putInt(KEY_SCANS, schedule.completedScans)
        putInt(KEY_SHOWN, schedule.promptsShown)
        putLong(KEY_LAST, schedule.lastShownAt)
    }

    /** A scan was saved as a document (any camera mode, or a scan handed over by another scanner). */
    @Synchronized
    fun recordCompletedScan(context: Context) = save(context, load(context).withCompletedScan())

    fun isDue(context: Context, now: Long = System.currentTimeMillis()): Boolean = load(context).due(now)

    /** The sheet is on screen: whatever the user does with it now, it counts as shown. */
    @Synchronized
    fun markShown(context: Context, now: Long = System.currentTimeMillis()) = save(context, load(context).withShown(now))
}
