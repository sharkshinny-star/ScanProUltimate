package com.example.scanpro.feature.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.RecentPdf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RecentPdfsHeader(onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.tools_recent_pdfs),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.tools_recent_clear)) }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.tools_recent_clear_title)) },
            text = { Text(stringResource(R.string.tools_recent_clear_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.tools_recent_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/**
 * One PDF opened before, with its first page, size and when it was opened. Tap to read; the overflow menu opens it,
 * sends it straight into a tool, or removes it from the list (the file itself is not touched).
 */
@Composable
internal fun RecentPdfRow(pdf: RecentPdf, onOpen: () -> Unit, onUseWith: (String) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var toolsOpen by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val size by produceState(0L, pdf.uri) { value = withContext(Dispatchers.IO) { sizeOf(context, pdf.uri) } }
    Box {
        FileRow(
            name = pdf.name,
            details = fileDetails(context, "pdf", size, pdf.openedAt),
            thumbnail = { FileThumbnail(pdf.uri, "pdf", "${pdf.uri}|${pdf.openedAt}") },
            onOpen = onOpen
        ) { close ->
            DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_open)) }, onClick = { close(); onOpen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_use_with_tool)) }, onClick = { close(); toolsOpen = true })
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.tools_recent_remove_from_recent)) }, onClick = { close(); removing = true })
        }
        // The same file straight into a tool, without picking it again.
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                PDF_TOOLS.filter { it.id !in setOf("merge", "img2pdf") }.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text(stringResource(tool.title)) },
                        leadingIcon = { Icon(tool.icon, contentDescription = null) },
                        onClick = { toolsOpen = false; onUseWith(tool.id) }
                    )
                }
            }
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.tools_recent_remove_title)) },
            text = { Text(stringResource(R.string.tools_recent_remove_body, pdf.name)) },
            confirmButton = { TextButton(onClick = { removing = false; onRemove() }) { Text(stringResource(R.string.tools_recent_remove)) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
