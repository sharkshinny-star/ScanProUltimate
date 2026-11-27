package com.example.scanpro.feature.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackModelTest {
    @Test
    fun fourAndFiveAreThankedAndLowerRatingsAreAskedWhatWentWrong() {
        assertEquals(RatingFollowUp.THANKS, followUpFor(5))
        assertEquals(RatingFollowUp.THANKS, followUpFor(4))
        (1..3).forEach { assertEquals("rating $it", RatingFollowUp.PROBLEM, followUpFor(it)) }
    }

    @Test
    fun nothingFollowsWhatIsNotARating() {
        assertNull(followUpFor(0))
        assertNull(followUpFor(6))
        assertNull(Mood.of(0))
        assertEquals(Mood.LOVED, Mood.of(5))
    }

    @Test
    fun formNeedsACategoryAndSixCharacters() {
        assertFalse(canSubmitForm(FeedbackFormKind.ISSUE, emptySet(), "It crashes on save"))
        assertFalse(canSubmitForm(FeedbackFormKind.ISSUE, setOf(IssueCategory.CRASH), "  abc  "))
        assertTrue(canSubmitForm(FeedbackFormKind.ISSUE, setOf(IssueCategory.CRASH), "abcdef"))
        assertTrue("a suggestion has no categories", canSubmitForm(FeedbackFormKind.FEATURE, emptySet(), "Dark PDFs"))
    }
}
