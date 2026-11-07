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
 * Crossfades between themes (light and dark, or two colour themes) instead of snapping. When [target]
 * changes, the last frame is frozen into a bitmap, the theme switches underneath it, and the frozen frame
 * fades out. Only the overlay's alpha animates, so the app recomposes once rather than on every frame of
 * the fade. [T] must have a meaningful equals, such as a data class.
 */
@Composable
fun <T> ThemeTransition(target: T, content: @Composable (theme: T) -> Unit) {
    var current by remember { mutableStateOf(target) }
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val layer = rememberGraphicsLayer()

    LaunchedEffect(target) {
        if (target == current) return@LaunchedEffect
        try {
            frozenFrame = runCatching { layer.toImageBitmap() }.getOrNull()
            fade.snapTo(1f)
            current = target
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
            content(current)
        }
        frozenFrame?.let { frame ->
            Canvas(Modifier.fillMaxSize()) { drawImage(frame, alpha = fade.value) }
        }
    }
}
