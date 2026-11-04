package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanPage
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.document.DocumentDetailScreen
import com.example.scanpro.feature.kept.KeptScansScreen
import com.example.scanpro.feature.library.LibraryScreen
import com.example.scanpro.feature.lock.LockScreen
import com.example.scanpro.feature.settings.SettingsScreen
import com.example.scanpro.feature.trash.TrashScreen
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.scanner.ScanReviewScreen
import com.example.scanpro.ui.WelcomeScreen
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import com.example.scanpro.work.KeptScan
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Compose screens with sample documents to PNG files, for design review (for example in Figma)
 * and for spotting layout regressions by eye. No device: Robolectric draws with the host's native graphics.
 *
 * Off by default because it is slow. Run with:
 *   ./gradlew :app:testDebugUnitTest --tests '*ScreenScreenshotTest*' -Pscanpro.screenshots=true
 * Output: app/build/screenshots/<screen>-<light|dark>.png, 1080 x 2400 px (360 x 800 dp at 3x).
 *
 * Screens that need CameraX, PdfRenderer or a Hilt view model (camera, PDF viewer, PDF tools) are not covered.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts Hilt and WorkManager, which these stateless screens don't need.
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScreenScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    // The v1 rule on purpose: under the v2 rule's StandardTestDispatcher the Files grid never received its first
    // page from Paging (the list layout did), so the grid rendered empty.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")
    private lateinit var samples: Samples

    @Before
    fun setUp() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
        samples = Samples(File(outDir, "sample-pages").apply { mkdirs() })
    }

    @Test
    fun welcome() = shoot("01-welcome") { WelcomeScreen(onGetStarted = {}) }

    @Test
    fun home() = shoot("02-home") { Library(ScanProDestinations.HOME) }

    @Test
    fun files() = shoot("03-files") { Library(ScanProDestinations.FILES) }

    @Test
    fun filesList() = shoot("03b-files-list") { Library(ScanProDestinations.FILES, grid = false) }

    @Test
    fun search() = shoot("04-search") { Library(ScanProDestinations.SEARCH, query = "invoice") }

    @Test
    fun emptyHome() = shoot("05-home-empty") {
        LibraryScreen(
            mode = ScanProDestinations.HOME, overview = LibraryOverview(), items = null, folder = "",
            favoritesOnly = false, sort = LibrarySort.UPDATED, onFolderChange = {}, onFavoritesOnlyChange = {},
            trashCount = 0, query = "", busy = false, busyLabel = null, snackbarHostState = SnackbarHostState(),
            onQueryChange = {}, onOpenDocument = {}, onStartScan = {}, onImportImages = {}, onImportPdf = {},
            onTrash = {}, onSearch = {}, onBack = {}, onBulkDelete = { false }, onShareSelected = {},
            onRenameFolder = { _, _ -> }, onTab = {}
        )
    }

    @Test
    fun document() = shoot("06-document") {
        val doc = samples.document
        DocumentDetailScreen(
            document = doc, otherDocuments = samples.summaries.drop(1), busy = false, busyLabel = null,
            snackbarHostState = SnackbarHostState(), onBack = {}, onRename = {}, onTags = {}, onFolder = {},
            onToggleFavorite = {}, onPdfQuality = {}, onPdfPageSize = {}, onSearchablePdf = {}, onPageNumbers = {},
            onScanMode = {}, onApplyFilter = { _, _ -> }, onRotate = {}, onCrop = { _, _ -> },
            onAdjustments = { _, _, _ -> }, onResetPage = {}, onMove = { _, _ -> }, onDuplicatePage = {},
            onExtractPage = {}, onSplitBook = { _, _ -> }, onDeletePage = {}, onSign = { _, _, _, _ -> },
            onTextAnnotation = { _, _ -> }, onHighlight = {}, onRedact = {}, onUndoAnnotation = {}, onWatermark = {},
            onLoadMarkupBase = { File(doc.pages.first().imagePath) }, onApplyMarkup = { _, _ -> },
            onAppendImages = {}, onMergeWith = {}, onReOcr = {}, onUpdateOcrText = { _, _ -> }, onOcrScript = {},
            onExportIdCopy = {}, onExportLongImage = {}, onExportOcrTextFile = {}, onSplitDocument = {},
            onExportProtectedPdf = {}, onExportEncryptedBackup = {}, onWithFreshPdf = { _, _ -> },
            onDeleteDocument = {}
        )
    }

    @Test
    fun scanReview() = shoot("07-scan-review") { ScanReview() }

    // A phone on its side: the page sits on the left and the controls scroll in a column on the right.
    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xxhdpi")
    fun scanReviewLandscape() = shoot("07b-scan-review-landscape") { ScanReview() }

    @Composable
    private fun ScanReview() {
        val pages = remember { mutableStateListOf(*samples.pageFiles.take(3).map { it.toUri() }.toTypedArray()) }
        val quads = remember { mutableStateMapOf<String, CropQuad>() }
        val filters = remember { mutableStateMapOf<String, PageFilter>() }
        ScanReviewScreen(
            pages = pages, quads = quads, filters = filters,
            defaultFilter = PageFilter.ORIGINAL, onDefaultFilterChange = {}, index = 0, processing = false,
            onIndexChange = {}, onAdd = {}, onBack = {}, onDone = {}, onRetake = {}
        )
    }

    @Test
    fun settings() = shoot("08-settings") {
        SettingsScreen(
            pdfQuality = PdfQuality.BALANCED, autoTitle = true, appLock = true,
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false,
            documentCount = samples.summaries.size, trashCount = 2, storageBytes = 48_300_000L,
            snackbarHostState = SnackbarHostState(), onPdfQualityChange = {}, onAutoTitleChange = {},
            onAppLockChange = {}, onThemeModeChange = {}
        )
    }

    @Test
    fun trash() = shoot("09-trash") {
        TrashScreen(
            documents = samples.summaries.take(2).map { it.copy(isTrashed = true, trashedAt = NOW - 3 * DAY) },
            busy = false, snackbarHostState = SnackbarHostState(), onBack = {}, onRestore = {},
            onDeleteForever = {}, onEmptyTrash = {}
        )
    }

    @Test
    fun keptScans() = shoot("10-unsaved-scans") {
        KeptScansScreen(
            scans = listOf(
                KeptScan("k1", samples.pageFiles.take(3), ScanMode.DOCUMENT, NOW - 2 * DAY, "Not enough storage"),
                KeptScan("k2", samples.pageFiles.take(1), ScanMode.RECEIPT, NOW - 9 * DAY, null)
            ),
            snackbarHostState = SnackbarHostState(), onBack = {}, onRetry = {}, onShare = {}, onDiscard = {}
        )
    }

    @Test
    fun lock() = shoot("11-lock") { LockScreen(onUnlock = {}, snackbarHostState = SnackbarHostState()) }

    @Composable
    private fun Library(mode: String, query: String = "", grid: Boolean = true) {
        val list = if (query.isEmpty()) samples.summaries else samples.summaries.filter { it.title.contains(query, true) }
        // Remembered: a new flow on every recomposition restarts paging, and the list never leaves "loading".
        val flow = remember(list) { flowOf(PagingData.from(list)) }
        val items = if (mode == ScanProDestinations.HOME) null else flow.collectAsLazyPagingItems()
        LibraryScreen(
            mode = mode, overview = samples.overview, items = items, folder = "", favoritesOnly = false,
            sort = LibrarySort.UPDATED, onFolderChange = {}, onFavoritesOnlyChange = {}, trashCount = 2,
            query = query, busy = false, busyLabel = null, snackbarHostState = SnackbarHostState(),
            onQueryChange = {}, onOpenDocument = {}, onStartScan = {}, onImportImages = {}, onImportPdf = {},
            onTrash = {}, onSearch = {}, onBack = {}, onBulkDelete = { false }, onShareSelected = {},
            onRenameFolder = { _, _ -> }, onTab = {}, savedGrid = grid, keptScanCount = if (mode == ScanProDestinations.HOME) 1 else 0
        )
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
        }
        // Thumbnails decode on the IO dispatcher and entrance animations run on the clock; let both finish.
        // Paging also hands its first page over through the test dispatcher, so give it several rounds.
        repeat(12) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            Thread.sleep(200)
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private const val DAY = 24L * 60 * 60 * 1000
private val NOW = System.currentTimeMillis()

