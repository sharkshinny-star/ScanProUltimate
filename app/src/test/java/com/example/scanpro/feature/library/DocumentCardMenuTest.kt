package com.example.scanpro.feature.library

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ⋮ quick menu on a document card runs the matching action for that one document. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class DocumentCardMenuTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val doc = DocumentSummary(
        id = "d1", title = "Lease agreement", createdAt = 1L, updatedAt = 1L, pdfPath = "", pageCount = 2,
        coverPath = null, sizeBytes = 1000L, isFavorite = true
    )
    private val calls = mutableListOf<String>()
    private val menu = DocumentCardMenu(
        onShare = { calls += "share" },
        onMoveToFolder = { calls += "move" },
        onToggleFavourite = { calls += "favourite" },
        onMoveToRecycleBin = { calls += "trash" }
    )

    private fun pick(grid: Boolean, label: Int) {
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) {
                if (grid) DocumentGridCard(doc, selected = false, onClick = {}, onLongClick = {}, menu = menu)
                else DocumentListCard(doc, selected = false, onClick = {}, onLongClick = {}, menu = menu)
            }
        }
        compose.onNodeWithContentDescription(app.getString(R.string.lib_cd_more_options)).performClick()
        compose.onNodeWithText(app.getString(label)).performClick()
    }

    @Test
    fun `list card share`() {
        pick(grid = false, R.string.doc_share_pdf)
        assertEquals(listOf("share"), calls)
    }

    @Test
    fun `grid card offers Remove from favourites for a favourite`() {
        pick(grid = true, R.string.lib_menu_remove_from_favorites)
        assertEquals(listOf("favourite"), calls)
    }

    @Test
    fun `grid card move to recycle bin`() {
        pick(grid = true, R.string.doc_move_to_recycle_bin)
        assertEquals(listOf("trash"), calls)
    }

    @Test
    fun `no menu while selecting`() {
        compose.setContent { ScanProTheme(themeMode = ThemeMode.LIGHT) { DocumentListCard(doc, selected = true, onClick = {}, onLongClick = {}, menu = null) } }
        compose.onNodeWithContentDescription(app.getString(R.string.lib_cd_more_options)).assertDoesNotExist()
    }
}
