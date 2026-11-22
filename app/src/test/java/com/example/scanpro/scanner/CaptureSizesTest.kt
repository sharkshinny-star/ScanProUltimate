package com.example.scanpro.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSizesTest {
    @Test
    fun `a 12 MP camera like the CPH2569 gets no HD button`() {
        // The JPEG sizes that phone's main camera lists, largest first.
        val sizes = listOf(4096 to 3072, 4096 to 2304, 3840 to 2160, 3456 to 2592, 3280 to 2464, 1920 to 1080)
        assertFalse(CaptureSizes.offersHd(sizes))
    }

    @Test
    fun `a 50 MP camera that exposes its full size gets HD`() {
        assertTrue(CaptureSizes.offersHd(listOf(4080 to 3060, 8160 to 6120)))
    }

    @Test
    fun `a large photo of another shape does not count`() {
        // 16:9 at 24 MP: the scanner shoots 4:3, so this would never be used.
        assertFalse(CaptureSizes.offersHd(listOf(4096 to 3072, 6528 to 3672)))
    }

    @Test
    fun `no sizes known means no HD`() {
        assertFalse(CaptureSizes.offersHd(emptyList()))
    }
}
