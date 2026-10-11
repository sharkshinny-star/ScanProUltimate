package com.example.scanpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        composable(ROUTE_HOME) {
            LibraryScreen(
                mode = ROUTE_HOME,
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
                onTab = { route -> navigateLibrary(navController, route, viewModel) }
            )
        }
        composable(ROUTE_DOCUMENTS) {
            LibraryScreen(
                mode = ROUTE_DOCUMENTS,
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
                onTab = { route -> navigateLibrary(navController, route, viewModel) }
            )
        }
        composable(ROUTE_SEARCH) {
            LibraryScreen(
                mode = ROUTE_SEARCH,
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
                onTab = { route -> navigateLibrary(navController, route, viewModel) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                pdfQuality = pdfQuality,
                autoTitle = autoTitle,
                appLock = appLock,
                documentCount = documents.size,
                trashCount = trash.size,
                storageBytes = storageBytes,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onPdfQualityChange = viewModel::setDefaultPdfQuality,
                onAutoTitleChange = viewModel::setAutoTitle,
                onAppLockChange = onAppLockChange,
                onImportBackup = viewModel::importEncryptedBackup,
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
    val selected = remember { mutableStateListOf<String>() }
    val sort = runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.UPDATED)
    val folders = documents.map { it.folderName.trim() }.filter { it.isNotBlank() }.distinct().sorted()

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
                        when (mode) {
                            ROUTE_DOCUMENTS -> "My files"
                            ROUTE_SEARCH -> "Search"
                            else -> "ScanPro"
                        }
                    )
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
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.Default.List else Icons.Default.GridView, contentDescription = "Toggle layout")
                        }
                        IconButton(onClick = onTrash) {
                            Icon(Icons.Default.Delete, contentDescription = "Recycle bin")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartScan) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Scan")
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

            if (mode == ROUTE_HOME) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "YOUR DOCUMENT WORKSPACE",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("A little less paper.", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (documents.isEmpty()) "Scan, clean, OCR and export your first document."
                    else "${documents.size} documents · ${documents.count { it.isFavorite }} favorites${if (trashCount > 0) " · $trashCount in trash" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            if (mode == ROUTE_SEARCH || mode == ROUTE_HOME) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search title, OCR, tags or folders") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (mode == ROUTE_HOME) {
                ScanHeroCard(onStartScan = onStartScan, enabled = !busy)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilledTonalButton(onClick = onImportImages, enabled = !busy) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Photos")
                    } }
                    item { FilledTonalButton(onClick = onImportPdf, enabled = !busy) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("PDF")
                    } }
                }
                Spacer(Modifier.height(10.dp))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = folder.isBlank(),
                        onClick = { folder = "" },
                        label = { Text("All") }
                    )
                }
                folders.forEach { name ->
                    item(key = "folder_$name") {
                        FilterChip(
                            selected = folder == name,
                            onClick = { folder = if (folder == name) "" else name },
                            label = { Text(name, maxLines = 1) }
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("Favorites") },
                        leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline, null) }
                    )
                }
                LibrarySort.entries.forEach { option ->
                    item(key = "sort_${option.name}") {
                        FilterChip(
                            selected = sort == option,
                            onClick = { sortName = option.name },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
            if (mode == ROUTE_DOCUMENTS && folder.isNotBlank()) {
                TextButton(onClick = { renameFolderSource = folder }) {
                    Text("Rename ‘$folder’")
                }
            }
            Spacer(Modifier.height(10.dp))

            if (selected.isNotEmpty()) {
                Text("${selected.size} selected", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
            }

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            query.isNotBlank() -> "No matching documents"
                            favoritesOnly -> "No favorite documents yet"
                            folder.isNotBlank() -> "No documents in this folder"
                            else -> "Scan your first document"
                        }
                    )
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PageThumbnail(
                path = document.pages.firstOrNull()?.imagePath,
                modifier = Modifier.size(width = 66.dp, height = 86.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (document.isFavorite) Icon(Icons.Default.Star, contentDescription = "Favorite", modifier = Modifier.size(18.dp))
                }
                Text("${document.pageCount} pages · ${formatBytes(document.sizeBytes)} · ${document.pdfQuality.label}", style = MaterialTheme.typography.bodySmall)
                val meta = buildList {
                    if (document.folderName.isNotBlank()) add(document.folderName)
                    if (document.tags.isNotEmpty()) add(document.tags.take(3).joinToString(" · "))
                    if (document.searchablePdf) add("OCR PDF")
                }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            if (selected) Checkbox(checked = true, onCheckedChange = { onClick() })
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
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", modifier = Modifier.padding(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${document.pageCount} pages · ${formatBytes(document.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
            if (document.folderName.isNotBlank()) Text(document.folderName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Gradient "Smart scan" hero from the approved design. Primary entry point on the home screen. */
@Composable
private fun ScanHeroCard(onStartScan: () -> Unit, enabled: Boolean) {
    val dark = isSystemInDarkTheme()
    val start = if (dark) Color(0xFF31477D) else Color(0xFF4F5BD0)
    val end = if (dark) Color(0xFF26365F) else Color(0xFF3543AA)
    val caption = if (dark) Color(0xFFCCD6EF) else Color(0xFFE6EAFF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(start, end)))
            .padding(18.dp)
    ) {
        Column {
            Text(
                "SMART SCAN",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = caption
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Every detail.\nPerfectly captured.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Auto-crop. Clean up. Make it searchable.",
                style = MaterialTheme.typography.bodySmall,
                color = caption
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStartScan,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = end)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start scanning")
            }
        }
    }
}

@Composable
private fun LibraryNavigationBar(selectedRoute: String, onTab: (String) -> Unit) {
    NavigationBar {
        listOf(
            Triple(ROUTE_HOME, "Home", Icons.Default.Home),
            Triple(ROUTE_DOCUMENTS, "Files", Icons.Default.Description),
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle bin") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (documents.isNotEmpty()) TextButton(enabled = !busy, onClick = onEmptyTrash) { Text("Empty") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Recycle bin is empty") }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PageThumbnail(doc.pages.firstOrNull()?.imagePath, Modifier.size(width = 56.dp, height = 72.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.pageCount} pages · ${formatBytes(doc.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(enabled = !busy, onClick = { onRestore(doc.id) }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore")
                            }
                            IconButton(enabled = !busy, onClick = { onDeleteForever(doc.id) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever")
                            }
                        }
                    }
                }
            }
        }
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
