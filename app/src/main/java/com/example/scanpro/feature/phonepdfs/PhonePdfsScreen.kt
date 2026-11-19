package com.example.scanpro.feature.phonepdfs

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the screens around "Files on this phone" do with a file; all of them start from a user's tap. */
class PhoneFileActions(
    val onOpenPdf: (Uri, String) -> Unit,
    /** Copies a PDF into ScanPro's library, where every document tool works on it. */
    val onAddToScanPro: (Uri) -> Unit,
    /** Switches to the Tools tab, which takes the request left in [PhoneFilesToTools]. */
    val onOpenTools: () -> Unit
)

/** Asks for the access the right way for this Android version, and reports a page that would not open. */
@Composable
fun rememberPhoneFilesAccessRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val failedMessage = stringResource(R.string.phone_pdfs_settings_failed)
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return {
        val activity = context as? Activity
        // Refused with "Don't ask again" (or by policy): only app settings can turn it on now.
        val blocked = askedOnce && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
        val opened = when {
            PhonePdfSource.usesSettingsPage -> PhonePdfSource.openAllFilesAccessSettings(context)
            blocked -> PhonePdfSource.openAppDetails(context)
            else -> {
                askedOnce = true
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                true
            }
        }
        if (!opened) Toast.makeText(context, failedMessage, Toast.LENGTH_LONG).show()
    }
}

/** The full-screen list (Home's "See all" and the Files menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonePdfsRoute(onBack: () -> Unit, actions: PhoneFileActions, viewModel: PhonePdfsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.phone_pdfs_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.hasAccess == true) IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.phone_pdfs_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            PhoneFilesPane(actions = actions, onNotNow = onBack, viewModel = viewModel)
        }
    }
}

/** The list with its explainer: the Files tab's "On this phone" and the full screen. */
@Composable
fun PhoneFilesPane(
    actions: PhoneFileActions,
    onNotNow: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** False when the screen around the pane shows [PhoneFilesSearchField] itself (Files' top bar). */
    showSearch: Boolean = true,
    viewModel: PhonePdfsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from the system settings page (or the permission dialog): look again.
    LifecycleResumeEffect(Unit) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    val requestAccess = rememberPhoneFilesAccessRequest(onResult = viewModel::checkAccess)
    val shareTitle = stringResource(R.string.phone_pdfs_share)
    val openWithTitle = stringResource(R.string.phone_files_open_with)
    val noPickerApp = stringResource(R.string.phone_files_no_app)
    var previewing by remember { mutableStateOf<PhoneFile?>(null) }
    val processed by viewModel.processed.collectAsStateWithLifecycle()
    // A file from Google Drive, Dropbox, OneDrive or any other storage app, through the phone's own picker.
    val cloudPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPicked(context, uri, actions, openWithTitle)
    }
    val openProcessed = {
        com.example.scanpro.feature.tools.ToolsFocusRequest.pending.value = com.example.scanpro.feature.tools.ToolsFocus.RESULTS
        actions.onOpenTools()
    }
    val openCloud = {
        runCatching { cloudPicker.launch(PhoneFiles.MIME_TYPES.values.flatten().distinct().toTypedArray()) }
            .onFailure { Toast.makeText(context, noPickerApp, Toast.LENGTH_SHORT).show() }
        Unit
    }
    // Back steps out of the open folder before it leaves the screen.
    androidx.activity.compose.BackHandler(enabled = state.hasAccess == true && state.folderPath != null, onBack = viewModel::folderUp)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (state.hasAccess) {
            null -> Unit
            // A build without All files access (see PhonePdfSource.available) offers what needs no permission.
            false -> if (PhonePdfSource.available) AccessExplainer(onAllow = requestAccess, onNotNow = onNotNow)
            else Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).padding(horizontal = 16.dp)) {
                StorageOverviewWithoutAccess(processed.size, openProcessed, openCloud)
            }
            true -> PhoneFilesList(
                state = state,
                showSearch = showSearch,
                onRefresh = viewModel::refresh,
                onQueryChange = viewModel::setQuery,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onToggleSelection = viewModel::toggleSelection,
                onClearSelection = viewModel::clearSelection,
                onOpen = { file ->
                    when (file.kind) {
                        PhoneFileKind.PDF -> actions.onOpenPdf(file.uri, file.name)
                        PhoneFileKind.IMAGE -> previewing = file
                        else -> PhonePdfSource.shareableUri(context, file) { uri -> if (uri != null) openWith(context, uri, file.mimeType, openWithTitle) }
                    }
                },
                onFileAction = { file, action ->
                    when (action) {
                        FileAction.OPEN_WITH -> PhonePdfSource.shareableUri(context, file) { uri -> if (uri != null) openWith(context, uri, file.mimeType, openWithTitle) }
                        FileAction.ADD -> PhonePdfSource.shareableUri(context, file) { uri -> if (uri != null) actions.onAddToScanPro(uri) }
                        FileAction.SHARE -> PhonePdfSource.shareableUri(context, file) { uri -> if (uri != null) shareUris(context, listOf(uri), file.mimeType, shareTitle) }
                        FileAction.CONVERT -> handOff(context, listOf(file), if (file.kind == PhoneFileKind.IMAGE) PhoneFilesToTools.IMAGES_TO_PDF else PhoneFilesToTools.OFFICE_TO_PDF, actions)
                        FileAction.OPEN -> if (file.kind == PhoneFileKind.PDF) actions.onOpenPdf(file.uri, file.name) else previewing = file
                    }
                },
                onOpenFolder = viewModel::openFolder,
                onGridChange = viewModel::setGrid,
                processedCount = processed.size,
                onOpenProcessed = openProcessed,
                onOpenCloud = openCloud,
                onSelectionAction = { action ->
                    val files = state.selection
                    when (action) {
                        SelectionAction.MERGE -> handOff(context, files, PhoneFilesToTools.MERGE, actions)
                        SelectionAction.IMAGES_TO_PDF -> handOff(context, files, PhoneFilesToTools.IMAGES_TO_PDF, actions)
                        SelectionAction.SHARE -> PhonePdfSource.shareableUris(context, files) { uris -> shareUris(context, uris, commonMimeType(files), shareTitle) }
                    }
                    viewModel.clearSelection()
                }
            )
        }
    }
    previewing?.let { image ->
        ImagePreviewDialog(
            file = image,
            onDismiss = { previewing = null },
            onConvert = {
                previewing = null
                handOff(context, listOf(image), PhoneFilesToTools.IMAGES_TO_PDF, actions)
            },
            onShare = { PhonePdfSource.shareableUri(context, image) { uri -> if (uri != null) shareUris(context, listOf(uri), image.mimeType, shareTitle) } }
        )
    }
}

