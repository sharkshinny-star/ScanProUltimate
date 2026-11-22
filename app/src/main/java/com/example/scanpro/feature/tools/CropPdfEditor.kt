package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.imaging.CropFraction
import com.example.scanpro.imaging.CropPlan
import com.example.scanpro.imaging.PdfCropTools
import com.example.scanpro.imaging.PdfPreviewSession
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width the page being cropped is drawn at, and the strip's thumbnails. */
private const val EDITOR_PAGE_WIDTH = 1000
private const val STRIP_THUMB_WIDTH = 120

/**
 * Crop PDF pages chosen by hand (F241): the page large with a frame to drag (corners, sides, or inside to move it),
 * Previous and Next and a strip of every page to move between them, This page or All pages for what a drag changes,
 * Adjust or Preview to see the page as it will come out, and Reset. One crop can serve every page, and any page can
 * be given its own (or kept whole) while the rest share it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualCropSection(
    uri: Uri,
    pageCount: Int,
    plan: CropPlan,
    onPlanChange: (CropPlan) -> Unit,
    allPages: Boolean,
    onAllPagesChange: (Boolean) -> Unit,
    pageIndex: Int,
    onPageChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    // One copy of the file for the editor and the strip, closed with them.
    val session by produceState<PdfPreviewSession?>(null, uri) {
        val opened = runCatching { PdfPreviewSession.open(context, uri) }.getOrNull() ?: return@produceState
        value = opened
        awaitDispose { opened.close() }
    }
    val index = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    // The previous page stays on screen until the next one is drawn, so moving between pages does not flash.
    val page by produceState<Bitmap?>(null, session, index) {
        val open = session ?: return@produceState
        value = withContext(Dispatchers.IO) { open.render(index, EDITOR_PAGE_WIDTH) }
    }
    val pageSize by produceState<Pair<Int, Int>?>(null, session, index) {
        val open = session ?: return@produceState
        value = withContext(Dispatchers.IO) { open.pageSizePoints(index) }
    }
    var previewing by rememberSaveable { mutableStateOf(false) }

    // In All pages a drag changes the crop every page shares; in This page it gives this page a crop of its own.
    val shown = if (allPages) plan.all else plan.forPage(index)
    fun edit(crop: CropFraction) = onPlanChange(if (allPages) plan.withAll(crop) else plan.withPage(index, crop))
    val (widthPt, heightPt) = pageSize ?: (595 to 842)
    // Never smaller than the tool accepts (36 pt), nor than a twentieth of the page, so a handle stays grabbable.
    val minWidth = max(PdfCropTools.MIN_SIDE_PT / widthPt.coerceAtLeast(1), 0.05f).coerceAtMost(1f)
    val minHeight = max(PdfCropTools.MIN_SIDE_PT / heightPt.coerceAtLeast(1), 0.05f).coerceAtMost(1f)

    ToolSection(stringResource(R.string.edit_crop_apply_to)) {
        SegmentedChoice(
            options = listOf(true to stringResource(R.string.edit_crop_scope_all), false to stringResource(R.string.edit_crop_scope_page)),
            selected = allPages,
            onSelect = onAllPagesChange
        )
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPageChange(index - 1) }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, stringResource(R.string.scan_review_previous_page))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.viewer_page_of, index + 1, pageCount), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    when {
                        allPages && plan.hasOwn(index) -> R.string.edit_crop_status_keeps_own
                        allPages -> R.string.edit_crop_status_shared_all
                        plan.hasOwn(index) && plan.forPage(index).isWhole -> R.string.edit_crop_status_whole
                        plan.hasOwn(index) -> R.string.edit_crop_status_own
                        else -> R.string.edit_crop_status_shared_page
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onPageChange(index + 1) }, enabled = index < pageCount - 1) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, stringResource(R.string.scan_review_next_page))
        }
    }

    PageCropBox(
        bitmap = page,
        crop = shown,
        minWidth = minWidth,
        minHeight = minHeight,
        previewing = previewing,
        onChange = ::edit,
        modifier = Modifier.fillMaxWidth().height(380.dp)
    )
    SegmentedChoice(
        options = listOf(false to stringResource(R.string.edit_crop_adjust), true to stringResource(R.string.edit_crop_preview)),
        selected = previewing,
        onSelect = { previewing = it }
    )
    if (!previewing) ToolHint(stringResource(R.string.edit_crop_drag_hint))

    if (pageCount > 1) PageStrip(session, pageCount, index, plan, onPageChange)

    if (pageSize != null) {
        Text(
            stringResource(
                R.string.edit_crop_page_size,
                index + 1,
                millimetres(widthPt.toFloat(), heightPt.toFloat()),
                millimetres(shown.width * widthPt, shown.height * heightPt)
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (allPages) {
            OutlinedButton(onClick = { onPlanChange(plan.withAll(CropFraction.WHOLE)) }, enabled = !plan.all.isWhole) {
                Text(stringResource(R.string.edit_crop_reset_all_pages))
            }
        } else {
            OutlinedButton(onClick = { onPlanChange(plan.withPage(index, CropFraction.WHOLE)) }, enabled = !plan.forPage(index).isWhole) {
                Text(stringResource(R.string.edit_crop_reset_page))
            }
            if (plan.hasOwn(index)) {
                TextButton(onClick = { onPlanChange(plan.withoutPage(index)) }) { Text(stringResource(R.string.edit_crop_use_shared)) }
            }
        }
        if (!plan.isEmpty || plan.pages.isNotEmpty()) {
            TextButton(onClick = { onPlanChange(CropPlan()) }) { Text(stringResource(R.string.edit_crop_start_over)) }
        }
    }
    if (allPages && plan.pages.isNotEmpty()) {
        ToolHint(pluralStringResource(R.plurals.edit_crop_own_count, plan.pages.size, plan.pages.size))
        TextButton(onClick = { onPlanChange(plan.sameEverywhere()) }) { Text(stringResource(R.string.edit_crop_same_everywhere)) }
    }
    val cropped = plan.croppedCount(pageCount)
    Text(
        pluralStringResource(R.plurals.edit_crop_pages_cropped, pageCount, cropped, pageCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** "210 × 297 mm" for a size in points. */
