package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.tools.OrganizerPageCard
import com.example.scanpro.feature.tools.OrganizerTopBar
import com.example.scanpro.feature.tools.PdfToolsScreen
import com.example.scanpro.feature.tools.ReorderablePageGrid
import com.example.scanpro.feature.tools.ThumbnailPageModel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ToolsScreenshotTest(private val dark: Boolean) {
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
        assumeTrue(System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
    }

    @Test
    fun organiser() = shoot("20-organiser") { Organiser("organize", "Organise pages", "Save (7)") }

    @Test
    fun organiserExtract() = shoot("21-organiser-extract") { Organiser("extract", "Extract pages", "Extract (2)") }

    @Test
    fun organiserRotate() = shoot("22-organiser-rotate") { Organiser("rotate", "Rotate pages", "Save PDF") }

    @Test
    fun landing() = shoot("23-tools-landing") { PdfToolsScreen(recentPdfs = emptyList()) }

    @Test
    fun pdfInfo() = shoot("24-tools-pdf-info") {
        // The dialog's content on a page of its own: under Robolectric a text field inside a Dialog never lets
        // Compose go idle, so the dialog window itself cannot be captured here.
        val info = com.example.scanpro.imaging.PdfInfo(
            title = "Quarterly report", author = "A. Author", creator = "Writer 7", producer = "Some PDF Library 2.1",
            created = 1_700_000_000_000L, pageCount = 12, version = "1.7", sizeBytes = 1_234_567, hasXmp = true
        )
        Surface(Modifier.fillMaxSize()) {
            com.example.scanpro.feature.tools.PdfInfoContent(
                info = info, title = info.title, author = info.author, subject = "", keywords = "",
                onTitle = {}, onAuthor = {}, onSubject = {}, onKeywords = {}, onRemoveAll = {},
                modifier = Modifier.padding(24.dp)
            )
        }
    }

    @Test
    fun blankPages() = shoot("25-tools-blank-pages") {
        PdfToolsScreen(recentPdfs = emptyList(), overlayContent = {
            com.example.scanpro.feature.tools.BlankPagesDialog(
                found = com.example.scanpro.feature.tools.BlankPagesFound(android.net.Uri.EMPTY, 12, listOf(2, 6, 11)),
                onDismiss = {}, onRemove = {}
            )
        })
    }

    /** The organiser as the Tools tab shows it: the editor owns the screen, so no landing title or bottom bar. */
    @Composable
    private fun Organiser(mode: String, title: String, save: String) {
        val pages = (0 until 7).map { ThumbnailPageModel(it, rotation = if (mode == "rotate" && it == 1) 90 else 0, selected = mode != "extract" || it in setOf(0, 3)) }
            .map { if (mode == "organize" && it.index == 2) it.copy(selected = false) else it }
        PdfToolsScreen(recentPdfs = emptyList(), editorOpen = true, bottomBar = { Text("BOTTOM BAR SHOULD NOT SHOW") }, overlayContent = {
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
                OrganizerTopBar(
                    title = title, onClose = {}, canUndo = true, onUndo = {}, menuEnabled = true, menuOpen = false,
                    onMenuOpenChange = {}, menu = {}, saveLabel = save, saveEnabled = true, onSave = {}
                )
            }) { pad ->
                ReorderablePageGrid(items = pages, key = { it.index }, onMove = { _, _ -> }, modifier = Modifier.fillMaxSize().padding(pad)) { item, index, dragging ->
                    OrganizerPageCard(item = item, position = index + 1, thumbnail = page(item.index), mode = mode, isDragging = dragging,
                        onClick = {}, onPreview = {}, onMore = {})
                }
            }
        })
    }

    /** A drawn page: a heading and text lines reaching the corners, where the old controls covered it. */
    private fun page(n: Int): Bitmap {
        val bmp = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(252, 251, 247))
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(40, 40, 50); textSize = 26f; isFakeBoldText = true }
        c.drawText("PAGE ${n + 1} HEADING", 14f, 34f, ink)
        val line = android.graphics.Paint().apply { color = android.graphics.Color.rgb(150, 150, 165) }
        var y = 60f
        while (y < 470f) { c.drawRect(14f, y, 346f - (y.toInt() * 7 % 90), y + 8f, line); y += 22f }
        return bmp
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            Thread.sleep(150)
        }
        val image: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
