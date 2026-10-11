package com.example.scanpro.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropQuadTest {
    @Test
    fun `default crop is identity`() {
        assertTrue(CropQuad.DEFAULT.isIdentity())
    }

    @Test
    fun `moved corner is not identity`() {
        assertFalse(CropQuad.DEFAULT.copy(topLeft = NormalizedPoint(0.1f, 0.1f)).isIdentity())
    }
}
