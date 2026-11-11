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
        compose.onNodeWithText(text(R.string.hint_tool_step_pick_pdfs)).assertExists()
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
    fun cameraIdCardIntroductionKeepsDontRemindChoice() {
        var result: Boolean? = null
        compose.setContent { ScanProTheme { ScanIntroDialog(ScanMode.ID_CARD) { result = it } } }
        compose.onNodeWithText(text(R.string.scan_idcard_intro_title)).assertExists()
        captureDialog("intro_camera_idcard_light")
        compose.onNodeWithText(text(R.string.scan_book_intro_dont_remind)).performClick()
        compose.onNodeWithText(text(R.string.scan_book_intro_start)).performClick()
        compose.waitForIdle()
        assertEquals(true, result)
    }

    @Test
    fun cameraBookIntroductionDark() {
        compose.setContent { ScanProTheme(themeMode = ThemeMode.DARK) { ScanIntroDialog(ScanMode.BOOK) { } } }
        compose.onNodeWithText(text(R.string.scan_book_intro_title)).assertExists()
        captureDialog("intro_camera_book_dark")
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
