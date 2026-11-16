package com.example.scanpro.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.feature.feedback.FeedbackFormKind
import com.example.scanpro.feature.feedback.FeedbackFormSheet
import com.example.scanpro.feature.feedback.RateExperienceSheet
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.sharePlayStoreListing
import com.example.scanpro.data.FirstRunHints
import com.example.scanpro.scanner.resetScanIntros
import com.example.scanpro.ui.WhatsNewSheet
import kotlinx.coroutines.launch

/** Feedback & support: help first, then the ways to get in touch, then rating and sharing. */
@Composable
internal fun SupportPage(snackbarHostState: SnackbarHostState) = SettingsPageColumn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var whatsNewOpen by rememberSaveable { mutableStateOf(false) }
    if (whatsNewOpen) {
        WhatsNewSheet(onPickColours = { whatsNewOpen = false }, onDismiss = { whatsNewOpen = false })
    }
    // Give feedback opens "Rate your experience"; Report an issue and Suggest a feature open the form. Each ends in
    // the user's email app with the message filled in.
    var rateOpen by rememberSaveable { mutableStateOf(false) }
    var form by rememberSaveable { mutableStateOf<FeedbackFormKind?>(null) }
    if (rateOpen) RateExperienceSheet(onClose = { rateOpen = false })
    form?.let { kind ->
        FeedbackFormSheet(
            kind = kind,
            onSent = { delivery -> scope.launch { snackbarHostState.showSnackbar(delivery.confirmation(context)) } },
            onClose = { form = null }
        )
    }

    SettingsGroup(header = stringResource(R.string.settings_group_help)) {
        SettingsRow(
            title = stringResource(R.string.settings_whats_new_title),
            icon = Icons.Rounded.AutoAwesome,
            summary = stringResource(R.string.settings_whats_new_subtitle),
            onClick = { whatsNewOpen = true }
        )
        // F146: the Home tour, the tool introductions and the camera's mode explainers all show once more.
        val introsReset = stringResource(R.string.settings_show_intros_done)
        SettingsRow(
            title = stringResource(R.string.settings_show_intros_title),
            icon = Icons.Rounded.Replay,
            summary = stringResource(R.string.settings_show_intros_subtitle),
            onClick = {
                FirstRunHints.reset(context)
                resetScanIntros(context)
                scope.launch { snackbarHostState.showSnackbar(introsReset) }
            }
        )
    }

    SettingsGroup(header = stringResource(R.string.settings_group_contact)) {
        SettingsRow(
            title = stringResource(R.string.settings_feedback),
            icon = Icons.Rounded.Feedback,
            summary = stringResource(R.string.settings_feedback_subtitle),
            onClick = { rateOpen = true }
        )
        SettingsRow(
            title = stringResource(R.string.settings_report_issue_title),
            icon = Icons.Rounded.BugReport,
            summary = stringResource(R.string.settings_report_issue_subtitle),
            onClick = { form = FeedbackFormKind.ISSUE }
        )
        SettingsRow(
            title = stringResource(R.string.settings_request_features),
            icon = Icons.Rounded.Lightbulb,
            onClick = { form = FeedbackFormKind.FEATURE }
        )
    }

    SettingsGroup(header = stringResource(R.string.settings_group_spread)) {
        SettingsRow(
            title = stringResource(R.string.settings_rate_title),
            icon = Icons.Rounded.Star,
            summary = stringResource(R.string.settings_rate_subtitle),
            onClick = { openPlayStoreListing(context) }
        )
        SettingsRow(
            title = stringResource(R.string.settings_share_with_friends),
            icon = Icons.Rounded.Share,
            summary = stringResource(R.string.settings_share_subtitle),
            onClick = { sharePlayStoreListing(context) }
        )
    }
}
