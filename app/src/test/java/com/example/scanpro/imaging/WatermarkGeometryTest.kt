package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkGeometryTest {
    private val w = 600f
    private val h = 800f

    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun upright_page_flips_the_y_axis() {
        // The top-left corner as seen is the top-left of the stored page: x 0, y at the top (= height).
        val (x, y) = WatermarkGeometry.toPage(0f, 0f, w, h, 0)
        assertNear(0f, x); assertNear(h, y)
    }

    @Test
    fun page_turned_90_maps_the_seen_corners_back_to_the_stored_ones() {
        // Stored bottom-left (0,0) is shown at the top-left of a page turned a quarter clockwise.
        val topLeft = WatermarkGeometry.toPage(0f, 0f, w, h, 90)
        assertNear(0f, topLeft.first); assertNear(0f, topLeft.second)
        // Seen top-right is the stored top-left (0, h).
        val seenW = h
        val topRight = WatermarkGeometry.toPage(seenW, 0f, w, h, 90)
        assertNear(0f, topRight.first); assertNear(h, topRight.second)
    }

    @Test
    fun page_turned_180_and_270_map_the_seen_origin_to_the_opposite_stored_corners() {
        val r180 = WatermarkGeometry.toPage(0f, 0f, w, h, 180)
        assertNear(w, r180.first); assertNear(0f, r180.second)
        val r270 = WatermarkGeometry.toPage(0f, 0f, w, h, 270)
        assertNear(w, r270.first); assertNear(h, r270.second)
    }

    @Test
    fun seen_size_swaps_for_quarter_turns_only() {
        assertEquals(w to h, WatermarkGeometry.seenSize(w, h, 0))
        assertEquals(h to w, WatermarkGeometry.seenSize(w, h, 90))
        assertEquals(w to h, WatermarkGeometry.seenSize(w, h, 180))
        assertEquals(h to w, WatermarkGeometry.seenSize(w, h, 270))
    }

    @Test
    fun a_single_stamp_sits_at_the_chosen_spot_and_turns_with_the_page() {
        val config = WatermarkConfig(text = "X", angleDegrees = 0f, centerX = 0.5f, centerY = 0.5f)
        val upright = WatermarkGeometry.stamps(config, 100f, 40f, w, h, 0).single()
        assertNear(w / 2f, upright.x); assertNear(h / 2f, upright.y); assertNear(0f, upright.angleRad.toFloat())
        // On a page stored turned a quarter, the text must be turned the same quarter in stored space to read upright.
        val turned = WatermarkGeometry.stamps(config, 100f, 40f, w, h, 90).single()
        assertEquals(Math.PI / 2, turned.angleRad, 1e-6)
    }

    @Test
    fun the_chosen_spot_is_a_fraction_of_the_page_as_seen() {
        val config = WatermarkConfig(text = "X", angleDegrees = 0f, centerX = 0.25f, centerY = 0.1f)
        val stamp = WatermarkGeometry.stamps(config, 10f, 10f, w, h, 0).single()
        assertNear(w * 0.25f, stamp.x)
        assertNear(h - h * 0.1f, stamp.y)
    }

    @Test
    fun tiling_covers_the_page_and_a_single_stamp_does_not() {
        val one = WatermarkGeometry.stamps(WatermarkConfig(text = "DRAFT"), 120f, 40f, w, h, 0)
        val many = WatermarkGeometry.stamps(WatermarkConfig(text = "DRAFT", tiled = true), 120f, 40f, w, h, 0)
        assertEquals(1, one.size)
        assertTrue("expected many tiles, got ${many.size}", many.size > 8)
    }
}
