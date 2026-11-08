package com.example.scanpro.feature.library

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.navigation.ScanProDestinations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks that the Home dashboard's search pill remains fully displayed at the top (R38)
 * even when the "Did you know?" tip card enters late at the bottom and across tab switches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class HomeScrollTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val doc = DocumentSummary(
        id = "d1",
        title = "Important Document",
        createdAt = 1000L,
        updatedAt = 1000L,
        pdfPath = "",
        pageCount = 1,
        coverPath = null,
        sizeBytes = 1024L
    )
    private val overview = LibraryOverview(
        recent = listOf(doc),
        documentCount = 1,
        favoriteCount = 0,
        folders = emptyList()
    )

    @Test
    fun `search launcher stays visible when tips card appears`() {
        var showTips by mutableStateOf(false)
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                LibraryScreenForTest(overview = overview, showTips = showTips)
            }
        }

        val searchHint = app.getString(R.string.lib_home_search_launcher)
        compose.onNodeWithText(searchHint).assertIsDisplayed()

        // Emulate late DataStore arrival of tips card
        showTips = true
        compose.waitForIdle()

        // Search launcher must still be displayed at the top and not pushed off-screen
        compose.onNodeWithText(searchHint).assertIsDisplayed()

        // Scrolling to the bottom reveals the tips card
        compose.onNodeWithText(app.getString(R.string.tip_eyebrow).uppercase()).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `search launcher remains visible when returning from files tab`() {
        var mode by mutableStateOf(ScanProDestinations.HOME)
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                LibraryScreen(
                    mode = mode,
                    overview = overview,
                    items = null,
                    folder = "",
                    favoritesOnly = false,
                    sort = LibrarySort.UPDATED,
                    onFolderChange = {},
                    onFavoritesOnlyChange = {},
                    trashCount = 0,
                    query = "",
                    busy = false,
                    busyLabel = null,
                    snackbarHostState = remember { SnackbarHostState() },
                    onQueryChange = {},
                    onOpenDocument = {},
                    onStartScan = {},
                    onImportImages = {},
                    onImportPdf = {},
                    onTrash = {},
                    onSearch = {},
                    onBack = {},
                    onBulkDelete = { false },
                    onShareSelected = {},
                    onRenameFolder = { _, _ -> },
                    onTab = {},
                    showTips = true
                )
            }
        }

        val searchHint = app.getString(R.string.lib_home_search_launcher)
        compose.onNodeWithText(searchHint).assertIsDisplayed()

        // Switch to Files tab
        mode = ScanProDestinations.FILES
        compose.waitForIdle()

        // Switch back to Home tab
        mode = ScanProDestinations.HOME
        compose.waitForIdle()

        // Search launcher must still be displayed at the top
        compose.onNodeWithText(searchHint).assertIsDisplayed()
    }
}
