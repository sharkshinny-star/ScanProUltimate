package com.example.scanpro.core.ui

import com.example.scanpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * One-time question after a few successful scans. "Share feedback" opens the feedback email; it never
 * leads to the Play review card, which Google's guidelines say must not follow a question like this.
 */
@Composable
fun FeedbackPromptDialog(onShareFeedback: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.feedback_prompt_title)) },
        text = { Text(stringResource(R.string.feedback_prompt_body)) },
        confirmButton = { TextButton(onClick = onShareFeedback) { Text(stringResource(R.string.feedback_prompt_share)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.feedback_prompt_later)) } }
    )
}
