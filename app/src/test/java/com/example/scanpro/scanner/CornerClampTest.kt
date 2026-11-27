package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class CornerClampTest {
    private fun p(x: Double, y: Double) = x to y

    @Test fun cornersInsideTheFrameAreUntouched() {
        val corners = listOf(p(10.0, 10.0), p(90.0, 12.0), p(88.0, 70.0), p(8.0, 68.0))
        assertEquals(corners, CornerClamp.intoFrame(corners, 100.0, 80.0))
    }

    @Test fun aCornerPastTheEdgeSlidesAlongItsSideNotAlongTheBorder() {
        // The page's top side runs from (-300, 50) to (300, 10): at x = 0 it is at y = 30, not at the clamped y = 50.
        val corners = listOf(p(-300.0, 50.0), p(300.0, 10.0), p(300.0, 400.0), p(-300.0, 410.0))
        val fitted = CornerClamp.intoFrame(corners, 400.0, 500.0)
        assertEquals(0.0, fitted[0].first, 1e-9)
        assertEquals(30.0, fitted[0].second, 1e-9)
        // The corner that was already inside stays where it was.
        assertEquals(p(300.0, 10.0), fitted[1])
    }

    @Test fun aSmallOvershootMovesTheCornerOnlyALittle() {
        val corners = listOf(p(-3.0, 20.0), p(200.0, 18.0), p(202.0, 300.0), p(-2.0, 302.0))
        val fitted = CornerClamp.intoFrame(corners, 210.0, 320.0)
        assertEquals(0.0, fitted[0].first, 1e-9)
        assertEquals(19.97, fitted[0].second, 0.01)
    }

    @Test fun everyResultIsInsideTheFrameEvenWhenNoSideEntersIt() {
        // A page wider than the frame on both sides: the left corners have no side that enters the frame towards each
        // other (both run outside), so they fall back to the border.
        val corners = listOf(p(-50.0, -20.0), p(150.0, -10.0), p(150.0, 90.0), p(-50.0, 80.0))
        val fitted = CornerClamp.intoFrame(corners, 100.0, 70.0)
        fitted.forEach { (x, y) ->
            assertEquals(true, x in 0.0..100.0)
            assertEquals(true, y in 0.0..70.0)
        }
    }
}
