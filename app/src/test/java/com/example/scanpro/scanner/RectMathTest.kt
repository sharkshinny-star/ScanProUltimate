package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RectMathTest {
    // A 640 x 480 buffer with a box in its top-left corner.
    private val box = IntBox(0, 0, 100, 50)

    @Test
    fun `no rotation leaves the box alone`() {
        assertEquals(box, rotateBox(box, 640, 480, 0))
    }

    @Test
    fun `quarter turn clockwise moves the top-left box to the top-right`() {
        // The 640x480 image becomes 480x640; its old top-left corner is now the top-right corner.
        assertEquals(IntBox(430, 0, 480, 100), rotateBox(box, 640, 480, 90))
    }

    @Test
    fun `half turn moves it to the bottom-right`() {
        assertEquals(IntBox(540, 430, 640, 480), rotateBox(box, 640, 480, 180))
    }

    @Test
    fun `three quarter turn moves it to the bottom-left`() {
        assertEquals(IntBox(0, 540, 50, 640), rotateBox(box, 640, 480, 270))
    }

    @Test
    fun `four quarter turns return to the start`() {
        var current = box
        var w = 640
        var h = 480
        repeat(4) {
            current = rotateBox(current, w, h, 90)
            val t = w; w = h; h = t
        }
        assertEquals(box, current)
    }
}
