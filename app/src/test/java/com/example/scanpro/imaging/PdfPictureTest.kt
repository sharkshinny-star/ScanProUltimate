package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Images to PDF reads each picture once, upright and laid on white (F48). It used to save every picture as a JPEG
 * first: a second round of compression, and JPEG has no alpha channel, so the transparent parts of a PNG logo,
 * signature or screenshot came out black. The tool's PDF itself is not checked here: Robolectric has no native
 * android.graphics.pdf.PdfDocument ("document is closed!" on the first page).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfPictureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pdf-picture-test").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun transparentPixelsTurnWhiteAndOpaqueOnesKeepTheirColour() {
        val picture = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        picture.setPixel(0, 0, Color.TRANSPARENT)
        picture.setPixel(1, 0, Color.RED)
        picture.setPixel(2, 0, Color.argb(128, 0, 0, 0))

        val flat = PdfExporter.onWhite(picture)

        assertEquals(Color.WHITE, flat.getPixel(0, 0))
        assertEquals(Color.RED, flat.getPixel(1, 0))
        // Half-transparent black over white is mid-grey, fully opaque.
        val grey = flat.getPixel(2, 0)
        assertEquals(255, Color.alpha(grey))
        assertTrue("grey was ${Integer.toHexString(grey)}", Color.red(grey) in 125..130 && Color.red(grey) == Color.blue(grey))
        assertFalse(flat.hasAlpha())
    }

    @Test
    fun aPictureThatCannotBeDrawnOnIsCopiedAndTheOriginalReleased() {
        val picture = Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT, Color.BLUE), 2, 1, Bitmap.Config.ARGB_8888)
        assertFalse(picture.isMutable)

        val flat = PdfExporter.onWhite(picture)

        assertNotSame(picture, flat)
        assertTrue(picture.isRecycled)
        assertEquals(Color.WHITE, flat.getPixel(0, 0))
        assertEquals(Color.BLUE, flat.getPixel(1, 0))
        assertFalse(flat.hasAlpha())
    }

    @Test
    fun anOpaquePictureIsLeftAsItIs() {
        val picture = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            setHasAlpha(false)
        }
        assertSame(picture, PdfExporter.onWhite(picture))
        assertEquals(Color.GREEN, picture.getPixel(1, 1))
    }

    @Test
    fun aTransparentPngIsReadOnWhite() {
        val png = logoPng()

        val page = requireNotNull(PdfExporter.decodePicture(png.absolutePath, 400))

        assertEquals(Color.WHITE, page.getPixel(2, 2))
        assertEquals(Color.BLACK, page.getPixel(20, 20))
        assertFalse(page.hasAlpha())
    }

    @Test
    fun theOldJpegStepTurnedTheSamePngBlack() {
        // What the tool did before: decode, then save as JPEG for the PDF writer to read back.
        val jpeg = File(dir, "logo.jpg")
        val decoded = requireNotNull(ImageProcessor.decodeOrientedScaled(logoPng().absolutePath, 400))
        ImageProcessor.saveBitmapJpeg(decoded, jpeg, 95)
        val reread = requireNotNull(ImageProcessor.decodeScaled(jpeg.absolutePath, 400))

        val corner = reread.getPixel(2, 2)
        assertTrue("corner was ${Integer.toHexString(corner)}", Color.red(corner) < 16 && Color.green(corner) < 16 && Color.blue(corner) < 16)
    }

    @Test
    fun aTransparentPngCroppedAsAPageGetsAWhiteBackground() {
        // Gallery picks and Add page keep a PNG's bytes; the page crop used to save its transparent parts as black.
        val page = File(dir, "page.jpg")
        ImageProcessor.cropSourceToNewFile(logoPng(), page, com.example.scanpro.data.CropQuad.DEFAULT)
        val reread = requireNotNull(ImageProcessor.decodeScaled(page.absolutePath, 400))

        val corner = reread.getPixel(2, 2)
        assertTrue("corner was ${Integer.toHexString(corner)}", Color.red(corner) > 240 && Color.green(corner) > 240 && Color.blue(corner) > 240)
    }

    @Test
    fun aRotatedPhotoIsStillTurnedUpright() {
        // Upright used to be baked in by the JPEG step; now the decode itself must do it.
        val photo = File(dir, "photo.jpg")
        val wide = Bitmap.createBitmap(60, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        photo.outputStream().use { wide.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(photo.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val page = requireNotNull(PdfExporter.decodePicture(photo.absolutePath, 400))

        assertEquals(20, page.width)
        assertEquals(60, page.height)
    }

    /** A 40 × 40 PNG, transparent but for a black square in the middle, like a logo or a signature. */
    private fun logoPng(name: String = "logo.png"): File {
        val logo = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (x in 15 until 25) for (y in 15 until 25) logo.setPixel(x, y, Color.BLACK)
        return File(dir, name).also { file -> file.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
