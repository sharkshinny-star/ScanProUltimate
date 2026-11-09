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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
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
import com.example.scanpro.imaging.ImageToolsEngine
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

private class ToolSection(val id: String, @StringRes val title: Int, val tools: List<PdfToolCard>)

/** Tiles grouped the way the leading scanners do: what you do to a PDF, what you turn it into, how you lock it. */
private val TOOL_SECTIONS = listOf(
    ToolSection(
        "edit", R.string.tools_section_edit, listOf(
            PdfToolCard("merge", R.string.tools_card_merge, Icons.Default.Merge, TileTone.Indigo),
            PdfToolCard("split", R.string.tools_card_split, Icons.AutoMirrored.Filled.CallSplit, TileTone.Indigo),
            PdfToolCard("organize", R.string.tools_card_organize, Icons.Default.Description, TileTone.Sky, label = R.string.tools_tile_organize),
            PdfToolCard("rotate", R.string.tools_card_rotate, Icons.AutoMirrored.Filled.RotateRight, TileTone.Sky, label = R.string.tools_tile_rotate),
            PdfToolCard("extract", R.string.tools_card_extract, Icons.AutoMirrored.Filled.DriveFileMove, TileTone.Indigo, label = R.string.tools_tile_extract),
            PdfToolCard("compress", R.string.tools_card_compress, Icons.Default.Compress, TileTone.Amber)
        )
    ),
    ToolSection(
        "convert", R.string.tools_section_convert, listOf(
            PdfToolCard("img2pdf", R.string.tools_images_to_pdf, Icons.Default.Image, TileTone.Coral),
            PdfToolCard("office", R.string.tools_card_office, Icons.AutoMirrored.Filled.Article, TileTone.Sky, label = R.string.tools_tile_office),
            PdfToolCard("ocr", R.string.tools_card_ocr, Icons.Default.FindInPage, TileTone.Teal),
            PdfToolCard("fill", R.string.tools_card_fill, Icons.Default.EditNote, TileTone.Violet),
            PdfToolCard("pdf2img", R.string.tools_card_pdf2img, Icons.Default.PictureAsPdf, TileTone.Coral)
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
            PdfToolCard("compress_img", R.string.tools_card_compress_img, Icons.Default.Compress, TileTone.Amber, label = R.string.tools_tile_compress_img),
            PdfToolCard("convert_img", R.string.tools_card_convert_img, Icons.Default.Transform, TileTone.Teal, label = R.string.tools_tile_convert_img),
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

private val VIEWER_TOOL = PdfToolCard("viewer", R.string.tools_card_viewer, Icons.Default.Visibility, TileTone.Slate)

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

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        when (tool) {
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
            if (uris.size == 1) viewModel.merge(uris) else ordering = uris
        }
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") {
            imagesToOrder = uris
        }
    }
    ordering?.let { picked ->
        OrderFilesDialog(
            title = stringResource(R.string.tools_order_merge_title),
            items = picked,
            nameOf = viewModel::displayName,
            onDismiss = { ordering = null },
            onConfirm = { inOrder ->
                ordering = null
                viewModel.merge(inOrder)
            }
        )
    }
    imagesToOrder?.let { picked ->
        ImagesToPdfOrderDialog(
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
            "compress_img", "resize_img" -> singleImagePicker.launch("image/*")
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
                    label = running.label,
                    onCancel = viewModel::cancel
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

            if (showCompressImageDialog && uri != null) {
                CompressImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showCompressImageDialog = false },
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
                        onConfirm = { format, quality ->
                            showConvertImageDialog = false
                            pendingImageUris = null
                            viewModel.convertImage(uris, format, quality)
                        }
                    )
                }
            }

            if (showResizeImageDialog && uri != null) {
                ResizeImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showResizeImageDialog = false },
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
                TOOL_SECTIONS.forEach { section ->
                    item(key = "section-${section.id}") {
                        SectionHeader(stringResource(section.title))
                        ToolTileGrid(section.tools, onClick = onLaunchTool)
                    }
                }
                item(key = "viewer") {
                    Spacer(Modifier.height(16.dp))
                    PlainToolRow(VIEWER_TOOL, onClick = { onLaunchTool(VIEWER_TOOL) })
                }
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
            if (targetUnit == "MB") {
                (number * 1024.0 * 1024.0).toLong()
            } else {
                (number * 1024.0).toLong()
            }
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

