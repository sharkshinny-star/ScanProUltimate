package com.example.scanpro.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.core.ui.shareUri
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanMode
import com.example.scanpro.feature.document.DocumentRoute
import com.example.scanpro.feature.library.LibraryRoute
import com.example.scanpro.feature.settings.SettingsRoute
import com.example.scanpro.feature.tools.PdfToolsScreen
import com.example.scanpro.feature.kept.KeptScansRoute
import com.example.scanpro.feature.trash.TrashRoute
import com.example.scanpro.feature.viewer.PdfViewerScreen
import com.example.scanpro.scanner.CameraScannerScreen

/** Back handlers use dropUnlessResumed so a double tap during the exit animation can't pop two screens. */
@Composable
fun ScanProNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onCameraScanComplete: (List<android.net.Uri>, ScanMode, List<CropQuad>, List<PageFilter>, List<PageAdjustments?>, String?) -> Unit,
    onCameraRetakeComplete: (RetakeTarget, android.net.Uri, CropQuad, PageFilter, PageAdjustments?) -> Unit,
    onFallbackScanner: () -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    defaultScanMode: String = "",
    defaultFilter: PageFilter? = null
) {
    val context = LocalContext.current
    val pdfDocumentTitle = stringResource(R.string.nav_pdf_document)
    val onTab: (String) -> Unit = { route -> navController.navigateToTopLevel(route) }
    val onScan: () -> Unit = { navController.navigate(ScanProDestinations.CAMERA) { launchSingleTop = true } }
    val onOpenPdfViewer: (android.net.Uri, String) -> Unit = { uri, title ->
        navController.navigate(ScanProDestinations.pdfViewer(uri, title))
    }

    // With animations turned off in system settings, screens change at once.
    val motion = rememberMotionEnabled()
    NavHost(
        navController = navController,
        startDestination = ScanProDestinations.HOME,
        // The theme background behind every screen, so nothing during a transition shows the darker window.
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        // Tab switches use Material "fade through"; screens pushed on top slide in from the end edge
        // (shared axis X) and slide back out when popped. The camera and full-screen editors rise from
        // the bottom like a sheet. Navigation's own default is a 700ms crossfade, which feels like a hang.
        enterTransition = { if (motion) enterFor(targetState, pop = false) else EnterTransition.None },
        exitTransition = { if (motion) exitFor(targetState, pop = false) else ExitTransition.None },
        popEnterTransition = { if (motion) enterFor(initialState, pop = true) else EnterTransition.None },
        popExitTransition = { if (motion) exitFor(initialState, pop = true) else ExitTransition.None }
    ) {
        // Search shares the library screen but is pushed on top of a tab rather than being one.
        listOf(ScanProDestinations.HOME, ScanProDestinations.FILES, ScanProDestinations.SEARCH).forEach { route ->
            composable(route) {
                LibraryRoute(
                    mode = route,
                    snackbarHostState = snackbarHostState,
                    onOpenDocument = { navController.navigate(ScanProDestinations.document(it)) },
                    onStartScan = { navController.navigate(ScanProDestinations.CAMERA) },
                    onQuickScan = { mode -> navController.navigate(ScanProDestinations.camera(mode)) },
                    onOpenTools = { onTab(ScanProDestinations.TOOLS) },
                    onImportImages = onImportImages,
                    onImportPdf = onImportPdf,
                    onTrash = { navController.navigate(ScanProDestinations.TRASH) },
                    onKeptScans = { navController.navigate(ScanProDestinations.KEPT_SCANS) { launchSingleTop = true } },
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
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.SETTINGS, onTab = onTab, onScan = onScan) }
            )
        }
        composable(ScanProDestinations.TOOLS) {
            PdfToolsScreen(
                onOpenPdfViewer = onOpenPdfViewer,
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.TOOLS, onTab = onTab, onScan = onScan) }
            )
        }
        composable(
            route = ScanProDestinations.PDF_VIEWER,
            arguments = listOf(
                navArgument(ScanProDestinations.PDF_URI_ARG) { type = NavType.StringType },
                navArgument(ScanProDestinations.PDF_TITLE_ARG) { type = NavType.StringType; defaultValue = pdfDocumentTitle }
            )
        ) { entry ->
            val pdfUri = entry.arguments?.getString(ScanProDestinations.PDF_URI_ARG).orEmpty().toUri()
            val title = entry.arguments?.getString(ScanProDestinations.PDF_TITLE_ARG) ?: pdfDocumentTitle
            PdfViewerScreen(
                title = title,
                pdfUri = pdfUri,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onShare = {
                    // Files ScanPro made are shared through its FileProvider; picked files are passed on as they are.
                    if (pdfUri.scheme == "file") shareFile(context, pdfUri.path.orEmpty(), "application/pdf", title)
                    else shareUri(context, pdfUri, "application/pdf", title)
                }
            )
        }
        composable(
            route = ScanProDestinations.CAMERA_ROUTE,
            arguments = listOf(
                navArgument(ScanProDestinations.CAMERA_MODE_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(ScanProDestinations.CAMERA_RETAKE_ARG) { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val retake = ScanProDestinations.parseRetake(entry.arguments?.getString(ScanProDestinations.CAMERA_RETAKE_ARG))
            // Act only while this screen is resumed, so a double tap during the exit animation can't
            // import twice or pop Home and leave an empty screen.
            fun resumed() = entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            CameraScannerScreen(
                // A shortcut or quick action names its mode; otherwise the camera opens in the mode chosen in Settings.
                initialMode = entry.arguments?.getString(ScanProDestinations.CAMERA_MODE_ARG).orEmpty().ifEmpty { defaultScanMode },
                defaultFilter = defaultFilter,
                retakeOfPage = retake?.position,
                onClose = dropUnlessResumed { navController.popBackStack() },
                onComplete = { uris, mode, crops, filters, adjustments, name ->
                    if (resumed()) {
                        if (retake != null) onCameraRetakeComplete(retake, uris.first(), crops.first(), filters.first(), adjustments.firstOrNull())
                        else onCameraScanComplete(uris, mode, crops, filters, adjustments, name)
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
        composable(ScanProDestinations.KEPT_SCANS) {
            KeptScansRoute(
                snackbarHostState = snackbarHostState,
                onBack = dropUnlessResumed { navController.popBackStack() }
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
                onBack = dropUnlessResumed { navController.popBackStack() },
                onRetakePage = { documentId, pageId, position ->
                    navController.navigate(ScanProDestinations.cameraRetake(documentId, pageId, position)) { launchSingleTop = true }
                }
            )
        }
    }
}

private const val PushDuration = 260
private const val FadeThroughDuration = 210

private fun NavBackStackEntry.isTopLevel() = destination.route in ScanProDestinations.topLevel
private fun NavBackStackEntry.isSheetLike() = destination.route == ScanProDestinations.CAMERA_ROUTE

/** The transition for the screen coming in. [entry] is the screen that defines the gesture (the one on top). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(entry: NavBackStackEntry, pop: Boolean): EnterTransition = when {
    // Returning to a tab or moving between tabs: fade through.
    !pop && targetState.isTopLevel() && initialState.isTopLevel() ->
        fadeIn(tween(FadeThroughDuration, delayMillis = 60)) + scaleIn(tween(FadeThroughDuration, delayMillis = 60), initialScale = 0.96f)
    entry.isSheetLike() -> if (pop) fadeIn(tween(PushDuration)) + scaleIn(tween(PushDuration), initialScale = 0.98f)
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(PushDuration, easing = FastOutSlowInEasing)) + fadeIn(tween(PushDuration / 2))
    pop -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PushDuration, easing = FastOutSlowInEasing), initialOffset = { it / 4 }) +
        fadeIn(tween(PushDuration / 2))
    // The pushed screen draws its own background, so it slides in opaque: fading it in showed the screen
    // underneath through it, which read as a dark scrim.
    else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PushDuration, easing = FastOutSlowInEasing), initialOffset = { it })
}

/** The transition for the screen going out, mirroring [enterFor]. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(entry: NavBackStackEntry, pop: Boolean): ExitTransition = when {
    !pop && targetState.isTopLevel() && initialState.isTopLevel() -> fadeOut(tween(90))
    entry.isSheetLike() -> if (pop) slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(PushDuration, easing = FastOutSlowInEasing)) + fadeOut(tween(PushDuration))
    else fadeOut(tween(PushDuration / 2)) + scaleOut(tween(PushDuration), targetScale = 0.98f)
    pop -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PushDuration, easing = FastOutSlowInEasing), targetOffset = { it }) +
        fadeOut(tween(PushDuration / 2))
    else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PushDuration, easing = FastOutSlowInEasing), targetOffset = { it / 4 }) +
        fadeOut(tween(PushDuration / 2))
}

/** Switches bottom-bar tabs the standard way: one copy of each tab, with its state saved and restored. */
fun NavHostController.navigateToTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
