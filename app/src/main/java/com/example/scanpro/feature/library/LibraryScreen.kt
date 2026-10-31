package com.example.scanpro.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.ConfirmDialog
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.scanpro.R
import com.example.scanpro.data.DocumentSummary
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.data.ScanMode
import com.example.scanpro.navigation.ScanProBottomBar
import com.example.scanpro.navigation.ScanProDestinations
import java.util.Calendar

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
    val savedGrid by viewModel.libraryGrid.collectAsStateWithLifecycle()
    LibraryScreen(
        mode = mode,
        overview = overview,
        items = items,
        folder = folder,
        favoritesOnly = favoritesOnly,
        sort = sort,
        onFolderChange = viewModel::setFolder,
        onFavoritesOnlyChange = viewModel::setFavoritesOnly,
        savedGrid = savedGrid,
        onSortChange = viewModel::setSort,
        onGridChange = viewModel::setLibraryGrid,
        onStop = viewModel::stopCurrent,
        onMoveToFolder = viewModel::moveToFolder,
        onSetFavorite = viewModel::setFavorite,
        onDeleteFolder = viewModel::deleteFolder,
        folderExists = viewModel::folderExists,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onBulkDelete: (Set<String>) -> Boolean,
    savedGrid: Boolean = true,
    onSortChange: (LibrarySort) -> Unit = {},
    onGridChange: (Boolean) -> Unit = {},
    onStop: () -> Unit = {},
    onMoveToFolder: (Set<String>, String) -> Unit = { _, _ -> },
    onSetFavorite: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    folderExists: suspend (String) -> Boolean = { false },
    onShareSelected: (Set<String>) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onTab: (String) -> Unit,
    onQuickScan: (String) -> Unit = {},
    onOpenTools: () -> Unit = {}
) {
    val isHome = mode == ScanProDestinations.HOME
    val isSearch = mode == ScanProDestinations.SEARCH
    // Files keeps the saved layout; Search is always a list. The saved sort orders both.
    val gridMode = mode == ScanProDestinations.FILES && savedGrid
    var moveToFolderOpen by rememberSaveable { mutableStateOf(false) }
    var deleteFolderOpen by rememberSaveable { mutableStateOf(false) }
    var renameMergeTarget by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var renameFolderSource by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val selected = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })) { mutableStateListOf<String>() }
    // One chip per folder whatever the case of its name.
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

    // The documents on screen: Home's recents, or the pages of the list loaded so far. Select all and the
    // favourite action work on these.
    val visible: List<DocumentSummary> = if (isHome) overview.recent else items?.itemSnapshotList?.items.orEmpty()

    // Home's selection is limited to the documents it shows.
    val homeIds = remember(overview) { overview.recent.mapTo(HashSet()) { it.id } }
    LaunchedEffect(homeIds) {
        if (isHome) selected.removeAll { it !in homeIds }
    }
    // A new folder, favorites filter or search shows different documents, so a selection made before it would
    // act on items no longer on screen. The last filters are saved with the selection, so a rotation keeps it.
    // (A new sort only reorders the same documents, and the saved sort loads asynchronously.)
    val filterKey = "$folder|$favoritesOnly|$query"
    var selectionFilterKey by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (selectionFilterKey != filterKey) {
            selected.clear()
            selectionFilterKey = filterKey
        }
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
            TopBarWithProgress(busy, onStop = onStop) {
                TopAppBar(
                    colors = topBarColors,
                    scrollBehavior = scrollBehavior,
                    title = {
                        when {
                            selecting -> TitleWithStatus(pluralStringResource(R.plurals.lib_title_selected_count, selected.size, selected.size), if (busy) busyLabel else null)
                            isHome -> BrandLockup()
                            isSearch -> Text(stringResource(R.string.common_search))
                            else -> TitleWithStatus(
                                stringResource(R.string.lib_title_files),
                                when {
                                    busy -> busyLabel
                                    overview.documentCount == 0 -> null
                                    else -> pluralStringResource(R.plurals.lib_document_count, overview.documentCount, overview.documentCount)
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        if (selecting) {
                            IconButton(onClick = { selected.clear() }) {
                                Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.lib_cd_clear_selection))
                            }
                        } else if (isSearch) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                        }
                    },
                    actions = {
                        if (selecting) {
                            IconButton(onClick = { onShareSelected(selected.toSet()) }) {
                                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.lib_cd_share_selected))
                            }
                            IconButton(onClick = {
                                // The selection stays if the app was busy and nothing moved.
                                if (onBulkDelete(selected.toSet())) selected.clear()
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.lib_cd_move_selected_to_trash))
                            }
                            var selectionMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { selectionMenuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.lib_cd_more_selection_actions))
                                }
                                DropdownMenu(expanded = selectionMenuOpen, onDismissRequest = { selectionMenuOpen = false }) {
                                    val allSelected = visible.all { it.id in selected }
                                    DropdownMenuItem(
                                        text = { Text(if (allSelected) stringResource(R.string.lib_cd_clear_selection) else stringResource(R.string.lib_select_all_count, visible.size)) },
                                        leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                                        onClick = {
                                            selectionMenuOpen = false
                                            if (allSelected) selected.clear() else visible.forEach { if (it.id !in selected) selected.add(it.id) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_menu_move_to_folder)) },
                                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = { selectionMenuOpen = false; moveToFolderOpen = true }
                                    )
                                    // Selected documents not in the loaded pages count as unstarred, so the action stars them.
                                    val loadedFavorites = visible.filter { it.isFavorite }.mapTo(HashSet()) { it.id }
                                    val anyUnstarred = selected.any { it !in loadedFavorites }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (anyUnstarred) R.string.lib_menu_add_to_favorites else R.string.lib_menu_remove_from_favorites)) },
                                        leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = {
                                            selectionMenuOpen = false
                                            onSetFavorite(selected.toSet(), anyUnstarred); selected.clear()
                                        }
                                    )
                                }
                            }
                        } else {
                            if (!isSearch && !isHome) {
                                IconButton(onClick = onSearch) {
                                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.common_search))
                                }
                            }
                            if (!isHome) Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.lib_cd_sort))
                                }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    LibrarySort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.label)) },
                                            trailingIcon = { if (sort == option) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lib_cd_selected)) },
                                            onClick = {
                                                onSortChange(option)
                                                sortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (mode == ScanProDestinations.FILES) IconButton(onClick = { onGridChange(!gridMode) }) {
                                Icon(
                                    if (gridMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = stringResource(if (gridMode) R.string.lib_cd_show_as_list else R.string.lib_cd_show_as_grid)
                                )
                            }
                            if (!isSearch) Box {
                                IconButton(onClick = { moreMenuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.lib_cd_more_options))
                                }
                                DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_menu_import_photos)) },
                                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = { moreMenuOpen = false; onImportImages() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_menu_import_pdf)) },
                                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = { moreMenuOpen = false; onImportPdf() }
                                    )
                                    if (mode == ScanProDestinations.FILES && folder.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_menu_rename_folder)) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = { moreMenuOpen = false; renameFolderSource = folder }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_menu_remove_folder)) },
                                            leadingIcon = { Icon(Icons.Rounded.FolderDelete, contentDescription = null) },
                                            enabled = !busy,
                                            onClick = { moreMenuOpen = false; deleteFolderOpen = true }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (trashCount > 0) stringResource(R.string.lib_menu_recycle_bin_count, trashCount) else stringResource(R.string.lib_menu_recycle_bin)) },
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
                    overview = overview,
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
                    if (overview.documentCount > 0 && (folders.isNotEmpty() || overview.favoriteCount > 0 || favoritesOnly)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                LibraryChip(selected = folder.isBlank() && !favoritesOnly, label = stringResource(R.string.lib_filter_all)) { onFolderChange(""); onFavoritesOnlyChange(false) }
                            }
                            item {
                                LibraryChip(
                                    selected = favoritesOnly,
                                    label = stringResource(R.string.lib_filter_favorites),
                                    icon = if (favoritesOnly) Icons.Rounded.Star else Icons.Outlined.StarOutline
                                ) { onFavoritesOnlyChange(!favoritesOnly) }
                            }
                            folders.forEach { name ->
                                item(key = "folder_$name") {
                                    LibraryChip(selected = folder.equals(name, ignoreCase = true), label = name, icon = Icons.Rounded.Folder) {
                                        onFolderChange(if (folder.equals(name, ignoreCase = true)) "" else name)
                                    }
                                }
                            }
                        }
                    }

                    val list = items
                    val loading = list == null || list.loadState.refresh is LoadState.Loading
                    when {
                        // First page on its way: nothing to say yet, and no "empty" message that would flash.
                        list == null || (list.itemCount == 0 && loading) -> Box(Modifier.fillMaxSize())
                        list.itemCount == 0 -> EmptyLibrary(
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
                            items(count = list.itemCount, key = list.itemKey { it.id }) { index ->
                                val doc = list[index] ?: return@items
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
                            items(count = list.itemCount, key = list.itemKey { it.id }) { index ->
                                val doc = list[index] ?: return@items
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
            title = stringResource(R.string.lib_menu_rename_folder),
            initial = source,
            label = stringResource(R.string.lib_rename_folder_label),
            confirmLabel = stringResource(R.string.common_rename),
            onDismiss = { renameFolderSource = null }
        ) { target ->
            val clean = target.trim().take(80)
            scope.launch {
                val merges = !clean.equals(source, ignoreCase = true) && folderExists(clean)
                if (merges) {
                    renameMergeTarget = clean
                } else {
                    onRenameFolder(source, clean); onFolderChange(clean)
                    renameFolderSource = null
                }
            }
        }
    }
    renameMergeTarget?.let { target ->
        val source = renameFolderSource.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.lib_merge_folder_title, target),
            message = stringResource(R.string.lib_merge_folder_message, target, source),
            confirmLabel = stringResource(R.string.lib_merge_folder_confirm),
            onDismiss = { renameMergeTarget = null },
            onConfirm = {
                renameMergeTarget = null
                onRenameFolder(source, target); onFolderChange(target)
                renameFolderSource = null
            }
        )
    }
    if (deleteFolderOpen && folder.isNotBlank()) {
        ConfirmDialog(
            title = stringResource(R.string.lib_remove_folder_title, folder),
            message = stringResource(R.string.lib_remove_folder_message),
            confirmLabel = stringResource(R.string.lib_menu_remove_folder),
            onDismiss = { deleteFolderOpen = false },
            onConfirm = {
                deleteFolderOpen = false
                onDeleteFolder(folder); onFolderChange("")
            }
        )
    }
    if (moveToFolderOpen) {
        MoveToFolderDialog(
            folders = folders,
            count = selected.size,
            onDismiss = { moveToFolderOpen = false },
            onConfirm = { target ->
                moveToFolderOpen = false
                onMoveToFolder(selected.toSet(), target); selected.clear()
            }
        )
    }
}

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
        placeholder = { Text(stringResource(R.string.lib_search_field_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.lib_cd_clear_search))
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
                    query.isNotBlank() -> stringResource(R.string.lib_empty_no_matching)
                    favoritesOnly -> stringResource(R.string.lib_empty_no_favorites_yet)
                    folder.isNotBlank() -> stringResource(R.string.lib_empty_folder_is_empty)
                    else -> stringResource(R.string.lib_empty_library_is_empty)
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    query.isNotBlank() -> stringResource(R.string.lib_search_hint)
                    favoritesOnly -> stringResource(R.string.lib_empty_favorites_hint)
                    folder.isNotBlank() -> stringResource(R.string.lib_empty_folder_hint)
                    else -> stringResource(R.string.lib_empty_hint)
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
                        Text(stringResource(R.string.lib_scan))
                    }
                    FilledTonalButton(onClick = onImportImages, enabled = !busy) { Text(stringResource(R.string.lib_photos)) }
                    FilledTonalButton(onClick = onImportPdf, enabled = !busy) { Text(stringResource(R.string.lib_pdf)) }
                }
                PrivacyLine(Modifier.padding(top = 20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
private fun greeting(): String = stringResource(
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.lib_greeting_morning
        in 12..16 -> R.string.lib_greeting_afternoon
        else -> R.string.lib_greeting_evening
    }
)

/** Home tab: greeting, search, the scan hero, one-tap shortcuts and the most recent documents. */
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
    onSearch: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    val recent = overview.recent
    val documentCount = overview.documentCount
    val favorites = overview.favoriteCount
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = if (com.example.scanpro.navigation.isWideWindow()) 1100.dp else 720.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Eyebrow(greeting())
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.lib_home_headline), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                if (documentCount == 0) {
                    Text(
                        stringResource(R.string.lib_home_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(Icons.Rounded.Description, pluralStringResource(R.plurals.lib_document_count, documentCount, documentCount))
                        if (favorites > 0) StatPill(Icons.Rounded.Star, pluralStringResource(R.plurals.lib_favorite_count, favorites, favorites))
                        if (trashCount > 0) StatPill(Icons.Rounded.DeleteOutline, pluralStringResource(R.plurals.lib_in_bin_count, trashCount, trashCount))
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
                SectionHeader(stringResource(R.string.lib_quick_actions), modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(10.dp))
                QuickActions(
                    enabled = !busy,
                    actions = listOf(
                        QuickAction(Icons.Rounded.Badge, stringResource(R.string.lib_quick_id_card), TileTone.Indigo) { onQuickScan(ScanMode.ID_CARD.name) },
                        QuickAction(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.lib_quick_book), TileTone.Amber) { onQuickScan(ScanMode.BOOK.name) },
                        QuickAction(Icons.Rounded.TextFields, stringResource(R.string.lib_quick_extract_text), TileTone.Teal) { onQuickScan(ScanMode.TEXT.name) },
                        QuickAction(Icons.Rounded.QrCodeScanner, stringResource(R.string.lib_quick_qr_code), TileTone.Violet) { onQuickScan(ScanProDestinations.CAMERA_QR) },
                        QuickAction(Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.lib_quick_receipt), TileTone.Rose) { onQuickScan(ScanMode.RECEIPT.name) },
                        QuickAction(Icons.Rounded.Image, stringResource(R.string.lib_photos), TileTone.Sky) { onImportImages() },
                        QuickAction(Icons.Rounded.PictureAsPdf, stringResource(R.string.lib_menu_import_pdf), TileTone.Coral) { onImportPdf() },
                        QuickAction(Icons.Rounded.Handyman, stringResource(R.string.lib_quick_pdf_tools), TileTone.Slate) { onOpenTools() }
                    )
                )
            }
        }
        item {
            Column {
                SectionHeader(
                    stringResource(R.string.lib_sort_recent),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    actionLabel = if (documentCount > recent.size) stringResource(R.string.lib_see_all) else null,
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
                stringResource(R.string.lib_home_search_launcher),
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
            stringResource(R.string.lib_privacy_line),
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
                Text(stringResource(R.string.lib_home_empty_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.lib_home_empty_body),
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
            Eyebrow(stringResource(R.string.lib_hero_eyebrow), color = brand.onGradientMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lib_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                color = brand.onGradient
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lib_hero_body),
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
                Text(stringResource(R.string.lib_hero_start_scanning), fontWeight = FontWeight.Bold)
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

/** Picks an existing folder, types a new one, or takes the documents out of any folder. */
@Composable
private fun MoveToFolderDialog(folders: List<String>, count: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.lib_move_documents_title, count, count)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.lib_rename_folder_label)) },
                    placeholder = { Text(stringResource(R.string.lib_folder_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(folders, key = { it }) { existing ->
                            androidx.compose.material3.TextButton(onClick = { name = existing }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Folder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(existing, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.lib_move)) }
        },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(onClick = { onConfirm("") }) { Text(stringResource(R.string.lib_no_folder)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}
