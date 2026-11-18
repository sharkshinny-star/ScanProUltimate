package com.example.scanpro.ui

import kotlinx.coroutines.CancellationException
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
import com.example.scanpro.feature.feedback.RateExperienceSheet
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.feature.lock.LockScreen
import com.example.scanpro.data.DefaultViewerPrompt
import com.example.scanpro.data.DefaultViewerPromptState
import com.example.scanpro.data.FilesAccessPrompts
import com.example.scanpro.data.FirstRunHints
import com.example.scanpro.feature.phonepdfs.DefaultViewerSheet
import com.example.scanpro.feature.phonepdfs.ImportTipSheet
import com.example.scanpro.feature.phonepdfs.isDefaultPdfViewer
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
    val feedbackPrompt by viewModel.feedbackPrompt.collectAsStateWithLifecycle()
    val scanSavedPending by viewModel.scanSavedPending.collectAsStateWithLifecycle()
    // Stays open once shown, even if the screen underneath changes, until the user closes it.
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    val whatsNewDue by viewModel.whatsNewDue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    // Several PDFs may be picked at once (long-press in the picker); each becomes its own document, as when shared.
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(viewModel::importPdf)
    }
    // The first PDF import explains the system picker once (tap, long-press for several, Select), then goes on to it.
    var importTipOpen by rememberSaveable { mutableStateOf(false) }
    val startPdfImport: () -> Unit = {
        if (FilesAccessPrompts.importTipShown(context)) pdfImportLauncher.launch(arrayOf("application/pdf"))
        else importTipOpen = true
    }

    LaunchedEffect(message) {
        message?.let { current ->
            val result = try {
                snackbarHostState.showSnackbar(
                    message = current.text,
                    actionLabel = current.actionLabel,
                    // Leave undoable messages up long enough to react to.
                    duration = if (current.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
            } catch (cancelled: CancellationException) {
                // A newer message replaced this one: its undo window is over, so run its dismiss step (e.g. purge a
                // removed page's files). If it is still current (rotation), it is shown again, so leave it alone.
                if (viewModel.message.value !== current) current.onDismiss?.invoke()
                throw cancelled
            }
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
                // A tab (the widget's Tools and Files) is switched to like a tap on the bottom bar, not stacked on top.
                if (route == ScanProDestinations.TOOLS || route == ScanProDestinations.FILES || route == ScanProDestinations.HOME) {
                    navController.navigateToTopLevel(route)
                } else {
                    navController.navigate(route) { launchSingleTop = true }
                }
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
                            onImportPdf = startPdfImport,
                            onImportPdfUri = { uri -> viewModel.importPdf(uri) },
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

                // "Rate your experience" (FeedbackSchedule): after a "Scan saved" sheet closed, once the user is back on
                // the document or a main tab with nothing else open, never over the welcome or lock screens.
                val feedbackCalm = feedbackPrompt && sessionUnlocked && welcomeSeen == true && launchSettled &&
                    scanSavedPending == null && !whatsNewDue &&
                    (currentRoute == ScanProDestinations.DOCUMENT || currentRoute in ScanProDestinations.topLevel)
                LaunchedEffect(feedbackCalm) {
                    if (feedbackCalm) {
                        // A beat after the "Scan saved" sheet has slid away, so the two never overlap.
                        kotlinx.coroutines.delay(400)
                        feedbackOpen = true
                        viewModel.onFeedbackPromptShown()
                    }
                }
                if (feedbackOpen) {
                    RateExperienceSheet(onClose = { feedbackOpen = false })
                }

                // Once, after an update that has something to tell (never together with the welcome or the rating sheet).
                // "All files access", asked once at start (F196, owner's request) for anyone who went through the welcome
                // before it asked; a new install answers it on the welcome's permission page, which marks it asked.
                // Comes before What's new, the tour and the default-viewer sheet, never together with them.
                var filesAskDue by remember {
                    mutableStateOf(!FilesAccessPrompts.startFilesAsked(context) && !com.example.scanpro.feature.phonepdfs.PhonePdfSource.hasAccess(context))
                }
                val filesAskShowing = filesAskDue && welcomeSeen == true && !feedbackOpen && launchSettled
                if (filesAskShowing) {
                    val requestFilesAccess = com.example.scanpro.feature.phonepdfs.rememberPhoneFilesAccessRequest()
                    FilesAccessStartDialog(
                        onAllow = {
                            FilesAccessPrompts.markStartFilesAsked(context)
                            filesAskDue = false
                            requestFilesAccess()
                        },
                        onNotNow = {
                            FilesAccessPrompts.markStartFilesAsked(context)
                            filesAskDue = false
                        }
                    )
                }

                if (whatsNewDue && !welcomeShowing && !feedbackOpen && launchSettled && !filesAskDue) {
                    WhatsNewSheet(
                        onPickColours = {
                            viewModel.markWhatsNewSeen()
                            navController.navigateToTopLevel(ScanProDestinations.SETTINGS)
                        },
                        onDismiss = viewModel::markWhatsNewSeen
                    )
                }

                // The short tour of scanning, Files and Tools (F146): once, on Home, for someone who has just been
                // through the welcome, and never together with another sheet. Looked up again whenever the screen
                // changes, so Settings > Show introductions again brings it back on the next visit to Home.
                var tourDue by remember(welcomeSeen, currentRoute) {
                    mutableStateOf(welcomeSeen == true && FirstRunHints.isDue(context, FirstRunHints.HOME_TOUR))
                }
                val tourAllowed = tourDue && !welcomeShowing && !whatsNewDue && !feedbackOpen && launchSettled && !filesAskDue &&
                    currentRoute == ScanProDestinations.HOME
                // A beat after the welcome has faded away, so the two never overlap.
                var tourReady by remember { mutableStateOf(false) }
                LaunchedEffect(tourAllowed) {
                    tourReady = false
                    if (tourAllowed) {
                        kotlinx.coroutines.delay(450)
                        tourReady = true
                    }
                }
                if (tourAllowed && tourReady) {
                    HomeTourDialog(onDone = {
                        FirstRunHints.markSeen(context, FirstRunHints.HOME_TOUR)
                        tourDue = false
                    })
                }

                if (importTipOpen) {
                    ImportTipSheet(
                        onImport = {
                            FilesAccessPrompts.markImportTipShown(context)
                            importTipOpen = false
                            pdfImportLauncher.launch(arrayOf("application/pdf"))
                        },
                        onCancel = {
                            FilesAccessPrompts.markImportTipShown(context)
                            importTipOpen = false
                        }
                    )
                }

                // "Set ScanPro as your default PDF viewer": once, on Home, after the first scan or the first PDF opened
                // in the viewer, and never in the same visit as the welcome, the tour, What's new or the rating sheet.
                var promptShownThisSession by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(feedbackPrompt, whatsNewDue, tourAllowed) {
                    if (feedbackPrompt || whatsNewDue || tourAllowed) promptShownThisSession = true
                }
                var defaultViewerShown by remember { mutableStateOf(FilesAccessPrompts.defaultViewerShown(context)) }
                // The feedback schedule counts saved scans (data/FeedbackSchedule.kt); read again when the screen changes.
                val completedScans = remember(currentRoute) { com.example.scanpro.data.FeedbackPrompts.load(context).completedScans }
                // Looked up again whenever the screen changes: a PDF may just have been opened, or ScanPro made the default.
                val pdfOpened = remember(currentRoute) { FilesAccessPrompts.pdfOpened(context) }
                val alreadyDefault = remember(currentRoute, defaultViewerShown) { !defaultViewerShown && isDefaultPdfViewer(context) }
                val defaultViewerAllowed = DefaultViewerPrompt.isDue(
                    DefaultViewerPromptState(
                        alreadyShown = defaultViewerShown,
                        pdfOpened = pdfOpened,
                        completedScans = completedScans,
                        alreadyDefault = alreadyDefault,
                        onHome = currentRoute == ScanProDestinations.HOME,
                        otherPromptDue = welcomeShowing || whatsNewDue || feedbackPrompt || tourDue || importTipOpen || !launchSettled || filesAskDue,
                        promptShownThisSession = promptShownThisSession
                    )
                )
                var defaultViewerReady by remember { mutableStateOf(false) }
                LaunchedEffect(defaultViewerAllowed) {
                    defaultViewerReady = false
                    if (defaultViewerAllowed) {
                        kotlinx.coroutines.delay(600)
                        defaultViewerReady = true
                    }
                }
                if (defaultViewerAllowed && defaultViewerReady) {
                    val failed = stringResource(R.string.default_viewer_settings_failed)
                    DefaultViewerSheet(
                        onSetDefault = {
                            FilesAccessPrompts.markDefaultViewerShown(context)
                            defaultViewerShown = true
                            promptShownThisSession = true
                            if (!com.example.scanpro.feature.settings.HomeScreenPins.openDefaultAppSettings(context)) {
                                android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        onNotNow = {
                            FilesAccessPrompts.markDefaultViewerShown(context)
                            defaultViewerShown = true
                            promptShownThisSession = true
                        }
                    )
                }

                // First launch only, drawn over the app so the library is already there underneath when it
                // closes; it fades and grows away rather than cutting to the home screen.
                androidx.compose.animation.AnimatedVisibility(
                    visible = welcomeShowing,
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.fadeOut(tween(320)) +
                        androidx.compose.animation.scaleOut(tween(320), targetScale = 1.06f)
                ) {
                    androidx.activity.compose.BackHandler { viewModel.markWelcomeSeen() }
                    WelcomeScreen(onGetStarted = viewModel::markWelcomeSeen)
                }
            }
        }
    }
}
