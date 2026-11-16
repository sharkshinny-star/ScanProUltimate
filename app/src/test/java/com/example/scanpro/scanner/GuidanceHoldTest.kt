package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceHoldTest {
    @Test
    fun aVerdictShowsOnlyAfterItHasHeldForTheHoldTime() {
        val hold = GuidanceHold(400)
        assertNull(hold.update(1, 0))
        assertNull(hold.update(1, 399))
        assertEquals(1, hold.update(1, 400))
        assertNull(hold.update(1, 800))
    }

    @Test
    fun aFlickeringVerdictNeverReplacesTheShownOne() {
        val hold = GuidanceHold(400)
        hold.update(1, 0)
        assertEquals(1, hold.update(1, 400))
        // 2, 3, 2, 3 every 100 ms: none of them holds for 400 ms.
        var t = 500L
        repeat(8) { i ->
            assertNull(hold.update(if (i % 2 == 0) 2 else 3, t))
            t += 100
        }
    }

    @Test
    fun aNewVerdictThatSticksReplacesTheOldOne() {
        val hold = GuidanceHold(400)
        hold.update(1, 0)
        hold.update(1, 400)
        assertNull(hold.update(2, 500))
        assertNull(hold.update(2, 899))
        assertEquals(2, hold.update(2, 900))
    }
}
