package com.example.scanpro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.designsystem.HintInput
import com.example.scanpro.feature.tools.TOOL_SECTIONS
import com.example.scanpro.feature.tools.toolIntro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F146: when the first-time introductions show, and that every tool tile has one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunHintsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mergeKey = FirstRunHints.toolKey("merge")

    @Test
    fun nothingShowsUntilTheWelcomeArmsTheHints() {
        // Someone updating from an older version never went through this version's welcome: no tour, no tool intros.
        assertFalse(FirstRunHints.isDue(context, FirstRunHints.HOME_TOUR))
        assertFalse(FirstRunHints.isDue(context, mergeKey))

        FirstRunHints.arm(context)

        assertTrue(FirstRunHints.isDue(context, FirstRunHints.HOME_TOUR))
        assertTrue(FirstRunHints.isDue(context, mergeKey))
    }

    @Test
    fun aHintShowsOnceAndArmingAgainDoesNotBringItBack() {
        FirstRunHints.arm(context)
        FirstRunHints.markSeen(context, mergeKey)

        assertFalse(FirstRunHints.isDue(context, mergeKey))
        // Seeing one tool's introduction leaves the others due.
        assertTrue(FirstRunHints.isDue(context, FirstRunHints.toolKey("split")))

        FirstRunHints.arm(context)
        assertFalse(FirstRunHints.isDue(context, mergeKey))
    }

    @Test
    fun showIntroductionsAgainBringsEveryHintBackEvenForUpdatedInstalls() {
        FirstRunHints.arm(context)
        FirstRunHints.markSeen(context, mergeKey)
        FirstRunHints.markSeen(context, FirstRunHints.HOME_TOUR)

        FirstRunHints.reset(context)

        assertTrue(FirstRunHints.isDue(context, mergeKey))
        assertTrue(FirstRunHints.isDue(context, FirstRunHints.HOME_TOUR))
    }

    @Test
    fun everyToolTileHasAnIntroduction() {
        val missing = TOOL_SECTIONS.flatMap { it.tools }.filter { toolIntro(it.id) == null }.map { it.id }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun theIntroductionAsksForWhatThePickerOpens() {
        // The first step names what the picker that follows will ask for.
        assertEquals(HintInput.Pdfs, toolIntro("merge")?.input)
        assertEquals(HintInput.Images, toolIntro("img2pdf")?.input)
        assertEquals(HintInput.Images, toolIntro("convert_img")?.input)
        assertEquals(HintInput.Image, toolIntro("crop_img")?.input)
        assertEquals(HintInput.Office, toolIntro("office")?.input)
        assertEquals(HintInput.Pdf, toolIntro("compress")?.input)
    }
}
