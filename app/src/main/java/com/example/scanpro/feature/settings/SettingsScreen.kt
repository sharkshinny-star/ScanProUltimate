package com.example.scanpro.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.animation.togetherWith
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CameraAlt
import com.example.scanpro.core.ui.findActivity
import com.example.scanpro.ui.WhatsNewSheet
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.core.designsystem.cappedFontScale
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.BuildConfig
import com.example.scanpro.R
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.InsetDivider
import com.example.scanpro.core.designsystem.PasswordInputDialog
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.SupportLinks
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.sharePlayStoreListing
import com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.TRASH_RETENTION_CHOICES
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.core.designsystem.PalettePicker
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    snackbarHostState: SnackbarHostState,
    onAppLockChange: (Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
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
        bottomBar = bottomBar
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
    defaultScanMode: String = com.example.scanpro.data.ScanMode.DOCUMENT.name,
    onDefaultScanModeChange: (String) -> Unit = {},
    defaultFilter: com.example.scanpro.data.PageFilter? = null,
    onDefaultFilterChange: (com.example.scanpro.data.PageFilter?) -> Unit = {},
    reviewMode: com.example.scanpro.data.ReviewMode = com.example.scanpro.data.ReviewMode.WHEN_NEEDED,
    onReviewModeChange: (com.example.scanpro.data.ReviewMode) -> Unit = {},
    namePattern: String = "",
    onNamePatternChange: (String) -> Unit = {},
    onTrashRetentionChange: (Int) -> Unit = {},
    onDefaultOcrScriptChange: (OcrScript) -> Unit = {},
    /** PDFs from older versions that are far larger than needed; null when there are none. */
    reclaimable: com.example.scanpro.data.OversizedPdfs? = null,
    freeingUp: Boolean = false,
    onFreeUpSpace: () -> Unit = {},
    onDismissFreeSpace: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var languageDialogOpen by rememberSaveable { mutableStateOf(false) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var whatsNewOpen by rememberSaveable { mutableStateOf(false) }
    // Backup: every document as its own encrypted file in a folder the user picks (a cloud drive works
    // too); restore reads one such file back. Documents never leave the device otherwise.
    var backupFolder by rememberSaveable { mutableStateOf<Uri?>(null) }
    var backupIntroOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingJump by remember { mutableStateOf<String?>(null) }
    var backupToRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            backupFolder = it
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupToRestore = uri
    }
    if (backupIntroOpen) {
        BackupIntroDialog(
            onChoose = { backupIntroOpen = false; pickBackupFolder.launch(documentsFolderUri()) },
            onDismiss = { backupIntroOpen = false }
        )
    }
    val nothingToBackUp = stringResource(R.string.settings_backup_nothing)
    backupFolder?.let { folder ->
        PasswordInputDialog(
            title = stringResource(R.string.settings_backup_all),
            message = stringResource(R.string.settings_backup_password_message),
            minimumLength = EncryptedDocumentPackage.MIN_NEW_PASSWORD_LENGTH,
            confirmLabel = stringResource(R.string.settings_backup_start),
            onDismiss = { backupFolder = null },
            warning = stringResource(R.string.backup_warning_unredacted_all),
            confirmTwice = true
        ) { password ->
            backupFolder = null
            onBackupAll(folder, password)
        }
    }
    backupToRestore?.let { uri ->
        PasswordInputDialog(
            title = stringResource(R.string.settings_restore_backup),
            message = stringResource(R.string.settings_restore_password_message),
            minimumLength = EncryptedDocumentPackage.MIN_RESTORE_PASSWORD_LENGTH,
            confirmLabel = stringResource(R.string.settings_restore_start),
            onDismiss = { backupToRestore = null }
        ) { password ->
            backupToRestore = null
            onRestoreBackup(uri, password)
        }
    }
    val linkUnavailable = stringResource(R.string.settings_link_unavailable)
    val openLink: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, linkUnavailable, Toast.LENGTH_SHORT).show()
        }
    }
    // Set per build from Gradle properties; the constants in SupportLinks are the fallback.
    val privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL.ifBlank { SupportLinks.PRIVACY_POLICY_URL }
    val termsUrl = BuildConfig.TERMS_URL.ifBlank { SupportLinks.TERMS_OF_USE_URL }
    val scope = rememberCoroutineScope()
    val contact: (SupportTopic) -> Unit = { topic ->
        scope.launch {
            val delivery = contactSupport(context, topic)
            snackbarHostState.showSnackbar(delivery.confirmation(context))
        }
    }
    if (privacyOpen) PrivacyDialog(onDismiss = { privacyOpen = false })
    if (whatsNewOpen) {
        WhatsNewSheet(
            onPickColours = { whatsNewOpen = false },
            onDismiss = { whatsNewOpen = false }
        )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // F140: one long scroll of about 35 rows, each with a coloured tile and a two-line hint, was cluttered and slow to open
    // (70 frames, up to 500 ms, on every switch to this tab). The main page is now the search, the library card and one
    // row per section; each section opens on its own page. A search result opens its page and lights the row up there.
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    val pageBySection = SettingsPage.entries.associateBy { stringResource(it.title) }
    val motion = com.example.scanpro.core.designsystem.rememberMotionEnabled()

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
        androidx.compose.animation.AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (!motion) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    // Into a page from the end edge, back out towards it: a short shared-axis slide.
                    val forward = targetState != null
                    val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    (androidx.compose.animation.slideInHorizontally(spec) { w -> if (forward) w / 6 else -w / 6 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 40))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(spec) { w -> if (forward) -w / 6 else w / 6 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)))
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "settingsPage"
        ) { target ->
            val scrollState = rememberScrollState()
            val locator = remember(scrollState) { SettingsLocator(scrollState) }
            androidx.compose.runtime.LaunchedEffect(pendingJump, query, target) {
                val title = pendingJump ?: return@LaunchedEffect
                if (query.isNotBlank() || target == null) return@LaunchedEffect
                locator.jumpTo(title)
                pendingJump = null
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalSettingsLocator provides locator, LocalSettingsPage provides target) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .onGloballyPositioned { locator.viewportTop = it.positionInRoot().y }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (target == null) 18.dp else 22.dp)
            ) {
                if (target == null) {
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
                        StorageCard(documentCount, trashCount, storageBytes, reclaimable, busy, freeingUp, onFreeUpSpace, onDismissFreeSpace)
                        SettingsPagesCard(onOpen = { page = it })
                        AboutFooter()
                    }
                } else {
                SettingsSection(SettingsPage.BACKUP) {
                    BackupStatus(lastBackupAt, hasDocuments = documentCount > 0)
                    SettingsRow(
                        icon = Icons.Rounded.Backup,
                        tone = TileTone.Indigo,
                        title = stringResource(R.string.settings_backup_all),
                        subtitle = stringResource(R.string.settings_backup_all_subtitle),
                        trailing = { Chevron() },
                        enabled = !busy,
                        onClick = {
                            if (documentCount == 0) scope.launch { snackbarHostState.showSnackbar(nothingToBackUp) } else backupIntroOpen = true
                        }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Restore,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_restore_backup),
                        subtitle = stringResource(R.string.settings_restore_backup_subtitle),
                        trailing = { Chevron() },
                        enabled = !busy,
                        onClick = { backupPicker.launch(arrayOf("*/*")) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.SettingsBackupRestore,
                        tone = TileTone.Violet,
                        title = stringResource(R.string.settings_recover_library),
                        subtitle = stringResource(R.string.settings_recover_library_subtitle),
                        trailing = { Chevron() },
                        enabled = !busy,
                        onClick = onRecoverLibrary
                    )
                }

                SettingsSection(SettingsPage.APPEARANCE) {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        tone = TileTone.Violet,
                        title = stringResource(R.string.settings_theme_title),
                        subtitle = stringResource(R.string.settings_theme_subtitle)
                    )
                    SettingsChoiceRow(options = ThemeMode.entries, selected = themeMode, onSelect = onThemeModeChange) { stringResource(it.labelRes) }
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.ColorLens,
                        tone = TileTone.Rose,
                        title = stringResource(R.string.settings_palette_title),
                        subtitle = stringResource(
                            if (dynamicColor && supportsDynamicColor) R.string.settings_palette_subtitle_wallpaper
                            else R.string.settings_palette_subtitle
                        )
                    )
                    PalettePicker(
                        // With wallpaper colours on, no theme applies, so none is marked.
                        selected = accentPalette.takeUnless { dynamicColor && supportsDynamicColor },
                        onSelect = onAccentPaletteChange,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                    )
                    if (supportsDynamicColor) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Wallpaper,
                            tone = TileTone.Sky,
                            title = stringResource(R.string.settings_dynamic_color_title),
                            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                    InsetDivider()
                    val currentTag = com.example.scanpro.core.text.AppLocale.current(context) ?: com.example.scanpro.core.text.AppLocale.suggested()
                    SettingsRow(
                        icon = Icons.Rounded.Language,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_app_language),
                        subtitle = com.example.scanpro.core.text.AppLocale.SUPPORTED.firstOrNull { it.first == currentTag }?.second
                            ?: stringResource(R.string.settings_app_language_subtitle),
                        trailing = { Chevron() },
                        onClick = { languageDialogOpen = true }
                    )
                    if (languageDialogOpen) {
                        com.example.scanpro.ui.LanguagePickerDialog(
                            current = currentTag,
                            onDismiss = { languageDialogOpen = false },
                            onChosen = { tag ->
                                languageDialogOpen = false
                                context.findActivity()?.let { com.example.scanpro.core.text.AppLocale.apply(it, tag) }
                            }
                        )
                    }
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.TipsAndUpdates,
                        tone = TileTone.Amber,
                        title = stringResource(R.string.settings_home_tips_title),
                        subtitle = stringResource(R.string.settings_home_tips_subtitle),
                        checked = homeTips,
                        onCheckedChange = onHomeTipsChange
                    )
                }

                SettingsSection(SettingsPage.SCANNING) {
                    // Camera start mode: every mode in the camera's strip, QR code last.
                    val modeChoices = com.example.scanpro.data.ScanMode.entries.map { it.name to stringResource(it.labelRes) } +
                        ("QR" to stringResource(R.string.scan_qr_code))
                    var modeMenuOpen by remember { mutableStateOf(false) }
                    val modeLabel = modeChoices.firstOrNull { it.first == defaultScanMode }?.second ?: modeChoices.first().second
                    SettingsRow(
                        icon = Icons.Rounded.CameraAlt,
                        tone = TileTone.Indigo,
                        title = stringResource(R.string.settings_default_mode_title),
                        subtitle = stringResource(R.string.settings_default_mode_subtitle),
                        trailing = {
                            Box {
                                Text(modeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                                    modeChoices.forEach { (value, label) ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { modeMenuOpen = false; onDefaultScanModeChange(value) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { modeMenuOpen = true }
                    )
                    InsetDivider()
                    // Starting filter: "Automatic" keeps each mode's own (Auto Clean, Whiteboard, Original for ID cards).
                    var filterMenuOpen by remember { mutableStateOf(false) }
                    val automatic = stringResource(R.string.settings_default_filter_automatic)
                    val filterLabel = defaultFilter?.let { stringResource(it.labelRes) } ?: automatic
                    SettingsRow(
                        icon = Icons.Rounded.AutoFixHigh,
                        tone = TileTone.Amber,
                        title = stringResource(R.string.settings_default_filter_title),
                        subtitle = stringResource(R.string.settings_default_filter_subtitle),
                        trailing = {
                            Box {
                                Text(filterLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(automatic) },
                                        onClick = { filterMenuOpen = false; onDefaultFilterChange(null) }
                                    )
                                    com.example.scanpro.data.PageFilter.entries.forEach { f ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stringResource(f.labelRes)) },
                                            onClick = { filterMenuOpen = false; onDefaultFilterChange(f) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { filterMenuOpen = true }
                    )
                    InsetDivider()
                    // Fast save (R26): Done in the camera saves straight away unless a page needs a look.
                    SettingsRow(
                        icon = Icons.Rounded.Checklist,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_review_mode_title),
                        subtitle = stringResource(R.string.settings_review_mode_subtitle)
                    )
                    SettingsChoiceRow(options = com.example.scanpro.data.ReviewMode.entries, selected = reviewMode, onSelect = onReviewModeChange) { stringResource(it.labelRes) }
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.PictureAsPdf,
                        tone = TileTone.Coral,
                        title = stringResource(R.string.settings_pdf_quality_title),
                        subtitle = stringResource(R.string.settings_pdf_quality_body)
                    )
                    SettingsChoiceRow(options = PdfQuality.entries, selected = pdfQuality, onSelect = onPdfQualityChange) { stringResource(it.labelRes) }
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Title,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_smart_titles_title),
                        subtitle = stringResource(R.string.settings_smart_titles_body),
                        checked = autoTitle,
                        onCheckedChange = onAutoTitleChange
                    )
                    InsetDivider()
                    // Name pattern (R34): tokens {text} {mode} {date} {time}; blank keeps the automatic name.
                    var patternOpen by remember { mutableStateOf(false) }
                    SettingsRow(
                        icon = Icons.Rounded.Title,
                        tone = TileTone.Slate,
                        title = stringResource(R.string.settings_name_pattern_title),
                        subtitle = stringResource(R.string.settings_name_pattern_body),
                        trailing = {
                            Text(
                                namePattern.ifBlank { stringResource(R.string.settings_default_filter_automatic) },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        },
                        onClick = { patternOpen = true }
                    )
                    if (patternOpen) {
                        com.example.scanpro.core.designsystem.TextInputDialog(
                            title = stringResource(R.string.settings_name_pattern_title),
                            initial = namePattern,
                            label = stringResource(R.string.settings_name_pattern_label),
                            allowBlank = true,
                            onDismiss = { patternOpen = false }
                        ) { onNamePatternChange(it); patternOpen = false }
                    }
                    InsetDivider()
                    var scriptMenuOpen by remember { mutableStateOf(false) }
                    val scriptLabel = stringResource(defaultOcrScript.labelRes)
                    SettingsRow(
                        icon = Icons.Rounded.Translate,
                        tone = TileTone.Violet,
                        title = stringResource(R.string.settings_ocr_language_title),
                        subtitle = stringResource(R.string.settings_ocr_language_subtitle, scriptLabel),
                        trailing = {
                            Box {
                                Text(scriptLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = scriptMenuOpen, onDismissRequest = { scriptMenuOpen = false }) {
                                    OcrScript.entries.forEach { script ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stringResource(script.labelRes)) },
                                            onClick = { scriptMenuOpen = false; onDefaultOcrScriptChange(script) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { scriptMenuOpen = true }
                    )
                }

                SettingsSection(SettingsPage.RECYCLE_BIN) {
                    var retentionMenuOpen by remember { mutableStateOf(false) }
                    SettingsRow(
                        icon = Icons.Rounded.Delete,
                        tone = TileTone.Slate,
                        title = stringResource(R.string.settings_trash_retention_title),
                        subtitle = stringResource(R.string.settings_trash_retention_subtitle),
                        trailing = {
                            Box {
                                Text(pluralStringResource(R.plurals.settings_trash_retention_days, trashRetentionDays, trashRetentionDays), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = retentionMenuOpen, onDismissRequest = { retentionMenuOpen = false }) {
                                    TRASH_RETENTION_CHOICES.forEach { days ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(pluralStringResource(R.plurals.settings_trash_retention_days, days, days)) },
                                            onClick = { retentionMenuOpen = false; onTrashRetentionChange(days) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { retentionMenuOpen = true }
                    )
                }

                SettingsSection(SettingsPage.PRIVACY) {
                    SettingsRow(
                        icon = Icons.Rounded.Fingerprint,
                        tone = TileTone.Indigo,
                        title = stringResource(R.string.settings_app_lock_title),
                        subtitle = stringResource(R.string.settings_app_lock_body),
                        checked = appLock,
                        onCheckedChange = onAppLockChange
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.PrivacyTip,
                        tone = TileTone.Slate,
                        title = stringResource(R.string.settings_privacy_title),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        trailing = { Chevron() },
                        onClick = { privacyOpen = true }
                    )
                    if (privacyPolicyUrl.isNotBlank()) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.PrivacyTip,
                            tone = TileTone.Slate,
                            title = stringResource(R.string.settings_privacy_policy),
                            trailing = { Chevron() },
                            onClick = { openLink(privacyPolicyUrl) }
                        )
                    }
                    if (termsUrl.isNotBlank()) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Description,
                            tone = TileTone.Slate,
                            title = stringResource(R.string.settings_terms_of_use),
                            trailing = { Chevron() },
                            onClick = { openLink(termsUrl) }
                        )
                    }
                }

                NotificationsSection(notifications)

                SettingsSection(SettingsPage.SUPPORT) {
                    SettingsRow(
                        icon = Icons.Rounded.AutoAwesome,
                        tone = TileTone.Amber,
                        title = stringResource(R.string.settings_whats_new_title),
                        subtitle = stringResource(R.string.settings_whats_new_subtitle),
                        trailing = { Chevron() },
                        onClick = { whatsNewOpen = true }
                    )
                    InsetDivider()
                    // F146: the Home tour, the tool introductions and the camera's mode explainers all show once more.
                    val introsReset = stringResource(R.string.settings_show_intros_done)
                    SettingsRow(
                        icon = Icons.Rounded.Replay,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_show_intros_title),
                        subtitle = stringResource(R.string.settings_show_intros_subtitle),
                        onClick = {
                            com.example.scanpro.data.FirstRunHints.reset(context)
                            com.example.scanpro.scanner.resetScanIntros(context)
                            scope.launch { snackbarHostState.showSnackbar(introsReset) }
                        }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Star,
                        tone = TileTone.Amber,
                        title = stringResource(R.string.settings_rate_title),
                        subtitle = stringResource(R.string.settings_rate_subtitle),
                        trailing = { Chevron() },
                        onClick = { openPlayStoreListing(context) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Feedback,
                        tone = TileTone.Teal,
                        title = stringResource(R.string.settings_feedback),
                        subtitle = stringResource(R.string.settings_feedback_subtitle),
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.FEEDBACK) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        tone = TileTone.Rose,
                        title = stringResource(R.string.settings_report_issue_title),
                        subtitle = stringResource(R.string.settings_report_issue_subtitle),
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.ISSUE) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Lightbulb,
                        tone = TileTone.Violet,
                        title = stringResource(R.string.settings_request_features),
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.FEATURE) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Share,
                        tone = TileTone.Sky,
                        title = stringResource(R.string.settings_share_with_friends),
                        subtitle = stringResource(R.string.settings_share_subtitle),
                        trailing = { Chevron() },
                        onClick = { sharePlayStoreListing(context) }
                    )
                }

                }
            }
            }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

