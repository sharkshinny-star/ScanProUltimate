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
    onRemoveAll: () -> Unit,
    uri: android.net.Uri? = null
) {
    var title by rememberSaveable { mutableStateOf(info.title) }
    var author by rememberSaveable { mutableStateOf(info.author) }
    var subject by rememberSaveable { mutableStateOf(info.subject) }
    var keywords by rememberSaveable { mutableStateOf(info.keywords) }
    val changed = title != info.title || author != info.author || subject != info.subject || keywords != info.keywords
    // A full tool page (F211) instead of a pop-up: the facts, the fields and the button stay in reach at any font size.
    ToolPage(
        title = stringResource(R.string.tools_card_pdf_info),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_info_save),
        onAction = { onSave(PdfMetadataEdit(title, author, subject, keywords)) },
        actionEnabled = changed,
        actionSupport = if (changed) stringResource(R.string.cvt_info_save_support) else stringResource(R.string.cvt_info_change_to_save),
        demoToolId = "pdf_info"
    ) {
        if (uri != null) PickedFileRow(uri)
        PdfInfoContent(
            info = info, title = title, author = author, subject = subject, keywords = keywords,
            onTitle = { title = it }, onAuthor = { author = it }, onSubject = { subject = it }, onKeywords = { keywords = it },
            onRemoveAll = onRemoveAll
        )
    }
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
        pdfPageSizeText(info)?.let { InfoRow(stringResource(R.string.cvt_info_page_size), it) }
        InfoRow(stringResource(R.string.tools_info_version), info.version)
        // The exact byte count too: "1.2 MB" alone hides whether the phone counts 1000 or 1024 to a kilobyte.
        InfoRow(
            stringResource(R.string.tools_info_size),
            formatBytes(androidx.compose.ui.platform.LocalContext.current, info.sizeBytes) + "\n" +
                stringResource(R.string.cvt_info_bytes, java.text.NumberFormat.getIntegerInstance().format(info.sizeBytes))
        )
        InfoRow(stringResource(R.string.cvt_info_security), stringResource(if (info.encrypted) R.string.cvt_info_encrypted else R.string.cvt_info_not_encrypted))
        InfoRow(stringResource(R.string.cvt_info_form_fields), if (info.formFields > 0) info.formFields.toString() else stringResource(R.string.cvt_info_none))
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

/** "A4 · 210 × 297 mm (8.27 × 11.69 in) · Portrait", or null when the page size is not known. */
@Composable
private fun pdfPageSizeText(info: PdfInfo): String? {
    val w = info.pageWidthPt
    val h = info.pageHeightPt
    if (w <= 0f || h <= 0f) return null
    val number = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }
    val mm = stringResource(
        R.string.cvt_size_mm,
        kotlin.math.round(com.example.scanpro.imaging.PaperSizes.toMillimetres(w)).toInt(),
        kotlin.math.round(com.example.scanpro.imaging.PaperSizes.toMillimetres(h)).toInt()
    )
    val inches = stringResource(
        R.string.cvt_size_in,
        number.format(com.example.scanpro.imaging.PaperSizes.toInches(w).toDouble()),
        number.format(com.example.scanpro.imaging.PaperSizes.toInches(h).toDouble())
    )
    val orientation = stringResource(if (w > h) R.string.cvt_landscape else R.string.cvt_portrait)
    val parts = listOfNotNull(com.example.scanpro.imaging.PaperSizes.name(w, h), "$mm ($inches)", orientation)
    val text = parts.joinToString(" · ")
    return if (info.mixedPageSizes) text + "\n" + stringResource(R.string.cvt_info_mixed_sizes) else text
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
