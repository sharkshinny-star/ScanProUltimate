package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * A card that answers a press like a physical tile (F197): it sinks a little and tilts in 3D towards the finger (press
 * near the top edge and that edge dips), then springs back level on release. Pass the same [interaction] the card's
 * clickable uses. Watches touches without consuming them, so clicks, long presses and scrolling are untouched. With
 * animations off it only shows the ripple.
 */
@Composable
fun Modifier.pressTilt(interaction: MutableInteractionSource, maxDegrees: Float = 7f, pressedScale: Float = 0.965f): Modifier {
    val motion = rememberMotionEnabled()
    if (!motion) return this
    val pressed by interaction.collectIsPressedAsState()
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // -1..1 from the centre: where on the card the finger is.
    val fx = if (pressed && touch.isSpecified && size.width > 0) (touch.x / size.width * 2f - 1f).coerceIn(-1f, 1f) else 0f
    val fy = if (pressed && touch.isSpecified && size.height > 0) (touch.y / size.height * 2f - 1f).coerceIn(-1f, 1f) else 0f
    val bounce = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    // The pressed edge sinks: a press at the top turns the top edge away (positive rotationX), at the right the right edge.
    val tiltX by animateFloatAsState(-fy * maxDegrees, bounce, label = "tiltX")
    val tiltY by animateFloatAsState(fx * maxDegrees, bounce, label = "tiltY")
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, bounce, label = "pressScale")
    return this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.let { touch = it.position }
                }
            }
        }
        .graphicsLayer {
            rotationX = tiltX
            rotationY = tiltY
            scaleX = scale
            scaleY = scale
            cameraDistance = 10f * density
        }
}
