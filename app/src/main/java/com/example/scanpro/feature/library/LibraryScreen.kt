package com.example.scanpro.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.SectionHeader
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.TitleWithStatus
import com.example.scanpro.core.designsystem.TopBarWithProgress
import com.example.scanpro.core.designsystem.drawScanGlyph
import com.example.scanpro.core.ui.sharePdfs
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.navigation.ScanProBottomBar
import com.example.scanpro.navigation.ScanProDestinations
import java.util.Calendar

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

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun toggleSelection(id: String) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    // Standard selection mode: system back leaves selection before leaving the screen.
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    val searchFocus = remember { FocusRequester() }
    if (isSearch) {
        LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    }

    val selecting = selected.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBarWithProgress(busy) {
                TopAppBar(
                    colors = topBarColors,
                    scrollBehavior = scrollBehavior,
                    title = {
                        when {
                            selecting -> TitleWithStatus("${selected.size} selected", if (busy) busyLabel else null)
                            isHome -> BrandLockup()
                            isSearch -> Text("Search")
                            else -> TitleWithStatus(
                                "My files",
                                when {
                                    busy -> busyLabel
                                    documents.isEmpty() -> null
                                    else -> "${documents.size} ${if (documents.size == 1) "document" else "documents"}"
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        if (selecting) {
                            IconButton(onClick = { selected.clear() }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear selection")
                            }
                        } else if (isSearch) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (selecting) {
                            IconButton(onClick = { onShareSelected(selected.toSet()) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share selected")
                            }
                            IconButton(onClick = {
                                onBulkDelete(selected.toSet())
                                selected.clear()
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Move selected to trash")
                            }
                        } else {
                            if (!isSearch && !isHome) {
                                IconButton(onClick = onSearch) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                                }
                            }
                            if (!isHome) Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    LibrarySort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            trailingIcon = { if (sort == option) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                                            onClick = {
                                                sortName = option.name
                                                sortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (!isHome) IconButton(onClick = { gridMode = !gridMode }) {
                                Icon(
                                    if (gridMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = if (gridMode) "Show as list" else "Show as grid"
                                )
                            }
                            if (!isSearch) Box {
                                IconButton(onClick = { moreMenuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Import photos") },
                                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = { moreMenuOpen = false; onImportImages() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import PDF") },
                                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = { moreMenuOpen = false; onImportPdf() }
                                    )
                                    if (mode == ScanProDestinations.FILES && folder.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Rename folder") },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = { moreMenuOpen = false; renameFolderSource = folder }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (trashCount > 0) "Recycle bin ($trashCount)" else "Recycle bin") },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                        onClick = { moreMenuOpen = false; onTrash() }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (!isSearch) ScanProBottomBar(selectedRoute = mode, onTab = onTab, onScan = onStartScan) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
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
                    onSearch = onSearch,
                    onSeeAll = { onTab(ScanProDestinations.FILES) },
                    onOpenDocument = onOpenDocument,
                    onToggleSelection = ::toggleSelection
                )
            } else {
                Column(Modifier.fillMaxSize().widthIn(max = 1100.dp)) {
                    if (isSearch) {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            focusRequester = searchFocus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Folder/favorite filters only earn their space once there is something to filter.
                    if (documents.isNotEmpty() && (folders.isNotEmpty() || documents.any { it.isFavorite } || favoritesOnly)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                LibraryChip(selected = folder.isBlank() && !favoritesOnly, label = "All") { folder = ""; favoritesOnly = false }
                            }
                            item {
                                LibraryChip(
                                    selected = favoritesOnly,
                                    label = "Favorites",
                                    icon = if (favoritesOnly) Icons.Rounded.Star else Icons.Outlined.StarOutline
                                ) { favoritesOnly = !favoritesOnly }
                            }
                            folders.forEach { name ->
                                item(key = "folder_$name") {
                                    LibraryChip(selected = folder == name, label = name, icon = Icons.Rounded.Folder) {
                                        folder = if (folder == name) "" else name
                                    }
                                }
                            }
                        }
                    }

                    when {
                        visible.isEmpty() -> EmptyLibrary(
                            query = query,
                            favoritesOnly = favoritesOnly,
                            folder = folder,
                            busy = busy,
                            onStartScan = onStartScan,
                            onImportImages = onImportImages,
                            onImportPdf = onImportPdf
                        )
                        gridMode -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(156.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItems(visible, key = { it.id }) { doc ->
                                DocumentGridCard(
                                    document = doc,
                                    selected = doc.id in selected,
                                    onClick = { if (selecting) toggleSelection(doc.id) else onOpenDocument(doc.id) },
                                    onLongClick = { toggleSelection(doc.id) }
                                )
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visible, key = { it.id }) { doc ->
                                DocumentListCard(
                                    document = doc,
                                    selected = doc.id in selected,
                                    onClick = { if (selecting) toggleSelection(doc.id) else onOpenDocument(doc.id) },
                                    onLongClick = { toggleSelection(doc.id) }
                                )
                            }
                        }
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

// ---------------------------------------------------------------------------------------------
// Files and search pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun LibraryChip(selected: Boolean, label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text("Titles, tags, folders and text") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun EmptyLibrary(
    query: String,
    favoritesOnly: Boolean,
    folder: String,
    busy: Boolean,
    onStartScan: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit
) {
    val fresh = query.isBlank() && !favoritesOnly && folder.isBlank()
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp)) {
            IconTile(
                icon = when {
                    query.isNotBlank() -> Icons.Rounded.Search
                    favoritesOnly -> Icons.Rounded.Star
                    folder.isNotBlank() -> Icons.Rounded.Folder
                    else -> Icons.Rounded.Description
                },
                tone = TileTone.Indigo,
                size = 84.dp,
                corner = 28.dp,
                iconSize = 40.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    query.isNotBlank() -> "No matching documents"
                    favoritesOnly -> "No favorites yet"
                    folder.isNotBlank() -> "This folder is empty"
                    else -> "Your library is empty"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    query.isNotBlank() -> "Search looks through titles, tags and every word recognised in your scans."
                    favoritesOnly -> "Star a document to keep it one tap away."
                    folder.isNotBlank() -> "Move documents here from their details page."
                    else -> "Scan a page, or bring in photos or a PDF you already have."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (fresh) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartScan, enabled = !busy) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan")
                    }
                    FilledTonalButton(onClick = onImportImages, enabled = !busy) { Text("Photos") }
                    FilledTonalButton(onClick = onImportPdf, enabled = !busy) { Text("PDF") }
                }
                PrivacyLine(Modifier.padding(top = 20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** Home tab: greeting, search, the scan hero, one-tap shortcuts and the most recent documents. */
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
    onSearch: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    val recent = remember(documents) { documents.sortedByDescending { it.updatedAt }.take(RECENT_LIMIT) }
    val favorites = remember(documents) { documents.count { it.isFavorite } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = if (com.example.scanpro.navigation.isWideWindow()) 1100.dp else 720.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Eyebrow(greeting())
                Spacer(Modifier.height(4.dp))
                Text("A little less paper.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                if (documents.isEmpty()) {
                    Text(
                        "Scan, clean up, recognise text and export your first document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(Icons.Rounded.Description, "${documents.size} ${if (documents.size == 1) "document" else "documents"}")
                        if (favorites > 0) StatPill(Icons.Rounded.Star, "$favorites ${if (favorites == 1) "favorite" else "favorites"}")
                        if (trashCount > 0) StatPill(Icons.Rounded.DeleteOutline, "$trashCount in bin")
                    }
                }
            }
        }
        item {
            SearchLauncher(onClick = onSearch, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ScanHeroCard(onStartScan = onStartScan, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Quick actions", modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(10.dp))
                QuickActions(
                    enabled = !busy,
                    actions = listOf(
                        QuickAction(Icons.Rounded.Badge, "ID card", TileTone.Indigo) { onQuickScan(ScanMode.ID_CARD.name) },
                        QuickAction(Icons.AutoMirrored.Rounded.MenuBook, "Book", TileTone.Amber) { onQuickScan(ScanMode.BOOK.name) },
                        QuickAction(Icons.Rounded.TextFields, "Extract text", TileTone.Teal) { onQuickScan(ScanMode.TEXT.name) },
                        QuickAction(Icons.Rounded.QrCodeScanner, "QR code", TileTone.Violet) { onQuickScan(ScanProDestinations.CAMERA_QR) },
                        QuickAction(Icons.AutoMirrored.Rounded.ReceiptLong, "Receipt", TileTone.Rose) { onQuickScan(ScanMode.RECEIPT.name) },
                        QuickAction(Icons.Rounded.Image, "Photos", TileTone.Sky) { onImportImages() },
                        QuickAction(Icons.Rounded.PictureAsPdf, "Import PDF", TileTone.Coral) { onImportPdf() },
                        QuickAction(Icons.Rounded.Handyman, "PDF tools", TileTone.Slate) { onOpenTools() }
                    )
                )
            }
        }
        item {
            Column {
                SectionHeader(
                    "Recent",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    actionLabel = if (documents.size > recent.size) "See all" else null,
                    onAction = onSeeAll
                )
                Spacer(Modifier.height(10.dp))
                if (recent.isEmpty()) {
                    EmptyRecentCard(Modifier.padding(horizontal = 16.dp))
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recent, key = { it.id }) { doc ->
                            DocumentRecentCard(
                                document = doc,
                                selected = doc.id in selected,
                                onClick = { if (selected.isNotEmpty()) onToggleSelection(doc.id) else onOpenDocument(doc.id) },
                                onLongClick = { onToggleSelection(doc.id) }
                            )
                        }
                    }
                }
            }
        }
        item { PrivacyLine(Modifier.fillMaxWidth().padding(top = 2.dp)) }
    }
}

@Composable
private fun StatPill(icon: ImageVector, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A search pill that opens the search screen. Looks like a field; acts like a button. */
@Composable
private fun SearchLauncher(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                "Search titles, tags and text",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The app's promise, kept visible: everything is done and kept on this phone. */
@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Private by design: no account, nothing uploaded",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyRecentCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Description, TileTone.Indigo, size = 48.dp, corner = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Your scans will appear here", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Each one becomes a searchable PDF you can share, sign or export to Word.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The hero: primary entry point on the home screen, on the brand gradient with the mark as a motif. */
@Composable
private fun ScanHeroCard(onStartScan: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .drawWithCache {
                val gradient = Brush.linearGradient(
                    listOf(brand.gradientStart, brand.gradientEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawRect(gradient)
                    // Soft highlight in the top-left corner.
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = size.width * 0.5f),
                        radius = size.width * 0.5f,
                        center = Offset(size.width * 0.1f, 0f)
                    )
                    // The brand glyph, large and faint, running off the right edge.
                    val glyph = size.height * 1.35f
                    translate(left = size.width - glyph * 0.78f, top = (size.height - glyph) / 2f) {
                        drawScanGlyph(
                            glyph,
                            paper = Color.White.copy(alpha = 0.16f),
                            ink = Color.White.copy(alpha = 0.22f),
                            line = brand.scanLine.copy(alpha = 0.85f),
                            bracket = Color.White.copy(alpha = 0.28f)
                        )
                    }
                }
            }
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxWidth(0.72f)) {
            Eyebrow("Smart scan", color = brand.onGradientMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                "Every detail.\nPerfectly captured.",
                style = MaterialTheme.typography.headlineSmall,
                color = brand.onGradient
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Auto-crop. Clean up. Make it searchable.",
                style = MaterialTheme.typography.bodyMedium,
                color = brand.onGradientMuted
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStartScan,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = brand.gradientStart,
                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                    disabledContentColor = brand.gradientStart.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start scanning", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private class QuickAction(val icon: ImageVector, val label: String, val tone: TileTone, val onClick: () -> Unit)

/** One-tap shortcuts for the jobs people do most, each opening the right mode or tool directly. */
@Composable
private fun QuickActions(enabled: Boolean, actions: List<QuickAction>) {
    // Two rows of four on phones, all on screen: a sideways-scrolling row hid half the shortcuts with no
    // hint they were there. Wide windows have room for a single row.
    val perRow = if (com.example.scanpro.navigation.isWideWindow()) 8 else 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(enabled = enabled, role = Role.Button, onClick = action.onClick)
                            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        IconTile(action.icon, action.tone, size = 44.dp, corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            lineHeight = MaterialTheme.typography.labelMedium.lineHeight
                        )
                    }
                }
                // A short last row keeps its cells the same width as the rows above.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
