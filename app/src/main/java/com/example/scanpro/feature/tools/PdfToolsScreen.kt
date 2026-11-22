package com.example.scanpro.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.imaging.TextExportFormat
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.ui.openFile
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.data.FirstRunHints
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.data.ToolResultFile
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageToolsEngine
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfToolsEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Office to PDF picker offers; the converter checks the file itself too. */
private val OFFICE_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-word.document.macroenabled.12",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel.sheet.macroenabled.12",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "application/vnd.ms-powerpoint.presentation.macroenabled.12",
    "text/plain", "text/csv", "text/comma-separated-values", "application/csv",
    "text/markdown", "text/html", "application/xhtml+xml",
    "multipart/related", "message/rfc822", "application/x-mimearchive"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onOpenPdfViewer: (Uri, String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: PdfToolsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Only leftovers from earlier sessions: a recent folder may belong to an editor that survived a screen turn
            // or tab switch (its photo list is restored) or to a conversion still running.
            val cutoff = System.currentTimeMillis() - STALE_STAGING_MS
            context.cacheDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("img2pdf_") && file.lastModified() < cutoff
            }?.forEach { it.deleteRecursively() }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val pdfDocumentTitle = stringResource(R.string.tools_pdf_document)

    // Whether a tool is running is all this screen needs: reading the progress itself here made the whole screen (and
    // every dialog in it) recompose on each tick of every tool. The overlay below collects the ticks on its own.
    val toolRunning by viewModel.toolRunning.collectAsStateWithLifecycle()
    val lastResult by viewModel.result.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val unlockNotice by viewModel.unlockNotice.collectAsStateWithLifecycle()
    val passwordNeeded by viewModel.passwordNeeded.collectAsStateWithLifecycle()
    val wrongPassword by viewModel.wrongPassword.collectAsStateWithLifecycle()
    val mergePassword by viewModel.mergePassword.collectAsStateWithLifecycle()
    val recentPdfs by viewModel.recentPdfs.collectAsStateWithLifecycle()
    val toolResults by viewModel.toolResults.collectAsStateWithLifecycle()
    val recentScope = rememberCoroutineScope()
    val formSession by viewModel.form.collectAsStateWithLifecycle()
    val formWithoutFields by viewModel.formWithoutFields.collectAsStateWithLifecycle()
    val signViewModel: SignPdfViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val signState by signViewModel.state.collectAsStateWithLifecycle()
    var showOcrDialog by rememberSaveable { mutableStateOf(false) }

    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImageUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var showCompressDialog by rememberSaveable { mutableStateOf(false) }
    var showCompressImageDialog by rememberSaveable { mutableStateOf(false) }
    var showConvertImageDialog by rememberSaveable { mutableStateOf(false) }
    var showResizeImageDialog by rememberSaveable { mutableStateOf(false) }
    var showPdfToImagesDialog by rememberSaveable { mutableStateOf(false) }
    // Office to PDF, Extract images and Repair open their own pages first (F211), so the result is known before running.
    var officeUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showExtractImagesPage by rememberSaveable { mutableStateOf(false) }
    var showRepairPage by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showSplitDialog by rememberSaveable { mutableStateOf(false) }
    var showOrganizerSheet by rememberSaveable { mutableStateOf(false) }
    // Arrange, Rotate or Extract: one Organise screen, switched at its top.
    var organizerMode by rememberSaveable { mutableStateOf("organize") }
    var showWatermarkDialog by rememberSaveable { mutableStateOf(false) }
    var showPageNumbersDialog by rememberSaveable { mutableStateOf(false) }
    var showGreyscaleDialog by rememberSaveable { mutableStateOf(false) }
    var showInvertDialog by rememberSaveable { mutableStateOf(false) }
    var showEditPdf by rememberSaveable { mutableStateOf(false) }
    var showFlattenDialog by rememberSaveable { mutableStateOf(false) }
    var showCropPdfDialog by rememberSaveable { mutableStateOf(false) }
    var showRedactDialog by rememberSaveable { mutableStateOf(false) }
    var showNupDialog by rememberSaveable { mutableStateOf(false) }
    var showRedactBoxesEditor by rememberSaveable { mutableStateOf(false) }
    var showHeaderFooterDialog by rememberSaveable { mutableStateOf(false) }
    val blankPagesFound by viewModel.blankPages.collectAsStateWithLifecycle()
    val pdfInfo by viewModel.pdfInfo.collectAsStateWithLifecycle()
    val fileInfo by viewModel.fileInfo.collectAsStateWithLifecycle()

    var activeCropSourcePath by rememberSaveable { mutableStateOf<String?>(null) }
    var activeCropUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // Where a finished crop goes back to (a dialog, or a picture in the Images to PDF list), as saved state rather than a
    // lambda: a rotation during the crop used to lose the lambda, so the crop could not reach its page (R48).
    var cropReturn by rememberSaveable { mutableStateOf<String?>(null) }
    val cropScope = rememberCoroutineScope()

    /** Hands a cropped picture back to whatever asked for the crop; see [cropReturn]. */
    fun deliverCrop(croppedUri: Uri, target: String) {
        when {
            target == CROP_RETURN_COMPRESS -> { pendingUri = croppedUri; showCompressImageDialog = true }
            target == CROP_RETURN_RESIZE -> { pendingUri = croppedUri; showResizeImageDialog = true }
            target.startsWith(CROP_RETURN_CONVERT) -> {
                val index = target.removePrefix(CROP_RETURN_CONVERT).toIntOrNull()
                val current = pendingImageUris
                if (index != null && current != null && index in current.indices) {
                    pendingImageUris = current.toMutableList().also { it[index] = croppedUri }
                }
                showConvertImageDialog = true
            }
        }
    }

    // Read here, where it follows a language change, not with context.getString inside the launcher.
    val cropFailedMessage = stringResource(R.string.tools_err_crop_img)
    fun launchCropForUri(targetUri: Uri, returnTo: String? = null) {
        cropScope.launch {
            // An unreadable, cloud-only or revoked picture must say so, not crash the app or do nothing.
            val stagedFile = try {
                withContext(Dispatchers.IO) {
                    PdfToolsEngine.copyUriToTemp(context, targetUri, "crop_staged_${UUID.randomUUID()}.jpg")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (stagedFile != null && stagedFile.length() > 0) {
                activeCropUri = targetUri
                cropReturn = returnTo
                activeCropSourcePath = stagedFile.absolutePath
            } else {
                stagedFile?.delete()
                snackbarHostState.showSnackbar(cropFailedMessage)
            }
        }
    }
    // A crop made by the view model for a dialog: hand it over (or report the failure) once, then clear it.
    val precrop by viewModel.precrop.collectAsStateWithLifecycle()
    LaunchedEffect(precrop) {
        val outcome = precrop ?: return@LaunchedEffect
        val cropped = outcome.uri
        if (cropped != null) deliverCrop(cropped, outcome.target) else snackbarHostState.showSnackbar(cropFailedMessage)
        viewModel.consumePrecrop()
    }
    var showPdfToTextDialog by rememberSaveable { mutableStateOf(false) }
    // The PDF to Excel tile opens the same dialog with Excel already chosen.
    var pdfTextFormat by rememberSaveable { mutableStateOf(TextExportFormat.WORD) }
    // The pages kept in Organise when "Insert pages" was chosen, as index and rotation pairs (survives rotation of the phone).
    var insertBase by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    val sourcePassword by viewModel.sourcePassword.collectAsStateWithLifecycle()

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

    var saveSourcePath by rememberSaveable { mutableStateOf<String?>(null) }
    fun saveTo(destUri: Uri?) {
        val file = saveSourcePath?.let { File(it) } ?: return
        if (destUri == null) return
        viewModel.saveTo(file, destUri)
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val saveJpegLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { saveTo(it) }
    val savePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { saveTo(it) }
    val saveWebpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { saveTo(it) }
    val saveTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { saveTo(it) }

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        when (tool) {
            "crop_img" -> launchCropForUri(uri)
            "compress_img" -> showCompressImageDialog = true
            "resize_img" -> showResizeImageDialog = true
        }
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "convert_img") {
            pendingImageUris = uris
            showConvertImageDialog = true
        }
    }
    val saveExcelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { saveTo(it) }
    val saveWordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { saveTo(it) }

    /** "Save" on a result: the system's save dialog for the file's own type, then a copy is written there. */
    fun saveCopy(file: File) {
        saveSourcePath = file.absolutePath
        val name = file.name
        when (mimeFor(file)) {
            "application/zip" -> saveZipLauncher.launch(name)
            "application/pdf" -> saveFileLauncher.launch(name)
            "image/png" -> savePngLauncher.launch(name)
            "image/webp" -> saveWebpLauncher.launch(name)
            "text/plain" -> saveTextLauncher.launch(name)
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> saveWordLauncher.launch(name)
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> saveExcelLauncher.launch(name)
            else -> saveJpegLauncher.launch(name)
        }
    }

    /** Opens a kept result: PDFs in ScanPro's viewer, anything else in an app the user picks. */
    fun openResultFile(file: File) {
        if (file.extension.equals("pdf", ignoreCase = true)) onOpenPdfViewer(Uri.fromFile(file), file.name)
        else openFile(context, file.absolutePath, mimeFor(file), file.name)
    }

    /** Opens [tool]'s options on [uri], a file that opens without a password (or an unlocked copy of one). */
    fun openTool(tool: String, uri: Uri) {
        selectedTool = tool
        pendingUri = uri
        when (tool) {
            "compress" -> showCompressDialog = true
            "pdf2img" -> showPdfToImagesDialog = true
            "protect" -> showPasswordDialog = true
            "unlock" -> viewModel.inspectForUnlock(uri)
            "repair" -> showRepairPage = true
            "split" -> showSplitDialog = true
            "organize", "extract", "rotate" -> { organizerMode = tool; showOrganizerSheet = true }
            "viewer" -> onOpenPdfViewer(uri, viewModel.displayName(uri))
            "fill" -> viewModel.openForm(uri)
            "ocr" -> showOcrDialog = true
            "page_numbers" -> showPageNumbersDialog = true
            "watermark" -> showWatermarkDialog = true
            "crop_img" -> launchCropForUri(uri)
            "compress_img" -> showCompressImageDialog = true
            "convert_img" -> {
                pendingImageUris = listOf(uri)
                showConvertImageDialog = true
            }
            "resize_img" -> showResizeImageDialog = true
            "sign" -> signViewModel.open(uri)
            "pdf2text" -> { pdfTextFormat = TextExportFormat.WORD; showPdfToTextDialog = true }
            "pdf2excel" -> { pdfTextFormat = TextExportFormat.EXCEL; showPdfToTextDialog = true }
            "blank_pages" -> viewModel.findBlankPages(uri)
            "greyscale" -> showGreyscaleDialog = true
            "invert_pdf" -> showInvertDialog = true
            "edit_pdf" -> showEditPdf = true
            "flatten" -> showFlattenDialog = true
            "crop_pdf" -> showCropPdfDialog = true
            "redact" -> showRedactDialog = true
            "redact_box" -> showRedactBoxesEditor = true
            "nup" -> showNupDialog = true
            "header_footer" -> showHeaderFooterDialog = true
            "extract_images" -> showExtractImagesPage = true
            "pdf_info" -> viewModel.openFileInfo(uri)
        }
    }
    fun startTool(tool: String, uri: Uri) {
        // Tools that open the PDF's pages check for an open password first and ask for it, rather than failing later.
        if (tool in OPENS_PAGES) {
            selectedTool = tool
            viewModel.prepareTool(tool, uri)
        } else {
            // A tool that does not read an unlocked copy must not inherit its password.
            viewModel.forgetUnlockedSource()
            openTool(tool, uri)
        }
    }
    val toolReady by viewModel.toolReady.collectAsStateWithLifecycle()
    LaunchedEffect(toolReady) {
        val target = toolReady ?: return@LaunchedEffect
        viewModel.clearToolReady()
        openTool(target.tool, target.uri)
    }
    val officePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) officeUri = uri
    }
    // File info and metadata (F243) takes any file, not only PDFs, and opens it straight away.
    val anyFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openFileInfo(uri)
    }
    val printFallback by viewModel.printFallback.collectAsStateWithLifecycle()
    val activityContext = LocalContext.current
    LaunchedEffect(printFallback) {
        val (html, name) = printFallback ?: return@LaunchedEffect
        viewModel.clearPrintFallback()
        com.example.scanpro.imaging.HtmlToPdf.printWithDialog(activityContext, html, name)
    }
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = selectedTool
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        viewModel.rememberPdfs(listOf(uri))
        startTool(tool, uri)
    }
    var ordering by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var imagesToOrder by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    var imagesToConvert by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>?>(null) }
    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "merge") {
            viewModel.rememberPdfs(uris)
            ordering = uris
        }
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && selectedTool == "img2pdf") {
            imagesToOrder = uris
        }
    }
    // Files on this phone handed files over (F168): the same flow as picking them here.
    val handoff by com.example.scanpro.feature.phonepdfs.PhoneFilesToTools.pending.collectAsStateWithLifecycle()
    LaunchedEffect(handoff) {
        val request = handoff ?: return@LaunchedEffect
        com.example.scanpro.feature.phonepdfs.PhoneFilesToTools.pending.value = null
        selectedTool = request.tool
        when (request.tool) {
            com.example.scanpro.feature.phonepdfs.PhoneFilesToTools.MERGE -> {
                viewModel.rememberPdfs(request.uris)
                ordering = request.uris
            }
            com.example.scanpro.feature.phonepdfs.PhoneFilesToTools.IMAGES_TO_PDF -> imagesToOrder = request.uris
            com.example.scanpro.feature.phonepdfs.PhoneFilesToTools.OFFICE_TO_PDF -> request.uris.firstOrNull()?.let { officeUri = it }
        }
    }
    ordering?.let { picked ->
        MergeEditor(
            initialUris = picked,
            displayName = viewModel::displayName,
            onDismiss = { ordering = null },
            onConfirm = { inOrder ->
                ordering = null
                viewModel.merge(inOrder)
            },
            onPicked = viewModel::rememberPdfs
        )
    }
    imagesToOrder?.let { picked ->
        ImagesToPdfEditor(
            initialUris = picked,
            displayName = viewModel::displayName,
            onDismiss = { imagesToOrder = null },
            onConfirm = { inOrder ->
                imagesToOrder = null
                imagesToConvert = inOrder
            }
        )
    }
    imagesToConvert?.let { images ->
        ImagesToPdfDialog(
            images = images,
            onDismiss = {
                // The ordered copies live in cache; deleting them is file work, so not on the main thread.
                cropScope.launch(Dispatchers.IO) {
                    images.mapNotNull { it.path?.let(::File)?.parentFile }
                        .filter { it.name.startsWith("img2pdf_order_") }
                        .distinct()
                        .forEach { it.deleteRecursively() }
                }
                imagesToConvert = null
            },
            onConfirm = { size, quality, margin ->
                imagesToConvert = null
                viewModel.imagesToPdf(images, size, quality, margin)
            }
        )
    }
    officeUri?.let { picked ->
        OfficeToPdfPage(
            uri = picked,
            onDismiss = { officeUri = null },
            onConfirm = {
                officeUri = null
                viewModel.officeToPdf(picked)
            }
        )
    }

    val openTool: (PdfToolCard) -> Unit = { tool ->
        selectedTool = tool.id
        when (tool.id) {
            "merge" -> multiPdfPicker.launch(arrayOf("application/pdf"))
            "img2pdf" -> imagesPicker.launch("image/*")
            "office" -> officePicker.launch(OFFICE_MIME_TYPES)
            "pdf_info" -> anyFilePicker.launch(arrayOf("*/*"))
            "crop_img", "compress_img", "resize_img" -> singleImagePicker.launch("image/*")
            "convert_img" -> multiImagePicker.launch("image/*")
            else -> singlePdfPicker.launch(arrayOf("application/pdf"))
        }
    }
    // The first time a tile is tapped (F146), its introduction comes before the picker. Marked as seen on every way
    // out, so it never shows twice. Saveable, so turning the phone keeps it up.
    var introToolId by rememberSaveable { mutableStateOf<String?>(null) }
    var imageToolsOpen by rememberSaveable { mutableStateOf(false) }
    val launchTool: (PdfToolCard) -> Unit = { tool ->
        if (tool.id == IMAGE_TOOLS_TILE.id) imageToolsOpen = true
        else if (toolIntro(tool.id) != null && FirstRunHints.isDue(context, FirstRunHints.toolKey(tool.id))) introToolId = tool.id
        else openTool(tool)
    }
    if (imageToolsOpen) {
        ImageToolsChooser(
            onPick = { picked -> imageToolsOpen = false; launchTool(picked) },
            onDismiss = { imageToolsOpen = false }
        )
    }
    introToolId?.let { id ->
        val tool = ALL_TOOLS.firstOrNull { it.id == id }
        val intro = toolIntro(id)
        if (tool == null || intro == null) {
            introToolId = null
        } else {
            ToolIntroDialog(
                tool = tool,
                intro = intro,
                onContinue = {
                    FirstRunHints.markSeen(context, FirstRunHints.toolKey(id))
                    introToolId = null
                    openTool(tool)
                },
                onCancel = {
                    FirstRunHints.markSeen(context, FirstRunHints.toolKey(id))
                    introToolId = null
                }
            )
        }
    }

    PdfToolsScreen(
        recentPdfs = recentPdfs,
        onOpenPdfViewer = { uri, name ->
            recentScope.launch { if (viewModel.checkRecent(uri)) onOpenPdfViewer(uri, name) }
        },
        onLaunchTool = launchTool,
        onClearRecentPdfs = viewModel::clearRecentPdfs,
        onRemoveRecentPdf = { pdf -> viewModel.forgetPdf(pdf.uri) },
        onUseRecentPdfWithTool = { pdf, tool ->
            recentScope.launch { if (viewModel.checkRecent(pdf.uri)) startTool(tool, pdf.uri) }
        },
        toolResults = toolResults,
        onOpenResult = { openResultFile(it.file) },
        onShareResult = { shareFile(context, it.file.absolutePath, mimeFor(it.file), it.name) },
        onSaveResult = { saveCopy(it.file) },
        onUseResultWithTool = { item, tool -> startTool(tool.id, Uri.fromFile(item.file)) },
        onDeleteResult = { viewModel.deleteResult(it.file) },
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        // The organiser and Sign PDF are drawn in this tab rather than in a window of their own, so while one is open
        // the landing's title and the bottom bar make way for it (they showed above and below the editor).
        editorOpen = (showOrganizerSheet && pendingUri != null && selectedTool != null) || signState.visible,
        overlayContent = {
            // Sign PDF fills the screen; the progress and result dialogs below are drawn over it.
            SignPdfScreen(
                viewModel = signViewModel,
                busy = toolRunning,
                onSign = { source, placements, name -> viewModel.signPdf(source, placements, name) }
            )

            if (toolRunning) ToolProgressHost(viewModel.progress, onCancel = viewModel::cancel)

            activeCropSourcePath?.let { stagedPath ->
                // A straight crop with aspect ratios, turn and mirror (F193), in the picture's own format.
                ImageCropEditor(
                    sourcePath = stagedPath,
                    onDismiss = {
                        File(stagedPath).delete()
                        activeCropSourcePath = null
                        activeCropUri = null
                        cropReturn = null
                    },
                    onConfirm = { spec ->
                        val originalUri = activeCropUri
                        val target = cropReturn
                        activeCropSourcePath = null
                        activeCropUri = null
                        cropReturn = null
                        if (originalUri != null) {
                            if (target != null) {
                                // In the view model, which survives a screen turn; the result comes back through
                                // [precrop] below. A crop that fails says so instead of ending the app.
                                viewModel.precropImage(stagedPath, spec, target)
                            } else {
                                File(stagedPath).delete()
                                viewModel.cropImageRect(originalUri, spec)
                            }
                        } else {
                            File(stagedPath).delete()
                        }
                    }
                )
            }

            failure?.let { reason ->
                val failedTool = selectedTool
                val failedUri = pendingUri
                val card = ALL_TOOLS.firstOrNull { it.id == failedTool }
                ToolFailureDialog(
                    message = reason,
                    onRetry = if (failedTool in RETRYABLE_TOOLS && failedUri != null) {
                        { viewModel.clearFailure(); startTool(failedTool!!, failedUri) }
                    } else null,
                    onChooseAnother = if (card != null && (failedTool in RETRYABLE_TOOLS || failedTool in MULTI_FILE_TOOLS)) {
                        { viewModel.clearFailure(); launchTool(card) }
                    } else null,
                    // A PDF that can't be read gets one more chance: Repair rewrites it with the forgiving parser and,
                    // failing that, keeps the pages the system renderer can still draw (owner's clean-up: no Repair tile).
                    onRepair = if (failedTool in RETRYABLE_TOOLS && failedTool !in IMAGE_INPUT_TOOLS && failedTool != "repair" && failedUri != null) {
                        { viewModel.clearFailure(); selectedTool = "repair"; viewModel.repair(failedUri) }
                    } else null,
                    onDismiss = viewModel::clearFailure
                )
            }

            lastResult?.let { result ->
                ResultDialog(
                    result = result,
                    onDismiss = viewModel::clearResult,
                    onView = {
                        val file = result.outputFile
                        viewModel.clearResult()
                        if (file.extension.equals("pdf", ignoreCase = true)) onOpenPdfViewer(Uri.fromFile(file), file.name)
                        else openFile(context, file.absolutePath, mimeFor(file), file.name)
                    },
                    // A protected result would only ask for its password again, so nothing is offered after Protect.
                    nextTools = if (selectedTool == "protect") emptyList() else nextToolsFor(result.outputFile.extension, selectedTool),
                    onContinue = { next ->
                        val file = result.outputFile
                        viewModel.clearResult()
                        startTool(next.id, Uri.fromFile(file))
                    },
                    onSaveAs = { saveCopy(result.outputFile) }
                )
            }

            val uri = pendingUri
            if (showCompressDialog && uri != null) {
                CompressPdfDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showCompressDialog = false },
                    onConfirm = { preset, customTargetBytes ->
                        showCompressDialog = false
                        viewModel.compress(uri, preset, customTargetBytes)
                    }
                )
            }

            if (showWatermarkDialog && uri != null) {
                WatermarkEditor(
                    uri = uri,
                    onDismiss = { showWatermarkDialog = false },
                    onConfirm = { config ->
                        showWatermarkDialog = false
                        viewModel.watermarkPdf(uri, config)
                    }
                )
            }

            if (showPageNumbersDialog && uri != null) {
                PageNumbersPdfDialog(
                    uri = uri,
                    onDismiss = { showPageNumbersDialog = false },
                    onConfirm = { config ->
                        showPageNumbersDialog = false
                        viewModel.addPageNumbers(uri, config)
                    }
                )
            }

            if (showCompressImageDialog && uri != null) {
                CompressImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showCompressImageDialog = false },
                    onCrop = {
                        showCompressImageDialog = false
                        launchCropForUri(uri, CROP_RETURN_COMPRESS)
                    },
                    onConfirm = { config ->
                        showCompressImageDialog = false
                        viewModel.compressImage(uri, config)
                    }
                )
            }

            pendingImageUris?.let { uris ->
                if (showConvertImageDialog && uris.isNotEmpty()) {
                    ConvertImageDialog(
                        uris = uris,
                        onDismiss = {
                            showConvertImageDialog = false
                            pendingImageUris = null
                        },
                        onCropImage = { index ->
                            val targetUri = uris[index]
                            showConvertImageDialog = false
                            launchCropForUri(targetUri, CROP_RETURN_CONVERT + index)
                        },
                        onConfirm = { format, quality ->
                            showConvertImageDialog = false
                            val currentUris = pendingImageUris ?: uris
                            pendingImageUris = null
                            viewModel.convertImage(currentUris, format, quality)
                        },
                        onConvertToPdf = {
                            showConvertImageDialog = false
                            val currentUris = pendingImageUris ?: uris
                            pendingImageUris = null
                            viewModel.imagesToPdf(currentUris, PdfPageSize.AUTO, PdfQuality.HIGH)
                        }
                    )
                }
            }

            if (showResizeImageDialog && uri != null) {
                ResizeImageDialog(
                    context = context,
                    uri = uri,
                    onDismiss = { showResizeImageDialog = false },
                    onCrop = {
                        showResizeImageDialog = false
                        launchCropForUri(uri, CROP_RETURN_RESIZE)
                    },
                    onConfirm = { config ->
                        showResizeImageDialog = false
                        viewModel.resizeImage(uri, config)
                    }
                )
            }

            if (showExtractImagesPage && uri != null) {
                ExtractImagesPage(
                    uri = uri,
                    onDismiss = { showExtractImagesPage = false },
                    onConfirm = { showExtractImagesPage = false; viewModel.extractImages(uri) },
                    onConfirmChosen = { dir, chosen -> showExtractImagesPage = false; viewModel.saveChosenImages(uri, dir, chosen) }
                )
            }

            if (showRepairPage && uri != null) {
                RepairPage(
                    uri = uri,
                    onDismiss = { showRepairPage = false },
                    onConfirm = { showRepairPage = false; viewModel.repair(uri) }
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
                    fileUri = uri,
                    initialError = if (!isProtect && wrongPassword) stringResource(R.string.tools_wrong_password_try_again) else null,
                    // The same file, now in Unlock: once unlocked, the copy can be protected with a new password.
                    onUnlockInstead = if (isProtect) ({ selectedTool = "unlock" }) else null,
                    onDismiss = { showPasswordDialog = false; viewModel.clearWrongPassword() },
                    onConfirm = { pwd ->
                        showPasswordDialog = false
                        viewModel.clearWrongPassword()
                        if (isProtect) viewModel.protect(uri, pwd) else viewModel.unlock(uri, pwd)
                    }
                )
            }

            mergePassword?.let { request ->
                key(request.attempt) {
                    PasswordDialog(
                        isProtect = false,
                        mergeFileName = request.fileName,
                        initialError = if (request.wrongPassword) stringResource(R.string.tools_wrong_password_try_again) else null,
                        onDismiss = viewModel::cancelMergePassword,
                        onConfirm = viewModel::submitMergePassword
                    )
                }
            }

            // PDF to text or Insert pages reached a protected file. Keyed by the question, so a retry starts with an empty field.
            sourcePassword?.let { request ->
                key(request.attempt) {
                    SourcePasswordDialog(
                        request = request,
                        onDismiss = viewModel::cancelSourcePassword,
                        onConfirm = viewModel::submitSourcePassword
                    )
                }
            }

            if (showPdfToTextDialog && uri != null) {
                PdfToTextDialog(
                    uri = uri,
                    initialFormat = pdfTextFormat,
                    onDismiss = { showPdfToTextDialog = false },
                    onConfirm = { format, script ->
                        showPdfToTextDialog = false
                        viewModel.pdfToText(uri, format, script)
                    }
                )
            }

            if (showOcrDialog && uri != null) {
                OcrScriptDialog(
                    uri = uri,
                    onDismiss = { showOcrDialog = false },
                    onConfirm = { script ->
                        showOcrDialog = false
                        viewModel.makeSearchable(uri, script)
                    }
                )
            }

            formWithoutFields?.let { uri ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissFormWithoutFields,
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    title = { Text(stringResource(R.string.tools_fill_no_fields_title)) },
                    text = { Text(stringResource(R.string.tools_fill_no_fields_body)) },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.dismissFormWithoutFields()
                            selectedTool = "sign"
                            signViewModel.open(uri)
                        }) { Text(stringResource(R.string.tools_fill_no_fields_action)) }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissFormWithoutFields) { Text(stringResource(R.string.common_close)) }
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

            blankPagesFound?.let { found ->
                BlankPagesDialog(found = found, onDismiss = viewModel::clearBlankPages, onRemove = { ticked -> viewModel.removeBlankPages(found.copy(blankPages = ticked)) })
            }

            if (showGreyscaleDialog && uri != null) {
                RecolourPdfPage(
                    uri = uri,
                    invert = false,
                    onDismiss = { showGreyscaleDialog = false },
                    onConfirm = { showGreyscaleDialog = false; viewModel.greyscale(uri) }
                )
            }

            if (showRedactBoxesEditor && uri != null) {
                RedactBoxesEditor(
                    uri = uri,
                    onDismiss = { showRedactBoxesEditor = false },
                    onConfirm = { boxes -> showRedactBoxesEditor = false; viewModel.redactBoxes(uri, boxes) }
                )
            }

            if (showNupDialog && uri != null) {
                NupDialog(
                    uri = uri,
                    onDismiss = { showNupDialog = false },
                    onConfirm = { perSheet -> showNupDialog = false; viewModel.nup(uri, perSheet) }
                )
            }

            if (showHeaderFooterDialog && uri != null) {
                HeaderFooterDialog(
                    uri = uri,
                    onDismiss = { showHeaderFooterDialog = false },
                    onConfirm = { config -> showHeaderFooterDialog = false; viewModel.headerFooter(uri, config) }
                )
            }

            if (showCropPdfDialog && uri != null) {
                CropPdfDialog(
                    uri = uri,
                    onDismiss = { showCropPdfDialog = false },
                    onConfirm = { options -> showCropPdfDialog = false; viewModel.cropPdf(uri, options) },
                    onConfirmPlan = { plan -> showCropPdfDialog = false; viewModel.cropPdfPlan(uri, plan) }
                )
            }

            if (showRedactDialog && uri != null) {
                RedactDialog(
                    uri = uri,
                    onDismiss = { showRedactDialog = false },
                    onConfirm = { terms, matchCase -> showRedactDialog = false; viewModel.redact(uri, terms, matchCase) }
                )
            }

            if (showEditPdf && uri != null) {
                EditPdfTextEditor(
                    uri = uri,
                    suggestedName = remember(uri) { viewModel.displayName(uri) + " edited" },
                    onDismiss = { showEditPdf = false },
                    onSave = { edits, name -> showEditPdf = false; viewModel.editPdfText(uri, edits, name) }
                )
            }

            if (showInvertDialog && uri != null) {
                RecolourPdfPage(
                    uri = uri,
                    invert = true,
                    onDismiss = { showInvertDialog = false },
                    onConfirm = { showInvertDialog = false; viewModel.invertColors(uri) }
                )
            }

            if (showFlattenDialog && uri != null) {
                FlattenPage(
                    uri = uri,
                    onDismiss = { showFlattenDialog = false },
                    onConfirm = { showFlattenDialog = false; viewModel.flatten(uri) }
                )
            }

            fileInfo?.let { session ->
                // Keyed by the file, so pending edits never carry over to the next file.
                key(session.uri) {
                    FileInfoScreen(
                        session = session,
                        onDismiss = viewModel::closeFileInfo,
                        onSave = { edit -> viewModel.saveFileInfo(session, edit) },
                        onExport = { json -> viewModel.exportFileInfo(session, json) }
                    )
                }
            }

            pdfInfo?.let { session ->
                // Keyed by the file, so the fields start from its details and not from the last PDF's edits.
                key(session.uri) {
                    PdfInfoDialog(
                        info = session.info,
                        uri = session.uri,
                        onDismiss = viewModel::closePdfInfo,
                        onSave = { edit -> viewModel.saveMetadata(session.uri, edit, removeAll = false) },
                        onRemoveAll = { viewModel.saveMetadata(session.uri, null, removeAll = true) }
                    )
                }
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
                    mode = organizerMode,
                    onModeChange = { organizerMode = it },
                    onClose = { showOrganizerSheet = false },
                    onApply = { pageSpecs, extractedIndices, rotationsMap ->
                        showOrganizerSheet = false
                        viewModel.organize(organizerMode, uri, pageSpecs, extractedIndices, rotationsMap)
                    },
                    onInsertPages = { kept -> insertBase = kept.flatMap { listOf(it.originalIndex, it.rotationDegrees) } }
                )
            }

            insertBase?.let { flat ->
                InsertPagesDialog(
                    baseUri = uri,
                    keptPages = flat.size / 2,
                    nameOf = viewModel::displayName,
                    onDismiss = { insertBase = null },
                    onConfirm = { insertion ->
                        insertBase = null
                        showOrganizerSheet = false
                        if (uri != null) viewModel.insertPages(uri, flat.chunked(2).map { (index, turn) -> PageSpec(index, turn) }, insertion)
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    recentPdfs: List<RecentPdf>,
    onOpenPdfViewer: (Uri, String) -> Unit = { _, _ -> },
    onLaunchTool: (PdfToolCard) -> Unit = {},
    onClearRecentPdfs: () -> Unit = {},
    onRemoveRecentPdf: (RecentPdf) -> Unit = {},
    onUseRecentPdfWithTool: (RecentPdf, String) -> Unit = { _, _ -> },
    toolResults: List<ToolResultFile> = emptyList(),
    onOpenResult: (ToolResultFile) -> Unit = {},
    onShareResult: (ToolResultFile) -> Unit = {},
    onSaveResult: (ToolResultFile) -> Unit = {},
    onUseResultWithTool: (ToolResultFile, PdfToolCard) -> Unit = { _, _ -> },
    onDeleteResult: (ToolResultFile) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** A full-screen editor drawn by [overlayContent] owns the screen: no title bar or bottom bar around it. */
    editorOpen: Boolean = false,
    overlayContent: @Composable () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    val toolsMemory = rememberToolsMemory()
    var showAllResults by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // F140: Home's "See all" for opened PDFs or tool results opens this tab at that list. The request waits until the list
    // it points at has loaded, then is taken once.
    val focus by ToolsFocusRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(focus, toolResults.isNotEmpty(), recentPdfs.isNotEmpty()) {
        val target = focus ?: return@LaunchedEffect
        val ready = if (target == ToolsFocus.RESULTS) toolResults.isNotEmpty() else recentPdfs.isNotEmpty()
        if (!ready) return@LaunchedEffect
        ToolsFocusRequest.pending.value = null
        query = ""
        if (target == ToolsFocus.RESULTS) showAllResults = true
        listState.scrollToKey(target.key)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!editorOpen) {
                TopAppBar(
                    title = { Text(stringResource(R.string.tools_screen_title)) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = { if (!editorOpen) bottomBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            // Tools redesign (2026-11-16): three compact tiles to a row on a phone (six on a wide window), so a section
            // fits on one screen instead of three. Two wide cards with their descriptions remain for large text, where
            // three columns would cut the names.
            val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            val toolColumns = when {
                com.example.scanpro.navigation.isWideWindow() -> if (largeText) 4 else 6
                largeText -> 2
                else -> 3
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 1100.dp)
            ) {
                item(key = "search") { ToolSearchField(query, onQueryChange = { query = it }, modifier = Modifier.padding(top = 4.dp)) }
                // Narrowing by category only makes sense while not searching: a search looks at every tool.
                if (query.isBlank()) {
                    item(key = "categories") { Box(Modifier.padding(top = 12.dp)) { ToolCategoryChips(category, onSelect = { category = it }) } }
                }
                toolsLanding(query, category, toolsMemory, perRow = toolColumns, onLaunch = onLaunchTool)
                // Everything the tools made, kept until deleted (iLovePDF's "Processed files"); hidden while searching tools.
                if (toolResults.isNotEmpty() && query.isBlank()) {
                    item(key = "results-header") {
                        val more = toolResults.size > RESULTS_SHOWN
                        FileSectionHeader(
                            title = stringResource(R.string.tools_results_title),
                            action = when {
                                !more -> null
                                showAllResults -> stringResource(R.string.tools_results_show_less)
                                else -> stringResource(R.string.tools_results_show_all, toolResults.size)
                            },
                            onAction = { showAllResults = !showAllResults }
                        )
                    }
                    val shown = if (showAllResults) toolResults else toolResults.take(RESULTS_SHOWN)
                    items(shown, key = { "result:" + it.file.path }, contentType = { "tool_result" }) { item ->
                        // A new result fades in at the top and a deleted one fades out while the rest glide up (F146).
                        Box(Modifier.animateItem()) {
                            ToolResultRow(
                                item = item,
                                onOpen = { onOpenResult(item) },
                                onShare = { onShareResult(item) },
                                onSave = { onSaveResult(item) },
                                onUseWith = { tool -> onUseResultWithTool(item, tool) },
                                onDelete = { onDeleteResult(item) }
                            )
                        }
                    }
                }
                if (recentPdfs.isNotEmpty()) {
                    item(key = "recent-header") {
                        RecentPdfsHeader(onClear = onClearRecentPdfs)
                    }
                    items(recentPdfs, key = { it.uri.toString() }, contentType = { "recent_pdf" }) { pdf ->
                        Box(Modifier.animateItem()) {
                            RecentPdfRow(
                                pdf = pdf,
                                onOpen = { onOpenPdfViewer(pdf.uri, pdf.name) },
                                onUseWith = { tool -> onUseRecentPdfWithTool(pdf, tool) },
                                onRemove = { onRemoveRecentPdf(pdf) }
                            )
                        }
                    }
                }
            }
            overlayContent()
        }
    }
}

/** How many results the Tools screen lists before "Show all". */
private const val RESULTS_SHOWN = 5

/** Tools that work on one picked file, so a failed run can be tried again with the same file. */
private val RETRYABLE_TOOLS = setOf(
    "compress", "pdf2img", "protect", "unlock", "repair", "split", "organize", "extract", "rotate", "ocr", "page_numbers",
    "watermark", "crop_img", "compress_img", "resize_img", "pdf2text", "pdf2excel",
    "blank_pages", "greyscale", "invert_pdf", "edit_pdf", "flatten", "extract_images", "pdf_info", "crop_pdf", "redact", "redact_box", "nup", "header_footer"
)

/** Tools whose input is a picture, not a PDF: their failures are never offered Repair. */
private val IMAGE_INPUT_TOOLS = setOf("crop_img", "compress_img", "resize_img", "convert_img")

/** Tools that start from several files or from a document that is not a PDF: after a failure they can only be picked again. */
private val MULTI_FILE_TOOLS = setOf("merge", "img2pdf", "office", "convert_img")

/**
 * Tools that open one PDF's pages and so need it unlocked: a protected file is asked about once and an unlocked working
 * copy is used. Sign and PDF to Word/Excel/text ask for themselves; Unlock, Protect and Repair deal with protection.
 */
private val OPENS_PAGES = setOf(
    "compress", "pdf2img", "split", "organize", "extract", "rotate", "fill", "ocr", "page_numbers", "watermark",
    "blank_pages", "greyscale", "invert_pdf", "edit_pdf", "flatten", "extract_images", "pdf_info", "crop_pdf", "redact", "redact_box", "nup", "header_footer"
)

internal fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "zip" -> "application/zip"
    "txt" -> "text/plain"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "csv" -> "text/csv"
    // Any other file a tool hands back (File info saves copies of every kind): its own type, never "PDF" by default.
    else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
}

/** Keeps the files picked for Merge or Images to PDF through a rotation (null when nothing is picked). */
private val UriListSaver = androidx.compose.runtime.saveable.Saver<List<Uri>?, java.util.ArrayList<String>>(
    save = { list -> list?.let { java.util.ArrayList(it.map(Uri::toString)) } },
    restore = { saved -> saved.map(Uri::parse) }
)

@Preview(name = "PDF Tools Screen Light")
@Composable
fun PdfToolsScreenPreview() {
    ScanProTheme(themeMode = ThemeMode.LIGHT) {
        PdfToolsScreen(
            recentPdfs = listOf(
                RecentPdf(Uri.parse("content://media/external/file/1"), "Invoice_2025_03.pdf", System.currentTimeMillis()),
                RecentPdf(Uri.parse("content://media/external/file/2"), "Project_Specification_Draft.pdf", System.currentTimeMillis() - 86400000L)
            )
        )
    }
}

@Preview(name = "PDF Tools Screen Dark")
@Composable
fun PdfToolsScreenDarkPreview() {
    ScanProTheme(themeMode = ThemeMode.DARK) {
        PdfToolsScreen(
            recentPdfs = listOf(
                RecentPdf(Uri.parse("content://media/external/file/1"), "Invoice_2025_03.pdf", System.currentTimeMillis()),
                RecentPdf(Uri.parse("content://media/external/file/2"), "Project_Specification_Draft.pdf", System.currentTimeMillis() - 86400000L)
            )
        )
    }
}

/** Values of the saved crop target in [PdfToolsScreen]: which dialog, or which Images to PDF picture, gets the crop. */
private const val CROP_RETURN_COMPRESS = "compress"
private const val CROP_RETURN_RESIZE = "resize"
private const val CROP_RETURN_CONVERT = "convert:"

/** Staged Images to PDF folders older than this (six hours) are swept; newer ones may still be in use. */
private const val STALE_STAGING_MS = 6L * 60 * 60 * 1000
