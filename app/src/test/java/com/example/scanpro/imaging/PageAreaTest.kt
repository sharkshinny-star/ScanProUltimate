package com.example.scanpro.imaging

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageAreaTest {
    private val quad = CropQuad(
        NormalizedPoint(0.20f, 0.10f), NormalizedPoint(0.80f, 0.12f),
        NormalizedPoint(0.78f, 0.90f), NormalizedPoint(0.22f, 0.88f)
    )

    @Test
    fun `upright and stored coordinates are inverse for every turn`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val (x, y) = PageArea.toRaw(0.3f, 0.7f, rotation)
            val (u, v) = PageArea.toUpright(x, y, rotation)
            assertEquals("rotation $rotation", 0.3f, u, 1e-6f)
            assertEquals("rotation $rotation", 0.7f, v, 1e-6f)
        }
    }

    @Test
    fun `a photo stored sideways reads the page from the right place`() {
        // Stored 8160 wide, 6120 high, turned 90 degrees to stand up: upright y (0.10..0.90) is stored x, and upright
        // x (0.20..0.80) is stored y, reversed. Each side has the 1% margin.
        val box = PageArea.rawBox(quad, 90, 8160, 6120)
        fun near(expected: Float, actual: Int) = assertTrue("$expected vs $actual", kotlin.math.abs(expected - actual) <= 1.5f)
        near(0.09f * 8160, box.left)
        near(0.91f * 8160, box.right)
        near(0.19f * 6120, box.top)
        near(0.81f * 6120, box.bottom)
    }

    @Test
    fun `the crop moved into the page area keeps its corners on the same pixels`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val (w, h) = if (rotation % 180 == 0) 8160 to 6120 else 6120 to 8160
            val box = PageArea.rawBox(quad, rotation, w, h)
            val inner = PageArea.cropWithin(quad, box, rotation, w, h)
            // The area's upright extent, then the inner corner mapped back out, must land on the original corner.
            val a = PageArea.toUpright(box.left.toFloat() / w, box.top.toFloat() / h, rotation)
            val b = PageArea.toUpright(box.right.toFloat() / w, box.bottom.toFloat() / h, rotation)
            val u0 = minOf(a.first, b.first); val u1 = maxOf(a.first, b.first)
            val v0 = minOf(a.second, b.second); val v1 = maxOf(a.second, b.second)
            val back = u0 + inner.bottomRight.x * (u1 - u0) to v0 + inner.bottomRight.y * (v1 - v0)
            assertEquals("rotation $rotation", quad.bottomRight.x, back.first, 1e-4f)
            assertEquals("rotation $rotation", quad.bottomRight.y, back.second, 1e-4f)
        }
    }
}
