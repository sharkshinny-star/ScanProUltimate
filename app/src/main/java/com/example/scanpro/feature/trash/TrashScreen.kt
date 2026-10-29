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
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone
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
                IconTile(Icons.Default.DeleteOutline, TileTone.Slate, size = 84.dp, corner = 28.dp, iconSize = 40.dp)
                Spacer(Modifier.height(20.dp))
                Text("The recycle bin is empty", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deleted documents wait here for 7 days before they are removed for good.",
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Documents are removed for good after 7 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
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
                            label = { Text(option.label) },
                            shape = CircleShape,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = sortOption == option,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            )
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shadowElevation = 3.dp
                                ) {
                                    PageThumbnail(
                                        doc.pages.firstOrNull()?.imagePath,
                                        Modifier.size(width = 50.dp, height = 66.dp),
                                        THUMBNAIL_SMALL,
                                        version = doc.pages.firstOrNull(),
                                        shape = RoundedCornerShape(8.dp),
                                        background = androidx.compose.ui.graphics.Color.Transparent
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(doc.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(3.dp))
                                    val pagesLabel = if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages"
                                    Text("$pagesLabel · ${formatBytes(doc.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                    val (countdownText, isUrgent) = formatTrashCountdown(doc.trashedAt, nowMs)
                                    // Time left as a small pill; it turns to the error colour in the last day.
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        val countdownColor = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = countdownColor)
                                            Spacer(Modifier.width(4.dp))
                                            Text(countdownText, style = MaterialTheme.typography.labelSmall, color = countdownColor, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
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
                                            text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
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
            message = "This document will be deleted for good and cannot be recovered.",
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
            title = "Empty the recycle bin?",
            message = "Every document in the recycle bin will be deleted for good. This cannot be undone.",
            confirmLabel = "Empty",
            onDismiss = { showEmptyDialog = false },
            onConfirm = {
                showEmptyDialog = false
                onEmptyTrash()
            }
        )
    }
}
