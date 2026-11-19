package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.tools.EditPdfWorkspace
import com.example.scanpro.feature.tools.EditTarget
import com.example.scanpro.feature.tools.EditorCalls
import com.example.scanpro.feature.tools.EditorSheet
import com.example.scanpro.feature.tools.EditorState
import com.example.scanpro.feature.tools.PageBox
import com.example.scanpro.imaging.PdfTextEditor
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
 * Edit PDF's workspace (F206) drawn with a made-up page: the main screen, each tab of the panel and each sheet, in
 * light and dark. Run with `-Pscanpro.screenshots=true`; the pictures go to `app/build/screenshots/edit-pdf-*.png`.
 * The page itself is a drawing here: on a phone it is the PDF as Android draws it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class EditPdfScreenshotTest(private val dark: Boolean) {
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

    /** The lines of the made-up page: text, left, baseline, size, bold. */
    private val lines = listOf(
        Line("NORTHSTAR", 48f, 70f, 9f, true), Line("CONFIDENTIAL", 470f, 70f, 7f, false),
        Line("Quarterly project update", 48f, 130f, 24f, true), Line("NOVEMBER 2026 / PRODUCT TEAM", 48f, 160f, 8f, false),
        Line("Executive summary", 48f, 210f, 14f, true),
        Line("This quarter, our focus was to improve the everyday experience.", 48f, 236f, 11f, false),
        Line("Our team delivered a faster, simpler PDF editing experience.", 48f, 290f, 11f, false),
        Line("The next milestone focuses on accessible text tools.", 48f, 312f, 11f, false),
        Line("Key highlights", 48f, 372f, 14f, true),
        Line("Reduced steps to edit text across devices", 64f, 400f, 10f, false),
        Line("Improved readability and accessibility", 64f, 424f, 10f, false),
        Line("Built flexible page layout controls", 64f, 448f, 10f, false)
    )

    private class Line(val text: String, val left: Float, val baseline: Float, val size: Float, val bold: Boolean)

    /** The page at two pixels a point, and the box of each of its lines in points. */
    private fun page(): Pair<Bitmap, List<PageBox>> {
        val bitmap = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val boxes = lines.mapIndexed { index, line ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = if (index == 0) android.graphics.Color.rgb(51, 68, 214) else android.graphics.Color.rgb(30, 42, 74)
                textSize = line.size * 2f
                isFakeBoldText = line.bold
            }
            canvas.drawText(line.text, line.left * 2f, line.baseline * 2f, paint)
            val width = paint.measureText(line.text) / 2f
            PageBox(EditTarget(0, index, 0), line.left, line.baseline - line.size * 0.9f, line.left + width, line.baseline + line.size * 0.25f, changed = index == 7)
        }
        return bitmap to boxes
    }

    private fun state(sheet: EditorSheet? = null, selected: Boolean = true, edit: PdfTextEditor.Edit? = null): EditorState {
        val (bitmap, boxes) = page()
        val line = lines[6]
        val current = edit ?: PdfTextEditor.Edit(0, 6, line.text, bold = true, italic = false, size = 18f, colour = 0xE5484D, wrap = true)
        val changes = listOf(
            current,
            PdfTextEditor.Edit(0, 7, "The next milestone focuses on accessible text tools and formatting.", bold = false, italic = false, size = 11f),
            PdfTextEditor.Edit(0, 3, "", bold = false, italic = false, size = 8f),
            PdfTextEditor.Edit(1, PdfTextEditor.NEW, "Paid in full on 19 November", bold = false, italic = true, size = 12f, x = 80f, y = 300f, id = 1)
        )
        return EditorState(
            fileName = "Quarterly Report.pdf", edits = changes, canUndo = true, canRedo = false, pageIndex = 0, pageCount = 12,
            page = bitmap, pageSize = 595f to 842f, boxes = boxes,
            selected = if (selected) EditTarget(0, 6, 0) else null, current = if (selected) current else null, originalColour = 0x1E2A4A,
            sheet = sheet,
            before = mapOf(EditTarget(0, 6, 0) to line.text, EditTarget(0, 7, 0) to lines[7].text, EditTarget(0, 3, 0) to lines[3].text),
            suggestedName = "Quarterly Report edited"
        )
    }

    @Test fun main() = shoot("01-main") { EditPdfWorkspace(state(), EditorCalls()) }
    @Test fun font() = shoot("02-font") { EditPdfWorkspace(state(EditorSheet.FONT), EditorCalls()) }
    @Test fun paragraph() = shoot("03-paragraph") { EditPdfWorkspace(state(EditorSheet.PARAGRAPH), EditorCalls()) }
    @Test fun colour() = shoot("04-colour") { EditPdfWorkspace(state(EditorSheet.COLOUR), EditorCalls()) }
    @Test fun typography() = shoot("05-typography") { EditPdfWorkspace(state(EditorSheet.TYPOGRAPHY), EditorCalls()) }
    @Test fun lists() = shoot("06-lists") { EditPdfWorkspace(state(EditorSheet.LISTS), EditorCalls()) }
    @Test fun position() = shoot("07-position") { EditPdfWorkspace(state(EditorSheet.POSITION), EditorCalls()) }
    @Test fun actions() = shoot("08-actions") { EditPdfWorkspace(state(EditorSheet.ACTIONS), EditorCalls()) }
    @Test fun styles() = shoot("09-styles") { EditPdfWorkspace(state(EditorSheet.STYLES), EditorCalls()) }
    @Test fun nothingSelected() = shoot("10-nothing-selected") { EditPdfWorkspace(state(selected = false), EditorCalls()) }
    @Test fun insertTab() = shoot("11-insert") { EditPdfWorkspace(state(), EditorCalls(), initialTab = 1) }
    @Test fun layoutTab() = shoot("12-layout") { EditPdfWorkspace(state(), EditorCalls(), initialTab = 2) }
    @Test fun reviewTab() = shoot("13-review") { EditPdfWorkspace(state(), EditorCalls(), initialTab = 3) }
    @Test fun text() = shoot("14-text") { EditPdfWorkspace(state(EditorSheet.TEXT), EditorCalls()) }
    @Test fun find() = shoot("15-find") { EditPdfWorkspace(state(EditorSheet.FIND), EditorCalls()) }
    @Test fun changes() = shoot("16-changes") { EditPdfWorkspace(state(EditorSheet.CHANGES), EditorCalls()) }
    @Test fun save() = shoot("17-save") { EditPdfWorkspace(state(EditorSheet.SAVE), EditorCalls()) }

    @Test
    @Config(qualifiers = "hi-w360dp-h800dp-xxhdpi")
    fun mainHindi() = shoot("18-main-hi") { EditPdfWorkspace(state(), EditorCalls()) }

    @Test
    @Config(qualifiers = "ta-w360dp-h800dp-xxhdpi")
    fun listsTamil() = shoot("19-lists-ta") { EditPdfWorkspace(state(EditorSheet.LISTS), EditorCalls()) }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        // No waiting for idle: a focused text field's cursor never lets Compose settle here.
        repeat(5) { compose.mainClock.advanceTimeBy(300); Thread.sleep(80) }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "edit-pdf-$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
