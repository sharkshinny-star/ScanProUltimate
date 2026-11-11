package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.example.scanpro.imaging.ImageCompressConfig
import com.example.scanpro.imaging.ImageMetadata
import com.example.scanpro.imaging.ImageResizeConfig
import com.example.scanpro.imaging.ImageResizeFit
import com.example.scanpro.imaging.ImageToolsEngine
import com.example.scanpro.imaging.PageNumberFormat
import com.example.scanpro.imaging.PageNumberPosition
import com.example.scanpro.imaging.PageNumbersConfig
import com.example.scanpro.imaging.WatermarkConfig
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.DarkChrome
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.displayedKiloByte
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.document.CropEditorDialog
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PageRanges
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfPreviewSession
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SplitMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfToolCard(
    val id: String,
    /** Full tool name: read out by screen readers and shown in the "Use with a tool" menu. */
    @StringRes val title: Int,
    val icon: ImageVector,
    /** Colour family of the icon tile, so related tools share a hue. */
    val tone: TileTone = TileTone.Indigo,
    /** Short label under the tile; the full [title] when no shorter word fits. */
    @StringRes val label: Int = title
)

internal class ToolSection(val id: String, @StringRes val title: Int, val tools: List<PdfToolCard>)

/** Tiles grouped the way the leading scanners do: what you do to a PDF, what you turn it into, how you lock it. */
internal val TOOL_SECTIONS = listOf(
    ToolSection(
        "edit", R.string.tools_section_edit, listOf(
            PdfToolCard("merge", R.string.tools_card_merge, Icons.Default.Merge, TileTone.Indigo),
            PdfToolCard("split", R.string.tools_card_split, Icons.AutoMirrored.Filled.CallSplit, TileTone.Indigo),
            PdfToolCard("organize", R.string.tools_card_organize, Icons.Default.Description, TileTone.Sky, label = R.string.tools_tile_organize),
            PdfToolCard("rotate", R.string.tools_card_rotate, Icons.AutoMirrored.Filled.RotateRight, TileTone.Sky, label = R.string.tools_tile_rotate),
            PdfToolCard("extract", R.string.tools_card_extract, Icons.AutoMirrored.Filled.DriveFileMove, TileTone.Indigo, label = R.string.tools_tile_extract),
            PdfToolCard("compress", R.string.tools_card_compress, Icons.Default.Compress, TileTone.Amber),
            PdfToolCard("page_numbers", R.string.tools_card_page_numbers, Icons.Default.FormatListNumbered, TileTone.Sky, label = R.string.tools_tile_page_numbers),
            PdfToolCard("watermark", R.string.tools_card_watermark, Icons.AutoMirrored.Filled.BrandingWatermark, TileTone.Rose, label = R.string.tools_tile_watermark)
        )
    ),
    ToolSection(
        "convert", R.string.tools_section_convert, listOf(
            PdfToolCard("img2pdf", R.string.tools_images_to_pdf, Icons.Default.Image, TileTone.Coral),
            PdfToolCard("office", R.string.tools_card_office, Icons.AutoMirrored.Filled.Article, TileTone.Sky, label = R.string.tools_tile_office),
            PdfToolCard("ocr", R.string.tools_card_ocr, Icons.Default.FindInPage, TileTone.Teal),
            PdfToolCard("fill", R.string.tools_card_fill, Icons.Default.EditNote, TileTone.Violet),
            PdfToolCard("pdf2img", R.string.tools_card_pdf2img, Icons.Default.PictureAsPdf, TileTone.Coral),
            PdfToolCard("pdf2text", R.string.tools_card_pdf2text, Icons.Default.TextFields, TileTone.Teal, label = R.string.tools_tile_pdf2text)
        )
    ),
    ToolSection(
        "secure", R.string.tools_section_secure, listOf(
            PdfToolCard("protect", R.string.tools_card_protect, Icons.Default.Lock, TileTone.Rose),
            PdfToolCard("unlock", R.string.tools_card_unlock, Icons.Default.LockOpen, TileTone.Rose)
        )
    ),
    ToolSection(
        "image", R.string.tools_section_image, listOf(
            PdfToolCard("crop_img", R.string.tools_card_crop_img, Icons.Default.Crop, TileTone.Coral, label = R.string.tools_tile_crop_img),
            PdfToolCard("compress_img", R.string.tools_card_compress_img, Icons.Default.Compress, TileTone.Amber, label = R.string.tools_tile_compress_img),
            PdfToolCard("convert_img", R.string.tools_card_convert_img, Icons.Default.SyncAlt, TileTone.Teal, label = R.string.tools_tile_convert_img),
            PdfToolCard("resize_img", R.string.tools_card_resize_img, Icons.Default.AspectRatio, TileTone.Sky, label = R.string.tools_tile_resize_img)
        )
    )
)

/** What the Office to PDF picker offers; the converter checks the file itself too. */
private val OFFICE_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-word.document.macroenabled.12",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel.sheet.macroenabled.12",
    "application/vnd.ms-excel",
    "text/plain", "text/csv", "text/comma-separated-values", "application/csv",
    "text/markdown", "text/html", "application/xhtml+xml",
    "multipart/related", "message/rfc822", "application/x-mimearchive"
)

internal val VIEWER_TOOL = PdfToolCard("viewer", R.string.tools_card_viewer, Icons.Default.Visibility, TileTone.Slate)

/** Every tool, for the "Use with a tool" menu on a recent PDF. */
private val PDF_TOOLS = TOOL_SECTIONS.filter { it.id != "image" }.flatMap { it.tools } + VIEWER_TOOL

