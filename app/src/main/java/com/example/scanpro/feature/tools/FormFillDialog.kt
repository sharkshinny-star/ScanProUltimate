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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            topBar = {
                TopAppBar(
                    title = { Text("Fill form") },
                    navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "Close") } }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = flatten, role = Role.Switch, onValueChange = { flatten = it })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Flatten", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Turns the answers into part of the page, so they can't be changed later",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = flatten, onCheckedChange = null)
                        }
                        Button(onClick = { onSave(flatten) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(if (changed == 0) "Save" else "Save ($changed ${if (changed == 1) "change" else "changes"})")
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
                        "${session.fields.size} ${if (session.fields.size == 1) "field" else "fields"} in this form",
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
            title = { Text("Discard your entries?") },
            text = { Text("The form hasn't been saved. What you entered will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(field: FormField, value: String, onChange: (String) -> Unit) {
    val enabled = !field.readOnly
    val label = if (field.readOnly) "${field.label} (read-only)" else field.label
    when (field.kind) {
        FormFieldKind.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = { onChange(if (field.maxLength > 0) it.take(field.maxLength) else it) },
            label = { Text(label) },
            enabled = enabled,
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
            supportingText = if (field.maxLength > 0) {
                { Text("${value.length} / ${field.maxLength}") }
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
                TextButton(onClick = { onChange("") }) { Text("Clear choice") }
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
                    DropdownMenuItem(text = { Text("None") }, onClick = { onChange(""); expanded = false })
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
        title = { Text("Make searchable") },
        text = {
            Column {
                Text(
                    "Pages without text are read on this phone and given an invisible text layer, so you can search, select and copy. Pages that already have text are left as they are.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.padding(top = 12.dp))
                Text("Language of the text", style = MaterialTheme.typography.titleSmall)
                OcrScript.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = script == option, role = Role.RadioButton) { script = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = script == option, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (option == OcrScript.LATIN) "Latin (English, Spanish, French…)" else option.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(script) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
