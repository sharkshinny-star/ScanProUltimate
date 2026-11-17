package com.example.scanpro.scanner

/**
 * Steadies the live hint pill. The detector's verdict can flip between "too dark", "hold steady" and "ready" from one
 * frame to the next; a new verdict is passed on only after it has held for [holdMs], so the text changes when the
 * situation does, not four times a second.
 */
class GuidanceHold(private val holdMs: Long = 400L) {
    private var shown = NONE
    private var pending = NONE
    private var pendingSince = 0L

    /** The verdict [value] seen at [nowMs]; returns the verdict to show when it changed, null to leave the pill as it is. */
    fun update(value: Int, nowMs: Long): Int? {
        if (value != pending) {
            pending = value
            pendingSince = nowMs
        }
        if (pending != shown && nowMs - pendingSince >= holdMs) {
            shown = pending
            return shown
        }
        return null
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}
