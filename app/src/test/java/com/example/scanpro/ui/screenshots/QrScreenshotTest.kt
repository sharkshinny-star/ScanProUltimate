package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ScanHistoryEntry
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.qr.CodeStyle
import com.example.scanpro.feature.qr.QrEncoder
import com.example.scanpro.feature.qr.QrForm
import com.example.scanpro.feature.qr.QrGeneratorScreen
import com.example.scanpro.feature.qr.QrHistoryScreen
import com.example.scanpro.feature.qr.QrKind
import com.example.scanpro.feature.qr.toBitmap
import com.example.scanpro.scanner.LinkPreviewDialog
import com.example.scanpro.scanner.LinkSafety
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
 * Renders the QR generator, the scan history and the link preview to PNG files, like [ScreenScreenshotTest].
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*QrScreenshotTest*' -Pscanpro.screenshots=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class QrScreenshotTest(private val dark: Boolean) {
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

    @Composable
    private fun Generator(kind: QrKind, values: Map<String, String>, styleOpen: Boolean = false, style: CodeStyle = CodeStyle()) =
        QrGeneratorScreen(
            onBack = {},
            render = { content, format, s -> QrEncoder.encode(content, format, s)?.toBitmap() },
            canSaveToGallery = true,
            onOutput = { _, _ -> },
            initialKind = kind,
            initialValues = values,
            initialStyleOpen = styleOpen,
            initialStyle = style
        )

    @Test
    fun generatorLink() = shoot("qr-01-generator-link") {
        Generator(QrKind.LINK, mapOf(QrForm.LINK to "example.com/menu?table=4"))
    }

    @Test
    fun generatorEvent() = shoot("qr-02-generator-event") {
        Generator(
            QrKind.EVENT,
            mapOf(
                QrForm.EVENT_TITLE to "Team lunch",
                QrForm.START_DATE to "2026-11-20",
                QrForm.START_TIME to "12:30",
                QrForm.END_TIME to "11:00"
            )
        )
    }

    @Test
    fun generatorStyle() = shoot("qr-03-generator-style") {
        Generator(
            QrKind.TEXT,
            mapOf(QrForm.TEXT to "Hello from ScanPro"),
            styleOpen = true,
            style = CodeStyle(foreground = 0xFFFFD54A.toInt(), background = -1)
        )
    }

    @Test
    fun generatorEan() = shoot("qr-04-generator-ean") {
        Generator(QrKind.EAN_13, mapOf(QrForm.EAN to "400638133393"))
    }

    @Test
    fun generatorEanWrong() = shoot("qr-05-generator-ean-wrong") {
        Generator(QrKind.EAN_13, mapOf(QrForm.EAN to "4006381333932"))
    }

    @Test
    fun history() = shoot("qr-06-history") {
        val now = System.currentTimeMillis()
        QrHistoryScreen(
            entries = listOf(
                ScanHistoryEntry("https://example.com/menu", 256, 8, now - 60_000, favourite = true),
                ScanHistoryEntry("WIFI:T:WPA;S:Home Net;P:secret;;", 256, 9, now - 3_600_000, created = true),
                ScanHistoryEntry("4006381333931", 32, 5, now - 86_400_000)
            ),
            saveHistory = true,
            onSaveHistoryChange = {},
            onFavourite = { _, _ -> },
            onRemove = {},
            onClear = {},
            onBack = {},
            onExport = {}
        )
    }

    @Test
    fun linkPreview() = shootDialog("qr-07-link-preview") {
        LinkPreviewDialog(LinkSafety.check("http://mybank.com@bit.ly/3xYz")!!, onOpen = {}, onCopy = {}, onDismiss = {})
    }

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
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        settle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun shootDialog(name: String, content: @Composable () -> Unit) {
        compose.setContent { ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        settle()
        save(name, compose.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap())
    }
}
