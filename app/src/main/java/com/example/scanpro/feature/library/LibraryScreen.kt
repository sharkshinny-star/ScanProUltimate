package com.example.scanpro.feature.library

import androidx.compose.material.icons.rounded.Tag
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.rounded.LockOpen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.unit.sp
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

@Composable
fun LibraryRoute(
    mode: String,
    snackbarHostState: SnackbarHostState,
    onOpenDocument: (String) -> Unit,
    /** Opens the camera; Files passes the folder it is showing, so the scan is filed there (blank: none). */
    onStartScan: (folder: String) -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onTrash: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onTab: (String) -> Unit,
    onQuickScan: (String) -> Unit = {},
    onOpenTools: () -> Unit = {},
    onKeptScans: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val tag by viewModel.tag.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    // Home shows the few newest documents from the overview; only the Files and Search lists page the database.
    val items = if (mode == ScanProDestinations.HOME) null else viewModel.documents.collectAsLazyPagingItems()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val keptScanCount by viewModel.keptScanCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyLabel by viewModel.busyLabel.collectAsStateWithLifecycle()
    val savedGrid by viewModel.libraryGrid.collectAsStateWithLifecycle()
    val lockedFolders by viewModel.lockedFolders.collectAsStateWithLifecycle()
    val hiddenFolders by viewModel.hiddenFolders.collectAsStateWithLifecycle()
    val homeTips by viewModel.homeTips.collectAsStateWithLifecycle()
    val movedAside by viewModel.movedAsideLibrary.collectAsStateWithLifecycle()
    if (movedAside != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::deferMovedAsideRestore,
            title = { Text(stringResource(R.string.lib_restore_aside_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.lib_restore_aside_body))
                    TextButton(onClick = viewModel::declineMovedAside) { Text(stringResource(R.string.lib_restore_aside_never)) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::restoreMovedAside) { Text(stringResource(R.string.lib_restore_aside_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::deferMovedAsideRestore) { Text(stringResource(R.string.lib_restore_aside_later)) } }
        )
    }
    LibraryScreen(
        mode = mode,
        overview = overview,
        items = items,
        folder = folder,
        favoritesOnly = favoritesOnly,
        sort = sort,
        onFolderChange = viewModel::setFolder,
        lockedFolders = lockedFolders,
        hiddenFolders = hiddenFolders,
        onSetFolderLocked = viewModel::setFolderLocked,
        onUnlockFolder = viewModel::unlockFolder,
        onFavoritesOnlyChange = viewModel::setFavoritesOnly,
        tag = tag,
        onTagChange = viewModel::setTag,
        savedGrid = savedGrid,
        onSortChange = viewModel::setSort,
        sortAscending = sortAscending,
        onSortAscendingChange = viewModel::setSortAscending,
        recentSearches = recentSearches,
        onRecordSearch = viewModel::recordSearch,
        onClearRecentSearches = viewModel::clearRecentSearches,
        onGridChange = viewModel::setLibraryGrid,
        onStop = viewModel::stopCurrent,
        onMoveToFolder = viewModel::moveToFolder,
        onSetFavorite = viewModel::setFavorite,
        onDeleteFolder = viewModel::deleteFolder,
        folderExists = viewModel::folderExists,
        trashCount = trashCount,
        keptScanCount = keptScanCount,
        onKeptScans = onKeptScans,
        query = query,
        busy = busy,
        busyLabel = busyLabel,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::setQuery,
        // Opening a result from Search keeps the term for next time (R35).
        onOpenDocument = { id -> if (mode == ScanProDestinations.SEARCH && query.isNotBlank()) viewModel.recordSearch(query); onOpenDocument(id) },
        onStartScan = { onStartScan(if (mode == ScanProDestinations.FILES) folder else "") },
        onImportImages = onImportImages,
        onImportPdf = onImportPdf,
        onTrash = onTrash,
        onSearch = onSearch,
        onBack = onBack,
        onBulkDelete = viewModel::moveManyToTrash,
        onShareSelected = { ids -> viewModel.withFreshPdfs(ids) { docs -> sharePdfs(context, docs) } },
        onShareCombined = { ids ->
            viewModel.combinePdfs(ids) { file -> com.example.scanpro.core.ui.shareFile(context, file.absolutePath, "application/pdf", file.name) }
        },
        onRenameFolder = viewModel::renameFolder,
        onTab = onTab,
        onQuickScan = onQuickScan,
        onOpenTools = onOpenTools,
        showTips = homeTips,
        onHideTips = viewModel::hideHomeTips,
        onTipAction = { action ->
            when (action) {
                is TipAction.Camera -> onQuickScan(action.mode)
                TipAction.Tools -> onOpenTools()
                TipAction.Search -> onSearch()
                TipAction.Settings -> onTab(ScanProDestinations.SETTINGS)
            }
        }
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
    lockedFolders: Set<String> = emptySet(),
    hiddenFolders: Set<String> = emptySet(),
    onSetFolderLocked: (String, Boolean) -> Unit = { _, _ -> },
    onUnlockFolder: (String) -> Unit = {},
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
    /** Tag filter (R35): the chosen tag, blank for none; the tags in use come from [overview]. */
    tag: String = "",
    onTagChange: (String) -> Unit = {},
    /** Sort direction and recent searches (R35). */
    sortAscending: Boolean = sort.defaultAscending,
    onSortAscendingChange: (Boolean) -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onRecordSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onGridChange: (Boolean) -> Unit = {},
    onStop: () -> Unit = {},
    onMoveToFolder: (Set<String>, String) -> Unit = { _, _ -> },
    onSetFavorite: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    folderExists: suspend (String) -> Boolean = { false },
    onShareSelected: (Set<String>) -> Unit,
    /** Shares the selection as one combined PDF, in selection order. */
    onShareCombined: (List<String>) -> Unit = {},
    onRenameFolder: (String, String) -> Unit,
    onTab: (String) -> Unit,
    onQuickScan: (String) -> Unit = {},
    onOpenTools: () -> Unit = {},
    keptScanCount: Int = 0,
    onKeptScans: () -> Unit = {},
    showTips: Boolean = false,
    onHideTips: () -> Unit = {},
    onTipAction: (TipAction) -> Unit = {}
) {
    val isHome = mode == ScanProDestinations.HOME
    val isSearch = mode == ScanProDestinations.SEARCH
    // Files keeps the saved layout; Search is always a list. The saved sort orders both.
    val gridMode = mode == ScanProDestinations.FILES && savedGrid
    var moveToFolderOpen by rememberSaveable { mutableStateOf(false) }
    // The document whose card ⋮ asked to move it; null when no such dialog is open.
    var movingDocument by rememberSaveable { mutableStateOf<String?>(null) }
    fun cardMenu(doc: DocumentSummary) = DocumentCardMenu(
        onShare = { onShareSelected(setOf(doc.id)) },
        onMoveToFolder = { movingDocument = doc.id },
        onToggleFavourite = { onSetFavorite(setOf(doc.id), !doc.isFavorite) },
        onMoveToRecycleBin = { onBulkDelete(setOf(doc.id)) }
    )
    var deleteFolderOpen by rememberSaveable { mutableStateOf(false) }
    var renameMergeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var renameFolderSource by rememberSaveable { mutableStateOf<String?>(null) }
    var lockFolderOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val deviceUnlock = com.example.scanpro.core.security.LocalDeviceUnlock.current
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
    // A new folder, favourites filter or search shows different documents, so a selection made before it would
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
    LaunchedEffect(mode) {
        if (mode == ScanProDestinations.HOME) {
            scrollBehavior.state.contentOffset = 0f
        }
    }
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
                            isHome -> BrandLockup(subtitle = stringResource(com.example.scanpro.core.designsystem.greetingRes()))
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
                            // Several documents: separate PDFs, or one combined PDF in the order they were picked.
                            var shareChoice by remember { mutableStateOf(false) }
                            androidx.compose.foundation.layout.Box {
                                IconButton(onClick = { if (selected.size > 1) shareChoice = true else onShareSelected(selected.toSet()) }) {
                                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.lib_cd_share_selected))
                                }
                                androidx.compose.material3.DropdownMenu(expanded = shareChoice, onDismissRequest = { shareChoice = false }) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_share_separate, selected.size)) },
                                        onClick = { shareChoice = false; onShareSelected(selected.toSet()) }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lib_share_one_pdf)) },
                                        onClick = { shareChoice = false; onShareCombined(selected.toList()) }
                                    )
                                }
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
                                    HorizontalDivider()
                                    // The direction, in the key's own words: Newest / Oldest first, A to Z / Z to A, Largest / Smallest first.
                                    listOf(true to sort.ascendingLabel, false to sort.descendingLabel).forEach { (asc, label) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            trailingIcon = { if (sortAscending == asc) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lib_cd_selected)) },
                                            onClick = {
                                                onSortAscendingChange(asc)
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
                                        val folderLocked = com.example.scanpro.core.security.FolderLock.key(folder) in lockedFolders
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_menu_rename_folder)) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            // A locked folder can be renamed or removed only after the phone's unlock, like removing its lock.
                                            onClick = { moreMenuOpen = false; val name = folder; if (folderLocked) deviceUnlock { renameFolderSource = name } else renameFolderSource = name }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (folderLocked) R.string.lib_menu_unlock_folder else R.string.lib_menu_lock_folder)) },
                                            leadingIcon = { Icon(if (folderLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null) },
                                            onClick = {
                                                moreMenuOpen = false
                                                val name = folder
                                                // Removing a lock needs the phone's unlock too; adding one does not.
                                                if (folderLocked) deviceUnlock { onSetFolderLocked(name, false) } else lockFolderOpen = name
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_menu_remove_folder)) },
                                            leadingIcon = { Icon(Icons.Rounded.FolderDelete, contentDescription = null) },
                                            enabled = !busy,
                                            onClick = { moreMenuOpen = false; if (folderLocked) deviceUnlock { deleteFolderOpen = true } else deleteFolderOpen = true }
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
                    keptScanCount = keptScanCount,
                    onKeptScans = onKeptScans,
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
                    onToggleSelection = ::toggleSelection,
                    showTips = showTips,
                    onHideTips = onHideTips,
                    onTipAction = onTipAction
                )
            } else {
                Column(Modifier.fillMaxSize().widthIn(max = 1100.dp)) {
                    if (isSearch) {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            onSubmit = onRecordSearch,
                            focusRequester = searchFocus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        // Recent searches while the field is empty (R35): one tap searches again.
                        if (query.isBlank() && recentSearches.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.lib_recent_searches), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearRecentSearches) { Text(stringResource(R.string.lib_recent_searches_clear)) }
                            }
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentSearches.forEach { term ->
                                    AssistChip(onClick = { onQueryChange(term); onRecordSearch(term) }, label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                }
                            }
                        }
                    }

                    // Folder/favourite filters only earn their space once there is something to filter.
                    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
                    if (overview.documentCount > 0 && (folders.isNotEmpty() || overview.favoriteCount > 0 || favoritesOnly || overview.tags.isNotEmpty() || tag.isNotBlank())) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                LibraryChip(selected = folder.isBlank() && !favoritesOnly && tag.isBlank(), label = stringResource(R.string.lib_filter_all)) { onFolderChange(""); onFavoritesOnlyChange(false); onTagChange("") }
                            }
                            if (overview.tags.isNotEmpty() || tag.isNotBlank()) item {
                                // One Tags chip; it names the tag while one is chosen and opens the sheet to change it.
                                LibraryChip(
                                    selected = tag.isNotBlank(),
                                    label = tag.ifBlank { stringResource(R.string.lib_filter_tags) },
                                    icon = Icons.Rounded.Tag
                                ) { tagSheetOpen = true }
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
                                    val key = com.example.scanpro.core.security.FolderLock.key(name)
                                    val locked = key in lockedFolders
                                    LibraryChip(
                                        selected = folder.equals(name, ignoreCase = true),
                                        label = name,
                                        icon = if (locked) Icons.Rounded.Lock else Icons.Rounded.Folder
                                    ) {
                                        when {
                                            folder.equals(name, ignoreCase = true) -> onFolderChange("")
                                            // A closed locked folder opens only after the phone's own unlock.
                                            com.example.scanpro.core.security.FolderLock.hides(hiddenFolders, name) -> deviceUnlock { onUnlockFolder(name); onFolderChange(name) }
                                            else -> onFolderChange(name)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (tagSheetOpen) {
                        TagFilterSheet(
                            tags = overview.tags,
                            selected = tag,
                            onSelect = { onTagChange(it); tagSheetOpen = false },
                            onDismiss = { tagSheetOpen = false }
                        )
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
                            items(count = list.itemCount, key = list.itemKey { it.id }, contentType = { "document_grid_card" }) { index ->
                                val doc = list[index] ?: return@items
                                DocumentGridCard(
                                    document = doc,
                                    selected = doc.id in selected,
                                    onClick = { if (selecting) toggleSelection(doc.id) else onOpenDocument(doc.id) },
                                    onLongClick = { toggleSelection(doc.id) },
                                    menu = if (selecting) null else cardMenu(doc)
                                )
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(count = list.itemCount, key = list.itemKey { it.id }, contentType = { "document_list_card" }) { index ->
                                val doc = list[index] ?: return@items
                                DocumentListCard(
                                    document = doc,
                                    selected = doc.id in selected,
                                    onClick = { if (selecting) toggleSelection(doc.id) else onOpenDocument(doc.id) },
                                    onLongClick = { toggleSelection(doc.id) },
                                    menu = if (selecting) null else cardMenu(doc)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    lockFolderOpen?.let { name ->
        // Said plainly before locking: this hides the folder inside the app; it does not encrypt the files.
        ConfirmDialog(
            title = stringResource(R.string.lib_lock_folder_title, name),
            message = stringResource(R.string.lib_lock_folder_message),
            confirmLabel = stringResource(R.string.lib_menu_lock_folder),
            onDismiss = { lockFolderOpen = null },
            onConfirm = {
                lockFolderOpen = null
                onSetFolderLocked(name, true)
            }
        )
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
    // One document moved from its card's ⋮ menu.
    movingDocument?.let { id ->
        FolderPickerDialog(
            title = pluralStringResource(R.plurals.lib_move_documents_title, 1, 1),
            confirmLabel = stringResource(R.string.lib_move),
            folders = folders,
            onDismiss = { movingDocument = null },
            onConfirm = { target -> movingDocument = null; onMoveToFolder(setOf(id), target) }
        )
    }
    if (moveToFolderOpen) {
        FolderPickerDialog(
            title = pluralStringResource(R.plurals.lib_move_documents_title, selected.size, selected.size),
            confirmLabel = stringResource(R.string.lib_move),
            folders = folders,
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

/** The tags in use as chips, one selectable, plus All tags to clear the filter (R35). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterSheet(tags: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.lib_filter_tags), style = MaterialTheme.typography.titleLarge)
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected.isBlank(), onClick = { onSelect("") }, label = { Text(stringResource(R.string.lib_filter_all_tags)) })
                tags.forEach { t ->
                    FilterChip(selected = t.equals(selected, ignoreCase = true), onClick = { onSelect(t) }, label = { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    }
}

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
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSubmit: (String) -> Unit, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit(query) }),
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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


/** Home tab: search, four shortcuts and a grid of the most recent documents; the scan card only while the library is empty. */
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
    onToggleSelection: (String) -> Unit,
    keptScanCount: Int = 0,
    onKeptScans: () -> Unit = {},
    showTips: Boolean = false,
    onHideTips: () -> Unit = {},
    onTipAction: (TipAction) -> Unit = {}
) {
    val recent = overview.recent
    val documentCount = overview.documentCount
    val wide = com.example.scanpro.navigation.isWideWindow()
    // R42: a plain `remember`, not the saved state LazyColumn creates by default. Coming back to Home from another
    // tab restored a scroll offset of about 14 dp that hid the search pill's top; Home now always opens at the top.
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }

    // Keep the search launcher unclipped at the top unless the user has actively scrolled.
    // An item entering at the bottom (e.g. tips card) must never pull the list down.
    // Not saved, like the list state: Home opens at the top on each visit, so the safety net below is armed again too.
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userScrolled = true
    }
    LaunchedEffect(showTips, documentCount, recent.size) {
        if (!userScrolled && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0, 0)
        }
    }
    // Not remember(selected): the list is one SnapshotStateList instance, so that key never changes and the
    // set stayed empty, leaving selected cards unhighlighted. Reading it here re-runs this on every change.
    val selectedSet = selected.toSet()
    val columns = if (wide) 4 else 2
    val chunkedRecent = remember(recent, columns) { recent.chunked(columns) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().widthIn(max = if (wide) 1100.dp else 720.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = if (wide) 32.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 12.dp)
    ) {
        item(key = "search", contentType = "search") {
            SearchLauncher(onClick = onSearch, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (keptScanCount > 0) {
            item(key = "kept_scans", contentType = "kept_scans") {
                KeptScansBanner(count = keptScanCount, onClick = onKeptScans, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        // The promo card is the empty state only: once there are documents, the camera button is the call to action.
        if (documentCount == 0) {
            item(key = "hero", contentType = "hero") {
                ScanHeroCard(onStartScan = onStartScan, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item(key = "quick_actions", contentType = "quick_actions") {
            // Four shortcuts, one row. Text, QR, Receipt and Photos live in the camera's mode strip and the Files menu.
            QuickActions(
                enabled = !busy,
                actions = listOf(
                    QuickAction(Icons.Rounded.Badge, stringResource(R.string.lib_quick_id_card), TileTone.Indigo) { onQuickScan(ScanMode.ID_CARD.name) },
                    QuickAction(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.lib_quick_book), TileTone.Amber) { onQuickScan(ScanMode.BOOK.name) },
                    QuickAction(Icons.Rounded.PictureAsPdf, stringResource(R.string.lib_menu_import_pdf), TileTone.Coral) { onImportPdf() },
                    QuickAction(Icons.Rounded.Handyman, stringResource(R.string.lib_quick_pdf_tools), TileTone.Slate) { onOpenTools() }
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (recent.isNotEmpty()) {
            item(key = "recent_header", contentType = "recent_header") {
                SectionHeader(
                    stringResource(R.string.lib_sort_recent),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    actionLabel = if (documentCount > recent.size) stringResource(R.string.lib_see_all) else null,
                    onAction = onSeeAll
                )
            }
            // A grid of large thumbnails, like the leading scanner apps' home screens: two columns on phones.
            items(chunkedRecent, key = { row -> row.first().id }, contentType = { "recent_row" }) { row ->
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { doc ->
                        DocumentRecentCard(
                            document = doc,
                            selected = doc.id in selectedSet,
                            onClick = { if (selected.isNotEmpty()) onToggleSelection(doc.id) else onOpenDocument(doc.id) },
                            onLongClick = { onToggleSelection(doc.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (showTips) {
            item(key = "tips", contentType = "tips") {
                HomeTipCard(
                    onAction = onTipAction,
                    onHide = onHideTips,
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(placementSpec = null)
                )
            }
        }
    }
}

/** Shown on Home while a camera scan could not be saved; its photos wait under "Unsaved scans". */
@Composable
private fun KeptScansBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.kept_banner_title, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.kept_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
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
        // At least 52dp, taller if the font needs it; one line, so at a large font size the hint ends in … instead
        // of wrapping and having its second line cut off by a fixed height.
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.lib_home_search_launcher),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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


/** The hero: primary entry point on the home screen, on the brand gradient with the mark as a motif. */
@Composable
private fun ScanHeroCard(onStartScan: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    // A scan beam sweeps down the page glyph and back, like the scanner finding a page. Read while drawing,
    // so it never recomposes the card; still when animations are off.
    val motion = com.example.scanpro.core.designsystem.rememberMotionEnabled()
    val beam = androidx.compose.animation.core.rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2_400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "beam"
    )
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
                    // Kept to the right third so the faint glyph (and its scan line) never runs behind the text.
                    // Capped by the width too: a large font makes the card taller, and a glyph sized by height alone
                    // then grew leftwards behind the text and the button.
                    val glyph = minOf(size.height * 1.05f, size.width * 0.75f)
                    val left = size.width - glyph * 0.62f
                    val top = (size.height - glyph) / 2f
                    translate(left = left, top = top) {
                        drawScanGlyph(
                            glyph,
                            paper = Color.White.copy(alpha = 0.16f),
                            ink = Color.White.copy(alpha = 0.22f),
                            // With motion the moving beam below replaces the glyph's fixed line.
                            line = if (motion) Color.Transparent else brand.scanLine.copy(alpha = 0.85f),
                            bracket = Color.White.copy(alpha = 0.28f)
                        )
                    }
                    if (motion) {
                        val u = glyph / 108f
                        val y = top + (30f + 48f * beam.value) * u
                        // A soft wake behind the beam, then the beam itself across the page.
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, brand.scanLine.copy(alpha = 0.28f)),
                                startY = y - 14f * u,
                                endY = y
                            ),
                            topLeft = Offset(left + 30f * u, y - 14f * u),
                            size = androidx.compose.ui.geometry.Size(48f * u, 14f * u)
                        )
                        drawLine(
                            brand.scanLine.copy(alpha = 0.9f),
                            Offset(left + 24f * u, y),
                            Offset(left + 84f * u, y),
                            strokeWidth = 5f * u,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxWidth(0.68f)) {
            Eyebrow(stringResource(R.string.lib_hero_eyebrow), color = brand.onGradientMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.lib_hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = brand.onGradient
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.lib_hero_body),
                style = MaterialTheme.typography.bodySmall,
                color = brand.onGradientMuted
            )
            Spacer(Modifier.height(10.dp))
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                // One line: at a large font size the label shrinks to fit rather than breaking onto two lines.
                val labelStyle = MaterialTheme.typography.labelLarge
                Text(
                    stringResource(R.string.lib_hero_start_scanning),
                    style = labelStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = labelStyle.fontSize)
                )
            }
        }
    }
}

private class QuickAction(val icon: ImageVector, val label: String, val tone: TileTone, val onClick: () -> Unit)

/** One-tap shortcuts for the jobs people do most, each opening the right mode or tool directly. */
@Composable
private fun QuickActions(enabled: Boolean, actions: List<QuickAction>, modifier: Modifier = Modifier) {
    // All on screen in rows of four (eight on wide windows): a sideways-scrolling row hid shortcuts with no hint.
    val perRow = if (com.example.scanpro.navigation.isWideWindow()) 8 else 4
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        IconTile(action.icon, action.tone, size = 40.dp, corner = 12.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
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
