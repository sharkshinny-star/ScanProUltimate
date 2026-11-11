package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the options step of a tool says about the file it is about to work on. */
private class PickedFile(val name: String, val sizeBytes: Long, val pages: Int?, val isPdf: Boolean)

/** Name, size and (for a PDF that opens without a password) page count of [uri]. Reads disk: call off the main thread. */
private fun readPickedFile(context: Context, uri: Uri): PickedFile {
    var name: String? = null
    var size = 0L
    if (uri.scheme == "file") {
        val file = File(uri.path.orEmpty())
        name = file.name
        size = file.length()
    } else {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                }
            }
        }
    }
    val shownName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    val isPdf = shownName.endsWith(".pdf", ignoreCase = true) ||
        runCatching { context.contentResolver.getType(uri) }.getOrNull() == "application/pdf"
    // A protected or damaged PDF cannot be counted; the row then shows the size alone.
    val pages = if (!isPdf) null else runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { PdfRenderer(it).use { renderer -> renderer.pageCount } }
    }.getOrNull()
    return PickedFile(shownName, size, pages, isPdf)
}

/**
 * The file a tool is about to change, at the top of its options: its name, size and page count. Between the system
 * picker and the result nothing used to say which file had been chosen, so a wrong pick was only seen at the end.
 */
@Composable
internal fun PickedFileRow(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info by produceState<PickedFile?>(null, uri) { value = withContext(Dispatchers.IO) { readPickedFile(context, uri) } }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (info?.isPdf == false) Icons.Default.Image else Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                val details = info?.let { file ->
                    listOfNotNull(
                        file.sizeBytes.takeIf { it > 0 }?.let { formatBytes(context, it) },
                        file.pages?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.doc_page_count, it, it) }
                    ).joinToString(" · ")
                }.orEmpty()
                // Always one line tall, so the dialog does not jump when the details arrive.
                Text(
                    details,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Shown when a tool could not finish. A message at the bottom of the screen vanished after a few seconds and left the
 * user to start again from the tile; this stays until answered and offers the two useful ways on.
 */
@Composable
internal fun ToolFailureDialog(
    message: String,
    onRetry: (() -> Unit)?,
    onChooseAnother: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.tools_failure_title)) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (onChooseAnother != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onChooseAnother, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.tools_failure_choose_another))
                    }
                }
            }
        },
        confirmButton = {
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.tools_failure_try_again)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
        dismissButton = {
            if (onRetry != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** Asked before a running tool is stopped: one stray Back press used to throw away minutes of work. */
@Composable
internal fun StopTaskDialog(onKeepGoing: () -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text(stringResource(R.string.tools_stop_title)) },
        text = { Text(stringResource(R.string.tools_stop_body)) },
        confirmButton = { Button(onClick = onKeepGoing) { Text(stringResource(R.string.tools_stop_keep_going)) } },
        dismissButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.common_stop)) } }
    )
}

/** The tools worth offering on a finished file, most likely first, for a PDF or a picture result. */
internal fun nextToolsFor(extension: String, justUsed: String?): List<PdfToolCard> {
    val ids = when (extension.lowercase()) {
        "pdf" -> listOf("compress", "protect", "sign", "watermark", "page_numbers", "organize", "split", "pdf2img")
        "jpg", "jpeg", "png", "webp" -> listOf("compress_img", "resize_img", "crop_img", "convert_img")
        else -> emptyList()
    }
    return ids.filter { it != justUsed }.mapNotNull { id -> ALL_TOOLS.firstOrNull { it.id == id } }
}

/**
 * "Continue with this file" on the result: the finished file goes straight into the next tool (compress, then
 * protect, then sign) without saving it and picking it again from the phone's files.
 */
@Composable
internal fun NextToolsRow(tools: List<PdfToolCard>, onPick: (PdfToolCard) -> Unit, modifier: Modifier = Modifier) {
    if (tools.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tools_result_continue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { tool ->
                AssistChip(
                    onClick = { onPick(tool) },
                    label = { Text(stringResource(tool.title), maxLines = 1) },
                    leadingIcon = { Icon(tool.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}
