package com.example.scanpro.core.security

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.security.MessageDigest
import java.util.UUID

/**
 * A secret made once per install and carried by the app's own launcher shortcuts (F215). The main activity has to be
 * exported, so any app can start it with one of its shortcut actions; the two that leave the app (they open an email
 * draft to support, one with a diagnostics file attached) are honoured only with this token, which another app cannot
 * know. Shortcuts pinned by an older build get the token the next time the app publishes its shortcuts.
 */
object ShortcutToken {
    const val EXTRA = "com.example.scanpro.extra.SHORTCUT_TOKEN"
    private const val PREFS = "shortcut_token"
    private const val KEY = "token"

    /** This install's token, made on first use. */
    fun of(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val made = UUID.randomUUID().toString()
        prefs.edit { putString(KEY, made) }
        return made
    }

    /** True when [intent] carries this install's token. */
    fun matches(context: Context, intent: Intent): Boolean {
        val given = runCatching { intent.getStringExtra(EXTRA) }.getOrNull() ?: return false
        return MessageDigest.isEqual(given.toByteArray(), of(context).toByteArray())
    }
}
