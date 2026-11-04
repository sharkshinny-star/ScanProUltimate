package com.example.scanpro.scanner

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The review sliders' Reset and "Apply to all pages" actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScanReviewSlidersTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val a = Uri.parse("file:///nowhere/a.jpg")
    private val b = Uri.parse("file:///nowhere/b.jpg")
    private val c = Uri.parse("file:///nowhere/c.jpg")
    private val pages = mutableStateListOf(a, b, c)
    private val adjustments = mutableStateMapOf<String, PageAdjustments>()
    private val brighter = reviewAdjustments(20, 10, 0)

    private fun show() = compose.setContent {
        ScanProTheme(themeMode = ThemeMode.LIGHT) {
            ScanReviewScreen(
                pages = pages, quads = remember { mutableStateMapOf<String, CropQuad>() }, filters = remember { mutableStateMapOf<String, PageFilter>() },
                adjustments = adjustments, defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {}, index = 0,
                processing = false, onIndexChange = {}, onAdd = {}, onBack = {}, onDone = {}
            )
        }
    }

    @Test
    fun `apply to all pages copies this page's sliders to every page`() {
        adjustments[a.toString()] = brighter
        show()
        compose.onNodeWithText(app.getString(R.string.scan_review_adjust_use_on_all)).performClick()
        compose.runOnIdle { listOf(a, b, c).forEach { assertEquals(brighter, adjustments[it.toString()]) } }
        // Every page now matches, so the action has nothing left to do and goes away.
        compose.onNodeWithText(app.getString(R.string.scan_review_adjust_use_on_all)).assertDoesNotExist()
    }

    @Test
    fun `reset clears this page's sliders and leaves the others`() {
        adjustments[a.toString()] = brighter
        adjustments[b.toString()] = brighter
        show()
        compose.onNodeWithText(app.getString(R.string.common_reset)).performClick()
        compose.runOnIdle {
            assertNull(adjustments[a.toString()])
            assertEquals(brighter, adjustments[b.toString()])
        }
    }

    @Test
    fun `nothing to offer when no slider has moved on any page`() {
        show()
        compose.onNodeWithText(app.getString(R.string.common_reset)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.scan_review_adjust_use_on_all)).assertDoesNotExist()
    }
}
