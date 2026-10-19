package com.example.scanpro.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.MainViewModel
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SplitMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: MainViewModel,
    onOpenPdfViewer: (Uri, String) -> Unit
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

    // Pickers
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedTool != null) {
            handleSinglePdfSelected(context, selectedTool!!, uri, viewModel, onOpenPdfViewer,
                onStart = { label -> isProcessing = true; progressLabel = label; lastResult = null },
                onProgress = { cur, tot, lbl -> progressCurrent = cur; progressTotal = tot; progressLabel = lbl },
                onComplete = { res -> isProcessing = false; lastResult = res },
                onError = { err -> isProcessing = false; scope.launch { snackbarHostState.showSnackbar(err) } }
            )
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        lastResult = null
                        onOpenPdfViewer(Uri.fromFile(file), file.name)
                    },
                    onShare = {
                        val file = lastResult!!.outputFile
                        shareFile(context, file.absolutePath, "application/pdf", file.name)
                    }
                )
            }
        }
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

@Composable
private fun ResultDialog(
    result: PdfToolResult,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit
) {
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
        title = { Text("PDF Ready", textAlign = TextAlign.Center) },
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
                            Text("Output File:", style = MaterialTheme.typography.labelMedium)
                            Text(result.outputFile.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Page Count:", style = MaterialTheme.typography.labelMedium)
                            Text("${result.pageCount} pages", style = MaterialTheme.typography.bodySmall)
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
                Text("View PDF")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onShare) {
                    Text("Share")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    )
}

private fun handleSinglePdfSelected(
    context: android.content.Context,
    toolId: String,
    uri: Uri,
    viewModel: MainViewModel,
    onOpenPdfViewer: (Uri, String) -> Unit,
    onStart: (String) -> Unit,
    onProgress: (Int, Int, String) -> Unit,
    onComplete: (PdfToolResult) -> Unit,
    onError: (String) -> Unit
) {
    if (toolId == "viewer") {
        onOpenPdfViewer(uri, "PDF Document")
        return
    }

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
                "split" -> {
                    onStart("Splitting PDF into single pages…")
                    val outputDir = File(context.cacheDir, "split_${System.currentTimeMillis()}").apply { mkdirs() }
                    val files = PdfToolsEngine.splitPdf(
                        context, uri, SplitMode.SINGLE_PAGES, outputDir = outputDir, onProgress = onProgress
                    )
                    if (files.isNotEmpty()) {
                        onComplete(
                            PdfToolResult(
                                outputFile = files.first(),
                                pageCount = files.size,
                                originalSizeBytes = 0,
                                outputSizeBytes = files.first().length(),
                                message = "Split into ${files.size} separate PDF files"
                            )
                        )
                    } else {
                        onError("Failed to split PDF")
                    }
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