/** Puts picked files in the order the user wants before Merge or Images to PDF runs on them. */
@Composable
private fun OrderFilesDialog(
    title: String,
    items: List<Uri>,
    nameOf: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit
) {
    val order = remember(items) { androidx.compose.runtime.mutableStateListOf<Uri>().apply { addAll(items) } }
    val names = remember(items) { items.associateWith(nameOf) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.tools_order_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(order, key = { _, uri -> uri.toString() }) { index, uri ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.tools_order_position, index + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                            Text(names[uri] ?: stringResource(R.string.tools_file_fallback_name), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(enabled = index > 0, onClick = { order.add(index - 1, order.removeAt(index)) }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_order_move_up))
                            }
                            IconButton(enabled = index < order.lastIndex, onClick = { order.add(index + 1, order.removeAt(index)) }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_order_move_down))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(order.toList()) }) { Text(stringResource(R.string.common_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private data class OrderedImage(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val name: String,
    /** The picked photo, untouched: a second crop starts from this, not from the first crop's result. */
    val original: File = file,
    val rotation: Int = 0
) {
    val isEdited: Boolean get() = file != original || rotation != 0
}

private val OrderedImageListSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<OrderedImage>, String>(
    save = { list ->
        list.map { item ->
            "${item.id}|||${item.file.absolutePath}|||${item.name}|||${item.original.absolutePath}|||${item.rotation}"
        }
    },
    restore = { serializedList ->
        mutableStateListOf<OrderedImage>().apply {
            serializedList.forEach { str ->
                val parts = str.split("|||")
                if (parts.size >= 5) {
                    add(
                        OrderedImage(
                            id = parts[0],
                            file = File(parts[1]),
                            name = parts[2],
                            original = File(parts[3]),
                            rotation = parts[4].toIntOrNull() ?: 0
                        )
                    )
                }
            }
        }
    }
)

/**
 * Puts picked images in order, shows their thumbnails, and allows perspective cropping, per-image rotation,
 * adding more images, or removing pages before PDF generation. State is preserved across device rotation,
 * and staging folders are reliably cleaned up.
 */
@Composable
private fun ImagesToPdfOrderDialog(
    initialUris: List<Uri>,
    displayName: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val stagingDirPath = rememberSaveable {
        val dir = File(context.cacheDir, "img2pdf_order_${UUID.randomUUID()}").apply { mkdirs() }
        dir.absolutePath
    }
    val stagingDir = remember(stagingDirPath) { File(stagingDirPath).apply { mkdirs() } }

    val order = rememberSaveable(saver = OrderedImageListSaver) { mutableStateListOf<OrderedImage>() }
    var isLoading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var croppingItem by remember { mutableStateOf<OrderedImage?>(null) }
    var cropFailed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val addImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { newUris ->
        if (newUris.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                val currentCount = order.size
                val loaded = withContext(Dispatchers.IO) {
                    newUris.mapIndexedNotNull { index, uri ->
                        runCatching {
                            val name = displayName(uri)
                            val ext = if (name.contains('.')) name.substringAfterLast('.', "jpg") else "jpg"
                            val file = File(stagingDir, "item_${currentCount + index}_${System.currentTimeMillis()}.$ext")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (file.length() > 0 && ImageProcessor.imageAspect(file.absolutePath) != null) {
                                OrderedImage(file = file, name = name)
                            } else {
                                file.delete()
                                null
                            }
                        }.getOrNull()
                    }
                }
                order.addAll(loaded)
                isProcessing = false
            }
        }
    }

    val dismissAndCleanup = {
        stagingDir.deleteRecursively()
        onDismiss()
    }

    LaunchedEffect(initialUris) {
        if (order.isEmpty()) {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                initialUris.mapIndexedNotNull { index, uri ->
                    runCatching {
                        val name = displayName(uri)
                        val ext = if (name.contains('.')) name.substringAfterLast('.', "jpg") else "jpg"
                        val file = File(stagingDir, "item_${index}_${System.currentTimeMillis()}.$ext")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (file.length() > 0 && ImageProcessor.imageAspect(file.absolutePath) != null) {
                            OrderedImage(file = file, name = name)
                        } else {
                            file.delete()
                            null
                        }
                    }.getOrNull()
                }
            }
            order.clear()
            order.addAll(loaded)
            isLoading = false
        }
    }

    val rotateItem: (Int) -> Unit = { index ->
        val item = order.getOrNull(index)
        if (item != null) {
            isProcessing = true
            scope.launch {
                val rotated = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = item.file
                        val dest = File(stagingDir, "rotated_${System.currentTimeMillis()}_${source.nameWithoutExtension}.jpg")
                        ImageProcessor.rotateImageFile(source, dest, 90)
                        dest
                    }
                }
                isProcessing = false
                rotated.onSuccess { dest ->
                    val idx = order.indexOfFirst { it.id == item.id }
                    if (idx != -1) {
                        val previous = order[idx].file
                        order[idx] = order[idx].copy(
                            file = dest,
                            rotation = (order[idx].rotation + 90) % 360
                        )
                        if (previous != item.original && previous != dest) previous.delete()
                    } else dest.delete()
                }
            }
        }
    }

    val rotateAll: () -> Unit = {
        if (order.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    order.indices.forEach { index ->
                        val item = order[index]
                        runCatching {
                            val source = item.file
                            val dest = File(stagingDir, "rotated_${System.currentTimeMillis()}_${source.nameWithoutExtension}.jpg")
                            ImageProcessor.rotateImageFile(source, dest, 90)
                            withContext(Dispatchers.Main) {
                                val previous = order[index].file
                                order[index] = order[index].copy(
                                    file = dest,
                                    rotation = (order[index].rotation + 90) % 360
                                )
                                if (previous != item.original && previous != dest) previous.delete()
                            }
                        }
                    }
                }
                isProcessing = false
            }
        }
    }

    val resetItem: (Int) -> Unit = { index ->
        val item = order.getOrNull(index)
        if (item != null && item.isEdited) {
            val previous = item.file
            order[index] = item.copy(file = item.original, rotation = 0)
            if (previous != item.original) previous.delete()
        }
    }

    val removeItem: (Int) -> Unit = { index ->
        if (index in order.indices) {
            val removed = order.removeAt(index)
            if (removed.file != removed.original) removed.file.delete()
            removed.original.delete()
        }
    }

    val currentCrop = croppingItem
    if (currentCrop != null) {
        CropEditorDialog(
            sourcePath = currentCrop.file.absolutePath,
            initial = CropQuad(),
            onDismiss = { croppingItem = null },
            onConfirm = { quad ->
                croppingItem = null
                isProcessing = true
                scope.launch {
                    val cropped = withContext(Dispatchers.IO) {
                        runCatching {
                            val source = currentCrop.file
                            val dest = File(stagingDir, "cropped_${System.currentTimeMillis()}_${source.nameWithoutExtension}.jpg")
                            ImageProcessor.cropSourceToNewFile(source, dest, quad)
                            dest
                        }
                    }
                    isProcessing = false
                    cropped.onSuccess { dest ->
                        val idx = order.indexOfFirst { it.id == currentCrop.id }
                        if (idx != -1) {
                            val previous = order[idx].file
                            order[idx] = order[idx].copy(file = dest)
                            if (previous != currentCrop.original && previous != dest) previous.delete()
                        } else dest.delete()
                    }.onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        cropFailed = true
                    }
                }
            }
        )
    }

    if (cropFailed) {
        AlertDialog(
            onDismissRequest = { cropFailed = false },
            title = { Text(stringResource(R.string.tools_img2pdf_crop_failed_title)) },
            text = { Text(stringResource(R.string.tools_img2pdf_crop_failed)) },
            confirmButton = { TextButton(onClick = { cropFailed = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }

    val isShortWindow = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val maxListHeight = if (isShortWindow) 220.dp else 400.dp

    AlertDialog(
        onDismissRequest = dismissAndCleanup,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.tools_order_pages_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${order.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (isLoading || isProcessing) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column {
                    Text(
                        stringResource(R.string.tools_img2pdf_order_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { addImagesLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.tools_img2pdf_add_images),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(
                            onClick = rotateAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.tools_img2pdf_rotate_all),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (order.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.tools_err_no_images),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxListHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(order, key = { _, item -> item.id }, contentType = { _, _ -> "ordered_image" }) { index, item ->
                                OrderedImageRow(
                                    index = index,
                                    item = item,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < order.lastIndex,
                                    canRemove = true,
                                    onMoveUp = { order.add(index - 1, order.removeAt(index)) },
                                    onMoveDown = { order.add(index + 1, order.removeAt(index)) },
                                    onCrop = { croppingItem = item },
                                    onRotate = { rotateItem(index) },
                                    onReset = { resetItem(index) },
                                    onRemove = { removeItem(index) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(order.map { Uri.fromFile(it.file) }) },
                enabled = !isLoading && !isProcessing && order.isNotEmpty()
            ) {
                Text(stringResource(R.string.common_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = dismissAndCleanup) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun OrderedImageRow(
    index: Int,
    item: OrderedImage,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit
) {
    val thumbnail by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, item.file) {
        value = withContext(Dispatchers.IO) {
            ImageProcessor.decodeOrientedScaled(item.file.absolutePath, 200)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Top row: Position badge, thumbnail (60dp), filename, and remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onCrop),
                    contentAlignment = Alignment.Center
                ) {
                    val bm = thumbnail
                    if (bm != null) {
                        Image(
                            bitmap = bm.asImageBitmap(),
                            contentDescription = stringResource(R.string.tools_page_number, index + 1),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }

                    if (item.isEdited) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(10.dp)
                        ) {}
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isEdited) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tools_img2pdf_edited_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (item.rotation != 0) {
                                Text(
                                    text = " • +${item.rotation}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.tools_img2pdf_remove_cd, index + 1),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom row: Crop, Rotate, Reset buttons and Move Up/Down
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCrop,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(
                        Icons.Default.Crop,
                        contentDescription = stringResource(R.string.tools_img2pdf_crop_cd, index + 1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.tools_img2pdf_crop_button),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onRotate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = stringResource(R.string.tools_img2pdf_rotate_cd, index + 1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.tools_img2pdf_rotate_button),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                if (item.isEdited) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.tools_img2pdf_reset_cd, index + 1),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            stringResource(R.string.common_reset),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.tools_order_move_up)
                    )
                }
                IconButton(
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.tools_order_move_down)
                    )
                }
            }
        }
    }
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

/** A small grey heading above each group of tiles. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Icon tiles with one short label each, four to a row on a phone and eight on a wide window, every row
 * the same width. Same shape as the shortcuts on the library screen.
 */
@Composable
private fun ToolTileGrid(tools: List<PdfToolCard>, onClick: (PdfToolCard) -> Unit) {
    val perRow = if (com.example.scanpro.navigation.isWideWindow()) 8 else 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tool ->
                    val name = stringResource(tool.title)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(role = Role.Button, onClickLabel = name) { onClick(tool) }
                            .semantics { contentDescription = name }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        IconTile(tool.icon, tool.tone, size = 44.dp, corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(tool.label),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = MaterialTheme.typography.labelMedium.lineHeight
                        )
                    }
                }
                // A short last row keeps its cells the same width as the rows above.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tool that is not a job on a file so much as a way to read one: a single row rather than a tile. */
@Composable
private fun PlainToolRow(tool: PdfToolCard, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(tool.icon, tool.tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(tool.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
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
    label: String,
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

                    val defaultTitle = stringResource(R.string.tools_processing_pdf)
                    val showSubtitle = label.isNotBlank() && label != defaultTitle
                    val title = if (total > 0 && showSubtitle) defaultTitle else label.ifBlank { defaultTitle }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (total > 0 && showSubtitle) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
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
private fun ResultDialog(
    result: PdfToolResult,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onSaveAs: () -> Unit
) {
    val context = LocalContext.current
    val saveLabel = stringResource(
        when (mimeFor(result.outputFile)) {
            "application/zip" -> R.string.tools_result_save_zip_cd
            "application/pdf" -> R.string.tools_result_save_pdf_cd
            else -> R.string.tools_result_save_image_cd
        }
    )

    val isCompression = result.originalSizeBytes > 0 && result.outputSizeBytes > 0
    val savedBytes = if (isCompression) (result.originalSizeBytes - result.outputSizeBytes).coerceAtLeast(0L) else 0L
    val savingsPct = if (isCompression) {
        ((savedBytes.toDouble() / result.originalSizeBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
    } else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.outputFile.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCompression) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            if (savingsPct > 0) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Compress,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.tools_result_saved_badge, savingsPct),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.tools_result_original_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        formatBytes(context, result.originalSizeBytes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.tools_result_compressed_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        formatBytes(context, result.outputSizeBytes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Button: Preview PDF
                FilledTonalButton(
                    onClick = onView,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (result.outputFile.extension.equals("pdf", ignoreCase = true))
                                R.string.tools_result_preview_pdf
                            else
                                R.string.tools_result_open
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Actions: Share & Save PDF with icon and label grouped vertically
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                shareFile(context, result.outputFile.absolutePath, mimeFor(result.outputFile), result.outputFile.name)
                            }
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.common_share),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.common_share),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSaveAs() }
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = saveLabel,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.tools_share_save_to_device),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    )
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
                        style = MaterialTheme.typography.titleMedium,
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
                        enabled = !isLoading && pages.isNotEmpty() && (mode == "rotate" || keptCount > 0)
                    ) {
                        Text(
                            when (mode) {
                                "extract" -> stringResource(R.string.tools_organizer_extract_count, keptCount)
                                "organize" -> stringResource(R.string.tools_organizer_save_count, keptCount)
                                else -> stringResource(R.string.tools_organizer_save_pdf)
                            }
                        )
                    }
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
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1200.dp),
                    // Content padding, not a padded box: the pages scroll right up to the edges instead of being cut off.
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages, key = { _, item -> item.index }, contentType = { _, _ -> "organizer_page" }) { index, item ->
                        OrganizerPageCard(
                            item = item,
                            session = session,
                            showCheckbox = mode != "rotate",
                            canMoveLeft = mode == "organize" && index > 0,
                            canMoveRight = mode == "organize" && index < pages.lastIndex,
                            onToggle = { change { pages[index] = item.copy(selected = !item.selected) } },
                            onRotate = { change { pages[index] = item.copy(rotation = (item.rotation + 90) % 360) } },
                            onMoveLeft = { change { pages.add(index - 1, pages.removeAt(index)) } },
                            onMoveRight = { change { pages.add(index + 1, pages.removeAt(index)) } },
                            onMoveToStart = { change { pages.add(0, pages.removeAt(index)) } },
                            onMoveToEnd = { change { pages.add(pages.removeAt(index)) } },
                            onPreview = { previewPage = item }
                        )
                    }
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

/** One page in the organiser. Its thumbnail is rendered only while the card is on screen. */
@Composable
private fun OrganizerPageCard(
    item: ThumbnailPageModel,
    session: PdfPreviewSession?,
    showCheckbox: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onToggle: () -> Unit,
    onRotate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onMoveToStart: () -> Unit = {},
    onMoveToEnd: () -> Unit = {},
    onPreview: () -> Unit = {}
) {
    val thumbnail by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, session, item.index) {
        val preview = session ?: return@produceState
        value = withContext(Dispatchers.IO) { preview.render(item.index, 300) }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // One focus target per card, read as a checkbox with its state, rather than a card plus a separate box.
            .then(
                if (showCheckbox) Modifier.toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onToggle() })
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (showCheckbox && item.selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCheckbox) Checkbox(checked = item.selected, onCheckedChange = null)
                    Text(stringResource(R.string.tools_page_number, item.index + 1), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.tools_organizer_preview_page, item.index + 1),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = onPreview,
                        onLongClickLabel = stringResource(R.string.tools_organizer_preview_page, item.index + 1)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.tools_page_number, item.index + 1),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = item.rotation.toFloat() }
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(onClick = onRotate) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = stringResource(R.string.tools_organizer_rotate_page_cd, item.index + 1))
                }
                // Tap: one step. Long press: all the way to the start or end.
                if (canMoveLeft) {
                    Box(
                        Modifier.size(48.dp).combinedClickable(onClick = onMoveLeft, onLongClick = onMoveToStart, onLongClickLabel = stringResource(R.string.tools_organizer_move_to_start)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = stringResource(R.string.tools_organizer_move_earlier_cd, item.index + 1)) }
                }
                if (canMoveRight) {
                    Box(
                        Modifier.size(48.dp).combinedClickable(onClick = onMoveRight, onLongClick = onMoveToEnd, onLongClickLabel = stringResource(R.string.tools_organizer_move_to_end)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = stringResource(R.string.tools_organizer_move_later_cd, item.index + 1)) }
                }
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

