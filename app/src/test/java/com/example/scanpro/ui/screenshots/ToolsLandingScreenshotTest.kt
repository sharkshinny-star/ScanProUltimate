package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test
    fun tools() {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { PdfToolsScreen(recentPdfs = emptyList()) }
        }
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "tools-landing-tall-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
