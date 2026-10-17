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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.example.scanpro.intelligence.DocumentIntelligenceEngine
import com.example.scanpro.intelligence.TranslationEngine
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
    onSign: (String, Bitmap) -> Unit,
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
    val insights = remember(document.ocrText) { DocumentIntelligenceEngine.analyze(document.ocrText) }
    var assistantQuestion by rememberSaveable(document.id) { mutableStateOf("") }
    var assistantAnswer by rememberSaveable(document.id) { mutableStateOf("") }
    val detailScope = rememberCoroutineScope()
    var translationTarget by rememberSaveable(document.id) { mutableStateOf("en") }
    var translationResult by rememberSaveable(document.id) { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { sharePdf(context, document) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                    OutlinedButton(
                        onClick = { shareImages(context, document) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) {
                        Text("Share JPG")
                    }
                }
                Spacer(Modifier.height(12.dp))
                PageThumbnail(currentPage.imagePath, Modifier.fillMaxWidth().height(480.dp))
                Spacer(Modifier.height(8.dp))
                Text("Page ${currentIndex + 1} of ${pages.size}", modifier = Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                        var dragDistance by remember(item.id) { mutableFloatStateOf(0f) }
                        // The gesture block outlives reorders, so read the live position instead of the captured one.
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

                SectionTitle("Scan enhancement")
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

                Spacer(Modifier.height(10.dp))
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
                            Icon(Icons.Default.ArrowLeft, contentDescription = null); Text("Left")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { onMove(currentPage.id, 1) }, enabled = !busy && currentIndex < pages.lastIndex) {
                            Text("Right"); Icon(Icons.Default.ArrowRight, contentDescription = null)
                        }
                    }
                    item { ToolButton("Duplicate", !busy) { onDuplicatePage(currentPage.id) } }
                    item { ToolButton("Extract", !busy) { onExtractPage(currentPage.id) } }
                    item { ToolButton("Split book", !busy) { onSplitBook(currentPage.id) } }
                    item { ToolButton("Delete page", !busy) { deletePageOpen = true } }
                }

                SectionTitle("Markup and signing")
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
                    item { ToolButton("Undo markup", !busy && currentPage.annotations.isNotEmpty()) { onUndoAnnotation(currentPage.id) } }
                }
            }

            SectionTitle("Document tools")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Button(onClick = { openPdf(context, document) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Open PDF")
                    }
                }
                item { ToolButton("Share PDF") { sharePdf(context, document) } }
                item { ToolButton("Save PDF") { savePdfLauncher.launch("${document.title}.pdf") } }
                item { ToolButton("Print") { printPdf(context, document) } }
                item { ToolButton("Protect PDF", !busy) { passwordOpen = true } }
                item { ToolButton("Encrypted backup", !busy) { backupPasswordOpen = true } }
                item { ToolButton("JPEGs") { shareImages(context, document) } }
                item { ToolButton("OCR text") { shareOcrText(context, document) } }
                item { ToolButton("TXT file", !busy && document.ocrText.isNotBlank()) { onExportOcrTextFile() } }
                item {
                    OutlinedButton(enabled = !busy, onClick = { appendLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Add pages")
                    }
                }
                item { ToolButton("Merge", !busy && otherDocuments.isNotEmpty()) { mergeOpen = true } }
                item { ToolButton("Split all", !busy && document.pageCount > 1) { onSplitDocument() } }
                item { ToolButton("ID copy", !busy) { onExportIdCopy() } }
                item { ToolButton("Long image", !busy) { onExportLongImage() } }
                item { ToolButton("Watermark", !busy) { watermarkOpen = true } }
                item { ToolButton("Refresh OCR", !busy) { onReOcr() } }
                item { ToolButton("Folder", !busy) { folderOpen = true } }
                item { ToolButton("Tags", !busy) { tagsOpen = true } }
            }

            SectionTitle("PDF output")
            Text("Quality", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PdfQuality.entries, key = { it.name }) { quality ->
                    FilterChip(selected = document.pdfQuality == quality, enabled = !busy, onClick = { onPdfQuality(quality) }, label = { Text(quality.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Page size", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PdfPageSize.entries, key = { it.name }) { size ->
                    FilterChip(selected = document.pdfPageSize == size, enabled = !busy, onClick = { onPdfPageSize(size) }, label = { Text(size.label) })
                }
            }
            ToggleRow("Searchable OCR PDF", "Adds recognized text to the PDF so document search can index it.", document.searchablePdf, !busy, onSearchablePdf)
            ToggleRow("Page numbers", "Adds page x / total at the bottom of exported PDF pages.", document.pageNumbers, !busy, onPageNumbers)

            SectionTitle("Document mode")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanMode.entries, key = { it.name }) { mode ->
                    FilterChip(selected = document.scanMode == mode, enabled = !busy, onClick = { onScanMode(mode) }, label = { Text(mode.label) })
                }
            }

            SectionTitle("OCR language script")
            Text("Choose the primary script, then ScanPro re-runs OCR locally on every page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OcrScript.entries, key = { it.name }) { script ->
                    FilterChip(
                        selected = document.ocrScript == script,
                        enabled = !busy,
                        onClick = { onOcrScript(script) },
                        label = { Text(script.label) }
                    )
                }
            }

            SectionTitle("Smart insights · offline")
            Text(insights.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(insights.summary, style = MaterialTheme.typography.bodyMedium)
            if (insights.amounts.isNotEmpty()) Text("Amounts: ${insights.amounts.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            if (insights.dates.isNotEmpty()) Text("Dates: ${insights.dates.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            if (insights.references.isNotEmpty()) Text("References: ${insights.references.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            if (insights.actionItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Potential action items", style = MaterialTheme.typography.labelLarge)
                insights.actionItems.forEach { item ->
                    Text("• $item", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (insights.suggestedTags.isNotEmpty()) {
                Text("Suggested tags: ${insights.suggestedTags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val merged = (document.tags + insights.suggestedTags).distinctBy { it.lowercase() }
                        onTags(merged.joinToString(", "))
                    }
                ) { Text("Apply suggested tags") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = assistantQuestion,
                onValueChange = { assistantQuestion = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ask about this scan") },
                supportingText = { Text("Answers are extracted only from OCR text; no document is uploaded.") },
                maxLines = 3
            )
            Button(
                onClick = { assistantAnswer = DocumentIntelligenceEngine.answer(assistantQuestion, document.ocrText) },
                enabled = assistantQuestion.isNotBlank() && document.ocrText.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Find answer") }
            if (assistantAnswer.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(assistantAnswer, modifier = Modifier.padding(12.dp))
                }
            }

            SectionTitle("Translate OCR")
            Text("Translation runs with ML Kit. The language model may download once, then works on-device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onClick = {
                    detailScope.launch {
                        translating = true
                        translationResult = runCatching { TranslationEngine.translate(document.ocrText, translationTarget) }
                            .getOrElse { it.message ?: "Translation failed" }
                        translating = false
                    }
                },
                enabled = document.ocrText.isNotBlank() && !translating,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(if (translating) "Translating…" else "Translate") }
            if (translationResult.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SelectionContainer { Text(translationResult, modifier = Modifier.padding(12.dp)) }
                }
            }

            SectionTitle("Recognized text")
            if (!currentPage?.ocrText.isNullOrBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { ocrEditOpen = true }, enabled = !busy) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit recognized text")
                    }
                    IconButton(onClick = { copyText(context, currentPage!!.ocrText) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                    }
                }
            }
            SelectionContainer {
                Text(currentPage?.ocrText?.ifBlank { "No text recognized on this page." } ?: "No page selected.")
            }

            SectionTitle("Document info")
            Text("Pages: ${document.pageCount}")
            Text("PDF size: ${formatBytes(document.sizeBytes)}")
            Text("Updated: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.updatedAt))}")
            if (document.folderName.isNotBlank()) Text("Folder: ${document.folderName}")
            if (document.tags.isNotEmpty()) Text("Tags: ${document.tags.joinToString(", ")}")

            Spacer(Modifier.height(22.dp))
            TextButton(onClick = { deleteDocumentOpen = true }, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Move document to recycle bin")
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
            onSign(currentPage.id, bitmap)
            signatureOpen = false
        }
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
    Spacer(Modifier.height(22.dp))
    Text(value, style = MaterialTheme.typography.titleMedium)
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
