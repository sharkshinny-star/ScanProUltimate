package com.example.scanpro.core.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.example.scanpro.core.designsystem.LoadingShimmer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.data.PageFilter

/**
 * A row of polished previews of the page under each filter; tap one to use it.
 * Highlights the active filter with an animated accent border, subtle elevation lift,
 * and a checkmark badge matching modern scanner apps.
 *
 * Previews still being made show a discreet progress indicator. [onDark] picks colours
 * for the dark scanner chrome.
 */
@Composable
fun FilterStrip(
    previews: Map<PageFilter, Bitmap>,
    selected: PageFilter,
    enabled: Boolean,
    onSelect: (PageFilter) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val accent = if (onDark) Color(0xFF2F7BFF) else MaterialTheme.colorScheme.primary
    val text = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val frame = if (onDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    // The chosen filter must be in view: with six filters the last one (Whiteboard) sits past the edge of a phone, and a
    // page using it showed no highlight at all, as if no filter were chosen.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(selected) {
        val index = PageFilter.entries.indexOf(selected)
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (index >= 0 && !fullyVisible) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        // Tight enough that the fifth filter shows part-way on a phone: six filters, so the row must look scrollable.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PageFilter.entries, key = { it.name }, contentType = { "filter_item" }) { filter ->
            val isSelected = filter == selected

            val cardElevation by animateDpAsState(
                targetValue = if (isSelected) 4.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                label = "elevation"
            )
            val cardScale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1.0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                label = "scale"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) accent else frame,
                animationSpec = tween(180),
                label = "border"
            )
            val borderWidth by animateDpAsState(
                targetValue = if (isSelected) 2.5.dp else 1.dp,
                animationSpec = tween(180),
                label = "borderWidth"
            )

            Column(
                Modifier
                    .width(70.dp)
                    .graphicsLayer {
                        alpha = if (enabled) 1f else 0.45f
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = {
                            if (!isSelected) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelect(filter)
                            }
                        }
                    )
                    .semantics { this.selected = isSelected },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 66.dp, height = 82.dp)
                        .shadow(cardElevation, RoundedCornerShape(10.dp), clip = false)
                        .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (onDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = previews[filter]
                    if ((preview != null) && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LoadingShimmer(
                            Modifier.matchParentSize(),
                            highlight = if (onDark) Color.White.copy(alpha = 0.12f) else Color.Unspecified
                        )
                    }

                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = accent,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (onDark) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(filter.labelRes),
                    color = if (isSelected) accent else text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // A longer name such as "Magic Colour" shrinks a little to fit instead of being cut off.
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize)
                )
            }
        }
    }
}
