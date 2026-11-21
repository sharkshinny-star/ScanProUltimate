package com.example.scanpro.imaging

import android.app.Application
import com.example.scanpro.data.PageFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HD capture keeps the sensor's extra detail: a photo longer than 4500 px on its long side (a 48 or 50 MP capture)
 * is rendered at a higher limit than a standard 12 MP one, so the saved page is sharper instead of being scaled
 * down to the same 3400 px.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RenderLimitTest {
    @Test
    fun aStandardPhotoKeepsTheUsualLimits() {
        assertEquals(3400, ImageProcessor.renderLimit(4000, PageFilter.ORIGINAL))
        assertEquals(3400, ImageProcessor.renderLimit(4500, PageFilter.AUTO))
        assertEquals(2200, ImageProcessor.renderLimit(4000, PageFilter.BW))
    }

    @Test
    fun anHdPhotoKeepsMore() {
        assertEquals(4600, ImageProcessor.renderLimit(8000, PageFilter.ORIGINAL))
        assertEquals(3200, ImageProcessor.renderLimit(8000, PageFilter.BW))
        assertTrue(ImageProcessor.renderLimit(4501, PageFilter.COLOR) > ImageProcessor.renderLimit(4500, PageFilter.COLOR))
    }
}
