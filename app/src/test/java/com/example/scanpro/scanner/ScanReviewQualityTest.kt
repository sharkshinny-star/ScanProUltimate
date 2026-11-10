package com.example.scanpro.scanner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
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

/** The review screen points out a blurred, glary or dark page with a Retake button, and marks it in the strip. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScanReviewQualityTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun photo(name: String): Uri {
        val file = folder.newFile("$name.jpg")
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private var retakes = 0

    private fun show(quality: Map<String, Set<PageIssue>>, pages: List<Uri>, index: Int = 0, retake: Boolean = true) =
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                ScanReviewScreen(
                    pages = remember { mutableStateListOf(*pages.toTypedArray()) },
                    quads = remember { mutableStateMapOf<String, CropQuad>().apply { pages.forEach { put(it.toString(), CropQuad.DEFAULT) } } },
                    filters = remember { mutableStateMapOf<String, PageFilter>() },
                    quality = quality,
                    defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {},
                    index = index, processing = false, onIndexChange = {}, onAdd = {}, onBack = {}, onDone = {},
                    onRetake = if (retake) ({ retakes++ }) else null
                )
            }
        }

    @Test
    fun aFlaggedPageSaysWhatIsWrongAndOffersRetake() {
        val page = photo("a")
        show(mapOf(page.toString() to setOf(PageIssue.GLARE, PageIssue.BLURRY)), listOf(page))
        compose.waitUntil(5_000) { compose.onAllNodesWithText(app.getString(R.string.scan_quality_blurry), substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Most serious first, both listed.
        compose.onNodeWithText(app.getString(R.string.scan_quality_glare), substring = true).assertExists()
        // For a person to look at: app/build/screenshots/review_quality_notice.png
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File("build/screenshots").apply { mkdirs() }.resolve("review_quality_notice.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText(app.getString(R.string.scan_quality_retake)).performClick()
        assertEquals(1, retakes)
        compose.onAllNodesWithContentDescription(app.getString(R.string.scan_quality_page_flagged, 1)).assertCountEquals(1)
    }

    @Test
    fun aGoodPageShowsNothing() {
        val good = photo("good")
        val bad = photo("bad")
        show(mapOf(good.toString() to emptySet(), bad.toString() to setOf(PageIssue.DARK)), listOf(good, bad))
        compose.waitForIdle()
        compose.onAllNodesWithText(app.getString(R.string.scan_quality_retake)).assertCountEquals(0)
        compose.onAllNodesWithText(app.getString(R.string.scan_quality_dark)).assertCountEquals(0)
        // The strip still marks the other page.
        compose.onAllNodesWithContentDescription(app.getString(R.string.scan_quality_page_flagged, 2)).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(app.getString(R.string.scan_review_page_n, 1)).assertCountEquals(1)
    }

    @Test
    fun withoutRetakeTheNoticeStillExplains() {
        val page = photo("c")
        show(mapOf(page.toString() to setOf(PageIssue.DARK)), listOf(page), retake = false)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(app.getString(R.string.scan_quality_dark)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(app.getString(R.string.scan_quality_retake)).assertCountEquals(0)
    }
}
