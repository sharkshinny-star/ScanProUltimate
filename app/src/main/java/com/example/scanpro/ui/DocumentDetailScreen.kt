@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    val pages = remember(document.pages) { document.pages.sortedBy { it.position } }
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
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var tabName by rememberSaveable { mutableStateOf(DetailTab.EDIT.name) }
    val tab = runCatching { DetailTab.valueOf(tabName) }.getOrDefault(DetailTab.EDIT)

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
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem("Rename", Icons.Default.Edit, !busy) { menuOpen = false; renameOpen = true }
                            MenuItem("Move to folder", Icons.Default.Folder, !busy) { menuOpen = false; folderOpen = true }
                            MenuItem("Tags", Icons.Default.Tag, !busy) { menuOpen = false; tagsOpen = true }
                            MenuItem("Details", Icons.Default.Info) { menuOpen = false; detailsOpen = true }
                            HorizontalDivider()
                            MenuItem("Move to recycle bin", Icons.Default.Delete, !busy) { menuOpen = false; deleteDocumentOpen = true }
                        }
                    }
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
                .padding(horizontal = 16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (!busyLabel.isNullOrBlank()) Text(busyLabel, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            if (currentPage != null) {
                PageThumbnail(currentPage.imagePath, Modifier.fillMaxWidth().height(400.dp), THUMBNAIL_LARGE)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                        var dragDistance by remember(item.id) { mutableFloatStateOf(0f) }
                        // The gesture block outlives reorders, so read the live position instead of the captured one.
                        val latestIndex by rememberUpdatedState(index)
                        val latestLast by rememberUpdatedState(pages.lastIndex)
                        val isCurrent = item.id == currentPage.id
                        Card(
                            modifier = Modifier
                                .size(width = 52.dp, height = 70.dp)
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
                            shape = RoundedCornerShape(8.dp),
                            border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            PageThumbnail(item.imagePath, Modifier.fillMaxSize(), THUMBNAIL_SMALL)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pages.size > 1) "Page ${currentIndex + 1} of ${pages.size} · long-press a page to reorder" else "Page 1 of 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                DetailTab.entries.forEach { entry ->
                    Tab(selected = tab == entry, onClick = { tabName = entry.name }, text = { Text(entry.label) })
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                DetailTab.EDIT -> if (currentPage == null) EmptyHint("No page selected.") else {
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
                    Spacer(Modifier.height(8.dp))
                    ToolGrid {
                        ToolTile(Icons.Default.Crop, "Crop", !busy) { cropOpen = true }
                        ToolTile(Icons.Default.Tune, "Fine tune", !busy) { adjustmentsOpen = true }
                        ToolTile(Icons.Default.RotateRight, "Rotate", !busy) { onRotate(currentPage.id) }
                        ToolTile(Icons.Default.RestartAlt, "Reset", !busy) { onResetPage(currentPage.id) }
                        ToolTile(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", !busy && currentIndex > 0) { onMove(currentPage.id, -1) }
                        ToolTile(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", !busy && currentIndex < pages.lastIndex) { onMove(currentPage.id, 1) }
                        ToolTile(Icons.Default.FileCopy, "Duplicate", !busy) { onDuplicatePage(currentPage.id) }
                        ToolTile(Icons.Default.Output, "Extract", !busy) { onExtractPage(currentPage.id) }
                        ToolTile(Icons.AutoMirrored.Filled.MenuBook, "Split book", !busy) { onSplitBook(currentPage.id) }
                        ToolTile(Icons.Default.Delete, "Delete page", !busy) { deletePageOpen = true }
                    }
                }

                DetailTab.MARKUP -> if (currentPage == null) EmptyHint("No page selected.") else {
                    ToolGrid {
                        ToolTile(Icons.Default.Draw, "Signature", !busy) { signatureOpen = true }
                        ToolTile(Icons.Default.TextFields, "Add text", !busy) { annotationTextOpen = true }
                        ToolTile(Icons.Default.Highlight, "Highlight", !busy) { onHighlight(currentPage.id) }
                        ToolTile(Icons.Default.VisibilityOff, "Redact", !busy) { onRedact(currentPage.id) }
                        ToolTile(Icons.Default.BrandingWatermark, "Watermark", !busy) { watermarkOpen = true }
                        ToolTile(Icons.AutoMirrored.Filled.Undo, "Undo", !busy && currentPage.annotations.isNotEmpty()) { onUndoAnnotation(currentPage.id) }
                    }
                }

                DetailTab.EXPORT -> {
                    SubHeader("Share and save")
                    ToolGrid {
                        ToolTile(Icons.AutoMirrored.Filled.OpenInNew, "Open PDF") { openPdf(context, document) }
                        ToolTile(Icons.Default.Save, "Save PDF") { savePdfLauncher.launch("${document.title}.pdf") }
                        ToolTile(Icons.Default.Print, "Print") { printPdf(context, document) }
                        ToolTile(Icons.Default.Image, "JPEGs") { shareImages(context, document) }
                        ToolTile(Icons.Default.Lock, "Protect PDF", !busy) { passwordOpen = true }
                        ToolTile(Icons.Default.Badge, "ID copy", !busy) { onExportIdCopy() }
                        ToolTile(Icons.Default.ViewDay, "Long image", !busy) { onExportLongImage() }
                        ToolTile(Icons.Default.Backup, "Encrypted backup", !busy) { backupPasswordOpen = true }
                    }
                    SubHeader("Organize")
                    ToolGrid {
                        ToolTile(Icons.Default.AddPhotoAlternate, "Add pages", !busy) {
                            appendLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        ToolTile(Icons.Default.Layers, "Merge", !busy && otherDocuments.isNotEmpty()) { mergeOpen = true }
                        ToolTile(Icons.Default.ContentCut, "Split all", !busy && document.pageCount > 1) { onSplitDocument() }
                    }
                }

                DetailTab.TEXT -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (currentPage != null) "Page ${currentIndex + 1}" else "Recognized text",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (!currentPage?.ocrText.isNullOrBlank()) {
                            IconButton(onClick = { ocrEditOpen = true }, enabled = !busy) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit recognized text")
                            }
                            IconButton(onClick = { copyText(context, currentPage!!.ocrText) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                currentPage?.ocrText?.ifBlank { "No text recognized on this page." } ?: "No page selected.",
                                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { shareOcrText(context, document) }) { Text("Share text") }
                        TextButton(onClick = onExportOcrTextFile, enabled = !busy && document.ocrText.isNotBlank()) { Text("TXT file") }
                        TextButton(onClick = onReOcr, enabled = !busy) { Text("Re-run OCR") }
                    }

                    SubHeader("OCR script")
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

                    SubHeader("Translate")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TranslationEngine.commonTargets, key = { it.languageTag }) { target ->
                            FilterChip(
                                selected = translationTarget == target.languageTag,
                                onClick = { translationTarget = target.languageTag },
                                label = { Text(target.label) }
                            )
                        }
                    }
                    FilledTonalButton(
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
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (translating) "Translating…" else "Translate")
                    }
                    Text(
                        "Runs on-device with ML Kit; the language model may download once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (translationResult.isNotBlank()) {
                        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            SelectionContainer { Text(translationResult, modifier = Modifier.padding(12.dp)) }
                        }
                    }
                }

                DetailTab.INSIGHTS -> {
                    Text(insights.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(insights.summary, style = MaterialTheme.typography.bodyMedium)
                    if (insights.amounts.isNotEmpty()) Text("Amounts: ${insights.amounts.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (insights.dates.isNotEmpty()) Text("Dates: ${insights.dates.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (insights.references.isNotEmpty()) Text("References: ${insights.references.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (insights.actionItems.isNotEmpty()) {
                        SubHeader("Potential action items")
                        insights.actionItems.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (insights.suggestedTags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Suggested tags: ${insights.suggestedTags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val merged = (document.tags + insights.suggestedTags).distinctBy { it.lowercase() }
                                onTags(merged.joinToString(", "))
                            }
                        ) { Text("Apply suggested tags") }
                    }
                    SubHeader("Ask about this scan")
                    OutlinedTextField(
                        value = assistantQuestion,
                        onValueChange = { assistantQuestion = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. What is the total?") },
                        supportingText = { Text("Answers come only from OCR text; nothing is uploaded.") },
                        maxLines = 3
                    )
                    FilledTonalButton(
                        onClick = { assistantAnswer = DocumentIntelligenceEngine.answer(assistantQuestion, document.ocrText) },
                        enabled = assistantQuestion.isNotBlank() && document.ocrText.isNotBlank()
                    ) { Text("Find answer") }
                    if (assistantAnswer.isNotBlank()) {
                        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(assistantAnswer, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                DetailTab.PDF -> {
                    SubHeader("Quality")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfQuality.entries, key = { it.name }) { quality ->
                            FilterChip(selected = document.pdfQuality == quality, enabled = !busy, onClick = { onPdfQuality(quality) }, label = { Text(quality.label) })
                        }
                    }
                    SubHeader("Page size")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfPageSize.entries, key = { it.name }) { size ->
                            FilterChip(selected = document.pdfPageSize == size, enabled = !busy, onClick = { onPdfPageSize(size) }, label = { Text(size.label) })
                        }
                    }
                    SubHeader("Document mode")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanMode.entries, key = { it.name }) { mode ->
                            FilterChip(selected = document.scanMode == mode, enabled = !busy, onClick = { onScanMode(mode) }, label = { Text(mode.label) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("Searchable PDF", "Embed recognized text so the PDF can be searched.", document.searchablePdf, !busy, onSearchablePdf)
                    ToggleRow("Page numbers", "Print page x / total at the bottom of each page.", document.pageNumbers, !busy, onPageNumbers)
                }
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

    if (detailsOpen) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pages: ${document.pageCount}")
                    Text("PDF size: ${formatBytes(document.sizeBytes)}")
                    Text("Updated: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.updatedAt))}")
                    if (document.folderName.isNotBlank()) Text("Folder: ${document.folderName}")
                    if (document.tags.isNotEmpty()) Text("Tags: ${document.tags.joinToString(", ")}")
                }
            },
            confirmButton = { TextButton(onClick = { detailsOpen = false }) { Text("Close") } }
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

private enum class DetailTab(val label: String) {
    EDIT("Edit"), MARKUP("Markup"), EXPORT("Export"), TEXT("Text"), INSIGHTS("Insights"), PDF("PDF settings")
}

@Composable
private fun SubHeader(value: String) {
    Spacer(Modifier.height(14.dp))
    Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EmptyHint(value: String) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolGrid(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
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