/** Library at a glance: three figures on one card. */
@Composable
private fun StorageCard(
    documentCount: Int,
    trashCount: Int,
    storageBytes: Long,
    reclaimable: com.example.scanpro.data.OversizedPdfs? = null,
    busy: Boolean = false,
    freeingUp: Boolean = false,
    onFreeUpSpace: () -> Unit = {},
    onDismissFreeSpace: () -> Unit = {}
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Storage, TileTone.Indigo, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_library_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_library_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(Modifier.weight(1f), documentCount.toString(), pluralStringResource(R.plurals.settings_stat_documents, documentCount))
                // No bin icon: in a third of a 360dp row it left room for "in" and nothing else.
                Stat(Modifier.weight(1f), "$trashCount", stringResource(R.string.settings_stat_in_recycle_bin))
                Stat(Modifier.weight(1f), formatBytes(LocalContext.current, storageBytes), stringResource(R.string.settings_stat_used))
            }
            // Scans from older versions were saved as lossless PDFs, many times larger than they need to be.
            if (reclaimable != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 14.dp)) {
                        // A close button (48 dp touch target): not everyone wants this, and it should not nag.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_free_space_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissFreeSpace, enabled = !freeingUp) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.settings_free_space_subtitle, formatBytes(LocalContext.current, reclaimable.estimatedBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.FilledTonalButton(onClick = onFreeUpSpace, enabled = !busy, modifier = Modifier.align(Alignment.End).padding(end = 10.dp)) {
                            if (freeingUp) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_free_space_running))
                            } else {
                                Text(stringResource(R.string.settings_free_space_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            // Three tiles share a 360dp phone's width, so "46.1 MB" doesn't fit at full size. One line with
            // maxLines = 1 dropped the unit after the line break; shrinking the text keeps it whole.
            val valueStyle = MaterialTheme.typography.titleLarge
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = valueStyle.fontSize)
            )
            // Two lines, so "in recycle bin" (and longer translations) isn't cut to its first word. A third of a phone's
            // width can't hold "documents" at a 150 % font size (it broke as "docume / nts"), so the label grows at
            // most 1.2 times.
            Text(label, style = MaterialTheme.typography.labelSmall.cappedFontScale(1.2f), color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Settings pages (F140). The main page lists them, each with a one-line summary made of the names of the settings it holds
 * (so the summary is translated already); each page holds what used to be one section of the long list.
 */
internal enum class SettingsPage(@StringRes val title: Int, val icon: ImageVector, val tone: TileTone, val summary: List<Int>) {
    SCANNING(R.string.settings_section_scanning, Icons.Rounded.CameraAlt, TileTone.Indigo,
        listOf(R.string.settings_default_mode_title, R.string.settings_pdf_quality_title, R.string.settings_ocr_language_title)),
    APPEARANCE(R.string.settings_section_appearance, Icons.Rounded.Palette, TileTone.Violet,
        listOf(R.string.settings_theme_title, R.string.settings_palette_title, R.string.settings_app_language)),
    BACKUP(R.string.settings_section_backup, Icons.Rounded.Backup, TileTone.Teal,
        listOf(R.string.settings_backup_all, R.string.settings_restore_backup)),
    PRIVACY(R.string.settings_section_privacy, Icons.Rounded.Fingerprint, TileTone.Rose,
        listOf(R.string.settings_app_lock_title, R.string.settings_privacy_title)),
    NOTIFICATIONS(R.string.settings_section_notifications, Icons.Rounded.Notifications, TileTone.Amber,
        listOf(R.string.settings_notify_finished_title, R.string.settings_notify_backup_title)),
    RECYCLE_BIN(R.string.settings_section_recycle_bin, Icons.Rounded.Delete, TileTone.Slate,
        listOf(R.string.settings_trash_retention_title)),
    SUPPORT(R.string.settings_section_support, Icons.Rounded.Feedback, TileTone.Sky,
        listOf(R.string.settings_whats_new_title, R.string.settings_report_issue_title, R.string.settings_rate_title))
}

/** The page being drawn; null on the main page. */
internal val LocalSettingsPage = staticCompositionLocalOf<SettingsPage?> { null }

/** The main page's list of pages: one row each, title and a one-line summary. */
@Composable
private fun SettingsPagesCard(onOpen: (SettingsPage) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SettingsPage.entries.forEachIndexed { index, page ->
                if (index > 0) InsetDivider()
                SettingsRow(
                    icon = page.icon,
                    tone = page.tone,
                    title = stringResource(page.title),
                    subtitle = page.summary.map { stringResource(it) }.joinToString(" · "),
                    subtitleMaxLines = 1,
                    trailing = { Chevron() },
                    onClick = { onOpen(page) }
                )
            }
        }
    }
}