/** Sample documents with drawn page images, so cards and thumbnails look like a used library. */
private class Samples(dir: File) {
    private val specs = listOf(
        Triple("Acme Supplies invoice 4471", "Work", ScanMode.DOCUMENT),
        Triple("Lease agreement, flat 3B", "Home", ScanMode.DOCUMENT),
        Triple("Blue Bottle receipt", "Receipts", ScanMode.RECEIPT),
        Triple("Passport copy", "Personal", ScanMode.ID_CARD),
        Triple("Sprint planning whiteboard", "Work", ScanMode.WHITEBOARD),
        Triple("Chapter 4, field notes", "Study", ScanMode.BOOK)
    )

    val pageFiles: List<File> = specs.mapIndexed { i, (title, _, mode) ->
        File(dir, "page$i.jpg").also { if (!it.exists()) drawPage(it, title, mode) }
    }

    val summaries: List<DocumentSummary> = specs.mapIndexed { i, (title, folder, mode) ->
        DocumentSummary(
            id = "doc$i", title = title, createdAt = NOW - i * 2 * DAY, updatedAt = NOW - i * 2 * DAY,
            pdfPath = "", pageCount = listOf(2, 6, 1, 1, 3, 12)[i], coverPath = pageFiles[i].path,
            sizeBytes = listOf(412_000L, 1_900_000L, 96_000L, 210_000L, 1_200_000L, 3_400_000L)[i],
            isFavorite = i == 0 || i == 3, folderName = folder, scanMode = mode
        )
    }

