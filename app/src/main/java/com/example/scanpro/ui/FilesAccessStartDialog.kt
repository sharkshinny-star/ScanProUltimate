package com.example.scanpro.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FilesAccessScene
import com.example.scanpro.core.designsystem.IllustratedDialog
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.ToolDemo
import com.example.scanpro.feature.phonepdfs.PhonePdfSource

/**
 * Asks once at start for access to the files on the phone (F196, owner's request), for people who went through the
 * welcome before it asked. Says what the access is for and what Android will show next; Not now leaves the app fully
 * usable (Files keeps its own access card).
 */
@Composable
internal fun FilesAccessStartDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    IllustratedDialog(
        title = stringResource(R.string.files_start_title),
        body = stringResource(
            if (PhonePdfSource.usesSettingsPage) R.string.ui_permission_files_body_settings else R.string.ui_permission_files_body_dialog
        ),
        confirmText = stringResource(R.string.files_start_allow),
        onConfirm = onAllow,
        onDismiss = onNotNow,
        dismissText = stringResource(R.string.ui_permissions_not_now),
        badgeIcon = Icons.Rounded.Folder,
        badgeTone = TileTone.Teal,
        illustration = { ToolDemo(FilesAccessScene, Modifier.fillMaxSize()) }
    )
}
