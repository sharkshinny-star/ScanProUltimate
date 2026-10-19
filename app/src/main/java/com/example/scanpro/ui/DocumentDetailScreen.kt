@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.intelligence.TranslationEngine
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: ScanDocument,
    otherDocuments: List<ScanDocument>,
    busy: Boolean,
    busyLabel: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPdfViewer: (Uri, String) -> Unit,
    onRename: (String) -> Unit,
    onTags: (String) -> Unit,
    onFolder: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onPdfQuality: (PdfQuality) -> Unit,
    onPdfPageSize: (PdfPageSize) -> Unit,
    onSearchablePdf: (Boolean) -> Unit,
    onPageNumbers: (Boolean) -> Unit,
    onScanMode: (ScanMode) -> Unit,
    onApplyFilter: (String, PageFilter) -> Unit,
    onRotate: (String) -> Unit,
    onCrop: (String, CropQuad) -> Unit,
    onAdjustments: (String, PageAdjustments) -> Unit,
    onResetPage: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicatePage: (String) -> Unit,
    onExtractPage: (String) -> Unit,
    onSplitBook: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onSign: (pageId: String, bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float) -> Unit,
    onTextAnnotation: (String, String) -> Unit,
    onHighlight: (String) -> Unit,
    onRedact: (String) -> Unit,
    onUndoAnnotation: (String) -> Unit,
    onWatermark: (String) -> Unit,
    onAppendImages: (List<Uri>) -> Unit,
    onMergeWith: (String) -> Unit,
    onReOcr: () -> Unit,
    onUpdateOcrText: (String, String) -> Unit,
    onOcrScript: (OcrScript) -> Unit,
    onExportIdCopy: () -> Unit,
    onExportLongImage: () -> Unit,
    onExportOcrTextFile: () -> Unit,
    onSplitDocument: () -> Unit,
    onExportProtectedPdf: (String) -> Unit,
    onExportEncryptedBackup: (String) -> Unit,
    onDeleteDocument: () -> Unit
) {
    val context = LocalContext.current
    val pages = document.pages.sortedBy { it.position }
    var selectedPageId by rememberSaveable(document.id) { mutableStateOf(pages.firstOrNull()?.id) }
    val currentPage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
    val currentIndex = pages.indexOfFirst { it.id == currentPage?.id }.coerceAtLeast(0)
    val detailScope = rememberCoroutineScope()
    var translationTarget by rememberSaveable(document.id) { mutableStateOf("en") }
    var translationResult by rememberSaveable(document.id) { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }

    var selectedTabIndex by rememberSaveable(document.id) { mutableIntStateOf(0) }
    val tabTitles = listOf("Enhance & Edit", "Markup & Sign", "PDF Settings", "OCR & Translation")

    var renameOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var signatureOpen by remember { mutableStateOf(false) }
    var watermarkOpen by remember { mutableStateOf(false) }
    var annotationTextOpen by remember { mutableStateOf(false) }
    var cropOpen by remember { mutableStateOf(false) }
    var adjustmentsOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var deletePageOpen by remember { mutableStateOf(false) }
    var deleteDocumentOpen by remember { mutableStateOf(false) }
    var passwordOpen by remember { mutableStateOf(false) }
    var backupPasswordOpen by remember { mutableStateOf(false) }
    var ocrEditOpen by remember { mutableStateOf(false) }
    var pendingSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signaturePlacementOpen by remember { mutableStateOf(false) }

    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) onAppendImages(uris)
    }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val ok = copyFileToUri(context, document.pdfPath, uri)
            if (!ok) android.widget.Toast.makeText(context, "Could not save PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pages.map { it.id }) {
        if (pages.none { it.id == selectedPageId }) selectedPageId = pages.firstOrNull()?.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite, enabled = !busy) {
                        Icon(if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite")
                    }
                    IconButton(onClick = { sharePdf(context, document) }) { Icon(Icons.Default.Share, contentDescription = "Share PDF") }
                    IconButton(onClick = { renameOpen = true }) { Icon(Icons.Default.Edit, contentDescription = "Rename") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (!busyLabel.isNullOrBlank()) Text(busyLabel, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
            }

            if (currentPage != null) {
                PageThumbnail(currentPage.imagePath, Modifier.fillMaxWidth().height(420.dp))
                Spacer(Modifier.height(8.dp))
                Text("Page ${currentIndex + 1} of ${pages.size}", modifier = Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                        var dragDistance by remember(item.id) { mutableFloatStateOf(0f) }
                        val latestIndex by rememberUpdatedState(index)
                        val latestLast by rememberUpdatedState(pages.lastIndex)
                        Card(
                            modifier = Modifier
                                .size(width = 64.dp, height = 86.dp)
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragDistance = 0f },
                                        onDragEnd = { dragDistance = 0f },
                                        onDragCancel = { dragDistance = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDistance += amount.x
                                            val threshold = 46.dp.toPx()
                                            if (dragDistance > threshold && latestIndex < latestLast) {
                                                onMove(item.id, 1)
                                                dragDistance = 0f
                                            } else if (dragDistance < -threshold && latestIndex > 0) {
                                                onMove(item.id, -1)
                                                dragDistance = 0f
                                            }
                                        }
                                    )
                                }
                                .clickable { selectedPageId = item.id },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            PageThumbnail(item.imagePath, Modifier.fillMaxSize())
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenPdfViewer(Uri.fromFile(java.io.File(document.pdfPath)), document.title) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { sharePdf(context, document) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { savePdfLauncher.launch("${document.title}.pdf") },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Save", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> {
                    // Tab 0: Enhance & Edit
                    if (currentPage != null) {
                        SectionTitle("Scan Enhancement Filter")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PageFilter.entries, key = { it.name }) { filter ->
                                FilterChip(
                                    selected = currentPage.filter == filter,
                                    enabled = !busy,
                                    onClick = { onApplyFilter(currentPage.id, filter) },
                                    label = { Text(filter.label) }
                                )
                            }
                        }

                        SectionTitle("Page Operations")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { ToolButton("Crop", !busy) { cropOpen = true } }
                            item { ToolButton("Fine tune", !busy) { adjustmentsOpen = true } }
                            item {
                                OutlinedButton(onClick = { onRotate(currentPage.id) }, enabled = !busy) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null)
                                    Spacer(Modifier.width(5.dp)); Text("Rotate")
                                }
                            }
                            item { ToolButton("Reset", !busy) { onResetPage(currentPage.id) } }
                            item {
                                OutlinedButton(onClick = { onMove(currentPage.id, -1) }, enabled = !busy && currentIndex > 0) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = null); Text("Left")
                                }
                            }
                            item {
                                OutlinedButton(onClick = { onMove(currentPage.id, 1) }, enabled = !busy && currentIndex < pages.lastIndex) {
                                    Text("Right"); Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null)
                                }
                            }
                            item { ToolButton("Duplicate", !busy) { onDuplicatePage(currentPage.id) } }
                            item { ToolButton("Extract", !busy) { onExtractPage(currentPage.id) } }
                            item { ToolButton("Split book", !busy) { onSplitBook(currentPage.id) } }
                            item { ToolButton("Delete page", !busy) { deletePageOpen = true } }
                        }
                    }
                }
                1 -> {
                    // Tab 1: Markup & Sign
                    if (currentPage != null) {
                        SectionTitle("Markup and Signing")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OutlinedButton(onClick = { signatureOpen = true }, enabled = !busy) {
                                    Icon(Icons.Default.Draw, contentDescription = null)
                                    Spacer(Modifier.width(5.dp)); Text("Signature")
                                }
                            }
                            item { ToolButton("Add text", !busy) { annotationTextOpen = true } }
                            item { ToolButton("Highlight", !busy) { onHighlight(currentPage.id) } }
                            item { ToolButton("Redact", !busy) { onRedact(currentPage.id) } }
                            item { ToolButton("Watermark", !busy) { watermarkOpen = true } }
                            item { ToolButton("Undo markup", !busy && currentPage.annotations.isNotEmpty()) { onUndoAnnotation(currentPage.id) } }
                        }
                    }
                }
                2 -> {
                    // Tab 2: PDF Settings & Document Operations
                    SectionTitle("PDF Quality")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfQuality.entries, key = { it.name }) { quality ->
                            FilterChip(selected = document.pdfQuality == quality, enabled = !busy, onClick = { onPdfQuality(quality) }, label = { Text(quality.label) })
                        }
                    }

                    val rawPagesBytes = remember(document.pages) {
                        document.pages.sumOf { java.io.File(it.imagePath).length() }.coerceAtLeast(100000L)
                    }
                    val estHigh = (rawPagesBytes * 0.70).toLong()
                    val estBalanced = (rawPagesBytes * 0.35).toLong()
                    val estSmall = (rawPagesBytes * 0.15).toLong()

                    val selectedEstSize = when (document.pdfQuality) {
                        PdfQuality.HIGH -> estHigh
                        PdfQuality.BALANCED -> estBalanced
                        PdfQuality.SMALL -> estSmall
                    }

                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Estimated PDF Size: ~${formatBytes(selectedEstSize)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Quality size range: High (~${formatBytes(estHigh)}) · Balanced (~${formatBytes(estBalanced)}) · Small (~${formatBytes(estSmall)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Page size", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfPageSize.entries, key = { it.name }) { size ->
                            FilterChip(selected = document.pdfPageSize == size, enabled = !busy, onClick = { onPdfPageSize(size) }, label = { Text(size.label) })
                        }
                    }
                    ToggleRow("Searchable OCR PDF", "Adds recognized text to the PDF so document search can index it.", document.searchablePdf, !busy, onSearchablePdf)
                    ToggleRow("Page numbers", "Adds page x / total at the bottom of exported PDF pages.", document.pageNumbers, !busy, onPageNumbers)

                    SectionTitle("Document Operations")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ToolButton("Print") { printPdf(context, document) } }
                        item { ToolButton("Protect PDF", !busy) { passwordOpen = true } }
                        item { ToolButton("Encrypted backup", !busy) { backupPasswordOpen = true } }
                        item { ToolButton("Share JPEGs") { shareImages(context, document) } }
                        item {
                            OutlinedButton(enabled = !busy, onClick = { appendLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Add pages")
                            }
                        }
                        item { ToolButton("Merge", !busy && otherDocuments.isNotEmpty()) { mergeOpen = true } }
                        item { ToolButton("Split all", !busy && document.pageCount > 1) { onSplitDocument() } }
                        item { ToolButton("ID copy", !busy) { onExportIdCopy() } }
                        item { ToolButton("Long image", !busy) { onExportLongImage() } }
                        item { ToolButton("Folder", !busy) { folderOpen = true } }
                        item { ToolButton("Tags", !busy) { tagsOpen = true } }
                    }
                }
                3 -> {
                    // Tab 3: OCR & Translation
                    SectionTitle("OCR Operations")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onReOcr() }, enabled = !busy) {
                            Text("Re-run OCR")
                        }
                        if (document.ocrText.isNotBlank()) {
                            OutlinedButton(onClick = { onExportOcrTextFile() }, enabled = !busy) {
                                Text("Export .TXT")
                            }
                        }
                    }

                    SectionTitle("Translate OCR")
                    Text("Translation runs with ML Kit on-device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TranslationEngine.commonTargets, key = { it.languageTag }) { target ->
                            FilterChip(
                                selected = translationTarget == target.languageTag,
                                onClick = { translationTarget = target.languageTag },
                                label = { Text(target.label) }
                            )
                        }
                    }
                    Button(
                        enabled = !translating && document.ocrText.isNotBlank(),
                        onClick = {
                            detailScope.launch {
                                translating = true
                                translationResult = runCatching { TranslationEngine.translate(document.ocrText, translationTarget) }
                                    .getOrElse { it.message ?: "Translation failed" }
                                translating = false
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(if (translating) "Translating…" else "Translate")
                    }
                    if (translationResult.isNotBlank()) {
                        SelectionContainer {
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(translationResult, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }

                    SectionTitle("Recognized Text (OCR)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (document.ocrText.isBlank()) "No text detected yet." else "${document.ocrText.length} characters recognized",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            IconButton(onClick = { shareOcrText(context, document) }, enabled = document.ocrText.isNotBlank()) {
                                Icon(Icons.Default.Share, contentDescription = "Share OCR text")
                            }
                            IconButton(onClick = { copyText(context, document.ocrText) }, enabled = document.ocrText.isNotBlank()) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                            }
                            IconButton(onClick = { ocrEditOpen = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit text")
                            }
                        }
                    }
                    if (document.ocrText.isNotBlank()) {
                        SelectionContainer {
                            Card(Modifier.fillMaxWidth()) {
                                Text(document.ocrText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            SectionTitle("Document Info")
            Text("Pages: ${document.pageCount}", style = MaterialTheme.typography.bodySmall)
            Text("PDF size: ${formatBytes(document.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
            Text("Updated: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.updatedAt))}", style = MaterialTheme.typography.bodySmall)
            if (document.folderName.isNotBlank()) Text("Folder: ${document.folderName}", style = MaterialTheme.typography.bodySmall)
            if (document.tags.isNotEmpty()) Text("Tags: ${document.tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { deleteDocumentOpen = true }, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(5.dp))
                Text("Move document to recycle bin", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (renameOpen) TextInputDialog("Rename document", document.title, "Name", onDismiss = { renameOpen = false }) { onRename(it); renameOpen = false }
    if (tagsOpen) TextInputDialog("Document tags", document.tags.joinToString(", "), "Comma-separated tags", allowBlank = true, onDismiss = { tagsOpen = false }) { onTags(it); tagsOpen = false }
    if (folderOpen) TextInputDialog("Move to folder", document.folderName, "Folder name", allowBlank = true, onDismiss = { folderOpen = false }) { onFolder(it); folderOpen = false }
    if (watermarkOpen) TextInputDialog("Watermark all pages", label = "Watermark text", confirmLabel = "Apply", onDismiss = { watermarkOpen = false }) { onWatermark(it); watermarkOpen = false }
    if (passwordOpen) PasswordInputDialog(
        title = "Protect PDF copy",
        message = "Create a password-protected PDF for sharing.",
        minimumLength = 4,
        confirmLabel = "Create",
        onDismiss = { passwordOpen = false }
    ) { onExportProtectedPdf(it); passwordOpen = false }
    if (backupPasswordOpen) PasswordInputDialog(
        title = "Encrypted ScanPro backup",
        message = "Backs up page images, OCR and document settings in an AES-GCM encrypted portable package.",
        minimumLength = 6,
        confirmLabel = "Create backup",
        onDismiss = { backupPasswordOpen = false }
    ) { onExportEncryptedBackup(it); backupPasswordOpen = false }
    if (annotationTextOpen && currentPage != null) TextInputDialog("Add text to page", label = "Text", confirmLabel = "Add", onDismiss = { annotationTextOpen = false }) { onTextAnnotation(currentPage.id, it); annotationTextOpen = false }
    if (ocrEditOpen && currentPage != null) TextInputDialog(
        title = "Edit recognized text",
        initial = currentPage.ocrText,
        label = "OCR text",
        confirmLabel = "Save text",
        maxLines = 12,
        onDismiss = { ocrEditOpen = false }
    ) { text -> onUpdateOcrText(currentPage.id, text); ocrEditOpen = false }

    if (signatureOpen && currentPage != null) {
        SignaturePadDialog(onDismiss = { signatureOpen = false }) { bitmap ->
            signatureOpen = false
            pendingSignatureBitmap = bitmap
            signaturePlacementOpen = true
        }
    }

    if (signaturePlacementOpen && currentPage != null && pendingSignatureBitmap != null) {
        SignaturePlacementDialog(
            pageImagePath = currentPage.imagePath,
            signatureBitmap = pendingSignatureBitmap!!,
            onDismiss = {
                signaturePlacementOpen = false
                pendingSignatureBitmap = null
            },
            onConfirmPlacement = { x, y, w, h ->
                val bmp = pendingSignatureBitmap!!
                signaturePlacementOpen = false
                pendingSignatureBitmap = null
                onSign(currentPage.id, bmp, x, y, w, h)
            }
        )
    }

    if (cropOpen && currentPage != null) {
        CropEditorDialog(
            sourcePath = currentPage.sourcePath,
            initial = currentPage.crop,
            onDismiss = { cropOpen = false },
            onConfirm = {
                onCrop(currentPage.id, it)
                cropOpen = false
            }
        )
    }

    if (adjustmentsOpen && currentPage != null) {
        AdjustmentsDialog(
            page = currentPage,
            onDismiss = { adjustmentsOpen = false },
            onConfirm = { value ->
                onAdjustments(currentPage.id, value)
                adjustmentsOpen = false
            }
        )
    }

    if (mergeOpen) {
        AlertDialog(
            onDismissRequest = { mergeOpen = false },
            title = { Text("Merge with another document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    otherDocuments.take(12).forEach { other ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onMergeWith(other.id)
                                mergeOpen = false
                            }
                        ) { Text(other.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mergeOpen = false }) { Text("Cancel") } }
        )
    }

    if (deletePageOpen && currentPage != null) {
        ConfirmDialog(
            title = "Delete this page?",
            message = if (pages.size == 1) "This is the only page, so the document will move to the recycle bin." else "The page will be removed and the PDF rebuilt.",
            confirmLabel = "Delete",
            onDismiss = { deletePageOpen = false },
            onConfirm = { onDeletePage(currentPage.id); deletePageOpen = false }
        )
    }

    if (deleteDocumentOpen) {
        ConfirmDialog(
            title = "Move to recycle bin?",
            message = "You can restore the document later or permanently delete it from the recycle bin.",
            confirmLabel = "Move",
            onDismiss = { deleteDocumentOpen = false },
            onConfirm = { onDeleteDocument(); deleteDocumentOpen = false }
        )
    }
}

@Composable
private fun SectionTitle(value: String) {
    Spacer(Modifier.height(16.dp))
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToolButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
