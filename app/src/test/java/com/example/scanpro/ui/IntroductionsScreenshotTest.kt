package com.example.scanpro.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.tools.ALL_TOOLS
import com.example.scanpro.feature.tools.ToolIntroDialog
import com.example.scanpro.feature.tools.toolIntro
import com.example.scanpro.scanner.ScanIntroDialog
import com.example.scanpro.scanner.BookSplitGuide
import com.example.scanpro.core.designsystem.renderSampleScanBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F146: renders the first-time introductions with Robolectric's native graphics, in light and dark. Each test fails
 * if a dialog throws while composing, measuring or drawing, or if its buttons don't do what they say; the images land
 * in app/build/screenshots for a person to look at (no golden images, as in [ColourThemeScreenshotTest]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class IntroductionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()


    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun captureDialog(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun toolIntroduction(id: String, dark: Boolean): List<String> {
        val tool = ALL_TOOLS.first { it.id == id }
        val events = mutableListOf<String>()
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ToolIntroDialog(tool, toolIntro(id)!!, onContinue = { events += "continue" }, onCancel = { events += "cancel" })
            }
        }
        // One short line under the picture: the step list was dropped (owner: shorter text).
        compose.onNodeWithText(text(tool.title)).assertExists()
        compose.onNodeWithText(text(R.string.hint_tool_step_pick_pdfs)).assertDoesNotExist()
        captureDialog("intro_tool_${id}_${if (dark) "dark" else "light"}")
        return events
    }

    @Test
    fun mergeIntroductionLightContinuesToThePicker() {
        val events = toolIntroduction("merge", dark = false)
        compose.onNodeWithText(text(R.string.common_continue)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("continue"), events)
    }

    @Test
    fun mergeIntroductionDarkCancels() {
        val events = toolIntroduction("merge", dark = true)
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun cameraIdCardIntroductionStartsWithOneTapAndShowsOneSentence() {
        var started = false
        compose.setContent { ScanProTheme { ScanIntroDialog(ScanMode.ID_CARD) { started = true } } }
        compose.onNodeWithText(text(R.string.scan_idcard_intro_title)).assertExists()
        // The first sentence only, and no "Don't remind me again" box: the introduction shows once by itself.
        compose.onNodeWithText(com.example.scanpro.core.designsystem.firstSentence(text(R.string.intro_idcard_body))).assertExists()
        compose.onNodeWithText(text(R.string.intro_idcard_body)).assertDoesNotExist()
        captureDialog("intro_camera_idcard_light")
        compose.onNodeWithText(text(R.string.scan_book_intro_start)).performClick()
        compose.waitForIdle()
        assertEquals(true, started)
    }

    @Test
    fun firstSentenceEndsAtTheFirstFullStopInEveryScript() {
        val first = { text: String -> com.example.scanpro.core.designsystem.firstSentence(text) }
        assertEquals("Lay the page flat.", first("Lay the page flat. ScanPro finds the edges."))
        assertEquals("पेज को समतल रखें।", first("पेज को समतल रखें। ScanPro किनारे ढूँढता है।"))
        assertEquals("Version 4.2 is here.", first("Version 4.2 is here. More below."))
        assertEquals("No full stop", first("No full stop"))
        assertEquals("One sentence.", first("One sentence."))
    }

    @Test
    fun cameraBookIntroductionDark() {
        compose.setContent { ScanProTheme(themeMode = ThemeMode.DARK) { ScanIntroDialog(ScanMode.BOOK) { } } }
        compose.onNodeWithText(text(R.string.scan_book_intro_title)).assertExists()
        captureDialog("intro_camera_book_dark")
    }

    @Test
    fun cameraDocumentIntroductionTryNowRunsTheSample() {
        val events = mutableListOf<String>()
        compose.setContent {
            ScanProTheme { ScanIntroDialog(ScanMode.DOCUMENT, onTry = { events += "try" }) { events += "start" } }
        }
        compose.onNodeWithText(text(R.string.intro_doc_title)).assertExists()
        captureDialog("intro_camera_document_light")
        compose.onNodeWithText(text(R.string.intro_doc_try)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("try"), events)
    }

    @Test
    fun cameraDocumentIntroductionDarkSkips() {
        val events = mutableListOf<String>()
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.DARK) {
                ScanIntroDialog(ScanMode.DOCUMENT, onTry = { events += "try" }) { events += "start" }
            }
        }
        captureDialog("intro_camera_document_dark")
        compose.onNodeWithText(text(R.string.hint_skip)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("start"), events)
    }

    @Test
    fun cameraBookIntroductionLight() {
        compose.setContent { ScanProTheme { ScanIntroDialog(ScanMode.BOOK) { } } }
        compose.onNodeWithText(text(R.string.intro_book_body)).assertExists()
        captureDialog("intro_camera_book_light")
    }

    /** The viewfinder's book guide, over a dark stand-in for the camera preview (CameraX does not render here). */
    @Test
    fun bookSplitGuideSwapsTheLetters() {
        var leftFirst by mutableStateOf(true)
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.fillMaxSize().background(Color(0xFF3B3F45))) {
                    BookSplitGuide(detection = null, quad = { null }, accent = Color(0xFF3DDC97), leftFirst = leftFirst)
                }
            }
        }
        captureRoot("intro_viewfinder_book_ab")
        leftFirst = false
        compose.waitForIdle()
        captureRoot("intro_viewfinder_book_ba")
    }

    /** The sample page "Try now" scans, as the bitmap it writes for the review. */
    @Test
    fun sampleScanBitmapIsAFullSizePage() {
        val bitmap = renderSampleScanBitmap(750, 1000)
        assertEquals(750, bitmap.width)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "intro_sample_scan.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun welcomeWalkthrough(dark: Boolean) {
        var started = 0
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { WelcomeScreen(onGetStarted = { started++ }) } }
        val suffix = if (dark) "dark" else "light"
        compose.onNodeWithText(text(R.string.intro_welcome_scan_title)).assertExists()
        captureRoot("intro_welcome_1_$suffix")
        compose.onNodeWithText(text(R.string.hint_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.intro_welcome_files_title)).assertExists()
        captureRoot("intro_welcome_2_$suffix")
        compose.onNodeWithText(text(R.string.hint_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.intro_welcome_tools_title)).assertExists()
        captureRoot("intro_welcome_3_$suffix")
        compose.onNodeWithText(text(R.string.hint_skip)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.ui_welcome_get_started)).assertExists()
    }

    @Test
    fun welcomePagesLight() = welcomeWalkthrough(dark = false)

    @Test
    fun welcomePagesDark() = welcomeWalkthrough(dark = true)

    private fun captureRoot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun cameraIdCardIntroductionDark() {
        compose.setContent { ScanProTheme(themeMode = ThemeMode.DARK) { ScanIntroDialog(ScanMode.ID_CARD) { } } }
        captureDialog("intro_camera_idcard_dark")
    }

    @Test
    fun homeTourStepsThroughToGotIt() {
        var done = 0
        compose.setContent { ScanProTheme { HomeTourDialog(onDone = { done++ }) } }
        compose.onNodeWithText(text(R.string.hint_tour_scan_title)).assertExists()
        captureDialog("intro_tour_1_light")
        compose.onNodeWithText(text(R.string.hint_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.hint_tour_files_title)).assertExists()
        captureDialog("intro_tour_2_light")
        compose.onNodeWithText(text(R.string.hint_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.hint_tour_tools_title)).assertExists()
        captureDialog("intro_tour_3_light")
        // No Skip on the last page; Got it ends the tour.
        compose.onNodeWithText(text(R.string.hint_skip)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.hint_got_it)).performClick()
        compose.waitForIdle()
        assertEquals(1, done)
    }

    @Test
    fun homeTourDarkCanBeSkipped() {
        var done = 0
        compose.setContent { ScanProTheme(themeMode = ThemeMode.DARK) { HomeTourDialog(onDone = { done++ }) } }
        captureDialog("intro_tour_1_dark")
        compose.onNodeWithText(text(R.string.hint_skip)).performClick()
        compose.waitForIdle()
        assertEquals(1, done)
    }
}
