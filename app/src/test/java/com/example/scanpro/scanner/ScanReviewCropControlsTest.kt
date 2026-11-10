package com.example.scanpro.scanner

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The review screen's dedicated crop toolbar and tool switching (Adobe Scan workflow). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScanReviewCropControlsTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val a = Uri.parse("file:///nowhere/a.jpg")
    private val b = Uri.parse("file:///nowhere/b.jpg")
    private val pages = mutableStateListOf(a, b)
    private val customQuad = CropQuad(
        topLeft = NormalizedPoint(0.1f, 0.1f),
        topRight = NormalizedPoint(0.9f, 0.1f),
        bottomRight = NormalizedPoint(0.9f, 0.9f),
        bottomLeft = NormalizedPoint(0.1f, 0.9f)
    )
    private val quads = mutableStateMapOf<String, CropQuad>()
    private val filters = mutableStateMapOf<String, PageFilter>()
    private val adjustments = mutableStateMapOf<String, PageAdjustments>()

    private fun show() {
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                ScanReviewScreen(
                    pages = pages, quads = quads, filters = filters,
                    adjustments = adjustments, defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {}, index = 0,
                    processing = false, onIndexChange = {}, onAdd = {}, onBack = {}, onDone = {}
                )
            }
        }
    }

    @Test
    fun `crop panel displays Auto-detect, No crop and Curve action buttons`() {
        show()
        // Tap Crop chip
        compose.onNode(hasText(app.getString(R.string.doc_crop), substring = true) and hasClickAction()).performClick()

        // Dedicated crop action buttons are visible right under the canvas
        compose.onNode(hasText(app.getString(R.string.scan_review_auto_detect), substring = true) and hasClickAction()).assertExists()
        compose.onNode(hasText(app.getString(R.string.scan_review_no_crop), substring = true) and hasClickAction()).assertExists()
        compose.onNode(hasText(app.getString(R.string.scan_review_curve), substring = true) and hasClickAction()).assertExists()
    }

    @Test
    fun `no crop button resets quad to default full frame`() {
        quads[a.toString()] = customQuad
        show()
        // Tap Crop chip
        compose.onNode(hasText(app.getString(R.string.doc_crop), substring = true) and hasClickAction()).performClick()

        // Tap No crop
        compose.onNode(hasText(app.getString(R.string.scan_review_no_crop), substring = true) and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals(CropQuad.DEFAULT, quads[a.toString()])
        }
    }

    @Test
    fun `rotate button and save button are present on the bottom bar`() {
        show()
        compose.onNode(hasContentDescription(app.getString(R.string.scan_review_rotate)) and hasClickAction()).assertExists()
        compose.onNode(hasText(app.getString(R.string.scan_review_save_all, 2), substring = true) and hasClickAction()).assertExists()
    }
}