/**
 * A file picked from a cloud drive or another app: a PDF opens in the viewer, a picture goes to Images to PDF and an
 * Office file to Office to PDF (as from the list), anything else opens in an app the user chooses.
 */
private fun openPicked(context: android.content.Context, uri: Uri, actions: PhoneFileActions, openWithTitle: String) {
    val resolver = context.contentResolver
    // Kept, so the file still opens from Recent after the app restarts (the picker's grant would end with it).
    runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
    fun toTools(tool: String) {
        PhoneFilesToTools.pending.value = PhoneFilesToTools.Request(tool, listOf(uri))
        actions.onOpenTools()
    }
    when (PhoneFiles.kindOf(mime, name)) {
        PhoneFileKind.PDF -> actions.onOpenPdf(uri, name)
        PhoneFileKind.IMAGE -> toTools(PhoneFilesToTools.IMAGES_TO_PDF)
        PhoneFileKind.WORD, PhoneFileKind.EXCEL, PhoneFileKind.POWERPOINT -> toTools(PhoneFilesToTools.OFFICE_TO_PDF)
        else -> openWith(context, uri, mime.ifBlank { PhoneFiles.mimeForName(name) }, openWithTitle)
    }
}

private fun handOff(context: android.content.Context, files: List<PhoneFile>, tool: String, actions: PhoneFileActions) {
    PhonePdfSource.shareableUris(context, files) { uris ->
        if (uris.isNotEmpty()) {
            PhoneFilesToTools.pending.value = PhoneFilesToTools.Request(tool, uris)
            actions.onOpenTools()
        }
    }
}

internal enum class FileAction { OPEN, OPEN_WITH, CONVERT, ADD, SHARE }
internal enum class SelectionAction { MERGE, IMAGES_TO_PDF, SHARE }

