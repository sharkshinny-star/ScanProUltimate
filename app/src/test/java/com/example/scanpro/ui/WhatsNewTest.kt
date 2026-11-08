package com.example.scanpro.ui

import com.example.scanpro.data.WHATS_NEW_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    @Test
    fun whatsNewVersionIsBumpedForNewFeatures() {
        assertEquals("WHATS_NEW_VERSION should be bumped to 2 for new features", 2, WHATS_NEW_VERSION)
    }

    @Test
    fun whatsNewDue_evaluatesCorrectly() {
        // Welcome not yet seen -> false (new installs get Welcome, not What's New)
        assertFalse(isWhatsNewDue(welcomeSeen = false, whatsNewSeen = 0))
        assertFalse(isWhatsNewDue(welcomeSeen = false, whatsNewSeen = 1))

        // Existing user who saw version 1 -> true (notified of new features!)
        assertTrue(isWhatsNewDue(welcomeSeen = true, whatsNewSeen = 1))
        assertTrue(isWhatsNewDue(welcomeSeen = true, whatsNewSeen = 0))

        // User who already saw version 2 -> false (already notified)
        assertFalse(isWhatsNewDue(welcomeSeen = true, whatsNewSeen = 2))
        assertFalse(isWhatsNewDue(welcomeSeen = true, whatsNewSeen = 3))
    }

    private fun isWhatsNewDue(welcomeSeen: Boolean, whatsNewSeen: Int): Boolean {
        return welcomeSeen && whatsNewSeen < WHATS_NEW_VERSION
    }
}
