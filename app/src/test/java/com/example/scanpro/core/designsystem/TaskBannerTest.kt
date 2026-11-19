package com.example.scanpro.core.designsystem

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.scanpro.data.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The banner under a top bar while a long task runs: it says what is running and offers Stop. It used to be a bare
 * "Stop" at the right edge, which on Home (whose title is the greeting) did not say what it stopped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class TaskBannerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(dark: Boolean = false, busy: Boolean = true, status: String?, onStop: (() -> Unit)? = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        TopBarWithProgress(busy = busy, onStop = onStop, status = status) {
                            BrandLockup(subtitle = "GOOD MORNING")
                        }
                        Text("The rest of the screen")
                    }
                }
            }
        }
    }

    @Test
    fun `a quick task shows no banner, a long one names the task and offers Stop`() {
        var stopped = 0
        show(status = "Reading text on 12 pages…", onStop = { stopped++ })
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("Reading text on 12 pages…").assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun `a screen with no words for the task gets the general ones`() {
        show(status = null)
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithText("Working on your document…").assertExists()
    }

    @Test
    fun `nothing shows when no task runs or the task cannot be stopped`() {
        show(busy = false, status = "Reading text…")
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithText("Stop").assertDoesNotExist()
    }

    @Test
    fun renders() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        show(status = "Reading text on 12 pages…")
        compose.mainClock.advanceTimeBy(1_650)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "task_banner-light.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rendersDarkLongText() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        show(dark = true, status = "Importing 24 photos from your gallery and reading the text on every page…")
        compose.mainClock.advanceTimeBy(1_650)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "task_banner-dark.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
