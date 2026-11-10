package com.example.scanpro.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import kotlin.math.roundToInt

/**
 * A tappable outline over each code seen in the camera preview, for frames with several codes (like Google Lens). Each
 * outline is at least 48dp square, white with a dark edge so it shows on light and dark backgrounds, and announces
 * the code it opens.
 */
@Composable
internal fun QrCodeOutlines(frame: FrameCodes, onPick: (ScannedCode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val minTouch = with(density) { 48.dp.toPx() }
        val shape = RoundedCornerShape(10.dp)
        frame.codes.forEach { item ->
            val mapped = item.box?.let { mapToFitCenter(it, frame.imageWidth, frame.imageHeight, viewWidth, viewHeight) }
            if (mapped != null) {
                val box = withMinimumTouchSize(mapped, minTouch, viewWidth, viewHeight)
                val description = stringResource(R.string.qr_outline_description, codeSummary(item.code))
                val widthDp = with(density) { box.width.toDp() }
                val heightDp = with(density) { box.height.toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
                        .size(widthDp, heightDp)
                        .clip(shape)
                        .background(Color(0xFF22D3EE).copy(alpha = 0.22f))
                        .border(5.dp, Color.Black.copy(alpha = 0.5f), shape)
                        .border(2.dp, Color.White, shape)
                        .clickable(role = Role.Button) { onPick(item.code) }
                        .semantics { contentDescription = description }
                )
            }
        }
    }
}

/** The codes found in one picture, one row each, so the person picks which to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrCodeChooserSheet(codes: List<ScannedCode>, onPick: (ScannedCode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.qr_chooser_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            codes.forEach { code ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onPick(code) },
                    headlineContent = { Text(titleFor(code)) },
                    supportingContent = { Text(codeSummary(code, 120), maxLines = 2) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
