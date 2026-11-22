package com.example.scanpro.imaging

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * A photo with a mirrored EXIF orientation is rewritten upright with the mirroring applied (it used to come out
 * mirrored, text backwards). The upright picture here is 40 x 20: its left half red, its right half blue.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class MirroredOrientationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun halves(w: Int, h: Int, firstHalfRed: Boolean, splitAcross: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val first = if (firstHalfRed) Color.RED else Color.BLUE
        val second = if (firstHalfRed) Color.BLUE else Color.RED
        paint.color = first
        if (splitAcross) canvas.drawRect(0f, 0f, w / 2f, h.toFloat(), paint) else canvas.drawRect(0f, 0f, w.toFloat(), h / 2f, paint)
        paint.color = second
        if (splitAcross) canvas.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), paint) else canvas.drawRect(0f, h / 2f, w.toFloat(), h.toFloat(), paint)
        return bitmap
    }

    private fun stored(bitmap: Bitmap, orientation: Int): File {
        val file = folder.newFile("photo.jpg")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun isReddish(pixel: Int) = Color.red(pixel) > 150 && Color.blue(pixel) < 100
    private fun isBluish(pixel: Int) = Color.blue(pixel) > 150 && Color.red(pixel) < 100

    @Test fun flippedHorizontallyIsMirroredBack() {
        // Stored mirrored left to right: blue on the left, red on the right. Orientation 2 says "mirror to see it".
        val file = stored(halves(40, 20, firstHalfRed = false, splitAcross = true), ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertTrue(ImageProcessor.bakeMirroredOrientation(file))
        val result = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(40, result.width)
        assertEquals(20, result.height)
        assertTrue(isReddish(result.getPixel(5, 10)))
        assertTrue(isBluish(result.getPixel(35, 10)))
        assertFalse(ExifInterface(file.absolutePath).isFlipped)
    }

    @Test fun transposedIsTurnedAndMirroredBack() {
        // Orientation 5 (transpose): stored(x, y) is the upright picture at (y, x), so the upright picture's left half
        // is the stored picture's top half: red on top, blue at the bottom, 20 wide and 40 high.
        val file = stored(halves(20, 40, firstHalfRed = true, splitAcross = false), ExifInterface.ORIENTATION_TRANSPOSE)
        assertTrue(ImageProcessor.bakeMirroredOrientation(file))
        val result = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(40, result.width)
        assertEquals(20, result.height)
        assertTrue(isReddish(result.getPixel(5, 10)))
        assertTrue(isBluish(result.getPixel(35, 10)))
    }

    @Test fun anUnmirroredPhotoIsLeftAlone() {
        val file = stored(halves(40, 20, firstHalfRed = true, splitAcross = true), ExifInterface.ORIENTATION_ROTATE_90)
        val before = file.readBytes()
        assertFalse(ImageProcessor.bakeMirroredOrientation(file))
        assertTrue(before.contentEquals(file.readBytes()))
    }
}
