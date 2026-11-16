package com.example.scanpro.feature.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackModelTest {
    @Test
    fun aFaceIsNeededBeforeSubmitting() {
        assertFalse(canSubmitRating(0, emptySet(), ""))
        assertTrue(canSubmitRating(2, emptySet(), ""))
    }

    @Test
    fun otherAloneNeedsText() {
        val other = setOf(FeedbackReason.OTHER)
        assertFalse(canSubmitRating(2, other, "  "))
        assertTrue(canSubmitRating(2, other, "Pages upside down"))
        assertTrue("another chip says enough", canSubmitRating(2, other + FeedbackReason.SLOW, ""))
    }

    @Test
    fun lowAndHighRatingsAskDifferentThings() {
        assertTrue(FeedbackReason.BLURRY in reasonsFor(1))
        assertEquals(reasonsFor(1), reasonsFor(3))
        assertTrue(FeedbackReason.QUALITY in reasonsFor(5))
        assertEquals(reasonsFor(4), reasonsFor(5))
        assertTrue(reasonsFor(0).isEmpty())
        assertTrue(reasonsFor(2).last() == FeedbackReason.OTHER && reasonsFor(4).last() == FeedbackReason.OTHER)
    }

    @Test
    fun emailTextListsTheAnswers() {
        val text = ratingEmailText("Rating: 2/5 (Poor)", "What went wrong?", listOf("Too slow", "Other"), "Comments:", " Takes ages \n")
        assertEquals("Rating: 2/5 (Poor)\n\nWhat went wrong?\n• Too slow\n• Other\n\nComments:\nTakes ages", text)
        assertEquals("Rating: 5/5 (Loved it)", ratingEmailText("Rating: 5/5 (Loved it)", "Q", emptyList(), "Comments:", ""))
    }

    @Test
    fun formNeedsACategoryAndSixCharacters() {
        assertFalse(canSubmitForm(FeedbackFormKind.ISSUE, emptySet(), "It crashes on save"))
        assertFalse(canSubmitForm(FeedbackFormKind.ISSUE, setOf(IssueCategory.CRASH), "  abc  "))
        assertTrue(canSubmitForm(FeedbackFormKind.ISSUE, setOf(IssueCategory.CRASH), "abcdef"))
        assertTrue("a suggestion has no categories", canSubmitForm(FeedbackFormKind.FEATURE, emptySet(), "Dark PDFs"))
    }
}
