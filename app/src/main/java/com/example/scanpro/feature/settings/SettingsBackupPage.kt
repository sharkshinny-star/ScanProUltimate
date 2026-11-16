package com.example.scanpro.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.backup.EncryptedDocumentPackage
import com.example.scanpro.core.designsystem.PasswordInputDialog
import com.example.scanpro.data.TRASH_RETENTION_CHOICES
import kotlinx.coroutines.launch

/**
 * Backup: every document as its own encrypted file in a folder the user picks (a cloud drive works too); restore reads
 * one such file back. Documents never leave the device otherwise. Recover, rarely needed, sits under Advanced.
 */
@Composable
internal fun BackupPage(
    busy: Boolean,
    lastBackupAt: Long,
    documentCount: Int,
    snackbarHostState: SnackbarHostState,
    onBackupAll: (Uri, String) -> Unit,
    onRestoreBackup: (Uri, String) -> Unit,
    onRecoverLibrary: () -> Unit
) = SettingsPageColumn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupFolder by rememberSaveable { mutableStateOf<Uri?>(null) }
    var backupIntroOpen by rememberSaveable { mutableStateOf(false) }
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
    val nothingToBackUp = stringResource(R.string.settings_backup_nothing)

    SettingsGroup {
        // The summary is the backup's state; a nudge only makes sense when there is something to lose.
        val warn = lastBackupAt <= 0L && documentCount > 0
        SettingsRow(
            title = stringResource(R.string.settings_backup_all),
            icon = Icons.Rounded.Backup,
            summary = backupStatusText(lastBackupAt),
            enabled = !busy,
            trailing = if (warn) {
                { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            } else null,
            onClick = {
                if (documentCount == 0) scope.launch { snackbarHostState.showSnackbar(nothingToBackUp) } else backupIntroOpen = true
            }
        )
        SettingsRow(
            title = stringResource(R.string.settings_restore_backup),
            icon = Icons.Rounded.Restore,
            summary = stringResource(R.string.settings_restore_backup_subtitle),
            enabled = !busy,
            onClick = { backupPicker.launch(arrayOf("*/*")) }
        )
    }

    SettingsGroup(header = stringResource(R.string.settings_group_advanced)) {
        SettingsRow(
            title = stringResource(R.string.settings_recover_library),
            icon = Icons.Rounded.SettingsBackupRestore,
            summary = stringResource(R.string.settings_recover_library_subtitle),
            enabled = !busy,
            onClick = onRecoverLibrary
        )
    }
}

/** Recycle bin: open it, and how long it keeps deleted documents. */
@Composable
internal fun RecycleBinPage(
    trashCount: Int,
    trashRetentionDays: Int,
    onTrashRetentionChange: (Int) -> Unit,
    onOpenRecycleBin: () -> Unit
) = SettingsPageColumn {
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_recycle_bin_open),
            icon = Icons.Rounded.DeleteOutline,
            summary = recycleBinCountText(trashCount),
            onClick = onOpenRecycleBin
        )
        SettingsMenuRow(
            title = stringResource(R.string.settings_trash_retention_title),
            icon = Icons.Rounded.AutoDelete,
            options = TRASH_RETENTION_CHOICES,
            selected = trashRetentionDays,
            onSelect = onTrashRetentionChange,
            summary = pluralStringResource(R.plurals.settings_trash_retention_days, trashRetentionDays, trashRetentionDays) +
                " · " + stringResource(R.string.settings_trash_retention_subtitle)
        ) { days -> pluralStringResource(R.plurals.settings_trash_retention_days, days, days) }
    }
}

@Composable
internal fun recycleBinCountText(trashCount: Int): String =
    if (trashCount == 0) stringResource(R.string.settings_recycle_bin_empty)
    else pluralStringResource(R.plurals.settings_recycle_bin_count, trashCount, trashCount)
