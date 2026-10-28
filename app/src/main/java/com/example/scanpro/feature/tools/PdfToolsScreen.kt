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
import androidx.compose.material.icons.filled.CallSplit
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val badge: String? = null
)

private val PDF_TOOLS = listOf(
    PdfToolCard("fill", "Fill Form", "Type into a PDF form's fields, tick boxes and save", Icons.Default.EditNote, badge = "New"),
    PdfToolCard("ocr", "Make Searchable", "Add selectable, searchable text to scanned PDFs", Icons.Default.FindInPage, badge = "New"),
    PdfToolCard("compress", "Compress PDF", "Reduce PDF size with quality preset options", Icons.Default.Compress),
    PdfToolCard("merge", "Merge PDFs", "Combine multiple PDFs into a single document", Icons.Default.Merge),
    PdfToolCard("split", "Split PDF", "Divide PDF by pages, ranges, or fixed chunks", Icons.Default.CallSplit),
    PdfToolCard("extract", "Extract Pages", "Pick specific pages and save as a new PDF", Icons.AutoMirrored.Filled.DriveFileMove),
    PdfToolCard("rotate", "Rotate Pages", "Rotate PDF pages 90°, 180°, or 270°", Icons.AutoMirrored.Filled.RotateRight),
    PdfToolCard("organize", "Organize Pages", "Reorder, delete, and manage PDF pages", Icons.Default.Description),
    PdfToolCard("img2pdf", "Images to PDF", "Convert gallery photos into a styled PDF", Icons.Default.Image),
    PdfToolCard("pdf2img", "PDF to Images", "Export PDF pages as JPEG or PNG images", Icons.Default.PictureAsPdf),
    PdfToolCard("protect", "Protect PDF", "Encrypt PDF with strong password protection", Icons.Default.Lock),
    PdfToolCard("unlock", "Unlock PDF", "Remove password security from a protected PDF", Icons.Default.LockOpen),
    PdfToolCard("viewer", "PDF Viewer", "Interactive reader with pinch-to-zoom & search", Icons.Default.Visibility)
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
        // Off the main thread: a large file going to a cloud provider takes long enough to freeze the screen.
        saveScope.launch {
            val ok = withContext(Dispatchers.IO) {
                copyFileToUri(context, file.absolutePath, destUri).also { copied ->
                    // The picker already created the destination; don't leave an empty file behind.
                    if (!copied) runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, destUri) }
                }
            }
            Toast.makeText(context, if (ok) "Saved to requested location" else "Could not save file", Toast.LENGTH_SHORT).show()
        }
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { saveTo(it) }
    // Declared with its own type: a PNG saved through the JPEG launcher is renamed "page.png.jpg" by some providers.
    val savePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { saveTo(it) }

    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        viewModel.rememberPdfs(listOf(uri))
        pendingUri = uri
        when (tool) {
            "compress" -> showCompressDialog = true
            "pdf2img" -> showPdfToImagesDialog = true
            "protect" -> showPasswordDialog = true
            "unlock" -> viewModel.inspectForUnlock(uri)
            "split" -> showSplitDialog = true
            "organize", "extract", "rotate" -> showOrganizerSheet = true
            "viewer" -> onOpenPdfViewer(uri, "PDF Document")
            "fill" -> viewModel.openForm(uri)
            "ocr" -> showOcrDialog = true
        }
    }
    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "merge") {
            viewModel.rememberPdfs(uris)
            viewModel.merge(uris)
        }
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") viewModel.imagesToPdf(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PDF Toolkit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                    onShare = { shareFile(context, result.outputFile.absolutePath, mimeFor(result.outputFile), result.outputFile.name) },
                    onSaveAs = {
                        saveSourceFile = result.outputFile
                        val name = result.outputFile.name
                        when (mimeFor(result.outputFile)) {
                            "application/zip" -> saveZipLauncher.launch(name)
                            "application/pdf" -> saveFileLauncher.launch(name)
                            "image/png" -> savePngLauncher.launch(name)
                            else -> saveImageLauncher.launch(name)
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
                    onDismiss = { showPdfToImagesDialog = false },
                    onConfirm = { format ->
                        showPdfToImagesDialog = false
                        viewModel.pdfToImages(uri, format)
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
    var compressionPercent by remember { mutableFloatStateOf(50f) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            originalSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        }
    }

    val estimatedSize = (originalSize * ((100f - compressionPercent) / 100f)).toLong().coerceAtLeast(1024L)
    val estimatedSavings = compressionPercent.toInt()

    val preset = when {
        compressionPercent >= 65f -> CompressionPreset.MAXIMUM
        compressionPercent >= 40f -> CompressionPreset.BALANCED
        else -> CompressionPreset.HIGH
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF Target") },
        text = {
            Column {
                Text("Drag slider to select target compression ratio:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Original Size:", style = MaterialTheme.typography.labelMedium)
                            Text(formatBytes(originalSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Output:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "~${formatBytes(estimatedSize)} (-$estimatedSavings%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compression Reduction:", style = MaterialTheme.typography.labelLarge)
                    Text("${compressionPercent.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = compressionPercent,
                    onValueChange = { compressionPercent = it },
                    valueRange = 15f..85f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("High Quality\n(15% reduction)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Max Compression\n(85% reduction)", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = compressionPercent in 15f..35f,
                        onClick = { compressionPercent = 25f },
                        modifier = Modifier.weight(1f),
                        label = { Text("High", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    )
                    FilterChip(
                        selected = compressionPercent in 36f..60f,
                        onClick = { compressionPercent = 50f },
                        modifier = Modifier.weight(1f),
                        label = { Text("Balanced", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    )
                    FilterChip(
                        selected = compressionPercent > 60f,
                        onClick = { compressionPercent = 75f },
                        modifier = Modifier.weight(1f),
                        label = { Text("Maximum", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    )
                }
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
    onDismiss: () -> Unit,
    onConfirm: (format: ImageExportFormat) -> Unit
) {
    var format by remember { mutableStateOf(ImageExportFormat.JPEG) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF to Images Options") },
        text = {
            Column {
                Text("Select target image export format:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ImageExportFormat.JPEG,
                        onClick = { format = ImageExportFormat.JPEG },
                        label = { Text("JPEG Image (.jpg)") }
                    )
                    FilterChip(
                        selected = format == ImageExportFormat.PNG,
                        onClick = { format = ImageExportFormat.PNG },
                        label = { Text("PNG Image (.png)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(format) }) {
                Text("Export Page Images")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** PDFs opened before, newest first: tap to read, long-press to remove from the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentPdfsRow(
    items: List<RecentPdf>,
    onOpen: (RecentPdf) -> Unit,
    onRemove: (RecentPdf) -> Unit,
    onClear: () -> Unit
) {
    var removing by remember { mutableStateOf<RecentPdf?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent PDFs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.uri.toString() }) { pdf ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClickLabel = "Open",
                            onLongClickLabel = "Remove from recent",
                            onLongClick = { removing = pdf },
                            onClick = { onOpen(pdf) }
                        )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(pdf.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                        textAlign = TextAlign.Center
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
    onShare: () -> Unit,
    onSaveAs: () -> Unit
) {
    val context = LocalContext.current
    var showShareSheet by remember { mutableStateOf(false) }

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
                        onClick = { showShareSheet = true },
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
                            Icon(Icons.Default.Download, contentDescription = "Save PDF to local", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("Share", style = MaterialTheme.typography.labelSmall)
                    Text("Save to Local", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )

    if (showShareSheet) {
        SmartShareSheet(
            file = result.outputFile,
            onDismiss = { showShareSheet = false },
            onSaveToLocal = {
                showShareSheet = false
                onSaveAs()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartShareSheet(
    file: File,
    onDismiss: () -> Unit,
    onSaveToLocal: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val kind = when (mimeFor(file)) {
                "application/zip" -> "ZIP"
                "image/png" -> "PNG"
                "image/jpeg" -> "JPG"
                else -> "PDF"
            }
            Text("Share as $kind via", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            // One share action with the file's real type. Offering "Share as JPG" for a PDF sent PDF bytes labelled
            // as an image, which receiving apps could not open.
            ListItem(
                modifier = Modifier.clickable {
                    onDismiss()
                    shareFile(context, file.absolutePath, mimeFor(file), file.name)
                },
                headlineContent = { Text("Share as $kind") },
                leadingContent = { Icon(if (kind == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Image, contentDescription = null) }
            )

            ListItem(
                modifier = Modifier.clickable {
                    onDismiss()
                    // Without the Drive app, the system picker still lists Drive, Dropbox, OneDrive and folders.
                    if (!saveToGoogleDrive(context, file.absolutePath, mimeFor(file))) onSaveToLocal()
                },
                headlineContent = { Text("Save to Google Drive") },
                supportingContent = { Text("Or pick Dropbox, OneDrive or another cloud app") },
                leadingContent = { Icon(Icons.Default.AddToDrive, contentDescription = null) }
            )

            ListItem(
                modifier = Modifier.clickable {
                    onDismiss()
                    onSaveToLocal()
                },
                headlineContent = { Text("Save to device") },
                supportingContent = { Text("Choose a folder such as Downloads") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PasswordDialog(
    isProtect: Boolean,
    initialError: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember(initialError) { mutableStateOf(initialError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isProtect) "Protect PDF with Password" else "Unlock PDF") },
        text = {
            Column {
                Text(
                    if (isProtect) "Enter a password (at least ${com.example.scanpro.imaging.PdfSecurity.MIN_PASSWORD_LENGTH} characters) to encrypt this PDF:"
                    else "Enter password to remove protection from this PDF:",
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
    onDismiss: () -> Unit,
    onConfirm: (SplitMode, String, Int) -> Unit
) {
    var mode by remember { mutableStateOf(SplitMode.SINGLE_PAGES) }
    var rangesText by remember { mutableStateOf("1-2, 3-5") }
    var chunkSizeText by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split PDF Options") },
        text = {
            Column {
                Text("Select how you would like to split this document:", style = MaterialTheme.typography.bodyMedium)
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
                        onValueChange = { rangesText = it },
                        label = { Text("Page Ranges (e.g., 1-3, 4-6)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mode == SplitMode.EVERY_N_PAGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chunkSizeText,
                        onValueChange = { chunkSizeText = it },
                        label = { Text("Pages per PDF chunk") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = chunkSizeText.toIntOrNull() ?: 1
                    onConfirm(mode, rangesText, n)
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

    androidx.compose.runtime.DisposableEffect(uri) {
        var opened: com.example.scanpro.imaging.PdfPreviewSession? = null
        val job = scope.launch {
            try {
                val preview = com.example.scanpro.imaging.PdfPreviewSession.open(context, uri)
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
                            onToggle = { pages[index] = item.copy(selected = !item.selected) },
                            onRotate = { pages[index] = item.copy(rotation = (item.rotation + 90) % 360) },
                            onMoveLeft = {
                                val prev = pages[index - 1]
                                pages[index - 1] = item
                                pages[index] = prev
                            },
                            onMoveRight = {
                                val next = pages[index + 1]
                                pages[index + 1] = item
                                pages[index] = next
                            }
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
    onMoveRight: () -> Unit
) {
    val thumbnail by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, session, item.index) {
        val preview = session ?: return@produceState
        value = withContext(Dispatchers.IO) { preview.render(item.index, 300) }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showCheckbox, onClick = onToggle),
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
                if (showCheckbox) Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
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
                if (canMoveLeft) {
                    IconButton(onClick = onMoveLeft) {
                        Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Move page ${item.index + 1} earlier")
                    }
                }
                if (canMoveRight) {
                    IconButton(onClick = onMoveRight) {
                        Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Move page ${item.index + 1} later")
                    }
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
