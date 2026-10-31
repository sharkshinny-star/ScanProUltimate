package com.example.scanpro.feature.kept

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ConfirmDialog
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.work.KeptScan

@Composable
fun KeptScansRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: KeptScansViewModel = hiltViewModel()
) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.kept_share_title)
    KeptScansScreen(
        scans = scans,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onShare = { scan ->
            val paths = scan.photos.map { it.path }
            shareFile(context, paths.first(), "image/jpeg", shareTitle, paths.drop(1))
        },
        onDiscard = viewModel::discard
    )
}

/** Camera scans that could not be saved: try again, share the photos, or discard them after a confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeptScansScreen(
    scans: List<KeptScan>?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onShare: (KeptScan) -> Unit,
    onDiscard: (String) -> Unit
) {
    var discarding by remember { mutableStateOf<KeptScan?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.kept_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                scans == null -> CircularProgressIndicator(Modifier.padding(top = 48.dp))
                scans.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.kept_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    items(scans, key = { it.id }) { scan ->
                        KeptScanCard(
                            scan = scan,
                            onRetry = { onRetry(scan.id) },
                            onShare = { onShare(scan) },
                            onDiscard = { discarding = scan }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    discarding?.let { scan ->
        ConfirmDialog(
            title = stringResource(R.string.kept_discard_dialog_title),
            message = pluralStringResource(R.plurals.kept_discard_dialog_message, scan.photos.size, scan.photos.size),
            confirmLabel = stringResource(R.string.kept_discard),
            onDismiss = { discarding = null },
            onConfirm = {
                discarding = null
                onDiscard(scan.id)
            }
        )
    }
}

@Composable
private fun KeptScanCard(scan: KeptScan, onRetry: () -> Unit, onShare: () -> Unit, onDiscard: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 3.dp) {
                    PageThumbnail(
                        scan.photos.first().path,
                        Modifier.size(width = 50.dp, height = 66.dp),
                        THUMBNAIL_SMALL,
                        shape = RoundedCornerShape(8.dp),
                        background = Color.Transparent
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.kept_item_title, stringResource(scan.mode.labelRes)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    val keptOn = DateUtils.formatDateTime(context, scan.keptAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
                    Text(
                        pluralStringResource(R.plurals.kept_item_meta, scan.photos.size, scan.photos.size, keptOn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    val until = DateUtils.formatDateTime(context, scan.expiresAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
                    Text(
                        stringResource(R.string.kept_item_expires, until),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.kept_cd_more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kept_share_photos)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { menuOpen = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kept_discard), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDiscard() }
                        )
                    }
                }
            }
            scan.failure?.let { reason ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.kept_item_failure, reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kept_retry))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconTile(Icons.Default.CloudDone, TileTone.Teal, size = 84.dp, corner = 28.dp, iconSize = 40.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.kept_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.kept_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
