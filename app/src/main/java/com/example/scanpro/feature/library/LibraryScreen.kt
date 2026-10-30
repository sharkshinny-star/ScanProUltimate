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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.ui.sharePdfs
import com.example.scanpro.navigation.ScanProBottomBar
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.core.designsystem.TitleWithStatus
import com.example.scanpro.core.designsystem.TopBarWithProgress

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

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
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    // Home shows the few newest documents from the overview; only the Files and Search lists page the database.
    val items = if (mode == ScanProDestinations.HOME) null else viewModel.documents.collectAsLazyPagingItems()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    LibraryScreen(
        mode = mode,
        overview = overview,
        items = items,
        folder = folder,
        favoritesOnly = favoritesOnly,
        sort = sort,
        onFolderChange = viewModel::setFolder,
        onFavoritesOnlyChange = viewModel::setFavoritesOnly,
        onSortChange = viewModel::setSort,
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
    overview: LibraryOverview,
    items: LazyPagingItems<DocumentSummary>?,
    folder: String,
    favoritesOnly: Boolean,
    sort: LibrarySort,
    onFolderChange: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
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
    var renameFolderSource by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val folders = overview.folders

    // A folder filter whose folder no longer exists (renamed, or its documents trashed) would hide
    // everything with no chip left to clear it.
    // Only on Files, and only once documents have loaded: an empty overview just after launch doesn't
    // mean the folder is gone.
    LaunchedEffect(folders) {
        if (mode == ScanProDestinations.FILES && overview.documentCount > 0 && folder.isNotBlank() &&
            folders.none { it.equals(folder, ignoreCase = true) }
        ) onFolderChange("")
    }

    // Home's selection is limited to the documents it shows.
    val homeIds = remember(overview) { overview.recent.mapTo(HashSet()) { it.id } }
    LaunchedEffect(homeIds) {
        if (mode == ScanProDestinations.HOME) selected.removeAll { it !in homeIds }
    }
    // A new folder, favorites, sort or search shows different documents, so a selection made before it would
    // act on items no longer on screen.
    LaunchedEffect(folder, favoritesOnly, sort, query) { selected.clear() }

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
                            selected.isNotEmpty() -> stringResource(R.string.lib_title_selected_count, selected.size)
                            mode == ScanProDestinations.FILES -> stringResource(R.string.lib_title_files)
                            isSearch -> stringResource(R.string.common_search)
                            else -> stringResource(R.string.lib_title_home)
                        },
                        status = if (busy) busyLabel else null
                    )
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.lib_cd_clear_selection))
                        }
                    } else if (isSearch) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { onShareSelected(selected.toSet()) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.lib_cd_share_selected))
                        }
                        IconButton(onClick = {
                            onBulkDelete(selected.toSet())
                            selected.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.lib_cd_move_selected_to_trash))
                        }
                    } else {
                        if (!isSearch) {
                            IconButton(onClick = onSearch) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                            }
                        }
                        if (!isHome) Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.lib_cd_sort))
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                LibrarySort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.label)) },
                                        trailingIcon = { if (sort == option) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.lib_cd_selected)) },
                                        onClick = {
                                            onSortChange(option)
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        if (!isHome) IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.AutoMirrored.Filled.List else Icons.Default.GridView, contentDescription = stringResource(R.string.lib_cd_toggle_layout))
                        }
                        if (!isSearch) Box {
                            IconButton(onClick = { moreMenuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.lib_cd_more_options))
                            }
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lib_menu_import_photos)) },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    enabled = !busy,
                                    onClick = { moreMenuOpen = false; onImportImages() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lib_menu_import_pdf)) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    enabled = !busy,
                                    onClick = { moreMenuOpen = false; onImportPdf() }
                                )
                                if (mode == ScanProDestinations.FILES && folder.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_menu_rename_folder)) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = { moreMenuOpen = false; renameFolderSource = folder }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (trashCount > 0) stringResource(R.string.lib_menu_recycle_bin_count, trashCount) else stringResource(R.string.lib_menu_recycle_bin)) },
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
                    text = { Text(stringResource(R.string.lib_scan)) }
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
                    overview = overview,
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
                        placeholder = { Text(stringResource(R.string.lib_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.lib_cd_clear_search))
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Folder/favorite filters only earn their space once there is something to filter.
                if (overview.documentCount > 0 && (folders.isNotEmpty() || overview.favoriteCount > 0 || favoritesOnly)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = folder.isBlank() && !favoritesOnly,
                                onClick = { onFolderChange(""); onFavoritesOnlyChange(false) },
                                label = { Text(stringResource(R.string.lib_filter_all)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                                label = { Text(stringResource(R.string.lib_filter_favorites)) },
                                leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline, null) }
                            )
                        }
                        folders.forEach { name ->
                            item(key = "folder_$name") {
                                FilterChip(
                                    selected = folder == name,
                                    onClick = { onFolderChange(if (folder == name) "" else name) },
                                    label = { Text(name, maxLines = 1) },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                val list = items
                val loading = list == null || list.loadState.refresh is LoadState.Loading
                if (list == null || (list.itemCount == 0 && loading)) {
                    // First page on its way: nothing to say yet, and no "empty" message that would flash.
                    Box(Modifier.fillMaxSize())
                } else if (list.itemCount == 0) {
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
                                    query.isNotBlank() -> stringResource(R.string.lib_empty_no_matching)
                                    favoritesOnly -> stringResource(R.string.lib_empty_no_favorites)
                                    folder.isNotBlank() -> stringResource(R.string.lib_empty_no_documents_in_folder)
                                    else -> stringResource(R.string.lib_empty_no_documents)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (query.isBlank() && !favoritesOnly && folder.isBlank()) {
                                Text(
                                    stringResource(R.string.lib_empty_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    Button(onClick = onStartScan, enabled = !busy) { Text(stringResource(R.string.lib_scan)) }
                                    FilledTonalButton(onClick = onImportImages, enabled = !busy) { Text(stringResource(R.string.lib_photos)) }
                                    FilledTonalButton(onClick = onImportPdf, enabled = !busy) { Text(stringResource(R.string.lib_pdf)) }
                                }
                                PrivacyLine(Modifier.padding(top = 16.dp))
                            } else if (query.isNotBlank()) {
                                Text(
                                    stringResource(R.string.lib_search_hint),
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
                        items(count = list.itemCount, key = list.itemKey { it.id }) { index ->
                            val doc = list[index] ?: return@items
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
                        items(count = list.itemCount, key = list.itemKey { it.id }) { index ->
                            val doc = list[index] ?: return@items
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
            title = stringResource(R.string.lib_menu_rename_folder),
            initial = source,
            label = stringResource(R.string.lib_rename_folder_label),
            confirmLabel = stringResource(R.string.common_rename),
            onDismiss = { renameFolderSource = null }
        ) { target ->
            onRenameFolder(source, target)
            onFolderChange(target.trim())
            renameFolderSource = null
        }
    }
}

/** Home tab: workspace header, primary scan entry points and the most recent documents. */
@Composable
private fun HomeDashboard(
    overview: LibraryOverview,
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
    val recent = overview.recent
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.lib_home_eyebrow),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.lib_home_headline), style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        overview.documentCount == 0 -> stringResource(R.string.lib_home_first_document)
                        trashCount > 0 -> stringResource(R.string.lib_home_stats_with_trash, overview.documentCount, overview.favoriteCount, trashCount)
                        else -> stringResource(R.string.lib_home_stats, overview.documentCount, overview.favoriteCount)
                    },
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
                    QuickAction(Icons.Default.Badge, stringResource(R.string.lib_quick_id_card)) { onQuickScan(ScanMode.ID_CARD.name) },
                    QuickAction(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.lib_quick_book)) { onQuickScan(ScanMode.BOOK.name) },
                    QuickAction(Icons.Default.TextFields, stringResource(R.string.lib_quick_extract_text)) { onQuickScan(ScanMode.TEXT.name) },
                    QuickAction(Icons.Default.QrCodeScanner, stringResource(R.string.lib_quick_qr_code)) { onQuickScan(ScanProDestinations.CAMERA_QR) },
                    QuickAction(Icons.Default.ReceiptLong, stringResource(R.string.lib_quick_receipt)) { onQuickScan(ScanMode.RECEIPT.name) },
                    QuickAction(Icons.Default.Image, stringResource(R.string.lib_photos)) { onImportImages() },
                    QuickAction(Icons.Default.PictureAsPdf, stringResource(R.string.lib_menu_import_pdf)) { onImportPdf() },
                    QuickAction(Icons.Default.Build, stringResource(R.string.lib_quick_pdf_tools)) { onOpenTools() }
                )
            )
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lib_sort_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (overview.documentCount > recent.size) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.lib_see_all)) }
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
                        Text(stringResource(R.string.lib_home_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.lib_home_empty_body),
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
            stringResource(R.string.lib_privacy_line),
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
                stringResource(R.string.lib_hero_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = caption
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lib_hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lib_hero_body),
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
                Text(stringResource(R.string.lib_hero_start_scanning))
            }
        }
    }
}

private class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** One-tap shortcuts for the jobs people do most, each opening the right mode or tool directly. */
@Composable
private fun QuickActions(enabled: Boolean, actions: List<QuickAction>) {
    // Two rows of four, all on screen: a sideways-scrolling row hid half the shortcuts with no hint they were there.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
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
                // A short last row keeps its cells the same width as the rows above.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