/** One page's rows on one tonal card; drawn only on its own page (the page title is in the top bar). */
@Composable
private fun SettingsSection(page: SettingsPage, content: @Composable () -> Unit) {
    if (LocalSettingsPage.current != page) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    tone: TileTone,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null
) {
    val locator = LocalSettingsLocator.current
    val flash by animateColorAsState(
        if (locator?.highlighted == title) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        label = "settingFlash"
    )
    Row(
        Modifier
            .fillMaxWidth()
            // Where this row sits, so a search result can scroll to it.
            .onGloballyPositioned { coords -> locator?.let { it.tops[title] = coords.positionInRoot().y - it.viewportTop + it.scroll.value } }
            .background(flash)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A row with a switch. The whole row is one control for a screen reader, title, hint and state together,
 * so the switch itself takes no clicks of its own.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    tone: TileTone,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        SettingsRow(
            icon = icon,
            tone = tone,
            title = title,
            subtitle = subtitle,
            trailing = { Switch(checked = checked, onCheckedChange = null) }
        )
    }
}

/** The Notifications section's state and actions, gathered so the screen's signature stays readable. */
data class NotificationSettings(
    val canPost: Boolean = true,
    val finished: Boolean = true,
    val failures: Boolean = true,
    val trash: Boolean = false,
    val backup: Boolean = false,
    val onFinishedChange: (Boolean) -> Unit = {},
    val onFailuresChange: (Boolean) -> Unit = {},
    val onTrashChange: (Boolean) -> Unit = {},
    val onBackupChange: (Boolean) -> Unit = {},
    val onPermissionResult: () -> Unit = {},
    val systemSettingsIntent: (() -> Intent)? = null
)

