package com.example.scanpro.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.BuildConfig
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.InsetDivider
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.SupportLinks
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import com.example.scanpro.core.ui.openLink
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.sharePlayStoreListing
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ThemeMode

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
    val documentCount by viewModel.documentCount.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val defaultOcrScript by viewModel.defaultOcrScript.collectAsStateWithLifecycle()
    SettingsScreen(
        trashRetentionDays = trashRetentionDays,
        defaultOcrScript = defaultOcrScript,
        onTrashRetentionChange = viewModel::setTrashRetentionDays,
        onDefaultOcrScriptChange = viewModel::setDefaultOcrScript,
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
        onRestoreBackup = viewModel::restoreBackup,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    onRestoreBackup: (android.net.Uri, String) -> Unit = { _, _ -> },
    trashRetentionDays: Int = com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS,
    defaultOcrScript: com.example.scanpro.data.OcrScript = com.example.scanpro.data.OcrScript.LATIN,
    onTrashRetentionChange: (Int) -> Unit = {},
    onDefaultOcrScriptChange: (com.example.scanpro.data.OcrScript) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    // Restore: pick the package, then ask for its password.
    var backupToRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val backupPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupToRestore = uri
    }
    backupToRestore?.let { uri ->
        com.example.scanpro.core.designsystem.PasswordInputDialog(
            title = "Restore encrypted backup",
            message = "Enter the password the backup was created with. The document is added to your library.",
            minimumLength = 6,
            confirmLabel = "Restore",
            onDismiss = { backupToRestore = null }
        ) { password ->
            backupToRestore = null
            onRestoreBackup(uri, password)
        }
    }
    val scope = rememberCoroutineScope()
    val contact: (SupportTopic) -> Unit = { topic ->
        val delivery = contactSupport(context, topic)
        scope.launch { snackbarHostState.showSnackbar(delivery.confirmation) }
    }
    if (privacyOpen) PrivacyDialog(onDismiss = { privacyOpen = false })
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                StorageCard(documentCount, trashCount, storageBytes)

                SettingsSection("Backup") {
                    SettingsRow(
                        icon = Icons.Rounded.Restore,
                        tone = TileTone.Teal,
                        title = "Restore encrypted backup",
                        subtitle = "Bring back a document from a ScanPro backup package",
                        trailing = { Chevron() },
                        onClick = { backupPicker.launch(arrayOf("*/*")) }
                    )
                }

                SettingsSection("Appearance") {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        tone = TileTone.Violet,
                        title = "Theme",
                        subtitle = "Light, dark, or follow the system"
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == themeMode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) { Text(if (mode == ThemeMode.SYSTEM) "System" else mode.label) }
                        }
                    }
                    if (supportsDynamicColor) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Wallpaper,
                            tone = TileTone.Sky,
                            title = "Wallpaper colours",
                            subtitle = "Tint the app with your wallpaper's palette",
                            trailing = { Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange) },
                            onClick = { onDynamicColorChange(!dynamicColor) }
                        )
                    }
                }

                SettingsSection("Scanning") {
                    SettingsRow(
                        icon = Icons.Rounded.PictureAsPdf,
                        tone = TileTone.Coral,
                        title = "Default PDF quality",
                        subtitle = "High keeps every detail. Small makes lighter files."
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                        PdfQuality.entries.forEachIndexed { index, quality ->
                            SegmentedButton(
                                selected = quality == pdfQuality,
                                onClick = { onPdfQualityChange(quality) },
                                shape = SegmentedButtonDefaults.itemShape(index, PdfQuality.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) { Text(quality.label) }
                        }
                    }
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Title,
                        tone = TileTone.Teal,
                        title = "Smart titles",
                        subtitle = "Name new scans from the text they contain",
                        trailing = { Switch(checked = autoTitle, onCheckedChange = onAutoTitleChange) },
                        onClick = { onAutoTitleChange(!autoTitle) }
                    )
                    InsetDivider()
                    var scriptMenuOpen by remember { mutableStateOf(false) }
                    SettingsRow(
                        icon = Icons.Rounded.Translate,
                        tone = TileTone.Violet,
                        title = "Text recognition language",
                        subtitle = "New scans are read as ${defaultOcrScript.label}; change it per document on its page",
                        trailing = {
                            Box {
                                Text(defaultOcrScript.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = scriptMenuOpen, onDismissRequest = { scriptMenuOpen = false }) {
                                    com.example.scanpro.data.OcrScript.entries.forEach { script ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(script.label) },
                                            onClick = { scriptMenuOpen = false; onDefaultOcrScriptChange(script) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { scriptMenuOpen = true }
                    )
                }

                SettingsSection("Recycle bin") {
                    var retentionMenuOpen by remember { mutableStateOf(false) }
                    SettingsRow(
                        icon = Icons.Rounded.Delete,
                        tone = TileTone.Slate,
                        title = "Keep deleted documents for",
                        subtitle = "Then they are removed for good",
                        trailing = {
                            Box {
                                Text("$trashRetentionDays days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.DropdownMenu(expanded = retentionMenuOpen, onDismissRequest = { retentionMenuOpen = false }) {
                                    com.example.scanpro.data.TRASH_RETENTION_CHOICES.forEach { days ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("$days days") },
                                            onClick = { retentionMenuOpen = false; onTrashRetentionChange(days) }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { retentionMenuOpen = true }
                    )
                }

                SettingsSection("Privacy & security") {
                    SettingsRow(
                        icon = Icons.Rounded.Fingerprint,
                        tone = TileTone.Indigo,
                        title = "App lock",
                        subtitle = "Unlock with fingerprint, face or your screen lock",
                        trailing = { Switch(checked = appLock, onCheckedChange = onAppLockChange) },
                        onClick = { onAppLockChange(!appLock) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.PrivacyTip,
                        tone = TileTone.Slate,
                        title = "Privacy",
                        subtitle = "No account, nothing uploaded",
                        trailing = { Chevron() },
                        onClick = { privacyOpen = true }
                    )
                    if (SupportLinks.PRIVACY_POLICY_URL.isNotBlank()) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.PrivacyTip,
                            tone = TileTone.Slate,
                            title = "Privacy policy",
                            trailing = { Chevron() },
                            onClick = { openLink(context, SupportLinks.PRIVACY_POLICY_URL) }
                        )
                    }
                    if (SupportLinks.TERMS_OF_USE_URL.isNotBlank()) {
                        InsetDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Description,
                            tone = TileTone.Slate,
                            title = "Terms of use",
                            trailing = { Chevron() },
                            onClick = { openLink(context, SupportLinks.TERMS_OF_USE_URL) }
                        )
                    }
                }

                SettingsSection("Feedback & support") {
                    SettingsRow(
                        icon = Icons.Rounded.Star,
                        tone = TileTone.Amber,
                        title = "Rate ScanPro",
                        subtitle = "Leave a rating on Google Play",
                        trailing = { Chevron() },
                        onClick = { openPlayStoreListing(context) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Feedback,
                        tone = TileTone.Teal,
                        title = "Give feedback",
                        subtitle = "Tell us what works and what doesn't",
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.FEEDBACK) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        tone = TileTone.Rose,
                        title = "Report an issue",
                        subtitle = "App and device details are filled in for you",
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.ISSUE) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Lightbulb,
                        tone = TileTone.Violet,
                        title = "Suggest a feature",
                        trailing = { Chevron() },
                        onClick = { contact(SupportTopic.FEATURE) }
                    )
                    InsetDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Share,
                        tone = TileTone.Sky,
                        title = "Share ScanPro",
                        subtitle = "Send the Play Store link to a friend",
                        trailing = { Chevron() },
                        onClick = { sharePlayStoreListing(context) }
                    )
                }

                AboutFooter()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

/** Library at a glance: three figures on one card. */
@Composable
private fun StorageCard(documentCount: Int, trashCount: Int, storageBytes: Long) {
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
                    Text("Your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Stored on this device only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(Modifier.weight(1f), "$documentCount", if (documentCount == 1) "document" else "documents")
                Stat(Modifier.weight(1f), "$trashCount", "in recycle bin", icon = Icons.Rounded.Delete)
                Stat(Modifier.weight(1f), formatBytes(storageBytes), "used")
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String, icon: ImageVector? = null) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** A titled group of rows on one tonal card. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Eyebrow(title, Modifier.padding(start = 6.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    tone: TileTone,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
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
        Text("ScanPro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            Spacer(Modifier.width(6.dp))
            Text(
                "Private by design. No account, nothing uploaded.",
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
        title = { Text("Your scans stay on this phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ScanPro needs no account and sends nothing to a server. Documents, text recognition and PDF tools all run on the device.")
                Text("Files are kept in the app's private storage. Other apps only see a document when you share, print or save it yourself.")
                Text("Translation needs language packs; they are downloaded from Google only when you choose a language and are the one thing that uses the network.")
                Text("Camera access is used for scanning only. No photo library permission is requested; pictures you import come through the system picker.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
