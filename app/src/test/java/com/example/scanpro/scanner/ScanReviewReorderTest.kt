package com.example.scanpro.scanner

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Putting pages in order on the review screen's thumbnail strip: by long-press and drag, and by screen reader action. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScanReviewReorderTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val a = Uri.parse("file:///nowhere/a.jpg")
    private val b = Uri.parse("file:///nowhere/b.jpg")
    private val c = Uri.parse("file:///nowhere/c.jpg")
    private val pages = mutableStateListOf(a, b, c)
    // Held here rather than created inside setContent, where lint wants them remembered.
    private val quads = mutableStateMapOf<String, CropQuad>()
    private val filters = mutableStateMapOf<String, PageFilter>()
    private var index by mutableIntStateOf(0)

    private fun show() = compose.setContent {
        ScanProTheme(themeMode = ThemeMode.LIGHT) {
            ScanReviewScreen(
                pages = pages, quads = quads, filters = filters,
                defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {}, index = index, processing = false,
                onIndexChange = { index = it }, onAdd = {}, onBack = {}, onDone = {}, onRetake = {}
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `move later action swaps the page with the next one and keeps the selected page selected`() {
        show()
        val moveLater = ApplicationProvider.getApplicationContext<Application>().getString(R.string.scan_review_move_later)
        compose.onNodeWithContentDescription("Page 1").performCustomAccessibilityActionWithLabel(moveLater)
        compose.runOnIdle {
            assertEquals(listOf(b, a, c), pages.toList())
            // Page a was being adjusted; it is still the selected one, now second.
            assertEquals(1, index)
        }
    }

    @Test
    fun `long-press and drag past the next thumbnail moves the page later`() {
        show()
        compose.onNodeWithContentDescription("Page 1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // One thumbnail and its gap are 56 dp (168 px at xxhdpi); go a little further, in steps like a finger.
            repeat(4) { moveBy(Offset(50f, 0f)) }
            up()
        }
        compose.runOnIdle { assertEquals(listOf(b, a, c), pages.toList()) }
    }

    @Test
    fun `a tap selects without reordering`() {
        show()
        compose.onNodeWithContentDescription("Page 3").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(listOf(a, b, c), pages.toList())
            assertEquals(2, index)
        }
    }
}
