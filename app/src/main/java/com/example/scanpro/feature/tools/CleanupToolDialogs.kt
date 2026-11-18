package com.example.scanpro.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.PdfInfo
import com.example.scanpro.imaging.PdfMetadataEdit
import java.text.DateFormat
import java.util.Date

/** A tool that runs on the whole file once confirmed (Greyscale, Flatten): what it will do, and the go-ahead. */
@Composable
internal fun ToolConfirmDialog(
    icon: ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/**
 * PDF info and metadata: the facts about the file, the four details a person can edit, and a one-tap "Remove all
 * metadata" for privacy. Both write a new copy.
 */
@Composable
internal fun PdfInfoDialog(
    info: PdfInfo,
    onDismiss: () -> Unit,
    onSave: (PdfMetadataEdit) -> Unit,
    onRemoveAll: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(info.title) }
    var author by rememberSaveable { mutableStateOf(info.author) }
    var subject by rememberSaveable { mutableStateOf(info.subject) }
    var keywords by rememberSaveable { mutableStateOf(info.keywords) }
    val changed = title != info.title || author != info.author || subject != info.subject || keywords != info.keywords
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_card_pdf_info), textAlign = TextAlign.Center) },
        text = {
            PdfInfoContent(
                info = info, title = title, author = author, subject = subject, keywords = keywords,
                onTitle = { title = it }, onAuthor = { author = it }, onSubject = { subject = it }, onKeywords = { keywords = it },
                onRemoveAll = onRemoveAll,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            Button(onClick = { onSave(PdfMetadataEdit(title, author, subject, keywords)) }, enabled = changed) {
                Text(stringResource(R.string.tools_info_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

/** The facts and the editable details of [PdfInfoDialog], apart from the dialog so it can be drawn on its own. */
@Composable
internal fun PdfInfoContent(
    info: PdfInfo,
    title: String,
    author: String,
    subject: String,
    keywords: String,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onSubject: (String) -> Unit,
    onKeywords: (String) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notSet = stringResource(R.string.tools_info_not_set)
    val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Column(modifier) {
        InfoRow(stringResource(R.string.tools_info_pages), info.pageCount.toString())
        InfoRow(stringResource(R.string.tools_info_version), info.version)
        InfoRow(stringResource(R.string.tools_info_size), formatBytes(androidx.compose.ui.platform.LocalContext.current, info.sizeBytes))
        InfoRow(stringResource(R.string.tools_info_created), info.created?.let { dates.format(Date(it)) } ?: notSet)
        InfoRow(stringResource(R.string.tools_info_modified), info.modified?.let { dates.format(Date(it)) } ?: notSet)
        InfoRow(stringResource(R.string.tools_info_creator), info.creator.ifEmpty { notSet })
        InfoRow(stringResource(R.string.tools_info_producer), info.producer.ifEmpty { notSet })
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            stringResource(R.string.tools_info_edit_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        InfoField(stringResource(R.string.tools_info_field_title), title, onTitle)
        InfoField(stringResource(R.string.tools_info_field_author), author, onAuthor)
        InfoField(stringResource(R.string.tools_info_field_subject), subject, onSubject)
        InfoField(stringResource(R.string.tools_info_field_keywords), keywords, onKeywords)
        if (info.hasXmp) {
            Text(
                stringResource(R.string.tools_info_xmp_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        OutlinedButton(onClick = onRemoveAll, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PrivacyTip, contentDescription = null)
            Spacer(Modifier.padding(start = 8.dp))
            Text(stringResource(R.string.tools_info_remove_all))
        }
        Text(
            stringResource(R.string.tools_info_remove_all_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun InfoField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}
