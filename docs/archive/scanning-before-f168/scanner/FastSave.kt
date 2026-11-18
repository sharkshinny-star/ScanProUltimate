package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.ReviewMode

/**
 * Fast save (R26): whether a scan can be saved from the camera's Done without the review screen, the way Genius Scan
 * saves with one tick and Google's scanner reviews only pages that need it.
 */
object FastSave {
    /**
     * A page needs a look when no outline was found in the photo (the crop is the whole frame, so the review's
     * handles are the only way to cut it out) or the quality check flagged it (blurred, glare, too dark).
     */
    fun needsReview(quad: CropQuad, issues: Set<PageIssue>): Boolean = quad.isIdentity() || issues.isNotEmpty()

    /**
     * Indices of the pages that need a look, in page order. [trusted] pages are skipped: a page the app composed
     * itself (both sides of an ID card, F67) fills its frame on purpose and is not a photo to judge.
     */
    fun pagesNeedingReview(quads: List<CropQuad>, issues: List<Set<PageIssue>>, trusted: Set<Int> = emptySet()): List<Int> =
        quads.indices.filter { it !in trusted && needsReview(quads[it], issues.getOrElse(it) { emptySet() }) }

    /** Whether the review opens: always, never, or only when [needing] is not empty. */
    fun shouldReview(mode: ReviewMode, needing: List<Int>): Boolean = when (mode) {
        ReviewMode.ALWAYS -> true
        ReviewMode.NEVER -> false
        ReviewMode.WHEN_NEEDED -> needing.isNotEmpty()
    }

    /**
     * The camera's Done, before any page is looked at: the review opens without a check when the setting says
     * Always, when a saved page is being shot again ([retaking]: the review is where it is confirmed), or when the
     * user left the review to add a page ([returnToReview]: they expect to come back to it).
     */
    fun reviewWithoutChecking(mode: ReviewMode, retaking: Boolean, returnToReview: Boolean): Boolean =
        mode == ReviewMode.ALWAYS || retaking || returnToReview
}