/** Why the access is needed, that nothing leaves the phone and that it's optional: always shown before the system page. */
@Composable
internal fun AccessExplainer(onAllow: () -> Unit, onNotNow: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FolderWithPdfIllustration(Modifier.size(width = 168.dp, height = 128.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.phone_pdfs_explainer_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        ExplainerPoint(Icons.Rounded.TravelExplore, TileTone.Indigo, stringResource(R.string.phone_pdfs_explainer_why))
        ExplainerPoint(Icons.Rounded.Lock, TileTone.Teal, stringResource(R.string.phone_pdfs_explainer_private))
        ExplainerPoint(Icons.Rounded.VerifiedUser, TileTone.Amber, stringResource(R.string.phone_pdfs_explainer_optional))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (PhonePdfSource.usesSettingsPage) R.string.phone_pdfs_explainer_steps else R.string.phone_pdfs_explainer_steps_dialog),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        GradientButton(text = stringResource(R.string.phone_pdfs_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth())
        if (onNotNow != null) TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.phone_pdfs_not_now)) }
    }
}

@Composable
private fun ExplainerPoint(icon: ImageVector, tone: TileTone, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * The On this phone search as a compact pill for Files' top bar, beside the title (owner's sketch, 2026-11-19), so it
 * no longer takes a row of its own above the list. It shares [PhoneFilesPane]'s view model. Nothing is drawn until
 * the files can be read, or while files are selected (the pane shows its selection bar then).
 */
@Composable
fun PhoneFilesSearchField(modifier: Modifier = Modifier, viewModel: PhonePdfsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.hasAccess != true || state.selected.isNotEmpty()) return
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    androidx.compose.foundation.text.BasicTextField(
        value = state.query,
        onValueChange = viewModel::setQuery,
        singleLine = true,
        textStyle = style,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(44.dp),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (state.query.isEmpty()) {
                        Text(stringResource(R.string.phone_pdfs_search_hint), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.phone_pdfs_clear_search), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    )
}