@Composable
private fun NotificationsSection(state: NotificationSettings) {
    val context = LocalContext.current
    val openSystemSettings: () -> Unit = {
        state.systemSettingsIntent?.let { runCatching { context.startActivity(it()) } }
    }
    // Asked once after the welcome on first launch (StartupPermissions); if refused there, asked again here when
    // the user turns a notice on.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.onPermissionResult()
        // Refused before: Android won't ask again, so its own settings page is the only way to allow them.
        if (!granted) openSystemSettings()
    }
    val requestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openSystemSettings()
    }
    // Turning a notice on is the moment to ask for permission, if Android does not allow notifications yet.
    fun enabling(change: (Boolean) -> Unit): (Boolean) -> Unit = { on ->
        change(on)
        if (on && !state.canPost) requestPermission()
    }
    SettingsSection(SettingsPage.NOTIFICATIONS) {
        if (!state.canPost) {
            SettingsRow(
                icon = Icons.Rounded.NotificationsOff,
                tone = TileTone.Rose,
                title = stringResource(R.string.settings_notifications_off_title),
                subtitle = stringResource(R.string.settings_notifications_off_body),
                trailing = { Chevron() },
                onClick = requestPermission
            )
            InsetDivider()
        }
        SettingsRow(
            icon = Icons.Rounded.TaskAlt,
            tone = TileTone.Teal,
            title = stringResource(R.string.settings_notify_finished_title),
            subtitle = stringResource(R.string.settings_notify_finished_body),
            checked = state.finished,
            onCheckedChange = enabling(state.onFinishedChange)
        )
        InsetDivider()
        SettingsRow(
            icon = Icons.Rounded.ErrorOutline,
            tone = TileTone.Rose,
            title = stringResource(R.string.settings_notify_failures_title),
            subtitle = stringResource(R.string.settings_notify_failures_body),
            checked = state.failures,
            onCheckedChange = enabling(state.onFailuresChange)
        )
        InsetDivider()
        SettingsRow(
            icon = Icons.Rounded.AutoDelete,
            tone = TileTone.Amber,
            title = stringResource(R.string.settings_notify_trash_title),
            subtitle = stringResource(R.string.settings_notify_trash_body),
            checked = state.trash,
            onCheckedChange = enabling(state.onTrashChange)
        )
        InsetDivider()
        SettingsRow(
            icon = Icons.Rounded.Backup,
            tone = TileTone.Indigo,
            title = stringResource(R.string.settings_notify_backup_title),
            subtitle = stringResource(R.string.settings_notify_backup_body),
            checked = state.backup,
            onCheckedChange = enabling(state.onBackupChange)
        )
        if (state.systemSettingsIntent != null) {
            InsetDivider()
            SettingsRow(
                icon = Icons.Rounded.Notifications,
                tone = TileTone.Slate,
                title = stringResource(R.string.settings_notifications_system_title),
                subtitle = stringResource(R.string.settings_notifications_system_body),
                trailing = { Chevron() },
                onClick = openSystemSettings
            )
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Brand sign-off at the end of the page. */
@Composable
private fun AboutFooter() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(size = 44.dp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.core_recommend_app_subject), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.settings_footer_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The app's privacy facts, in plain words, for the Privacy row. */
@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_privacy_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_privacy_dialog_account))
                Text(stringResource(R.string.settings_privacy_dialog_storage))
                Text(stringResource(R.string.settings_privacy_dialog_translation))
                Text(stringResource(R.string.settings_privacy_dialog_camera))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_privacy_dialog_ok)) } }
    )
}

/**
 * A short pick-one list. Side by side as segments normally; as a vertical list of radio rows when the text is
 * large, because three segments a third of a phone wide break words in the middle ("Alway/s", "If need/ed", R43).
 */
@Composable
private fun <T> SettingsChoiceRow(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f) {
        Column(
            Modifier.fillMaxWidth().selectableGroup().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(selected = option == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // Every segment as tall as the tallest, so a label that wraps ("System default" on a narrow
                    // phone) doesn't leave the outline stepped.
                    modifier = Modifier.fillMaxHeight(),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text(label(option)) }
            }
        }
    }
}
