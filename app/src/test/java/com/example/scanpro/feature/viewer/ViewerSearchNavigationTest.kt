package com.example.scanpro.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Next and Previous match in the PDF viewer go by the page on screen, not by the last match visited. */
class ViewerSearchNavigationTest {
    private val matches = listOf(2, 9, 30)

    @Test
    fun nextIsTheFirstMatchAfterThePageOnScreen() {
        assertEquals(0, nextMatchIndex(matches, 0))   // before the first match
        assertEquals(1, nextMatchIndex(matches, 2))   // on a match: the one after it
        assertEquals(2, nextMatchIndex(matches, 20))  // scrolled away to page 21: the match at 30, not the second one
    }

    @Test
    fun nextWrapsToTheFirstMatchPastTheEnd() {
        assertEquals(0, nextMatchIndex(matches, 30))
        assertEquals(0, nextMatchIndex(matches, 55))
    }

    @Test
    fun previousIsTheLastMatchBeforeThePageOnScreen() {
        assertEquals(1, previousMatchIndex(matches, 20))
        assertEquals(0, previousMatchIndex(matches, 9))
        assertEquals(2, previousMatchIndex(matches, 55))
    }

    @Test
    fun previousWrapsToTheLastMatchBeforeTheStart() {
        assertEquals(2, previousMatchIndex(matches, 2))
        assertEquals(2, previousMatchIndex(matches, 0))
    }
}
