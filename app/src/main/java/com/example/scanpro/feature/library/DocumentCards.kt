package com.example.scanpro.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.ScanDocument
import java.text.DateFormat
import java.util.Date
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.pagesLabel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentListCard(
    document: ScanDocument,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Formatting a date allocates a formatter; do it once per value rather than on every recomposition.
    val updatedText = remember(document.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(document.updatedAt))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Open",
                onLongClickLabel = if (selected) "Deselect" else "Select",
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PageThumbnail(
                path = document.pages.firstOrNull()?.imagePath,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                maxDimension = THUMBNAIL_SMALL
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(document.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (document.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Favorite", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${pagesLabel(document.pageCount)} · ${formatBytes(document.sizeBytes)} · $updatedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentGridCard(
    document: ScanDocument,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Open",
                onLongClickLabel = if (selected) "Deselect" else "Select",
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                PageThumbnail(
                    path = document.pages.firstOrNull()?.imagePath,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", modifier = Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(document.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pagesLabel(document.pageCount)} · ${formatBytes(document.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
