package com.example.scanpro.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.OcrScript
import com.example.scanpro.imaging.PageInsertion
import com.example.scanpro.imaging.TextExportFormat

/**
 * A protected PDF that PDF to text or Insert pages reached: it is shown as [fileName], and [wrongPassword] is true when
 * the last password typed for it did not work. [attempt] numbers the questions, so each one opens a fresh dialog.
 */
data class SourcePasswordRequest(
    val fileName: String,
    val wrongPassword: Boolean,
    val attempt: Int,
    val forInsert: Boolean
)

/**
 * Asks for the output (Word, Excel or plain text) and the writing system, which pages without text are read in.
 * [initialFormat] is the one chosen when the dialog opens: the PDF to Excel tile opens it on Excel.
 */
@Composable
internal fun PdfToTextDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (TextExportFormat, OcrScript) -> Unit,
    initialFormat: TextExportFormat = TextExportFormat.WORD
) {
    var format by rememberSaveable { mutableStateOf(initialFormat) }
    var script by rememberSaveable { mutableStateOf(OcrScript.LATIN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_pdf2text_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PickedFileRow(uri)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tools_pdf2text_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tools_pdf2text_format), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(selected = format == TextExportFormat.WORD, label = stringResource(R.string.tools_pdf2text_word)) { format = TextExportFormat.WORD }
                ChoiceRow(selected = format == TextExportFormat.EXCEL, label = stringResource(R.string.tools_pdf2text_excel)) { format = TextExportFormat.EXCEL }
                ChoiceRow(selected = format == TextExportFormat.TEXT, label = stringResource(R.string.tools_pdf2text_plain)) { format = TextExportFormat.TEXT }
                if (format == TextExportFormat.EXCEL) {
                    Text(
                        stringResource(R.string.tools_pdf2text_excel_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tools_ocr_language), style = MaterialTheme.typography.titleSmall)
                OcrScript.entries.forEach { option ->
                    ChoiceRow(
                        selected = script == option,
                        label = if (option == OcrScript.LATIN) stringResource(R.string.tools_ocr_latin_label) else stringResource(option.labelRes)
                    ) { script = option }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.tools_pdf2text_unprotected_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(format, script) }) {
                Text(stringResource(R.string.tools_ocr_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * The password of a protected PDF that PDF to text or Insert pages needs. It says that what comes out has no password,
 * so a protected file never quietly turns into an unprotected one.
 */
@Composable
internal fun SourcePasswordDialog(
    request: SourcePasswordRequest,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val enterPasswordError = stringResource(R.string.tools_password_enter)
    val wrongError = if (request.wrongPassword) stringResource(R.string.tools_wrong_password_try_again) else null
    val shownError = errorText ?: wrongError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_pdf_password_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (request.forInsert) R.string.tools_source_password_prompt_insert else R.string.tools_source_password_prompt_text,
                        request.fileName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (request.forInsert) R.string.tools_source_password_note_insert else R.string.tools_source_password_note_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text(stringResource(R.string.tools_password_label)) },
                    singleLine = true,
                    isError = shownError != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    // Password type even while shown, so the keyboard never learns or suggests it.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tools_password_toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (shownError != null) {
                    Text(
                        shownError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (password.isEmpty()) errorText = enterPasswordError else onConfirm(password) }) {
                Text(stringResource(R.string.common_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private const val POSITION_START = 0
private const val POSITION_AFTER = 1
private const val POSITION_END = 2

/**
 * Organise's "insert pages from another PDF": which PDF, which of its pages, and where among the [keptPages] pages of the
 * PDF being organised. Ranges are checked against the second PDF when the pages are inserted.
 */
@Composable
internal fun InsertPagesDialog(
    keptPages: Int,
    nameOf: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (PageInsertion) -> Unit
) {
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var rangeText by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableIntStateOf(POSITION_END) }
    var afterText by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = uri
    }
    val source = picked
    val sourceName = remember(source) { source?.let(nameOf) }
    val after = afterText.trim().toIntOrNull()
    val afterValid = position != POSITION_AFTER || (after != null && after in 1..keptPages)
    val rangeValid = allPages || rangeText.isNotBlank()
    val ready = source != null && afterValid && rangeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_insert_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tools_insert_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(sourceName ?: stringResource(R.string.tools_insert_choose_pdf), maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tools_insert_which_pages), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(selected = allPages, label = stringResource(R.string.tools_insert_all_pages)) { allPages = true }
                ChoiceRow(selected = !allPages, label = stringResource(R.string.tools_insert_page_range)) { allPages = false }
                if (!allPages) {
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        label = { Text(stringResource(R.string.tools_insert_range_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tools_insert_where), style = MaterialTheme.typography.titleSmall)
                ChoiceRow(selected = position == POSITION_START, label = stringResource(R.string.tools_insert_at_start)) { position = POSITION_START }
                ChoiceRow(selected = position == POSITION_AFTER, label = stringResource(R.string.tools_insert_after_page)) { position = POSITION_AFTER }
                if (position == POSITION_AFTER) {
                    OutlinedTextField(
                        value = afterText,
                        onValueChange = { afterText = it },
                        label = { Text(stringResource(R.string.tools_insert_after_label, keptPages)) },
                        singleLine = true,
                        isError = afterText.isNotEmpty() && !afterValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ChoiceRow(selected = position == POSITION_END, label = stringResource(R.string.tools_insert_at_end)) { position = POSITION_END }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    val afterPosition = when (position) {
                        POSITION_START -> 0
                        POSITION_AFTER -> after ?: 0
                        else -> keptPages
                    }
                    // The button is enabled only with a file chosen and a valid position.
                    if (source != null) onConfirm(PageInsertion(source, if (allPages) null else rangeText.trim(), afterPosition))
                }
            ) { Text(stringResource(R.string.tools_insert_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
