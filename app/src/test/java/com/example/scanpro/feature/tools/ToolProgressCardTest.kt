package com.example.scanpro.feature.tools

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tools' progress card: how long it stays once a tool has finished, how it is arranged for the window, and that
 * its content sits in the middle of the card (it used to sit against the left edge).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ToolProgressCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an instant tool keeps the card for the rest of two seconds`() {
        assertEquals(2_000L, ToolFinish.MIN_VISIBLE_MS)
        assertEquals(1_950L, ToolFinish.holdMillis(visibleForMillis = 50, animationsOn = true))
        assertEquals(800L, ToolFinish.holdMillis(visibleForMillis = 1_200, animationsOn = true))
    }

    @Test
    fun `a long tool only adds the short finish`() {
        assertEquals(ToolFinish.FINISH_MS, ToolFinish.holdMillis(visibleForMillis = 1_900, animationsOn = true))
        assertEquals(ToolFinish.FINISH_MS, ToolFinish.holdMillis(visibleForMillis = 45_000, animationsOn = true))
    }

    @Test
    fun `nothing is held when animations are off`() {
        assertEquals(0L, ToolFinish.holdMillis(visibleForMillis = 0, animationsOn = false))
        assertEquals(0L, ToolFinish.holdMillis(visibleForMillis = 45_000, animationsOn = false))
    }

    @Test
    fun `a clock that went backwards cannot hold the card for longer than two seconds`() {
        assertEquals(2_000L, ToolFinish.holdMillis(visibleForMillis = -5_000, animationsOn = true))
    }

    @Test
    fun `the dial sits above the text on a phone held upright and beside it on its side`() {
        val upright = progressCardLayout(360.dp, 800.dp)
        assertFalse(upright.sideBySide)
        assertEquals(144.dp, upright.dial)
        assertEquals(360.dp, upright.maxWidth)

        val onItsSide = progressCardLayout(800.dp, 360.dp)
        assertTrue(onItsSide.sideBySide)
        assertEquals(520.dp, onItsSide.maxWidth)
        assertTrue("the dial fits the height", onItsSide.dial <= 360.dp - 112.dp)
    }

    @Test
    fun `the dial is larger on a tablet and smaller on a narrow phone`() {
        assertEquals(168.dp, progressCardLayout(800.dp, 1280.dp).dial)
        assertEquals(420.dp, progressCardLayout(800.dp, 1280.dp).maxWidth)
        assertEquals(112.dp, progressCardLayout(320.dp, 640.dp).dial)
        // A short, narrow window (split screen) keeps the stacked card, which scrolls; the dial is never under 96 dp.
        assertFalse(progressCardLayout(360.dp, 400.dp).sideBySide)
        assertEquals(96.dp, progressCardLayout(520.dp, 180.dp).dial)
    }

    private fun centreOf(text: String): Float {
        val bounds: Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return bounds.center.x
    }

    private fun show(finishMillis: Int = 0) {
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                ProcessingOverlay(
                    current = 2, total = 11, title = "Merging 11 PDFs…", subtitle = "Merging file 2 of 11…",
                    onCancel = {}, finishMillis = finishMillis
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `on a phone the dial, text and Cancel are centred in the window`() {
        show()
        val middle = compose.onRoot().fetchSemanticsNode().size.width / 2f
        for (text in listOf("18%", "Merging 11 PDFs…", "Merging file 2 of 11…", "2 / 11", "Cancel")) {
            assertTrue("$text is centred (at ${centreOf(text)}, middle $middle)", abs(centreOf(text) - middle) <= 1.5f)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun `on its side the text is to the right of the figure and Cancel is on screen`() {
        show()
        val root = compose.onRoot().fetchSemanticsNode().size
        assertTrue("the title is right of the percentage", centreOf("Merging 11 PDFs…") > centreOf("18%"))
        val cancel = compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel is inside the window", cancel.bottom <= root.height && cancel.top >= 0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `the finish counts up to 100 percent and Cancel can no longer be pressed`() {
        show(finishMillis = 900)
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithText("11 / 11").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
}
