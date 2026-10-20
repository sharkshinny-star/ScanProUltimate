package com.example.scanpro.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.feature.document.DocumentRoute
import com.example.scanpro.feature.library.LibraryRoute
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.feature.settings.SettingsRoute
import com.example.scanpro.feature.tools.PdfToolsScreen
import com.example.scanpro.feature.viewer.PdfViewerScreen
import com.example.scanpro.feature.trash.TrashRoute
import com.example.scanpro.scanner.CameraScannerScreen

/** Back handlers use dropUnlessResumed so a double tap during the exit animation can't pop two screens. */
@Composable
fun ScanProNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onCameraScanComplete: (List<android.net.Uri>, ScanMode) -> Unit,
    onFallbackScanner: () -> Unit,
    onAppLockChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val onTab: (String) -> Unit = { route -> navController.navigateToTopLevel(route) }
    val onOpenPdfViewer: (android.net.Uri, String) -> Unit = { uri, title ->
        navController.navigate(ScanProDestinations.pdfViewer(uri, title))
    }

    NavHost(navController = navController, startDestination = ScanProDestinations.HOME) {
        // Search shares the library screen but is pushed on top of a tab rather than being one.
        listOf(ScanProDestinations.HOME, ScanProDestinations.FILES, ScanProDestinations.SEARCH).forEach { route ->
            composable(route) {
                LibraryRoute(
                    mode = route,
                    snackbarHostState = snackbarHostState,
                    onOpenDocument = { navController.navigate(ScanProDestinations.document(it)) },
                    onStartScan = { navController.navigate(ScanProDestinations.CAMERA) },
                    onImportImages = onImportImages,
                    onImportPdf = onImportPdf,
                    onTrash = { navController.navigate(ScanProDestinations.TRASH) },
                    onSearch = { navController.navigate(ScanProDestinations.SEARCH) { launchSingleTop = true } },
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onTab = onTab
                )
            }
        }
        composable(ScanProDestinations.SETTINGS) {
            SettingsRoute(
                snackbarHostState = snackbarHostState,
                onAppLockChange = onAppLockChange,
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.SETTINGS, onTab = onTab) }
            )
        }
        composable(ScanProDestinations.TOOLS) {
            PdfToolsScreen(
                onOpenPdfViewer = onOpenPdfViewer,
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.TOOLS, onTab = onTab) }
            )
        }
        composable(
            route = ScanProDestinations.PDF_VIEWER,
            arguments = listOf(
                navArgument(ScanProDestinations.PDF_URI_ARG) { type = NavType.StringType },
                navArgument(ScanProDestinations.PDF_TITLE_ARG) { type = NavType.StringType; defaultValue = "PDF Document" }
            )
        ) { entry ->
            val pdfUri = android.net.Uri.parse(entry.arguments?.getString(ScanProDestinations.PDF_URI_ARG).orEmpty())
            val title = entry.arguments?.getString(ScanProDestinations.PDF_TITLE_ARG) ?: "PDF Document"
            PdfViewerScreen(
                title = title,
                pdfUri = pdfUri,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onShare = { shareFile(context, pdfUri.path.orEmpty(), "application/pdf", title) }
            )
        }
        composable(ScanProDestinations.CAMERA) { entry ->
            // Act only while this screen is resumed, so a double tap during the exit animation can't
            // import twice or pop Home and leave an empty screen.
            fun resumed() = entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            CameraScannerScreen(
                onClose = dropUnlessResumed { navController.popBackStack() },
                onComplete = { uris, mode ->
                    if (resumed()) {
                        onCameraScanComplete(uris, mode)
                        navController.popBackStack()
                    }
                },
                onFallbackScanner = {
                    if (resumed()) {
                        navController.popBackStack()
                        onFallbackScanner()
                    }
                }
            )
        }
        composable(ScanProDestinations.TRASH) {
            TrashRoute(
                snackbarHostState = snackbarHostState,
                onBack = dropUnlessResumed { navController.popBackStack() }
            )
        }
        composable(
            route = ScanProDestinations.DOCUMENT,
            arguments = listOf(navArgument(ScanProDestinations.DOCUMENT_ID_ARG) { type = NavType.StringType })
        ) {
            DocumentRoute(
                snackbarHostState = snackbarHostState,
                onOpenPdfViewer = onOpenPdfViewer,
                onBack = dropUnlessResumed { navController.popBackStack() }
            )
        }
    }
}

/** Switches bottom-bar tabs the standard way: one copy of each tab, with its state saved and restored. */
fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
