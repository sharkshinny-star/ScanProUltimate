package com.example.scanpro.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.PageFilter
import androidx.compose.runtime.Composable

/**
 * A row of small previews of the page under each filter; tap one to use it. Previews still being made show a
 * spinner, and the strip works without them. [onDark] picks colours for the dark scanner screens.
 */
@Composable
fun FilterStrip(
    previews: Map<PageFilter, Bitmap>,
    selected: PageFilter,
    enabled: Boolean,
    onSelect: (PageFilter) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false
) {
    val accent = if (onDark) Color(0xFF2F7BFF) else MaterialTheme.colorScheme.primary
    val text = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val frame = if (onDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(PageFilter.entries, key = { it.name }) { filter ->
            val isSelected = filter == selected
            Column(
                Modifier
                    .width(74.dp)
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(filter) }
                    .semantics { this.selected = isSelected },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 62.dp, height = 78.dp)
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) accent else frame, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (onDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = previews[filter]
                    if (preview != null && !preview.isRecycled) {
                        Image(preview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                    }
                }
                Text(
                    filter.label,
                    color = if (isSelected) accent else text,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
