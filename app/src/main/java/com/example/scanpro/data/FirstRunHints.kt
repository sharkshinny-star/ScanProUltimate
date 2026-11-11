package com.example.scanpro.data

import android.content.Context
import androidx.core.content.edit

/**
 * One-time introductions for people new to the app (F146): the illustrated dialog a PDF tool shows the first time it
 * is opened, and the short tour of Home, Files and Tools after the welcome. (The camera's mode explainers keep their
 * own "Don't remind me again" switches in the scanner's preferences.)
 *
 * Armed only when the welcome is finished, so someone updating from an older version, who already knows the app, is
 * not walked through it again. Settings > Show introductions again re-arms everything. Kept in SharedPreferences and
 * read synchronously, so whether a dialog shows is decided in the same frame as the screen and it never pops in late.
 */
object FirstRunHints {
    /** The tour of Home, Files and Tools, shown on Home after the welcome. */
    const val HOME_TOUR = "home_tour"

    /** The introduction a tool shows the first time it is opened. */
    fun toolKey(toolId: String) = "tool_$toolId"

    private const val PREFS = "first_run_hints"
    private const val KEY_ARMED = "armed"
    private const val SEEN_PREFIX = "seen_"

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the hint [key] should show now: hints are armed and this one has not been seen. */
    fun isDue(context: Context, key: String): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_ARMED, false) && !prefs.getBoolean(SEEN_PREFIX + key, false)
    }

    /** Called on every way out of a hint (its buttons, Back, a tap outside), so it never comes back by itself. */
    fun markSeen(context: Context, key: String) = prefs(context).edit { putBoolean(SEEN_PREFIX + key, true) }

    /** The welcome is finished: this is someone new, so the hints may show from now on. Hints already seen stay seen. */
    fun arm(context: Context) = prefs(context).edit { putBoolean(KEY_ARMED, true) }

    /** Settings > Show introductions again: every hint shows once more. */
    fun reset(context: Context) = prefs(context).edit {
        clear()
        putBoolean(KEY_ARMED, true)
    }
}
