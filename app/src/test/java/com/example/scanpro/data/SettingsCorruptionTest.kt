package com.example.scanpro.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A damaged settings file must never open a locked app. */
class SettingsCorruptionTest {
    @Test
    fun aDamagedFileIsReplacedWithDefaultsExceptThatAppLockStaysOn() {
        val replacement = settingsAfterCorruption()
        assertTrue(replacement[booleanPreferencesKey("app_lock")] == true)
        // Nothing else is invented: every other setting reads as its default.
        assertEquals(1, replacement.asMap().size)
    }
}
