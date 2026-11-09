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
import org.junit.Assert.assertTrue
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
}
