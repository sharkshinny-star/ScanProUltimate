package com.example.scanpro.feature.document

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R

/** Asked before a full-screen editor closes with edits that have not been applied. */
@Composable
fun DiscardEditsDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.doc_discard_edits_title)) },
        text = { Text(stringResource(R.string.doc_discard_edits_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.tools_form_discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.tools_form_keep_editing)) } }
    )
}
