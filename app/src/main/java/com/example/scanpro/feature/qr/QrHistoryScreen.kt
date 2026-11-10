package com.example.scanpro.feature.qr

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ConfirmDialog
import com.example.scanpro.data.ScanHistory
import com.example.scanpro.data.ScanHistoryEntry
import com.example.scanpro.scanner.BarcodeResultSheet
import com.example.scanpro.scanner.ScannedCode
import com.example.scanpro.scanner.codeSummary
import com.example.scanpro.scanner.parseScannedCode
import com.example.scanpro.scanner.titleFor

@Composable
fun QrHistoryRoute(onBack: () -> Unit, viewModel: QrHistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val saveHistory by viewModel.saveHistory.collectAsStateWithLifecycle()
    QrHistoryScreen(
        entries = entries,
        saveHistory = saveHistory,
        onSaveHistoryChange = viewModel::setSaveHistory,
        onFavourite = viewModel::setFavourite,
        onRemove = viewModel::remove,
        onClear = viewModel::clear,
        onBack = onBack
    )
}

/** The codes scanned before, newest first: search them, star the ones worth keeping, delete one or all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrHistoryScreen(
    entries: List<ScanHistoryEntry>,
    saveHistory: Boolean,
    onSaveHistoryChange: (Boolean) -> Unit,
    onFavourite: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<ScannedCode?>(null) }
    val shown = remember(entries, query) { ScanHistory.filter(entries, query) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.qr_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = saveHistory, role = Role.Switch, onValueChange = onSaveHistoryChange)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.qr_history_save_switch),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = saveHistory, onCheckedChange = null)
                }
                Text(
                    stringResource(R.string.qr_history_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { confirmClear = true },
                    enabled = entries.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.qr_history_clear_all))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.qr_history_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(if (entries.isEmpty()) R.string.qr_history_empty else R.string.qr_history_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(shown, key = { it.raw }) { entry ->
                HistoryRow(
                    entry = entry,
                    onOpen = { opened = parseScannedCode(entry.raw, entry.format, entry.valueType) },
                    onFavourite = { onFavourite(entry.raw, !entry.favourite) },
                    onRemove = { onRemove(entry.raw) }
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.qr_history_clear_title),
            message = stringResource(R.string.qr_history_clear_message),
            confirmLabel = stringResource(R.string.qr_history_clear_all),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                onClear()
            }
        )
    }
    opened?.let { code ->
        // "Scan another" from here goes back to the camera.
        BarcodeResultSheet(code, onScanAgain = { opened = null; onBack() }, onDismiss = { opened = null })
    }
}

@Composable
private fun HistoryRow(entry: ScanHistoryEntry, onOpen: () -> Unit, onFavourite: () -> Unit, onRemove: () -> Unit) {
    val code = remember(entry.raw, entry.format, entry.valueType) { parseScannedCode(entry.raw, entry.format, entry.valueType) }
    val whenScanned = remember(entry.savedAt) {
        DateUtils.getRelativeTimeSpanString(entry.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
        overlineContent = { Text("${titleFor(code)} · $whenScanned") },
        headlineContent = { Text(codeSummary(code, 120), maxLines = 2) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavourite) {
                    Icon(
                        if (entry.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(if (entry.favourite) R.string.qr_history_favourite_remove else R.string.qr_history_favourite_add)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.qr_history_delete))
                }
            }
        }
    )
}
