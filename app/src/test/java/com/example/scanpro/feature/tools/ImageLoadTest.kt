package com.example.scanpro.feature.tools

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import com.example.scanpro.imaging.ImageExportFormat
import org.junit.Assert.assertNull

/** The picture a tool screen shows (F239): it loads, and a file that is no picture fails instead of loading for ever. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ImageLoadTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun aPictureLoadsWithItsFacts() {
        val file = File(app.cacheDir, "load_test.png")
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val loaded = loadImage(app, Uri.fromFile(file))

        assertNotNull(loaded)
        assertEquals(300, loaded!!.width)
        assertEquals(200, loaded.height)
        assertEquals(ImageExportFormat.PNG, loaded.format)
        assertTrue(loaded.meta.sizeBytes > 0)
    }

    @Test
    fun aFileThatIsNoPictureFails() {
        val file = File(app.cacheDir, "not_a_picture.png").apply { writeText("hello") }
        assertNull(loadImage(app, Uri.fromFile(file)))
    }

    @Test
    fun aMissingFileFails() {
        assertNull(loadImage(app, Uri.fromFile(File(app.cacheDir, "gone.png"))))
    }
}
