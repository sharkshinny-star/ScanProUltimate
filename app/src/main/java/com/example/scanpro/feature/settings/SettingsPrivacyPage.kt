package com.example.scanpro.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.scanpro.BuildConfig
import com.example.scanpro.R
import com.example.scanpro.core.ui.SupportLinks

/** Privacy & security: app lock, the privacy facts, and the policy and terms (only when this build has them). */
@Composable
internal fun PrivacyPage(appLock: Boolean, onAppLockChange: (Boolean) -> Unit) = SettingsPageColumn {
    val context = LocalContext.current
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    if (privacyOpen) PrivacyDialog(onDismiss = { privacyOpen = false })
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

    SettingsGroup {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_app_lock_title),
            icon = Icons.Rounded.Fingerprint,
            summary = stringResource(R.string.settings_app_lock_body),
            checked = appLock,
            onCheckedChange = onAppLockChange
        )
        SettingsRow(
            title = stringResource(R.string.settings_privacy_title),
            icon = Icons.Rounded.PrivacyTip,
            summary = stringResource(R.string.settings_privacy_subtitle),
            onClick = { privacyOpen = true }
        )
    }
    if (privacyPolicyUrl.isNotBlank() || termsUrl.isNotBlank()) {
        SettingsGroup(header = stringResource(R.string.settings_group_legal)) {
            if (privacyPolicyUrl.isNotBlank()) {
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_policy),
                    icon = Icons.Rounded.Policy,
                    onClick = { openLink(privacyPolicyUrl) }
                )
            }
            if (termsUrl.isNotBlank()) {
                SettingsRow(
                    title = stringResource(R.string.settings_terms_of_use),
                    icon = Icons.Rounded.Description,
                    onClick = { openLink(termsUrl) }
                )
            }
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