private data class ThumbnailPageModel(
    val index: Int,
    val rotation: Int = 0,
    val selected: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onOpenPdfViewer: (Uri, String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: PdfToolsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles { file ->
                file.isDirectory && (file.name.startsWith("img2pdf_order_") || file.name.startsWith("img2pdf_"))
            }?.forEach { it.deleteRecursively() }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val pdfDocumentTitle = stringResource(R.string.tools_pdf_document)

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val lastResult by viewModel.result.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val unlockNotice by viewModel.unlockNotice.collectAsStateWithLifecycle()
    val passwordNeeded by viewModel.passwordNeeded.collectAsStateWithLifecycle()
    val wrongPassword by viewModel.wrongPassword.collectAsStateWithLifecycle()
    val mergePassword by viewModel.mergePassword.collectAsStateWithLifecycle()
    val recentPdfs by viewModel.recentPdfs.collectAsStateWithLifecycle()
    val recentScope = rememberCoroutineScope()
    val formSession by viewModel.form.collectAsStateWithLifecycle()
    var showOcrDialog by rememberSaveable { mutableStateOf(false) }

    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImageUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var showCompressDialog by rememberSaveable { mutableStateOf(false) }
    var showCompressImageDialog by rememberSaveable { mutableStateOf(false) }
    var showConvertImageDialog by rememberSaveable { mutableStateOf(false) }
    var showResizeImageDialog by rememberSaveable { mutableStateOf(false) }
    var showPdfToImagesDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showSplitDialog by rememberSaveable { mutableStateOf(false) }
    var showOrganizerSheet by rememberSaveable { mutableStateOf(false) }
    var showWatermarkDialog by rememberSaveable { mutableStateOf(false) }
    var showPageNumbersDialog by rememberSaveable { mutableStateOf(false) }

    var activeCropSourcePath by rememberSaveable { mutableStateOf<String?>(null) }
    var activeCropUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var onCropFinishedCallback by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    // Saved across a rotation, unlike the callback itself: tells that this crop belongs to Images to PDF.
    var cropNeedsCallback by rememberSaveable { mutableStateOf(false) }
    val cropScope = rememberCoroutineScope()

    // Read here, where it follows a language change, not with context.getString inside the launcher.
    val cropFailedMessage = stringResource(R.string.tools_err_crop_img)
    fun launchCropForUri(targetUri: Uri, onCropped: ((Uri) -> Unit)? = null) {
        cropScope.launch {
            // An unreadable, cloud-only or revoked picture must say so, not crash the app or do nothing.
            val stagedFile = try {
                withContext(Dispatchers.IO) {
                    PdfToolsEngine.copyUriToTemp(context, targetUri, "crop_staged_${UUID.randomUUID()}.jpg")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (stagedFile != null && stagedFile.length() > 0) {
                activeCropUri = targetUri
                cropNeedsCallback = onCropped != null
                onCropFinishedCallback = onCropped
                activeCropSourcePath = stagedFile.absolutePath
            } else {
                stagedFile?.delete()
                snackbarHostState.showSnackbar(cropFailedMessage)
            }
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }
    LaunchedEffect(passwordNeeded) {
        passwordNeeded?.let {
            pendingUri = it
            selectedTool = "unlock"
            showPasswordDialog = true
            viewModel.clearPasswordNeeded()
        }
    }

    var saveSourceFile by remember { mutableStateOf<File?>(null) }
    fun saveTo(destUri: Uri?) {
        val file = saveSourceFile ?: return
        if (destUri == null) return
        viewModel.saveTo(file, destUri)
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val saveJpegLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { saveTo(it) }
    val savePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { saveTo(it) }
    val saveWebpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { saveTo(it) }
    val saveTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { saveTo(it) }

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        when (tool) {
            "crop_img" -> launchCropForUri(uri)
            "compress_img" -> showCompressImageDialog = true
            "resize_img" -> showResizeImageDialog = true
        }
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "convert_img") {
            pendingImageUris = uris
            showConvertImageDialog = true
        }
    }

    fun startTool(tool: String, uri: Uri) {
        selectedTool = tool
        pendingUri = uri
        when (tool) {
            "compress" -> showCompressDialog = true
            "pdf2img" -> showPdfToImagesDialog = true
            "protect" -> showPasswordDialog = true
            "unlock" -> viewModel.inspectForUnlock(uri)
            "split" -> showSplitDialog = true
            "organize", "extract", "rotate" -> showOrganizerSheet = true
            "viewer" -> onOpenPdfViewer(uri, viewModel.displayName(uri))
            "fill" -> viewModel.openForm(uri)
            "ocr" -> showOcrDialog = true
            "page_numbers" -> showPageNumbersDialog = true
            "watermark" -> showWatermarkDialog = true
            "pdf2text" -> viewModel.extractText(uri)
            "crop_img" -> launchCropForUri(uri)
            "compress_img" -> showCompressImageDialog = true
            "convert_img" -> {
                pendingImageUris = listOf(uri)
                showConvertImageDialog = true
            }
            "resize_img" -> showResizeImageDialog = true
        }
    }
    val officePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.officeToPdf(uri)
    }
    val printFallback by viewModel.printFallback.collectAsStateWithLifecycle()
    val activityContext = LocalContext.current
    LaunchedEffect(printFallback) {
        val (html, name) = printFallback ?: return@LaunchedEffect
        viewModel.clearPrintFallback()
        com.example.scanpro.imaging.HtmlToPdf.printWithDialog(activityContext, html, name)
    }
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        viewModel.rememberPdfs(listOf(uri))
        startTool(tool, uri)
    }
    var ordering by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var imagesToOrder by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var imagesToConvert by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "merge") {
            viewModel.rememberPdfs(uris)
            ordering = uris
        }
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") {
            imagesToOrder = uris
        }
    }
    ordering?.let { picked ->
        MergeEditor(
            initialUris = picked,
            displayName = viewModel::displayName,
            onDismiss = { ordering = null },
            onConfirm = { inOrder ->
                ordering = null
                viewModel.merge(inOrder)
            },
            onPicked = viewModel::rememberPdfs
        )
    }
    imagesToOrder?.let { picked ->
        ImagesToPdfEditor(
            initialUris = picked,
            displayName = viewModel::displayName,
            onDismiss = { imagesToOrder = null },
            onConfirm = { inOrder ->
                imagesToOrder = null
                imagesToConvert = inOrder
            }
        )
    }
    imagesToConvert?.let { images ->
        ImagesToPdfDialog(
            count = images.size,
            onDismiss = {
                images.mapNotNull { it.path?.let(::File)?.parentFile }
                    .filter { it.name.startsWith("img2pdf_order_") }
                    .distinct()
                    .forEach { it.deleteRecursively() }
                imagesToConvert = null
            },
            onConfirm = { size, quality ->
                imagesToConvert = null
                viewModel.imagesToPdf(images, size, quality)
            }
        )
    }

    val launchTool: (PdfToolCard) -> Unit = { tool ->
        selectedTool = tool.id
        when (tool.id) {
            "merge" -> multiPdfPicker.launch(arrayOf("application/pdf"))
            "img2pdf" -> imagesPicker.launch("image/*")
            "office" -> officePicker.launch(OFFICE_MIME_TYPES)
            "crop_img", "compress_img", "resize_img" -> singleImagePicker.launch("image/*")
            "convert_img" -> multiImagePicker.launch("image/*")
            else -> singlePdfPicker.launch(arrayOf("application/pdf"))
        }
    }

    PdfToolsScreen(
        recentPdfs = recentPdfs,
        onOpenPdfViewer = { uri, name ->
            recentScope.launch { if (viewModel.checkRecent(uri)) onOpenPdfViewer(uri, name) }
        },
        onLaunchTool = launchTool,
        onClearRecentPdfs = viewModel::clearRecentPdfs,
        onRemoveRecentPdf = { pdf -> viewModel.forgetPdf(pdf.uri) },
        onUseRecentPdfWithTool = { pdf, tool ->
            recentScope.launch { if (viewModel.checkRecent(pdf.uri)) startTool(tool, pdf.uri) }
        },
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        overlayContent = {
            progress?.let { running ->
                ProcessingOverlay(
                    current = running.current,
                    total = running.total,
                    title = running.title,
                    subtitle = running.subtitle,
                    onCancel = viewModel::cancel
                )
            }

            // A rotation loses the callback that hands an Images to PDF crop back to its page. Saving the crop would then
            // produce a stray cropped copy and leave the page unchanged, so the editor is closed instead; crop again.
            val lostCallback = cropNeedsCallback && onCropFinishedCallback == null
            LaunchedEffect(lostCallback, activeCropSourcePath) {
                val staged = activeCropSourcePath
                if (lostCallback && staged != null) {
                    File(staged).delete()
                    activeCropSourcePath = null
                    activeCropUri = null
                    cropNeedsCallback = false
                }
            }
            activeCropSourcePath?.takeUnless { lostCallback }?.let { stagedPath ->
                CropEditorDialog(
                    sourcePath = stagedPath,
                    initial = CropQuad.DEFAULT,
                    onDismiss = {
                        File(stagedPath).delete()
                        activeCropSourcePath = null
                        activeCropUri = null
                        onCropFinishedCallback = null
                    },
                    onConfirm = { quad ->
                        val originalUri = activeCropUri
                        val callback = onCropFinishedCallback
                        activeCropSourcePath = null
                        activeCropUri = null
                        onCropFinishedCallback = null
                        if (originalUri != null) {
                            if (callback != null) {
                                cropScope.launch {
                                    val croppedFile = withContext(Dispatchers.IO) {
                                        val dest = File(context.cacheDir, "precrop_${UUID.randomUUID()}.jpg")
                                        ImageProcessor.cropSourceToNewFile(File(stagedPath), dest, quad)
                                        File(stagedPath).delete()
                                        dest
                                    }
                                    if (croppedFile.exists() && croppedFile.length() > 0) {
                                        callback(Uri.fromFile(croppedFile))
                                    }
                                }
                            } else {
                                File(stagedPath).delete()
                                viewModel.cropImage(originalUri, quad)
                            }
                        } else {
                            File(stagedPath).delete()
                        }
                    }
                )
            }

            lastResult?.let { result ->
                ResultDialog(
                    result = result,
                    onDismiss = viewModel::clearResult,
                    onView = {
                        val file = result.outputFile
                        viewModel.clearResult()
                        if (file.extension.equals("pdf", ignoreCase = true)) onOpenPdfViewer(Uri.fromFile(file), file.name)
                        else shareFile(context, file.absolutePath, mimeFor(file), file.name)
                    },
                    onSaveAs = {
                        saveSourceFile = result.outputFile
                        val name = result.outputFile.name
                        when (mimeFor(result.outputFile)) {
                            "application/zip" -> saveZipLauncher.launch(name)
                            "application/pdf" -> saveFileLauncher.launch(name)
                            "image/png" -> savePngLauncher.launch(name)
                            "image/webp" -> saveWebpLauncher.launch(name)
                            "text/plain" -> saveTextLauncher.launch(name)
                            else -> saveJpegLauncher.launch(name)
                        }
                    }
                )
            }

            val uri = pendingUri
            if (showCompressDialog && uri != null) {
                CompressPdfDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showCompressDialog = false },
                    onConfirm = { preset, customTargetBytes ->
                        showCompressDialog = false
                        viewModel.compress(uri, preset, customTargetBytes)
                    }
                )
            }

            if (showWatermarkDialog && uri != null) {
                WatermarkEditor(
                    uri = uri,
                    onDismiss = { showWatermarkDialog = false },
                    onConfirm = { config ->
                        showWatermarkDialog = false
                        viewModel.watermarkPdf(uri, config)
                    }
                )
            }

            if (showPageNumbersDialog && uri != null) {
                PageNumbersPdfDialog(
                    uri = uri,
                    onDismiss = { showPageNumbersDialog = false },
                    onConfirm = { config ->
                        showPageNumbersDialog = false
                        viewModel.addPageNumbers(uri, config)
                    }
                )
            }

            if (showCompressImageDialog && uri != null) {
                CompressImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showCompressImageDialog = false },
                    onCrop = {
                        showCompressImageDialog = false
                        launchCropForUri(uri) { croppedUri ->
                            pendingUri = croppedUri
                            showCompressImageDialog = true
                        }
                    },
                    onConfirm = { config ->
                        showCompressImageDialog = false
                        viewModel.compressImage(uri, config)
                    }
                )
            }

            pendingImageUris?.let { uris ->
                if (showConvertImageDialog && uris.isNotEmpty()) {
                    ConvertImageDialog(
                        uris = uris,
                        onDismiss = {
                            showConvertImageDialog = false
                            pendingImageUris = null
                        },
                        onCropImage = { index ->
                            val targetUri = uris[index]
                            showConvertImageDialog = false
                            launchCropForUri(targetUri) { croppedUri ->
                                val updated = uris.toMutableList()
                                updated[index] = croppedUri
                                pendingImageUris = updated
                                showConvertImageDialog = true
                            }
                        },
                        onConfirm = { format, quality ->
                            showConvertImageDialog = false
                            val currentUris = pendingImageUris ?: uris
                            pendingImageUris = null
                            viewModel.convertImage(currentUris, format, quality)
                        },
                        onConvertToPdf = {
                            showConvertImageDialog = false
                            val currentUris = pendingImageUris ?: uris
                            pendingImageUris = null
                            viewModel.imagesToPdf(currentUris, PdfPageSize.AUTO, PdfQuality.HIGH)
                        }
                    )
                }
            }

            if (showResizeImageDialog && uri != null) {
                ResizeImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showResizeImageDialog = false },
                    onCrop = {
                        showResizeImageDialog = false
                        launchCropForUri(uri) { croppedUri ->
                            pendingUri = croppedUri
                            showResizeImageDialog = true
                        }
                    },
                    onConfirm = { config ->
                        showResizeImageDialog = false
                        viewModel.resizeImage(uri, config)
                    }
                )
            }

            if (showPdfToImagesDialog && uri != null) {
                PdfToImagesDialog(
                    uri = uri,
                    onDismiss = { showPdfToImagesDialog = false },
                    onConfirm = { format, pages, scale ->
                        showPdfToImagesDialog = false
                        viewModel.pdfToImages(uri, format, pages, scale)
                    }
                )
            }

            unlockNotice?.let { (noticeUri, protection) ->
                val restricted = protection == PdfProtection.RESTRICTED_ONLY
                AlertDialog(
                    onDismissRequest = viewModel::clearUnlockNotice,
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text(stringResource(if (restricted) R.string.tools_unlock_no_open_password_title else R.string.tools_unlock_no_password_title)) },
                    text = {
                        Text(
                            stringResource(if (restricted) R.string.tools_unlock_restricted_body else R.string.tools_unlock_nothing_body)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearUnlockNotice()
                            if (restricted) {
                                viewModel.unlock(noticeUri, "")
                            } else {
                                pendingUri = noticeUri
                                selectedTool = "protect"
                                showPasswordDialog = true
                            }
                        }) { Text(stringResource(if (restricted) R.string.tools_unlock_remove_restrictions else R.string.tools_unlock_set_password)) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                viewModel.clearUnlockNotice()
                                onOpenPdfViewer(noticeUri, pdfDocumentTitle)
                            }) { Text(stringResource(R.string.tools_view_pdf)) }
                            TextButton(onClick = viewModel::clearUnlockNotice) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                )
            }

            if (showPasswordDialog && uri != null && selectedTool != null) {
                val isProtect = selectedTool == "protect"
                PasswordDialog(
                    isProtect = isProtect,
                    initialError = if (!isProtect && wrongPassword) stringResource(R.string.tools_wrong_password_try_again) else null,
                    onDismiss = { showPasswordDialog = false; viewModel.clearWrongPassword() },
                    onConfirm = { pwd ->
                        showPasswordDialog = false
                        viewModel.clearWrongPassword()
                        if (isProtect) viewModel.protect(uri, pwd) else viewModel.unlock(uri, pwd)
                    }
                )
            }

            mergePassword?.let { request ->
                key(request.attempt) {
                    PasswordDialog(
                        isProtect = false,
                        mergeFileName = request.fileName,
                        initialError = if (request.wrongPassword) stringResource(R.string.tools_wrong_password_try_again) else null,
                        onDismiss = viewModel::cancelMergePassword,
                        onConfirm = viewModel::submitMergePassword
                    )
                }
            }

            if (showOcrDialog && uri != null) {
                OcrScriptDialog(
                    onDismiss = { showOcrDialog = false },
                    onConfirm = { script ->
                        showOcrDialog = false
                        viewModel.makeSearchable(uri, script)
                    }
                )
            }

            formSession?.let { session ->
                FormFillDialog(
                    session = session,
                    onValueChange = viewModel::updateFormValue,
                    onSave = viewModel::saveForm,
                    onClose = viewModel::closeForm
                )
            }

            if (showSplitDialog && uri != null) {
                SplitDialog(
                    uri = uri,
                    onDismiss = { showSplitDialog = false },
                    onConfirm = { mode, rangesText, everyN ->
                        showSplitDialog = false
                        viewModel.split(uri, mode, rangesText, everyN)
                    }
                )
            }

            val tool = selectedTool
            if (showOrganizerSheet && uri != null && tool != null) {
                PdfPageOrganizerSheet(
                    uri = uri,
                    mode = tool,
                    onClose = { showOrganizerSheet = false },
                    onApply = { pageSpecs, extractedIndices, rotationsMap ->
                        showOrganizerSheet = false
                        viewModel.organize(tool, uri, pageSpecs, extractedIndices, rotationsMap)
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    recentPdfs: List<RecentPdf>,
    onOpenPdfViewer: (Uri, String) -> Unit = { _, _ -> },
    onLaunchTool: (PdfToolCard) -> Unit = {},
    onClearRecentPdfs: () -> Unit = {},
    onRemoveRecentPdf: (RecentPdf) -> Unit = {},
    onUseRecentPdfWithTool: (RecentPdf, String) -> Unit = { _, _ -> },
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    overlayContent: @Composable () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    val toolsMemory = rememberToolsMemory()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_screen_title)) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 1100.dp)
            ) {
                item(key = "search") { ToolSearchField(query, onQueryChange = { query = it }, modifier = Modifier.padding(top = 4.dp)) }
                // Narrowing by category only makes sense while not searching: a search looks at every tool.
                if (query.isBlank()) {
                    item(key = "categories") { Box(Modifier.padding(top = 12.dp)) { ToolCategoryChips(category, onSelect = { category = it }) } }
                }
                toolsLanding(query, category, toolsMemory, onLaunch = onLaunchTool)
                if (recentPdfs.isNotEmpty()) {
                    item(key = "recent-header") {
                        RecentPdfsHeader(onClear = onClearRecentPdfs)
                    }
                    items(recentPdfs, key = { it.uri.toString() }, contentType = { "recent_pdf" }) { pdf ->
                        RecentPdfRow(
                            pdf = pdf,
                            onOpen = { onOpenPdfViewer(pdf.uri, pdf.name) },
                            onUseWith = { tool -> onUseRecentPdfWithTool(pdf, tool) },
                            onRemove = { onRemoveRecentPdf(pdf) }
                        )
                    }
                }
            }
            overlayContent()
        }
    }
}

