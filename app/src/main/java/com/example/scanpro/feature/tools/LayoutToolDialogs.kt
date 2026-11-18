package com.example.scanpro.feature.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfHeaderFooterTools

/** N-up (F173): how many pages go on each sheet. */
@Composable
internal fun NupDialog(onDismiss: () -> Unit, onConfirm: (perSheet: Int) -> Unit) {
    var perSheet by rememberSaveable { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.GridView, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_card_nup)) },
        text = {
            Column {
                Text(stringResource(R.string.tools_nup_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(selected = perSheet == 2, onClick = { perSheet = 2 }, label = { Text(stringResource(R.string.tools_nup_two)) })
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    FilterChip(selected = perSheet == 4, onClick = { perSheet = 4 }, label = { Text(stringResource(R.string.tools_nup_four)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(perSheet) }) { Text(stringResource(R.string.tools_nup_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
