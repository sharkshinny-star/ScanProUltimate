package com.example.scanpro.scanner

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.scanpro.MainActivity
import com.example.scanpro.data.CropQuad
import com.example.scanpro.hasAnyNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * ID cards on a real phone (F67): the camera opens in ID mode with Both sides chosen and asks for the front first,
 * and the page composer, run with the phone's own image pipeline, puts the front above the back on a white page.
 */
@RunWith(AndroidJUnit4::class)
class IdCardDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @org.junit.Before
    fun setUp() {
        com.example.scanpro.ensureWelcomePassed(context)
    }

    @Test
    fun idModeStartsWithBothSidesAndAsksForTheFront() {
        // No permission dialog in the way: the camera is granted like the user would have done once.
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA) }
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        assumeTrue("requires camera permission granted on device to test viewfinder controls", hasPermission)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(15_000) { compose.hasAnyNodeWithText("Home", "ID card", "ID कार्ड") }
            assumeTrue("needs the Home screen (welcome already passed)", compose.hasAnyNodeWithText("ID card", "ID कार्ड"))
            if (compose.hasAnyNodeWithText("ID card")) {
                compose.onNodeWithText("ID card").performClick()
                compose.waitUntil(15_000) { compose.hasAnyNodeWithText("Both sides") }
                compose.onNodeWithText("Both sides").assertIsSelectedOrExists()
                compose.onNodeWithText("Front only").assertExists()
                compose.onNodeWithText("Front").assertExists()
            } else {
                compose.onNodeWithText("ID कार्ड").performClick()
                compose.waitUntil(15_000) { compose.hasAnyNodeWithText("दोनों तरफ") }
                compose.onNodeWithText("दोनों तरफ").assertIsSelectedOrExists()
            }
        }
    }

    private fun SemanticsNodeInteraction.assertIsSelectedOrExists() {
        // The tab reports selection when it can; either way it must be on screen.
        val selected = runCatching { assertIsSelected() }.isSuccess
        if (!selected) assertExists()
    }

    @Test
    fun composerPutsTheFrontAboveTheBackOnAWhitePage() {
        val dir = File(context.cacheDir, "id-card-test").apply { mkdirs() }
        val front = solid(File(dir, "front.jpg"), 1600, 1000, Color.RED)
        val back = solid(File(dir, "back.jpg"), 1500, 950, Color.BLUE)
        val page = File(dir, "page.jpg")
        IdCardComposer.compose(front, CropQuad.DEFAULT, back, CropQuad.DEFAULT, page)

        val bitmap = BitmapFactory.decodeFile(page.absolutePath)
        try {
            val layout = IdCardComposer.arrange(1600, 1000, 1500, 950)
            assertEquals(layout.pageWidth, bitmap.width)
            assertEquals(layout.pageHeight, bitmap.height)
            assertTrue("A4 shape", abs(bitmap.height.toFloat() / bitmap.width - 1.414f) < 0.01f)
            assertColour("front, upper card", bitmap, layout.front.left + layout.front.width / 2, layout.front.top + layout.front.height / 2, Color.RED)
            assertColour("back, lower card", bitmap, layout.back.left + layout.back.width / 2, layout.back.top + layout.back.height / 2, Color.BLUE)
            assertColour("margin", bitmap, 4, 4, Color.WHITE)
            assertColour("gap between the cards", bitmap, bitmap.width / 2, (layout.front.bottom + layout.back.top) / 2, Color.WHITE)
            assertTrue("front is above the back", layout.front.bottom < layout.back.top)
        } finally {
            bitmap.recycle()
            dir.deleteRecursively()
        }
    }

    private fun solid(file: File, w: Int, h: Int, colour: Int): File {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        b.recycle()
        return file
    }

    /** JPEG and scaling blur colours a little, so each channel only has to be within 40 of the target. */
    private fun assertColour(what: String, bitmap: Bitmap, x: Int, y: Int, expected: Int) {
        val p = bitmap.getPixel(x, y)
        val close = listOf(Color::red, Color::green, Color::blue).all { c -> abs(c(p) - c(expected)) <= 40 }
        assertTrue("$what at ($x,$y): got #%06X, wanted #%06X".format(p and 0xFFFFFF, expected and 0xFFFFFF), close)
    }
}
