package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.PageRanges
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfPreviewSession
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.SplitMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfToolCard(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    @StringRes val badge: Int? = null,
    /** Colour family of the icon tile, so related tools share a hue. */
    val tone: TileTone = TileTone.Indigo
)

private val PDF_TOOLS = listOf(
    PdfToolCard("fill", R.string.tools_card_fill, R.string.tools_fill_form_desc, Icons.Default.EditNote, badge = R.string.tools_badge_new, tone = TileTone.Violet),
    PdfToolCard("ocr", R.string.tools_card_ocr, R.string.tools_make_searchable_desc, Icons.Default.FindInPage, badge = R.string.tools_badge_new, tone = TileTone.Teal),
    PdfToolCard("compress", R.string.tools_card_compress, R.string.tools_card_compress_desc, Icons.Default.Compress, tone = TileTone.Amber),
    PdfToolCard("merge", R.string.tools_card_merge, R.string.tools_card_merge_desc, Icons.Default.Merge, tone = TileTone.Indigo),
    PdfToolCard("split", R.string.tools_card_split, R.string.tools_card_split_desc, Icons.AutoMirrored.Filled.CallSplit, tone = TileTone.Indigo),
    PdfToolCard("extract", R.string.tools_card_extract, R.string.tools_card_extract_desc, Icons.AutoMirrored.Filled.DriveFileMove, tone = TileTone.Indigo),
    PdfToolCard("rotate", R.string.tools_card_rotate, R.string.tools_card_rotate_desc, Icons.AutoMirrored.Filled.RotateRight, tone = TileTone.Sky),
    PdfToolCard("organize", R.string.tools_card_organize, R.string.tools_card_organize_desc, Icons.Default.Description, tone = TileTone.Sky),
    PdfToolCard("img2pdf", R.string.tools_images_to_pdf, R.string.tools_card_img2pdf_desc, Icons.Default.Image, tone = TileTone.Coral),
    PdfToolCard("pdf2img", R.string.tools_card_pdf2img, R.string.tools_card_pdf2img_desc, Icons.Default.PictureAsPdf, tone = TileTone.Coral),
    PdfToolCard("protect", R.string.tools_card_protect, R.string.tools_card_protect_desc, Icons.Default.Lock, tone = TileTone.Rose),
    PdfToolCard("unlock", R.string.tools_card_unlock, R.string.tools_card_unlock_desc, Icons.Default.LockOpen, tone = TileTone.Rose),
    PdfToolCard("viewer", R.string.tools_card_viewer, R.string.tools_card_viewer_desc, Icons.Default.Visibility, tone = TileTone.Slate)
)

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
    val snackbarHostState = remember { SnackbarHostState() }
    // Read here: the picker callbacks and dialog buttons below are not composable scopes.
    val pdfDocumentTitle = stringResource(R.string.tools_pdf_document)

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val lastResult by viewModel.result.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val unlockNotice by viewModel.unlockNotice.collectAsStateWithLifecycle()
    val passwordNeeded by viewModel.passwordNeeded.collectAsStateWithLifecycle()
    val wrongPassword by viewModel.wrongPassword.collectAsStateWithLifecycle()
    val recentPdfs by viewModel.recentPdfs.collectAsStateWithLifecycle()
    val formSession by viewModel.form.collectAsStateWithLifecycle()
    var showOcrDialog by rememberSaveable { mutableStateOf(false) }

    // Saveable, so an open dialog and the chosen file survive rotation, window resizing and folding.
    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showCompressDialog by rememberSaveable { mutableStateOf(false) }
    var showPdfToImagesDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showSplitDialog by rememberSaveable { mutableStateOf(false) }
    var showOrganizerSheet by rememberSaveable { mutableStateOf(false) }

    // Show first, clear after: clearing first changes this effect's key, which cancelled the snackbar the moment
    // it appeared, so no tool message (errors, "wrong password", "cancelled") was ever visible.
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

    // SAF save: one launcher per output type, so the system picker suggests the right extension.
    var saveSourceFile by remember { mutableStateOf<File?>(null) }
    fun saveTo(destUri: Uri?) {
        val file = saveSourceFile ?: return
        if (destUri == null) return
        // The ViewModel does the copy off the main thread and reports through its message flow, so a
        // rotation during a large copy to a cloud provider neither freezes nor cancels it.
        viewModel.saveTo(file, destUri)
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val saveJpegLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { saveTo(it) }
    // Declared with its own type: a PNG saved through the JPEG launcher is renamed "page.png.jpg" by some providers.
    val savePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { saveTo(it) }

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
        }
    }
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        viewModel.rememberPdfs(listOf(uri))
        startTool(tool, uri)
    }
    // Files picked for Merge or Images to PDF are shown in a list to put in order before the tool runs:
    // the picker's order is not the order they were tapped in.
    var ordering by remember { mutableStateOf<List<Uri>?>(null) }
    var imagesToConvert by remember { mutableStateOf<List<Uri>?>(null) }
    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "merge") {
            viewModel.rememberPdfs(uris)
            if (uris.size == 1) viewModel.merge(uris) else ordering = uris
        }
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") {
            if (uris.size == 1) imagesToConvert = uris else ordering = uris
        }
    }
    ordering?.let { picked ->
        OrderFilesDialog(
            title = stringResource(if (selectedTool == "merge") R.string.tools_order_merge_title else R.string.tools_order_pages_title),
            items = picked,
            nameOf = viewModel::displayName,
            onDismiss = { ordering = null },
            onConfirm = { inOrder ->
                ordering = null
                if (selectedTool == "merge") viewModel.merge(inOrder) else imagesToConvert = inOrder
            }
        )
    }
    imagesToConvert?.let { images ->
        ImagesToPdfDialog(
            count = images.size,
            onDismiss = { imagesToConvert = null },
            onConfirm = { size, quality ->
                imagesToConvert = null
                viewModel.imagesToPdf(images, size, quality)
            }
        )
    }

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
            LazyVerticalGrid(
                // Cards keep a readable size: two columns on a 360 dp phone, more on wider windows, and the grid
                // stops growing past a comfortable width on tablets instead of stretching cards across the screen.
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 1100.dp)
            ) {
                if (recentPdfs.isNotEmpty()) {
                    item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                        RecentPdfsRow(
                            items = recentPdfs,
                            onOpen = { onOpenPdfViewer(it.uri, it.name) },
                            onUseWith = { pdf, tool -> startTool(tool, pdf.uri) },
                            onRemove = { viewModel.forgetPdf(it.uri) },
                            onClear = viewModel::clearRecentPdfs
                        )
                    }
                }
                items(PDF_TOOLS, key = { it.id }) { tool ->
                    ToolCardItem(
                        tool = tool,
                        onClick = {
                            selectedTool = tool.id
                            when (tool.id) {
                                "merge" -> multiPdfPicker.launch(arrayOf("application/pdf"))
                                "img2pdf" -> imagesPicker.launch("image/*")
                                else -> singlePdfPicker.launch(arrayOf("application/pdf"))
                            }
                        }
                    )
                }
            }

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
                        // The built-in viewer only reads PDFs; images and zips open through the share sheet.
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
                    onConfirm = { preset ->
                        showCompressDialog = false
                        viewModel.compress(uri, preset)
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
    }
}