@Composable
private fun millimetres(widthPt: Float, heightPt: Float): String =
    stringResource(R.string.edit_size_mm, (widthPt / PdfCropTools.PT_PER_MM).roundToInt(), (heightPt / PdfCropTools.PT_PER_MM).roundToInt())

/** Where a page of [bw] × [bh] pixels sits when fitted into [area], [inset] pixels in from every side. */
private fun fitPage(area: IntSize, bw: Int, bh: Int, inset: Float): Rect? {
    val aw = area.width - 2 * inset
    val ah = area.height - 2 * inset
    if (aw <= 0f || ah <= 0f || bw <= 0 || bh <= 0) return null
    val scale = min(aw / bw, ah / bh)
    val w = bw * scale
    val h = bh * scale
    return Rect(Offset((area.width - w) / 2f, (area.height - h) / 2f), Size(w, h))
}

/** [crop] as a rectangle on the page drawn at [page]. */
private fun cropRect(page: Rect, crop: CropFraction) = Rect(
    page.left + crop.left * page.width,
    page.top + crop.top * page.height,
    page.left + crop.right * page.width,
    page.top + crop.bottom * page.height
)

/**
 * The page with its crop frame. A touch on the frame drags it; anywhere else it is left to the screen, so the page can
 * still be scrolled past. In Preview only what is kept is shown, as large as it fits.
 */
