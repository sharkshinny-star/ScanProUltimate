package com.example.scanpro.feature.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Opens the Settings main page and every Settings page, in light and dark, and saves each to app/build/screenshots
 * (settings_<theme>_<page>.png) for a person to look at. Fails if any page throws while composing or drawing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h2000dp-xhdpi")
class SettingsPagesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun everyPage(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mode = mutableStateOf(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        compose.setContent {
            ScanProTheme(themeMode = mode.value) {
                SettingsScreen(
                    pdfQuality = PdfQuality.BALANCED,
                    autoTitle = true,
                    appLock = false,
                    themeMode = mode.value,
                    dynamicColor = false,
                    documentCount = 24,
                    trashCount = 3,
                    storageBytes = 48_000_000L,
                    lastBackupAt = 1_760_000_000_000L,
                    snackbarHostState = SnackbarHostState(),
                    onPdfQualityChange = {},
                    onAutoTitleChange = {},
                    onAppLockChange = {},
                    onThemeModeChange = {},
                    notifications = NotificationSettings(canPost = false, systemSettingsIntent = { android.content.Intent() })
                )
            }
        }
        capture("settings_${theme}_main")
        SettingsPage.entries.forEach { page ->
            compose.onNodeWithText(context.getString(page.title)).performScrollTo().performClick()
            capture("settings_${theme}_${page.name.lowercase()}")
            compose.onNodeWithContentDescription(context.getString(R.string.common_back)).performClick()
            compose.waitForIdle()
        }
    }

    /** A search hit opens the page that holds it; a value row opens its choices with the explanation. */
    @Test
    fun searchOpensThePageAndChoicesOpenADialog() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent {
            ScanProTheme {
                SettingsScreen(
                    pdfQuality = PdfQuality.BALANCED, autoTitle = true, appLock = false, themeMode = ThemeMode.LIGHT,
                    dynamicColor = false, documentCount = 2, trashCount = 0, storageBytes = 1_000L,
                    snackbarHostState = SnackbarHostState(), onPdfQualityChange = {}, onAutoTitleChange = {},
                    onAppLockChange = {}, onThemeModeChange = {}
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("pattern")
        val title = context.getString(R.string.settings_name_pattern_title)
        compose.onNodeWithText(title).performClick()
        // The Scanning page is open: its top bar says so and the row is there.
        compose.onNodeWithText(context.getString(R.string.settings_section_scanning)).assertExists()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_review_mode_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_review_mode_subtitle)).assertExists()
        compose.waitForIdle()
        val dialog = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(File("build/screenshots").apply { mkdirs() }, "settings_light_review_mode_dialog.png").outputStream().use { dialog.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun everyPageLight() = everyPage(dark = false)

    @Test
    fun everyPageDark() = everyPage(dark = true)
}