@Composable
private fun CompressPdfDialog(
    context: android.content.Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (preset: CompressionPreset) -> Unit
) {
    var originalSize by remember { mutableLongStateOf(0L) }
    var preset by remember { mutableStateOf(CompressionPreset.BALANCED) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            originalSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_compress_pdf)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.tools_compress_current_size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(originalSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Three real presets rather than a slider: the engine has three settings, and how much a
                // file shrinks depends on what is in it, so no number is promised up front.
                CompressionPreset.entries.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { preset = item }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = preset == item, onClick = { preset = item })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(item.labelRes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(item.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = { onConfirm(preset) }) {
                Text(stringResource(R.string.tools_compress_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun PdfToImagesDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (format: ImageExportFormat, pages: List<Int>?, scale: Float) -> Unit
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(ImageExportFormat.JPEG) }
    var scale by remember { mutableFloatStateOf(2.5f) }
    var allPages by remember { mutableStateOf(true) }
    var rangesText by remember { mutableStateOf("") }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageExportFormat.entries.forEach { item ->
                        FilterChip(selected = format == item, onClick = { format = item }, label = { Text(item.extension.uppercase()) })
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.tools_pdf2img_resolution), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
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

/** Page size and quality for Images to PDF, the two settings the engine already takes. */
@Composable
private fun ImagesToPdfDialog(count: Int, onDismiss: () -> Unit, onConfirm: (PdfPageSize, PdfQuality) -> Unit) {
    var size by remember { mutableStateOf(PdfPageSize.AUTO) }
    var quality by remember { mutableStateOf(PdfQuality.BALANCED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.tools_img2pdf_title, count, count)) },
        text = {
            Column {
                Text(stringResource(R.string.tools_img2pdf_page_size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { item -> FilterChip(selected = size == item, onClick = { size = item }, label = { Text(stringResource(item.labelRes)) }) }
                }
                Text(
                    if (size == PdfPageSize.AUTO) stringResource(R.string.tools_img2pdf_auto_hint) else stringResource(R.string.tools_img2pdf_fitted_hint, stringResource(size.labelRes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.tools_img2pdf_quality), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfQuality.entries.forEach { item -> FilterChip(selected = quality == item, onClick = { quality = item }, label = { Text(stringResource(item.labelRes)) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(size, quality) }) { Text(stringResource(R.string.tools_img2pdf_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** PDFs opened before, newest first: tap to read, long-press for the tools or to remove from the list. */
@Composable
private fun RecentPdfsRow(
    items: List<RecentPdf>,
    onOpen: (RecentPdf) -> Unit,
    onUseWith: (RecentPdf, String) -> Unit,
    onRemove: (RecentPdf) -> Unit,
    onClear: () -> Unit
) {
    var removing by remember { mutableStateOf<RecentPdf?>(null) }
    var menuFor by remember { mutableStateOf<RecentPdf?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tools_recent_pdfs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            var confirmClear by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.tools_recent_clear)) }
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.uri.toString() }) { pdf ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClickLabel = stringResource(R.string.tools_recent_open),
                            onLongClickLabel = stringResource(R.string.tools_recent_use_with_tool),
                            onLongClick = { menuFor = pdf },
                            onClick = { onOpen(pdf) }
                        )
                ) {
                    Box {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.PictureAsPdf, TileTone.Coral, size = 36.dp, corner = 10.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(pdf.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        // Long-press: the same file straight into a tool, without picking it again.
                        DropdownMenu(expanded = menuFor == pdf, onDismissRequest = { menuFor = null }) {
                            PDF_TOOLS.filter { it.id !in setOf("merge", "img2pdf") }.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(tool.title)) },
                                    leadingIcon = { Icon(tool.icon, contentDescription = null) },
                                    onClick = { menuFor = null; onUseWith(pdf, tool.id) }
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_remove_from_recent)) }, onClick = { menuFor = null; removing = pdf })
                        }
                    }
                }
            }
        }
    }
    removing?.let { pdf ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.tools_recent_remove_title)) },
            text = { Text(stringResource(R.string.tools_recent_remove_body, pdf.name)) },
            confirmButton = { TextButton(onClick = { onRemove(pdf); removing = null }) { Text(stringResource(R.string.tools_recent_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun ToolCardItem(
    tool: PdfToolCard,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(tool.icon, tool.tone, size = 44.dp, corner = 14.dp)
                Spacer(Modifier.weight(1f))
                tool.badge?.let { badge ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(tool.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(tool.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        Box(contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tools_processing_pdf),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        // Screen readers hear each step as it changes.
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                    )
                    if (total > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tools_progress_count, current, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(result.outputFile.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Buttons: Preview PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onView,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(if (result.outputFile.extension.equals("pdf", ignoreCase = true)) R.string.tools_result_preview_pdf else R.string.tools_result_open))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direct Quick Action Cards: Share & Save PDF to Local
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Card(
                        onClick = { shareFile(context, result.outputFile.absolutePath, mimeFor(result.outputFile), result.outputFile.name) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.common_share), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Card(
                        onClick = onSaveAs,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Download, contentDescription = saveLabel, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(stringResource(R.string.common_share), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.tools_share_save_to_device), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        }
    )
}

@Composable
private fun PasswordDialog(
    isProtect: Boolean,
    initialError: String? = null,
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
        title = { Text(stringResource(if (isProtect) R.string.tools_password_protect_title else R.string.tools_unlock_pdf)) },
        text = {
            Column {
                Text(
                    if (isProtect) pluralStringResource(R.plurals.tools_password_protect_warning, PdfSecurity.MIN_PASSWORD_LENGTH, PdfSecurity.MIN_PASSWORD_LENGTH)
                    else stringResource(R.string.tools_password_unlock_prompt),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text(stringResource(R.string.tools_password_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                Text(stringResource(if (isProtect) R.string.tools_protect_pdf else R.string.tools_unlock_pdf))
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
    var mode by remember { mutableStateOf(SplitMode.SINGLE_PAGES) }
    var rangesText by remember { mutableStateOf("") }
    var chunkSizeText by remember { mutableStateOf("2") }
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
            } catch (error: SecurityException) {
                loadError = protectedError
            } catch (error: Exception) {
                loadError = error.localizedMessage ?: openFailedError
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (mode) {
                                "extract" -> R.string.tools_organizer_extract_title
                                "rotate" -> R.string.tools_organizer_rotate_title
                                else -> R.string.tools_organizer_organize_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                        .widthIn(max = 1200.dp)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages, key = { _, item -> item.index }) { index, item ->
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
                            onMoveToEnd = { change { pages.add(pages.removeAt(index)) } }
                        )
                    }
                }
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
    onMoveToEnd: () -> Unit = {}
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
                if (showCheckbox) Checkbox(checked = item.selected, onCheckedChange = null)
                Text(stringResource(R.string.tools_page_number, item.index + 1), style = MaterialTheme.typography.labelMedium)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.tools_page_number, item.index + 1),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(rotationZ = item.rotation.toFloat())
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

private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "zip" -> "application/zip"
    else -> "application/pdf"
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
