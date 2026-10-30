package com.example.scanpro.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.THUMBNAIL_GRID
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.pagesLabel
import com.example.scanpro.data.DocumentSummary
import java.text.DateFormat
import java.util.Date

/*
 * Document cards for the library. Three shapes share one look: a paper-like thumbnail with a soft shadow on
 * a tonal surface, a bold title, quiet metadata, a star for favourites and an animated check for selection.
 */

@Composable
private fun rememberUpdatedText(updatedAt: Long): String =
    // Formatting a date allocates a formatter; do it once per value rather than on every recomposition.
    remember(updatedAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(updatedAt)) }

/** The first page as a sheet of paper: white card, hairline, soft shadow, on a tonal backdrop. */
@Composable
private fun PaperThumbnail(document: DocumentSummary, modifier: Modifier = Modifier, maxDimension: Int, corner: Dp = 8.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)), shape)
    ) {
        PageThumbnail(
            path = document.coverPath,
            version = document.updatedAt,
            modifier = Modifier.fillMaxSize(),
            maxDimension = maxDimension,
            shape = shape,
            background = Color.Transparent
        )
    }
}

@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = selected,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 900f)) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 2.dp, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lib_cd_selected), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f), shadowElevation = 1.dp, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.lib_cd_favorite), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(size * 0.62f))
        }
    }
}

@Composable
private fun cardColors(selected: Boolean): Pair<Color, Color> {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "container"
    )
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
        label = "border"
    )
    return container to border
}

/** Full-width row: thumbnail, title and metadata. Used by Files in list mode and by the Home recents. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentListCard(
    document: DocumentSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val updatedText = rememberUpdatedText(document.updatedAt)
    val (container, border) = cardColors(selected)
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.5.dp, border, shape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.lib_card_open),
                onLongClickLabel = stringResource(if (selected) R.string.lib_card_deselect else R.string.lib_card_select),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { this.selected = selected }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp)) {
            PaperThumbnail(document, Modifier.size(width = 50.dp, height = 66.dp), THUMBNAIL_SMALL)
            SelectionBadge(selected, Modifier.align(Alignment.TopStart))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                document.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.lib_card_meta_three, pagesLabel(LocalContext.current, document.pageCount), formatBytes(document.sizeBytes), updatedText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (document.folderName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    document.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (document.isFavorite) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.lib_cd_favorite), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Grid tile: a tall thumbnail with the title underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentGridCard(
    document: DocumentSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val (container, border) = cardColors(selected)
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.5.dp, border, shape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.lib_card_open),
                onLongClickLabel = stringResource(if (selected) R.string.lib_card_deselect else R.string.lib_card_select),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { this.selected = selected }
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            PaperThumbnail(
                document,
                Modifier
                    .fillMaxSize(0.78f)
                    .padding(top = 6.dp)
                    .aspectRatio(0.72f, matchHeightConstraintsFirst = true),
                THUMBNAIL_GRID
            )
            if (document.isFavorite && !selected) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            SelectionBadge(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            document.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.lib_card_meta_two, pagesLabel(LocalContext.current, document.pageCount), formatBytes(document.sizeBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Compact card for the Home "Recent" row: a fixed-width tile with the thumbnail on top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentRecentCard(
    document: DocumentSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val updatedText = rememberUpdatedText(document.updatedAt)
    val (container, border) = cardColors(selected)
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .width(140.dp)
            .clip(shape)
            .background(container)
            .border(1.5.dp, border, shape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.lib_card_open),
                onLongClickLabel = stringResource(if (selected) R.string.lib_card_deselect else R.string.lib_card_select),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { this.selected = selected }
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            PaperThumbnail(document, Modifier.size(width = 72.dp, height = 96.dp).padding(top = 6.dp), THUMBNAIL_SMALL, corner = 6.dp)
            if (document.isFavorite && !selected) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(5.dp), size = 20.dp)
            SelectionBadge(selected, Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            document.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            stringResource(R.string.lib_card_meta_two, pagesLabel(LocalContext.current, document.pageCount), updatedText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
