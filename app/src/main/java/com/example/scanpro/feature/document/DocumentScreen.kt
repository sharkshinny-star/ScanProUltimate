@file:Suppress("SpellCheckingInspection")

package com.example.scanpro.feature.document

import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.systemBarsPadding
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.core.designsystem.ConfirmDialog
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.PasswordInputDialog
import com.example.scanpro.core.designsystem.THUMBNAIL_GRID
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.TopBarWithProgress
import com.example.scanpro.core.ui.ContactDraft
import com.example.scanpro.core.ui.FilterStrip
import com.example.scanpro.core.ui.copyFileToUri
import com.example.scanpro.core.ui.copyText
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.openNewContactForm
import com.example.scanpro.core.ui.openPdf
import com.example.scanpro.core.ui.printPdf
import com.example.scanpro.core.ui.saveToGoogleDrive
import com.example.scanpro.core.ui.shareImages
import com.example.scanpro.core.ui.shareOcrText
import com.example.scanpro.core.ui.sharePdf
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.ScanPage
import com.example.scanpro.data.UndoState
import com.example.scanpro.data.WatermarkStyle
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.intelligence.BusinessCard
import com.example.scanpro.intelligence.BusinessCardParser
import com.example.scanpro.intelligence.DocumentInsights
import com.example.scanpro.intelligence.DocumentIntelligenceEngine
import com.example.scanpro.intelligence.TranslationEngine
import com.example.scanpro.intelligence.TranslationFeature
import com.example.scanpro.navigation.isWideWindow
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rotation is a physical direction, so this icon must not flip in right-to-left layouts (the AutoMirrored one would). */
@Suppress("DEPRECATION")
private val RotateRightIcon: ImageVector = Icons.Filled.RotateRight

/** The star's fill once the document is a favourite, from the owner's design. */
private val FavouriteGold = Color(0xFFC47A00)

/** The tabs under the top bar, in order. */
private enum class DocumentTab(@StringRes val labelRes: Int) {
    PAGES(R.string.doc_tab_pages),
    TEXT(R.string.doc_tab_text),
    INSIGHTS(R.string.doc_tab_insights)
}

