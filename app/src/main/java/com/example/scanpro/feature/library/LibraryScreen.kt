package com.example.scanpro.feature.library

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Build
import com.example.scanpro.data.ScanMode
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.ui.sharePdfs
import com.example.scanpro.navigation.ScanProBottomBar
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.core.designsystem.TitleWithStatus
import com.example.scanpro.core.designsystem.TopBarWithProgress

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private enum class LibrarySort(val label: String) {
    UPDATED("Recent"), NAME("Name"), SIZE("Size")
}

@Composable
fun LibraryRoute(
    mode: String,
    snackbarHostState: SnackbarHostState,
    onOpenDocument: (String) -> Unit,
    onStartScan: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onTrash: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onTab: (String) -> Unit,
    onQuickScan: (String) -> Unit = {},
    onOpenTools: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    LibraryScreen(
        mode = mode,
        documents = documents,
        trashCount = trashCount,
        query = query,
        busy = busy,
        busyLabel = busyLabel,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::setQuery,
        onOpenDocument = onOpenDocument,
        onStartScan = onStartScan,
        onImportImages = onImportImages,
        onImportPdf = onImportPdf,
        onTrash = onTrash,
        onSearch = onSearch,
        onBack = onBack,
        onBulkDelete = viewModel::moveManyToTrash,
        onShareSelected = { ids -> viewModel.withFreshPdfs(ids) { docs -> sharePdfs(context, docs) } },
        onRenameFolder = viewModel::renameFolder,
        onTab = onTab,
        onQuickScan = onQuickScan,
        onOpenTools = onOpenTools
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LibraryScreen(
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
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onBulkDelete: (Set<String>) -> Unit,
    onShareSelected: (Set<String>) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onTab: (String) -> Unit,
    onQuickScan: (String) -> Unit = {},
    onOpenTools: () -> Unit = {}
) {
    val isHome = mode == ScanProDestinations.HOME
    val isSearch = mode == ScanProDestinations.SEARCH
    var gridMode by rememberSaveable { mutableStateOf(mode == ScanProDestinations.FILES) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.UPDATED.name) }
    var folder by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var renameFolderSource by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val sort = runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.UPDATED)
    val folders = remember(documents) { documents.map { it.folderName.trim() }.filter { it.isNotBlank() }.distinct().sorted() }

    // A folder filter whose folder no longer exists (renamed, or its documents trashed) would hide
    // everything with no chip left to clear it.
    // Only on Files, and only once documents have loaded: an empty list just after launch, or search
    // results that happen to miss the folder, don't mean the folder is gone.
    LaunchedEffect(folders) {
        if (mode == ScanProDestinations.FILES && documents.isNotEmpty() && folder.isNotBlank() &&
            folders.none { it.equals(folder, ignoreCase = true) }
        ) folder = ""
    }

    val visible = remember(documents, mode, folder, favoritesOnly, sort) {
        // Home only shows the most recent documents, so selection and bulk actions are limited to those.
        if (mode == ScanProDestinations.HOME) {
            return@remember documents.sortedByDescending { it.updatedAt }.take(RECENT_LIMIT)
        }
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

    // Standard selection mode: system back leaves selection before leaving the screen.
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    val searchFocus = remember { FocusRequester() }
    if (isSearch) {
        LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    }

    Scaffold(
        topBar = {
            TopBarWithProgress(busy) { TopAppBar(
                title = {
                    TitleWithStatus(
                        title = when {
                            selected.isNotEmpty() -> "${selected.size} selected"
                            mode == ScanProDestinations.FILES -> "My files"
                            isSearch -> "Search"
                            else -> "ScanPro"
                        },
                        status = if (busy) busyLabel else null
                    )
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                        }
                    } else if (isSearch) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { onShareSelected(selected.toSet()) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = {
                            onBulkDelete(selected.toSet())
                            selected.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Move selected to trash")
                        }
                    } else {
                        if (!isSearch) {
                            IconButton(onClick = onSearch) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        if (!isHome) Box {
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
                        if (!isHome) IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.AutoMirrored.Filled.List else Icons.Default.GridView, contentDescription = "Toggle layout")
                        }
                        if (!isSearch) Box {
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
                                if (mode == ScanProDestinations.FILES && folder.isNotBlank()) {
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
            ) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selected.isEmpty() && mode == ScanProDestinations.FILES) {
                ExtendedFloatingActionButton(
                    onClick = onStartScan,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text("Scan") }
                )
            }
        },
        bottomBar = { if (!isSearch) ScanProBottomBar(selectedRoute = mode, onTab = onTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (isHome) {
                HomeDashboard(
                    documents = documents,
                    trashCount = trashCount,
                    busy = busy,
                    selected = selected,
                    onStartScan = onStartScan,
                    onImportImages = onImportImages,
                    onImportPdf = onImportPdf,
                    onQuickScan = onQuickScan,
                    onOpenTools = onOpenTools,
                    onSeeAll = { onTab(ScanProDestinations.FILES) },
                    onOpenDocument = onOpenDocument,
                    onToggleSelection = ::toggleSelection
                )
            } else {
                if (isSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
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
                                    "Scan a page, or bring in photos or a PDF you already have.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    Button(onClick = onStartScan, enabled = !busy) { Text("Scan") }
                                    FilledTonalButton(onClick = onImportImages, enabled = !busy) { Text("Photos") }
                                    FilledTonalButton(onClick = onImportPdf, enabled = !busy) { Text("PDF") }
                                }
                                PrivacyLine(Modifier.padding(top = 16.dp))
                            } else if (query.isNotBlank()) {
                                Text(
                                    "Search looks through titles, tags and every word recognised in your scans.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
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

private const val RECENT_LIMIT = 8

/** Home tab: workspace header, primary scan entry points and the most recent documents. */
@Composable
private fun HomeDashboard(
    documents: List<ScanDocument>,
    trashCount: Int,
    busy: Boolean,
    selected: List<String>,
    onStartScan: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onQuickScan: (String) -> Unit,
    onOpenTools: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    val recent = remember(documents) { documents.sortedByDescending { it.updatedAt }.take(RECENT_LIMIT) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
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
            }
        }
        item { ScanHeroCard(onStartScan = onStartScan, enabled = !busy) }
        item {
            QuickActions(
                enabled = !busy,
                actions = listOf(
                    QuickAction(Icons.Default.Badge, "ID card") { onQuickScan(ScanMode.ID_CARD.name) },
                    QuickAction(Icons.AutoMirrored.Filled.MenuBook, "Book") { onQuickScan(ScanMode.BOOK.name) },
                    QuickAction(Icons.Default.TextFields, "Extract text") { onQuickScan(ScanMode.TEXT.name) },
                    QuickAction(Icons.Default.QrCodeScanner, "QR code") { onQuickScan(ScanProDestinations.CAMERA_QR) },
                    QuickAction(Icons.Default.ReceiptLong, "Receipt") { onQuickScan(ScanMode.RECEIPT.name) },
                    QuickAction(Icons.Default.Image, "Photos") { onImportImages() },
                    QuickAction(Icons.Default.PictureAsPdf, "Import PDF") { onImportPdf() },
                    QuickAction(Icons.Default.Build, "PDF tools") { onOpenTools() }
                )
            )
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (documents.size > recent.size) TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            items(recent, key = { it.id }) { doc ->
                DocumentListCard(
                    document = doc,
                    selected = doc.id in selected,
                    onClick = { if (selected.isNotEmpty()) onToggleSelection(doc.id) else onOpenDocument(doc.id) },
                    onLongClick = { onToggleSelection(doc.id) }
                )
            }
        }
        if (recent.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your scans will appear here", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Each one becomes a searchable PDF you can share, sign or export to Word.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        item { PrivacyLine(Modifier.fillMaxWidth().padding(top = 4.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** The app's promise, kept visible: everything is done and kept on this phone. */
@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Private by design: no account, nothing uploaded",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Gradient "Smart scan" hero from the approved design. Primary entry point on the home screen. */
@Composable
private fun ScanHeroCard(onStartScan: () -> Unit, enabled: Boolean) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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

private class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** One-tap shortcuts for the jobs people do most, each opening the right mode or tool directly. */
@Composable
private fun QuickActions(enabled: Boolean, actions: List<QuickAction>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(actions, key = { it.label }) { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = enabled, role = Role.Button, onClick = action.onClick)
                    .padding(vertical = 6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
