package com.example.scanpro.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.library.LibraryScreenForTest
import com.example.scanpro.feature.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders Home and Settings on the JVM with Robolectric's native graphics, in light and dark and in several
 * colour themes. Each test fails if a screen throws while composing, measuring or drawing; the images land
 * in app/build/screenshots for a person to look at. There are no golden images to compare against yet:
 * fonts and anti-aliasing differ between machines, so pixel comparisons would need a pinned renderer.
 *
 * The plain Application keeps Hilt, WorkManager and notifications out of the way: these screens take
 * plain values and callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class ColourThemeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The home dashboard with a few documents, as it looks once something has been scanned. */
    @androidx.compose.runtime.Composable
    private fun Home(showTips: Boolean = true) {
        val now = 1_760_000_000_000L
        val titles = listOf("Lease agreement", "Receipt - Corner Cafe", "Passport", "Lecture notes")
        LibraryScreenForTest(
            overview = LibraryOverview(
                documentCount = 24,
                favoriteCount = 5,
                folders = listOf("Receipts", "Work"),
                recent = titles.mapIndexed { i, title ->
                    DocumentSummary(
                        id = "doc$i",
                        title = title,
                        createdAt = now - i * 86_400_000L,
                        updatedAt = now - i * 86_400_000L,
                        pdfPath = "",
                        pageCount = i + 1,
                        coverPath = null,
                        sizeBytes = 240_000L * (i + 1)
                    )
                }
            ),
            showTips = showTips
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xhdpi")
    fun homeLightAndDark() {
        val dark = mutableStateOf(false)
        val palette = mutableStateOf(AccentPalette.INDIGO)
        compose.setContent {
            ScanProTheme(themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT, palette = palette.value) { Home() }
        }
        capture("light_home_indigo")
        palette.value = AccentPalette.EMERALD
        capture("light_home_emerald")
        dark.value = true
        palette.value = AccentPalette.OCEAN
        capture("dark_home_ocean")
        compose.onNodeWithText("Next tip").performClick()
        capture("dark_home_ocean_next_tip")
    }

    @Test
    @Config(qualifiers = "w411dp-h1900dp-xhdpi")
    fun settingsAppearance() {
        val palette = mutableStateOf(AccentPalette.SUNSET)
        compose.setContent {
            ScanProTheme(palette = palette.value) {
                SettingsScreen(
                    pdfQuality = PdfQuality.BALANCED,
                    autoTitle = true,
                    appLock = false,
                    themeMode = ThemeMode.LIGHT,
                    dynamicColor = false,
                    documentCount = 24,
                    trashCount = 3,
                    storageBytes = 48_000_000L,
                    snackbarHostState = SnackbarHostState(),
                    onPdfQualityChange = {},
                    onAutoTitleChange = {},
                    onAppLockChange = {},
                    onThemeModeChange = {},
                    accentPalette = palette.value,
                    onAccentPaletteChange = { palette.value = it }
                )
            }
        }
        // F140: the main page lists the pages; the colour themes are on Appearance.
        capture("light_settings_main")
        compose.onNodeWithText("Appearance").performClick()
        capture("light_settings_sunset")
        compose.onNodeWithText("Ocean").performClick()
        capture("light_settings_ocean")
    }
}
