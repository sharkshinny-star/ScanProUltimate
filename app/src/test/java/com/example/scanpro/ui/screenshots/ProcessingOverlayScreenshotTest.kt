package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.tools.ProcessingOverlay
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the tools' progress card to PNG files: a phone upright and on its side, a narrow phone, a tablet, a tool
 * that reports no count, and the finish (ring closed, tick drawn).
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*ProcessingOverlayScreenshotTest*' -Pscanpro.screenshots=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class ProcessingOverlayScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")

    @Before
    fun setUp() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
    }

    private fun shoot(
        name: String,
        dark: Boolean = false,
        current: Int = 2,
        total: Int = 11,
        finishMillis: Int = 0,
        afterMillis: Long = 1_000
    ) {
        // The clock is moved by hand, so a frame part-way through the finish can be caught.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ProcessingOverlay(
                    current = current,
                    total = total,
                    title = "Merging 11 PDFs…",
                    subtitle = "Merging file $current of $total…",
                    onCancel = {},
                    finishMillis = finishMillis
                )
            }
        }
        compose.mainClock.advanceTimeBy(afterMillis)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "progress_card_$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun phoneLight() = shoot("phone-light")

    @Test fun phoneDark() = shoot("phone-dark", dark = true)

    @Test fun phoneNoCount() = shoot("phone-no-count", total = 0, current = 0)

    @Test fun phoneFinishClosing() = shoot("phone-finish-closing", finishMillis = 1_900, afterMillis = 900)

    @Test fun phoneFinishRipple() = shoot("phone-finish-ripple", current = 11, finishMillis = 700, afterMillis = 560)

    @Test fun phoneFinished() = shoot("phone-finished", current = 11, finishMillis = 700, afterMillis = 1_500)

    @Test fun phoneArriving() = shoot("phone-arriving", afterMillis = 96)

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun phoneOnItsSide() = shoot("phone-landscape")

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun narrowPhone() = shoot("phone-narrow")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tablet() = shoot("tablet")

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.6f)
    fun phoneLargeText() = shoot("phone-large-text")
}
