package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.example.scanpro.data.RecentPdf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.isActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.ui.copyFileToUri
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.core.ui.saveToGoogleDrive
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfToolCard(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String? = null,
    /** Colour family of the icon tile, so related tools share a hue. */
    val tone: com.example.scanpro.core.designsystem.TileTone = com.example.scanpro.core.designsystem.TileTone.Indigo
)

private val PDF_TOOLS = listOf(
    PdfToolCard("fill", "Fill form", "Type into a PDF form's fields, tick boxes and save", Icons.Default.EditNote, badge = "New", tone = com.example.scanpro.core.designsystem.TileTone.Violet),
    PdfToolCard("ocr", "Make searchable", "Add selectable, searchable text to scanned PDFs", Icons.Default.FindInPage, badge = "New", tone = com.example.scanpro.core.designsystem.TileTone.Teal),
    PdfToolCard("compress", "Compress", "Reduce PDF size with quality presets", Icons.Default.Compress, tone = com.example.scanpro.core.designsystem.TileTone.Amber),
    PdfToolCard("merge", "Merge", "Combine several PDFs into one document", Icons.Default.Merge, tone = com.example.scanpro.core.designsystem.TileTone.Indigo),
    PdfToolCard("split", "Split", "Divide a PDF by pages, ranges or fixed chunks", Icons.AutoMirrored.Filled.CallSplit, tone = com.example.scanpro.core.designsystem.TileTone.Indigo),
    PdfToolCard("extract", "Extract pages", "Pick specific pages and save them as a new PDF", Icons.AutoMirrored.Filled.DriveFileMove, tone = com.example.scanpro.core.designsystem.TileTone.Indigo),
    PdfToolCard("rotate", "Rotate pages", "Turn pages by 90°, 180° or 270°", Icons.AutoMirrored.Filled.RotateRight, tone = com.example.scanpro.core.designsystem.TileTone.Sky),
    PdfToolCard("organize", "Organise pages", "Reorder, delete and manage pages", Icons.Default.Description, tone = com.example.scanpro.core.designsystem.TileTone.Sky),
    PdfToolCard("img2pdf", "Images to PDF", "Turn gallery photos into a tidy PDF", Icons.Default.Image, tone = com.example.scanpro.core.designsystem.TileTone.Coral),
    PdfToolCard("pdf2img", "PDF to images", "Export pages as JPEG or PNG", Icons.Default.PictureAsPdf, tone = com.example.scanpro.core.designsystem.TileTone.Coral),
    PdfToolCard("protect", "Protect", "Make a copy that needs a password to open", Icons.Default.Lock, tone = com.example.scanpro.core.designsystem.TileTone.Rose),
    PdfToolCard("unlock", "Unlock", "Remove the password from a protected PDF", Icons.Default.LockOpen, tone = com.example.scanpro.core.designsystem.TileTone.Rose),
    PdfToolCard("viewer", "Viewer", "Read with pinch to zoom and search", Icons.Default.Visibility, tone = com.example.scanpro.core.designsystem.TileTone.Slate)
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
    val saveScope = rememberCoroutineScope()
    fun saveTo(destUri: Uri?) {
        val file = saveSourceFile ?: return
        if (destUri == null) return
        // The copy runs off the main thread: a large merged PDF or zip would otherwise freeze the screen.
        saveScope.launch {
            val ok = withContext(Dispatchers.IO) { copyFileToUri(context, file.absolutePath, destUri) }
            Toast.makeText(context, if (ok) "Saved to requested location" else "Could not save file", Toast.LENGTH_SHORT).show()
        }
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val saveJpegLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { saveTo(it) }
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
            title = if (selectedTool == "merge") "Merge order" else "Page order",
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
                title = { Text("PDF tools") },
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
                val restricted = protection == com.example.scanpro.imaging.PdfProtection.RESTRICTED_ONLY
                AlertDialog(
                    onDismissRequest = viewModel::clearUnlockNotice,
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text(if (restricted) "No password to open" else "This PDF has no password") },
                    text = {
                        Text(
                            if (restricted) "This PDF opens without a password, but it has editing or copying restrictions. You can remove those, or just view it."
                            else "There is nothing to unlock. You can view it, or protect it with a password instead."
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
                        }) { Text(if (restricted) "Remove restrictions" else "Set password") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                viewModel.clearUnlockNotice()
                                onOpenPdfViewer(noticeUri, "PDF Document")
                            }) { Text("View PDF") }
                            TextButton(onClick = viewModel::clearUnlockNotice) { Text("Cancel") }
                        }
                    }
                )
            }

            if (showPasswordDialog && uri != null && selectedTool != null) {
                val isProtect = selectedTool == "protect"
                PasswordDialog(
                    isProtect = isProtect,
                    initialError = if (!isProtect && wrongPassword) "That password didn't work. Try again." else null,
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
        title = { Text("Compress PDF") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(item.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Text stays selectable where the PDF already has it. A file that would not get smaller is kept as it is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(preset) }) {
                Text("Compress PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        title = { Text("PDF to images") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageExportFormat.entries.forEach { item ->
                        FilterChip(selected = format == item, onClick = { format = item }, label = { Text(item.extension.uppercase()) })
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("Resolution", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Screen" to 1.5f, "Sharp" to 2.5f, "Print" to 4f).forEach { (label, value) ->
                        FilterChip(selected = scale == value, onClick = { scale = value }, label = { Text(label) })
                    }
                }
                Text(
                    when (scale) { 1.5f -> "About 110 dpi: small files for messaging"; 4f -> "About 290 dpi: large files, fit for printing"; else -> "About 180 dpi: sharp on any screen" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Pages", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = allPages, onClick = { allPages = true; error = null }, label = { Text(pageCount?.let { "All $it" } ?: "All") })
                    FilterChip(selected = !allPages, onClick = { allPages = false }, label = { Text("Some") })
                }
                if (!allPages) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text("Pages, e.g. 1-3, 7") },
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
                else com.example.scanpro.imaging.PageRanges.parse(rangesText, pageCount ?: Int.MAX_VALUE)
                    .onSuccess { ranges -> onConfirm(format, ranges.flatMap { it.toList() }, scale) }
                    .onFailure { error = it.message }
            }) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                Text("Use the arrows to put the files in order. The first is the first in the result.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(order, key = { _, uri -> uri.toString() }) { index, uri ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                            Text(names[uri] ?: "File", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(enabled = index > 0, onClick = { order.add(index - 1, order.removeAt(index)) }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(enabled = index < order.lastIndex, onClick = { order.add(index + 1, order.removeAt(index)) }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(order.toList()) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Page size and quality for Images to PDF, the two settings the engine already takes. */
@Composable
private fun ImagesToPdfDialog(count: Int, onDismiss: () -> Unit, onConfirm: (PdfPageSize, PdfQuality) -> Unit) {
    var size by remember { mutableStateOf(PdfPageSize.AUTO) }
    var quality by remember { mutableStateOf(PdfQuality.BALANCED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Image to PDF" else "$count images to PDF") },
        text = {
            Column {
                Text("Page size", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { item -> FilterChip(selected = size == item, onClick = { size = item }, label = { Text(item.label) }) }
                }
                Text(
                    if (size == PdfPageSize.AUTO) "Each page takes the image's own proportions" else "Images are fitted onto ${size.label} pages",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Quality", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfQuality.entries.forEach { item -> FilterChip(selected = quality == item, onClick = { quality = item }, label = { Text(item.label) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(size, quality) }) { Text("Create PDF") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** PDFs opened before, newest first: tap to read, long-press for the tools or to remove from the list. */
@OptIn(ExperimentalFoundationApi::class)
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
            Text("Recent PDFs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            var confirmClear by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear recent PDFs?") },
                    text = { Text("The files stay on your device; only this list is cleared.") },
                    confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
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
                            onClickLabel = "Open",
                            onLongClickLabel = "Use with a tool",
                            onLongClick = { menuFor = pdf },
                            onClick = { onOpen(pdf) }
                        )
                ) {
                    Box {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.example.scanpro.core.designsystem.IconTile(Icons.Default.PictureAsPdf, com.example.scanpro.core.designsystem.TileTone.Coral, size = 36.dp, corner = 10.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(pdf.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        // Long-press: the same file straight into a tool, without picking it again.
                        DropdownMenu(expanded = menuFor == pdf, onDismissRequest = { menuFor = null }) {
                            PDF_TOOLS.filter { it.id !in setOf("merge", "img2pdf") }.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.title) },
                                    leadingIcon = { Icon(tool.icon, contentDescription = null) },
                                    onClick = { menuFor = null; onUseWith(pdf, tool.id) }
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(text = { Text("Remove from recent") }, onClick = { menuFor = null; removing = pdf })
                        }
                    }
                }
            }
        }
    }
    removing?.let { pdf ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from recent?") },
            text = { Text("\"${pdf.name}\" stays on your device; it is only removed from this list.") },
            confirmButton = { TextButton(onClick = { onRemove(pdf); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
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
                com.example.scanpro.core.designsystem.IconTile(tool.icon, tool.tone, size = 44.dp, corner = 14.dp)
                Spacer(Modifier.weight(1f))
                tool.badge?.let { badge ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            badge,
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
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description,
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
                        text = "Processing PDF",
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
                            text = "$current / $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultDialog(
    result: PdfToolResult,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onSaveAs: () -> Unit
) {
    val context = LocalContext.current
    val saveLabel = when (mimeFor(result.outputFile)) {
        "application/zip" -> "Save zip to device"
        "application/pdf" -> "Save PDF to device"
        else -> "Save image to device"
    }

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
                    Icon(Icons.Default.Close, contentDescription = "Close")
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
                        Text(if (result.outputFile.extension.equals("pdf", ignoreCase = true)) "Preview PDF" else "Open")
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
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    Text("Share", style = MaterialTheme.typography.labelSmall)
                    Text("Save to device", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isProtect) "Protect PDF with Password" else "Unlock PDF") },
        text = {
            Column {
                Text(
                    if (isProtect) "Anyone opening the copy will need this password (at least ${com.example.scanpro.imaging.PdfSecurity.MIN_PASSWORD_LENGTH} characters). There is no way to recover it."
                    else "Enter the password to remove protection from this PDF:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
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
                        label = { Text("Confirm password") },
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
                    // Unlocking must accept whatever password the file already has, however short.
                    val minimum = if (isProtect) com.example.scanpro.imaging.PdfSecurity.MIN_PASSWORD_LENGTH else 1
                    if (password.length < minimum) {
                        errorText = if (isProtect) "Password must be at least $minimum characters" else "Enter the PDF's password"
                    } else if (isProtect && confirm != password) {
                        errorText = "The passwords don't match"
                    } else {
                        onConfirm(password)
                    }
                }
            ) {
                Text(if (isProtect) "Protect PDF" else "Unlock PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        title = { Text("Split PDF Options") },
        text = {
            Column {
                Text(
                    pageCount?.let { "This PDF has ${if (it == 1) "1 page" else "$it pages"}. Choose how to split it:" } ?: "Choose how to split this document:",
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
                            Text(item.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (mode == SplitMode.PAGE_RANGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text("Page ranges, e.g. 1-3, 4-6") },
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
                        label = { Text("Pages per file") },
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
                        SplitMode.PAGE_RANGES -> com.example.scanpro.imaging.PageRanges.parse(rangesText, pages)
                            .onSuccess { onConfirm(mode, rangesText, 1) }
                            .onFailure { error = it.message }
                        SplitMode.EVERY_N_PAGES -> when {
                            n == null || n < 1 -> error = "Enter how many pages each file should have"
                            pageCount != null && n >= pageCount!! -> error = "That is the whole document; choose fewer than $pageCount pages"
                            else -> onConfirm(mode, rangesText, n)
                        }
                        SplitMode.SINGLE_PAGES -> onConfirm(mode, rangesText, 1)
                    }
                }
            ) {
                Text("Split PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    // Extract starts with nothing chosen; Organize starts with every page kept (untick to delete one).
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
    var session by remember { mutableStateOf<com.example.scanpro.imaging.PdfPreviewSession?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
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
        var opened: com.example.scanpro.imaging.PdfPreviewSession? = null
        val job = scope.launch {
            try {
                val preview = com.example.scanpro.imaging.PdfPreviewSession.open(context, uri)
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
                loadError = "This PDF is password-protected. Unlock it first with the Unlock PDF tool."
            } catch (error: Exception) {
                loadError = error.localizedMessage ?: "Could not open this PDF"
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
                        when (mode) {
                            "extract" -> "Select pages to extract"
                            "rotate" -> "Rotate pages"
                            else -> "Organize pages"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    var organizerMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { organizerMenu = true }, enabled = !isLoading) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = organizerMenu, onDismissRequest = { organizerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rotate all pages") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                                onClick = { organizerMenu = false; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(rotation = (pages[i].rotation + 90) % 360) } } }
                            )
                            if (mode != "rotate") {
                                DropdownMenuItem(
                                    text = { Text(if (pages.all { it.selected }) "Untick all" else "Tick all") },
                                    onClick = { organizerMenu = false; val all = pages.all { it.selected }; change { pages.indices.forEach { i -> pages[i] = pages[i].copy(selected = !all) } } }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Reset") },
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
                            // Organize drops the pages that were unticked; Rotate keeps every page.
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
                                "extract" -> "Extract ($keptCount)"
                                "organize" -> "Save ($keptCount)"
                                else -> "Save PDF"
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
                    Text("Opening PDF…")
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

/** One page in the organizer. Its thumbnail is rendered only while the card is on screen. */
@Composable
private fun OrganizerPageCard(
    item: ThumbnailPageModel,
    session: com.example.scanpro.imaging.PdfPreviewSession?,
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
                Text("Page ${item.index + 1}", style = MaterialTheme.typography.labelMedium)
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
                        contentDescription = "Page ${item.index + 1}",
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
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate page ${item.index + 1}")
                }
                // Tap: one step. Long press: all the way to the start or end.
                if (canMoveLeft) {
                    Box(
                        Modifier.size(48.dp).combinedClickable(onClick = onMoveLeft, onLongClick = onMoveToStart, onLongClickLabel = "Move to start"),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Move page ${item.index + 1} earlier") }
                }
                if (canMoveRight) {
                    Box(
                        Modifier.size(48.dp).combinedClickable(onClick = onMoveRight, onLongClick = onMoveToEnd, onLongClickLabel = "Move to end"),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Move page ${item.index + 1} later") }
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
