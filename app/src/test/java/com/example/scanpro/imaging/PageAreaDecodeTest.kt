package com.example.scanpro.imaging

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page-area path for very large photos (HD captures), end to end on a real JPEG: a coloured mark in each corner of
 * the page must come out in the same corner of the page area, for a photo stored upright and one stored sideways.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PageAreaDecodeTest {
    @get:Rule val folder = TemporaryFolder()

    // The page, upright: 20%..80% across, 10%..90% down.
    private val crop = CropQuad(
        NormalizedPoint(0.2f, 0.1f), NormalizedPoint(0.8f, 0.1f), NormalizedPoint(0.8f, 0.9f), NormalizedPoint(0.2f, 0.9f)
    )

    /** An upright photo [w] x [h] with red, green, blue and yellow marks just inside the page's four corners. */
    private fun uprightPhoto(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawRect(0.2f * w, 0.1f * h, 0.8f * w, 0.9f * h, paint)
        val s = 0.06f * w
        fun mark(colour: Int, x: Float, y: Float) { paint.color = colour; canvas.drawRect(x, y, x + s, y + s, paint) }
        mark(Color.RED, 0.2f * w + 4, 0.1f * h + 4)
        mark(Color.GREEN, 0.8f * w - s - 4, 0.1f * h + 4)
        mark(Color.BLUE, 0.8f * w - s - 4, 0.9f * h - s - 4)
        mark(Color.YELLOW, 0.2f * w + 4, 0.9f * h - s - 4)
        return bitmap
    }

    /** Saves [upright] as a JPEG stored turned back by [rotation], with the EXIF tag that stands it up again. */
    private fun storedJpeg(upright: Bitmap, rotation: Int): java.io.File {
        val stored = if (rotation == 0) upright else {
            val m = android.graphics.Matrix().apply { postRotate(-rotation.toFloat()) }
            Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, m, true)
        }
        val file = folder.newFile("photo_$rotation.jpg")
        file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (rotation != 0) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    when (rotation) { 90 -> ExifInterface.ORIENTATION_ROTATE_90; 180 -> ExifInterface.ORIENTATION_ROTATE_180; else -> ExifInterface.ORIENTATION_ROTATE_270 }.toString()
                )
                saveAttributes()
            }
        }
        return file
    }

    private fun assertColourNear(expected: Int, actual: Int, where: String) {
        val d = maxOf(
            kotlin.math.abs(Color.red(expected) - Color.red(actual)),
            kotlin.math.abs(Color.green(expected) - Color.green(actual)),
            kotlin.math.abs(Color.blue(expected) - Color.blue(actual))
        )
        assertTrue("$where: expected ${Integer.toHexString(expected)}, got ${Integer.toHexString(actual)}", d < 60)
    }

    @Test
    fun `each corner of the page comes out where it was, however the photo is stored`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val file = storedJpeg(uprightPhoto(900, 1200), rotation)
            val result = ImageProcessor.decodePageArea(file.absolutePath, crop, rotation, maxDimension = 4600, wholePhotoLimit = 1000)
            assertNotNull(result)
            val (area, inner) = result!!
            assertEquals("rotation $rotation: area is upright and page-shaped", true, area.height > area.width)
            fun at(p: NormalizedPoint, dx: Float, dy: Float) = area.getPixel(
                ((p.x + dx) * area.width).toInt().coerceIn(0, area.width - 1),
                ((p.y + dy) * area.height).toInt().coerceIn(0, area.height - 1)
            )
            val step = 0.06f
            assertColourNear(Color.RED, at(inner.topLeft, step, step * 0.6f), "rotation $rotation top left")
            assertColourNear(Color.GREEN, at(inner.topRight, -step, step * 0.6f), "rotation $rotation top right")
            assertColourNear(Color.BLUE, at(inner.bottomRight, -step, -step * 0.6f), "rotation $rotation bottom right")
            assertColourNear(Color.YELLOW, at(inner.bottomLeft, step, -step * 0.6f), "rotation $rotation bottom left")
            area.recycle()
        }
    }

    @Test
    fun `the page area is not shrunk to the whole-photo limit`() {
        // A 2400 px photo with a render limit of 1000: the old path shrank the whole photo to 1000 px, leaving the page
        // 600 px wide; the page area keeps the page at the full 1000 px.
        val file = storedJpeg(uprightPhoto(2400, 1800), 0)
        val (area, _) = ImageProcessor.decodePageArea(file.absolutePath, crop, 0, maxDimension = 1000, wholePhotoLimit = 1000)!!
        assertEquals(1000, maxOf(area.width, area.height))
    }

    @Test
    fun `ordinary photos and uncropped pages keep the usual path`() {
        val file = storedJpeg(uprightPhoto(1200, 900), 0)
        assertNull(ImageProcessor.decodePageArea(file.absolutePath, crop, 0, maxDimension = 4600))
        assertNull(ImageProcessor.decodePageArea(file.absolutePath, CropQuad.DEFAULT, 0, maxDimension = 4600, wholePhotoLimit = 1000))
    }
}