/** Below this a PDF is too small for the compression presets to promise anything. */
private const val SMALL_PDF_BYTES = 150_000L

@Composable
private fun CompressPdfDialog(
    context: android.content.Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (preset: CompressionPreset, customTargetBytes: Long?) -> Unit
) {
    var originalSize by remember { mutableLongStateOf(0L) }
    var preset by rememberSaveable { mutableStateOf(CompressionPreset.BALANCED) }
    var isCustomTarget by rememberSaveable { mutableStateOf(false) }
    var targetInput by rememberSaveable { mutableStateOf("") }
    var targetUnit by rememberSaveable { mutableStateOf("KB") }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            originalSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        }
    }

    val customBytes: Long? = remember(targetInput, targetUnit) {
        val number = targetInput.trim().toDoubleOrNull()
        if (number != null && number > 0.0) {
            // Same unit as the sizes the dialog shows (decimal kB/MB on Android 8+), or "170 KB" would be
            // rejected against a file shown as 171 kB.
            val unit = displayedKiloByte()
            if (targetUnit == "MB") (number * unit * unit).toLong() else (number * unit).toLong()
        } else null
    }

    val isTargetTooLarge = isCustomTarget && customBytes != null && originalSize > 0 && customBytes >= originalSize
    val isTargetZero = isCustomTarget && targetInput.isNotBlank() && (customBytes == null || customBytes <= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_compress_pdf)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.tools_compress_current_size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatBytes(LocalContext.current, originalSize),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                CompressionPreset.entries.forEach { item ->
                    val isSelected = !isCustomTarget && preset == item
                    val estFraction = when (item) {
                        CompressionPreset.HIGH -> 0.65f
                        CompressionPreset.BALANCED -> 0.40f
                        CompressionPreset.MAXIMUM -> 0.20f
                    }
                    val estBytes = (originalSize * estFraction).toLong()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isCustomTarget = false
                                preset = item
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                isCustomTarget = false
                                preset = item
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(item.labelRes),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(item.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (originalSize > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = stringResource(R.string.tools_compress_estimated_tag, formatBytes(LocalContext.current, estBytes)),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // 4th option: Custom Target Size
                val isCustomSelected = isCustomTarget
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isCustomTarget = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomTarget = true }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.tools_compress_preset_custom),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.tools_compress_preset_custom_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCustomSelected && customBytes != null && customBytes > 0 && !isTargetTooLarge) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = stringResource(R.string.tools_compress_estimated_tag, formatBytes(LocalContext.current, customBytes)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (isCustomTarget) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { text ->
                                if (text.isEmpty() || text.matches(Regex("""^\d*\.?\d*$"""))) {
                                    targetInput = text
                                }
                            },
                            label = { Text(stringResource(R.string.tools_compress_custom_target_hint)) },
                            placeholder = { Text(if (targetUnit == "KB") "e.g. 500" else "e.g. 0.5") },
                            singleLine = true,
                            isError = isTargetTooLarge || isTargetZero,
                            supportingText = {
                                if (isTargetTooLarge) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_larger, formatBytes(LocalContext.current, originalSize)),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isTargetZero) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_positive),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (customBytes != null && customBytes > 0 && originalSize > 0) {
                                    val saved = originalSize - customBytes
                                    val pct = ((saved.toDouble() / originalSize.toDouble()) * 100).toInt().coerceIn(1, 95)
                                    Text(stringResource(R.string.tools_compress_target_valid_hint, pct))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = targetUnit == "KB",
                                onClick = { targetUnit = "KB" },
                                label = { Text(stringResource(R.string.tools_compress_unit_kb)) }
                            )
                            FilterChip(
                                selected = targetUnit == "MB",
                                onClick = { targetUnit = "MB" },
                                label = { Text(stringResource(R.string.tools_compress_unit_mb)) }
                            )
                        }
                    }
                }

                if (originalSize > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isTargetTooLarge) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.tools_compress_warning_larger_than_original,
                                        formatBytes(LocalContext.current, originalSize)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (!isCustomTarget && originalSize in 1 until SMALL_PDF_BYTES) {
                        // Under about 150 kB there is rarely anything to squeeze, and a made-up percentage would mislead.
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.tools_compress_small_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    } else if (!isCustomTarget || (customBytes != null && customBytes > 0)) {
                        val (summaryBytes, summaryPercent) = if (isCustomTarget) {
                            val bytes = customBytes ?: (originalSize * 0.40f).toLong()
                            val pct = (((originalSize - bytes).toDouble() / originalSize.toDouble()) * 100).toInt().coerceIn(1, 95)
                            bytes to pct
                        } else {
                            val fraction = when (preset) {
                                CompressionPreset.HIGH -> 0.65f
                                CompressionPreset.BALANCED -> 0.40f
                                CompressionPreset.MAXIMUM -> 0.20f
                            }
                            val pct = when (preset) {
                                CompressionPreset.HIGH -> 35
                                CompressionPreset.BALANCED -> 60
                                CompressionPreset.MAXIMUM -> 80
                            }
                            (originalSize * fraction).toLong() to pct
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Compress,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.tools_compress_expected_summary,
                                        formatBytes(LocalContext.current, summaryBytes),
                                        formatBytes(LocalContext.current, originalSize),
                                        summaryPercent
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tools_compress_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            val canConfirm = if (isCustomTarget) {
                customBytes != null && customBytes > 0 && (originalSize <= 0 || customBytes < originalSize)
            } else true
            Button(
                onClick = { onConfirm(preset, if (isCustomTarget) customBytes else null) },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.tools_compress_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PdfToImagesDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (format: ImageExportFormat, pages: List<Int>?, scale: Float) -> Unit
) {
    val context = LocalContext.current
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }
    var scale by rememberSaveable { mutableStateOf(2.5f) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri) {
        pageCount = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { android.graphics.pdf.PdfRenderer(it).use { r -> r.pageCount } }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_pdf2img_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tools_pdf2img_format), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImageExportFormat.entries.forEach { item ->
                        FilterChip(selected = format == item, onClick = { format = item }, label = { Text(item.extension.uppercase()) })
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.tools_pdf2img_resolution), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(R.string.tools_pdf2img_screen to 1.5f, R.string.tools_pdf2img_sharp to 2.5f, R.string.tools_pdf2img_print to 4f).forEach { (label, value) ->
                        FilterChip(selected = scale == value, onClick = { scale = value }, label = { Text(stringResource(label)) })
                    }
                }
                Text(
                    stringResource(
                        when (scale) {
                            1.5f -> R.string.tools_pdf2img_screen_hint
                            4f -> R.string.tools_pdf2img_print_hint
                            else -> R.string.tools_pdf2img_sharp_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.tools_pdf2img_pages), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = allPages,
                        onClick = { allPages = true; error = null },
                        label = { Text(pageCount?.let { stringResource(R.string.tools_pdf2img_all_count, it) } ?: stringResource(R.string.tools_pdf2img_all)) }
                    )
                    FilterChip(selected = !allPages, onClick = { allPages = false }, label = { Text(stringResource(R.string.tools_pdf2img_some)) })
                }
                if (!allPages) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text(stringResource(R.string.tools_pdf2img_pages_label)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (allPages) onConfirm(format, null, scale)
                else PageRanges.parse(rangesText, pageCount ?: Int.MAX_VALUE)
                    .onSuccess { ranges -> onConfirm(format, ranges.flatMap { it.toList() }, scale) }
                    .onFailure { error = it.message }
            }) { Text(stringResource(R.string.tools_pdf2img_export_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Page size and quality for Images to PDF, the two settings the engine already takes. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ImagesToPdfDialog(count: Int, onDismiss: () -> Unit, onConfirm: (PdfPageSize, PdfQuality) -> Unit) {
    var size by rememberSaveable { mutableStateOf(PdfPageSize.AUTO) }
    var quality by rememberSaveable { mutableStateOf(PdfQuality.BALANCED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.tools_img2pdf_title, count, count)) },
        text = {
            Column {
                Text(stringResource(R.string.tools_img2pdf_page_size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfPageSize.entries.forEach { item ->
                        FilterChip(
                            selected = size == item,
                            onClick = { size = item },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
                Text(
                    if (size == PdfPageSize.AUTO) stringResource(R.string.tools_img2pdf_auto_hint) else stringResource(R.string.tools_img2pdf_fitted_hint, stringResource(size.labelRes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.tools_img2pdf_quality), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfQuality.entries.forEach { item ->
                        FilterChip(
                            selected = quality == item,
                            onClick = { quality = item },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(size, quality) }) { Text(stringResource(R.string.tools_img2pdf_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun RecentPdfsHeader(onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.tools_recent_pdfs),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.tools_recent_clear)) }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.tools_recent_clear_title)) },
            text = { Text(stringResource(R.string.tools_recent_clear_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.tools_recent_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/** One PDF opened before: tap to read; the overflow menu opens it, sends it straight into a tool, or removes it. */
@Composable
private fun RecentPdfRow(pdf: RecentPdf, onOpen: () -> Unit, onUseWith: (String) -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tools_recent_open), onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(Icons.Default.PictureAsPdf, TileTone.Coral, size = 36.dp, corner = 10.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(pdf.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "${stringResource(R.string.common_more)}: ${pdf.name}"
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_open)) }, onClick = { menuOpen = false; onOpen() })
                DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_use_with_tool)) }, onClick = { menuOpen = false; toolsOpen = true })
                androidx.compose.material3.HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_remove_from_recent)) }, onClick = { menuOpen = false; removing = true })
            }
            // The same file straight into a tool, without picking it again.
            DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                PDF_TOOLS.filter { it.id !in setOf("merge", "img2pdf") }.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text(stringResource(tool.title)) },
                        leadingIcon = { Icon(tool.icon, contentDescription = null) },
                        onClick = { toolsOpen = false; onUseWith(tool.id) }
                    )
                }
            }
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.tools_recent_remove_title)) },
            text = { Text(stringResource(R.string.tools_recent_remove_body, pdf.name)) },
            confirmButton = { TextButton(onClick = { removing = false; onRemove() }) { Text(stringResource(R.string.tools_recent_remove)) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun ProcessingOverlay(
    current: Int,
    total: Int,
    title: String,
    subtitle: String?,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    ) {
        BackHandler(onBack = onCancel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    // Scrolls when a large font or landscape leaves too little height, so Cancel is never cut off.
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    val showSubtitle = !subtitle.isNullOrBlank() && subtitle != title
                    if (total > 0 && showSubtitle) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tools_progress_count, current, total),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    isProtect: Boolean,
    initialError: String? = null,
    /** Set when Merge asks for the password of this file: the dialog names it and the button carries on merging. */
    mergeFileName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember(initialError) { mutableStateOf(initialError) }
    // Unlocking must accept whatever password the file already has, however short.
    val minimum = if (isProtect) PdfSecurity.MIN_PASSWORD_LENGTH else 1
    // Read here: the confirm button's onClick is not a composable scope.
    val tooShortError = pluralStringResource(R.plurals.tools_password_too_short, minimum, minimum)
    val enterPasswordError = stringResource(R.string.tools_password_enter)
    val mismatchError = stringResource(R.string.tools_password_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        mergeFileName != null -> R.string.ui_pdf_password_title
                        isProtect -> R.string.tools_password_protect_title
                        else -> R.string.tools_unlock_pdf
                    }
                )
            )
        },
        text = {
            Column {
                Text(
                    when {
                        mergeFileName != null -> stringResource(R.string.tools_merge_password_prompt, mergeFileName)
                        isProtect -> pluralStringResource(R.plurals.tools_password_protect_warning, PdfSecurity.MIN_PASSWORD_LENGTH, PdfSecurity.MIN_PASSWORD_LENGTH)
                        else -> stringResource(R.string.tools_password_unlock_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mergeFileName != null) {
                    // The pages leave the protected file behind: say so before the user carries on.
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tools_merge_password_unprotected_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text(stringResource(R.string.tools_password_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    // Password type even while shown, so the keyboard never learns or suggests it.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tools_password_toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isProtect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; errorText = null },
                        label = { Text(stringResource(R.string.tools_password_confirm_label)) },
                        singleLine = true,
                        isError = errorText != null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Text(
                        errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length < minimum) {
                        errorText = if (isProtect) tooShortError else enterPasswordError
                    } else if (isProtect && confirm != password) {
                        errorText = mismatchError
                    } else {
                        onConfirm(password)
                    }
                }
            ) {
                Text(
                    stringResource(
                        when {
                            mergeFileName != null -> R.string.common_continue
                            isProtect -> R.string.tools_protect_pdf
                            else -> R.string.tools_unlock_pdf
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SplitDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (SplitMode, String, Int) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var mode by rememberSaveable { mutableStateOf(SplitMode.SINGLE_PAGES) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var chunkSizeText by rememberSaveable { mutableStateOf("2") }
    var error by remember { mutableStateOf<String?>(null) }
    // The page count makes the ranges checkable before the split starts.
    var pageCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri) {
        pageCount = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { android.graphics.pdf.PdfRenderer(it).use { r -> r.pageCount } }
            }.getOrNull()
        }
        if (rangesText.isEmpty()) pageCount?.let { rangesText = if (it > 1) "1-${(it + 1) / 2}, ${(it + 1) / 2 + 1}-$it" else "1" }
    }

    if (pageCount == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.tools_split_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Text(stringResource(R.string.tools_organizer_opening), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        )
        return
    }

    if (pageCount == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            title = {
                Text(
                    stringResource(R.string.tools_split_single_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.tools_split_single_page_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.tools_split_single_page_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_split_title)) },
        text = {
            Column {
                Text(
                    pageCount?.let { pluralStringResource(R.plurals.tools_split_has_pages, it, it) } ?: stringResource(R.string.tools_split_choose),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                SplitMode.entries.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = item }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = mode == item, onClick = { mode = item })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(item.labelRes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(item.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (mode == SplitMode.PAGE_RANGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text(stringResource(R.string.tools_split_ranges_example)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mode == SplitMode.EVERY_N_PAGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chunkSizeText,
                        onValueChange = { chunkSizeText = it.filter(Char::isDigit); error = null },
                        label = { Text(stringResource(R.string.tools_split_pages_per_file)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = chunkSizeText.toIntOrNull()
                    val pages = pageCount ?: Int.MAX_VALUE
                    when (mode) {
                        SplitMode.PAGE_RANGES -> PageRanges.parse(rangesText, pages)
                            .onSuccess { onConfirm(mode, rangesText, 1) }
                            .onFailure { error = it.message }
                        SplitMode.EVERY_N_PAGES -> when {
                            n == null || n < 1 -> error = resources.getString(R.string.tools_split_enter_count)
                            pageCount != null && n >= pageCount!! -> error = resources.getQuantityString(R.plurals.tools_split_whole_document, pageCount!!, pageCount!!)
                            else -> onConfirm(mode, rangesText, n)
                        }
                        SplitMode.SINGLE_PAGES -> onConfirm(mode, rangesText, 1)
                    }
                }
            ) {
                Text(stringResource(R.string.tools_split_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPageOrganizerSheet(
    uri: Uri,
    mode: String,
    onClose: () -> Unit,
    onApply: (pageSpecs: List<PageSpec>, extractedIndices: List<Int>, rotationsMap: Map<Int, Int>) -> Unit
) {
    val context = LocalContext.current
    // Extract starts with nothing chosen; Organise starts with every page kept (untick to delete one).
    val defaultSelected = mode != "extract"
    // Page order, rotations and selections survive rotation and window changes; thumbnails are re-rendered on demand.
    val pages = rememberSaveable(
        uri, mode,
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { list -> list.flatMap { listOf(it.index, it.rotation, if (it.selected) 1 else 0) } },
            restore = { flat -> mutableStateListOf<ThumbnailPageModel>().apply { flat.chunked(3).forEach { (i, r, sel) -> add(ThumbnailPageModel(i, r, sel == 1)) } } }
        )
    ) { mutableStateListOf<ThumbnailPageModel>() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<PdfPreviewSession?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // Read here: the loading coroutine below is not a composable scope.
    val protectedError = stringResource(R.string.tools_organizer_protected)
    val openFailedError = stringResource(R.string.tools_organizer_open_failed)
    // Every change is recorded first, so Undo steps back through deletes, moves and rotations.
    val undoStack = remember { mutableStateListOf<List<ThumbnailPageModel>>() }
    fun change(block: () -> Unit) {
        undoStack.add(pages.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
        block()
    }
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        pages.clear()
        pages.addAll(previous)
    }

    androidx.compose.runtime.DisposableEffect(uri) {
        var opened: PdfPreviewSession? = null
        val job = scope.launch {
            try {
                val preview = PdfPreviewSession.open(context, uri)
                if (!isActive) {
                    // The sheet closed while the file was opening; nothing will close this later.
                    preview.close()
                    return@launch
                }
                opened = preview
                session = preview
                if (pages.isEmpty()) pages.addAll((0 until preview.pageCount).map { ThumbnailPageModel(it, selected = defaultSelected) })
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                loadError = protectedError
            } catch (_: Exception) {
                loadError = openFailedError // not the exception's text: it is English and can contain file paths
            }
        }
        onDispose {
            job.cancel()
            opened?.close()
            session = null
        }
    }

    val isLoading = session == null && loadError == null
    val keptCount = pages.count { it.selected }
    var previewPage by remember { mutableStateOf<ThumbnailPageModel?>(null) }
    var actionPage by remember { mutableStateOf<ThumbnailPageModel?>(null) }
    // Extracting every page gives back the same document, so that is not offered: select fewer.
    val extractsEverything = mode == "extract" && pages.isNotEmpty() && keptCount == pages.size

    if (mode == "organize" || mode == "extract") {
        if (isLoading) {
            val titleRes = if (mode == "extract") R.string.tools_organizer_extract_title else R.string.tools_organizer_organize_title
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(titleRes)) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.tools_organizer_opening), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) } }
            )
            return
        }

        if (session?.pageCount == 1) {
            AlertDialog(
                onDismissRequest = onClose,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (mode == "extract") Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(if (mode == "extract") R.string.tools_extract_single_page_title else R.string.tools_organize_single_page_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(if (mode == "extract") R.string.tools_extract_single_page_hint else R.string.tools_organize_single_page_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(if (mode == "extract") R.string.tools_extract_single_page_desc else R.string.tools_organize_single_page_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
            return
        }
    }

    // Closing with deletes, moves or rotations that were not applied asks first.
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (undoStack.isNotEmpty()) confirmClose = true else onClose() }
    androidx.activity.compose.BackHandler(enabled = undoStack.isNotEmpty()) { confirmClose = true }
    if (confirmClose) {
        com.example.scanpro.feature.document.DiscardEditsDialog(
            onKeepEditing = { confirmClose = false },
            onDiscard = { confirmClose = false; onClose() }
        )
    }

    // Shown inside the Tools tab, which already keeps clear of the status bar and the bottom bar: taking those insets
    // again here left a dark band under the pages and a gap above the title.
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        text = stringResource(
                            when (mode) {
                                "extract" -> R.string.tools_organizer_extract_title
                                "rotate" -> R.string.tools_organizer_rotate_title
                                else -> R.string.tools_organizer_organize_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.common_undo))
                    }
                    var organizerMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { organizerMenu = true }, enabled = !isLoading) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(expanded = organizerMenu, onDismissRequest = { organizerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tools_organizer_rotate_all)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                                onClick = { organizerMenu = false; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(rotation = (pages[i].rotation + 90) % 360) } } }
                            )
                            if (mode != "rotate") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (pages.all { it.selected }) R.string.tools_organizer_untick_all else R.string.tools_organizer_tick_all)) },
                                    onClick = { organizerMenu = false; val all = pages.all { it.selected }; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(selected = !all) } } }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_reset)) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                onClick = {
                                    organizerMenu = false
                                    val count = session?.pageCount ?: pages.size
                                    change { pages.clear(); pages.addAll((0 until count).map { ThumbnailPageModel(it, selected = defaultSelected) }) }
                                }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            // Organise drops the pages that were unticked; Rotate keeps every page.
                            val specs = pages.filter { mode == "rotate" || it.selected }.map { PageSpec(it.index, it.rotation) }
                            val extracted = pages.filter { it.selected }.map { it.index }
                            val rotations = pages.associate { it.index to it.rotation }
                            onApply(specs, extracted, rotations)
                        },
                        // Nothing to extract or keep would only end in an error message.
                        enabled = !isLoading && pages.isNotEmpty() && (mode == "rotate" || keptCount > 0) && !extractsEverything
                    ) {
                        Text(
                            when (mode) {
                                "extract" -> stringResource(R.string.tools_organizer_extract_count, keptCount)
                                "organize" -> stringResource(R.string.tools_organizer_save_count, keptCount)
                                else -> stringResource(R.string.tools_organizer_save_pdf)
                            }
                        )
                    }
                    // The bar has no window insets here, so without this the Save button touched the screen edge.
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                loadError != null -> Text(loadError!!, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.tools_organizer_opening))
                }
                else -> Column(Modifier.fillMaxSize().widthIn(max = 1200.dp)) {
                    when {
                        extractsEverything -> OrganizerBanner(stringResource(R.string.tools_extract_all_selected), warning = true)
                        mode == "organize" -> OrganizerBanner(stringResource(R.string.tools_organizer_drag_hint), warning = false)
                    }
                    ReorderablePageGrid(
                        items = pages,
                        key = { it.index },
                        dragEnabled = mode == "organize",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onMove = { from, to -> pages.add(to, pages.removeAt(from)) },
                        // One undo step for the whole drag, recorded when the page is picked up.
                        onDragStarted = { undoStack.add(pages.toList()); if (undoStack.size > 50) undoStack.removeAt(0) }
                    ) { item, index, dragging ->
                        OrganizerPageCard(
                            item = item,
                            position = index + 1,
                            session = session,
                            mode = mode,
                            isDragging = dragging,
                            onClick = {
                                when (mode) {
                                    "organize" -> actionPage = item
                                    "rotate" -> change { pages[index] = item.copy(rotation = (item.rotation + 90) % 360) }
                                    else -> change { pages[index] = item.copy(selected = !item.selected) }
                                }
                            },
                            onPreview = { previewPage = item },
                            onMore = { actionPage = item }
                        )
                    }
                }
            }
            actionPage?.let { target ->
                val index = pages.indexOfFirst { it.index == target.index }
                if (index >= 0) {
                    val current = pages[index]
                    PageActionsSheet(
                        title = stringResource(R.string.tools_page_number, index + 1),
                        subtitle = stringResource(R.string.tools_page_position, index + 1, pages.size),
                        onDismiss = { actionPage = null },
                        actions = listOf(
                            PageAction(Icons.Default.VerticalAlignTop, stringResource(R.string.tools_page_move_start), index > 0) { change { pages.add(0, pages.removeAt(index)) } },
                            PageAction(Icons.Default.KeyboardArrowUp, stringResource(R.string.tools_page_move_earlier), index > 0) { change { pages.add(index - 1, pages.removeAt(index)) } },
                            PageAction(Icons.Default.KeyboardArrowDown, stringResource(R.string.tools_page_move_later), index < pages.lastIndex) { change { pages.add(index + 1, pages.removeAt(index)) } },
                            PageAction(Icons.Default.VerticalAlignBottom, stringResource(R.string.tools_page_move_end), index < pages.lastIndex) { change { pages.add(pages.removeAt(index)) } },
                            PageAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.tools_page_rotate)) { change { pages[index] = current.copy(rotation = (current.rotation + 90) % 360) } },
                            PageAction(Icons.Default.Fullscreen, stringResource(R.string.tools_page_view)) { previewPage = current },
                            if (current.selected) PageAction(Icons.Default.Delete, stringResource(R.string.tools_page_remove), destructive = true) { change { pages[index] = current.copy(selected = false) } }
                            else PageAction(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.tools_page_keep)) { change { pages[index] = current.copy(selected = true) } }
                        )
                    )
                }
            }
            previewPage?.let { page ->
                PdfPagePreviewDialog(
                    page = page,
                    session = session,
                    onDismiss = { previewPage = null }
                )
            }
        }
    }
}

