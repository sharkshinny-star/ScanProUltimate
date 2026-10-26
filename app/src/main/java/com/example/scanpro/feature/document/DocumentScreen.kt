@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.feature.document

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import com.example.scanpro.core.ui.FilterStrip
import com.example.scanpro.imaging.ImageProcessor
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.provider.DocumentsContract
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AddToDrive
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
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import com.example.scanpro.data.WatermarkStyle
import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.intelligence.DocumentInsights
import com.example.scanpro.intelligence.DocumentIntelligenceEngine
import com.example.scanpro.intelligence.TranslationEngine
import java.text.DateFormat
import java.util.Date
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.core.designsystem.ConfirmDialog
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.PasswordInputDialog
import com.example.scanpro.core.designsystem.THUMBNAIL_LARGE
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.ui.copyFileToUri
import com.example.scanpro.core.ui.copyText
import com.example.scanpro.core.ui.openPdf
import com.example.scanpro.core.ui.printPdf
import com.example.scanpro.core.ui.shareImages
import com.example.scanpro.core.ui.shareOcrText
import com.example.scanpro.core.ui.sharePdf
import com.example.scanpro.core.ui.ContactDraft
import com.example.scanpro.core.ui.openNewContactForm
import com.example.scanpro.intelligence.BusinessCard
import com.example.scanpro.intelligence.BusinessCardParser
import com.example.scanpro.core.ui.saveToGoogleDrive
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.designsystem.TitleWithStatus
import com.example.scanpro.core.designsystem.TopBarWithProgress

