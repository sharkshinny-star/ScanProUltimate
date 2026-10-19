package com.example.scanpro.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
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
import com.example.scanpro.MainViewModel
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val selected: Boolean = true,
    val bitmap: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: MainViewModel,
    onOpenPdfViewer: (Uri, String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTool by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var progressLabel by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<PdfToolResult?>(null) }

    // Dialog state
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showPdfToImagesDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showOrganizerSheet by remember { mutableStateOf(false) }

    // SAF Save Launcher
    var saveSourceFile by remember { mutableStateOf<File?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
        if (destUri != null && saveSourceFile != null) {
            val ok = copyFileToUri(context, saveSourceFile!!.absolutePath, destUri)
            if (ok) {
                Toast.makeText(context, "Saved to requested location", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not save file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Pickers
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedTool != null) {
            pendingUri = uri
            when (selectedTool) {
                "compress" -> showCompressDialog = true
                "pdf2img" -> showPdfToImagesDialog = true
                "protect", "unlock" -> showPasswordDialog = true
                "split" -> showSplitDialog = true
                "organize", "extract", "rotate" -> showOrganizerSheet = true
                "viewer" -> onOpenPdfViewer(uri, "PDF Document")
                else -> {
                    handleSinglePdfSelected(context, selectedTool!!, uri,
                        onStart = { label -> isProcessing = true; progressLabel = label; lastResult = null },
                        onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl },
                        onComplete = { res -> isProcessing = false; lastResult = res },
                        onError = { err -> isProcessing = false; scope.launch { snackbarHostState.showSnackbar(err) } }
                    )
                }
            }
        }
    }

    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "merge") {
            handleMergePdfs(context, uris,
                onStart = { label -> isProcessing = true; progressLabel = label; lastResult = null },
                onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl },
                onComplete = { res -> isProcessing = false; lastResult = res },
                onError = { err -> isProcessing = false; scope.launch { snackbarHostState.showSnackbar(err) } }
            )
        }
    }

    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") {
            handleImagesToPdf(context, uris,
                onStart = { label -> isProcessing = true; progressLabel = label; lastResult = null },
                onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl },
                onComplete = { res -> isProcessing = false; lastResult = res },
                onError = { err -> isProcessing = false; scope.launch { snackbarHostState.showSnackbar(err) } }
            )
        }
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
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(PDF_TOOLS) { tool ->
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

            if (isProcessing) {
                ProcessingOverlay(
                    current = progressCurrent,
                    total = progressTotal,
                    label = progressLabel,
                    onCancel = {
                        activeJob?.cancel()
                        isProcessing = false
                        scope.launch { snackbarHostState.showSnackbar("Operation cancelled") }
                    }
                )
            }

            if (lastResult != null) {
                ResultDialog(
                    result = lastResult!!,
                    onDismiss = { lastResult = null },
                    onView = {
                        val file = lastResult!!.outputFile
                        val isImg = file.extension.lowercase() in listOf("jpg", "jpeg", "png")
                        lastResult = null
                        onOpenPdfViewer(Uri.fromFile(file), file.name)
                    },
                    onShare = {
                        val file = lastResult!!.outputFile
                        val ext = file.extension.lowercase()
                        val mime = when (ext) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            else -> "application/pdf"
                        }
                        shareFile(context, file.absolutePath, mime, file.name)
                    },
                    onSaveAs = {
                        saveSourceFile = lastResult!!.outputFile
                        saveFileLauncher.launch(lastResult!!.outputFile.name)
                    }
                )
            }

            if (showCompressDialog && pendingUri != null) {
                CompressPdfDialog(
                    context = context,
                    uri = pendingUri!!,
                    onDismiss = { showCompressDialog = false },
                    onConfirm = { preset ->
                        showCompressDialog = false
                        val uri = pendingUri!!
                        scope.launch {
                            try {
                                isProcessing = true
                                progressLabel = "Compressing PDF with selected target…"
                                val res = com.example.scanpro.imaging.CompressPdfEngine.compressPdf(context, uri, preset)
                                isProcessing = false
                                lastResult = PdfToolResult(
                                    outputFile = res.outputFile,
                                    pageCount = 1,
                                    originalSizeBytes = res.originalSizeBytes,
                                    outputSizeBytes = res.compressedSizeBytes,
                                    message = "Compressed by ${res.savingsPercentage}% (${formatBytes(res.originalSizeBytes)} → ${formatBytes(res.compressedSizeBytes)})"
                                )
                            } catch (e: Exception) {
                                isProcessing = false
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Compression failed")
                            }
                        }
                    }
                )
            }

            if (showPdfToImagesDialog && pendingUri != null) {
                PdfToImagesDialog(
                    onDismiss = { showPdfToImagesDialog = false },
                    onConfirm = { format ->
                        showPdfToImagesDialog = false
                        val uri = pendingUri!!
                        scope.launch {
                            try {
                                isProcessing = true
                                progressLabel = "Exporting PDF pages to ${format.extension.uppercase()}…"
                                val outputDir = File(context.cacheDir, "pdf2img_${System.currentTimeMillis()}").apply { mkdirs() }
                                val files = PdfToolsEngine.pdfToImages(context, uri, format, outputDir,
                                    onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl }
                                )
                                isProcessing = false
                                if (files.isNotEmpty()) {
                                    lastResult = PdfToolResult(
                                        outputFile = files.first(),
                                        pageCount = files.size,
                                        originalSizeBytes = 0,
                                        outputSizeBytes = files.sumOf { it.length() },
                                        message = "Exported ${files.size} page images successfully"
                                    )
                                }
                            } catch (e: Exception) {
                                isProcessing = false
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Export failed")
                            }
                        }
                    }
                )
            }

            if (showPasswordDialog && pendingUri != null && selectedTool != null) {
                val isProtect = selectedTool == "protect"
                PasswordDialog(
                    isProtect = isProtect,
                    onDismiss = { showPasswordDialog = false },
                    onConfirm = { pwd ->
                        showPasswordDialog = false
                        val uri = pendingUri!!
                        scope.launch {
                            try {
                                isProcessing = true
                                progressLabel = if (isProtect) "Encrypting PDF with password…" else "Unlocking PDF…"
                                val outFile = File(context.cacheDir, "${if (isProtect) "protected" else "unlocked"}_${System.currentTimeMillis()}.pdf")
                                val res = if (isProtect) PdfToolsEngine.protectPdf(context, uri, pwd, outFile)
                                else PdfToolsEngine.unlockPdf(context, uri, pwd, outFile)
                                isProcessing = false
                                lastResult = res
                            } catch (e: Exception) {
                                isProcessing = false
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Operation failed")
                            }
                        }
                    }
                )
            }

            if (showSplitDialog && pendingUri != null) {
                SplitDialog(
                    onDismiss = { showSplitDialog = false },
                    onConfirm = { mode, rangesText, everyN ->
                        showSplitDialog = false
                        val uri = pendingUri!!
                        scope.launch {
                            try {
                                isProcessing = true
                                progressLabel = "Splitting PDF document…"
                                val outputDir = File(context.cacheDir, "split_${System.currentTimeMillis()}").apply { mkdirs() }
                                val files = PdfToolsEngine.splitPdf(
                                    context = context,
                                    inputUri = uri,
                                    mode = mode,
                                    customRangesText = rangesText,
                                    everyNPages = everyN,
                                    outputDir = outputDir,
                                    onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl }
                                )
                                isProcessing = false
                                if (files.isNotEmpty()) {
                                    lastResult = PdfToolResult(
                                        outputFile = files.first(),
                                        pageCount = files.size,
                                        originalSizeBytes = 0,
                                        outputSizeBytes = files.first().length(),
                                        message = "Split PDF into ${files.size} output files"
                                    )
                                }
                            } catch (e: Exception) {
                                isProcessing = false
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Split failed")
                            }
                        }
                    }
                )
            }

            if (showOrganizerSheet && pendingUri != null && selectedTool != null) {
                PdfPageOrganizerSheet(
                    uri = pendingUri!!,
                    mode = selectedTool!!,
                    onClose = { showOrganizerSheet = false },
                    onApply = { pageSpecs, extractedIndices, rotationsMap ->
                        showOrganizerSheet = false
                        val uri = pendingUri!!
                        val tool = selectedTool!!
                        scope.launch {
                            try {
                                isProcessing = true
                                progressLabel = "Saving PDF pages…"
                                val outFile = File(context.cacheDir, "organized_${System.currentTimeMillis()}.pdf")
                                val res = when (tool) {
                                    "extract" -> PdfToolsEngine.extractPages(context, uri, extractedIndices, outFile,
                                        onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl })
                                    "rotate" -> PdfToolsEngine.rotatePages(context, uri, rotationsMap, outFile,
                                        onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl })
                                    else -> PdfToolsEngine.organizePages(context, uri, pageSpecs, outFile,
                                        onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl })
                                }
                                isProcessing = false
                                lastResult = res
                            } catch (e: Exception) {
                                isProcessing = false
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Page operation failed")
                            }
                        }
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

