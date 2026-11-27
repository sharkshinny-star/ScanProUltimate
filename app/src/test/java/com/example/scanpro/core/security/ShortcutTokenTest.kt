package com.example.scanpro.core.security

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The per-install secret the app's own shortcuts carry (F215). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShortcutTokenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theTokenIsMadeOnceAndKept() {
        val first = ShortcutToken.of(context)
        assertTrue(first.length >= 32)
        assertEquals(first, ShortcutToken.of(context))
    }

    @Test
    fun onlyAnIntentCarryingTheTokenMatches() {
        val token = ShortcutToken.of(context)
        assertTrue(ShortcutToken.matches(context, Intent().putExtra(ShortcutToken.EXTRA, token)))
        assertFalse(ShortcutToken.matches(context, Intent()))
        assertFalse(ShortcutToken.matches(context, Intent().putExtra(ShortcutToken.EXTRA, "")))
        assertFalse(ShortcutToken.matches(context, Intent().putExtra(ShortcutToken.EXTRA, token.dropLast(1))))
        // A token of the wrong type is not a token.
        assertFalse(ShortcutToken.matches(context, Intent().putExtra(ShortcutToken.EXTRA, 7)))
    }
}
