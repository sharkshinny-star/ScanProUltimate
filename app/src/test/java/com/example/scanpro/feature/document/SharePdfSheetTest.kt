package com.example.scanpro.feature.document

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ApplicationProvider
import android.text.format.Formatter
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Share PDF card (R28): page ticks, a size per quality, a size target that picks the quality, then Share. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SharePdfSheetTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun size(bytes: Long) = Formatter.formatShortFileSize(app, bytes)
    private val pages = (0 until 3).map { ScanPage("p$it", "d1", it, "", "") }
    private val estimates = mapOf(PdfQuality.HIGH to 6_000_000L, PdfQuality.BALANCED to 2_500_000L, PdfQuality.SMALL to 900_000L)
    private val estimated = mutableListOf<Set<String>>()
    private var shared: Triple<Set<String>, PdfQuality, Long?>? = null

    private fun show(sizes: Map<PdfQuality, Long>? = estimates) = compose.setContent {
        ScanProTheme(themeMode = ThemeMode.LIGHT) {
            SharePdfSheet(
                pages = pages, preferredQuality = PdfQuality.BALANCED, estimates = sizes, busy = false,
                onEstimate = { estimated += it }, onShare = { ids, q, max -> shared = Triple(ids, q, max) }, onDismiss = {}
            )
        }
    }

    /** The Share button, not the sheet title of the same name. */
    private fun shareButton() = compose.onNode(hasText("Share PDF") and hasClickAction())

    private fun capture(name: String) {
        val bitmap = compose.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `all pages ticked, a size per quality, estimates asked for the choice`() {
        show()
        compose.onNodeWithText("All pages").assertExists()
        compose.onNodeWithText("≈ ${size(2_500_000L)}").assertExists()
        compose.onNodeWithText("≈ ${size(900_000L)}").assertExists()
        assertEquals(listOf(pages.map { it.id }.toSet()), estimated)
        capture("share_pdf_card")
    }

    @Test
    fun `unticking a page narrows the choice and a size target picks the quality`() {
        show()
        compose.onNodeWithContentDescription("Page 2 of 3").performClick()
        compose.onNodeWithText("2 of 3 pages").assertExists()
        assertEquals(setOf("p0", "p2"), estimated.last())
        compose.onNodeWithText(size(5_000_000L)).performClick()
        shareButton().assertIsEnabled()
        shareButton().performClick()
        assertEquals(Triple(setOf("p0", "p2"), PdfQuality.BALANCED, 5_000_000L), shared)
    }

    @Test
    fun `nothing ticked disables Share and shows no sizes`() {
        show(sizes = null)
        pages.indices.forEach { compose.onNodeWithContentDescription("Page ${it + 1} of 3").performClick() }
        compose.onNodeWithText("0 of 3 pages").assertExists()
        shareButton().assertIsNotEnabled()
    }
}
