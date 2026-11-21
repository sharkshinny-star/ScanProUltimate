package com.example.scanpro.feature.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * A grid of pages that can be picked up and moved, the way you would hold a sheet of paper: press and hold a page,
 * then drag it; the pages around it slide out of the way and it drops into the slot it is over. Near the top or
 * bottom edge the grid scrolls by itself, so a page can be carried a long way.
 *
 * The grid never reorders [items] itself. It asks through [onMove] (from index, to index) and the caller changes its
 * list, which keeps undo, saved state and the actual page data in the caller's hands. [onDragStarted] runs once
 * when a page is picked up (the place to record an undo step) and [onDragFinished] when it is dropped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T : Any> ReorderablePageGrid(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    dragEnabled: Boolean = true,
    columns: GridCells = GridCells.Adaptive(minSize = 150.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onDragStarted: () -> Unit = {},
    onDragFinished: () -> Unit = {},
    itemContent: @Composable (item: T, index: Int, isDragging: Boolean) -> Unit
) {
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val latestItems by rememberUpdatedState(items)
    val latestKey by rememberUpdatedState(key)
    val latestOnMove by rememberUpdatedState(onMove)

    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var startOffset by remember { mutableStateOf(IntOffset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    fun visible(k: Any): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }

    // While a page is held near the top or bottom edge, keep scrolling so it can be carried further.
    LaunchedEffect(draggedKey) {
        val held = draggedKey ?: return@LaunchedEffect
        while (draggedKey == held) {
            val info = visible(held)
            if (info != null) {
                val centreY = startOffset.y + dragDelta.y + info.size.height / 2f
                val viewport = gridState.layoutInfo.viewportSize.height
                val edge = (info.size.height * 0.35f).coerceAtLeast(72f)
                when {
                    centreY < edge -> gridState.scrollBy(-22f)
                    centreY > viewport - edge -> gridState.scrollBy(22f)
                }
            }
            delay(16.milliseconds)
        }
    }

    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }, contentType = { _, _ -> "page" }) { index, item ->
            val itemKey = key(item)
            val dragging = draggedKey == itemKey
            Box(
                Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            // Keep the page under the finger: its slot may have changed since the drag began.
                            val now = visible(itemKey)?.offset ?: startOffset
                            translationX = startOffset.x + dragDelta.x - now.x
                            translationY = startOffset.y + dragDelta.y - now.y
                            scaleX = 1.05f
                            scaleY = 1.05f
                            shadowElevation = 24f
                        }
                    }
                    .then(
                        if (dragEnabled) Modifier.pointerInput(itemKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val info = visible(itemKey) ?: return@detectDragGesturesAfterLongPress
                                    startOffset = info.offset
                                    dragDelta = Offset.Zero
                                    draggedKey = itemKey
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragStarted()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (draggedKey != itemKey) return@detectDragGesturesAfterLongPress
                                    dragDelta += amount
                                    val held = visible(itemKey) ?: return@detectDragGesturesAfterLongPress
                                    val cx = startOffset.x + dragDelta.x + held.size.width / 2f
                                    val cy = startOffset.y + dragDelta.y + held.size.height / 2f
                                    val over = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.key != itemKey &&
                                            cx >= it.offset.x && cx <= it.offset.x + it.size.width &&
                                            cy >= it.offset.y && cy <= it.offset.y + it.size.height
                                    }
                                    if (over != null) {
                                        val from = latestItems.indexOfFirst { latestKey(it) == itemKey }
                                        if (from >= 0 && from != over.index) {
                                            latestOnMove(from, over.index)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedKey = null
                                    dragDelta = Offset.Zero
                                    onDragFinished()
                                },
                                onDragCancel = {
                                    draggedKey = null
                                    dragDelta = Offset.Zero
                                    onDragFinished()
                                }
                            )
                        } else Modifier
                    )
            ) {
                itemContent(item, index, dragging)
            }
        }
    }
}

/** One row in a [PageActionsSheet]. */
internal class PageAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    /** Drawn in the error colour: removing something. */
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The menu for one page: a bottom sheet with a header (what the page is) and one clear row per action, replacing rows
 * of tiny arrows. Disabled actions stay in place, greyed, so the sheet never rearranges itself between pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageActionsSheet(
    title: String,
    subtitle: String?,
    actions: List<PageAction>,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                header()
                Column(Modifier.padding(start = 14.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            actions.forEach { action ->
                val tint = when {
                    !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    action.destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .then(
                            if (action.enabled) Modifier.clickable(role = Role.Button) {
                                // Close first, then act: the sheet animates away while the grid already shows the result.
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                action.onClick()
                            } else Modifier
                        )
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                }
            }
        }
    }
}