@Composable
private fun PageCropBox(
    bitmap: Bitmap?,
    crop: CropFraction,
    minWidth: Float,
    minHeight: Float,
    previewing: Boolean,
    onChange: (CropFraction) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val inset = with(density) { 18.dp.toPx() }
    val reach = with(density) { 28.dp.toPx() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val pageRect = remember(area, bitmap) { bitmap?.let { fitPage(area, it.width, it.height, inset) } }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    // The gesture outlives recompositions; it reads the newest values through these.
    val latestCrop by rememberUpdatedState(crop)
    val latestRect by rememberUpdatedState(pageRect)
    val latestChange by rememberUpdatedState(onChange)
    val latestMinWidth by rememberUpdatedState(minWidth)
    val latestMinHeight by rememberUpdatedState(minHeight)
    var dragging by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(if (previewing) R.string.edit_crop_preview else R.string.edit_crop_frame_description)

    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { area = it }
            .semantics { contentDescription = description }
            .pointerInput(previewing) {
                if (previewing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rect = latestRect ?: return@awaitEachGesture
                    val handle = hitCropHandle(
                        latestCrop,
                        (down.position.x - rect.left) / rect.width,
                        (down.position.y - rect.top) / rect.height,
                        reach / rect.width,
                        reach / rect.height
                    ) ?: return@awaitEachGesture
                    down.consume()
                    var working = latestCrop
                    dragging = true
                    try {
                        drag(down.id) { change ->
                            val moved = change.positionChange()
                            working = dragCrop(working, handle, moved.x / rect.width, moved.y / rect.height, latestMinWidth, latestMinHeight)
                            latestChange(working)
                            change.consume()
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val rect = pageRect
        if (image == null || rect == null) {
            FileLoadingAnimation(size = 72.dp)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                if (previewing) drawKeptPart(image, crop, inset, outline)
                else drawCropEditor(image, rect, crop, dragging, primary, outline)
            }
        }
    }
}

private fun DrawScope.drawCropEditor(image: ImageBitmap, page: Rect, crop: CropFraction, dragging: Boolean, primary: Color, outline: Color) {
    drawImage(
        image,
        dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
        dstSize = IntSize(page.width.roundToInt(), page.height.roundToInt())
    )
    drawRect(outline, page.topLeft, page.size, style = Stroke(1f))
    val kept = cropRect(page, crop)
    // What is cut away, darkened.
    val shade = Color.Black.copy(alpha = 0.5f)
    drawRect(shade, page.topLeft, Size(page.width, kept.top - page.top))
    drawRect(shade, Offset(page.left, kept.bottom), Size(page.width, page.bottom - kept.bottom))
    drawRect(shade, Offset(page.left, kept.top), Size(kept.left - page.left, kept.height))
    drawRect(shade, Offset(kept.right, kept.top), Size(page.right - kept.right, kept.height))
    val line = 2.dp.toPx()
    drawRect(primary, kept.topLeft, kept.size, style = Stroke(line))
    // Thirds while dragging, to line things up.
    if (dragging) {
        val guide = Color.White.copy(alpha = 0.7f)
        for (k in 1..2) {
            val x = kept.left + kept.width * k / 3f
            val y = kept.top + kept.height * k / 3f
            drawLine(guide, Offset(x, kept.top), Offset(x, kept.bottom), strokeWidth = 1.dp.toPx())
            drawLine(guide, Offset(kept.left, y), Offset(kept.right, y), strokeWidth = 1.dp.toPx())
        }
    }
    // Round handles on the corners, bars in the middle of the sides.
    val knob = 9.dp.toPx()
    listOf(kept.topLeft, kept.topRight, kept.bottomLeft, kept.bottomRight).forEach { centre ->
        drawCircle(Color.White, knob, centre)
        drawCircle(primary, knob, centre, style = Stroke(3.dp.toPx()))
    }
    val bar = 22.dp.toPx()
    val thick = 6.dp.toPx()
    val corner = androidx.compose.ui.geometry.CornerRadius(thick / 2f)
    fun bar(centre: Offset, horizontal: Boolean) {
        val size = if (horizontal) Size(bar, thick) else Size(thick, bar)
        val topLeft = Offset(centre.x - size.width / 2f, centre.y - size.height / 2f)
        drawRoundRect(Color.White, topLeft, size, corner)
        drawRoundRect(primary, topLeft, size, corner, style = Stroke(2.dp.toPx()))
    }
    bar(Offset(kept.center.x, kept.top), true)
    bar(Offset(kept.center.x, kept.bottom), true)
    bar(Offset(kept.left, kept.center.y), false)
    bar(Offset(kept.right, kept.center.y), false)
}

/** Only the part that is kept, fitted into the space: the page as it will come out. */
private fun DrawScope.drawKeptPart(image: ImageBitmap, crop: CropFraction, inset: Float, outline: Color) {
    val srcLeft = (crop.left * image.width).roundToInt().coerceIn(0, image.width - 1)
    val srcTop = (crop.top * image.height).roundToInt().coerceIn(0, image.height - 1)
    val srcWidth = (crop.width * image.width).roundToInt().coerceIn(1, image.width - srcLeft)
    val srcHeight = (crop.height * image.height).roundToInt().coerceIn(1, image.height - srcTop)
    val target = fitPage(IntSize(size.width.roundToInt(), size.height.roundToInt()), srcWidth, srcHeight, inset) ?: return
    drawRoundRect(Color.Black.copy(alpha = 0.12f), target.topLeft + Offset(0f, 6f), target.size, androidx.compose.ui.geometry.CornerRadius(6f))
    drawImage(
        image,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(target.left.roundToInt(), target.top.roundToInt()),
        dstSize = IntSize(target.width.roundToInt(), target.height.roundToInt())
    )
    drawRect(outline, target.topLeft, target.size, style = Stroke(1f))
}

/** Every page small, with its crop outlined; the page being cropped is ringed, and a tap goes to a page. */
@Composable
private fun PageStrip(session: PdfPreviewSession?, pageCount: Int, current: Int, plan: CropPlan, onPick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(8.dp)
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(pageCount, key = { it }) { i ->
            val thumb by produceState<Bitmap?>(null, session, i) {
                val open = session ?: return@produceState
                value = withContext(Dispatchers.IO) { open.render(i, STRIP_THUMB_WIDTH) }
            }
            val image = remember(thumb) { thumb?.asImageBitmap() }
            val crop = plan.forPage(i)
            val label = stringResource(R.string.viewer_page_of, i + 1, pageCount)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 52.dp, height = 70.dp)
                        .clip(shape)
                        .border(if (i == current) 2.dp else 1.dp, if (i == current) primary else outline, shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(i) }
                        .semantics { contentDescription = label }
                ) {
                    if (image != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val page = fitPage(IntSize(size.width.roundToInt(), size.height.roundToInt()), image.width, image.height, 2.dp.toPx())
                                ?: return@Canvas
                            drawImage(
                                image,
                                dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
                                dstSize = IntSize(page.width.roundToInt(), page.height.roundToInt())
                            )
                            if (!crop.isWhole) {
                                val kept = cropRect(page, crop)
                                drawRect(Color.Black.copy(alpha = 0.35f), page.topLeft, Size(page.width, kept.top - page.top))
                                drawRect(Color.Black.copy(alpha = 0.35f), Offset(page.left, kept.bottom), Size(page.width, page.bottom - kept.bottom))
                                drawRect(primary, kept.topLeft, kept.size, style = Stroke(1.5.dp.toPx()))
                            }
                        }
                    }
                }
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == current) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
