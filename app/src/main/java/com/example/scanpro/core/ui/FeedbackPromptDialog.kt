package com.example.scanpro.core.ui

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
        title = { Text("How was your scanning experience? \uD83D\uDE0A") },
        text = { Text("A line or two about what worked and what didn't helps make ScanPro better. It opens in your email app.") },
        confirmButton = { TextButton(onClick = onShareFeedback) { Text("Share feedback") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Maybe later") } }
    )
}
