package com.example.scanpro.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.scanpro.core.designsystem.bookHalfLetters
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint

/**
 * Book mode, two pages (R29): where the spread will be split. With a page outline from the edge detector, a dashed line
 * runs down the middle of that outline; without one (detection off, or nothing found yet) a fixed spread-shaped frame
 * shows where to put the book. Either way the two halves carry large translucent letters, A on the page saved first
 * ([bookHalfLetters]), so swapping the order visibly swaps the letters.
 */
@Composable
internal fun BookSplitGuide(detection: DocumentDetection?, quad: () -> CropQuad?, accent: Color, leftFirst: Boolean) {
    val measurer = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f))
    val (leftLetter, rightLetter) = bookHalfLetters(leftFirst)
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
        val outline = quad()
        val leftCentre: Offset
        val rightCentre: Offset
        if (detection != null && outline != null) {
            val frameWidth = detection.frameWidth.toFloat().coerceAtLeast(1f)
            val frameHeight = detection.frameHeight.toFloat().coerceAtLeast(1f)
            val scale = minOf(size.width / frameWidth, size.height / frameHeight)
            val left = (size.width - frameWidth * scale) / 2f
            val top = (size.height - frameHeight * scale) / 2f
            fun point(x: Float, y: Float): Offset = Offset(left + x * frameWidth * scale, top + y * frameHeight * scale)
            // The cut is at half the width of the page once it is straightened (BookSpread.split), and under perspective that
            // is not the line between the midpoints of the top and bottom edges: it is the image of the page's middle line.
            val map = com.example.scanpro.imaging.Homography.squareToQuad(
                outline.topLeft.x, outline.topLeft.y, outline.topRight.x, outline.topRight.y,
                outline.bottomRight.x, outline.bottomRight.y, outline.bottomLeft.x, outline.bottomLeft.y
            )
            fun onPage(u: Float, v: Float): Offset? =
                map?.let { com.example.scanpro.imaging.Homography.apply(it, u, v) }?.let { (x, y) -> point(x, y) }
            val topMid = onPage(0.5f, 0f)
                ?: point((outline.topLeft.x + outline.topRight.x) / 2f, (outline.topLeft.y + outline.topRight.y) / 2f)
            val bottomMid = onPage(0.5f, 1f)
                ?: point((outline.bottomLeft.x + outline.bottomRight.x) / 2f, (outline.bottomLeft.y + outline.bottomRight.y) / 2f)
            drawLine(accent.copy(alpha = 0.9f), topMid, bottomMid, strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
            fun half(a: NormalizedPoint, b: Offset, c: NormalizedPoint, d: Offset): Offset {
                val pa = point(a.x, a.y)
                val pc = point(c.x, c.y)
                return Offset((pa.x + b.x + pc.x + d.x) / 4f, (pa.y + b.y + pc.y + d.y) / 4f)
            }
            leftCentre = half(outline.topLeft, topMid, outline.bottomLeft, bottomMid)
            rightCentre = half(outline.topRight, topMid, outline.bottomRight, bottomMid)
        } else {
            // A spread is wider than tall: 90 % of the width, about 0.7 as high, centred.
            val width = size.width * 0.9f
            val height = minOf(width * 0.72f, size.height * 0.7f)
            val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.85f), topLeft, Size(width, height), CornerRadius(18f), style = Stroke(4f, pathEffect = dash))
            val cx = size.width / 2f
            drawLine(accent.copy(alpha = 0.95f), Offset(cx, topLeft.y), Offset(cx, topLeft.y + height), strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
            leftCentre = Offset(topLeft.x + width * 0.25f, topLeft.y + height / 2f)
            rightCentre = Offset(topLeft.x + width * 0.75f, topLeft.y + height / 2f)
        }
        listOf(leftCentre to leftLetter, rightCentre to rightLetter).forEach { (centre, letter) ->
            val laid = measurer.measure(letter, letterStyle)
            drawText(laid, topLeft = Offset(centre.x - laid.size.width / 2f, centre.y - laid.size.height / 2f))
        }
    }
}