@Composable
fun DocumentRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val otherDocuments by viewModel.otherDocuments.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    val scanSaved by viewModel.scanSaved.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()

    // If the open document disappears (trashed or deleted elsewhere), leave instead of stranding the
    // user on an "unavailable" screen with no way back.
    var hadDocument by remember { mutableStateOf(false) }
    LaunchedEffect(document == null) {
        if (document != null) hadDocument = true else if (hadDocument) onBack()
    }

    val current = document
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(48.dp))
            else Text("Document is unavailable")
        }
        return
    }

    DocumentDetailScreen(
        document = current,
        otherDocuments = otherDocuments,
        busy = busy,
        busyLabel = busyLabel,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRename = viewModel::rename,
        onTags = viewModel::setTags,
        onFolder = viewModel::setFolder,
        onToggleFavorite = viewModel::toggleFavorite,
        onPdfQuality = viewModel::setPdfQuality,
        onPdfPageSize = viewModel::setPdfPageSize,
        onSearchablePdf = viewModel::setSearchablePdf,
        onPageNumbers = viewModel::setPageNumbers,
        onScanMode = viewModel::setScanMode,
        onApplyFilter = viewModel::applyFilter,
        onRotate = viewModel::rotatePage,
        onCrop = viewModel::applyCrop,
        onAdjustments = viewModel::applyAdjustments,
        onResetPage = viewModel::resetPage,
        onMove = viewModel::movePage,
        onDuplicatePage = viewModel::duplicatePage,
        onExtractPage = viewModel::extractPage,
        onSplitBook = viewModel::splitBookSpread,
        onDeletePage = viewModel::deletePage,
        onSign = viewModel::stampSignature,
        savedSignatures = savedSignatures,
        onSignSaved = viewModel::stampSavedSignature,
        onDeleteSignature = viewModel::deleteSavedSignature,
        onTextAnnotation = viewModel::addTextAnnotation,
        onHighlight = viewModel::addHighlight,
        onRedact = viewModel::addRedaction,
        onUndoAnnotation = viewModel::undoLastAnnotation,
        onWatermark = viewModel::addWatermark,
        onLoadMarkupBase = viewModel::markupBase,
        onApplyMarkup = viewModel::applyMarkup,
        onAppendImages = viewModel::appendImages,
        onMergeWith = viewModel::mergeWith,
        onReOcr = { viewModel.reOcr() },
        onUpdateOcrText = viewModel::updatePageOcrText,
        onOcrScript = { script -> viewModel.reOcr(script) },
        onExportIdCopy = viewModel::exportIdCopy,
        onExportLongImage = viewModel::exportLongImage,
        onExportOcrTextFile = viewModel::exportOcrTextFile,
        onExportWord = viewModel::exportWordFile,
        onExportExcel = viewModel::exportExcelFile,
        onReplaceText = viewModel::replaceText,
        onErase = viewModel::erase,
        onSplitDocument = viewModel::splitDocument,
        onExportProtectedPdf = viewModel::exportPasswordProtectedPdf,
        onExportEncryptedBackup = viewModel::exportEncryptedBackup,
        onWithFreshPdf = { onFailed, onReady -> viewModel.withFreshPdf(onFailed, onReady) },
        scanSaved = scanSaved,
        undoState = undoState,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onDismissScanSaved = viewModel::dismissScanSaved,
        onDeleteDocument = {
            viewModel.moveToTrash()
            onBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentDetailScreen(
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
    onAdjustments: (String, PageAdjustments, Boolean) -> Unit,
    onResetPage: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicatePage: (String) -> Unit,
    onExtractPage: (String) -> Unit,
    onSplitBook: (String, Float) -> Unit,
    onDeletePage: (String) -> Unit,
    onSign: (String, Bitmap, Boolean) -> Unit,
    savedSignatures: List<java.io.File> = emptyList(),
    onSignSaved: (String, java.io.File) -> Unit = { _, _ -> },
    onDeleteSignature: (java.io.File) -> Unit = {},
    onTextAnnotation: (String, String) -> Unit,
    onHighlight: (String) -> Unit,
    onRedact: (String) -> Unit,
    onUndoAnnotation: (String) -> Unit,
    onWatermark: (WatermarkStyle) -> Unit,
    onLoadMarkupBase: suspend (String) -> java.io.File,
    onApplyMarkup: (String, List<com.example.scanpro.data.PageAnnotation>) -> Unit,
    onAppendImages: (List<Uri>) -> Unit,
    onMergeWith: (String) -> Unit,
    onReOcr: () -> Unit,
    onUpdateOcrText: (String, String) -> Unit,
    onOcrScript: (OcrScript) -> Unit,
    onExportIdCopy: () -> Unit,
    onExportLongImage: () -> Unit,
    onExportOcrTextFile: () -> Unit,
    onExportWord: () -> Unit = {},
    onExportExcel: () -> Unit = {},
    onReplaceText: (String, TextEdit) -> Unit = { _, _ -> },
    onErase: (String, Map<Float, List<List<com.example.scanpro.data.NormalizedPoint>>>) -> Unit = { _, _ -> },
    onSplitDocument: () -> Unit,
    onExportProtectedPdf: (String) -> Unit,
    onExportEncryptedBackup: (String) -> Unit,
    /** Runs onReady with a current PDF (page edits rebuild it lazily), or onFailed if that isn't possible. */
    onWithFreshPdf: (onFailed: () -> Unit, onReady: (ScanDocument) -> Unit) -> Unit,
    /** True right after this document was scanned: offer share, save and rename straight away. */
    scanSaved: Boolean = false,
    undoState: com.example.scanpro.data.UndoState = com.example.scanpro.data.UndoState(),
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDismissScanSaved: () -> Unit = {},
    onDeleteDocument: () -> Unit
) {
    val context = LocalContext.current
    val pages = remember(document.pages) { document.pages.sortedBy { it.position } }
    var selectedPageId by rememberSaveable(document.id) { mutableStateOf(pages.firstOrNull()?.id) }
    val currentPage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
    val currentIndex = pages.indexOfFirst { it.id == currentPage?.id }.coerceAtLeast(0)
    // ~40 regex passes over the whole OCR text; off the main thread so long documents don't jank.
    val insights: DocumentInsights by produceState(initialValue = remember { DocumentIntelligenceEngine.analyze("") }, document.ocrText) {
        value = withContext(Dispatchers.Default) { DocumentIntelligenceEngine.analyze(document.ocrText) }
    }
    // Only read as a business card when the text has contact details; parsing runs off the main thread.
    val businessCard: BusinessCard? by produceState<BusinessCard?>(initialValue = null, document.ocrText) {
        value = withContext(Dispatchers.Default) {
            BusinessCardParser.parse(document.ocrText).takeIf { it.isUseful }
        }
    }
    val addContact: (BusinessCard) -> Unit = { card ->
        openNewContactForm(
            context,
            ContactDraft(
                name = card.name,
                company = card.company,
                jobTitle = card.jobTitle,
                phones = card.phones,
                emails = card.emails,
                website = card.website,
                address = card.address
            )
        )
    }
    var assistantQuestion by rememberSaveable(document.id) { mutableStateOf("") }
    var assistantAnswer by rememberSaveable(document.id) { mutableStateOf("") }
    val detailScope = rememberCoroutineScope()
    var translationTarget by rememberSaveable(document.id) { mutableStateOf("en") }
    // Plain remember: a whole translated document in saved state can exceed the Binder limit and crash.
    var translationResult by remember(document.id) { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }

    var renameOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var signatureOpen by remember { mutableStateOf(false) }
    var watermarkOpen by remember { mutableStateOf(false) }
    var annotationTextOpen by remember { mutableStateOf(false) }
    var cropOpen by remember { mutableStateOf(false) }
    var bookSplitOpen by rememberSaveable { mutableStateOf(false) }
    var markupOpen by rememberSaveable { mutableStateOf(false) }
    var editTextOpen by rememberSaveable { mutableStateOf(false) }
    var eraserOpen by rememberSaveable { mutableStateOf(false) }
    var adjustmentsOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var deletePageOpen by remember { mutableStateOf(false) }
    var deleteDocumentOpen by remember { mutableStateOf(false) }
    var passwordOpen by remember { mutableStateOf(false) }
    var backupPasswordOpen by remember { mutableStateOf(false) }
    var ocrEditOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var tabName by rememberSaveable { mutableStateOf(DetailTab.TEXT.name) }
    val tab = runCatching { DetailTab.valueOf(tabName) }.getOrDefault(DetailTab.TEXT)
    // Everything that isn't on the bottom toolbar lives in the "More" sheet.
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) onAppendImages(uris)
    }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            // An edit may have finished while the picker was open, so make sure the PDF is current again,
            // then copy off the main thread (cloud providers can be slow).
            val deleteDestination = { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
            onWithFreshPdf({ deleteDestination() }) { fresh ->
                detailScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        copyFileToUri(context, fresh.pdfPath, uri).also { copied ->
                            if (!copied) deleteDestination()
                        }
                    }
                    if (!ok) android.widget.Toast.makeText(context, "Could not save PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Google Drive's own "Save to Drive" screen; without Drive, the system picker, which lists other cloud apps too. */
    val saveToDrive: (String) -> Unit = { title ->
        onWithFreshPdf({}) { fresh ->
            if (!saveToGoogleDrive(context, fresh.pdfPath, "application/pdf", "$title.pdf")) {
                android.widget.Toast.makeText(context, "Google Drive isn't installed. Choose where to save.", android.widget.Toast.LENGTH_SHORT).show()
                savePdfLauncher.launch("$title.pdf")
            }
        }
    }

    if (scanSaved) {
        ScanSavedSheet(
            document = document,
            busy = busy,
            onRename = onRename,
            onSharePdf = { title -> onWithFreshPdf({}) { sharePdf(context, it.copy(title = title)) } },
            onShareJpg = { shareImages(context, document) },
            onSaveToDevice = { title -> savePdfLauncher.launch("$title.pdf") },
            onSaveToDrive = saveToDrive,
            onPrint = { title -> onWithFreshPdf({}) { printPdf(context, it.copy(title = title)) } },
            onDismiss = onDismissScanSaved
        )
    }

    // When the selected page disappears (deleted, or split into two new pages), stay at the same
    // position instead of jumping back to page 1.
    var lastSelectedIndex by rememberSaveable(document.id) { mutableIntStateOf(0) }
    // Record only while the selected page still exists: when it disappears, currentPage briefly falls
    // back to page 1 and would otherwise overwrite the position we want to return to.
    LaunchedEffect(currentIndex, selectedPageId) {
        if (pages.any { it.id == selectedPageId }) lastSelectedIndex = currentIndex
    }
    LaunchedEffect(pages.map { it.id }) {
        if (pages.none { it.id == selectedPageId }) {
            selectedPageId = pages.getOrNull(lastSelectedIndex.coerceAtMost(pages.lastIndex))?.id
        }
    }

    Scaffold(
        topBar = {
            TopBarWithProgress(busy) { TopAppBar(
                title = { TitleWithStatus(document.title, if (busy) busyLabel else null) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Undo and redo cover page edits made in this session (filters, crop, clean-up, mark-up, text, order).
                    IconButton(onClick = onUndo, enabled = !busy && undoState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    if (undoState.canRedo) {
                        IconButton(onClick = onRedo, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                    }
                    IconButton(onClick = onToggleFavorite, enabled = !busy) {
                        Icon(if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite")
                    }
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
            ) }
        },
        bottomBar = {
            // The most-used tools sit in a fixed bottom toolbar; the rest are one tap away under More.
            if (currentPage != null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BarAction(Icons.Default.Crop, "Crop", !busy) { cropOpen = true }
                        BarAction(Icons.Default.AutoFixHigh, "Clean up", !busy) { adjustmentsOpen = true }
                        BarAction(Icons.Default.Draw, "Sign", !busy) { signatureOpen = true }
                        BarAction(Icons.Default.Share, "Share PDF", !busy, primary = true) { onWithFreshPdf({}) { sharePdf(context, it) } }
                        BarAction(Icons.Default.MoreHoriz, "More", true) { moreOpen = true }
                    }
                }
            }
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
            Spacer(Modifier.height(8.dp))

            if (currentPage != null) {
                // "Ready to export" status row, as in the viewer design. Export actions are in the bottom bar.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text("Ready to export", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Text(
                        "${document.pages.size} ${if (document.pages.size == 1) "page" else "pages"} · searchable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (document.ocrText.isNotBlank()) {
                    SummaryCard(
                        documentId = document.id,
                        insights = insights,
                        card = businessCard,
                        onAddContact = addContact,
                        onMore = { tabName = DetailTab.INSIGHTS.name }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                PageThumbnail(currentPage.imagePath, Modifier.fillMaxWidth().height(400.dp), THUMBNAIL_LARGE, version = currentPage)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                        var dragDistance by remember(item.id) { mutableFloatStateOf(0f) }
                        // The gesture block outlives reorders, so read the live position instead of the captured one.
                        val latestIndex by rememberUpdatedState(index)
                        val latestLast by rememberUpdatedState(pages.lastIndex)
                        val latestBusy by rememberUpdatedState(busy)
                        val isCurrent = item.id == currentPage.id
                        Card(
                            modifier = Modifier
                                .size(width = 52.dp, height = 70.dp)
                                // The thumbnail follows the finger while dragging.
                                .graphicsLayer { translationX = dragDistance }
                                .zIndex(if (dragDistance != 0f) 1f else 0f)
                                .pointerInput(item.id) {
                                    // One move per gesture, applied on release. Moving on every slot
                                    // crossing raced the one-operation-at-a-time rule and dropped moves.
                                    val slot = 60.dp.toPx() // thumbnail width plus spacing
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragDistance = 0f },
                                        onDragEnd = {
                                            val target = (latestIndex + (dragDistance / slot).roundToInt()).coerceIn(0, latestLast)
                                            val delta = target - latestIndex
                                            dragDistance = 0f
                                            if (delta != 0 && !latestBusy) onMove(item.id, delta)
                                        },
                                        onDragCancel = { dragDistance = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDistance += amount.x
                                        }
                                    )
                                }
                                .clickable { selectedPageId = item.id },
                            shape = RoundedCornerShape(8.dp),
                            border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            PageThumbnail(item.imagePath, Modifier.fillMaxSize(), THUMBNAIL_SMALL, version = item)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pages.size > 1) "Page ${currentIndex + 1} of ${pages.size} · long-press a page to reorder" else "Page 1 of 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                // Each filter previewed on this page, made from the original photo so every look can be compared.
                val previews by produceState<Map<PageFilter, android.graphics.Bitmap>>(
                    emptyMap(), currentPage.id, currentPage.sourcePath, currentPage.crop, currentPage.rotationDegrees, currentPage.adjustments
                ) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            val photo = ImageProcessor.decodeOrientedScaled(currentPage.sourcePath, 900) ?: return@runCatching emptyMap()
                            try {
                                ImageProcessor.filterPreviews(photo, currentPage.crop, currentPage.rotationDegrees, currentPage.adjustments)
                            } finally {
                                photo.recycle()
                            }
                        }.getOrDefault(emptyMap())
                    }
                }
                FilterStrip(
                    previews = previews,
                    selected = currentPage.filter,
                    enabled = !busy,
                    onSelect = { filter -> if (filter != currentPage.filter) onApplyFilter(currentPage.id, filter) }
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
                    // Wraps on narrow phones instead of squeezing four buttons into one line.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { shareOcrText(context, document) }) { Text("Share text") }
                        TextButton(onClick = onExportOcrTextFile, enabled = !busy && document.ocrText.isNotBlank()) { Text("TXT file") }
                        TextButton(onClick = onExportWord, enabled = !busy && document.ocrText.isNotBlank()) { Text("Word file") }
                        TextButton(onClick = onExportExcel, enabled = !busy && document.ocrText.isNotBlank()) { Text("Excel file") }
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
                                translationResult = try {
                                    TranslationEngine.translate(document.ocrText, translationTarget)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    error.message ?: "Translation failed"
                                }
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
                    businessCard?.let { card ->
                        SubHeader("Contact details")
                        listOfNotNull(card.name, card.jobTitle, card.company).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        (card.phones + card.emails + listOfNotNull(card.website, card.address)).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        FilledTonalButton(onClick = { addContact(card) }, modifier = Modifier.padding(top = 4.dp)) { Text("Add to contacts") }
                    }
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
                        items(ScanMode.entries.filter { it != ScanMode.TEXT }, key = { it.name }) { mode ->
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
    if (moreOpen) {
        ModalBottomSheet(onDismissRequest = { moreOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Each tool closes the sheet first, so its dialog or result isn't hidden behind it.
            fun run(action: () -> Unit): () -> Unit = { moreOpen = false; action() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                val page = currentPage
                if (page != null) {
                    SubHeader("This page")
                    ToolGrid {
                        ToolTile(Icons.AutoMirrored.Filled.RotateRight, "Rotate", !busy, run { onRotate(page.id) })
                        ToolTile(Icons.Default.Tune, "Fine tune", !busy, run { adjustmentsOpen = true })
                        ToolTile(Icons.Default.RestartAlt, "Reset", !busy, run { onResetPage(page.id) })
                        ToolTile(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", !busy && currentIndex > 0, run { onMove(page.id, -1) })
                        ToolTile(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", !busy && currentIndex < pages.lastIndex, run { onMove(page.id, 1) })
                        ToolTile(Icons.Default.FileCopy, "Duplicate", !busy, run { onDuplicatePage(page.id) })
                        ToolTile(Icons.Default.Output, "Extract", !busy, run { onExtractPage(page.id) })
                        ToolTile(Icons.AutoMirrored.Filled.MenuBook, "Split book", !busy, run { bookSplitOpen = true })
                        ToolTile(Icons.Default.Delete, "Delete page", !busy, run { deletePageOpen = true })
                    }
                    SubHeader("Mark up")
                    ToolGrid {
                        ToolTile(Icons.Default.EditNote, "Edit text", !busy, run { editTextOpen = true })
                        ToolTile(Icons.Default.AutoFixOff, "Eraser", !busy, run { eraserOpen = true })
                        ToolTile(Icons.Default.TextFields, "Add text", !busy, run { annotationTextOpen = true })
                        ToolTile(Icons.Default.Highlight, "Highlight", !busy, run { onHighlight(page.id) })
                        ToolTile(Icons.Default.VisibilityOff, "Redact", !busy, run { onRedact(page.id) })
                        ToolTile(Icons.Default.BrandingWatermark, "Watermark", !busy, run { watermarkOpen = true })
                        ToolTile(Icons.Default.Edit, "Edit markup", !busy && page.annotations.isNotEmpty(), run { markupOpen = true })
                        ToolTile(Icons.Default.RemoveCircleOutline, "Remove last", !busy && page.annotations.isNotEmpty(), run { onUndoAnnotation(page.id) })
                    }
                }
                SubHeader("Share and save")
                ToolGrid {
                    ToolTile(Icons.Default.Image, "Share JPG", !busy, run { shareImages(context, document) })
                    ToolTile(Icons.AutoMirrored.Filled.OpenInNew, "Open PDF", !busy, run { onWithFreshPdf({}) { openPdf(context, it) } })
                    ToolTile(Icons.Default.Save, "Save PDF", !busy, run { savePdfLauncher.launch("${document.title}.pdf") })
                    ToolTile(Icons.Default.AddToDrive, "Save to Drive", !busy, run { saveToDrive(document.title) })
                    ToolTile(Icons.Default.Description, "Word (.docx)", !busy && document.ocrText.isNotBlank(), run { onExportWord() })
                    ToolTile(Icons.Default.TableChart, "Excel (.xlsx)", !busy && document.ocrText.isNotBlank(), run { onExportExcel() })
                    ToolTile(Icons.Default.Print, "Print", !busy, run { onWithFreshPdf({}) { printPdf(context, it) } })
                    ToolTile(Icons.Default.Lock, "Protect PDF", !busy, run { passwordOpen = true })
                    ToolTile(Icons.Default.Badge, "ID copy", !busy, run { onExportIdCopy() })
                    ToolTile(Icons.Default.ViewDay, "Long image", !busy, run { onExportLongImage() })
                    ToolTile(Icons.Default.Backup, "Encrypted backup", !busy, run { backupPasswordOpen = true })
                }
                SubHeader("Organise")
                ToolGrid {
                    ToolTile(Icons.Default.AddPhotoAlternate, "Add pages", !busy, run {
                        appendLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    ToolTile(Icons.Default.Layers, "Merge", !busy && otherDocuments.isNotEmpty(), run { mergeOpen = true })
                    ToolTile(Icons.Default.ContentCut, "Split all", !busy && document.pageCount > 1, run { onSplitDocument() })
                }
            }
        }
    }

    if (watermarkOpen) WatermarkDialog(onDismiss = { watermarkOpen = false }) { onWatermark(it); watermarkOpen = false }
    if (passwordOpen) PasswordInputDialog(
        title = "Protect PDF copy",
        message = "Create a password-protected PDF for sharing.",
        minimumLength = PdfSecurity.MIN_PASSWORD_LENGTH,
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
    if (annotationTextOpen && currentPage != null) TextInputDialog("Add text to page", label = "Text", confirmLabel = "Add", maxLines = 3, onDismiss = { annotationTextOpen = false }) { onTextAnnotation(currentPage.id, it); annotationTextOpen = false }
    if (ocrEditOpen && currentPage != null) TextInputDialog(
        title = "Edit recognized text",
        initial = currentPage.ocrText,
        label = "OCR text",
        confirmLabel = "Save text",
        maxLines = 12,
        onDismiss = { ocrEditOpen = false }
    ) { text -> onUpdateOcrText(currentPage.id, text); ocrEditOpen = false }

    if (signatureOpen && currentPage != null) {
        SignaturePadDialog(
            saved = savedSignatures,
            onUseSaved = { file ->
                onSignSaved(currentPage.id, file)
                signatureOpen = false
            },
            onDeleteSaved = onDeleteSignature,
            onDismiss = { signatureOpen = false }
        ) { bitmap, keep ->
            onSign(currentPage.id, bitmap, keep)
            signatureOpen = false
        }
    }

    if (markupOpen && currentPage != null) {
        MarkupEditorDialog(
            initial = currentPage.annotations,
            loadBase = { onLoadMarkupBase(currentPage.id) },
            onDismiss = { markupOpen = false },
            onApply = { edited ->
                markupOpen = false
                onApplyMarkup(currentPage.id, edited)
            }
        )
    }

    if (editTextOpen && currentPage != null) {
        EditTextDialog(
            imagePath = currentPage.imagePath,
            // Positions from hand-corrected text no longer match the words, so there are none to tap then.
            words = if (currentPage.ocrEdited) emptyList() else currentPage.ocrWords,
            onDismiss = { editTextOpen = false },
            onApply = { edit ->
                editTextOpen = false
                onReplaceText(currentPage.id, edit)
            }
        )
    }

    if (eraserOpen && currentPage != null) {
        EraserDialog(
            imagePath = currentPage.imagePath,
            onDismiss = { eraserOpen = false },
            onApply = { strokes ->
                eraserOpen = false
                onErase(currentPage.id, strokes)
            }
        )
    }

    if (bookSplitOpen && currentPage != null) {
        BookSplitDialog(
            imagePath = currentPage.imagePath,
            onDismiss = { bookSplitOpen = false },
            onConfirm = { fraction ->
                bookSplitOpen = false
                onSplitBook(currentPage.id, fraction)
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
            pageCount = pages.size,
            onDismiss = { adjustmentsOpen = false },
            onConfirm = { value, allPages ->
                onAdjustments(currentPage.id, value, allPages)
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
            message = if (pages.size == 1) "This is the only page, so the document will move to the recycle bin." else "The page will be removed from this document.",
            confirmLabel = "Delete",
            onDismiss = { deletePageOpen = false },
            onConfirm = {
                deletePageOpen = false
                if (pages.size == 1) onDeleteDocument() else onDeletePage(currentPage.id)
            }
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
    TEXT("Text"), INSIGHTS("Insights"), PDF("PDF settings")
}

/** One button on the bottom toolbar: an icon over a short label, sharing the width equally. */
@Composable
private fun RowScope.BarAction(icon: ImageVector, label: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (primary) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
