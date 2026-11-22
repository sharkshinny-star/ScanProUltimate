package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineTrackerTest {
    private fun box(left: Float, top: Float, right: Float, bottom: Float) = CropQuad(
        NormalizedPoint(left, top), NormalizedPoint(right, top), NormalizedPoint(right, bottom), NormalizedPoint(left, bottom)
    )

    private val page = box(0.2f, 0.1f, 0.8f, 0.9f)
    private val desk = box(0.02f, 0.02f, 0.98f, 0.6f)

    @Test
    fun `the first outline is shown at once`() {
        assertEquals(page, OutlineTracker().update(page, 0))
    }

    @Test
    fun `a frame or two without a page keeps the outline, a long gap drops it`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        assertEquals(page, tracker.update(null, 66))
        assertEquals(page, tracker.update(null, 133))
        assertEquals(page, tracker.update(null, OutlineTracker.HOLD_MS))
        assertNull(tracker.update(null, OutlineTracker.HOLD_MS + 1))
    }

    @Test
    fun `one stray outline far away is ignored`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        assertEquals(page, tracker.update(desk, 33))
        // The page is back the next frame: nothing moved.
        assertEquals(page, tracker.update(page, 66))
    }

    @Test
    fun `a far outline the detector repeats replaces the one shown in one step`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        var shown: CropQuad? = null
        repeat(OutlineTracker.JUMP_FRAMES) { frame -> shown = tracker.update(desk, 33L * (frame + 1)) }
        assertEquals(desk, shown)
    }

    @Test
    fun `stray frames that do not agree with each other never replace the outline`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        val other = box(0.5f, 0.5f, 0.98f, 0.98f)
        var now = 0L
        repeat(3) {
            now += 33; assertEquals(page, tracker.update(desk, now))
            now += 33; assertEquals(page, tracker.update(other, now))
            // The page itself is seen in between, so the outline is not stale.
            now += 33; assertEquals(page, tracker.update(page, now))
        }
    }

    @Test
    fun `one side jumping is a jump although the mean corner movement is small`() {
        // A card's face without its header band, then the whole card: only the top two corners move.
        val face = box(0.1f, 0.4f, 0.9f, 0.8f)
        val card = box(0.1f, 0.3f, 0.9f, 0.8f)
        assertTrue(OutlineTracker.cornerDistance(face, card) < OutlineTracker.JUMP)
        val tracker = OutlineTracker()
        tracker.update(face, 0)
        assertEquals(face, tracker.update(card, 33))
        assertEquals(face, tracker.update(face, 66))
    }

    @Test
    fun `a far outline is taken once the one shown has had no support for the hold time`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        assertEquals(desk, tracker.update(desk, OutlineTracker.HOLD_MS + 1))
    }

    @Test
    fun `trembling barely moves the outline and a real move is followed quickly`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        val trembling = box(0.202f, 0.102f, 0.802f, 0.902f)
        val afterTremble = tracker.update(trembling, 33)
        assertNotNull(afterTremble)
        assertTrue("moved ${OutlineTracker.cornerDistance(page, afterTremble!!)}", OutlineTracker.cornerDistance(page, afterTremble) < 0.0006f)

        val moved = box(0.24f, 0.13f, 0.84f, 0.93f)
        val fresh = OutlineTracker()
        fresh.update(page, 0)
        val afterMove = fresh.update(moved, 33)!!
        val whole = OutlineTracker.cornerDistance(page, moved)
        assertTrue(OutlineTracker.cornerDistance(page, afterMove) > whole * 0.75f)
    }

    @Test
    fun `the follow share grows with the distance and is capped`() {
        assertEquals(0.12f, OutlineTracker.followShare(0.001f), 1e-6f)
        assertTrue(OutlineTracker.followShare(0.01f) > OutlineTracker.followShare(0.006f))
        assertEquals(0.8f, OutlineTracker.followShare(0.06f), 1e-6f)
    }

    @Test
    fun `after a reset the next outline is shown at once`() {
        val tracker = OutlineTracker()
        tracker.update(page, 0)
        tracker.reset()
        assertEquals(desk, tracker.update(desk, 33))
    }

    // ID card mode: the card's own outline inside the guide.
    private val guide = box(0.06f, 0.3f, 0.94f, 0.7f)

    @Test
    fun `a card lined up in the guide is cropped to its own outline`() {
        val card = CropQuad(NormalizedPoint(0.1f, 0.32f), NormalizedPoint(0.9f, 0.33f), NormalizedPoint(0.89f, 0.68f), NormalizedPoint(0.11f, 0.67f))
        assertTrue(IdCardOutline.fits(guide, card))
        assertEquals(card, IdCardOutline.choose(guide, card))
    }

    @Test
    fun `an outline slightly over the guide still counts, one well outside does not`() {
        assertTrue(IdCardOutline.fits(guide, box(0.03f, 0.29f, 0.96f, 0.71f)))
        assertFalse(IdCardOutline.fits(guide, box(0.06f, 0.1f, 0.94f, 0.7f)))
        assertEquals(guide, IdCardOutline.choose(guide, box(0.06f, 0.1f, 0.94f, 0.7f)))
    }

    @Test
    fun `a small outline inside the guide is the portrait, not the card`() {
        assertFalse(IdCardOutline.fits(guide, box(0.1f, 0.35f, 0.35f, 0.65f)))
    }

    @Test
    fun `no outline or the whole photo keeps the guide`() {
        assertEquals(guide, IdCardOutline.choose(guide, null))
        assertEquals(guide, IdCardOutline.choose(guide, CropQuad.DEFAULT))
    }
}
