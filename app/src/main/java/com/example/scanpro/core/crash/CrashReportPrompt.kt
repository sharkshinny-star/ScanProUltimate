package com.example.scanpro.core.crash

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.example.scanpro.R
import java.io.File

/** Once after a crash: offers to share the saved report. Shown nowhere else, and never twice for the same crash. */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<File?>(null) }
    // Read once per process start; the prompt is dismissed by clearing the state.
    val initial = remember { CrashReporter.pendingReport(context).also { report = it } }
    val current = report ?: return
    if (initial == null) return

    fun done() {
        CrashReporter.markOffered(context, current)
        report = null
    }

    AlertDialog(
        onDismissRequest = { done() },
        title = { Text(stringResource(R.string.crash_prompt_title)) },
        text = { Text(stringResource(R.string.crash_prompt_body)) },
        confirmButton = {
            val subject = stringResource(R.string.crash_share_subject)
            val chooserTitle = stringResource(R.string.crash_prompt_share)
            TextButton(onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", current)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, runCatching { current.readText().take(4000) }.getOrDefault(""))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
                done()
            }) { Text(stringResource(R.string.crash_prompt_share)) }
        },
        dismissButton = { TextButton(onClick = { done() }) { Text(stringResource(R.string.crash_prompt_dismiss)) } }
    )
}
