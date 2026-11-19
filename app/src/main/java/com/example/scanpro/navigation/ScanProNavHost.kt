package com.example.scanpro.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.scanpro.feature.qr.QrGeneratorRoute
import com.example.scanpro.feature.qr.QrHistoryRoute
import com.example.scanpro.feature.trash.TrashRoute
import com.example.scanpro.feature.viewer.PdfViewerScreen
import com.example.scanpro.feature.phonepdfs.PhoneFile
import com.example.scanpro.feature.phonepdfs.PhoneFileActions
import com.example.scanpro.feature.phonepdfs.PhoneFileKind
import com.example.scanpro.feature.phonepdfs.PhonePdfSource
import com.example.scanpro.feature.phonepdfs.PhonePdfsRoute
import com.example.scanpro.scanner.CameraScannerScreen

/** Back handlers use dropUnlessResumed so a double tap during the exit animation can't pop two screens. */
@Composable
fun ScanProNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onCameraScanComplete: (List<android.net.Uri>, ScanMode, List<CropQuad>, List<PageFilter>, List<PageAdjustments?>, String?, String?) -> Unit,
    onCameraRetakeComplete: (RetakeTarget, android.net.Uri, CropQuad, PageFilter, PageAdjustments?) -> Unit,
    onFallbackScanner: () -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    defaultScanMode: String = "",
    /** Pages shot from a document's Add page: document id, then the pages as for a new scan. */
    onCameraAppendComplete: (String, List<android.net.Uri>, List<CropQuad>, List<PageFilter>, List<PageAdjustments?>) -> Unit = { _, _, _, _, _ -> },
    /** Folders a new scan can be saved into, for the review screen's Folder choice. */
    saveFolders: List<String> = emptyList(),
    defaultFilter: PageFilter? = null,
    /** Check pages before saving (Settings): whether the camera's Done opens the review. */
    reviewMode: com.example.scanpro.data.ReviewMode = com.example.scanpro.data.ReviewMode.ALWAYS,
    /** Copies a PDF from shared storage into the library (Files on this phone > Add to ScanPro). */
    onImportPdfUri: (android.net.Uri) -> Unit = {},
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
        // (shared axis X) and slide back out when popped. The camera rises from the bottom like a sheet,
        // leaving the caller stationary and visible underneath with zero blank screen flicker.
        enterTransition = { if (motion) enterFor(pop = false) else EnterTransition.None },
        exitTransition = { if (motion) exitFor(pop = false) else ExitTransition.None },
        popEnterTransition = { if (motion) enterFor(pop = true) else EnterTransition.None },
        popExitTransition = { if (motion) exitFor(pop = true) else ExitTransition.None },
        // The back gesture follows the same transitions as the close buttons. Left unset, the library's own predictive
        // transition took over: the camera shrank into a card while its controls re-laid themselves out (seen in an
        // owner's screen recording), which looked like dropped frames.
        predictivePopEnterTransition = { if (motion) enterFor(pop = true) else EnterTransition.None },
        predictivePopExitTransition = { if (motion) exitFor(pop = true) else ExitTransition.None }
    ) {
        // Search shares the library screen but is pushed on top of a tab rather than being one.
        listOf(ScanProDestinations.HOME, ScanProDestinations.FILES, ScanProDestinations.SEARCH).forEach { route ->
            composable(route) {
                LibraryRoute(
                    mode = route,
                    snackbarHostState = snackbarHostState,
                    onOpenDocument = { navController.navigate(ScanProDestinations.document(it)) },
                    onStartScan = { folder -> navController.navigate(ScanProDestinations.cameraInFolder(folder)) },
                    onQuickScan = { mode -> navController.navigate(ScanProDestinations.camera(mode)) },
                    onOpenTools = { onTab(ScanProDestinations.TOOLS) },
                    onOpenPdfViewer = onOpenPdfViewer,
                    onImportImages = onImportImages,
                    onImportPdf = onImportPdf,
                    onTrash = { navController.navigate(ScanProDestinations.TRASH) },
                    onKeptScans = { navController.navigate(ScanProDestinations.KEPT_SCANS) { launchSingleTop = true } },
                    onSearch = { navController.navigate(ScanProDestinations.SEARCH) { launchSingleTop = true } },
                    onPhonePdfs = { navController.navigate(ScanProDestinations.PHONE_PDFS) { launchSingleTop = true } },
                    onImportPdfUri = onImportPdfUri,
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onTab = onTab
                )
            }
        }
        composable(ScanProDestinations.SETTINGS) {
            SettingsRoute(
                snackbarHostState = snackbarHostState,
                onAppLockChange = onAppLockChange,
                bottomBar = { ScanProBottomBar(selectedRoute = ScanProDestinations.SETTINGS, onTab = onTab, onScan = onScan) },
                onOpenFiles = { onTab(ScanProDestinations.FILES) },
                onOpenRecycleBin = { navController.navigate(ScanProDestinations.TRASH) }
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
            // One of the moments after which the "default PDF viewer" sheet may be offered on Home.
            LaunchedEffect(Unit) { com.example.scanpro.data.FilesAccessPrompts.markPdfOpened(context) }
            PdfViewerScreen(
                title = title,
                pdfUri = pdfUri,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onShare = {
                    val path = pdfUri.path.orEmpty()
                    when {
                        // Files ScanPro made are shared through its FileProvider; picked files are passed on as they are.
                        pdfUri.scheme == "file" && !PhonePdfSource.isInSharedStorage(path) ->
                            shareFile(context, path, "application/pdf", title)
                        // A PDF that Files on this phone found by walking shared storage: ScanPro's FileProvider
                        // covers only its own folders, so it is shared by its MediaStore address.
                        pdfUri.scheme == "file" -> PhonePdfSource.shareableUri(context, PhoneFile(pdfUri, title, PhoneFileKind.PDF, "application/pdf", 0, 0, path = path)) { uri ->
                            if (uri != null) shareUri(context, uri, "application/pdf", title)
                        }
                        else -> shareUri(context, pdfUri, "application/pdf", title)
                    }
                }
            )
        }
        composable(ScanProDestinations.PHONE_PDFS) {
            PhonePdfsRoute(
                onBack = dropUnlessResumed { navController.popBackStack() },
                actions = PhoneFileActions(
                    onOpenPdf = onOpenPdfViewer,
                    onAddToScanPro = onImportPdfUri,
                    onOpenTools = { onTab(ScanProDestinations.TOOLS) }
                )
            )
        }
        composable(
            route = ScanProDestinations.CAMERA_ROUTE,
            arguments = listOf(
                navArgument(ScanProDestinations.CAMERA_MODE_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(ScanProDestinations.CAMERA_RETAKE_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(ScanProDestinations.CAMERA_FOLDER_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(ScanProDestinations.CAMERA_APPEND_ARG) { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val retake = ScanProDestinations.parseRetake(entry.arguments?.getString(ScanProDestinations.CAMERA_RETAKE_ARG))
            val appendTo = entry.arguments?.getString(ScanProDestinations.CAMERA_APPEND_ARG)?.takeIf { it.isNotBlank() }
            // Act only while this screen is resumed, so a double tap during the exit animation can't
            // import twice or pop Home and leave an empty screen.
            fun resumed() = entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            CameraScannerScreen(
                // A shortcut or quick action names its mode; otherwise the camera opens in the mode chosen in Settings.
                initialMode = entry.arguments?.getString(ScanProDestinations.CAMERA_MODE_ARG).orEmpty().ifEmpty { defaultScanMode },
                defaultFilter = defaultFilter,
                reviewMode = reviewMode,
                retakeOfPage = retake?.position,
                folders = saveFolders,
                initialFolder = entry.arguments?.getString(ScanProDestinations.CAMERA_FOLDER_ARG).orEmpty(),
                appending = appendTo != null,
                onOpenQrHistory = { if (resumed()) navController.navigate(ScanProDestinations.QR_HISTORY) { launchSingleTop = true } },
                onOpenQrGenerator = { if (resumed()) navController.navigate(ScanProDestinations.QR_GENERATOR) { launchSingleTop = true } },
                onClose = dropUnlessResumed { navController.popBackStack() },
                onComplete = { uris, mode, crops, filters, adjustments, name, folder ->
                    if (resumed()) {
                        if (retake != null) onCameraRetakeComplete(retake, uris.first(), crops.first(), filters.first(), adjustments.firstOrNull())
                        else if (appendTo != null) onCameraAppendComplete(appendTo, uris, crops, filters, adjustments)
                        else onCameraScanComplete(uris, mode, crops, filters, adjustments, name, folder)
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
        composable(ScanProDestinations.QR_HISTORY) {
            QrHistoryRoute(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(ScanProDestinations.QR_GENERATOR) {
            QrGeneratorRoute(onBack = dropUnlessResumed { navController.popBackStack() })
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
                },
                onAddPagesWithCamera = { documentId ->
                    navController.navigate(ScanProDestinations.cameraAppend(documentId)) { launchSingleTop = true }
                },
                onScanAnother = { navController.navigate(ScanProDestinations.CAMERA) { launchSingleTop = true } }
            )
        }
    }
}

private const val PushDuration = 280
private const val TabFadeDuration = 150
/** How long the camera takes to appear; the camera screen waits this long before binding the camera (see there). */
internal const val CameraEnterDuration = 180
private const val CameraExitDuration = 150

private fun NavBackStackEntry.isTopLevel() = destination.route in ScanProDestinations.topLevel
private fun NavBackStackEntry.isSheetLike() = destination.route?.startsWith("camera") == true
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() = initialState.isTopLevel() && targetState.isTopLevel()

/*
 * Tabs (F140). Switching to Home pops back to the start destination, so it used to take the "back" branch: Home slid in
 * from the side like a closing screen while the other way round faded and scaled. Each tab draws its own bottom bar, so
 * the bar shrank to 96 % and both bars were half transparent at the same moment, letting the background flash through.
 * Now a tab switch is one layer fading over a screen that stays opaque underneath, whichever way the back stack moved:
 * the incoming tab fades in on top (navigate), or the outgoing tab fades off the incoming one (pop to Home). The bar's
 * pixels are the same in both, so only the selected tab's highlight changes.
 *
 * Pushed screens slide without fading (Android's parallax): fading both layers at once dimmed the screen mid-way, and
 * every alpha layer costs an offscreen buffer per frame.
 */

/** The transition for the screen coming in. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(pop: Boolean): EnterTransition = when {
    betweenTabs() -> if (pop) EnterTransition.None else fadeIn(tween(TabFadeDuration, easing = LinearOutSlowInEasing))
    // Sheet rising from bottom: opaque slide up over the stationary caller screen with maximum initial velocity on frame 1.
    // The camera grows a little and fades in over the caller. It used to slide up the whole screen: a full-height
    // slide shows every late frame as a jump, and the camera's first frames are its heaviest. A short fade with a small
    // scale covers the same distance in pixels of almost nothing, so a late frame is not seen.
    !pop && targetState.isSheetLike() ->
        fadeIn(tween(CameraEnterDuration, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(CameraEnterDuration, easing = LinearOutSlowInEasing), initialScale = 0.94f)
    // Sheet popping back to caller: caller is already rendered and stationary underneath.
    pop && initialState.isSheetLike() -> EnterTransition.None
    // The screen underneath comes back from a quarter of the way across, opaque.
    pop -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PushDuration, easing = FastOutSlowInEasing)) { it / 4 }
    // The pushed screen draws its own background, so it slides in opaque.
    else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PushDuration, easing = FastOutSlowInEasing)) { it }
}

/** The transition for the screen going out, mirroring [enterFor]. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(pop: Boolean): ExitTransition = when {
    betweenTabs() -> if (pop) fadeOut(tween(TabFadeDuration, easing = FastOutLinearInEasing)) else ExitTransition.KeepUntilTransitionsFinished
    // Sheet rising on top: the caller screen stays stationary and fully visible underneath. It must be held: an exit
    // with no animation ends on the first frame and the caller would be removed there, leaving the camera to fade in
    // over the empty background (a light flash before the dark viewfinder).
    !pop && targetState.isSheetLike() -> ExitTransition.KeepUntilTransitionsFinished
    // Sheet popping: drops immediately off-screen accelerating downward with zero hesitation.
    pop && initialState.isSheetLike() ->
        fadeOut(tween(CameraExitDuration, easing = FastOutLinearInEasing)) +
            scaleOut(tween(CameraExitDuration, easing = FastOutLinearInEasing), targetScale = 0.96f)
    pop -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PushDuration, easing = FastOutSlowInEasing), targetOffset = { it })
    else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PushDuration, easing = FastOutSlowInEasing), targetOffset = { it / 4 })
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
