package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.phonepdfs.AccessExplainer
import com.example.scanpro.feature.phonepdfs.DefaultViewerSheet
import com.example.scanpro.feature.phonepdfs.ImportTipSheet
import com.example.scanpro.feature.phonepdfs.PhoneFile
import com.example.scanpro.feature.phonepdfs.PhoneFileActions
import com.example.scanpro.feature.phonepdfs.PhoneFileFilter
import com.example.scanpro.feature.phonepdfs.PhoneFiles
import com.example.scanpro.feature.phonepdfs.PhoneFilesAccessCard
import com.example.scanpro.feature.phonepdfs.PhoneFilesList
import com.example.scanpro.feature.phonepdfs.PhoneFilesState
import com.example.scanpro.feature.phonepdfs.PhoneFilesStrip
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Files on this phone, the default PDF viewer sheet and the import tip, rendered to PNG files like [ScreenScreenshotTest].
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*FilesAccessScreenshotTest*' -Pscanpro.screenshots=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class FilesAccessScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")

    @Before
    fun setUp() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
    }

    private val day = 86_400_000L
    private val now = 1_763_280_000_000L
    private fun file(name: String, folder: String, size: Long, daysAgo: Int) = PhoneFile(
        Uri.parse("content://media/external/file/${name.hashCode()}"), name, PhoneFiles.kindOf(null, name)!!,
        PhoneFiles.mimeForName(name), size, now - daysAgo * day, folder
    )

    private val files = listOf(
        file("Electricity bill November.pdf", "Download", 182_000, 0),
        file("IMG_20261114_101512.jpg", "DCIM/Camera", 3_400_000, 1),
        file("Rental agreement 2026.docx", "Download/WhatsApp Documents", 64_000, 2),
        file("Household budget.xlsx", "Documents", 28_000, 3),
        file("Team update.pptx", "Download", 2_100_000, 5),
        file("Train ticket.pdf", "Download", 96_000, 6),
        file("notes.txt", "Documents", 1_200, 9)
    )
    private val actions = PhoneFileActions({ _, _ -> }, {}, {})

    @Composable
    private fun List(state: PhoneFilesState) = PhoneFilesList(
        state = state, onRefresh = {}, onQueryChange = {}, onFilterChange = {}, onSortChange = {},
        onToggleSelection = {}, onClearSelection = {}, onOpen = {}, onFileAction = { _, _ -> }, onSelectionAction = {}
    )

    @Test fun explainer() = shoot("files-01-explainer") { AccessExplainer(onAllow = {}, onNotNow = {}) }

    @Test fun list() = shoot("files-02-list") { List(PhoneFilesState(hasAccess = true, all = files)) }

    /** The storage tiles above the list, as the Files tab shows them. */
    @Test fun overview() = shoot("files-08-overview") {
        PhoneFilesList(
            state = PhoneFilesState(hasAccess = true, all = files), onRefresh = {}, onQueryChange = {}, onFilterChange = {}, onSortChange = {},
            onToggleSelection = {}, onClearSelection = {}, onOpen = {}, onFileAction = { _, _ -> }, onSelectionAction = {}, processedCount = 12
        )
    }

    @Test fun listPdfFilter() = shoot("files-03-list-pdf") { List(PhoneFilesState(hasAccess = true, all = files, filter = PhoneFileFilter.PDF)) }

    @Test fun selection() = shoot("files-04-selection") {
        List(PhoneFilesState(hasAccess = true, all = files, selected = listOf(files[0].uri.toString(), files[5].uri.toString())))
    }

    @Test fun homeCardAndStrip() = shoot("files-05-home") {
        Column {
            PhoneFilesAccessCard(onAllow = {}, onDismiss = {}, modifier = Modifier.padding(16.dp))
            PhoneFilesStrip(PhoneFiles.newest(files), actions = actions, onSeeAll = {})
        }
    }

    @Test fun defaultViewerSheet() = shootDialog("files-06-default-viewer") { DefaultViewerSheet(onSetDefault = {}, onNotNow = {}) }

    @Test fun importTip() = shootDialog("files-07-import-tip") { ImportTipSheet(onImport = {}, onCancel = {}) }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            Thread.sleep(150)
        }
    }

    private fun save(name: String, image: Bitmap) {
        File(outDir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        settle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun shootDialog(name: String, content: @Composable () -> Unit) {
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        settle()
        save(name, compose.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap())
    }
}