@Composable
internal fun PhoneFilesList(
    state: PhoneFilesState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (PhoneFileFilter) -> Unit,
    onSortChange: (PhoneFileSort) -> Unit,
    onToggleSelection: (PhoneFile) -> Unit,
    onClearSelection: () -> Unit,
    onOpen: (PhoneFile) -> Unit,
    onFileAction: (PhoneFile, FileAction) -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onOpenFolder: (String?) -> Unit = {},
    onGridChange: (Boolean) -> Unit = {},
    /** How many files the tools have made, for the Made in ScanPro entry; null hides the storage overview. */
    processedCount: Int? = null,
    onOpenProcessed: () -> Unit = {},
    onOpenCloud: () -> Unit = {},
    showSearch: Boolean = true
) {
    val shown = state.shown
    val selecting = state.selected.isNotEmpty()
    // The storage overview (iLovePDF's "My storage" and "Cloud storage") sits above the plain list of every file.
    val overview = processedCount != null && !selecting && state.query.isBlank() && state.folderPath == null && state.filter == PhoneFileFilter.ALL
    Column(Modifier.fillMaxSize().widthIn(max = 900.dp)) {
        if (selecting) {
            SelectionBar(state.selection, onClear = onClearSelection, onAction = onSelectionAction)
        } else if (showSearch) {
            // The same filled pill as the Settings search (2026-11-19), so the two tabs read as one app.
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.phone_pdfs_clear_search))
                    }
                },
                placeholder = { Text(stringResource(R.string.phone_pdfs_search_hint)) },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.folderPath != null,
                    onClick = { onOpenFolder(if (state.folderPath == null) "" else null) },
                    label = { Text(stringResource(R.string.phone_files_folders)) },
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            items(PhoneFileFilter.entries) { filter ->
                FilterChip(selected = state.filter == filter, onClick = { onFilterChange(filter) }, label = { Text(stringResource(filterLabel(filter))) })
            }
        }
        // The count, the sort order and the list/grid switch share one line. Above the storage tiles that line sits
        // with the "All files" heading, where the list starts (owner, 2026-11-19: too much space above the tiles).
        val controls: @Composable (String?) -> Unit = { heading ->
            ListControls(
                heading = heading,
                count = shown?.let { pluralStringResource(R.plurals.phone_files_count, it.size, it.size) },
                sort = state.sort, ascending = state.ascending, grid = state.grid,
                onSortChange = onSortChange, onGridChange = onGridChange
            )
        }
        if (!overview) controls(null)
        if (state.browsingFolders) FolderPathBar(path = state.folderPath.orEmpty(), onOpen = onOpenFolder)
        PullToRefreshBox(isRefreshing = state.loading && shown != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                shown == null -> Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FileLoadingAnimation(size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.phone_pdfs_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = if (state.grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val wide: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
                    if (overview && processedCount != null) {
                        item(key = "storage", span = wide, contentType = "storage") {
                            StorageOverview(
                                all = state.all.orEmpty(),
                                processedCount = processedCount,
                                onOpenFolder = onOpenFolder,
                                onOpenProcessed = onOpenProcessed,
                                onOpenCloud = onOpenCloud,
                                controls = { controls(stringResource(R.string.phone_files_all_files)) }
                            )
                        }
                    }
                    items(state.folders, key = { "folder:" + it.path }, span = { GridItemSpan(maxLineSpan) }, contentType = { "folder" }) { folder ->
                        FolderRow(folder = folder, onClick = { onOpenFolder(folder.path) })
                    }
                    if (shown.isEmpty() && state.folders.isEmpty()) item(span = wide) {
                        Text(
                            if (state.query.isBlank()) stringResource(R.string.phone_pdfs_empty)
                            else stringResource(R.string.phone_pdfs_no_match, state.query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                    items(shown, key = { it.uri.toString() }, contentType = { if (state.grid) "phone_file_cell" else "phone_file" }) { file ->
                        val isSelected = file.uri.toString() in state.selected
                        val click = { if (selecting) onToggleSelection(file) else onOpen(file) }
                        if (state.grid) {
                            PhoneFileCell(file = file, selected = isSelected, onClick = click, onLongClick = { onToggleSelection(file) })
                        } else {
                            PhoneFileRow(
                                file = file,
                                selected = isSelected,
                                selecting = selecting,
                                onClick = click,
                                onLongClick = { onToggleSelection(file) },
                                onAction = { onFileAction(file, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Above the list of every file: where files are (the whole storage, Downloads, the tools' results) and a way to open
 * one from a cloud drive. The cloud entry is the phone's own file picker, which lists Google Drive, Dropbox, OneDrive
 * and any other storage app installed; ScanPro signs in to nothing and reaches no network itself.
 */
@Composable
private fun StorageOverview(
    all: List<PhoneFile>,
    processedCount: Int,
    onOpenFolder: (String?) -> Unit,
    onOpenProcessed: () -> Unit,
    onOpenCloud: () -> Unit,
    controls: @Composable () -> Unit
) {
    val downloads = remember(all) { PhoneFiles.downloadsFolder(all) }
    val downloadsCount = remember(all, downloads) { downloads?.let { PhoneFiles.countUnder(all, it) } ?: 0 }
    Column(Modifier.fillMaxWidth()) {
        // Side by side, and small (owner, 2026-11-19): the files are what the tab is for.
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageTile(Icons.Rounded.PhoneAndroid, TileTone.Indigo, stringResource(R.string.phone_files_internal_storage), all.size, Modifier.weight(1f).fillMaxHeight()) { onOpenFolder("") }
            if (downloads != null) {
                StorageTile(Icons.Rounded.Download, TileTone.Sky, stringResource(R.string.phone_files_downloads), downloadsCount, Modifier.weight(1f).fillMaxHeight()) { onOpenFolder(downloads) }
            }
            StorageTile(Icons.Rounded.TaskAlt, TileTone.Teal, stringResource(R.string.phone_files_processed), processedCount, Modifier.weight(1f).fillMaxHeight(), onOpenProcessed)
        }
        Spacer(Modifier.height(5.dp))
        CloudRow(onOpenCloud)
        // The list's heading and its controls hang 16 dp outside the list's own padding, like the line they replace.
        controls()
    }
}

/** Other apps' files: the phone's own picker, which lists Google Drive, Dropbox, OneDrive and any other storage app. */
@Composable
private fun CloudRow(onOpenCloud: () -> Unit) {
    StorageRow(
        Icons.Rounded.Cloud, TileTone.Violet, stringResource(R.string.phone_files_cloud_title), null,
        subtitle = stringResource(R.string.phone_files_cloud_open), onClick = onOpenCloud
    )
}

/** One place files are kept, as a small tile: its icon beside how many files, its name under them. Three sit side by side. */
@Composable
private fun StorageTile(icon: ImageVector, tone: TileTone, title: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clip(shape).clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, tone, size = 26.dp, corner = 8.dp, iconSize = 15.dp)
                Spacer(Modifier.width(8.dp))
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** How many files are listed, the sort order and the list/grid switch, on one line; over the whole list it carries the heading too. */
@Composable
private fun ListControls(
    heading: String?,
    count: String?,
    sort: PhoneFileSort,
    ascending: Boolean,
    grid: Boolean,
    onSortChange: (PhoneFileSort) -> Unit,
    onGridChange: (Boolean) -> Unit
) {
    Row(
        // In the list the row already has the list's 16 dp at each side; alone above the list it needs its own.
        Modifier.fillMaxWidth().padding(start = if (heading != null) 4.dp else 20.dp, end = if (heading != null) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (heading != null) {
            Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(8.dp))
        }
        Text(
            count.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        SortMenu(sort, ascending, onSortChange)
        IconButton(onClick = { onGridChange(!grid) }) {
            Icon(
                if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                contentDescription = stringResource(if (grid) R.string.phone_files_show_list else R.string.phone_files_show_grid)
            )
        }
    }
}

/** The sort order as one button: it names the order and its direction; picking the current order again turns it round. */
@Composable
private fun SortMenu(sort: PhoneFileSort, ascending: Boolean, onSortChange: (PhoneFileSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val arrow = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward
    val direction = stringResource(if (ascending) R.string.phone_pdfs_sort_ascending else R.string.phone_pdfs_sort_descending)
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(sortLabel(sort)))
            Spacer(Modifier.width(4.dp))
            Icon(arrow, contentDescription = direction, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PhoneFileSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(option)), fontWeight = if (option == sort) FontWeight.SemiBold else null) },
                    trailingIcon = if (option == sort) {
                        { Icon(arrow, contentDescription = direction, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = { onSortChange(option); open = false }
                )
            }
        }
    }
}

/** Files for a build without All files access: the tools' results and other apps' files, no phone-wide list. */
@Composable
private fun StorageOverviewWithoutAccess(processedCount: Int, onOpenProcessed: () -> Unit, onOpenCloud: () -> Unit) {
    OverviewHeading(stringResource(R.string.phone_files_my_storage))
    StorageRow(Icons.Rounded.TaskAlt, TileTone.Teal, stringResource(R.string.phone_files_processed), processedCount.toString(), onClick = onOpenProcessed)
    CloudRow(onOpenCloud)
}

@Composable
private fun OverviewHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp).semantics { heading() }
    )
}

@Composable
private fun StorageRow(icon: ImageVector, tone: TileTone, title: String, count: String?, subtitle: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tone, size = 30.dp, corner = 9.dp, iconSize = 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (count != null) Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Where the Folders view is: the top of the storage, then each folder down to the open one; each step can be tapped. */
@Composable
private fun FolderPathBar(path: String, onOpen: (String?) -> Unit) {
    val steps = remember(path) { PhoneFiles.pathSteps(path) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            IconButton(onClick = { onOpen(if (path.isEmpty()) null else path.substringBeforeLast('/', "")) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.phone_files_up))
            }
        }
        item {
            TextButton(onClick = { onOpen("") }, enabled = steps.isNotEmpty()) { Text(stringResource(R.string.phone_files_internal_storage)) }
        }
        items(steps, key = { it.path }) { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                TextButton(onClick = { onOpen(step.path) }, enabled = step.path != path.trim('/')) { Text(step.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: PhoneFolder, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val count = pluralStringResource(R.plurals.phone_files_count, folder.count, folder.count)
    val info = buildList {
        add(count)
        if (folder.modified > 0) add(DateUtils.formatDateTime(context, folder.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR))
    }.joinToString(" · ")
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Folder, TileTone.Violet, size = 48.dp, corner = 12.dp, iconSize = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A file in the grid view: its thumbnail and name; a long press selects, as in the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneFileCell(file: PhoneFile, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                KindTile(file, 84.dp)
                if (selected) Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                file.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SelectionBar(selection: List<PhoneFile>, onClear: () -> Unit, onAction: (SelectionAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.phone_files_clear_selection)) }
            Text(
                pluralStringResource(R.plurals.phone_files_selected, selection.size, selection.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (PhoneFiles.canMerge(selection)) TextButton(onClick = { onAction(SelectionAction.MERGE) }) {
                Icon(Icons.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.phone_files_merge))
            }
            if (PhoneFiles.canMakePdfFromImages(selection)) TextButton(onClick = { onAction(SelectionAction.IMAGES_TO_PDF) }) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.phone_files_images_to_pdf))
            }
            IconButton(onClick = { onAction(SelectionAction.SHARE) }, enabled = selection.isNotEmpty()) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.phone_pdfs_share))
            }
        }
    }
}

private fun filterLabel(filter: PhoneFileFilter) = when (filter) {
    PhoneFileFilter.ALL -> R.string.phone_files_filter_all
    PhoneFileFilter.PDF -> R.string.phone_files_filter_pdf
    PhoneFileFilter.IMAGES -> R.string.phone_files_filter_images
    PhoneFileFilter.WORD -> R.string.phone_files_filter_word
    PhoneFileFilter.EXCEL -> R.string.phone_files_filter_excel
    PhoneFileFilter.POWERPOINT -> R.string.phone_files_filter_powerpoint
    PhoneFileFilter.TEXT -> R.string.phone_files_filter_text
}

private fun sortLabel(sort: PhoneFileSort) = when (sort) {
    PhoneFileSort.NAME -> R.string.phone_pdfs_sort_name
    PhoneFileSort.DATE -> R.string.phone_pdfs_sort_date
    PhoneFileSort.SIZE -> R.string.phone_pdfs_sort_size
}

internal fun kindIcon(kind: PhoneFileKind): Pair<ImageVector, TileTone> = when (kind) {
    PhoneFileKind.PDF -> Icons.Rounded.PictureAsPdf to TileTone.Coral
    PhoneFileKind.IMAGE -> Icons.Rounded.Image to TileTone.Teal
    PhoneFileKind.WORD -> Icons.Rounded.Description to TileTone.Sky
    PhoneFileKind.EXCEL -> Icons.Rounded.TableChart to TileTone.Teal
    PhoneFileKind.POWERPOINT -> Icons.Rounded.Slideshow to TileTone.Amber
    PhoneFileKind.TEXT -> Icons.Rounded.TextSnippet to TileTone.Slate
}

@Composable
private fun KindTile(file: PhoneFile, size: Dp) {
    val (icon, tone) = kindIcon(file.kind)
    PhoneFileThumbnail(file, size, Modifier.size(size).clip(RoundedCornerShape(12.dp))) {
        IconTile(icon, tone, size = size, corner = 12.dp, iconSize = size / 2)
    }
}

@Composable
private fun details(file: PhoneFile, withFolder: Boolean = true): String {
    val context = LocalContext.current
    return buildList {
        if (withFolder && file.folder.isNotBlank()) add(file.folder)
        if (file.size > 0) add(Formatter.formatShortFileSize(context, file.size))
        if (file.modified > 0) add(
            DateUtils.formatDateTime(context, file.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR)
        )
    }.joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneFileRow(
    file: PhoneFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val info = details(file)
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
    ) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                KindTile(file, 48.dp)
                if (selected) Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selecting) Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.phone_pdfs_more, file.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    fun item(label: Int, icon: ImageVector, action: FileAction): @Composable () -> Unit = {
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(action) }
                        )
                    }
                    when (file.kind) {
                        PhoneFileKind.PDF -> {
                            item(R.string.phone_pdfs_open, Icons.Rounded.OpenInNew, FileAction.OPEN)()
                            item(R.string.phone_pdfs_add, Icons.Rounded.LibraryAdd, FileAction.ADD)()
                        }
                        PhoneFileKind.IMAGE -> {
                            item(R.string.phone_pdfs_open, Icons.Rounded.OpenInNew, FileAction.OPEN)()
                            item(R.string.phone_files_convert, Icons.Rounded.PictureAsPdf, FileAction.CONVERT)()
                        }
                        PhoneFileKind.TEXT -> item(R.string.phone_files_open_with, Icons.Rounded.OpenInNew, FileAction.OPEN_WITH)()
                        else -> {
                            item(R.string.phone_files_convert, Icons.Rounded.PictureAsPdf, FileAction.CONVERT)()
                            item(R.string.phone_files_open_with, Icons.Rounded.OpenInNew, FileAction.OPEN_WITH)()
                        }
                    }
                    item(R.string.phone_pdfs_share, Icons.Rounded.Share, FileAction.SHARE)()
                }
            }
        }
    }
}

