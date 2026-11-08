package com.example.scanpro.feature.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R

/**
 * A document's tags as chips (R35, after Genius Scan and Adobe Scan): the ones it has with an x to drop them, the
 * other tags in use in the library to add with a tap, and a field for a new one. Save hands back the final list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsSheet(current: List<String>, suggestions: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var tags by rememberSaveable { mutableStateOf(current.map { it.trim() }.filter { it.isNotEmpty() }) }
    var draft by rememberSaveable { mutableStateOf("") }
    fun add(raw: String) {
        val tag = raw.trim().trimEnd(',')
        if (tag.isNotEmpty() && tags.none { it.equals(tag, ignoreCase = true) }) tags = tags + tag
        draft = ""
    }
    val others = suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.doc_document_tags), style = MaterialTheme.typography.titleLarge)
            if (tags.isNotEmpty()) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        InputChip(
                            selected = true,
                            onClick = { tags = tags - t },
                            label = { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.doc_remove_tag, t)) }
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.doc_no_tags_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.doc_add_tag)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(draft) })
                )
                IconButton(onClick = { add(draft) }, enabled = draft.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.doc_add_tag))
                }
            }
            if (others.isNotEmpty()) {
                Text(stringResource(R.string.doc_tags_in_use), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { s -> FilterChip(selected = false, onClick = { add(s) }, label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (draft.isNotBlank()) add(draft); onSave(tags) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
