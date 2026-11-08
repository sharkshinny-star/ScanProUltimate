package com.example.scanpro.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.feature.library.LibraryScreenForTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The launch animation (Figma "Launch v2"): its timing contract with MainActivity, and frames rendered over the
 * Home screen into app/build/screenshots (intro_*.png) for comparing with the Figma render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class LaunchIntroTest {
    @get:Rule
    val compose = createComposeRule()

    private var reveals = 0
    private var finishes = 0

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScanProTheme {
                Box {
                    LibraryScreenForTest(
                        LibraryOverview(
                            documentCount = 3,
                            recent = listOf("Lease agreement", "Receipt", "Passport").mapIndexed { i, title ->
                                DocumentSummary("d$i", title, 0L, 0L, "", i + 1, null, 100_000L)
                            }
                        )
                    )
                    LaunchIntro(dark = false, onReveal = { reveals++ }, onFinished = { finishes++ })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun revealsAt1720msAndFinishesAt2250ms() {
        show()
        compose.mainClock.advanceTimeBy(1_650)
        assertEquals(0, reveals)
        compose.mainClock.advanceTimeBy(150)
        assertEquals(1, reveals)
        assertEquals(0, finishes)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(1, finishes)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("reveal runs once", 1, reveals)
        assertEquals("finish runs once", 1, finishes)
    }

    @Test
    fun aTapSkipsToTheExit() {
        show()
        compose.mainClock.advanceTimeBy(200)
        compose.onRoot().performTouchInput { click(center) }
        // From the exit start (1.52 s) the reveal is 0.20 s away and the end 0.73 s.
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, reveals)
        compose.mainClock.advanceTimeBy(550)
        assertEquals(1, finishes)
    }

    @Test
    fun framesForComparisonWithFigma() {
        show()
        var elapsed = 0L
        listOf(0L, 100L, 350L, 600L, 800L, 1_000L, 1_200L, 1_400L, 1_550L, 1_650L, 1_800L, 2_050L).forEach { at ->
            compose.mainClock.advanceTimeBy(at - elapsed)
            elapsed = at
            capture("intro_%04d".format(at))
        }
    }
}
