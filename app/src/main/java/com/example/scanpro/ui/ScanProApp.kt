package com.example.scanpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.scanpro.AppViewModel
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.feature.rating.RatePromptSheet
import com.example.scanpro.feature.lock.LockScreen
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.navigation.ScanProNavHost

/** App shell: app lock gate, app-wide snackbar and share effects, and the navigation graph. */
@Composable
fun ScanProApp(
    viewModel: AppViewModel,
    sessionUnlocked: Boolean,
    onFallbackScanner: () -> Unit,
    onImportImages: () -> Unit,
    onUnlock: () -> Unit,
    onAppLockChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportArtifact by viewModel.exportArtifact.collectAsStateWithLifecycle()
    val openDocumentRequest by viewModel.openDocumentRequest.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val scanRequested by viewModel.scanRequested.collectAsStateWithLifecycle()
    val externalPdfToView by viewModel.externalPdfToView.collectAsStateWithLifecycle()
    val showRatingPrompt by viewModel.showRatingPrompt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(message) {
        message?.let { current ->
            val result = snackbarHostState.showSnackbar(
                message = current.text,
                actionLabel = current.actionLabel,
                // Leave undoable messages up long enough to react to.
                duration = if (current.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            // Clear before running the action, so a message the action posts (e.g. "Document restored")
            // isn't wiped by this clear.
            viewModel.clearMessage(current)
            if (result == SnackbarResult.ActionPerformed) current.onAction?.invoke()
        }
    }
    LaunchedEffect(exportArtifact) {
        exportArtifact?.let {
            shareFile(context, it.path, it.mimeType, it.title)
            viewModel.clearExportArtifact()
        }
    }
    when {
        // Draw nothing until the lock setting is known, so a locked library never flashes on screen.
        appLock == null -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            return
        }
        appLock == true && !sessionUnlocked -> {
            LockScreen(onUnlock, snackbarHostState)
            return
        }
    }

    // Launcher "Scan" shortcut: open the camera once the library is unlocked.
    LaunchedEffect(scanRequested) {
        if (scanRequested) {
            navController.navigate(ScanProDestinations.CAMERA) { launchSingleTop = true }
            viewModel.clearScanRequest()
        }
    }

    // A finished camera scan opens its new document once the library has picked it up. This sits
    // below the lock gate: navigating before the NavHost has set its graph would crash.
    LaunchedEffect(openDocumentRequest, documents) {
        val id = openDocumentRequest ?: return@LaunchedEffect
        if (documents.any { it.id == id }) {
            navController.navigate(ScanProDestinations.document(id)) { launchSingleTop = true }
            viewModel.clearOpenDocumentRequest()
        }
    }

    // A PDF opened from another app, below the lock gate for the same reason.
    LaunchedEffect(externalPdfToView) {
        externalPdfToView?.let {
            navController.navigate(ScanProDestinations.pdfViewer(it.uri, it.title))
            viewModel.clearExternalPdfToView()
        }
    }

    // The rating sheet waits until the user is back on Home or Files after their first document,
    // and a moment longer so it doesn't land on top of the "Scan saved" message.
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val onLibraryTab = route == ScanProDestinations.HOME || route == ScanProDestinations.FILES
    var ratingSheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showRatingPrompt, onLibraryTab) {
        if (showRatingPrompt && onLibraryTab) {
            kotlinx.coroutines.delay(1_500)
            ratingSheetOpen = true
        }
    }
    if (ratingSheetOpen) {
        RatePromptSheet(
            onRate = {
                ratingSheetOpen = false
                viewModel.onRatingPromptFinished()
                openPlayStoreListing(context)
            },
            onDismiss = {
                ratingSheetOpen = false
                viewModel.onRatingPromptFinished()
            }
        )
    }

    ScanProNavHost(
        navController = navController,
        snackbarHostState = snackbarHostState,
        onImportImages = onImportImages,
        onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
        onCameraScanComplete = viewModel::importCameraSession,
        onFallbackScanner = onFallbackScanner,
        onAppLockChange = onAppLockChange
    )
}