/** A picture from the phone, large, with what can be done with it. */
@Composable
private fun ImagePreviewDialog(file: PhoneFile, onDismiss: () -> Unit, onConvert: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, file.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.phone_files_close)) }
                }
                Box(
                    Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    val current = bitmap
                    if (current == null) FileLoadingAnimation(size = 72.dp)
                    else Image(current.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Text(details(file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.phone_pdfs_share))
                    }
                    GradientButton(text = stringResource(R.string.phone_files_convert), icon = Icons.Rounded.PictureAsPdf, onClick = onConvert, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The explainer card on Home and in Files while ScanPro can't see the phone's files: why, that nothing leaves the
 * phone and that it's optional. "Allow access" goes on to the system page; closing the card hides it for good there.
 */
@Composable
fun PhoneFilesAccessCard(onAllow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = TileTone.Indigo.container().copy(alpha = 0.55f), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                FolderWithPdfIllustration(Modifier.size(width = 64.dp, height = 50.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(stringResource(R.string.phone_files_card_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.phone_pdfs_card_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.phone_pdfs_card_hide)) }
            }
            Row(Modifier.fillMaxWidth().padding(end = 10.dp, top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.phone_pdfs_not_now)) }
                Spacer(Modifier.width(6.dp))
                GradientButton(text = stringResource(R.string.phone_pdfs_allow), onClick = onAllow)
            }
        }
    }
}

