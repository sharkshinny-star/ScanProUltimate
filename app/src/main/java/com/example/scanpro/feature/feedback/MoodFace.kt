package com.example.scanpro.feature.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable

/**
 * Each face's colour, red to green. Mid-tone on purpose: the dark features drawn on top stay readable on every one,
 * and the filled disc stands out on both the light and the dark sheet.
 */
val Mood.colour: Color
    get() = when (this) {
        Mood.AWFUL -> Color(0xFFEF5350)
        Mood.POOR -> Color(0xFFFF8A3D)
        Mood.OKAY -> Color(0xFFFFC233)
        Mood.GOOD -> Color(0xFF9CCC65)
        Mood.LOVED -> Color(0xFF3DBE74)
    }

/** Eyes and mouth on a filled face: a warm near-black, softer than pure black on the bright discs. */
private val FEATURES_ON_COLOUR = Color(0xFF2A1F1A)

/**
 * A face drawn with Compose shapes, so it looks the same on every phone (platform emoji differ by maker). Chosen,
 * it is a filled disc in [Mood.colour]; otherwise only an outline in [outline], so the row reads as a choice.
 * Purely visual: the caller gives it its label and selected state for TalkBack.
 */
@Composable
fun MoodFace(mood: Mood, selected: Boolean, outline: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val features = if (selected) FEATURES_ON_COLOUR else outline
        val strokeWidth = size.minDimension * 0.06f
        val r = size.minDimension / 2f - strokeWidth / 2f
        val c = center
        if (selected) {
            drawCircle(mood.colour, radius = r + strokeWidth / 2f, center = c)
        } else {
            drawCircle(outline, radius = r, center = c, style = Stroke(strokeWidth))
        }
        drawEyes(mood, c, r, features, strokeWidth)
        drawMouth(mood, c, r, features, strokeWidth)
    }
}

private fun DrawScope.drawEyes(mood: Mood, c: Offset, r: Float, colour: Color, stroke: Float) {
    val dx = r * 0.34f
    val y = c.y - r * 0.2f
    if (mood == Mood.LOVED) {
        // Happy, closed eyes: little arches.
        for (x in listOf(c.x - dx, c.x + dx)) {
            val path = Path().apply {
                moveTo(x - r * 0.15f, y + r * 0.05f)
                quadraticTo(x, y - r * 0.2f, x + r * 0.15f, y + r * 0.05f)
            }
            drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        return
    }
    drawCircle(colour, radius = r * 0.095f, center = Offset(c.x - dx, y))
    drawCircle(colour, radius = r * 0.095f, center = Offset(c.x + dx, y))
    if (mood == Mood.AWFUL) {
        // Brows tilted up towards the middle.
        val by = y - r * 0.24f
        drawLine(colour, Offset(c.x - dx - r * 0.16f, by + r * 0.02f), Offset(c.x - dx + r * 0.14f, by - r * 0.1f), stroke * 0.85f, StrokeCap.Round)
        drawLine(colour, Offset(c.x + dx + r * 0.16f, by + r * 0.02f), Offset(c.x + dx - r * 0.14f, by - r * 0.1f), stroke * 0.85f, StrokeCap.Round)
    }
}

private fun DrawScope.drawMouth(mood: Mood, c: Offset, r: Float, colour: Color, stroke: Float) {
    val half = r * 0.4f
    // Mouth line height and how far its middle bends: up (negative) for a frown, down for a smile.
    val (y, bend) = when (mood) {
        Mood.AWFUL -> c.y + r * 0.5f to -r * 0.42f
        Mood.POOR -> c.y + r * 0.44f to -r * 0.2f
        Mood.OKAY -> c.y + r * 0.38f to 0f
        Mood.GOOD -> c.y + r * 0.26f to r * 0.32f
        Mood.LOVED -> c.y + r * 0.16f to r * 0.62f
    }
    val path = Path().apply {
        moveTo(c.x - half, y)
        quadraticTo(c.x, y + bend, c.x + half, y)
    }
    if (mood == Mood.LOVED) {
        path.close()
        drawPath(path, colour)
        drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round))
    } else {
        drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
