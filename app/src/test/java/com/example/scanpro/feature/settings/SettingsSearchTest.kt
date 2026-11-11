package com.example.scanpro.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings search: every word must match, and a hit in the name outranks a hit in the description. */
class SettingsSearchTest {
    private fun rank(query: String, title: String, subtitle: String?, section: String) =
        settingMatchRank(searchWords(query), title, subtitle, section)

    @Test
    fun blankQueryMatchesNothing() {
        assertEquals(emptyList<String>(), searchWords("   "))
        assertNull(rank("", "Theme", "Light, dark, or follow the system", "Appearance"))
    }

    @Test
    fun aWordInTheNameIsTheBestMatch() {
        assertEquals(0, rank("theme", "Theme", "Light, dark, or follow the system", "Appearance"))
    }

    @Test
    fun aWordOnlyInTheDescriptionOrSectionIsASecondaryMatch() {
        assertEquals(1, rank("dark", "Theme", "Light, dark, or follow the system", "Appearance"))
        assertEquals(1, rank("appearance", "Theme", null, "Appearance"))
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        assertEquals(1, rank("dark theme", "Theme", "Light, dark, or follow the system", "Appearance"))
        assertNull(rank("dark banana", "Theme", "Light, dark, or follow the system", "Appearance"))
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpace() {
        assertEquals(listOf("dark", "mode"), searchWords("  DARK   Mode "))
        assertEquals(0, rank("  BACK UP ", "Back up all documents", null, "Backup"))
    }

    @Test
    fun aMissingDescriptionIsFine() {
        assertNull(rank("zebra", "Privacy policy", null, "Privacy & security"))
        assertEquals(0, rank("policy", "Privacy policy", null, "Privacy & security"))
    }
}
