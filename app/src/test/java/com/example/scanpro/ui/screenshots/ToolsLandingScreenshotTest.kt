package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.tools.PdfToolsScreen
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tools tab drawn whole (a tall window, so every section is on one picture), in light and dark, for reviewing its
 * layout without a phone. Opt-in like [ScreenScreenshotTest]:
 *   ./gradlew :app:testDebugUnitTest --tests '*ToolsLandingScreenshotTest*' -Pscanpro.screenshots=true
 * Output: app/build/screenshots/tools-landing-tall-<light|dark>.png.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2200dp-xxhdpi", application = Application::class)
class ToolsLandingScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")

    @Before
    fun setUp() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
    }

    /** The longest names are in the Indian languages: the same page in Hindi, to see that none is cut off. */
    @Test
    @Config(qualifiers = "hi-w360dp-h2200dp-xxhdpi")
    fun toolsHindi() = render("tools-landing-tall-hi")

    @Test
    fun tools() = render("tools-landing-tall")

    /** Edit PDF's options for one line (the dialog's content on a page of its own; a Dialog can't be captured here). */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun editPdfOptions() {
        val span = com.example.scanpro.imaging.PdfTextEditor.Span(
            page = 0, index = 3, text = "Total due: 1,250.00", left = 300f, top = 400f, right = 520f, bottom = 416f,
            fontName = "Arial-BoldMT", fontSize = 11f, bold = true, italic = false, colour = 0x1A3FB8
        )
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.example.scanpro.feature.tools.EditSpanForm(
                        span = span, existing = null, onDismiss = {}, onApply = {}, onUndo = null,
                        modifier = androidx.compose.ui.Modifier.padding(24.dp)
                    )
                }
            }
        }
        repeat(4) { compose.mainClock.advanceTimeBy(300); Thread.sleep(100) }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "edit-pdf-options-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Edit PDF text: the list of changes made so far (F205), apart from its dialog window. */
    @Test
    fun editPdfChanges() {
        val edit = com.example.scanpro.imaging.PdfTextEditor::Edit
        val edits = listOf(
            edit(0, 3, "Total due: 1,480.00", com.example.scanpro.imaging.PdfTextEditor.Family.ORIGINAL, true, false, 11f, null, 0f, 0f, com.example.scanpro.imaging.PdfTextEditor.Align.LEFT, false, 0f, 0f),
            edit(0, 7, "", com.example.scanpro.imaging.PdfTextEditor.Family.ORIGINAL, false, false, 10f, null, 0f, 0f, com.example.scanpro.imaging.PdfTextEditor.Align.LEFT, false, 0f, 0f),
            edit(1, com.example.scanpro.imaging.PdfTextEditor.NEW, "Paid in full on 19 November", com.example.scanpro.imaging.PdfTextEditor.Family.SANS, false, true, 12f, null, 80f, 300f, com.example.scanpro.imaging.PdfTextEditor.Align.LEFT, false, 0f, 0f)
        )
        val before = mapOf((0 to 3) to "Total due: 1,250.00", (0 to 7) to "Draft: not for circulation")
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.example.scanpro.feature.tools.ChangesList(
                        edits = edits, before = before, onOpen = {}, onRemove = {},
                        modifier = androidx.compose.ui.Modifier.padding(24.dp)
                    )
                }
            }
        }
        repeat(4) { compose.mainClock.advanceTimeBy(300); Thread.sleep(100) }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "edit-pdf-changes-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(name: String) {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { PdfToolsScreen(recentPdfs = emptyList()) }
        }
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