@Composable
private fun ResultDialog(
    result: PdfToolResult,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit
) {
    val isImage = result.outputFile.extension.lowercase() in listOf("jpg", "jpeg", "png")
    val titleText = if (isImage) "Export Ready" else "PDF Ready"
    val viewButtonText = if (isImage) "View Image" else "View PDF"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = { Text(titleText, textAlign = TextAlign.Center) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isImage) "Output Image:" else "Output File:", style = MaterialTheme.typography.labelMedium)
                            Text(result.outputFile.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isImage) "Image Count:" else "Page Count:", style = MaterialTheme.typography.labelMedium)
                            Text("${result.pageCount} ${if (isImage) "images" else "pages"}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("File Size:", style = MaterialTheme.typography.labelMedium)
                            Text(formatBytes(result.outputSizeBytes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onView) {
                Text(viewButtonText)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveAs) {
                    Text("Save As…")
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(onClick = onShare) {
                    Text("Share")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    )
}

@Composable
private fun PasswordDialog(
    isProtect: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isProtect) "Protect PDF with Password" else "Unlock PDF") },
        text = {
            Column {
                Text(
                    if (isProtect) "Enter a password (at least 4 characters) to encrypt this PDF:"
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
                    if (password.length < 4) {
                        errorText = "Password must be at least 4 characters"
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
    val pages = remember { mutableStateListOf<ThumbnailPageModel>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                isLoading = true
                val pageCount = PdfToolsEngine.getPdfPageCount(context, uri)
                val list = mutableListOf<ThumbnailPageModel>()
                for (i in 0 until pageCount) {
                    val bmp = PdfToolsEngine.renderPdfPageThumbnail(context, uri, i, targetWidth = 300)
                    list.add(ThumbnailPageModel(index = i, bitmap = bmp))
                }
                pages.clear()
                pages.addAll(list)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == "extract") "Select Pages to Extract" else "Organize Pages") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val specs = pages.map { PageSpec(it.index, it.rotation) }
                            val extracted = pages.filter { it.selected }.map { it.index }
                            val rotations = pages.associate { it.index to it.rotation }
                            onApply(specs, extracted, rotations)
                        },
                        enabled = !isLoading && pages.isNotEmpty()
                    ) {
                        Text(if (mode == "extract") "Extract (${pages.count { it.selected }})" else "Save PDF")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading PDF page thumbnails…")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { index, item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pages[index] = item.copy(selected = !item.selected)
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = { checked ->
                                            pages[index] = item.copy(selected = checked)
                                        }
                                    )
                                    Text("Page ${item.index + 1}", style = MaterialTheme.typography.labelMedium)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.bitmap != null) {
                                        Image(
                                            bitmap = item.bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(rotationZ = item.rotation.toFloat())
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    IconButton(
                                        onClick = {
                                            val newRot = (item.rotation + 90) % 360
                                            pages[index] = item.copy(rotation = newRot)
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate")
                                    }
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val prev = pages[index - 1]
                                                pages[index - 1] = item
                                                pages[index] = prev
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Move left")
                                        }
                                    }
                                    if (index < pages.lastIndex) {
                                        IconButton(
                                            onClick = {
                                                val next = pages[index + 1]
                                                pages[index + 1] = item
                                                pages[index] = next
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Move right")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleSinglePdfSelected(
    context: android.content.Context,
    toolId: String,
    uri: Uri,
    onStart: (String) -> Unit,
    onProgress: (Int, Int, String) -> Unit,
    onComplete: (PdfToolResult) -> Unit,
    onError: (String) -> Unit
) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    scope.launch {
        try {
            when (toolId) {
                "compress" -> {
                    onStart("Compressing PDF document…")
                    val res = com.example.scanpro.imaging.CompressPdfEngine.compressPdf(
                        context, uri, CompressionPreset.BALANCED
                    )
                    onComplete(
                        PdfToolResult(
                            outputFile = res.outputFile,
                            pageCount = 1,
                            originalSizeBytes = res.originalSizeBytes,
                            outputSizeBytes = res.compressedSizeBytes,
                            message = "Compressed by ${res.savingsPercentage}% (${formatBytes(res.originalSizeBytes)} → ${formatBytes(res.compressedSizeBytes)})"
                        )
                    )
                }
                "pdf2img" -> {
                    onStart("Exporting PDF pages to JPEG images…")
                    val outputDir = File(context.cacheDir, "pdf2img_${System.currentTimeMillis()}").apply { mkdirs() }
                    val files = PdfToolsEngine.pdfToImages(
                        context, uri, ImageExportFormat.JPEG, outputDir, onProgress
                    )
                    if (files.isNotEmpty()) {
                        onComplete(
                            PdfToolResult(
                                outputFile = files.first(),
                                pageCount = files.size,
                                originalSizeBytes = 0,
                                outputSizeBytes = files.sumOf { it.length() },
                                message = "Exported ${files.size} page images successfully"
                            )
                        )
                    } else {
                        onError("Failed to export PDF pages as images")
                    }
                }
                else -> {
                    onStart("Processing PDF document…")
                    val outFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.pdf")
                    val res = PdfToolsEngine.extractPages(context, uri, listOf(0), outFile, onProgress)
                    onComplete(res)
                }
            }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Operation failed")
        }
    }
}

private fun handleMergePdfs(
    context: android.content.Context,
    uris: List<Uri>,
    onStart: (String) -> Unit,
    onProgress: (Int, Int, String) -> Unit,
    onComplete: (PdfToolResult) -> Unit,
    onError: (String) -> Unit
) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    scope.launch {
        try {
            onStart("Merging ${uris.size} PDF files…")
            val outFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
            val res = PdfToolsEngine.mergePdfs(context, uris, outFile, onProgress)
            onComplete(res)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to merge PDFs")
        }
    }
}

private fun handleImagesToPdf(
    context: android.content.Context,
    uris: List<Uri>,
    onStart: (String) -> Unit,
    onProgress: (Int, Int, String) -> Unit,
    onComplete: (PdfToolResult) -> Unit,
    onError: (String) -> Unit
) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    scope.launch {
        try {
            onStart("Converting ${uris.size} images to PDF…")
            val outFile = File(context.cacheDir, "images_${System.currentTimeMillis()}.pdf")
            val res = PdfToolsEngine.imagesToPdf(context, uris, PdfPageSize.AUTO, PdfQuality.BALANCED, outFile, onProgress)
            onComplete(res)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to convert images to PDF")
        }
    }
}