    val document = ScanDocument(
        id = "doc0", title = summaries[0].title, createdAt = summaries[0].createdAt, updatedAt = summaries[0].updatedAt,
        pdfPath = File(dir, "doc0.pdf").path, sizeBytes = summaries[0].sizeBytes, isFavorite = true, folderName = "Work",
        tags = listOf("finance", "q4"),
        pages = listOf(0, 2).mapIndexed { pos, i ->
            ScanPage(
                id = "p$pos", documentId = "doc0", position = pos, sourcePath = pageFiles[i].path, imagePath = pageFiles[i].path,
                ocrText = if (pos == 0) "ACME SUPPLIES LTD\nInvoice INV-4471\nDate: 14 Oct 2026\nDue: 13 Nov 2026\n" +
                    "Bill to: Northwind Studio, accounts@northwind.example, +1 415 555 0142\nTotal due: $189.50" else ""
            )
        }
    )

    val overview = LibraryOverview(
        documentCount = summaries.size, favoriteCount = 2,
        folders = summaries.map { it.folderName }.distinct(), recent = summaries.take(4)
    )

    private fun drawPage(file: File, title: String, mode: ScanMode) {
        val (w, h) = if (mode == ScanMode.RECEIPT) 500 to 1100 else if (mode == ScanMode.ID_CARD) 1000 to 640 else 850 to 1100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (mode == ScanMode.WHITEBOARD) Color.rgb(246, 247, 245) else Color.rgb(251, 250, 246))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(43, 43, 51) }
        val head = Paint(ink).apply { textSize = w / 16f; typeface = Typeface.DEFAULT_BOLD }
        val pad = w * 0.1f
        c.drawText(title.uppercase().take(22), pad, h * 0.1f, head)
        val line = Paint(ink).apply { color = Color.rgb(170, 170, 180) }
        var y = h * 0.17f
        var n = 0
        while (y < h * 0.9f) {
            val len = 0.45f + ((n * 37) % 45) / 100f
            c.drawRect(pad, y, pad + (w - 2 * pad) * len, y + h * 0.012f, line)
            y += h * 0.045f
            n++
            if (n == 5 && mode == ScanMode.DOCUMENT) {
                c.drawRect(pad, y, w - pad, y + h * 0.12f, Paint().apply { color = Color.rgb(226, 228, 236) })
                y += h * 0.16f
            }
        }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }
}
