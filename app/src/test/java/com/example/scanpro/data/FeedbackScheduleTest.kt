package com.example.scanpro.data

import com.example.scanpro.data.FeedbackSchedule.Companion.MIN_GAP_MS
import com.example.scanpro.data.FeedbackSchedule.Companion.REPEAT_AFTER_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackScheduleTest {
    private val t0 = 1_760_000_000_000L
    private val day = MIN_GAP_MS

    @Test
    fun notDueBeforeAnyScan() {
        assertFalse(FeedbackSchedule().due(t0))
    }

    @Test
    fun dueAfterTheFirstScan() {
        assertTrue(FeedbackSchedule().withCompletedScan().due(t0))
    }

    @Test
    fun dueAgainAfterTheSecondScanOnALaterDay() {
        val afterFirst = FeedbackSchedule().withCompletedScan().withShown(t0)
        assertFalse("no new scan yet", afterFirst.due(t0 + 2 * day))
        val second = afterFirst.withCompletedScan()
        assertTrue(second.due(t0 + day))
    }

    @Test
    fun neverTwiceInOneDay() {
        val second = FeedbackSchedule().withCompletedScan().withShown(t0).withCompletedScan()
        assertFalse(second.due(t0 + day - 1))
        assertTrue("the next scan on another day asks", second.due(t0 + day))
    }

    @Test
    fun afterTwoPromptsOnlyEveryThirtyDays() {
        val shownTwice = FeedbackSchedule().withCompletedScan().withShown(t0).withCompletedScan().withShown(t0 + day)
        val later = shownTwice.withCompletedScan()
        assertFalse(later.due(t0 + day + 2 * day))
        assertFalse(later.due(t0 + day + REPEAT_AFTER_MS - 1))
        assertTrue(later.due(t0 + day + REPEAT_AFTER_MS))
        val third = later.withShown(t0 + day + REPEAT_AFTER_MS)
        assertFalse(third.withCompletedScan().due(t0 + day + REPEAT_AFTER_MS + 5 * day))
    }

    @Test
    fun aClockSetBackwardsDoesNotAskAgain() {
        val second = FeedbackSchedule().withCompletedScan().withShown(t0).withCompletedScan()
        assertFalse(second.due(t0 - 10 * day))
    }

    @Test
    fun scansCountedBeforeThePromptsStillGetBothEarlyPrompts() {
        // Three text scans finished before any "Scan saved" sheet: the next two chances still ask, a day apart.
        val many = FeedbackSchedule(completedScans = 3)
        assertTrue(many.due(t0))
        assertTrue(many.withShown(t0).due(t0 + day))
        assertFalse(many.withShown(t0).withShown(t0 + day).due(t0 + 3 * day))
    }
}
