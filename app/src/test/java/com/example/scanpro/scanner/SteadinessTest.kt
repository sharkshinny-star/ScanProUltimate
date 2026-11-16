package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadinessTest {
    /** Feeds still frames [frameMs] apart and returns the time at which the page first counts as steady. */
    private fun timeToSteady(frameMs: Long): Long {
        val steadiness = Steadiness()
        var now = 1_000L
        while (!steadiness.onFrame(still = true, now = now)) now += frameMs
        return now - 1_000L
    }

    @Test
    fun `a fast phone holds as long as the hold time, not seven quick frames`() {
        // 30 frames a second: seven frames used to pass in 200 ms.
        val waited = timeToSteady(33)
        assertTrue("fired after $waited ms", waited >= Steadiness.HOLD_MS)
        assertTrue("fired after $waited ms", waited < Steadiness.HOLD_MS + 33)
    }

    @Test
    fun `a slow phone no longer waits seven slow frames`() {
        // 150 ms a frame: seven frames used to take 900 ms. Now the fourth still frame is enough.
        assertEquals(450L, timeToSteady(150))
    }

    @Test
    fun `one long gap between two frames is not a steady page`() {
        val steadiness = Steadiness()
        assertFalse(steadiness.onFrame(still = true, now = 0))
        assertFalse(steadiness.onFrame(still = true, now = 2_000))
    }

    @Test
    fun `movement starts the hold again`() {
        val steadiness = Steadiness()
        var now = 0L
        repeat(3) { steadiness.onFrame(still = true, now = now); now += 100 }
        assertFalse(steadiness.onFrame(still = false, now = now))
        assertEquals(0f, steadiness.progress(now))
        now += 100
        assertFalse(steadiness.onFrame(still = true, now = now))
        assertFalse(steadiness.onFrame(still = true, now = now + 340))
    }

    @Test
    fun `progress fills with the hold and stops at one`() {
        val steadiness = Steadiness()
        assertEquals(0f, steadiness.progress(0))
        var now = 0L
        var last = 0f
        repeat(12) {
            steadiness.onFrame(still = true, now = now)
            val progress = steadiness.progress(now)
            assertTrue(progress >= last)
            last = progress
            now += 50
        }
        assertEquals(1f, last)
        steadiness.reset()
        assertEquals(0f, steadiness.progress(now))
    }
}
