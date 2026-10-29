package com.example.scanpro.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.data.ScanDocument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.scanpro.core.designsystem.ConfirmDialog
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.ui.formatTrashCountdown
import com.example.scanpro.core.ui.formatBytes

private enum class TrashSortOption(val label: String) {
    EXPIRY("Soonest to expire"),
    DATE("Deletion date"),
    NAME("File name"),
    SIZE("File size")
}

@Composable
fun TrashRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    TrashScreen(
        documents = documents,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRestore = viewModel::restore,
        onDeleteForever = viewModel::deleteForever,
        onEmptyTrash = viewModel::emptyTrash
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrashScreen(
    documents: List<ScanDocument>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var sortOption by remember { mutableStateOf(TrashSortOption.EXPIRY) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var deleteSingleDoc by remember { mutableStateOf<ScanDocument?>(null) }
    var activeMenuDocId by remember { mutableStateOf<String?>(null) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            nowMs = System.currentTimeMillis()
        }
    }

    val sortedDocuments = remember(documents, sortOption, nowMs) {
        val retentionMs = 7L * 24L * 60L * 60L * 1000L
        when (sortOption) {
            TrashSortOption.EXPIRY -> documents.sortedBy { (it.trashedAt + retentionMs) - nowMs }
            TrashSortOption.DATE -> documents.sortedByDescending { it.trashedAt }
            TrashSortOption.NAME -> documents.sortedBy { it.title.lowercase() }
            TrashSortOption.SIZE -> documents.sortedByDescending { it.sizeBytes }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Recycle bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (documents.isNotEmpty()) {
                        TextButton(enabled = !busy, onClick = { showEmptyDialog = true }) {
                            Text("Empty", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Recycle Bin is Empty", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deleted files will appear here for 7 days before being permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Files are automatically deleted after 7 days.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(TrashSortOption.entries) { option ->
                        FilterChip(
                            selected = sortOption == option,
                            onClick = { sortOption = option },
                            label = { Text(option.label) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedDocuments, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PageThumbnail(doc.pages.firstOrNull()?.imagePath, Modifier.size(width = 56.dp, height = 74.dp), THUMBNAIL_SMALL, version = doc.pages.firstOrNull())
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val (countdownText, isUrgent) = formatTrashCountdown(doc.trashedAt, nowMs)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        val countdownColor = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = countdownColor
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            countdownText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = countdownColor,
                                            fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val pagesLabel = if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages"
                                    Text("$pagesLabel • ${formatBytes(doc.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(enabled = !busy, onClick = { activeMenuDocId = doc.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                    }
                                    DropdownMenu(
                                        expanded = activeMenuDocId == doc.id,
                                        onDismissRequest = { activeMenuDocId = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Restore") },
                                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                            onClick = {
                                                activeMenuDocId = null
                                                onRestore(doc.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                activeMenuDocId = null
                                                deleteSingleDoc = doc
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteSingleDoc != null) {
        ConfirmDialog(
            title = "Delete permanently?",
            message = "This file will be permanently deleted and cannot be recovered. Are you sure?",
            confirmLabel = "Delete",
            onDismiss = { deleteSingleDoc = null },
            onConfirm = {
                val id = deleteSingleDoc?.id
                deleteSingleDoc = null
                if (id != null) onDeleteForever(id)
            }
        )
    }

    if (showEmptyDialog) {
        ConfirmDialog(
            title = "Empty Recycle Bin?",
            message = "This will permanently delete all files in the Recycle Bin. This action cannot be undone.",
            confirmLabel = "Empty",
            onDismiss = { showEmptyDialog = false },
            onConfirm = {
                showEmptyDialog = false
                onEmptyTrash()
            }
        )
    }
}
