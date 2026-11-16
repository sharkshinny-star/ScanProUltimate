package com.example.scanpro.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.TileTone

/** The Notifications page's state and actions, gathered so the screen's signature stays readable. */
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
internal fun NotificationsPage(state: NotificationSettings) = SettingsPageColumn {
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
    if (!state.canPost) {
        // On its own card above the switches: it is the one thing on the page that needs doing.
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.settings_notifications_off_title),
                icon = Icons.Rounded.NotificationsOff,
                tone = TileTone.Rose,
                summary = stringResource(R.string.settings_notifications_off_body),
                onClick = requestPermission
            )
        }
    }
    SettingsGroup(header = stringResource(R.string.settings_group_notify_about)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_notify_finished_title),
            icon = Icons.Rounded.TaskAlt,
            summary = stringResource(R.string.settings_notify_finished_body),
            checked = state.finished,
            onCheckedChange = enabling(state.onFinishedChange)
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_notify_failures_title),
            icon = Icons.Rounded.ErrorOutline,
            summary = stringResource(R.string.settings_notify_failures_body),
            checked = state.failures,
            onCheckedChange = enabling(state.onFailuresChange)
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_notify_trash_title),
            icon = Icons.Rounded.AutoDelete,
            summary = stringResource(R.string.settings_notify_trash_body),
            checked = state.trash,
            onCheckedChange = enabling(state.onTrashChange)
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_notify_backup_title),
            icon = Icons.Rounded.Backup,
            summary = stringResource(R.string.settings_notify_backup_body),
            checked = state.backup,
            onCheckedChange = enabling(state.onBackupChange)
        )
    }
    if (state.systemSettingsIntent != null) {
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.settings_notifications_system_title),
                icon = Icons.Rounded.Notifications,
                summary = stringResource(R.string.settings_notifications_system_body),
                onClick = openSystemSettings
            )
        }
    }
}
