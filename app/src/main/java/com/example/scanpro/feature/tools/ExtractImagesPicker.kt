package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.DarkChrome
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.designsystem.GlassIconButton
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfCleanupTools
import com.example.scanpro.imaging.PdfCleanupTools.ExtractedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** The pictures of a PDF, already saved to [dir] so the picker can show them and the chosen ones can be kept. */
internal class ExtractPreview(val dir: File, val images: List<ExtractedImage>)

/**
 * Extract images' picker: every picture in the PDF as a thumbnail with a tick. Tapping a picture ticks or unticks it,
 * the magnifier opens it full screen to look at properly. Everything starts ticked, so "Done" without touching
 * anything saves what the tool always saved.
 */
@Composable
internal fun ExtractImagesPicker(
    uri: android.net.Uri,
    preview: ExtractPreview?,
    selected: Set<Int>,
    onLoaded: (ExtractPreview) -> Unit,
    onSelectedChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var viewing by remember { mutableIntStateOf(-1) }

    // The pictures are saved once, when the picker is first opened, then reused.
    LaunchedEffect(uri, preview) {
        if (preview != null) return@LaunchedEffect
        val dir = File(context.cacheDir, "ximg_preview/${UUID.randomUUID()}")
        try {
            val images = PdfCleanupTools.extractImagesWithPages(context, uri, dir)
            onLoaded(ExtractPreview(dir, images))
        } catch (cancelled: CancellationException) {
            dir.deleteRecursively()
            throw cancelled
        } catch (_: Throwable) {
            dir.deleteRecursively()
            failed = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                    Text(
                        stringResource(R.string.cvt_ximg_choose_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (preview != null) {
                        val all = selected.size == preview.images.size
                        TextButton(onClick = {
                            onSelectedChange(if (all) emptySet() else preview.images.indices.toSet())
                        }) { Text(stringResource(if (all) R.string.cvt_ximg_select_none else R.string.cvt_ximg_select_all)) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        failed -> Text(
                            stringResource(R.string.cvt_ximg_prepare_failed),
                            modifier = Modifier.padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        preview == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FileLoadingAnimation()
                            Text(stringResource(R.string.cvt_ximg_preparing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(104.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(preview.images, key = { _, image -> image.file.name }) { index, image ->
                                PictureCell(
                                    image = image,
                                    ticked = index in selected,
                                    onToggle = { onSelectedChange(if (index in selected) selected - index else selected + index) },
                                    onView = { viewing = index }
                                )
                            }
                        }
                    }
                }
                if (preview != null) {
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                        ) { Text(pluralStringResource(R.plurals.cvt_ximg_pick_done, selected.size, selected.size)) }
                    }
                }
            }
        }
    }

    if (preview != null && viewing in preview.images.indices) {
        ExtractedImageViewer(
            images = preview.images,
            start = viewing,
            selected = selected,
            onToggle = { index -> onSelectedChange(if (index in selected) selected - index else selected + index) },
            onDismiss = { viewing = -1 }
        )
    }
}

@Composable
private fun PictureCell(image: ExtractedImage, ticked: Boolean, onToggle: () -> Unit, onView: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(R.string.cvt_ximg_from_page, image.page)
    Box(
        Modifier
            .aspectRatio(1f)
            .border(if (ticked) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClick = onToggle)
            .semantics { role = Role.Checkbox; selected = ticked; contentDescription = description }
    ) {
        PageThumbnail(
            path = image.file.absolutePath,
            modifier = Modifier.fillMaxSize().padding(3.dp),
            shape = RoundedCornerShape(9.dp),
            contentDescription = null
        )
        // The tick, top left.
        Box(
            Modifier
                .padding(6.dp)
                .size(24.dp)
                .background(if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                .border(1.5.dp, if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            if (ticked) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        // Where it came from, bottom left; the magnifier, bottom right.
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        IconButton(
            onClick = onView,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(36.dp)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(Icons.Default.ZoomIn, stringResource(R.string.cvt_ximg_view_larger), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** One picture full screen: swipe for the others, pinch or double-tap to zoom, and a button to tick or untick it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtractedImageViewer(
    images: List<ExtractedImage>,
    start: Int,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { images.size }
        // Zoomed in, a drag moves the picture instead of turning the page.
        var zoomed by remember { mutableStateOf(false) }
        val current = pager.currentPage
        val ticked = current in selected
        Column(Modifier.fillMaxSize().background(DarkChrome.backdrop)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onClick = onDismiss)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        stringResource(R.string.cvt_ximg_picture_of, current + 1, images.size),
                        color = DarkChrome.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.cvt_ximg_from_page, images[current].page),
                        color = DarkChrome.text.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                ZoomableFile(images[page].file, onZoomChange = { if (page == pager.currentPage) zoomed = it })
            }
            Row(
                Modifier.fillMaxWidth().background(DarkChrome.panel).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (ticked) {
                    Button(onClick = { onToggle(current) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.cvt_ximg_in_selection), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    OutlinedButton(onClick = { onToggle(current) }) { Text(stringResource(R.string.cvt_ximg_tick)) }
                }
            }
        }
    }
}

@Composable
private fun ZoomableFile(file: File, onZoomChange: (Boolean) -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeOrientedScaled(file.absolutePath, 2400) }.getOrNull() }
    }
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                    onZoomChange(scale > 1f)
                }
            }
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    onZoomChange(scale > 1f)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
            )
        } ?: FileLoadingAnimation(onDark = true)
    }
}
