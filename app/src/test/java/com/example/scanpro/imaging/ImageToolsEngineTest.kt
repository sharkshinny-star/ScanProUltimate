package com.example.scanpro.imaging

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ImageToolsEngineTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(app.cacheDir, "image_tools_test_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun createSampleBitmap(width: Int, height: Int, withAlpha: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (withAlpha) {
            canvas.drawColor(Color.TRANSPARENT)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        val paint = Paint().apply { color = Color.BLUE }
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, paint)
        paint.color = Color.RED
        canvas.drawRect(10f, 10f, (width / 3).toFloat(), (height / 3).toFloat(), paint)
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, file: File): File {
        FileOutputStream(file).use { out ->
            bitmap.compress(format, 95, out)
        }
        return file
    }

    @Test
    fun inspectImage_returnsAccurateMetadata() {
        val sourceFile = File(testDir, "sample.jpg")
        val bmp = createSampleBitmap(400, 300)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)

        val meta = ImageToolsEngine.inspectImage(app, Uri.fromFile(sourceFile))
        assertNotNull("Image metadata should not be null", meta)
        assertEquals("sample", meta?.fileName)
        assertEquals(400, meta?.width)
        assertEquals(300, meta?.height)
        assertTrue("Size should be positive", (meta?.sizeBytes ?: 0) > 0)
    }

    @Test
    fun compressImage_withTargetSize_producesSmallerFile() = runBlocking {
        val sourceFile = File(testDir, "large_source.jpg")
        val bmp = createSampleBitmap(1600, 1200)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)
        val originalSize = sourceFile.length()

        // Half the picture's own size: a target it does not already meet. (F193: a target the original already meets
        // keeps the original, full resolution and all, instead of shrinking it anyway.)
        val targetBytes = originalSize / 2
        val outFile = File(testDir, "compressed.jpg")
        val config = ImageCompressConfig(
            targetSizeBytes = targetBytes,
            outputFormat = ImageExportFormat.JPEG
        )

        val result = ImageToolsEngine.compressImage(app, Uri.fromFile(sourceFile), config, outFile)
        assertTrue("Output file must exist", outFile.exists())
        assertEquals(1, result.pageCount)
        assertEquals(originalSize, result.originalSizeBytes)
        assertTrue("Output file should be substantially smaller than original", outFile.length() < originalSize)
        assertTrue("Output must meet the target", outFile.length() <= targetBytes)

        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        assertNotNull("Compressed file must be a valid image", decoded)
    }

    @Test
    fun compressImage_tinyPng_isNeverLargerThanTheOriginal() = runBlocking {
        val sourceFile = File(testDir, "tiny.png")
        saveBitmap(createSampleBitmap(16, 16), Bitmap.CompressFormat.PNG, sourceFile)
        val originalSize = sourceFile.length()
        val outFile = File(testDir, "tiny_out.png")

        val result = ImageToolsEngine.compressImage(app, Uri.fromFile(sourceFile), ImageCompressConfig(outputFormat = ImageExportFormat.PNG), outFile)

        assertTrue("A tiny PNG must not come back larger (was $originalSize, got ${outFile.length()})", outFile.length() <= originalSize)
        assertEquals(outFile.length(), result.outputSizeBytes)
    }

    @Test
    fun compressImage_withQuality_respectsMaxDimension() = runBlocking {
        val sourceFile = File(testDir, "highres.jpg")
        val bmp = createSampleBitmap(2000, 1500)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)

        val outFile = File(testDir, "capped.jpg")
        val config = ImageCompressConfig(
            qualityPercent = 70,
            maxDimension = 800,
            outputFormat = ImageExportFormat.JPEG
        )

        ImageToolsEngine.compressImage(app, Uri.fromFile(sourceFile), config, outFile)
        assertTrue(outFile.exists())

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, bounds)
        assertTrue("Width should be <= 800", bounds.outWidth <= 800)
        assertTrue("Height should be <= 800", bounds.outHeight <= 800)
    }

    @Test
    fun convertImage_jpgToPng_isLosslessAndPreservesContent() = runBlocking {
        val sourceFile = File(testDir, "photo.jpg")
        val bmp = createSampleBitmap(500, 400)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)

        val outFile = File(testDir, "photo_converted.png")
        val result = ImageToolsEngine.convertImage(
            context = app,
            inputUris = listOf(Uri.fromFile(sourceFile)),
            targetFormat = ImageExportFormat.PNG,
            outputLocation = outFile
        )

        assertTrue(outFile.exists())
        assertEquals(1, result.pageCount)

        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        assertNotNull("Converted PNG must decode cleanly", decoded)
        assertEquals(500, decoded.width)
        assertEquals(400, decoded.height)
    }

    @Test
    fun convertImage_pngWithAlphaToJpg_flattensTransparentToWhite() = runBlocking {
        val sourceFile = File(testDir, "transparent.png")
        val bmp = createSampleBitmap(200, 200, withAlpha = true)
        saveBitmap(bmp, Bitmap.CompressFormat.PNG, sourceFile)

        val outFile = File(testDir, "flattened.jpg")
        ImageToolsEngine.convertImage(
            context = app,
            inputUris = listOf(Uri.fromFile(sourceFile)),
            targetFormat = ImageExportFormat.JPEG,
            outputLocation = outFile
        )

        assertTrue(outFile.exists())
        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        assertNotNull(decoded)

        // Corner pixel (0,0) was transparent, should now be pure solid white in JPEG
        val pixel = decoded.getPixel(0, 0)
        assertEquals("Alpha should be fully opaque", 255, Color.alpha(pixel))
        assertTrue("Pixel should be near white (R > 240)", Color.red(pixel) > 240)
        assertTrue("Pixel should be near white (G > 240)", Color.green(pixel) > 240)
        assertTrue("Pixel should be near white (B > 240)", Color.blue(pixel) > 240)
    }

    @Test
    fun convertImage_multipleImages_bundlesIntoZip() = runBlocking {
        val file1 = saveBitmap(createSampleBitmap(300, 300), Bitmap.CompressFormat.JPEG, File(testDir, "img1.jpg"))
        val file2 = saveBitmap(createSampleBitmap(300, 300), Bitmap.CompressFormat.JPEG, File(testDir, "img2.jpg"))

        val outDir = File(testDir, "multi_convert").apply { mkdirs() }
        val outZip = File(outDir, "bundle.zip")

        val result = ImageToolsEngine.convertImage(
            context = app,
            inputUris = listOf(Uri.fromFile(file1), Uri.fromFile(file2)),
            targetFormat = ImageExportFormat.PNG,
            outputLocation = outZip
        )

        assertEquals(2, result.pageCount)
        assertTrue("Output should be a zip file", result.outputFile.name.endsWith(".zip"))
        assertTrue("Zip file must exist", result.outputFile.exists())

        ZipFile(result.outputFile).use { zip ->
            assertEquals("Zip should contain 2 converted files", 2, zip.size())
            val names = zip.entries().toList().map { it.name }
            assertTrue("Should contain img1.png", names.any { it.contains("img1.png") })
            assertTrue("Should contain img2.png", names.any { it.contains("img2.png") })
        }
    }

    @Test
    fun resizeImage_changesDimensionsAccurately() = runBlocking {
        val sourceFile = File(testDir, "original.jpg")
        val bmp = createSampleBitmap(1000, 800)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)

        val outFile = File(testDir, "resized.jpg")
        val config = ImageResizeConfig(
            targetWidth = 600,
            targetHeight = 600,
            outputFormat = ImageExportFormat.JPEG
        )

        val result = ImageToolsEngine.resizeImage(app, Uri.fromFile(sourceFile), config, outFile)
        assertTrue(outFile.exists())
        assertEquals(1, result.pageCount)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, bounds)
        assertEquals(600, bounds.outWidth)
        assertEquals(600, bounds.outHeight)
    }

    /** A 1000x800 source resized into a 600x600 box in the given fit mode; returns the width and height written. */
    private fun resizeWithFit(fit: ImageResizeFit): Pair<Int, Int> = runBlocking {
        val sourceFile = File(testDir, "fit_${fit.name}.jpg")
        saveBitmap(createSampleBitmap(1000, 800), Bitmap.CompressFormat.JPEG, sourceFile)
        val outFile = File(testDir, "fit_${fit.name}_out.jpg")
        ImageToolsEngine.resizeImage(app, Uri.fromFile(sourceFile), ImageResizeConfig(600, 600, ImageExportFormat.JPEG, fit = fit), outFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, bounds)
        bounds.outWidth to bounds.outHeight
    }

    @Test
    fun resizeImage_fitKeepsProportionsInsideTheBox() {
        val (w, h) = resizeWithFit(ImageResizeFit.FIT)
        assertEquals(600, w)
        assertEquals(480, h) // 1000x800 is 5:4, so a 600-wide result is 480 tall, not 600
    }

    @Test
    fun resizeImage_fillGivesTheExactBoxWithoutStretching() {
        val (w, h) = resizeWithFit(ImageResizeFit.FILL)
        assertEquals(600, w)
        assertEquals(600, h)
    }

    @Test
    fun resizeImage_stretchGivesTheExactBox() {
        val (w, h) = resizeWithFit(ImageResizeFit.STRETCH)
        assertEquals(600, w)
        assertEquals(600, h)
    }

    @Test
    fun resizeImage_throwsOnInvalidDimensions() = runBlocking {
        val sourceFile = File(testDir, "valid.jpg")
        saveBitmap(createSampleBitmap(100, 100), Bitmap.CompressFormat.JPEG, sourceFile)

        val outFile = File(testDir, "invalid.jpg")
        val config = ImageResizeConfig(
            targetWidth = 5, // < 10 is invalid
            targetHeight = 100
        )

        try {
            ImageToolsEngine.resizeImage(app, Uri.fromFile(sourceFile), config, outFile)
            fail("Should throw IllegalArgumentException on invalid dimensions")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun cropImage_cropsAccurately() = runBlocking {
        val sourceFile = File(testDir, "uncropped.jpg")
        val bmp = createSampleBitmap(1000, 1000)
        saveBitmap(bmp, Bitmap.CompressFormat.JPEG, sourceFile)

        val outFile = File(testDir, "cropped.jpg")
        val quad = CropQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.9f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f)
        )

        val result = ImageToolsEngine.cropImage(app, Uri.fromFile(sourceFile), quad, outFile)
        assertTrue(outFile.exists())
        assertEquals(1, result.pageCount)
        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        assertNotNull("Cropped file must be readable", decoded)
    }

    // F193: a straight crop keeps the picture's own format and transparency, and applies the turn before the cut.
    @Test
    fun cropToFile_turnsThenCutsAndKeepsPngTransparency() {
        val sourceFile = saveBitmap(createSampleBitmap(200, 100, withAlpha = true), Bitmap.CompressFormat.PNG, File(testDir, "wide.png"))
        val outFile = File(testDir, "cut.png")

        val written = ImageToolsEngine.cropToFile(sourceFile, outFile, ImageCropSpec(left = 0.5f, rotation = 90))

        assertEquals(ImageExportFormat.PNG, written)
        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        // Turned a quarter: 100 wide, 200 high; the right half of that is 50 wide.
        assertEquals(50, decoded.width)
        assertEquals(200, decoded.height)
        assertTrue("Transparent corners must stay transparent", Color.alpha(decoded.getPixel(decoded.width - 1, 0)) < 255)
    }

    // F193: Compress to a size the full-resolution picture can meet keeps the full resolution.
    @Test
    fun compressImage_targetMetAtFullResolution_keepsResolution() = runBlocking {
        val sourceFile = saveBitmap(createSampleBitmap(1200, 900), Bitmap.CompressFormat.JPEG, File(testDir, "plain.jpg"))
        val atForty = ImageToolsEngine.encodeBytes(BitmapFactory.decodeFile(sourceFile.absolutePath), ImageExportFormat.JPEG, 40).size
        val target = minOf(sourceFile.length() - 1, atForty * 2L)
        val outFile = File(testDir, "target.jpg")

        ImageToolsEngine.compressImage(app, Uri.fromFile(sourceFile), ImageCompressConfig(targetSizeBytes = target), outFile)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, bounds)
        assertEquals(1200, bounds.outWidth)
        assertTrue(outFile.length() <= target)
    }

    // F193: two pictures with the same name (from two folders) both reach the bundle instead of one overwriting the other.
    @Test
    fun convertImage_sameNames_bothKept() = runBlocking {
        val first = saveBitmap(createSampleBitmap(120, 120), Bitmap.CompressFormat.JPEG, File(File(testDir, "a").apply { mkdirs() }, "photo.jpg"))
        val second = saveBitmap(createSampleBitmap(140, 140), Bitmap.CompressFormat.JPEG, File(File(testDir, "b").apply { mkdirs() }, "photo.jpg"))
        val outDir = File(testDir, "same_names").apply { mkdirs() }

        val result = ImageToolsEngine.convertImage(
            context = app,
            inputUris = listOf(Uri.fromFile(first), Uri.fromFile(second)),
            targetFormat = ImageExportFormat.PNG,
            outputLocation = File(outDir, "bundle.png")
        )

        ZipFile(result.outputFile).use { zip -> assertEquals(2, zip.size()) }
    }
}
