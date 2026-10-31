package com.example.scanpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.scanpro.AppViewModel
import com.example.scanpro.R
import com.example.scanpro.core.ui.FeedbackPromptDialog
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import com.example.scanpro.core.ui.findActivity
import com.example.scanpro.core.ui.requestInAppReview
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.feature.lock.LockScreen
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.navigation.ScanProNavHost
import com.example.scanpro.navigation.ScanProNavigationRail
import com.example.scanpro.navigation.navigateToTopLevel

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
    val libraryRevision by viewModel.libraryRevision.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportArtifact by viewModel.exportArtifact.collectAsStateWithLifecycle()
    val openDocumentRequest by viewModel.openDocumentRequest.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val scanRequested by viewModel.scanRequested.collectAsStateWithLifecycle()
    val destinationRequest by viewModel.destinationRequest.collectAsStateWithLifecycle()
    val pdfPasswordRequest by viewModel.pdfPasswordRequest.collectAsStateWithLifecycle()
    val welcomeSeen by viewModel.welcomeSeen.collectAsStateWithLifecycle()
    val reviewDue by viewModel.reviewDue.collectAsStateWithLifecycle()
    val feedbackPromptDue by viewModel.feedbackPromptDue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Requested once, after the third saved scan, from the screen the scan lands on; marked as requested
    // before the call so a crash or a quota-limited answer never leads to a second ask.
    LaunchedEffect(reviewDue, sessionUnlocked) {
        if (!reviewDue || !sessionUnlocked) return@LaunchedEffect
        viewModel.markReviewRequested()
        context.findActivity()?.let { requestInAppReview(it) }
    }
    if (feedbackPromptDue && sessionUnlocked) {
        FeedbackPromptDialog(
            onShareFeedback = {
                viewModel.markFeedbackPromptShown()
                contactSupport(context, SupportTopic.FEEDBACK)
            },
            onLater = viewModel::markFeedbackPromptShown
        )
    }
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
            if (result == SnackbarResult.ActionPerformed) current.onAction?.invoke() else current.onDismiss?.invoke()
        }
    }
    LaunchedEffect(exportArtifact) {
        exportArtifact?.let {
            shareFile(context, it.path, it.mimeType, it.title, it.extraPaths)
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

    // A reminder notification's screen (recycle bin, settings), also only once the library is unlocked.
    LaunchedEffect(destinationRequest) {
        val route = destinationRequest ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        viewModel.clearDestinationRequest()
    }

    // A finished camera scan opens its new document once the library has picked it up. This sits
    // below the lock gate: navigating before the NavHost has set its graph would crash.
    LaunchedEffect(openDocumentRequest, libraryRevision) {
        val id = openDocumentRequest ?: return@LaunchedEffect
        if (viewModel.documentExists(id)) {
            navController.navigate(ScanProDestinations.document(id)) { launchSingleTop = true }
            viewModel.clearOpenDocumentRequest()
        }
    }

    pdfPasswordRequest?.let { request ->
        var password by remember(request) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::cancelPdfPassword,
            title = { Text(stringResource(R.string.ui_pdf_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_pdf_password_body))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        isError = request.wrongPassword,
                        label = { Text(stringResource(R.string.ui_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (request.wrongPassword) {
                        Text(stringResource(R.string.ui_pdf_password_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { viewModel.importPdf(request.uri, password) }) { Text(stringResource(R.string.ui_pdf_password_import)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelPdfPassword) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // Wide windows keep navigation at the side of the content instead of along the bottom.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showRail = com.example.scanpro.navigation.isWideWindow() && currentRoute in ScanProDestinations.topLevel
    val welcomeShowing = welcomeSeen == false
    Box(Modifier.fillMaxSize()) {
        // While the welcome covers the app, screen readers must not reach the controls underneath.
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxSize()
                .then(if (welcomeShowing) Modifier.clearAndSetSemantics { } else Modifier)
        ) {
            if (showRail) {
                ScanProNavigationRail(
                    selectedRoute = currentRoute.orEmpty(),
                    onTab = { route -> navController.navigateToTopLevel(route) }
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                ScanProNavHost(
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    onImportImages = onImportImages,
                    onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                    onCameraScanComplete = viewModel::importCameraSession,
                    onCameraRetakeComplete = viewModel::replacePageFromCamera,
                    onFallbackScanner = onFallbackScanner,
                    onAppLockChange = onAppLockChange
                )
            }
        }

        // First launch only, drawn over the app so the library is already there underneath when it
        // closes; it fades and grows away rather than cutting to the home screen.
        androidx.compose.animation.AnimatedVisibility(
            visible = welcomeShowing,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(320)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(320), targetScale = 1.06f)
        ) {
            androidx.activity.compose.BackHandler { viewModel.markWelcomeSeen() }
            WelcomeScreen(
                onStartScanning = {
                    viewModel.markWelcomeSeen()
                    navController.navigate(ScanProDestinations.CAMERA) { launchSingleTop = true }
                },
                onExplore = viewModel::markWelcomeSeen
            )
        }
    }
}
