package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Crossfades between light and dark instead of snapping. When [targetDark] changes, the last frame is
 * frozen into a bitmap, the theme switches underneath it, and the frozen frame fades out. Only the
 * overlay's alpha animates, so the app recomposes once rather than on every frame of the fade.
 */
@Composable
fun ThemeTransition(targetDark: Boolean, content: @Composable (dark: Boolean) -> Unit) {
    var dark by remember { mutableStateOf(targetDark) }
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val layer = rememberGraphicsLayer()

    LaunchedEffect(targetDark) {
        if (targetDark == dark) return@LaunchedEffect
        try {
            frozenFrame = runCatching { layer.toImageBitmap() }.getOrNull()
            fade.snapTo(1f)
            dark = targetDark
            if (frozenFrame != null) {
                withFrameNanos { } // let the new theme draw once under the overlay
                fade.animateTo(0f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
            }
        } finally {
            // Also on cancellation (a second quick toggle), so a frozen frame is never left on screen.
            frozenFrame = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content(dark)
        }
        frozenFrame?.let { frame ->
            Canvas(Modifier.fillMaxSize()) { drawImage(frame, alpha = fade.value) }
        }
    }
}
