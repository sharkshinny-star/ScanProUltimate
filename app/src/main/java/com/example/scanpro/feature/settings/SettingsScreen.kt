package com.example.scanpro.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.OversizedPdfs
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ReviewMode
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.ThemeMode

/*
 * Settings, split by job: this file holds the route, the screen's frame (top bar, page navigation, search) and nothing
 * else. SettingsMainPage.kt is the first page, Settings<Name>Page.kt each page's rows, SettingsComponents.kt the shared
 * row anatomy, SettingsSearch.kt the search index and SettingsShortcutsPage.kt the home-screen and default-app requests.
 */

@Composable
fun SettingsRoute(
    snackbarHostState: SnackbarHostState,
    onAppLockChange: (Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
    /** The library row opens the documents; the Recycle bin page opens the recycle bin. */
    onOpenFiles: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pdfQuality by viewModel.pdfQuality.collectAsStateWithLifecycle()
    val autoTitle by viewModel.autoTitle.collectAsStateWithLifecycle()
    val appLock by viewModel.appLock.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val accentPalette by viewModel.accentPalette.collectAsStateWithLifecycle()
    val homeTips by viewModel.homeTips.collectAsStateWithLifecycle()
    val documentCount by viewModel.documentCount.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val defaultOcrScript by viewModel.defaultOcrScript.collectAsStateWithLifecycle()
    val defaultScanMode by viewModel.defaultScanMode.collectAsStateWithLifecycle()
    val defaultFilter by viewModel.defaultFilter.collectAsStateWithLifecycle()
    val reviewMode by viewModel.reviewMode.collectAsStateWithLifecycle()
    val namePattern by viewModel.namePattern.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val reclaimable by viewModel.freeSpaceOffer.collectAsStateWithLifecycle()
    val freeingUp by viewModel.freeingUp.collectAsStateWithLifecycle()
    val notifyFinished by viewModel.notifyFinished.collectAsStateWithLifecycle()
    val notifyFailures by viewModel.notifyFailures.collectAsStateWithLifecycle()
    val notifyTrash by viewModel.notifyTrash.collectAsStateWithLifecycle()
    val notifyBackup by viewModel.notifyBackup.collectAsStateWithLifecycle()
    // Re-read on every return to the screen: the permission or the system switch may have changed meanwhile.
    var canNotify by remember { mutableStateOf(viewModel.canPostNotifications()) }
    LifecycleResumeEffect(Unit) {
        canNotify = viewModel.canPostNotifications()
        onPauseOrDispose { }
    }
    SettingsScreen(
        busy = busy,
        notifications = NotificationSettings(
            canPost = canNotify,
            finished = notifyFinished,
            failures = notifyFailures,
            trash = notifyTrash,
            backup = notifyBackup,
            onFinishedChange = viewModel::setNotifyFinished,
            onFailuresChange = viewModel::setNotifyFailures,
            onTrashChange = viewModel::setNotifyTrash,
            onBackupChange = viewModel::setNotifyBackup,
            onPermissionResult = { canNotify = viewModel.canPostNotifications() },
            systemSettingsIntent = viewModel::notificationSettingsIntent
        ),
        onBackupAll = viewModel::backupAll,
        lastBackupAt = lastBackupAt,
        trashRetentionDays = trashRetentionDays,
        defaultOcrScript = defaultOcrScript,
        onTrashRetentionChange = viewModel::setTrashRetentionDays,
        onDefaultOcrScriptChange = viewModel::setDefaultOcrScript,
        defaultScanMode = defaultScanMode,
        onDefaultScanModeChange = viewModel::setDefaultScanMode,
        defaultFilter = defaultFilter,
        onDefaultFilterChange = viewModel::setDefaultFilter,
        reviewMode = reviewMode,
        onReviewModeChange = viewModel::setReviewMode,
        namePattern = namePattern,
        onNamePatternChange = viewModel::setNamePattern,
        pdfQuality = pdfQuality,
        autoTitle = autoTitle,
        appLock = appLock,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        documentCount = documentCount,
        trashCount = trashCount,
        storageBytes = storageBytes,
        snackbarHostState = snackbarHostState,
        onPdfQualityChange = viewModel::setPdfQuality,
        onAutoTitleChange = viewModel::setAutoTitle,
        onAppLockChange = onAppLockChange,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        accentPalette = accentPalette,
        onAccentPaletteChange = viewModel::setAccentPalette,
        homeTips = homeTips,
        onHomeTipsChange = viewModel::setHomeTips,
        onRestoreBackup = viewModel::restoreBackup,
        reclaimable = reclaimable,
        freeingUp = freeingUp,
        onFreeUpSpace = viewModel::freeUpSpace,
        onDismissFreeSpace = viewModel::dismissFreeSpace,
        onRecoverLibrary = viewModel::recoverLibrary,
        bottomBar = bottomBar,
        onOpenFiles = onOpenFiles,
        onOpenRecycleBin = onOpenRecycleBin
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    busy: Boolean = false,
    lastBackupAt: Long = 0L,
    notifications: NotificationSettings = NotificationSettings(),
    onBackupAll: (Uri, String) -> Unit = { _, _ -> },
    pdfQuality: PdfQuality,
    autoTitle: Boolean,
    appLock: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    documentCount: Int,
    trashCount: Int,
    storageBytes: Long,
    snackbarHostState: SnackbarHostState,
    onPdfQualityChange: (PdfQuality) -> Unit,
    onAutoTitleChange: (Boolean) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit = {},
    accentPalette: AccentPalette = AccentPalette.INDIGO,
    onAccentPaletteChange: (AccentPalette) -> Unit = {},
    homeTips: Boolean = true,
    onHomeTipsChange: (Boolean) -> Unit = {},
    onRestoreBackup: (Uri, String) -> Unit = { _, _ -> },
    onRecoverLibrary: () -> Unit = {},
    trashRetentionDays: Int = DEFAULT_TRASH_RETENTION_DAYS,
    defaultOcrScript: OcrScript = OcrScript.LATIN,
    defaultScanMode: String = ScanMode.DOCUMENT.name,
    onDefaultScanModeChange: (String) -> Unit = {},
    defaultFilter: PageFilter? = null,
    onDefaultFilterChange: (PageFilter?) -> Unit = {},
    reviewMode: ReviewMode = ReviewMode.WHEN_NEEDED,
    onReviewModeChange: (ReviewMode) -> Unit = {},
    namePattern: String = "",
    onNamePatternChange: (String) -> Unit = {},
    onTrashRetentionChange: (Int) -> Unit = {},
    onDefaultOcrScriptChange: (OcrScript) -> Unit = {},
    /** PDFs from older versions that are far larger than needed; null when there are none. */
    reclaimable: OversizedPdfs? = null,
    freeingUp: Boolean = false,
    onFreeUpSpace: () -> Unit = {},
    onDismissFreeSpace: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pendingJump by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // F140: the main page is the search, the library and one row per page; each page opens on its own. A search result
    // opens its page and lights the row up there.
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val resources = LocalResources.current
    val pageBySection = remember(resources) { SettingsPage.entries.associateBy { resources.getString(it.title) } }
    val motion = rememberMotionEnabled()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(page?.let { stringResource(it.title) } ?: stringResource(R.string.common_settings)) },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (!motion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // Into a page from the end edge, back out towards it: a short shared-axis slide.
                    val forward = targetState != null
                    val spec = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                    (slideInHorizontally(spec) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(180, delayMillis = 40))) togetherWith
                        (slideOutHorizontally(spec) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(100)))
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "settingsPage"
        ) { target ->
            val scrollState = rememberScrollState()
            val locator = remember(scrollState) { SettingsLocator(scrollState) }
            LaunchedEffect(pendingJump, query, target) {
                val title = pendingJump ?: return@LaunchedEffect
                if (query.isNotBlank() || target == null) return@LaunchedEffect
                locator.jumpTo(title)
                pendingJump = null
            }
            CompositionLocalProvider(LocalSettingsLocator provides locator) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .onGloballyPositioned { locator.viewportTop = it.positionInRoot().y }
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (target) {
                            null -> {
                                SettingsSearchField(query, onQueryChange = { query = it })
                                if (query.isNotBlank()) {
                                    SettingsSearchResults(query, rememberSettingHits(query)) { hit ->
                                        query = ""
                                        pageBySection[hit.section]?.let { found ->
                                            pendingJump = hit.title
                                            page = found
                                        }
                                    }
                                } else {
                                    SettingsMainPage(
                                        overview = SettingsOverview(
                                            documentCount = documentCount,
                                            trashCount = trashCount,
                                            trashRetentionDays = trashRetentionDays,
                                            storageBytes = storageBytes,
                                            lastBackupAt = lastBackupAt,
                                            canPostNotifications = notifications.canPost,
                                            reclaimable = reclaimable,
                                            busy = busy,
                                            freeingUp = freeingUp,
                                            defaultScanMode = defaultScanMode,
                                            pdfQuality = pdfQuality,
                                            defaultOcrScript = defaultOcrScript
                                        ),
                                        onOpenPage = { page = it },
                                        onOpenFiles = onOpenFiles,
                                        onFreeUpSpace = onFreeUpSpace,
                                        onDismissFreeSpace = onDismissFreeSpace
                                    )
                                }
                            }
                            SettingsPage.SCANNING -> ScanningPage(
                                defaultScanMode = defaultScanMode,
                                onDefaultScanModeChange = onDefaultScanModeChange,
                                defaultFilter = defaultFilter,
                                onDefaultFilterChange = onDefaultFilterChange,
                                reviewMode = reviewMode,
                                onReviewModeChange = onReviewModeChange,
                                pdfQuality = pdfQuality,
                                onPdfQualityChange = onPdfQualityChange,
                                autoTitle = autoTitle,
                                onAutoTitleChange = onAutoTitleChange,
                                namePattern = namePattern,
                                onNamePatternChange = onNamePatternChange,
                                defaultOcrScript = defaultOcrScript,
                                onDefaultOcrScriptChange = onDefaultOcrScriptChange
                            )
                            SettingsPage.APPEARANCE -> AppearancePage(
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                                accentPalette = accentPalette,
                                onAccentPaletteChange = onAccentPaletteChange,
                                dynamicColor = dynamicColor,
                                onDynamicColorChange = onDynamicColorChange,
                                homeTips = homeTips,
                                onHomeTipsChange = onHomeTipsChange
                            )
                            SettingsPage.NOTIFICATIONS -> NotificationsPage(notifications)
                            SettingsPage.BACKUP -> BackupPage(
                                busy = busy,
                                lastBackupAt = lastBackupAt,
                                documentCount = documentCount,
                                snackbarHostState = snackbarHostState,
                                onBackupAll = onBackupAll,
                                onRestoreBackup = onRestoreBackup,
                                onRecoverLibrary = onRecoverLibrary
                            )
                            SettingsPage.RECYCLE_BIN -> RecycleBinPage(
                                trashCount = trashCount,
                                trashRetentionDays = trashRetentionDays,
                                onTrashRetentionChange = onTrashRetentionChange,
                                onOpenRecycleBin = onOpenRecycleBin
                            )
                            SettingsPage.PRIVACY -> PrivacyPage(appLock = appLock, onAppLockChange = onAppLockChange)
                            SettingsPage.SHORTCUTS -> ShortcutsPage()
                            SettingsPage.SUPPORT -> SupportPage(snackbarHostState)
                        }
                    }
                }
            }
        }
    }
}
