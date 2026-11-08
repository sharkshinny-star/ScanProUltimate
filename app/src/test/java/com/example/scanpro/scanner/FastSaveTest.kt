package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.ReviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSaveTest {
    private val found = CropQuad(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f))

    @Test
    fun `a sharp page with an outline needs no look`() {
        assertFalse(FastSave.needsReview(found, emptySet()))
    }

    @Test
    fun `no outline or a quality issue needs a look`() {
        assertTrue("whole-frame crop", FastSave.needsReview(CropQuad.DEFAULT, emptySet()))
        assertTrue("blurred", FastSave.needsReview(found, setOf(PageIssue.BLURRY)))
    }

    @Test
    fun `only the pages that need it, in order`() {
        val needing = FastSave.pagesNeedingReview(
            listOf(found, CropQuad.DEFAULT, found, found),
            listOf(emptySet(), emptySet(), setOf(PageIssue.DARK), emptySet())
        )
        assertEquals(listOf(1, 2), needing)
    }

    @Test
    fun `a composed ID page is trusted although it fills its frame`() {
        val needing = FastSave.pagesNeedingReview(listOf(CropQuad.DEFAULT, CropQuad.DEFAULT), listOf(emptySet(), emptySet()), trusted = setOf(0))
        assertEquals("only the untrusted whole-frame page", listOf(1), needing)
    }

    @Test
    fun `Always, a retake and coming back from the review skip the check`() {
        assertTrue(FastSave.reviewWithoutChecking(ReviewMode.ALWAYS, retaking = false, returnToReview = false))
        assertTrue(FastSave.reviewWithoutChecking(ReviewMode.NEVER, retaking = true, returnToReview = false))
        assertTrue(FastSave.reviewWithoutChecking(ReviewMode.WHEN_NEEDED, retaking = false, returnToReview = true))
        assertFalse(FastSave.reviewWithoutChecking(ReviewMode.WHEN_NEEDED, retaking = false, returnToReview = false))
        assertFalse(FastSave.reviewWithoutChecking(ReviewMode.NEVER, retaking = false, returnToReview = false))
    }

    @Test
    fun `the setting decides`() {
        assertTrue(FastSave.shouldReview(ReviewMode.ALWAYS, emptyList()))
        assertFalse(FastSave.shouldReview(ReviewMode.NEVER, listOf(0)))
        assertFalse(FastSave.shouldReview(ReviewMode.WHEN_NEEDED, emptyList()))
        assertTrue(FastSave.shouldReview(ReviewMode.WHEN_NEEDED, listOf(2)))
    }
}
