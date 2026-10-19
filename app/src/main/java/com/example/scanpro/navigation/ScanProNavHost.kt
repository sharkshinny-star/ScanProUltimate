package com.example.scanpro.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.feature.document.DocumentRoute
import com.example.scanpro.feature.library.LibraryRoute
import com.example.scanpro.feature.settings.SettingsRoute
import com.example.scanpro.feature.trash.TrashRoute
import com.example.scanpro.scanner.CameraScannerScreen

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
    val onTab: (String) -> Unit = { route -> navController.navigateToTopLevel(route) }

    NavHost(navController = navController, startDestination = ScanProDestinations.HOME) {
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
                    onTab = onTab
                )
            }
        }
        composable(ScanProDestinations.SETTINGS) {
            SettingsRoute(
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onAppLockChange = onAppLockChange,
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.SETTINGS, onTab = onTab) }
            )
        }
        composable(ScanProDestinations.CAMERA) {
            CameraScannerScreen(
                onClose = { navController.popBackStack() },
                onComplete = { uris, mode ->
                    onCameraScanComplete(uris, mode)
                    navController.popBackStack()
                },
                onFallbackScanner = {
                    navController.popBackStack()
                    onFallbackScanner()
                }
            )
        }
        composable(ScanProDestinations.TRASH) {
            TrashRoute(
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = ScanProDestinations.DOCUMENT,
            arguments = listOf(navArgument(ScanProDestinations.DOCUMENT_ID_ARG) { type = NavType.StringType })
        ) {
            DocumentRoute(
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() }
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
