package com.example.scanpro.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.scanpro.core.designsystem.PasswordInputDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.BuildConfig
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.openPlayStoreListing
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
    val documentCount by viewModel.documentCount.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    SettingsScreen(
        busy = busy,
        onBackupAll = viewModel::backupAll,
        onRestoreBackup = viewModel::restoreBackup,
        pdfQuality = pdfQuality,
        autoTitle = autoTitle,
        appLock = appLock,
        themeMode = themeMode,
        documentCount = documentCount,
        trashCount = trashCount,
        storageBytes = storageBytes,
        snackbarHostState = snackbarHostState,
        onPdfQualityChange = viewModel::setPdfQuality,
        onAutoTitleChange = viewModel::setAutoTitle,
        onAppLockChange = onAppLockChange,
        onThemeModeChange = viewModel::setThemeMode,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    busy: Boolean = false,
    onBackupAll: (Uri, String) -> Unit = { _, _ -> },
    onRestoreBackup: (Uri, String) -> Unit = { _, _ -> },
    pdfQuality: PdfQuality,
    autoTitle: Boolean,
    appLock: Boolean,
    themeMode: ThemeMode,
    documentCount: Int,
    trashCount: Int,
    storageBytes: Long,
    snackbarHostState: SnackbarHostState,
    onPdfQualityChange: (PdfQuality) -> Unit,
    onAutoTitleChange: (Boolean) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_settings), fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 1: Feedback & Feature Requests
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { openPlayStoreListing(context) },
                        headlineContent = { Text(stringResource(R.string.settings_feedback), fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Feedback, contentDescription = null) }
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { openPlayStoreListing(context) },
                        headlineContent = { Text(stringResource(R.string.settings_request_features), fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null) }
                    )
                }
            }

            // Group 2: Scanning & PDF Defaults
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_pdf_quality_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.settings_pdf_quality_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfQuality.entries) { quality ->
                            FilterChip(
                                selected = quality == pdfQuality,
                                onClick = { onPdfQualityChange(quality) },
                                label = { Text(stringResource(quality.labelRes)) }
                            )
                        }
                    }
                }
            }

            // Group 3: App Theme & Security
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ThemeMode.entries) { mode ->
                                FilterChip(
                                    selected = mode == themeMode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(stringResource(mode.labelRes)) }
                                )
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= 33) {
                        val languageUnavailable = stringResource(R.string.settings_app_language_unavailable)
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) {
                                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, languageUnavailable, Toast.LENGTH_SHORT).show()
                                }
                            },
                            headlineContent = { Text(stringResource(R.string.settings_app_language), fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(stringResource(R.string.settings_app_language_subtitle)) },
                            leadingContent = { Icon(Icons.Default.Language, contentDescription = null) }
                        )
                    }

                    // Each switch row is one control for a screen reader: title, hint and state together.
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(value = autoTitle, role = Role.Switch, onValueChange = onAutoTitleChange),
                        headlineContent = { Text(stringResource(R.string.settings_smart_titles_title), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(stringResource(R.string.settings_smart_titles_body)) },
                        leadingContent = { Icon(Icons.Default.Title, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = autoTitle,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    )

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(value = appLock, role = Role.Switch, onValueChange = onAppLockChange),
                        headlineContent = { Text(stringResource(R.string.settings_app_lock_title), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(stringResource(R.string.settings_app_lock_body)) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = appLock,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    )
                }
            }

            // Group 4: Legal & App Details
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { openPlayStoreListing(context) },
                        headlineContent = { Text(stringResource(R.string.settings_share_with_friends), fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    // Backup: every document as its own encrypted file in a folder the user picks (a cloud drive
                    // works too); restore reads one such file back. Documents never leave the device otherwise.
                    var backupFolder by remember { mutableStateOf<Uri?>(null) }
                    var restoreFile by remember { mutableStateOf<Uri?>(null) }
                    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        uri?.let {
                            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            backupFolder = it
                        }
                    }
                    val pickRestoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreFile = uri }
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = !busy, role = Role.Button) { pickBackupFolder.launch(null) },
                        headlineContent = { Text(stringResource(R.string.settings_backup_all), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_all_subtitle)) },
                        leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) }
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = !busy, role = Role.Button) { pickRestoreFile.launch(arrayOf("*/*")) },
                        headlineContent = { Text(stringResource(R.string.settings_restore_backup), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(stringResource(R.string.settings_restore_backup_subtitle)) },
                        leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) }
                    )
                    backupFolder?.let { folder ->
                        PasswordInputDialog(
                            title = stringResource(R.string.settings_backup_all),
                            message = stringResource(R.string.settings_backup_password_message),
                            confirmLabel = stringResource(R.string.settings_backup_start),
                            onDismiss = { backupFolder = null }
                        ) { password -> onBackupAll(folder, password); backupFolder = null }
                    }
                    restoreFile?.let { file ->
                        PasswordInputDialog(
                            title = stringResource(R.string.settings_restore_backup),
                            message = stringResource(R.string.settings_restore_password_message),
                            confirmLabel = stringResource(R.string.settings_restore_start),
                            onDismiss = { restoreFile = null }
                        ) { password -> onRestoreBackup(file, password); restoreFile = null }
                    }
                    val linkUnavailable = stringResource(R.string.settings_link_unavailable)
                    val openLink: (String) -> Unit = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, linkUnavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) { openLink(BuildConfig.PRIVACY_POLICY_URL) },
                            headlineContent = { Text(stringResource(R.string.settings_privacy_policy), fontWeight = FontWeight.Medium) },
                            leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) }
                        )
                    }
                    if (BuildConfig.TERMS_URL.isNotBlank()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) { openLink(BuildConfig.TERMS_URL) },
                            headlineContent = { Text(stringResource(R.string.settings_terms_of_use), fontWeight = FontWeight.Medium) },
                            leadingContent = { Icon(Icons.Default.Description, contentDescription = null) }
                        )
                    }
                }
            }

            // Storage & Version info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.settings_storage_summary, documentCount, trashCount, formatBytes(storageBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