/** A short line above the pages: how to use the screen, or why the action is not available. */
@Composable
internal fun OrganizerBanner(text: String, warning: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (warning) Icons.Default.Info else Icons.Default.TouchApp,
            contentDescription = null,
            tint = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One page in the organiser: a portrait thumbnail with its current position, and what a tap does for the tool
 * (Organise: open the page menu, Rotate: turn it, Extract: tick it). Pressing and holding picks it up to move it.
 * The thumbnail is rendered only while the card is on screen.
 */
@Composable
private fun OrganizerPageCard(
    item: ThumbnailPageModel,
    position: Int,
    session: PdfPreviewSession?,
    mode: String,
    isDragging: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    onMore: () -> Unit
) {
    val thumbnail by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, session, item.index) {
        val preview = session ?: return@produceState
        value = withContext(Dispatchers.IO) { preview.render(item.index, 360) }
    }
    val extract = mode == "extract"
    val removed = mode == "organize" && !item.selected
    val chosen = extract && item.selected
    val pageName = stringResource(R.string.tools_page_number, position)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (chosen) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (extract) Modifier.toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .semantics { contentDescription = pageName }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.74f).padding(10.dp), contentAlignment = Alignment.Center) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = item.rotation.toFloat()
                            // A page turned on its side is wider than the card: shrink it to fit instead of clipping.
                            if (item.rotation % 180 != 0) { scaleX = 0.74f; scaleY = 0.74f }
                            alpha = if (removed) 0.35f else 1f
                        }
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)
            ) {
                Text(
                    "$position",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            when {
                extract -> Icon(
                    if (item.selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                )
                else -> IconButton(onClick = onPreview, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.tools_organizer_preview_page, position),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (mode == "organize") {
                if (removed) {
                    Text(
                        stringResource(R.string.tools_page_removed_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                IconButton(onClick = onMore, modifier = Modifier.align(Alignment.BottomEnd).size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (mode == "rotate") {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)
                )
            }
        }
    }
}

/** Full-resolution preview of a single page when tapped or long-pressed in the organiser. */
@Composable
private fun PdfPagePreviewDialog(
    page: ThumbnailPageModel,
    session: PdfPreviewSession?,
    onDismiss: () -> Unit
) {
    val fullBitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, page.index, session) {
        val preview = session ?: return@produceState
        value = withContext(Dispatchers.IO) { preview.render(page.index, 1400) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkChrome.backdrop)
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tools_page_number, page.index + 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkChrome.text
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = DarkChrome.text
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = fullBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.tools_page_number, page.index + 1),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = page.rotation.toFloat() },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

internal fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "zip" -> "application/zip"
    "txt" -> "text/plain"
    else -> "application/pdf"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CompressImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageCompressConfig) -> Unit
) {
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            metadata = ImageToolsEngine.inspectImage(context, uri)
        }
    }

    var mode by rememberSaveable { mutableStateOf(0) }
    var selectedPresetKb by rememberSaveable { mutableStateOf(100) }
    var customKbInput by rememberSaveable { mutableStateOf("100") }
    var qualityPercent by rememberSaveable { mutableStateOf(75) }
    var maxDim by rememberSaveable { mutableStateOf<Int?>(null) }
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }

    val origBytes = metadata?.sizeBytes ?: 0L
    val targetKb = if (selectedPresetKb > 0) selectedPresetKb else customKbInput.toIntOrNull() ?: 0
    val targetBytes = if (mode == 0 && targetKb > 0) (targetKb * displayedKiloByte()).toLong() else null

    val isTargetLarger = mode == 0 && origBytes > 0 && (targetBytes ?: 0L) >= origBytes
    val isTargetZero = mode == 0 && targetKb <= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_compress_img_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                metadata?.let { meta ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    meta.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatBytes(context, meta.sizeBytes)} • ${meta.width} × ${meta.height} px",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tools_crop_img_button))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(stringResource(R.string.tools_compress_img_mode_target)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(stringResource(R.string.tools_compress_img_mode_quality)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (mode == 0) {
                    Text(
                        stringResource(R.string.tools_compress_img_quick_sizes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 100, 200, 500, 1024, 2048).forEach { kb ->
                            val label = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
                            FilterChip(
                                selected = selectedPresetKb == kb,
                                onClick = {
                                    selectedPresetKb = kb
                                    customKbInput = kb.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = selectedPresetKb == -1,
                            onClick = { selectedPresetKb = -1 },
                            label = { Text(stringResource(R.string.tools_compress_img_custom_size)) }
                        )
                    }

                    if (selectedPresetKb == -1) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customKbInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    customKbInput = str
                                }
                            },
                            label = { Text(stringResource(R.string.tools_compress_img_target_kb)) },
                            suffix = { Text(stringResource(R.string.tools_compress_unit_kb)) },
                            singleLine = true,
                            isError = isTargetLarger || isTargetZero,
                            supportingText = {
                                if (isTargetLarger) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_larger, formatBytes(context, origBytes)),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isTargetZero) {
                                    Text(
                                        stringResource(R.string.tools_compress_err_target_positive),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.tools_compress_img_quality_label, qualityPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = qualityPercent.toFloat(),
                        onValueChange = { qualityPercent = it.roundToInt() },
                        valueRange = 15f..95f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.tools_compress_img_resolution_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val resOptions = listOf(
                        null to stringResource(R.string.tools_compress_img_res_auto),
                        2048 to stringResource(R.string.tools_compress_img_res_2048),
                        1600 to stringResource(R.string.tools_compress_img_res_1600),
                        1280 to stringResource(R.string.tools_compress_img_res_1280),
                        800 to stringResource(R.string.tools_compress_img_res_800)
                    )
                    resOptions.forEach { (dim, label) ->
                        FilterChip(
                            selected = maxDim == dim,
                            onClick = { maxDim = dim },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.tools_convert_img_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ImageExportFormat.JPEG,
                        onClick = { format = ImageExportFormat.JPEG },
                        label = { Text("JPG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.WEBP,
                        onClick = { format = ImageExportFormat.WEBP },
                        label = { Text("WEBP") }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.tools_compress_img_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            val canConfirm = if (mode == 0) {
                targetKb > 0 && !isTargetLarger
            } else true
            Button(
                onClick = {
                    val config = ImageCompressConfig(
                        targetSizeBytes = if (mode == 0) targetBytes else null,
                        qualityPercent = qualityPercent,
                        maxDimension = maxDim,
                        outputFormat = format
                    )
                    onConfirm(config)
                },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.tools_compress_img_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun ConvertImageDialog(
    uris: List<Uri>,
    onDismiss: () -> Unit,
    onCropImage: (Int) -> Unit,
    onConfirm: (targetFormat: ImageExportFormat, quality: Int) -> Unit,
    onConvertToPdf: () -> Unit
) {
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.PNG) }
    var exportPdf by rememberSaveable { mutableStateOf(false) }
    var jpegQuality by rememberSaveable { mutableStateOf(92) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (uris.size <= 1) stringResource(R.string.tools_convert_img_title)
                else stringResource(R.string.tools_convert_img_title) + " (${uris.size})"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uris.size == 1) {
                    OutlinedButton(
                        onClick = { onCropImage(0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tools_crop_img_button))
                    }
                } else {
                    Text(
                        stringResource(R.string.tools_crop_img_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(uris) { idx, _ ->
                            OutlinedButton(
                                onClick = { onCropImage(idx) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.tools_page_number, idx + 1), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.PNG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.PNG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.PNG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.PNG,
                                onClick = { exportPdf = false; format = ImageExportFormat.PNG }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_png),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    stringResource(R.string.tools_convert_img_lossless_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_png_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.JPEG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.JPEG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.JPEG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.JPEG,
                                onClick = { exportPdf = false; format = ImageExportFormat.JPEG }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_jpg),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_jpg_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                        if (!exportPdf && format == ImageExportFormat.JPEG) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tools_compress_img_quality_label, jpegQuality),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 40.dp)
                            )
                            Slider(
                                value = jpegQuality.toFloat(),
                                onValueChange = { jpegQuality = it.roundToInt() },
                                valueRange = 70f..100f,
                                steps = 5,
                                modifier = Modifier.padding(start = 32.dp).fillMaxWidth()
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!exportPdf && format == ImageExportFormat.WEBP) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (!exportPdf && format == ImageExportFormat.WEBP) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = false; format = ImageExportFormat.WEBP }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportPdf && format == ImageExportFormat.WEBP,
                                onClick = { exportPdf = false; format = ImageExportFormat.WEBP }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_webp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_webp_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (exportPdf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (exportPdf) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportPdf = true }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportPdf,
                                onClick = { exportPdf = true }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.tools_convert_img_format_pdf),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.tools_convert_img_format_pdf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (exportPdf) onConvertToPdf()
                else onConfirm(format, if (format == ImageExportFormat.PNG) 100 else jpegQuality)
            }) {
                Text(stringResource(R.string.tools_convert_img_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ResizeImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onConfirm: (ImageResizeConfig) -> Unit
) {
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var resizeMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Dimensions, 1 = Percentage
    var percentage by rememberSaveable { mutableIntStateOf(50) }
    var widthInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var lockAspect by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }
    var fit by rememberSaveable { mutableStateOf(ImageResizeFit.FILL) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val meta = ImageToolsEngine.inspectImage(context, uri)
            metadata = meta
            if (meta != null && widthInput.isEmpty()) {
                widthInput = meta.width.toString()
                heightInput = meta.height.toString()
            }
        }
    }

    val origW = metadata?.width ?: 1
    val origH = metadata?.height ?: 1
    val aspectRatio = origW.toFloat() / origH.toFloat()

    val w = if (resizeMode == 1) {
        (origW * percentage / 100f).roundToInt().coerceIn(10, 8000)
    } else widthInput.toIntOrNull() ?: 0

    val h = if (resizeMode == 1) {
        (origH * percentage / 100f).roundToInt().coerceIn(10, 8000)
    } else heightInput.toIntOrNull() ?: 0

    val isValid = w in 10..8000 && h in 10..8000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_resize_img_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                metadata?.let { meta ->
                    Text(
                        "${meta.fileName} (${meta.width} × ${meta.height} px)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tools_crop_img_button))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = resizeMode == 0,
                        onClick = { resizeMode = 0 },
                        label = { Text(stringResource(R.string.tools_resize_img_mode_dimensions)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = resizeMode == 1,
                        onClick = {
                            resizeMode = 1
                            widthInput = ((origW * percentage / 100f).roundToInt()).toString()
                            heightInput = ((origH * percentage / 100f).roundToInt()).toString()
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_mode_percent)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (resizeMode == 1) {
                    Text(
                        "${percentage}% (${w} × ${h} px)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 50, 75, 150, 200).forEach { pct ->
                            FilterChip(
                                selected = percentage == pct,
                                onClick = { percentage = pct },
                                label = { Text("$pct%") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = percentage.toFloat(),
                        onValueChange = { percentage = it.roundToInt() },
                        valueRange = 10f..300f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        stringResource(R.string.tools_compress_img_quick_sizes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            600 to 600 to stringResource(R.string.tools_resize_img_preset_passport),
                            400 to 400 to stringResource(R.string.tools_resize_img_preset_avatar),
                            1080 to 1080 to stringResource(R.string.tools_resize_img_preset_instagram),
                            1080 to 1920 to stringResource(R.string.tools_resize_img_preset_story),
                            1920 to 1080 to stringResource(R.string.tools_resize_img_preset_fhd),
                            1280 to 720 to stringResource(R.string.tools_resize_img_preset_hd),
                            2480 to 3508 to stringResource(R.string.tools_resize_img_preset_a4)
                        )
                        presets.forEach { (dims, label) ->
                            val (pw, ph) = dims
                            FilterChip(
                                selected = w == pw && h == ph,
                                onClick = {
                                    lockAspect = false
                                    widthInput = pw.toString()
                                    heightInput = ph.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = widthInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    widthInput = str
                                    if (lockAspect && str.isNotEmpty()) {
                                        val newW = str.toIntOrNull()
                                        if (newW != null && newW > 0) {
                                            heightInput = (newW / aspectRatio).roundToInt().coerceIn(10, 8000).toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.tools_resize_img_width)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    heightInput = str
                                    if (lockAspect && str.isNotEmpty()) {
                                        val newH = str.toIntOrNull()
                                        if (newH != null && newH > 0) {
                                            widthInput = (newH * aspectRatio).roundToInt().coerceIn(10, 8000).toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.tools_resize_img_height)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lockAspect = !lockAspect },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = lockAspect,
                            onCheckedChange = { lockAspect = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.tools_resize_img_lock_aspect),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.tools_resize_fit_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ImageResizeFit.FIT to R.string.tools_resize_fit_fit,
                            ImageResizeFit.FILL to R.string.tools_resize_fit_fill,
                            ImageResizeFit.STRETCH to R.string.tools_resize_fit_stretch
                        ).forEach { (mode, label) ->
                            FilterChip(selected = fit == mode, onClick = { fit = mode }, label = { Text(stringResource(label), maxLines = 1) })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(R.string.tools_convert_img_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ImageExportFormat.JPEG,
                        onClick = { format = ImageExportFormat.JPEG },
                        label = { Text("JPG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.PNG,
                        onClick = { format = ImageExportFormat.PNG },
                        label = { Text("PNG") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.WEBP,
                        onClick = { format = ImageExportFormat.WEBP },
                        label = { Text("WEBP") }
                    )
                }

                if (!isValid && (widthInput.isNotEmpty() || heightInput.isNotEmpty())) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tools_err_invalid_dimensions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        ImageResizeConfig(
                            targetWidth = w,
                            targetHeight = h,
                            outputFormat = format,
                            fit = if (resizeMode == 0) fit else ImageResizeFit.STRETCH
                        )
                    )
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.tools_resize_img_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PageNumbersPdfDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (PageNumbersConfig) -> Unit
) {
    var selectedPosition by rememberSaveable { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var selectedFormat by rememberSaveable { mutableStateOf(PageNumberFormat.PAGE_N_OF_M) }
    var skipCover by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_page_numbers_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.tools_page_numbers_position_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val posList = listOf(
                        PageNumberPosition.BOTTOM_CENTER to stringResource(R.string.tools_page_numbers_pos_bottom_center),
                        PageNumberPosition.BOTTOM_RIGHT to stringResource(R.string.tools_page_numbers_pos_bottom_right),
                        PageNumberPosition.BOTTOM_LEFT to stringResource(R.string.tools_page_numbers_pos_bottom_left),
                        PageNumberPosition.TOP_CENTER to stringResource(R.string.tools_page_numbers_pos_top_center),
                        PageNumberPosition.TOP_RIGHT to stringResource(R.string.tools_page_numbers_pos_top_right)
                    )
                    posList.forEach { (pos, label) ->
                        FilterChip(
                            selected = selectedPosition == pos,
                            onClick = { selectedPosition = pos },
                            label = { Text(label) }
                        )
                    }
                }

                Text(
                    stringResource(R.string.tools_page_numbers_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val formats = listOf(
                        PageNumberFormat.PAGE_N_OF_M to stringResource(R.string.tools_page_numbers_fmt_page_n_of_m),
                        PageNumberFormat.N_SLASH_M to stringResource(R.string.tools_page_numbers_fmt_n_slash_m),
                        PageNumberFormat.DASH to stringResource(R.string.tools_page_numbers_fmt_dash),
                        PageNumberFormat.SIMPLE to stringResource(R.string.tools_page_numbers_fmt_simple)
                    )
                    formats.forEach { (fmt, label) ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { skipCover = !skipCover },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = skipCover,
                        onCheckedChange = { skipCover = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.tools_page_numbers_skip_cover),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PageNumbersConfig(
                            position = selectedPosition,
                            format = selectedFormat,
                            skipFirstPage = skipCover
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.tools_page_numbers_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@get:StringRes
private val CompressionPreset.labelRes: Int
    get() = when (this) {
        CompressionPreset.HIGH -> R.string.tools_compress_preset_high
        CompressionPreset.BALANCED -> R.string.tools_compress_preset_balanced
        CompressionPreset.MAXIMUM -> R.string.tools_compress_preset_maximum
    }

@get:StringRes
private val CompressionPreset.descriptionRes: Int
    get() = when (this) {
        CompressionPreset.HIGH -> R.string.tools_compress_preset_high_desc
        CompressionPreset.BALANCED -> R.string.tools_compress_preset_balanced_desc
        CompressionPreset.MAXIMUM -> R.string.tools_compress_preset_maximum_desc
    }

/** Keeps the files picked for Merge or Images to PDF through a rotation (null when nothing is picked). */
private val UriListSaver = androidx.compose.runtime.saveable.Saver<List<Uri>?, java.util.ArrayList<String>>(
    save = { list -> list?.let { java.util.ArrayList(it.map(Uri::toString)) } },
    restore = { saved -> saved.map(Uri::parse) }
)

@Preview(name = "PDF Tools Screen Light")
@Composable
fun PdfToolsScreenPreview() {
    ScanProTheme(themeMode = ThemeMode.LIGHT) {
        PdfToolsScreen(
            recentPdfs = listOf(
                RecentPdf(Uri.parse("content://media/external/file/1"), "Invoice_2025_03.pdf", System.currentTimeMillis()),
                RecentPdf(Uri.parse("content://media/external/file/2"), "Project_Specification_Draft.pdf", System.currentTimeMillis() - 86400000L)
            )
        )
    }
}

@Preview(name = "PDF Tools Screen Dark")
@Composable
fun PdfToolsScreenDarkPreview() {
    ScanProTheme(themeMode = ThemeMode.DARK) {
        PdfToolsScreen(
            recentPdfs = listOf(
                RecentPdf(Uri.parse("content://media/external/file/1"), "Invoice_2025_03.pdf", System.currentTimeMillis()),
                RecentPdf(Uri.parse("content://media/external/file/2"), "Project_Specification_Draft.pdf", System.currentTimeMillis() - 86400000L)
            )
        )
    }
}
