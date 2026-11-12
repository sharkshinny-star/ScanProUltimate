package com.example.scanpro.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IdCardCropTest {
    @Test
    fun `the guide maps onto the fitted photo`() {
        // A 3:4 portrait photo fills the width of a 1080 x 2000 camera area exactly (1080 x 1440, 280 px above and below).
        val crop = idCardCrop(1080f, 2000f, 3000, 4000, density = 1f)
        assertNotNull(crop); crop!!
        val frame = idCardFrame(1080f, 2000f, 1f)
        assertEquals(frame.left / 1080f, crop.topLeft.x, 1e-4f)
        assertEquals(frame.right / 1080f, crop.topRight.x, 1e-4f)
        assertEquals((frame.top - 280f) / 1440f, crop.topLeft.y, 1e-4f)
        assertEquals((frame.bottom - 280f) / 1440f, crop.bottomLeft.y, 1e-4f)
    }

    @Test
    fun `no crop without a measured view`() {
        assertEquals(null, idCardCrop(0f, 0f, 3000, 4000, 1f))
    }

    @Test
    fun `a passport guide is the ID-3 shape and the crop follows it`() {
        val frame = idCardFrame(1080f, 2000f, 1f, IdCardKind.PASSPORT.heightRatio)
        assertEquals(88f / 125f, frame.height() / frame.width(), 1e-4f)
        val crop = idCardCrop(1080f, 2000f, 3000, 4000, density = 1f, heightRatio = IdCardKind.PASSPORT.heightRatio)
        assertNotNull(crop); crop!!
        assertEquals((frame.top - 280f) / 1440f, crop.topLeft.y, 1e-4f)
        assertEquals((frame.bottom - 280f) / 1440f, crop.bottomLeft.y, 1e-4f)
    }

    @Test
    fun `cards keep the old guide and only both sides takes two shots`() {
        assertEquals(0.63f, IdCardKind.ONE_SIDE.heightRatio, 0f)
        assertEquals(0.63f, IdCardKind.BOTH_SIDES.heightRatio, 0f)
        val default = idCardFrame(1080f, 2000f, 1f)
        val card = idCardFrame(1080f, 2000f, 1f, IdCardKind.BOTH_SIDES.heightRatio)
        assertEquals(default, card)
        assertEquals(listOf("ONE_SIDE", "BOTH_SIDES", "PASSPORT"), IdCardKind.entries.map { it.name })
    }
}
