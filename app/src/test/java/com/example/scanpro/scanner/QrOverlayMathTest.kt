package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrOverlayMathTest {
    private val delta = 0.001f

    @Test
    fun aWiderViewLeavesBarsLeftAndRight() {
        // A 400x300 image in a 1000x600 view: scale 2, 100px bars at each side.
        val box = mapToFitCenter(IntBox(100, 50, 200, 150), 400, 300, 1000f, 600f)!!
        assertEquals(300f, box.left, delta)
        assertEquals(100f, box.top, delta)
        assertEquals(500f, box.right, delta)
        assertEquals(300f, box.bottom, delta)
    }

    @Test
    fun aTallerViewLeavesBarsAboveAndBelow() {
        // A 300x400 image (a portrait phone frame) in a 600x1400 view: scale 2, 300px bars top and bottom.
        val box = mapToFitCenter(IntBox(0, 0, 300, 400), 300, 400, 600f, 1400f)!!
        assertEquals(0f, box.left, delta)
        assertEquals(300f, box.top, delta)
        assertEquals(600f, box.right, delta)
        assertEquals(1100f, box.bottom, delta)
    }

    @Test
    fun theWholeImageFillsTheFittedArea() {
        val box = mapToFitCenter(IntBox(0, 0, 480, 640), 480, 640, 1080f, 1920f)!!
        assertEquals(0f, box.left, delta)
        assertEquals(1080f, box.right, delta)
        assertEquals(1080f * 640 / 480, box.height, delta)
        assertEquals((1920f - box.height) / 2f, box.top, delta)
    }

    @Test
    fun emptySizesGiveNothing() {
        assertNull(mapToFitCenter(IntBox(0, 0, 10, 10), 0, 100, 100f, 100f))
        assertNull(mapToFitCenter(IntBox(0, 0, 10, 10), 100, 100, 0f, 100f))
    }

    @Test
    fun smallBoxesGrowToTheTouchSizeAroundTheirCentre() {
        val grown = withMinimumTouchSize(ViewBox(100f, 100f, 120f, 110f), 48f, 1000f, 1000f)
        assertEquals(48f, grown.width, delta)
        assertEquals(48f, grown.height, delta)
        assertEquals(110f, (grown.left + grown.right) / 2f, delta)
        assertEquals(105f, (grown.top + grown.bottom) / 2f, delta)
    }

    @Test
    fun bigBoxesStayAsTheyAre() {
        val box = ViewBox(100f, 200f, 400f, 500f)
        assertEquals(box, withMinimumTouchSize(box, 48f, 1000f, 1000f))
    }

    @Test
    fun growingNeverLeavesTheView() {
        val corner = withMinimumTouchSize(ViewBox(0f, 0f, 10f, 10f), 48f, 300f, 300f)
        assertEquals(0f, corner.left, delta)
        assertEquals(0f, corner.top, delta)
        assertEquals(48f, corner.right, delta)
        val far = withMinimumTouchSize(ViewBox(295f, 295f, 300f, 300f), 48f, 300f, 300f)
        assertEquals(300f, far.right, delta)
        assertEquals(300f, far.bottom, delta)
        assertTrue(far.width >= 48f - delta)
    }
}
