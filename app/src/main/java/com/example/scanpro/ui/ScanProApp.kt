package com.example.scanpro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.example.scanpro.core.ui.findActivity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.scanpro.AppViewModel
import com.example.scanpro.R
import com.example.scanpro.core.ui.RatingSheet
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import kotlinx.coroutines.launch
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
    onAppLockChange: (Boolean) -> Unit,
    /** False while the launch animation plays: nothing that opens its own window may cover it. */
    launchSettled: Boolean = true
) {
    val context = LocalContext.current
    val supportScope = androidx.compose.runtime.rememberCoroutineScope()
    val libraryRevision by viewModel.libraryRevision.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportArtifact by viewModel.exportArtifact.collectAsStateWithLifecycle()
    val openDocumentRequest by viewModel.openDocumentRequest.collectAsStateWithLifecycle()
    val openWhenIdleRequest by viewModel.openWhenIdleRequest.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val scanRequested by viewModel.scanRequested.collectAsStateWithLifecycle()
    val destinationRequest by viewModel.destinationRequest.collectAsStateWithLifecycle()
    val pdfPasswordRequest by viewModel.pdfPasswordRequest.collectAsStateWithLifecycle()
    val welcomeSeen by viewModel.welcomeSeen.collectAsStateWithLifecycle()
    val ratingDue by viewModel.ratingDue.collectAsStateWithLifecycle()
    val whatsNewDue by viewModel.whatsNewDue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Once, after the first saved document and never over the welcome or lock screens. Marked as shown on every
    // way out, so it cannot come back.
    if (ratingDue && sessionUnlocked && welcomeSeen == true && launchSettled) {
        RatingSheet(
            onNotNow = viewModel::markRatingShown,
            onRateOnPlay = {
                viewModel.markRatingShown()
                openPlayStoreListing(context)
            },
            onSendFeedback = {
                viewModel.markRatingShown()
                supportScope.launch { contactSupport(context, SupportTopic.FEEDBACK) }
            }
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

    // First launch: the language comes before the welcome, so the welcome is already in the chosen language.
    // Also before the navigation effects below: they need the NavHost, which isn't composed while the picker is
    // up (a "Scan" shortcut or notification opened before a language was chosen crashed the app).
    var languageChosen by rememberSaveable { mutableStateOf(com.example.scanpro.core.text.AppLocale.isChosen(context)) }
    Crossfade(
        targetState = languageChosen,
        animationSpec = tween(durationMillis = 260),
        label = "languagePickerCrossfade"
    ) { chosen ->
        if (!chosen) {
            LanguagePickerScreen(
                initial = com.example.scanpro.core.text.AppLocale.current(context)
                    ?: com.example.scanpro.core.text.AppLocale.suggested()
            ) { tag ->
                languageChosen = true
                context.findActivity()?.let { com.example.scanpro.core.text.AppLocale.apply(it, tag) }
            }
        } else {
            // Launcher "Scan" shortcut: open the camera once the library is unlocked.
            LaunchedEffect(scanRequested) {
                if (scanRequested) {
                    val mode = viewModel.scanRequestMode.value
                    navController.navigate(if (mode.isBlank()) ScanProDestinations.CAMERA else ScanProDestinations.camera(mode)) { launchSingleTop = true }
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

            // A scan that finished in the background opens by itself only if the user is on a top-level screen. Anywhere else
            // (editing a document, scanning again) it would pull them away from what they are doing, so it is announced with
            // an Open button instead.
            LaunchedEffect(openWhenIdleRequest, libraryRevision) {
                val id = openWhenIdleRequest ?: return@LaunchedEffect
                if (viewModel.documentExists(id)) {
                    viewModel.clearOpenWhenIdleRequest()
                    val route = navController.currentBackStackEntry?.destination?.route
                    if (route == null || route in ScanProDestinations.topLevel) {
                        navController.navigate(ScanProDestinations.document(id)) { launchSingleTop = true }
                    } else {
                        viewModel.announceScanSaved(id)
                    }
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
                                // A password type keeps the keyboard from learning the password or suggesting it later.
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                            onTab = { route -> navController.navigateToTopLevel(route) },
                            // The rail's scan button did nothing before: it relied on the default no-op.
                            onScan = { navController.navigate(ScanProDestinations.CAMERA) { launchSingleTop = true } }
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
                            onAppLockChange = onAppLockChange,
                            defaultScanMode = viewModel.defaultScanMode.collectAsStateWithLifecycle().value,
                            saveFolders = viewModel.saveFolders.collectAsStateWithLifecycle().value,
                            onCameraAppendComplete = viewModel::appendPagesFromCamera,
                            defaultFilter = viewModel.defaultFilter.collectAsStateWithLifecycle().value,
                            reviewMode = viewModel.reviewMode.collectAsStateWithLifecycle().value
                        )
                    }
                }

                // Once, after an update that has something to tell (never together with the welcome or the rating sheet).
                if (whatsNewDue && !welcomeShowing && !ratingDue && launchSettled) {
                    WhatsNewSheet(
                        onPickColours = {
                            viewModel.markWhatsNewSeen()
                            navController.navigateToTopLevel(ScanProDestinations.SETTINGS)
                        },
                        onDismiss = viewModel::markWhatsNewSeen
                    )
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
                    WelcomeScreen(onGetStarted = viewModel::markWelcomeSeen)
                }
            }
        }
    }
}
