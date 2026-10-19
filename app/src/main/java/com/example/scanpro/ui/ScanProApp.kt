package com.example.scanpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.scanpro.MainViewModel
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.scanner.CameraScannerScreen
import java.text.DateFormat
import java.util.Date

private const val ROUTE_HOME = "home"
private const val ROUTE_DOCUMENTS = "documents"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CAMERA = "camera"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_DETAIL = "detail"

private enum class LibrarySort(val label: String) {
    UPDATED("Recent"), NAME("Name"), SIZE("Size")
}

@Composable
fun ScanProApp(
    viewModel: MainViewModel,
    sessionUnlocked: Boolean,
    onFallbackScanner: () -> Unit,
    onImportImages: () -> Unit,
    onUnlock: () -> Unit,
    onAppLockChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportArtifact by viewModel.exportArtifact.collectAsStateWithLifecycle()
    val newScanId by viewModel.newScanId.collectAsStateWithLifecycle()
    val pdfQuality by viewModel.defaultPdfQuality.collectAsStateWithLifecycle()
    val autoTitle by viewModel.autoTitle.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(exportArtifact) {
        exportArtifact?.let {
            shareFile(context, it.path, it.mimeType, it.title)
            viewModel.clearExportArtifact()
        }
    }

    LaunchedEffect(newScanId, documents) {
        val id = newScanId ?: return@LaunchedEffect
        if (documents.any { it.id == id }) {
            navController.navigate("$ROUTE_DETAIL/$id") { launchSingleTop = true }
            viewModel.clearNewScanId()
        }
    }

    if (appLock && !sessionUnlocked) {
        LockScreen(onUnlock)
        return
    }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        listOf(ROUTE_HOME, ROUTE_DOCUMENTS, ROUTE_SEARCH).forEach { route ->
            composable(route) {
                LibraryScreen(
                    mode = route,
                    documents = documents,
                    trashCount = trash.size,
                    query = query,
                    busy = busy,
                    busyLabel = busyLabel,
                    snackbarHostState = snackbarHostState,
                    onQueryChange = viewModel::setQuery,
                    onOpenDocument = { navController.navigate("$ROUTE_DETAIL/$it") },
                    onStartScan = { navController.navigate(ROUTE_CAMERA) },
                    onImportImages = onImportImages,
                    onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                    onTrash = { navController.navigate(ROUTE_TRASH) },
                    onBulkDelete = viewModel::moveManyToTrash,
                    onRenameFolder = viewModel::renameFolder,
                    onTab = { tab -> navigateLibrary(navController, tab, viewModel) }
                )
            }
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                pdfQuality = pdfQuality,
                autoTitle = autoTitle,
                appLock = appLock,
                themeMode = themeMode,
                documentCount = documents.size,
                trashCount = trash.size,
                storageBytes = storageBytes,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onPdfQualityChange = viewModel::setDefaultPdfQuality,
                onAutoTitleChange = viewModel::setAutoTitle,
                onAppLockChange = onAppLockChange,
                onThemeModeChange = viewModel::setThemeMode,
                bottomBar = {
                    LibraryNavigationBar(
                        selectedRoute = ROUTE_SETTINGS,
                        onTab = { route -> navigateLibrary(navController, route, viewModel) }
                    )
                }
            )
        }
        composable(ROUTE_CAMERA) {
            CameraScannerScreen(
                onClose = { navController.popBackStack() },
                onComplete = { uris, mode ->
                    viewModel.importCameraSession(uris, mode)
                    navController.popBackStack()
                },
                onFallbackScanner = {
                    navController.popBackStack()
                    onFallbackScanner()
                }
            )
        }
        composable(ROUTE_TRASH) {
            TrashScreen(
                documents = trash,
                busy = busy,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onRestore = viewModel::restore,
                onDeleteForever = viewModel::deleteForever,
                onEmptyTrash = viewModel::emptyTrash
            )
        }
        composable("$ROUTE_DETAIL/{documentId}") { entry ->
            val documentId = entry.arguments?.getString("documentId")
            val selected = documents.firstOrNull { it.id == documentId }
            if (selected == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(48.dp))
                    else Text("Document is unavailable")
                }
            } else {
                DocumentDetailScreen(
                    document = selected,
                    otherDocuments = documents.filterNot { it.id == selected.id },
                    busy = busy,
                    busyLabel = busyLabel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                    onRename = { viewModel.rename(selected.id, it) },
                    onTags = { viewModel.setTags(selected.id, it) },
                    onFolder = { viewModel.setFolder(selected.id, it) },
                    onToggleFavorite = { viewModel.toggleFavorite(selected.id) },
                    onPdfQuality = { viewModel.setDocumentPdfQuality(selected.id, it) },
                    onPdfPageSize = { viewModel.setDocumentPdfPageSize(selected.id, it) },
                    onSearchablePdf = { viewModel.setSearchablePdf(selected.id, it) },
                    onPageNumbers = { viewModel.setPageNumbers(selected.id, it) },
                    onScanMode = { viewModel.setScanMode(selected.id, it) },
                    onApplyFilter = { pageId, filter -> viewModel.applyFilter(selected.id, pageId, filter) },
                    onRotate = { viewModel.rotatePage(selected.id, it) },
                    onCrop = { pageId, crop -> viewModel.applyCrop(selected.id, pageId, crop) },
                    onAdjustments = { pageId, value -> viewModel.applyAdjustments(selected.id, pageId, value) },
                    onResetPage = { viewModel.resetPage(selected.id, it) },
                    onMove = { pageId, delta -> viewModel.movePage(selected.id, pageId, delta) },
                    onDuplicatePage = { viewModel.duplicatePage(selected.id, it) },
                    onExtractPage = { viewModel.extractPage(selected.id, it) },
                    onSplitBook = { viewModel.splitBookSpread(selected.id, it) },
                    onDeletePage = { viewModel.deletePage(selected.id, it) },
                    onSign = { pageId, bitmap -> viewModel.stampSignature(selected.id, pageId, bitmap) },
                    onTextAnnotation = { pageId, text -> viewModel.addTextAnnotation(selected.id, pageId, text) },
                    onHighlight = { viewModel.addHighlight(selected.id, it) },
                    onRedact = { viewModel.addRedaction(selected.id, it) },
                    onUndoAnnotation = { viewModel.undoLastAnnotation(selected.id, it) },
                    onWatermark = { viewModel.addWatermark(selected.id, it) },
                    onAppendImages = { viewModel.appendImages(selected.id, it) },
                    onMergeWith = { viewModel.mergeDocuments(selected.id, it) },
                    onReOcr = { viewModel.reOcrDocument(selected.id) },
                    onUpdateOcrText = { pageId, text -> viewModel.updatePageOcrText(selected.id, pageId, text) },
                    onOcrScript = { script -> viewModel.reOcrDocument(selected.id, script) },
                    onExportIdCopy = { viewModel.exportIdCopy(selected.id) },
                    onExportLongImage = { viewModel.exportLongImage(selected.id) },
                    onExportOcrTextFile = { viewModel.exportOcrTextFile(selected.id) },
                    onSplitDocument = { viewModel.splitDocument(selected.id) },
                    onExportProtectedPdf = { password -> viewModel.exportPasswordProtectedPdf(selected.id, password) },
                    onExportEncryptedBackup = { password -> viewModel.exportEncryptedBackup(selected.id, password) },
                    onDeleteDocument = {
                        viewModel.moveToTrash(selected.id)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

private fun navigateLibrary(
    navController: androidx.navigation.NavHostController,
    route: String,
    viewModel: MainViewModel
) {
    if (route != ROUTE_SEARCH) viewModel.setQuery("")
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
    mode: String,
    documents: List<ScanDocument>,
    trashCount: Int,
    query: String,
    busy: Boolean,
    busyLabel: String?,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onStartScan: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onTrash: () -> Unit,
    onBulkDelete: (Set<String>) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    var gridMode by rememberSaveable { mutableStateOf(mode != ROUTE_HOME) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.UPDATED.name) }
    var folder by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var renameFolderSource by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val sort = runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.UPDATED)
    val folders = remember(documents) { documents.map { it.folderName.trim() }.filter { it.isNotBlank() }.distinct().sorted() }

    val visible = remember(documents, mode, folder, favoritesOnly, sort) {
        val base = documents.asSequence()
            .filter { folder.isBlank() || it.folderName.equals(folder, ignoreCase = true) }
            .filter { !favoritesOnly || it.isFavorite }
            .toList()
        when (sort) {
            LibrarySort.UPDATED -> base.sortedByDescending { it.updatedAt }
            LibrarySort.NAME -> base.sortedBy { it.title.lowercase() }
            LibrarySort.SIZE -> base.sortedByDescending { it.sizeBytes }
        }
    }

    // Drop selections hidden by a folder/favorites/sort change so bulk actions only touch visible items.
    LaunchedEffect(visible) {
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        selected.removeAll { it !in visibleIds }
    }

    fun toggleSelection(id: String) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selected.isNotEmpty() -> "${selected.size} selected"
                            mode == ROUTE_DOCUMENTS -> "Documents"
                            mode == ROUTE_SEARCH -> "Search"
                            else -> "ScanPro"
                        }
                    )
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { sharePdfs(context, documents.filter { it.id in selected }) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = {
                            onBulkDelete(selected.toSet())
                            selected.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Move selected to trash")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                LibrarySort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        trailingIcon = { if (sort == option) Icon(Icons.Default.Check, contentDescription = "Selected") },
                                        onClick = {
                                            sortName = option.name
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.Default.List else Icons.Default.GridView, contentDescription = "Toggle layout")
                        }
                        Box {
                            IconButton(onClick = { moreMenuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Import photos") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    enabled = !busy,
                                    onClick = { moreMenuOpen = false; onImportImages() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    enabled = !busy,
                                    onClick = { moreMenuOpen = false; onImportPdf() }
                                )
                                if (mode == ROUTE_DOCUMENTS && folder.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Rename folder") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = { moreMenuOpen = false; renameFolderSource = folder }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (trashCount > 0) "Recycle bin ($trashCount)" else "Recycle bin") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onTrash() }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onStartScan,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text("Scan") }
                )
            }
        },
        bottomBar = { LibraryNavigationBar(selectedRoute = mode, onTab = onTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (!busyLabel.isNullOrBlank()) Text(busyLabel, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            if (mode == ROUTE_HOME && documents.isNotEmpty()) {
                Text(
                    "${documents.size} documents" + if (documents.any { it.isFavorite }) " · ${documents.count { it.isFavorite }} favorites" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            if (mode == ROUTE_SEARCH) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search titles, tags, folders and text") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // Folder/favorite filters only earn their space once there is something to filter.
            if (documents.isNotEmpty() && (folders.isNotEmpty() || documents.any { it.isFavorite } || favoritesOnly)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = folder.isBlank() && !favoritesOnly,
                            onClick = { folder = ""; favoritesOnly = false },
                            label = { Text("All") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("Favorites") },
                            leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline, null) }
                        )
                    }
                    folders.forEach { name ->
                        item(key = "folder_$name") {
                            FilterChip(
                                selected = folder == name,
                                onClick = { folder = if (folder == name) "" else name },
                                label = { Text(name, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.Folder, null) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (query.isNotBlank()) Icons.Default.Search else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                query.isNotBlank() -> "No matching documents"
                                favoritesOnly -> "No favorite documents yet"
                                folder.isNotBlank() -> "No documents in this folder"
                                else -> "No documents yet"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (query.isBlank() && !favoritesOnly && folder.isBlank()) {
                            Text(
                                "Tap Scan to capture your first document.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (gridMode) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(visible, key = { it.id }) { doc ->
                        DocumentGridCard(
                            document = doc,
                            selected = doc.id in selected,
                            onClick = {
                                if (selected.isNotEmpty()) toggleSelection(doc.id) else onOpenDocument(doc.id)
                            },
                            onLongClick = { toggleSelection(doc.id) }
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { doc ->
                        DocumentListCard(
                            document = doc,
                            selected = doc.id in selected,
                            onClick = {
                                if (selected.isNotEmpty()) toggleSelection(doc.id) else onOpenDocument(doc.id)
                            },
                            onLongClick = { toggleSelection(doc.id) }
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    renameFolderSource?.let { source ->
        TextInputDialog(
            title = "Rename folder",
            initial = source,
            label = "Folder name",
            confirmLabel = "Rename",
            onDismiss = { renameFolderSource = null }
        ) { target ->
            onRenameFolder(source, target)
            folder = target.trim()
            renameFolderSource = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentListCard(
    document: ScanDocument,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PageThumbnail(
                path = document.pages.firstOrNull()?.imagePath,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                maxDimension = THUMBNAIL_SMALL
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(document.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (document.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Favorite", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${pagesLabel(document.pageCount)} · ${formatBytes(document.sizeBytes)} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(document.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentGridCard(
    document: ScanDocument,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                PageThumbnail(
                    path = document.pages.firstOrNull()?.imagePath,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", modifier = Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(document.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pagesLabel(document.pageCount)} · ${formatBytes(document.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun pagesLabel(count: Int) = if (count == 1) "1 page" else "$count pages"

@Composable
private fun LibraryNavigationBar(selectedRoute: String, onTab: (String) -> Unit) {
    NavigationBar {
        listOf(
            Triple(ROUTE_HOME, "Home", Icons.Default.Home),
            Triple(ROUTE_DOCUMENTS, "Documents", Icons.Default.Description),
            Triple(ROUTE_SEARCH, "Search", Icons.Default.Search),
            Triple(ROUTE_SETTINGS, "Settings", Icons.Default.Settings)
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = selectedRoute == route,
                onClick = { onTab(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

private enum class TrashSortOption(val label: String) {
    EXPIRY("Soonest to expire"),
    DATE("Deletion date"),
    NAME("File name"),
    SIZE("File size")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashScreen(
    documents: List<ScanDocument>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var sortOption by remember { mutableStateOf(TrashSortOption.EXPIRY) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var deleteSingleDoc by remember { mutableStateOf<ScanDocument?>(null) }
    var activeMenuDocId by remember { mutableStateOf<String?>(null) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val sortedDocuments = remember(documents, sortOption, nowMs) {
        val retentionMs = 7L * 24L * 60L * 60L * 1000L
        when (sortOption) {
            TrashSortOption.EXPIRY -> documents.sortedBy { (it.trashedAt + retentionMs) - nowMs }
            TrashSortOption.DATE -> documents.sortedByDescending { it.trashedAt }
            TrashSortOption.NAME -> documents.sortedBy { it.title.lowercase() }
            TrashSortOption.SIZE -> documents.sortedByDescending { it.sizeBytes }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (documents.isNotEmpty()) {
                        TextButton(enabled = !busy, onClick = { showEmptyDialog = true }) {
                            Text("Empty", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Recycle Bin is Empty", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deleted files will appear here for 7 days before being permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Files are automatically deleted after 7 days.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(TrashSortOption.entries) { option ->
                        FilterChip(
                            selected = sortOption == option,
                            onClick = { sortOption = option },
                            label = { Text(option.label) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedDocuments, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PageThumbnail(doc.pages.firstOrNull()?.imagePath, Modifier.size(width = 56.dp, height = 74.dp), THUMBNAIL_SMALL)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val (countdownText, isUrgent) = formatTrashCountdown(doc.trashedAt, nowMs)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        val countdownColor = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = countdownColor
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            countdownText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = countdownColor,
                                            fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val pagesLabel = if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages"
                                    Text("$pagesLabel • ${formatBytes(doc.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(enabled = !busy, onClick = { activeMenuDocId = doc.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                    }
                                    DropdownMenu(
                                        expanded = activeMenuDocId == doc.id,
                                        onDismissRequest = { activeMenuDocId = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Restore") },
                                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                            onClick = {
                                                activeMenuDocId = null
                                                onRestore(doc.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                activeMenuDocId = null
                                                deleteSingleDoc = doc
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteSingleDoc != null) {
        ConfirmDialog(
            title = "Delete permanently?",
            message = "This file will be permanently deleted and cannot be recovered. Are you sure?",
            confirmLabel = "Delete",
            onDismiss = { deleteSingleDoc = null },
            onConfirm = {
                val id = deleteSingleDoc?.id
                deleteSingleDoc = null
                if (id != null) onDeleteForever(id)
            }
        )
    }

    if (showEmptyDialog) {
        ConfirmDialog(
            title = "Empty Recycle Bin?",
            message = "This will permanently delete all files in the Recycle Bin. This action cannot be undone.",
            confirmLabel = "Empty",
            onDismiss = { showEmptyDialog = false },
            onConfirm = {
                showEmptyDialog = false
                onEmptyTrash()
            }
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp))
            Text("ScanPro is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