/**
 * Home's "Files on this phone": the access card until it is granted or closed, then the newest files with "See all".
 * Nothing at all once the card was closed without access.
 */
@Composable
fun PhoneFilesHomeSection(
    actions: PhoneFileActions,
    onSeeAll: () -> Unit,
    cardDismissed: Boolean,
    onDismissCard: () -> Unit,
    modifier: Modifier = Modifier,
    /** False: only the access card (Files > My documents, where On this phone has the list). */
    showFiles: Boolean = true,
    viewModel: PhonePdfsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    val requestAccess = rememberPhoneFilesAccessRequest(onResult = viewModel::checkAccess)
    when (state.hasAccess) {
        null -> Unit
        false -> if (!cardDismissed) PhoneFilesAccessCard(onAllow = requestAccess, onDismiss = onDismissCard, modifier = modifier.padding(horizontal = 16.dp))
        true -> if (showFiles) {
            val newest = remember(state.all) { PhoneFiles.newest(state.all.orEmpty()) }
            if (newest.isEmpty()) return
            PhoneFilesStrip(newest, actions = actions, onSeeAll = onSeeAll, modifier = modifier)
        }
    }
}

@Composable
internal fun PhoneFilesStrip(files: List<PhoneFile>, actions: PhoneFileActions, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val openWithTitle = stringResource(R.string.phone_files_open_with)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.phone_pdfs_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.lib_see_all)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(files, key = { it.uri.toString() }) { file ->
                Surface(
                    onClick = {
                        when (file.kind) {
                            PhoneFileKind.PDF -> actions.onOpenPdf(file.uri, file.name)
                            // Images and Office files open in the full list, where their actions are.
                            PhoneFileKind.IMAGE -> onSeeAll()
                            else -> PhonePdfSource.shareableUri(context, file) { uri -> if (uri != null) openWith(context, uri, file.mimeType, openWithTitle) }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.width(124.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            val (icon, tone) = kindIcon(file.kind)
                            PhoneFileThumbnail(file, 108.dp, Modifier.fillMaxSize()) { IconTile(icon, tone, size = 44.dp, corner = 12.dp) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(file.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(details(file, withFolder = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
