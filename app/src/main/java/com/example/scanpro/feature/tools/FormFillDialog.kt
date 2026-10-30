package com.example.scanpro.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.data.OcrScript
import com.example.scanpro.imaging.FormField
import com.example.scanpro.imaging.FormFieldKind
import com.example.scanpro.imaging.FormFieldRules

/**
 * Full-screen list of a PDF form's fields with an input suited to each. Values live in the view model, so
 * nothing typed is lost on rotation; Save writes only the fields that changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormFillDialog(
    session: FormSession,
    onValueChange: (String, String) -> Unit,
    onSave: (flatten: Boolean) -> Unit,
    onClose: () -> Unit
) {
    var flatten by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val changed = session.changes.size
    val close = { if (changed > 0) confirmDiscard = true else onClose() }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(stringResource(R.string.tools_form_fill_form)) },
                    navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } }
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = flatten, role = Role.Switch, onValueChange = { flatten = it })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.tools_form_flatten), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(
                                    stringResource(R.string.tools_form_flatten_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = flatten, onCheckedChange = null)
                        }
                        val missing = FormFieldRules.missingRequired(session.fields, session.values)
                        if (missing.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.tools_form_required_missing,
                                    missing.size,
                                    missing.size,
                                    missing.take(3).joinToString { it.label } + if (missing.size > 3) "…" else ""
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Button(onClick = { onSave(flatten) }, enabled = missing.isEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(if (changed == 0) stringResource(R.string.common_save) else pluralStringResource(R.plurals.tools_form_save_changes, changed, changed))
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        pluralStringResource(R.plurals.tools_form_field_count, session.fields.size, session.fields.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(session.fields, key = { it.name }) { field ->
                    FieldInput(field, session.values[field.name] ?: field.value) { onValueChange(field.name, it) }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.tools_form_discard_title)) },
            text = { Text(stringResource(R.string.tools_form_discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.tools_form_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.tools_form_keep_editing)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(field: FormField, value: String, onChange: (String) -> Unit) {
    val enabled = !field.readOnly
    val label = when {
        field.readOnly -> stringResource(R.string.tools_form_read_only_label, field.label)
        field.required -> "${field.label} *"
        else -> field.label
    }
    when (field.kind) {
        FormFieldKind.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = { onChange(if (field.maxLength > 0) it.take(field.maxLength) else it) },
            label = { Text(label) },
            enabled = enabled,
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
            supportingText = if (field.maxLength > 0) {
                { Text(stringResource(R.string.tools_form_char_count, value.length, field.maxLength)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        FormFieldKind.CHECKBOX -> Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .toggleable(
                    value = FormFieldRules.isChecked(value),
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onChange(if (it) FormFieldRules.CHECKED else "") }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = FormFieldRules.isChecked(value), onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }

        FormFieldKind.RADIO -> Column(Modifier.alpha(if (enabled) 1f else 0.5f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            field.options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = value == option.value, enabled = enabled, role = Role.RadioButton) { onChange(option.value) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = value == option.value, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Text(option.label)
                }
            }
            if (value.isNotEmpty() && enabled) {
                TextButton(onClick = { onChange("") }) { Text(stringResource(R.string.tools_form_clear_choice)) }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }

        FormFieldKind.CHOICE -> {
            var expanded by rememberSaveable(field.name) { mutableStateOf(false) }
            val shown = field.options.firstOrNull { it.value == value }?.label ?: value
            ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
                OutlinedTextField(
                    value = shown,
                    onValueChange = { if (field.editable) onChange(it) },
                    readOnly = !field.editable,
                    enabled = enabled,
                    label = { Text(label) },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(if (field.editable) ExposedDropdownMenuAnchorType.PrimaryEditable else ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                )
                ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tools_form_none)) }, onClick = { onChange(""); expanded = false })
                    field.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onChange(option.value); expanded = false })
                    }
                }
            }
        }

        FormFieldKind.MULTI_CHOICE -> Column(Modifier.alpha(if (enabled) 1f else 0.5f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            val chosen = FormFieldRules.multiValues(value).toSet()
            field.options.forEach { option ->
                val on = option.value in chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = on, enabled = enabled, role = Role.Checkbox) { checked ->
                            // Kept in the form's own option order.
                            val next = field.options.map { it.value }.filter { if (it == option.value) checked else it in chosen }
                            onChange(FormFieldRules.joinMulti(next))
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Text(option.label)
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
    }
}

/** Asks which writing system the PDF is in before "Make searchable" reads it. */
@Composable
internal fun OcrScriptDialog(onDismiss: () -> Unit, onConfirm: (OcrScript) -> Unit) {
    var script by rememberSaveable { mutableStateOf(OcrScript.LATIN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_ocr_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.tools_ocr_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.tools_ocr_language), style = MaterialTheme.typography.titleSmall)
                OcrScript.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = script == option, role = Role.RadioButton) { script = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = script == option, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (option == OcrScript.LATIN) stringResource(R.string.tools_ocr_latin_label) else stringResource(option.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(script) }) { Text(stringResource(R.string.tools_ocr_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
