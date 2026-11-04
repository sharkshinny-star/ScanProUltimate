package com.example.scanpro.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.scanpro.data.LibraryOverview
import com.example.scanpro.data.LibrarySort
import com.example.scanpro.navigation.ScanProDestinations

/** The Home tab with fixed data and no-op actions, for rendering tests outside this package. */
@Composable
fun LibraryScreenForTest(overview: LibraryOverview, showTips: Boolean = false) {
    LibraryScreen(
        mode = ScanProDestinations.HOME,
        overview = overview,
        items = null,
        folder = "",
        favoritesOnly = false,
        sort = LibrarySort.UPDATED,
        onFolderChange = {},
        onFavoritesOnlyChange = {},
        trashCount = 2,
        query = "",
        busy = false,
        busyLabel = null,
        snackbarHostState = remember { SnackbarHostState() },
        onQueryChange = {},
        onOpenDocument = {},
        onStartScan = {},
        onImportImages = {},
        onImportPdf = {},
        onTrash = {},
        onSearch = {},
        onBack = {},
        onBulkDelete = { false },
        onShareSelected = {},
        onRenameFolder = { _, _ -> },
        onTab = {},
        showTips = showTips
    )
}
