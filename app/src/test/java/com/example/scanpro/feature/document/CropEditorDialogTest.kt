package com.example.scanpro.feature.document

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class CropEditorDialogTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var testImageFile: File
    private val initialCrop = CropQuad(
        topLeft = NormalizedPoint(0.1f, 0.1f),
        topRight = NormalizedPoint(0.9f, 0.1f),
        bottomRight = NormalizedPoint(0.9f, 0.9f),
        bottomLeft = NormalizedPoint(0.1f, 0.9f)
    )

    @Before
    fun setUp() {
        testImageFile = File(app.cacheDir, "test_crop_image.png")
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(testImageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @Test
    fun cropEditorDialog_displays_tools_and_handles_no_crop_and_done() {
        var confirmedCrop: CropQuad? = null
        var dismissed = false

        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.DARK) {
                CropEditorDialog(
                    sourcePath = testImageFile.absolutePath,
                    initial = initialCrop,
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmedCrop = it },
                    pageIndex = 0,
                    pageCount = 1
                )
            }
        }

        // Wait for image to load off Dispatchers.IO and Done button to become enabled
        compose.waitUntil(5000) {
            runCatching {
                compose.onNode(hasText(app.getString(R.string.common_done), substring = true) and hasClickAction()).assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        // Verify tool buttons exist
        compose.onNode(hasText(app.getString(R.string.doc_no_crop), substring = true) and hasClickAction()).assertExists()
        compose.onNode(hasText(app.getString(R.string.doc_curve), substring = true) and hasClickAction()).assertExists()
        compose.onNode(hasText(app.getString(R.string.common_done), substring = true) and hasClickAction()).assertExists()

        // Tap "No crop"
        compose.onNode(hasText(app.getString(R.string.doc_no_crop), substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()

        // Tap "Done"
        compose.onNode(hasText(app.getString(R.string.common_done), substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()

        // Confirmed crop should now be full bounds
        val expected = CropQuad(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f)
        )
        assertEquals(expected, confirmedCrop)
    }

    /**
     * Rotate turns the picture off the main thread; the outline follows when the turned picture arrives. However many
     * turns were made, an outline the user did not touch must come back from Done exactly as it went in.
     */
    @Test
    fun rotating_keeps_the_outline_and_the_picture_in_step() {
        val lopsided = CropQuad(
            topLeft = NormalizedPoint(0.05f, 0.1f),
            topRight = NormalizedPoint(0.7f, 0.2f),
            bottomRight = NormalizedPoint(0.8f, 0.95f),
            bottomLeft = NormalizedPoint(0.1f, 0.6f)
        )
        var confirmedCrop: CropQuad? = null
        var dismissed = false
        val turns = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.DARK) {
                CropEditorDialog(
                    sourcePath = testImageFile.absolutePath,
                    initial = lopsided,
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmedCrop = it },
                    onRotateRight = { turns.intValue++ },
                    turns = turns.intValue
                )
            }
        }
        val done = hasText(app.getString(R.string.common_done), substring = true) and hasClickAction()
        val rotate = hasText(app.getString(R.string.doc_rotate), substring = true) and hasClickAction()
        compose.waitUntil(5000) { runCatching { compose.onNode(done).assertIsEnabled(); true }.getOrDefault(false) }
        // Three taps, the last two in quick succession: a turn that is overtaken must not leave the outline behind.
        compose.onNode(rotate).performClick()
        repeat(5) { Thread.sleep(100); compose.waitForIdle() }
        compose.onNode(rotate).performClick()
        compose.onNode(rotate).performClick()
        repeat(8) { Thread.sleep(100); compose.waitForIdle() }
        compose.onNode(done).performClick()
        compose.waitForIdle()
        assertEquals(lopsided, confirmedCrop)
        assertEquals(false, dismissed)
    }
}
