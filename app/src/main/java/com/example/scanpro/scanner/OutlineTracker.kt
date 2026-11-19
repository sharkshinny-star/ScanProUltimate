package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import kotlin.math.hypot

/**
 * The page outline as it is drawn over the camera preview, made from the detector's outline of each frame
 * (2026-11-19). The detector judges every frame on its own, so its outline flickers: one frame finds no page, the
 * next finds a neighbouring rectangle, the next finds the page again a pixel or two away. Drawn as found (with one
 * blend towards each new outline, as before), the outline blinked off, jumped and trembled.
 *
 * Three rules, in corner distances normalised to the frame (0..1):
 * - **Hold**: a frame with no outline keeps the last one for [HOLD_MS]; only a page gone for longer is dropped.
 * - **Jump gate**: an outline with a corner more than [JUMP] from the one shown replaces it only after [JUMP_FRAMES] frames in
 *   a row agree with each other; a single stray frame is ignored. A real change (another page, the phone moved) is
 *   then taken in one step, not glided across the screen.
 * - **Follow**: a nearby outline is followed by a share that grows with the distance: trembling of under
 *   [STILL] barely moves the drawn outline, while a real movement is followed almost at once.
 *
 * Plain values in and out, so it is tested on the JVM. One instance per camera screen, used from one thread.
 */
internal class OutlineTracker {
    private var shown: CropQuad? = null
    private var lastSeenAt = 0L
    private var pending: CropQuad? = null
    private var pendingFrames = 0

    /** The outline to draw now, given the detector's outline for this frame (null when it found none). */
    fun update(detected: CropQuad?, now: Long): CropQuad? {
        val current = shown
        if (detected == null) {
            pending = null
            pendingFrames = 0
            if (current != null && now - lastSeenAt > HOLD_MS) shown = null
            return shown
        }
        if (current == null) {
            lastSeenAt = now
            pending = null
            pendingFrames = 0
            shown = detected
            return detected
        }
        // The farthest corner decides whether this is the same outline: when only one side jumps (a card's face with
        // and without its header band, one page of a book against both), the mean of four corners stays small.
        if (farthestCorner(current, detected) <= JUMP) {
            lastSeenAt = now
            pending = null
            pendingFrames = 0
            shown = current.movedTowards(detected, followShare(cornerDistance(current, detected)))
            return shown
        }
        // Far from the outline shown: wait for the detector to repeat itself before believing it.
        val candidate = pending
        if (candidate != null && farthestCorner(candidate, detected) <= JUMP) pendingFrames++ else pendingFrames = 1
        pending = detected
        if (pendingFrames >= JUMP_FRAMES || now - lastSeenAt > HOLD_MS) {
            lastSeenAt = now
            pending = null
            pendingFrames = 0
            shown = detected
        }
        return shown
    }

    /** Forgets the page: the next outline found is shown at once (camera reopened, mode changed, page captured). */
    fun reset() {
        shown = null
        pending = null
        pendingFrames = 0
    }

    companion object {
        /** A page lost for longer than this is no longer drawn; a blurred or blocked frame or two is bridged. */
        const val HOLD_MS = 400L
        /** A corner moving farther than this in one frame makes a new outline another object, not the same page moving. */
        const val JUMP = 0.07f
        /** Frames in a row that must agree before a far outline replaces the one shown. */
        const val JUMP_FRAMES = 3
        /** Corner trembling below this is detector noise on a page lying still. */
        const val STILL = 0.004f

        /** Share of the way to move towards an outline [distance] away: 0.12 for trembling, up to 0.8 for a real move. */
        fun followShare(distance: Float): Float = when {
            distance <= STILL -> 0.12f
            else -> (0.12f + (distance - STILL) / 0.03f * 0.68f).coerceAtMost(0.8f)
        }

        /** The largest distance between matching corners of two outlines. */
        fun farthestCorner(a: CropQuad, b: CropQuad): Float {
            fun d(p: NormalizedPoint, q: NormalizedPoint) = hypot(p.x - q.x, p.y - q.y)
            return maxOf(d(a.topLeft, b.topLeft), d(a.topRight, b.topRight), d(a.bottomRight, b.bottomRight), d(a.bottomLeft, b.bottomLeft))
        }

        /** Mean distance between matching corners of two outlines. */
        fun cornerDistance(a: CropQuad, b: CropQuad): Float {
            fun d(p: NormalizedPoint, q: NormalizedPoint) = hypot(p.x - q.x, p.y - q.y)
            return (d(a.topLeft, b.topLeft) + d(a.topRight, b.topRight) + d(a.bottomRight, b.bottomRight) + d(a.bottomLeft, b.bottomLeft)) / 4f
        }
    }
}

/** This outline moved [fraction] of the way towards [target]; the bends are taken from the target. */
internal fun CropQuad.movedTowards(target: CropQuad, fraction: Float): CropQuad {
    fun step(a: NormalizedPoint, b: NormalizedPoint) = NormalizedPoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
    return target.copy(
        topLeft = step(topLeft, target.topLeft),
        topRight = step(topRight, target.topRight),
        bottomRight = step(bottomRight, target.bottomRight),
        bottomLeft = step(bottomLeft, target.bottomLeft)
    )
}

/**
 * ID card mode (2026-11-19): the card's own outline inside the guide. The mode used to keep everything inside the
 * guide, card or not, so a card held a little small or askew was saved with a border of desk round it, and no outline
 * showed that the card had been seen. A detected outline is trusted only when it [fits] the guide; otherwise the guide
 * is used, as before.
 */
internal object IdCardOutline {
    /** How far outside the guide a corner may lie, as a share of the guide's width and height. */
    const val MARGIN = 0.06f
    /** The least share of the guide the card must cover: smaller is the portrait or a patch of print, not the card. */
    const val MIN_SHARE = 0.4f

    /**
     * True when [detected] can be the card lined up in [guide] (both in the same normalised coordinates): every corner
     * inside the guide or within [MARGIN] of it, and covering at least [MIN_SHARE] of it.
     */
    fun fits(guide: CropQuad, detected: CropQuad?): Boolean {
        if (detected == null || detected.isIdentity()) return false
        val width = guide.topRight.x - guide.topLeft.x
        val height = guide.bottomLeft.y - guide.topLeft.y
        if (width <= 0f || height <= 0f) return false
        val left = guide.topLeft.x - width * MARGIN
        val right = guide.topRight.x + width * MARGIN
        val top = guide.topLeft.y - height * MARGIN
        val bottom = guide.bottomLeft.y + height * MARGIN
        val corners = listOf(detected.topLeft, detected.topRight, detected.bottomRight, detected.bottomLeft)
        if (corners.any { it.x < left || it.x > right || it.y < top || it.y > bottom }) return false
        return QuadGeometry.area(detected) >= width * height * MIN_SHARE
    }

    /** The crop to keep: the card's own outline when it [fits] the guide, else the guide. */
    fun choose(guide: CropQuad, detected: CropQuad?): CropQuad = if (fits(guide, detected)) detected!!.straightened() else guide
}