private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "zip" -> "application/zip"
    else -> "application/pdf"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CompressImageDialog(
    context: Context,
    uri: Uri,
    onDismiss: () -> Unit,
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
    val targetBytes = if (mode == 0 && targetKb > 0) targetKb * 1024L else null

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
                    Spacer(Modifier.height(12.dp))
                }

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
                        listOf(50, 100, 200, 500).forEach { kb ->
                            FilterChip(
                                selected = selectedPresetKb == kb,
                                onClick = {
                                    selectedPresetKb = kb
                                    customKbInput = kb.toString()
                                },
                                label = { Text("$kb KB") }
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
    onConfirm: (targetFormat: ImageExportFormat, quality: Int) -> Unit
) {
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.PNG) }
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (format == ImageExportFormat.PNG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (format == ImageExportFormat.PNG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { format = ImageExportFormat.PNG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == ImageExportFormat.PNG,
                                onClick = { format = ImageExportFormat.PNG }
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
                    color = if (format == ImageExportFormat.JPEG) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (format == ImageExportFormat.JPEG) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { format = ImageExportFormat.JPEG }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == ImageExportFormat.JPEG,
                                onClick = { format = ImageExportFormat.JPEG }
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
                        if (format == ImageExportFormat.JPEG) {
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
                    color = if (format == ImageExportFormat.WEBP) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (format == ImageExportFormat.WEBP) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { format = ImageExportFormat.WEBP }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == ImageExportFormat.WEBP,
                                onClick = { format = ImageExportFormat.WEBP }
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
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(format, if (format == ImageExportFormat.PNG) 100 else jpegQuality) }) {
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
    onConfirm: (ImageResizeConfig) -> Unit
) {
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var widthInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var lockAspect by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }

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

    val w = widthInput.toIntOrNull() ?: 0
    val h = heightInput.toIntOrNull() ?: 0
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
                    FilterChip(
                        selected = w == 600 && h == 600,
                        onClick = {
                            lockAspect = false
                            widthInput = "600"
                            heightInput = "600"
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_preset_passport)) }
                    )
                    FilterChip(
                        selected = w == 400 && h == 400,
                        onClick = {
                            lockAspect = false
                            widthInput = "400"
                            heightInput = "400"
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_preset_avatar)) }
                    )
                    FilterChip(
                        selected = w == 1920 && h == 1080,
                        onClick = {
                            lockAspect = false
                            widthInput = "1920"
                            heightInput = "1080"
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_preset_fhd)) }
                    )
                    FilterChip(
                        selected = w == 1280 && h == 720,
                        onClick = {
                            lockAspect = false
                            widthInput = "1280"
                            heightInput = "720"
                        },
                        label = { Text(stringResource(R.string.tools_resize_img_preset_hd)) }
                    )
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
                            outputFormat = format
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
