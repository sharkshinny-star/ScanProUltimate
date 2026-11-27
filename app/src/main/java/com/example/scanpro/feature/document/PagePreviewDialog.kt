package com.example.scanpro.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.AccentButton
import com.example.scanpro.core.designsystem.DarkChrome
import com.example.scanpro.core.designsystem.GlassIconButton
import com.example.scanpro.data.ScanPage
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The finished page, full screen: what is saved and exported, with the crop, filter and edits applied. A tap on a page
 * in the document used to open the crop editor, which shows the original photo with the outline on it, so a cropped
 * page looked uncropped when opened. Here the result is shown, pinch zooms, and Crop opens the editor on purpose.
 */
@Composable
internal fun PagePreviewDialog(
    page: ScanPage,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    /** Moves by the given number of pages (-1 earlier, 1 later); null hides the arrows. */
    onPage: ((delta: Int) -> Unit)?
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Keyed by the page object, so a re-rendered page (new crop, filter) is decoded again.
        val bitmap by produceState<Bitmap?>(initialValue = null, page) {
            value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeOrientedScaled(page.imagePath, 2400) }.getOrNull() }
        }
        var scale by remember(page.id) { mutableFloatStateOf(1f) }
        var offset by remember(page.id) { mutableStateOf(Offset.Zero) }
        val pagePreview = stringResource(R.string.a11y_page_preview)

        Column(Modifier.fillMaxSize().background(DarkChrome.backdrop)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onClick = onDismiss)
                Text(
                    stringResource(R.string.viewer_page_of, index + 1, count),
                    color = DarkChrome.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .pointerInput(page.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(page.id) {
                        // Double tap toggles between fitted and 2.5x.
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { image ->
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = pagePreview,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        }
                    )
                } ?: com.example.scanpro.core.designsystem.FileLoadingAnimation(onDark = true)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(DarkChrome.panel)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onPage != null) {
                    IconButton(onClick = { onPage(-1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, stringResource(R.string.scan_review_previous_page), tint = DarkChrome.text)
                    }
                    IconButton(onClick = { onPage(1) }, enabled = index < count - 1) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, stringResource(R.string.scan_review_next_page), tint = DarkChrome.text)
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                AccentButton(stringResource(R.string.doc_crop), onClick = onCrop)
            }
        }
    }
}
