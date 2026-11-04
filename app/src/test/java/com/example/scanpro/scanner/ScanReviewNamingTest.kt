package com.example.scanpro.scanner

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The review screen's Name row: "Automatic" by default, the typed name once there is one, absent for a retake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScanReviewNamingTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val automatic = app.getString(R.string.scan_review_name_auto)
    private var name by mutableStateOf("")

    private val pages = mutableStateListOf(Uri.parse("file:///nowhere/a.jpg"))
    private val quads = mutableStateMapOf<String, CropQuad>()
    private val filters = mutableStateMapOf<String, PageFilter>()

    private fun show(nameable: Boolean = true) = compose.setContent {
        ScanProTheme(themeMode = ThemeMode.LIGHT) {
            ScanReviewScreen(
                pages = pages, quads = quads,
                filters = filters, defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {},
                index = 0, processing = false, onIndexChange = {}, onAdd = {}, onBack = {}, onDone = {},
                name = name, onNameChange = if (nameable) ({ name = it }) else null
            )
        }
    }

    // Typing into the name dialog is not tested here: under Robolectric, Compose never goes idle once any dialog with a
    // text field is open (a plain Material AlertDialog + OutlinedTextField hangs too), so that step needs a device.
    // The trimming and blank handling of the typed name are covered by ImportTitleTest.

    @Test
    fun `the row reads Automatic until there is a name, then shows the name`() {
        show()
        compose.onNodeWithText(automatic).assertExists()
        name = "Tax return 2026"
        compose.onNodeWithText("Tax return 2026").assertExists()
        compose.onNodeWithText(automatic).assertDoesNotExist()
    }

    @Test
    fun `no Name row when a page is shot again for a saved document`() {
        show(nameable = false)
        compose.onNodeWithText(automatic).assertDoesNotExist()
    }
}