@Composable
fun DocumentRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    /** Opens the camera to shoot the page again: document id, page id and the page's position. */
    onRetakePage: (String, String, Int) -> Unit = { _, _, _ -> },
    /** Opens the camera to add pages to the document (its id). */
    onAddPagesWithCamera: (String) -> Unit = {},
    /** Opens the camera for a new document (the "scan saved" sheet's Scan another). */
    onScanAnother: () -> Unit = {},
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val otherDocuments by viewModel.otherDocuments.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    val scanSaved by viewModel.scanSaved.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()

    // If the open document disappears (trashed or deleted elsewhere), leave instead of stranding the
    // user on an "unavailable" screen with no way back.
    var hadDocument by remember { mutableStateOf(false) }
    LaunchedEffect(document != null, missing) {
        if (document != null) hadDocument = true else if (missing && hadDocument) onBack()
    }

    val preview by viewModel.preview.collectAsStateWithLifecycle()

    val hiddenFolders by viewModel.hiddenFolders.collectAsStateWithLifecycle()
    val lockedDoc = document?.takeIf { d -> com.example.scanpro.core.security.FolderLock.hides(hiddenFolders, d.folderName) }
    if (lockedDoc != null) {
        // Opened from a notification, a share, search or a link while its folder is locked.
        LockedFolderGate(folder = lockedDoc.folderName, onBack = onBack, onUnlocked = { viewModel.unlockFolder(lockedDoc.folderName) })
        return
    }
    val current = document
    if (current == null) {
        // Pages are read from the database after the screen opens; only a document the library no
        // longer lists is really unavailable. Until then the screen is already laid out, with the title
        // and cover Home had, so it slides in looking ready rather than as a blank page.
        DocumentLoadingScreen(
            // Nothing of a locked folder's document shows while its pages load.
            title = preview?.takeUnless { com.example.scanpro.core.security.FolderLock.hides(hiddenFolders, it.folderName) }?.title,
            coverPath = preview?.takeUnless { com.example.scanpro.core.security.FolderLock.hides(hiddenFolders, it.folderName) }?.coverPath,
            coverVersion = preview?.updatedAt,
            unavailable = missing && !busy,
            onBack = onBack
        )
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
        libraryTags = viewModel.libraryTags.collectAsStateWithLifecycle().value,
        onFolder = viewModel::setFolder,
        onToggleFavorite = viewModel::toggleFavorite,
        onPdfQuality = viewModel::setPdfQuality,
        pdfSizeEstimates = viewModel.pdfSizeEstimates.collectAsStateWithLifecycle().value,
        onEstimatePdfSizes = viewModel::estimatePdfSizes,
        onSharePdfSelection = viewModel::sharePdfSelection,
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
        onStop = viewModel::stopCurrent,
        onUpdateOcrText = viewModel::updatePageOcrText,
        onOcrScript = { script -> viewModel.reOcr(script) },
        onExportIdCopy = viewModel::exportIdCopy,
        onExportLongImage = viewModel::exportLongImage,
        onExportPng = viewModel::exportPng,
        onDuplicateDocument = viewModel::duplicateDocument,
        onRetakePage = { pageId, position -> onRetakePage(current.id, pageId, position) },
        onAddPagesWithCamera = { onAddPagesWithCamera(current.id) },
        onScanAnother = onScanAnother,
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
            // The move is queued, never dropped, so the screen can close straight away.
            viewModel.moveToTrash(); onBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentDetailScreen(
    document: ScanDocument,
    otherDocuments: List<DocumentSummary>,
    busy: Boolean,
    busyLabel: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onTags: (String) -> Unit,
    /** Tags in use across the library, offered as chips in the Tags sheet (R35). */
    libraryTags: List<String> = emptyList(),
    onFolder: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onPdfQuality: (PdfQuality) -> Unit,
    /** Share PDF card (R28): estimates for a page choice, how to ask for them, and the share itself. */
    pdfSizeEstimates: Pair<Set<String>, Map<PdfQuality, Long>>? = null,
    onEstimatePdfSizes: (Set<String>) -> Unit = {},
    onSharePdfSelection: (Set<String>, PdfQuality, Long?) -> Unit = { _, _, _ -> },
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
    onExportPng: () -> Unit = {},
    onDuplicateDocument: () -> Unit = {},
    onRetakePage: (String, Int) -> Unit = { _, _ -> },
    /** Add page › Scan with camera; null offers photos only. */
    onAddPagesWithCamera: (() -> Unit)? = null,
    onScanAnother: (() -> Unit)? = null,
    onSign: (String, Bitmap, Boolean, SignaturePlacement?) -> Unit,
    savedSignatures: List<java.io.File> = emptyList(),
    onSignSaved: (String, java.io.File, SignaturePlacement?) -> Unit = { _, _, _ -> },
    onDeleteSignature: (java.io.File) -> Unit = {},
    onTextAnnotation: (String, String) -> Unit,
    onHighlight: (String) -> Unit,
    onRedact: (String) -> Unit,
    onUndoAnnotation: (String) -> Unit,
    onWatermark: (WatermarkStyle) -> Unit,
    onLoadMarkupBase: suspend (String) -> java.io.File,
    onApplyMarkup: (String, List<PageAnnotation>) -> Unit,
    onAppendImages: (List<Uri>) -> Unit,
    onMergeWith: (String) -> Unit,
    onReOcr: () -> Unit,
    onStop: () -> Unit = {},
    onUpdateOcrText: (String, String) -> Unit,
    onOcrScript: (OcrScript) -> Unit,
    onExportIdCopy: () -> Unit,
    onExportLongImage: () -> Unit,
    onExportOcrTextFile: () -> Unit,
    onExportWord: () -> Unit = {},
    onExportExcel: () -> Unit = {},
    onReplaceText: (String, TextEdit) -> Unit = { _, _ -> },
    onErase: (String, Map<Float, List<List<NormalizedPoint>>>) -> Unit = { _, _ -> },
    onSplitDocument: () -> Unit,
    onExportProtectedPdf: (String) -> Unit,
    onExportEncryptedBackup: (String) -> Unit,
    /** Runs onReady with a current PDF (page edits rebuild it lazily), or onFailed if that isn't possible. */
    onWithFreshPdf: (onFailed: () -> Unit, onReady: (ScanDocument) -> Unit) -> Unit,
    /** True right after this document was scanned: offer share, save and rename straight away. */
    scanSaved: Boolean = false,
    undoState: UndoState = UndoState(),
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDismissScanSaved: () -> Unit = {},
    onDeleteDocument: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val pages = remember(document.pages) { document.pages.sortedBy { it.position } }
    // The page the chip row and the per-page tools act on: the last page tapped or long-pressed, page 1 at first.
    var selectedPageId by rememberSaveable(document.id) { mutableStateOf(pages.firstOrNull()?.id) }
    val currentPage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
    val currentIndex = pages.indexOfFirst { it.id == currentPage?.id }.coerceAtLeast(0)
    val hasText = document.ocrText.isNotBlank()
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
    val scope = rememberCoroutineScope()
    val detailScope = rememberCoroutineScope()
    var translationTarget by rememberSaveable(document.id) { mutableStateOf("en") }
    // Plain remember: a whole translated document in saved state can exceed the Binder limit and crash.
    var translationResult by remember(document.id) { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }
    // Set while the on-demand translation module is being fetched from Google Play.
    var translationProgress by remember { mutableStateOf<TranslationFeature.Progress?>(null) }

    // A scan made in Text mode was taken for its words: it opens on the Text tab, where the copied text can be checked
    // (Lens, ACE Scanner and OKEN show the recognised text straight after such a scan).
    var tab by rememberSaveable(document.id) {
        mutableIntStateOf(if (document.scanMode == ScanMode.TEXT) DocumentTab.TEXT.ordinal else DocumentTab.PAGES.ordinal)
    }
    // The translate controls unfold under the Text tab's chips only when asked for.
    var translateOpen by rememberSaveable(document.id) { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    // The action waiting for "replace your corrections?" to be answered.
    var reOcrConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var splitAllOpen by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var folderOpen by rememberSaveable { mutableStateOf(false) }
    var signatureOpen by rememberSaveable { mutableStateOf(false) }
    var pendingSignature by remember { mutableStateOf<PendingSignature?>(null) }
    var watermarkOpen by rememberSaveable { mutableStateOf(false) }
    var annotationTextOpen by rememberSaveable { mutableStateOf(false) }
    var cropOpen by rememberSaveable { mutableStateOf(false) }
    var bookSplitOpen by rememberSaveable { mutableStateOf(false) }
    var markupOpen by rememberSaveable { mutableStateOf(false) }
    var editTextOpen by rememberSaveable { mutableStateOf(false) }
    var eraserOpen by rememberSaveable { mutableStateOf(false) }
    var penOpen by rememberSaveable { mutableStateOf(false) }
    var adjustmentsOpen by rememberSaveable { mutableStateOf(false) }
    var mergeOpen by rememberSaveable { mutableStateOf(false) }
    var deletePageOpen by rememberSaveable { mutableStateOf(false) }
    var deleteDocumentOpen by rememberSaveable { mutableStateOf(false) }
    var passwordOpen by rememberSaveable { mutableStateOf(false) }
    var backupPasswordOpen by rememberSaveable { mutableStateOf(false) }
    var ocrEditOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var pdfSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // Every way of getting the document out of the app, from the share icon in the top bar.
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    var sharePdfOpen by rememberSaveable { mutableStateOf(false) }
    // The rest of the per-page tools, from a long press on a page.
    var pageSheetOpen by rememberSaveable { mutableStateOf(false) }

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
                    if (!ok) android.widget.Toast.makeText(context, resources.getString(R.string.doc_could_not_save_pdf), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Google Drive's own "Save to Drive" screen; without Drive, the system picker, which lists other cloud apps too. */
    val saveToDrive: (String) -> Unit = { title ->
        onWithFreshPdf({}) { fresh ->
            if (!saveToGoogleDrive(context, fresh.pdfPath, "application/pdf", "$title.pdf")) {
                android.widget.Toast.makeText(context, resources.getString(R.string.doc_drive_not_installed), android.widget.Toast.LENGTH_SHORT).show()
                savePdfLauncher.launch("${com.example.scanpro.data.fileSafeName(title)}.pdf")
            }
        }
    }
    val sharePdfNow: () -> Unit = { onWithFreshPdf({}) { sharePdf(context, it) } }

    if (scanSaved) {
        ScanSavedSheet(
            document = document,
            busy = busy,
            onRename = onRename,
            onSharePdf = { title -> onWithFreshPdf({}) { sharePdf(context, it.copy(title = title)) } },
            onShareJpg = { shareImages(context, document) },
            onSaveToDevice = { title -> savePdfLauncher.launch("${com.example.scanpro.data.fileSafeName(title)}.pdf") },
            onSaveToDrive = saveToDrive,
            onPrint = { title -> onWithFreshPdf({}) { printPdf(context, it.copy(title = title)) } },
            onDismiss = onDismissScanSaved,
            highlight = scanHighlight(insights, businessCard, document.ocrText),
            onAddContact = { businessCard?.let(addContact) },
            onCopy = { copyText(context, it) },
            onAddPages = onAddPagesWithCamera,
            onScanAnother = onScanAnother
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

    val wide = isWideWindow()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBarWithProgress(busy, onStop = onStop) {
                Column {
                    DocumentTopBar(title = document.title, status = if (busy) busyLabel else null, onBack = onBack) {
                        val favoriteState = stringResource(if (document.isFavorite) R.string.a11y_favorite_on else R.string.a11y_favorite_off)
                        IconButton(onClick = onToggleFavorite, enabled = !busy, modifier = Modifier.semantics { stateDescription = favoriteState }) {
                            Icon(
                                if (document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.doc_favorite),
                                tint = if (document.isFavorite) FavouriteGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { exportOpen = true }, enabled = !busy) {
                            Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.doc_export))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.doc_more_options)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuItem(stringResource(R.string.common_rename), Icons.Default.Edit, !busy) { menuOpen = false; renameOpen = true }
                                MenuItem(stringResource(R.string.doc_tags), Icons.Default.Tag, !busy) { menuOpen = false; tagsOpen = true }
                                MenuItem(stringResource(R.string.doc_move_to_folder), Icons.Default.Folder, !busy) { menuOpen = false; folderOpen = true }
                                MenuItem(stringResource(R.string.doc_details), Icons.Default.Info) { menuOpen = false; detailsOpen = true }
                                MenuItem(stringResource(R.string.doc_tab_pdf_settings), Icons.Default.PictureAsPdf) { menuOpen = false; pdfSettingsOpen = true }
                                HorizontalDivider()
                                // Undo and redo cover page edits made in this session (filters, crop, clean-up, mark-up, text, order).
                                MenuItem(stringResource(R.string.common_undo), Icons.AutoMirrored.Filled.Undo, !busy && undoState.canUndo) { menuOpen = false; onUndo() }
                                if (undoState.canRedo) {
                                    MenuItem(stringResource(R.string.common_redo), Icons.AutoMirrored.Filled.Redo, !busy) { menuOpen = false; onRedo() }
                                }
                                HorizontalDivider()
                                MenuItem(stringResource(R.string.doc_open_pdf), Icons.AutoMirrored.Filled.OpenInNew, !busy) { menuOpen = false; onWithFreshPdf({}) { openPdf(context, it) } }
                                MenuItem(stringResource(R.string.doc_share_png), Icons.Default.Image, !busy) { menuOpen = false; onExportPng() }
                                MenuItem(stringResource(R.string.doc_duplicate_doc), Icons.Default.FileCopy, !busy) { menuOpen = false; onDuplicateDocument() }
                                HorizontalDivider()
                                MenuItem(stringResource(R.string.doc_move_to_recycle_bin), Icons.Default.Delete, !busy) { menuOpen = false; deleteDocumentOpen = true }
                            }
                        }
                    }
                    DocumentTabs(selected = tab, onSelect = { tab = it })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (DocumentTab.entries[tab]) {
                DocumentTab.PAGES -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (wide) 4 else 2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "markup", span = { GridItemSpan(maxLineSpan) }) {
                        // Mark-up for the current page; the watermark covers every page, as before.
                        val canMark = !busy && currentPage != null
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { ActionChip(Icons.Default.Draw, stringResource(R.string.doc_sign), canMark) { signatureOpen = true } }
                            item { ActionChip(Icons.Default.Gesture, stringResource(R.string.doc_pen), canMark) { penOpen = true } }
                            item { ActionChip(Icons.Default.TextFields, stringResource(R.string.doc_text), canMark) { annotationTextOpen = true } }
                            item { ActionChip(Icons.Default.Highlight, stringResource(R.string.doc_highlight), canMark) { currentPage?.let { onHighlight(it.id) } } }
                            item { ActionChip(Icons.Default.VisibilityOff, stringResource(R.string.doc_redact), canMark) { currentPage?.let { onRedact(it.id) } } }
                            item { ActionChip(Icons.AutoMirrored.Filled.BrandingWatermark, stringResource(R.string.doc_watermark), canMark) { watermarkOpen = true } }
                        }
                    }
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        PageCell(
                            page = page,
                            index = index,
                            count = pages.size,
                            current = page.id == currentPage?.id,
                            enabled = !busy,
                            onOpen = { selectedPageId = page.id; cropOpen = true },
                            onOptions = { selectedPageId = page.id; pageSheetOpen = true },
                            onMoveEarlier = if (index > 0) ({ onMove(page.id, -1) }) else null,
                            onMoveLater = if (index < pages.lastIndex) ({ onMove(page.id, 1) }) else null,
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "organise", span = { GridItemSpan(maxLineSpan) }) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // Add page offers the camera first, as Genius Scan, ACE and CamScanner do, and photos second.
                            var addChoice by remember { mutableStateOf(false) }
                            val pickPhotos = { appendLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            Box {
                                FilledTonalButton(
                                    onClick = { if (onAddPagesWithCamera != null) addChoice = true else pickPhotos() },
                                    enabled = !busy
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.doc_add_page))
                                }
                                DropdownMenu(expanded = addChoice, onDismissRequest = { addChoice = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.doc_add_page_camera)) },
                                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                                        onClick = { addChoice = false; onAddPagesWithCamera?.invoke() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.doc_add_page_photos)) },
                                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                        onClick = { addChoice = false; pickPhotos() }
                                    )
                                }
                            }
                            OutlinedButton(onClick = { splitAllOpen = true }, enabled = !busy && document.pageCount > 1) { Text(stringResource(R.string.doc_split)) }
                            OutlinedButton(onClick = { mergeOpen = true }, enabled = !busy && otherDocuments.isNotEmpty()) { Text(stringResource(R.string.doc_merge)) }
                        }
                    }
                }

                DocumentTab.TEXT -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ActionChip(Icons.Default.ContentCopy, stringResource(R.string.doc_copy), hasText) { copyText(context, document.ocrText) } }
                        item {
                            var exportMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                ActionChip(Icons.Default.IosShare, stringResource(R.string.doc_export_txt), true, trailing = Icons.Default.ArrowDropDown) { exportMenuOpen = true }
                                DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                    val canExport = !busy && hasText
                                    MenuItem(stringResource(R.string.doc_txt_file), Icons.Default.TextFields, canExport) { exportMenuOpen = false; onExportOcrTextFile() }
                                    MenuItem(stringResource(R.string.doc_word_file), Icons.Default.Description, canExport) { exportMenuOpen = false; onExportWord() }
                                    MenuItem(stringResource(R.string.doc_excel_file), Icons.Default.TableChart, canExport) { exportMenuOpen = false; onExportExcel() }
                                    MenuItem(stringResource(R.string.doc_share_text), Icons.Default.Share) { exportMenuOpen = false; shareOcrText(context, document) }
                                }
                            }
                        }
                        item {
                            FilterChip(
                                selected = translateOpen,
                                onClick = { translateOpen = !translateOpen },
                                label = { Text(stringResource(R.string.doc_translate)) },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                            )
                        }
                        item {
                            // The script the pages were read in; the menu switches script (reading again) or re-runs OCR.
                            var scriptMenuOpen by remember { mutableStateOf(false) }
                            val scriptLabel = stringResource(document.ocrScript.labelRes)
                            val scriptDescription = stringResource(R.string.doc_ocr_script_current, scriptLabel)
                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { scriptMenuOpen = true },
                                    label = { Text(scriptLabel) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    modifier = Modifier.semantics { contentDescription = scriptDescription }
                                )
                                DropdownMenu(expanded = scriptMenuOpen, onDismissRequest = { scriptMenuOpen = false }) {
                                    Text(
                                        stringResource(R.string.doc_ocr_script),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                    OcrScript.entries.forEach { script ->
                                        val isSelected = document.ocrScript == script
                                        DropdownMenuItem(
                                            text = { Text(stringResource(script.labelRes)) },
                                            // Reading again in another script replaces corrections, so it asks first.
                                            onClick = {
                                                scriptMenuOpen = false
                                                if (pages.any { it.ocrEdited } && !isSelected) reOcrConfirm = { onOcrScript(script) } else onOcrScript(script)
                                            },
                                            enabled = !busy,
                                            trailingIcon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                                            modifier = Modifier.semantics { selected = isSelected }
                                        )
                                    }
                                    HorizontalDivider()
                                    MenuItem(stringResource(R.string.doc_rerun_ocr), Icons.Default.Replay, !busy) {
                                        scriptMenuOpen = false
                                        if (pages.any { it.ocrEdited }) reOcrConfirm = { onReOcr() } else onReOcr()
                                    }
                                }
                            }
                        }
                    }
                    if (translateOpen) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            var languageMenuOpen by remember { mutableStateOf(false) }
                            val targetLabel = TranslationEngine.commonTargets.firstOrNull { it.languageTag == translationTarget }?.label ?: translationTarget
                            Box {
                                val translateTo = stringResource(R.string.doc_translate_to_language, targetLabel)
                                OutlinedButton(
                                    onClick = { languageMenuOpen = true },
                                    modifier = Modifier.semantics { contentDescription = translateTo }
                                ) {
                                    Text(targetLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                                    Text(
                                        stringResource(R.string.doc_translate_to),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                    TranslationEngine.commonTargets.forEach { target ->
                                        val isSelected = translationTarget == target.languageTag
                                        DropdownMenuItem(
                                            text = { Text(target.label) },
                                            onClick = { languageMenuOpen = false; translationTarget = target.languageTag },
                                            trailingIcon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                                            modifier = Modifier.semantics { selected = isSelected }
                                        )
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    detailScope.launch {
                                        translating = true
                                        translationResult = try {
                                            val translator = TranslationFeature.translator(context) { translationProgress = it }
                                            translationProgress = null
                                            translator.translate(document.ocrText, translationTarget)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            // A failure is a message, not a "translation" the user could copy or share.
                                            android.widget.Toast.makeText(context, R.string.doc_translation_failed, android.widget.Toast.LENGTH_LONG).show()
                                            ""
                                        } finally {
                                            translating = false
                                            translationProgress = null
                                        }
                                    }
                                },
                                enabled = hasText && !translating
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (val progress = translationProgress) {
                                        is TranslationFeature.Progress.Preparing -> stringResource(R.string.doc_preparing_translation)
                                        is TranslationFeature.Progress.Downloading -> stringResource(R.string.doc_downloading_translation, progress.percent)
                                        is TranslationFeature.Progress.Installing -> stringResource(R.string.doc_installing_translation)
                                        null -> if (translating) stringResource(R.string.doc_translating) else stringResource(R.string.doc_translate)
                                    }
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.doc_translate_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (translationResult.isNotBlank()) {
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                SelectionContainer { Text(translationResult, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // The whole document, page by page. Tapping a page's text (or its pencil) opens the editor
                    // for that page; the saved text is what search indexes.
                    val editLabel = stringResource(R.string.doc_edit_recognized_text)
                    pages.forEachIndexed { index, page ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MonoHeader(stringResource(R.string.doc_page_n, index + 1), modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedPageId = page.id; ocrEditOpen = true }, enabled = !busy) {
                                Icon(Icons.Default.Edit, contentDescription = editLabel, modifier = Modifier.size(20.dp))
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = !busy, role = Role.Button, onClickLabel = editLabel) { selectedPageId = page.id; ocrEditOpen = true }
                        ) {
                            val blank = page.ocrText.isBlank()
                            Text(
                                if (blank) stringResource(R.string.doc_no_text_recognized) else page.ocrText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = if (blank) null else FontFamily.Monospace,
                                lineHeight = 20.sp,
                                color = if (blank) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        stringResource(R.string.doc_text_editable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DocumentTab.INSIGHTS -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasText) {
                        InsightCard(stringResource(R.string.doc_detected_as)) {
                            Text(stringResource(R.string.doc_insights_no_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        InsightCard(stringResource(R.string.doc_detected_as)) {
                            Text(stringResource(insights.kind.labelRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        val card = businessCard
                        val facts = buildList {
                            insights.amounts.forEach { add(R.string.doc_fact_label_amount to it) }
                            insights.dates.forEach { add(R.string.doc_fact_label_date to it) }
                            insights.references.forEach { add(R.string.doc_fact_label_reference to it) }
                            if (card != null) {
                                card.name?.let { add(R.string.doc_fact_label_name to it) }
                                card.jobTitle?.let { add(R.string.doc_fact_label_role to it) }
                                card.company?.let { add(R.string.doc_fact_label_company to it) }
                                card.phones.forEach { add(R.string.doc_fact_label_phone to it) }
                                card.emails.forEach { add(R.string.doc_fact_label_email to it) }
                                card.website?.let { add(R.string.doc_fact_label_website to it) }
                                card.address?.let { add(R.string.doc_fact_label_address to it) }
                            }
                        }
                        if (facts.isNotEmpty()) {
                            InsightCard(stringResource(R.string.doc_found_in_text)) {
                                facts.forEach { (labelRes, value) -> FactRow(stringResource(labelRes), value) }
                                if (card != null) {
                                    FilledTonalButton(onClick = { addContact(card) }, modifier = Modifier.padding(top = 6.dp)) {
                                        Text(stringResource(R.string.doc_add_to_contacts))
                                    }
                                }
                            }
                        }
                        InsightCard(stringResource(R.string.doc_summary)) {
                            SelectionContainer { Text(insights.summary, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp) }
                        }
                        if (insights.actionItems.isNotEmpty()) {
                            InsightCard(stringResource(R.string.doc_potential_action_items)) {
                                insights.actionItems.forEach { item ->
                                    Text(stringResource(R.string.doc_bullet_item, item), style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                    InsightCard(stringResource(R.string.doc_ask_about_scan)) {
                        val ask = {
                            val question = assistantQuestion
                            val text = document.ocrText
                            if (question.isNotBlank() && text.isNotBlank()) {
                                scope.launch { assistantAnswer = withContext(Dispatchers.Default) { DocumentIntelligenceEngine.answer(question, text) } }
                            }
                        }
                        OutlinedTextField(
                            value = assistantQuestion,
                            onValueChange = { assistantQuestion = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.doc_ask_placeholder)) },
                            trailingIcon = {
                                IconButton(onClick = ask, enabled = assistantQuestion.isNotBlank() && hasText) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.doc_find_answer))
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { ask() }),
                            maxLines = 3
                        )
                        // Answers are passages of the recognised text, so they read in the same mono as the text tab.
                        SelectionContainer {
                            Text(
                                assistantAnswer.ifBlank { stringResource(R.string.doc_ask_supporting) },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp,
                                color = if (assistantAnswer.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    if (insights.suggestedTags.isNotEmpty()) {
                        InsightCard(stringResource(R.string.doc_suggested_tags)) {
                            Text(insights.suggestedTags.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    val merged = (document.tags + insights.suggestedTags).distinctBy { it.lowercase() }
                                    onTags(merged.joinToString(", "))
                                }
                            ) { Text(stringResource(R.string.doc_apply_suggested_tags)) }
                        }
                    }
                }
            }
        }
    }

    if (exportOpen) {
        ModalBottomSheet(onDismissRequest = { exportOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Each row closes the sheet first, so the share dialog or password prompt isn't hidden behind it.
            fun run(action: () -> Unit): () -> Unit = { exportOpen = false; action() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    stringResource(R.string.doc_export_title, document.title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                val quality = stringResource(document.pdfQuality.labelRes)
                val pageSize = stringResource(document.pdfPageSize.labelRes)
                ExportRow(
                    Icons.Default.PictureAsPdf,
                    stringResource(R.string.doc_export_pdf),
                    if (document.searchablePdf) stringResource(R.string.doc_export_pdf_hint_searchable, quality, pageSize) else stringResource(R.string.doc_export_pdf_hint, quality, pageSize),
                    !busy,
                    run { sharePdfOpen = true }
                )
                ExportRow(Icons.Default.Lock, stringResource(R.string.doc_protected_pdf), stringResource(R.string.doc_protected_pdf_hint), !busy, run { passwordOpen = true })
                ExportRow(Icons.Default.Image, stringResource(R.string.doc_share_as_images), stringResource(R.string.doc_share_as_images_hint), !busy, run { shareImages(context, document) })
                ExportRow(Icons.Default.ViewDay, stringResource(R.string.doc_long_image), stringResource(R.string.doc_long_image_hint), !busy, run(onExportLongImage))
                ExportRow(Icons.Default.Badge, stringResource(R.string.doc_id_copy_sheet), stringResource(R.string.doc_id_copy_sheet_hint), !busy, run(onExportIdCopy))
                ExportRow(Icons.Default.Print, stringResource(R.string.doc_print), stringResource(R.string.doc_print_hint), !busy, run { onWithFreshPdf({}) { printPdf(context, it) } })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                ExportRow(Icons.Default.Save, stringResource(R.string.doc_save_pdf), stringResource(R.string.doc_save_to_device_hint), !busy, run { savePdfLauncher.launch("${com.example.scanpro.data.fileSafeName(document.title)}.pdf") })
                ExportRow(Icons.Default.AddToDrive, stringResource(R.string.doc_save_to_drive), stringResource(R.string.doc_save_to_google_drive_hint), !busy, run { saveToDrive(com.example.scanpro.data.fileSafeName(document.title)) })
                ExportRow(Icons.Default.Description, stringResource(R.string.doc_word_docx), stringResource(R.string.doc_word_docx_hint), !busy && hasText, run(onExportWord))
                ExportRow(Icons.Default.TableChart, stringResource(R.string.doc_excel_xlsx), stringResource(R.string.doc_excel_xlsx_hint), !busy && hasText, run(onExportExcel))
                ExportRow(Icons.Default.Backup, stringResource(R.string.doc_encrypted_backup), stringResource(R.string.doc_encrypted_backup_hint), !busy, run { backupPasswordOpen = true })
            }
        }
    }

    if (pageSheetOpen && currentPage != null) {
        ModalBottomSheet(onDismissRequest = { pageSheetOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Each tool closes the sheet first, so its dialog or result isn't hidden behind it.
            fun run(action: () -> Unit): () -> Unit = { pageSheetOpen = false; action() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    stringResource(R.string.viewer_page_of, currentIndex + 1, pages.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
                Eyebrow(stringResource(R.string.doc_filters), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                // Each filter previewed on this page, made from the original photo so every look can be compared.
                val previews by produceState<Map<PageFilter, Bitmap>>(
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
                    onSelect = { filter -> if (filter != currentPage.filter) onApplyFilter(currentPage.id, filter) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                val hasMarkup = currentPage.annotations.isNotEmpty()
                SheetRow(Icons.Default.Tune, stringResource(R.string.doc_clean_up), !busy, run { adjustmentsOpen = true })
                SheetRow(RotateRightIcon, stringResource(R.string.doc_rotate), !busy, run { onRotate(currentPage.id) })
                SheetRow(Icons.Default.Replay, stringResource(R.string.doc_retake), !busy, run { onRetakePage(currentPage.id, currentIndex) })
                SheetRow(Icons.Default.RestartAlt, stringResource(R.string.common_reset), !busy, run { onResetPage(currentPage.id) })
                SheetRow(Icons.Default.FileCopy, stringResource(R.string.doc_duplicate), !busy, run { onDuplicatePage(currentPage.id) })
                SheetRow(Icons.Default.Output, stringResource(R.string.doc_extract), !busy, run { onExtractPage(currentPage.id) })
                SheetRow(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.doc_split_book), !busy, run { bookSplitOpen = true })
                SheetRow(Icons.Default.Gesture, stringResource(R.string.doc_pen), !busy, run { penOpen = true })
                SheetRow(Icons.Default.AutoFixOff, stringResource(R.string.doc_eraser), !busy, run { eraserOpen = true })
                SheetRow(Icons.Default.EditNote, stringResource(R.string.doc_edit_text), !busy, run { editTextOpen = true })
                SheetRow(Icons.Default.Edit, stringResource(R.string.doc_edit_markup), !busy && hasMarkup, run { markupOpen = true })
                SheetRow(Icons.AutoMirrored.Filled.Notes, stringResource(R.string.doc_edit_recognized_text), !busy, run { ocrEditOpen = true })
                SheetRow(Icons.Default.RemoveCircleOutline, stringResource(R.string.doc_remove_last), !busy && hasMarkup, run { onUndoAnnotation(currentPage.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                SheetRow(Icons.Default.Delete, stringResource(R.string.doc_delete_page), !busy, run { if (pages.size == 1) deletePageOpen = true else onDeletePage(currentPage.id) }, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (sharePdfOpen) {
        SharePdfSheet(
            pages = pages,
            preferredQuality = document.pdfQuality,
            estimates = pdfSizeEstimates?.second,
            busy = busy,
            onEstimate = onEstimatePdfSizes,
            onShare = onSharePdfSelection,
            onDismiss = { sharePdfOpen = false }
        )
    }

    if (pdfSettingsOpen) {
        ModalBottomSheet(onDismissRequest = { pdfSettingsOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                Text(stringResource(R.string.doc_tab_pdf_settings), style = MaterialTheme.typography.titleLarge)
                SubHeader(stringResource(R.string.doc_quality))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfQuality.entries.forEach { quality ->
                        FilterChip(selected = document.pdfQuality == quality, enabled = !busy, onClick = { onPdfQuality(quality) }, label = { Text(stringResource(quality.labelRes)) })
                    }
                }
                SubHeader(stringResource(R.string.doc_page_size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { size ->
                        FilterChip(selected = document.pdfPageSize == size, enabled = !busy, onClick = { onPdfPageSize(size) }, label = { Text(stringResource(size.labelRes)) })
                    }
                }
                SubHeader(stringResource(R.string.doc_document_mode))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanMode.entries.filter { it != ScanMode.TEXT }.forEach { mode ->
                        FilterChip(selected = document.scanMode == mode, enabled = !busy, onClick = { onScanMode(mode) }, label = { Text(stringResource(mode.labelRes)) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                ToggleRow(stringResource(R.string.doc_searchable_pdf), stringResource(R.string.doc_searchable_pdf_hint), document.searchablePdf, !busy, onSearchablePdf)
                ToggleRow(stringResource(R.string.doc_page_numbers), stringResource(R.string.doc_page_numbers_hint), document.pageNumbers, !busy, onPageNumbers)
                // Settings chosen here apply to the next share, so it is offered straight from here.
                FilledTonalButton(
                    onClick = { pdfSettingsOpen = false; sharePdfNow() },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.doc_share_pdf))
                }
            }
        }
    }

    if (renameOpen) TextInputDialog(stringResource(R.string.doc_rename_document), document.title, stringResource(R.string.doc_name), onDismiss = { renameOpen = false }) { onRename(it.take(120)); renameOpen = false }
    reOcrConfirm?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.doc_replace_corrected_title),
            message = stringResource(R.string.doc_replace_corrected_message),
            confirmLabel = stringResource(R.string.doc_read_again),
            onDismiss = { reOcrConfirm = null },
            onConfirm = { reOcrConfirm = null; action() }
        )
    }
    if (splitAllOpen) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.doc_split_into_title, document.pageCount, document.pageCount),
            message = stringResource(R.string.doc_split_into_message, document.title),
            confirmLabel = stringResource(R.string.doc_split),
            onDismiss = { splitAllOpen = false },
            onConfirm = { splitAllOpen = false; onSplitDocument() }
        )
    }
    if (tagsOpen) TagsSheet(current = document.tags, suggestions = libraryTags, onDismiss = { tagsOpen = false }) { onTags(it.joinToString(", ")); tagsOpen = false }
    if (folderOpen) TextInputDialog(stringResource(R.string.doc_move_to_folder), document.folderName, stringResource(R.string.doc_folder_name), allowBlank = true, onDismiss = { folderOpen = false }) { onFolder(it); folderOpen = false }

    if (watermarkOpen) WatermarkDialog(onDismiss = { watermarkOpen = false }) { onWatermark(it); watermarkOpen = false }
    if (passwordOpen) PasswordInputDialog(
        title = stringResource(R.string.doc_protect_pdf_copy),
        message = stringResource(R.string.doc_protect_pdf_message),
        minimumLength = PdfSecurity.MIN_PASSWORD_LENGTH,
        confirmLabel = stringResource(R.string.doc_create),
        onDismiss = { passwordOpen = false },
        confirmTwice = true
    ) { onExportProtectedPdf(it); passwordOpen = false }
    if (backupPasswordOpen) PasswordInputDialog(
        title = stringResource(R.string.doc_encrypted_backup_title),
        message = stringResource(R.string.doc_encrypted_backup_message),
        minimumLength = EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH,
        confirmLabel = stringResource(R.string.doc_create_backup),
        onDismiss = { backupPasswordOpen = false },
        // Backups keep each page's original photo so edits stay reversible, including what redaction or erasing hid.
        warning = if (document.hidesContent()) stringResource(R.string.backup_warning_unredacted_document) else null,
        confirmTwice = true
    ) { onExportEncryptedBackup(it); backupPasswordOpen = false }
    if (annotationTextOpen && currentPage != null) TextInputDialog(stringResource(R.string.doc_add_text_to_page), label = stringResource(R.string.doc_text), confirmLabel = stringResource(R.string.doc_add), maxLines = 3, onDismiss = { annotationTextOpen = false }) { onTextAnnotation(currentPage.id, it); annotationTextOpen = false }
    if (ocrEditOpen && currentPage != null) TextInputDialog(
        title = stringResource(R.string.doc_edit_recognized_text),
        initial = currentPage.ocrText,
        label = stringResource(R.string.doc_ocr_text),
        confirmLabel = stringResource(R.string.doc_save_text),
        maxLines = 12,
        onDismiss = { ocrEditOpen = false }
    ) { text -> onUpdateOcrText(currentPage.id, text); ocrEditOpen = false }

    if (signatureOpen && currentPage != null) {
        SignaturePadDialog(
            saved = savedSignatures,
            onUseSaved = { file ->
                signatureOpen = false
                pendingSignature = PendingSignature.Saved(file)
            },
            onDeleteSaved = onDeleteSignature,
            onDismiss = { signatureOpen = false }
        ) { bitmap, keep ->
            signatureOpen = false
            pendingSignature = PendingSignature.Drawn(bitmap, keep)
        }
    }
    // Second step: put the signature where it belongs on the page, then Save.
    val pending = pendingSignature
    if (pending != null && currentPage != null) {
        val pageId = currentPage.id
        SignaturePlacementDialog(
            loadPage = { onLoadMarkupBase(pageId) },
            loadSignature = {
                when (pending) {
                    is PendingSignature.Drawn -> pending.bitmap
                    is PendingSignature.Saved -> ImageProcessor.decodeScaled(pending.file.absolutePath, 1600)
                }
            },
            onDismiss = { pendingSignature = null },
            onSave = { placement ->
                pendingSignature = null
                when (pending) {
                    is PendingSignature.Drawn -> onSign(pageId, pending.bitmap, pending.keep, placement)
                    is PendingSignature.Saved -> onSignSaved(pageId, pending.file, placement)
                }
            }
        )
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

    if (penOpen && currentPage != null) {
        PenDialog(
            imagePath = currentPage.imagePath,
            onDismiss = { penOpen = false },
            onApply = { ink ->
                penOpen = false
                onApplyMarkup(currentPage.id, currentPage.annotations + ink)
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
            },
            pageIndex = currentIndex,
            pageCount = pages.size,
            // Moving to another page keeps the editor open; a changed crop on the page being left is applied first.
            onPage = { delta, crop ->
                if (crop != currentPage.crop) onCrop(currentPage.id, crop)
                pages.getOrNull(currentIndex + delta)?.let { selectedPageId = it.id }
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
        var mergeQuery by rememberSaveable { mutableStateOf("") }
        val candidates = remember(otherDocuments, mergeQuery) {
            otherDocuments.filter { mergeQuery.isBlank() || it.title.contains(mergeQuery, ignoreCase = true) }
                .sortedByDescending { it.updatedAt }
        }
        AlertDialog(
            onDismissRequest = { mergeOpen = false },
            title = { Text(stringResource(R.string.doc_merge_with_another)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = mergeQuery,
                        onValueChange = { mergeQuery = it },
                        label = { Text(stringResource(R.string.doc_search_documents)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.doc_no_documents_match), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(candidates, key = { it.id }) { other ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onMergeWith(other.id)
                                    mergeOpen = false
                                }
                            ) { Text(other.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mergeOpen = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (deletePageOpen && currentPage != null) {
        ConfirmDialog(
            title = stringResource(R.string.doc_delete_this_page),
            message = if (pages.size == 1) stringResource(R.string.doc_delete_only_page_message) else stringResource(R.string.doc_delete_page_message),
            confirmLabel = stringResource(R.string.common_delete),
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
            title = { Text(stringResource(R.string.doc_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.doc_details_pages, document.pageCount))
                    Text(stringResource(R.string.doc_details_pdf_size, formatBytes(LocalContext.current, document.sizeBytes)))
                    Text(stringResource(R.string.doc_details_updated, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.updatedAt))))
                    if (document.folderName.isNotBlank()) Text(stringResource(R.string.doc_details_folder, document.folderName))
                    if (document.tags.isNotEmpty()) Text(stringResource(R.string.doc_details_tags, document.tags.joinToString(", ")))
                }
            },
            confirmButton = { TextButton(onClick = { detailsOpen = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }

    if (deleteDocumentOpen) {
        ConfirmDialog(
            title = stringResource(R.string.doc_move_to_recycle_bin_question),
            message = stringResource(R.string.doc_move_to_recycle_bin_message),
            confirmLabel = stringResource(R.string.doc_move),
            onDismiss = { deleteDocumentOpen = false },
            onConfirm = { onDeleteDocument(); deleteDocumentOpen = false }
        )
    }
}

/** The 52 dp bar from the design: back, the title (with the running task under it while busy) and the actions. */
@Composable
private fun DocumentTopBar(title: String, status: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
        Column(
            Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (!status.isNullOrBlank()) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Pages · Text · Insights, with the design's 3 dp underline on the selected tab. */
@Composable
private fun DocumentTabs(selected: Int, onSelect: (Int) -> Unit) {
    SecondaryTabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(selected, matchContentSize = false),
                height = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) }
    ) {
        DocumentTab.entries.forEachIndexed { index, item ->
            val isSelected = index == selected
            Tab(
                selected = isSelected,
                onClick = { onSelect(index) },
                // One line that shrinks to fit: "நுண்ணறிவு" used to wrap in the middle of the word (R46).
                text = { com.example.scanpro.core.designsystem.FitText(stringResource(item.labelRes), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One page in the grid: the thumbnail, its number under it and the earlier/later buttons. A tap opens the
 * crop editor; a long press the rest of the page's tools. The current page (the one the chip row acts on)
 * has a primary border.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCell(
    page: ScanPage,
    index: Int,
    count: Int,
    current: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    onMoveEarlier: (() -> Unit)?,
    onMoveLater: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val description = stringResource(R.string.viewer_page_of, index + 1, count)
    val editLabel = stringResource(R.string.doc_edit_page, index + 1)
    val optionsLabel = stringResource(R.string.doc_page_options)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, if (current) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = editLabel,
                onLongClickLabel = optionsLabel,
                onClick = onOpen,
                onLongClick = onOptions
            )
            .semantics { selected = current }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
    ) {
        PageThumbnail(
            page.imagePath,
            Modifier
                .fillMaxWidth()
                .aspectRatio(210f / 297f),
            THUMBNAIL_GRID,
            version = page,
            shape = RoundedCornerShape(4.dp),
            background = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentDescription = description
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
            )
            Row(Modifier.align(Alignment.CenterEnd)) {
                MoveButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.doc_move_earlier), enabled, onMoveEarlier)
                MoveButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.doc_move_later), enabled, onMoveLater)
            }
        }
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: (() -> Unit)?) {
    val active = enabled && onClick != null
    IconButton(onClick = onClick ?: {}, enabled = active, modifier = Modifier.size(40.dp)) {
        // An explicit tint overrides the button's disabled colour, so the dimming is applied here.
        val tint = MaterialTheme.colorScheme.onSurfaceVariant.let { if (active) it else it.copy(alpha = 0.3f) }
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
    }
}

/** A tool chip: an icon and a short label, with an optional trailing icon for chips that open a menu. */
@Composable
private fun ActionChip(icon: ImageVector, label: String, enabled: Boolean, trailing: ImageVector? = null, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        trailingIcon = trailing?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } }
    )
}

/** One way out of the app in the export sheet: what it makes and, under it, what that is. */
@Composable
private fun ExportRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, TileTone.Indigo, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One tool in the per-page sheet. */
@Composable
private fun SheetRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** A card on the Insights tab: a small uppercase mono header over its content. */
@Composable
private fun InsightCard(header: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            MonoHeader(header)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** Small uppercase mono label, as the design uses over cards and page headers. */
@Composable
private fun MonoHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }
    )
}

/** "Label · value" in the Found in the text card; the value in mono so numbers line up. */
@Composable
private fun FactRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SubHeader(value: String) {
    Spacer(Modifier.height(16.dp))
    Eyebrow(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
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

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    // The whole row is the switch, so a screen reader hears the title, hint and state as one control.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

/** True when some page hides content of the original photo: redaction, erasing, or text typed over the old words. */
internal fun ScanDocument.hidesContent(): Boolean = pages.any { page ->
    page.annotations.any {
        it.type == AnnotationType.REDACT || it.type == AnnotationType.ERASE || (it.type == AnnotationType.TEXT && it.background != null)
    }
}

/** A signature waiting to be placed: freshly drawn (and maybe to be kept), or one of the saved ones. */
private sealed interface PendingSignature {
    class Drawn(val bitmap: Bitmap, val keep: Boolean) : PendingSignature
    class Saved(val file: java.io.File) : PendingSignature
}

/**
 * What the document screen shows while its pages are read: the same bar and tabs as the loaded screen, on the
 * normal background, with the title and cover the library already had (when it had them), so opening a document
 * from Recent feels instant instead of sliding in a blank page.
 */
@Composable
private fun DocumentLoadingScreen(title: String?, coverPath: String?, coverVersion: Any?, unavailable: Boolean, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                DocumentTopBar(title = title.orEmpty(), status = null, onBack = onBack)
                DocumentTabs(selected = DocumentTab.PAGES.ordinal, onSelect = {})
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
        ) {
            if (unavailable) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.doc_unavailable)) }
                return@Column
            }
            val opening = stringResource(R.string.doc_opening_document)
            // Stands in for the chip row, so nothing jumps when the document arrives.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 84.dp, height = 32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // The first row of the grid: the cover where the first page will be, blank cells beside it.
            val columns = if (isWideWindow()) 4 else 2
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = opening },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(columns) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 26.dp)
                    ) {
                        if (index == 0 && coverPath != null) {
                            PageThumbnail(
                                coverPath,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(210f / 297f),
                                THUMBNAIL_GRID,
                                version = coverVersion,
                                shape = RoundedCornerShape(4.dp),
                                background = MaterialTheme.colorScheme.surfaceContainerLowest,
                                contentDescription = null
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(210f / 297f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shown instead of a document whose folder is locked and not opened this session. */
@Composable
private fun LockedFolderGate(folder: String, onBack: () -> Unit, onUnlocked: () -> Unit) {
    val unlock = com.example.scanpro.core.security.LocalDeviceUnlock.current
    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.doc_locked_folder_title, folder), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.doc_locked_folder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { unlock(onUnlocked) }) { Text(stringResource(R.string.doc_unlock)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
    }
}

/**
 * The scan's smart action for the "scan saved" sheet: a business card with contact details, or a receipt or invoice
 * whose total was found; null for anything else.
 */
internal fun scanHighlight(insights: DocumentInsights, card: BusinessCard?, text: String): ScanHighlight? = when {
    card != null -> ScanHighlight.Contact(card.name, card.company ?: card.phones.firstOrNull() ?: card.emails.firstOrNull())
    insights.kind == com.example.scanpro.intelligence.DocumentKind.RECEIPT || insights.kind == com.example.scanpro.intelligence.DocumentKind.INVOICE ->
        DocumentIntelligenceEngine.total(text)?.let { ScanHighlight.Bill(insights.kind, it, insights.dates.firstOrNull()) }
    else -> null
}
