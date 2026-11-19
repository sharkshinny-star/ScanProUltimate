package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the items in a [DragToReorderHint] are laid out: a list of files, or a grid of pages. */
enum class DragHintLayout { List, Grid }

/** Length of one demonstration, and how many play before it holds still. */
private const val HINT_PERIOD = 3_400L
private const val HINT_LOOPS = 3

/**
 * A small looping demonstration of press, hold and drag to reorder: a fingertip presses an item, a ring fills
 * while it is held, the item lifts with a shadow and is dragged to the first place while the items it passes
 * slide aside, then it is dropped. It plays [HINT_LOOPS] times, then holds the mid-drag frame (the picture that
 * explains it best); with animations off it shows only that frame. Decorative: the text beside it says the same.
 */
@Composable
fun DragToReorderHint(layout: DragHintLayout, modifier: Modifier = Modifier, width: Dp = 72.dp, height: Dp = 60.dp) {
    val motion = rememberMotionEnabled()
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val elapsed = remember { mutableLongStateOf(-1L) }
    if (motion && !inspection) {
        LaunchedEffect(Unit) {
            var start = -1L
            while (true) {
                val done = withInfiniteAnimationFrameMillis { now ->
                    if (start < 0) start = now
                    elapsed.longValue = now - start
                    now - start >= HINT_PERIOD * HINT_LOOPS
                }
                if (done) {
                    elapsed.longValue = -1L
                    break
                }
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val brand = ScanProTheme.colors
    val card = scheme.surfaceContainerHighest
    val line = scheme.onSurfaceVariant.copy(alpha = 0.35f)
    val accents = listOf(brand.gradientStart, scheme.tertiary, brand.scanLine, scheme.secondary, brand.gradientEnd, scheme.primary)
    val outline = scheme.primary
    val finger = scheme.onSurface
    val fingerRing = scheme.surface

    Canvas(modifier.size(width, height)) {
        val e = elapsed.longValue
        // Still frame: the item lifted halfway along its drag, the finger on it.
        val p = if (e < 0) 0.5f else (e % HINT_PERIOD).toFloat() / HINT_PERIOD
        val slots = if (layout == DragHintLayout.List) listSlots() else gridSlots()
        val source = slots.lastIndex.coerceAtMost(if (layout == DragHintLayout.List) 2 else 4)
        val target = 0

        // After the drop the new order rests, fades out, and fades back in from the original order.
        val resetting = e >= 0 && p >= 0.92f
        val q = if (resetting) 0f else p
        val alpha = when {
            e < 0 -> 1f
            resetting -> ease((p - 0.92f) / 0.08f)
            else -> 1f - ease((p - 0.84f) / 0.06f)
        }

        // Timeline, as fractions of the cycle.
        val approach = ease((q - 0.02f) / 0.12f)          // finger arrives
        val hold = ease((q - 0.16f) / 0.16f)              // ring fills while pressed
        val lift = ease((q - 0.30f) / 0.06f) * (1f - ease((q - 0.68f) / 0.06f))
        val move = ease((q - 0.36f) / 0.30f)              // dragged to the first place
        val release = ease((q - 0.70f) / 0.10f)           // finger leaves

        val travel = move * (source - target)
        fun slotOf(index: Int): Rect = when {
            index == source -> lerpRect(slots[source], slots[target], move)
            index in target until source -> {
                // Slides one place on as the held item passes it.
                val distance = (source - index).toFloat()
                val shift = ease((travel - (distance - 0.8f)) / 0.6f)
                lerpRect(slots[index], slots[index + 1], shift)
            }
            else -> slots[index]
        }

        // Resting items first, the held one on top.
        for (i in slots.indices) {
            if (i == source) continue
            drawItem(slotOf(i), layout, card, line, accents[i % accents.size], alpha, lifted = 0f, outline = outline)
        }
        val held = slotOf(source)
        drawItem(held, layout, card, line, accents[source % accents.size], alpha, lifted = lift, outline = outline)

        // The fingertip: comes in from below right, presses, rides the item, then lifts off.
        val touch = Offset(held.center.x + held.width * 0.12f, held.center.y)
        val from = Offset(this.size.width * 1.05f, this.size.height * 1.1f)
        val fingerAlpha = alpha * approach * (1f - release)
        if (fingerAlpha > 0f) {
            val at = Offset(from.x + (touch.x - from.x) * approach, from.y + (touch.y - from.y) * approach)
            val r = 7.dp.toPx()
            val pressed = if (hold > 0f && release < 1f) 0.86f else 1f
            // A ripple as the finger lands.
            if (hold in 0.01f..0.99f) {
                drawCircle(finger.copy(alpha = 0.18f * (1f - hold) * fingerAlpha), radius = r * (1.2f + 1.4f * hold), center = at)
            }
            // The hold ring fills around the fingertip; when full, the item lifts.
            if (hold > 0f && lift < 1f) {
                drawArc(
                    color = outline.copy(alpha = fingerAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f * hold,
                    useCenter = false,
                    topLeft = Offset(at.x - r * 1.45f, at.y - r * 1.45f),
                    size = Size(r * 2.9f, r * 2.9f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            scale(pressed, at) {
                drawCircle(finger.copy(alpha = 0.42f * fingerAlpha), radius = r, center = at)
                drawCircle(fingerRing.copy(alpha = 0.9f * fingerAlpha), radius = r, center = at, style = Stroke(width = 1.4.dp.toPx()))
            }
        }
    }
}

/** Three file rows, top to bottom. */
private fun DrawScope.listSlots(): List<Rect> {
    val gap = size.height * 0.07f
    val rowHeight = (size.height - gap * 4) / 3f
    return List(3) { i ->
        val top = gap + i * (rowHeight + gap)
        Rect(size.width * 0.06f, top, size.width * 0.94f, top + rowHeight)
    }
}

/** Six pages, three across and two down, in reading order. */
private fun DrawScope.gridSlots(): List<Rect> {
    val gapX = size.width * 0.06f
    val gapY = size.height * 0.07f
    val w = (size.width - gapX * 4) / 3f
    val h = (size.height - gapY * 3) / 2f
    return List(6) { i ->
        val col = i % 3
        val row = i / 3
        val left = gapX + col * (w + gapX)
        val top = gapY + row * (h + gapY)
        Rect(left, top, left + w, top + h)
    }
}

/** One file row or page tile, raised by [lifted] (0 to 1): bigger, with a shadow and an outline. */
private fun DrawScope.drawItem(
    slot: Rect,
    layout: DragHintLayout,
    card: Color,
    line: Color,
    accent: Color,
    alpha: Float,
    lifted: Float,
    outline: Color
) {
    if (alpha <= 0f) return
    val grow = 1f + 0.08f * lifted
    val r = Rect(
        slot.center.x - slot.width * grow / 2f,
        slot.center.y - slot.height * grow / 2f,
        slot.center.x + slot.width * grow / 2f,
        slot.center.y + slot.height * grow / 2f
    )
    val corner = CornerRadius(minOf(r.width, r.height) * 0.18f)
    if (lifted > 0f) {
        val drop = 2.5.dp.toPx() * lifted
        drawRoundRect(
            Color.Black.copy(alpha = 0.22f * lifted * alpha),
            topLeft = Offset(r.left + drop * 0.3f, r.top + drop),
            size = r.size,
            cornerRadius = corner
        )
    }
    drawRoundRect(card.copy(alpha = alpha), topLeft = r.topLeft, size = r.size, cornerRadius = corner)
    if (lifted > 0f) {
        drawRoundRect(outline.copy(alpha = lifted * alpha), topLeft = r.topLeft, size = r.size, cornerRadius = corner, style = Stroke(width = 1.4.dp.toPx()))
    }
    val stroke = maxOf(1.dp.toPx(), r.height * 0.1f)
    if (layout == DragHintLayout.List) {
        // Thumbnail, two lines of name and size, and the drag handle.
        val pad = r.height * 0.18f
        val thumb = Rect(r.left + pad, r.top + pad, r.left + pad + (r.height - pad * 2) * 0.8f, r.bottom - pad)
        drawRoundRect(accent.copy(alpha = alpha), topLeft = thumb.topLeft, size = thumb.size, cornerRadius = CornerRadius(thumb.width * 0.2f))
        val textLeft = thumb.right + pad
        val handleX = r.right - pad - r.height * 0.3f
        val y1 = r.top + r.height * 0.38f
        val y2 = r.top + r.height * 0.66f
        drawLine(line.copy(alpha = line.alpha * alpha), Offset(textLeft, y1), Offset(textLeft + (handleX - textLeft - pad) * 0.9f, y1), stroke, StrokeCap.Round)
        drawLine(line.copy(alpha = line.alpha * alpha * 0.7f), Offset(textLeft, y2), Offset(textLeft + (handleX - textLeft - pad) * 0.55f, y2), stroke, StrokeCap.Round)
        val handleW = r.height * 0.3f
        listOf(0.4f, 0.6f).forEach { f ->
            val y = r.top + r.height * f
            drawLine(line.copy(alpha = line.alpha * alpha * 1.6f), Offset(handleX, y), Offset(handleX + handleW, y), stroke * 0.8f, StrokeCap.Round)
        }
    } else {
        // A page: a coloured heading bar and a few lines of text.
        val pad = r.width * 0.16f
        drawLine(accent.copy(alpha = alpha), Offset(r.left + pad, r.top + r.height * 0.2f), Offset(r.right - pad * 1.6f, r.top + r.height * 0.2f), stroke * 1.2f, StrokeCap.Round)
        listOf(0.42f to 1f, 0.6f to 0.8f, 0.78f to 0.6f).forEach { (f, w) ->
            val y = r.top + r.height * f
            drawLine(line.copy(alpha = line.alpha * alpha), Offset(r.left + pad, y), Offset(r.left + pad + (r.width - pad * 2) * w, y), stroke * 0.8f, StrokeCap.Round)
        }
    }
}

private fun ease(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpRect(a: Rect, b: Rect, f: Float): Rect = Rect(
    a.left + (b.left - a.left) * f,
    a.top + (b.top - a.top) * f,
    a.right + (b.right - a.right) * f,
    a.bottom + (b.bottom - a.bottom) * f
)
