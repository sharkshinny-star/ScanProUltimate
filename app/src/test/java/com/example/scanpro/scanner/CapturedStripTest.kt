package com.example.scanpro.scanner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests for the camera's captured page strip (R30): thumbnails, quality flags, tap to open, long-press to delete. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class CapturedStripTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun photo(name: String): Uri {
        val file = folder.newFile("$name.jpg")
        val bitmap = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun `strip displays page numbers and handles clicks`() {
        val p1 = photo("page1")
        val p2 = photo("page2")
        val pages = mutableStateListOf(p1, p2)
        val quads = mutableStateMapOf<String, CropQuad>()
        val filters = mutableStateMapOf<String, PageFilter>()
        val quality = mutableStateMapOf<String, Set<PageIssue>>()

        var opened = -1
        var deleted = -1

        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                CapturedStrip(
                    pages = pages,
                    quads = quads,
                    filters = filters,
                    quality = quality,
                    sessionFilter = null,
                    onOpen = { opened = it },
                    onDelete = { deleted = it }
                )
            }
        }

        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithText("2").assertExists()

        compose.onNodeWithText("1").performClick()
        assertEquals(0, opened)

        compose.onNodeWithText("2").performTouchInput { longClick() }
        assertEquals(1, deleted)
    }

    @Test
    fun `flagged page shows warning icon and accessibility label`() {
        val p1 = photo("clean")
        val p2 = photo("blurry")
        val pages = mutableStateListOf(p1, p2)
        val quads = mutableStateMapOf(
            p1.toString() to CropQuad(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f)),
            p2.toString() to CropQuad(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f))
        )
        val filters = mutableStateMapOf<String, PageFilter>()
        val quality = mutableStateMapOf(
            p2.toString() to setOf(PageIssue.BLURRY)
        )

        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                CapturedStrip(
                    pages = pages,
                    quads = quads,
                    filters = filters,
                    quality = quality,
                    sessionFilter = null,
                    onOpen = {},
                    onDelete = {}
                )
            }
        }

        // Clean page has standard label, blurry page has flagged label
        val deleteHint = app.getString(R.string.scan_strip_delete_hint)
        val cleanLabel = "${app.getString(R.string.scan_review_page_n, 1)}. $deleteHint"
        val flaggedLabel = "${app.getString(R.string.scan_quality_page_flagged, 2)}. $deleteHint"

        assertEquals("Clean page semantics", 1, compose.onAllNodesWithContentDescription(cleanLabel).fetchSemanticsNodes().size)
        assertEquals("Flagged page semantics", 1, compose.onAllNodesWithContentDescription(flaggedLabel).fetchSemanticsNodes().size)
    }

    @Test
    fun `page with default quad is treated as needing review`() {
        val p1 = photo("no_outline")
        val pages = mutableStateListOf(p1)
        val quads = mutableStateMapOf(p1.toString() to CropQuad.DEFAULT)
        val filters = mutableStateMapOf<String, PageFilter>()
        val quality = mutableStateMapOf<String, Set<PageIssue>>()

        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                CapturedStrip(
                    pages = pages,
                    quads = quads,
                    filters = filters,
                    quality = quality,
                    sessionFilter = null,
                    onOpen = {},
                    onDelete = {}
                )
            }
        }

        val deleteHint = app.getString(R.string.scan_strip_delete_hint)
        val flaggedLabel = "${app.getString(R.string.scan_quality_page_flagged, 1)}. $deleteHint"

        assertEquals("Default quad should be flagged", 1, compose.onAllNodesWithContentDescription(flaggedLabel).fetchSemanticsNodes().size)
    }
}
