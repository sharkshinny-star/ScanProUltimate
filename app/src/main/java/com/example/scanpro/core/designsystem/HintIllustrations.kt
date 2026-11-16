package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * The pictures on the first-time introductions (F146). Each is drawn in Compose in the welcome's visual language
 * (paper, ink, mint scan line, brand gradient), so it follows the colour theme and stays crisp at any size, and each
 * moves gently to show what the feature does. Animated values are read in the draw phase, so they redraw without
 * recomposing; with animations turned off in the system settings every picture holds a still, readable frame.
 */

/** What a tool starts from, for the left-hand side of [ToolFlowIllustration]. */
enum class HintInput { Pdf, Pdfs, Image, Images, Office }

/**
 * What a tool does, as a little production line: the file it starts from on the left, dots flowing into the tool's
 * own tile (which breathes, with a ripple going out from it) and on to the finished file, ticked, on the right.
 */
@Composable
fun ToolFlowIllustration(icon: ImageVector, tone: TileTone, input: HintInput, modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val container = tone.container()
    val content = tone.content()
    val shadow = illustrationShadow()
    val painter = rememberVectorPainter(icon)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "toolFlow")
    val flow = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "flow")
    Canvas(modifier.fillMaxSize()) {
        val progress = if (motion) flow.value else 0.35f
        // Large enough to read at a glance (2026-11-16): the files are real pages with a "PDF" label, not glyphs.
        val tile = minOf(size.height * 0.44f, 80.dp.toPx())
        val centre = Offset(size.width / 2f, size.height / 2f)
        val sheetWidth = tile * 0.92f
        val sheetHeight = tile * 1.22f
        val spread = minOf(size.width * 0.32f, tile * 2.0f)
        val inputCentre = Offset(centre.x - spread, centre.y)
        val outputCentre = Offset(centre.x + spread, centre.y)

        // The ripple going out from the tool.
        drawCircle(
            container.copy(alpha = (1f - progress) * 0.9f),
            radius = tile * (0.56f + 0.5f * progress),
            center = centre,
            style = Stroke(2.dp.toPx())
        )
        // Dots carrying the file into the tool, and the result out of it.
        for (i in 0 until 3) {
            val f = (progress + i / 3f) % 1f
            val dot = brand.scanLine.copy(alpha = sin(f * PI).toFloat().coerceIn(0f, 1f))
            val radius = 3.dp.toPx()
            drawCircle(dot, radius, Offset(mix(inputCentre.x + sheetWidth * 0.75f, centre.x - tile * 0.72f, f), centre.y))
            drawCircle(dot, radius, Offset(mix(centre.x + tile * 0.72f, outputCentre.x - sheetWidth * 0.75f, f), centre.y))
        }

        drawToolInput(input, inputCentre, sheetWidth, sheetHeight, brand, shadow, measurer)

        val breathing = tile * (1f + 0.04f * sin(progress * 2f * PI).toFloat())
        val tileCorner = CornerRadius(breathing * 0.3f)
        val tileTopLeft = Offset(centre.x - breathing / 2f, centre.y - breathing / 2f)
        drawRoundRect(shadow, tileTopLeft + Offset(2.dp.toPx(), 5.dp.toPx()), Size(breathing, breathing), tileCorner)
        drawRoundRect(container, tileTopLeft, Size(breathing, breathing), tileCorner)
        val iconSize = breathing * 0.5f
        translate(centre.x - iconSize / 2f, centre.y - iconSize / 2f) {
            with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(content)) }
        }

        val result = Rect(Offset(outputCentre.x - sheetWidth / 2f, outputCentre.y - sheetHeight / 2f), Size(sheetWidth, sheetHeight))
        drawSheet(result, brand.paper, brand.ink, shadow, lines = 4)
        drawFileLabel(measurer, if (input == HintInput.Image) "IMG" else "PDF", result, if (input == HintInput.Image) brand.gradientStart else PdfMark)
        val badge = sheetWidth * 0.22f
        val badgeCentre = Offset(result.right - badge * 0.2f, result.bottom - badge * 0.2f)
        drawCircle(brand.scanLine, badge, badgeCentre)
        val tick = Path().apply {
            moveTo(badgeCentre.x - badge * 0.45f, badgeCentre.y + badge * 0.02f)
            lineTo(badgeCentre.x - badge * 0.1f, badgeCentre.y + badge * 0.36f)
            lineTo(badgeCentre.x + badge * 0.48f, badgeCentre.y - badge * 0.32f)
        }
        drawPath(tick, TickInk, style = Stroke(badge * 0.24f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Files arriving in a folder one after another, with the search lens hovering over it. [current]: replay the arrival. */
@Composable
fun FilesIllustration(current: Boolean, modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val scheme = MaterialTheme.colorScheme
    val folder = TileTone.Amber.container()
    val folderTab = TileTone.Amber.content().copy(alpha = 0.35f)
    val row = scheme.surface
    val text = scheme.onSurfaceVariant
    val accent = scheme.primary
    val shadow = illustrationShadow()
    val motion = rememberMotionEnabled()
    val arrival = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(current, motion) {
        if (current && motion) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, tween(1_100, easing = LinearEasing))
        }
    }
    val transition = rememberInfiniteTransition(label = "files")
    val bob = transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Canvas(modifier.fillMaxSize()) {
        val height = size.height * 0.66f
        val width = minOf(size.width * 0.6f, height * 1.5f)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f + 6.dp.toPx()
        drawRoundRect(folderTab, Offset(left, top - height * 0.09f), Size(width * 0.38f, height * 0.22f), CornerRadius(8.dp.toPx()))
        drawRoundRect(folder, Offset(left, top), Size(width, height), CornerRadius(14.dp.toPx()))
        val rowHeight = height * 0.22f
        val rowGap = height * 0.06f
        val rowWidth = width * 0.86f
        val rowLeft = left + width * 0.07f
        val corner = CornerRadius(8.dp.toPx())
        for (i in 0 until 3) {
            val shown = FastOutSlowInEasing.transform(((arrival.value - i * 0.2f) / 0.5f).coerceIn(0f, 1f))
            if (shown <= 0f) continue
            val y = top + height * 0.12f + i * (rowHeight + rowGap) + (1f - shown) * 18.dp.toPx()
            drawRoundRect(shadow.copy(alpha = shadow.alpha * shown), Offset(rowLeft, y + 2.dp.toPx()), Size(rowWidth, rowHeight), corner)
            drawRoundRect(row.copy(alpha = shown), Offset(rowLeft, y), Size(rowWidth, rowHeight), corner)
            val thumb = rowHeight * 0.7f
            val inset = rowHeight * 0.15f
            drawRoundRect(brand.ink.copy(alpha = 0.6f * shown), Offset(rowLeft + inset, y + inset), Size(thumb * 0.78f, thumb), CornerRadius(3.dp.toPx()))
            val lineHeight = rowHeight * 0.16f
            val textLeft = rowLeft + inset + thumb
            drawRoundRect(text.copy(alpha = 0.7f * shown), Offset(textLeft, y + rowHeight * 0.28f), Size(rowWidth * 0.45f, lineHeight), CornerRadius(lineHeight))
            drawRoundRect(text.copy(alpha = 0.4f * shown), Offset(textLeft, y + rowHeight * 0.56f), Size(rowWidth * 0.28f, lineHeight), CornerRadius(lineHeight))
        }
        val lift = (if (motion) bob.value else 0f) * 4.dp.toPx()
        val lens = Offset(left + width * 0.94f, top - height * 0.02f + lift)
        val radius = height * 0.13f
        drawCircle(row, radius, lens)
        drawCircle(accent, radius, lens, style = Stroke(3.dp.toPx()))
        drawLine(accent, lens + Offset(radius * 0.72f, radius * 0.72f), lens + Offset(radius * 1.4f, radius * 1.4f), 4.dp.toPx(), StrokeCap.Round)
    }
}

/** A few of the tools popping into a grid, with a highlight moving from one to the next. [current]: replay the pop. */
@Composable
fun ToolsGridIllustration(current: Boolean, modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val arrival = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(current, motion) {
        if (current && motion) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val transition = rememberInfiniteTransition(label = "toolsGrid")
    val spotlight = transition.animateFloat(
        initialValue = 0f,
        targetValue = GridTools.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(GridTools.size * 650, easing = LinearEasing)),
        label = "spotlight"
    )
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val tile = minOf(maxHeight * 0.3f, 52.dp)
        val gap = tile * 0.32f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            GridTools.chunked(3).forEachIndexed { rowIndex, rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowTools.forEachIndexed { columnIndex, (icon, tone) ->
                        val index = rowIndex * 3 + columnIndex
                        IconTile(
                            icon,
                            tone,
                            Modifier.graphicsLayer {
                                val shown = ((arrival.value - index * 0.1f) / 0.5f).coerceIn(0f, 1f)
                                val spot = spotlight.value - index
                                val lit = if (motion && spot >= 0f && spot < 1f) sin(spot * PI).toFloat() else 0f
                                val scale = (0.6f + 0.4f * backOut(shown)) * (1f + 0.1f * lit)
                                scaleX = scale
                                scaleY = scale
                                alpha = shown
                            },
                            size = tile,
                            corner = tile * 0.3f,
                            iconSize = tile * 0.5f
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------------------------

private const val CARD_RATIO = 1.586f // ID-1, the size of bank and identity cards
private val SheetLines = floatArrayOf(0.70f, 0.62f, 0.68f, 0.46f, 0.64f, 0.54f)
private val TickInk = Color(0xFF00382A)
private val PdfMark = Color(0xFFD9472B)
private val Sun = Color(0xFFFFC24D)
private val GridTools = listOf(
    Icons.Default.Merge to TileTone.Indigo,
    Icons.AutoMirrored.Filled.CallSplit to TileTone.Sky,
    Icons.Default.Compress to TileTone.Amber,
    Icons.Default.Draw to TileTone.Violet,
    Icons.Default.Lock to TileTone.Rose,
    Icons.Default.Image to TileTone.Coral
)

@Composable
private fun illustrationShadow() = Color.Black.copy(alpha = if (isDarkTheme()) 0.4f else 0.12f)

private fun mix(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

/** Ease-out with a little overshoot, for things that pop into place. */
private fun backOut(t: Float): Float {
    val overshoot = 1.70158f
    val x = t - 1f
    return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x
}

/** A sheet of paper with a soft shadow and, when [lines] is above zero, a heading and that many lines of text. */
private fun DrawScope.drawSheet(rect: Rect, paper: Color, ink: Color, shadow: Color, lines: Int = SheetLines.size) {
    val corner = CornerRadius(rect.width * 0.08f)
    drawRoundRect(shadow, rect.topLeft + Offset(rect.width * 0.03f, rect.height * 0.04f), rect.size, corner)
    drawRoundRect(paper, rect.topLeft, rect.size, corner)
    if (lines <= 0) return
    val inset = rect.width * 0.15f
    val lineHeight = rect.height * 0.036f
    val radius = CornerRadius(lineHeight)
    drawRoundRect(ink, Offset(rect.left + inset, rect.top + rect.height * 0.13f), Size(rect.width * 0.42f, lineHeight * 1.6f), radius)
    val faint = ink.copy(alpha = ink.alpha * 0.6f)
    val step = 0.52f / lines
    for (i in 0 until lines) {
        val fraction = SheetLines[i % SheetLines.size]
        drawRoundRect(faint, Offset(rect.left + inset, rect.top + rect.height * (0.3f + i * step)), Size(rect.width * fraction, lineHeight), radius)
    }
}

/** The file a tool starts from; two of them, fanned, when it takes several. */
private fun DrawScope.drawToolInput(
    input: HintInput,
    centre: Offset,
    width: Float,
    height: Float,
    brand: ScanProColors,
    shadow: Color,
    measurer: androidx.compose.ui.text.TextMeasurer
) {
    val rect = Rect(Offset(centre.x - width / 2f, centre.y - height / 2f), Size(width, height))
    val several = input == HintInput.Pdfs || input == HintInput.Images
    if (several) drawInputGlyph(rect.translate(-width * 0.2f, -height * 0.12f), input, brand, shadow, dimmed = true)
    val front = if (several) rect.translate(width * 0.08f, height * 0.06f) else rect
    drawInputGlyph(front, input, brand, shadow, dimmed = false)
    when (input) {
        HintInput.Pdf, HintInput.Pdfs -> drawFileLabel(measurer, "PDF", front, PdfMark)
        HintInput.Office -> drawFileLabel(measurer, "DOC", front, brand.gradientStart)
        else -> Unit
    }
}

/** A coloured file-type tag ("PDF", "DOC") across the lower left of a page, readable at phone size. */
private fun DrawScope.drawFileLabel(measurer: androidx.compose.ui.text.TextMeasurer, text: String, page: Rect, colour: Color) {
    val tag = Rect(Offset(page.left - page.width * 0.1f, page.bottom - page.height * 0.32f), Size(page.width * 0.62f, page.height * 0.2f))
    drawRoundRect(colour, tag.topLeft, tag.size, CornerRadius(tag.height * 0.2f))
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = (tag.height * 0.58f).toSp(),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
        color = Color.White
    )
    val laid = measurer.measure(text, style)
    drawText(laid, topLeft = Offset(tag.center.x - laid.size.width / 2f, tag.center.y - laid.size.height / 2f))
}

private fun DrawScope.drawInputGlyph(rect: Rect, input: HintInput, brand: ScanProColors, shadow: Color, dimmed: Boolean) {
    val corner = CornerRadius(rect.width * 0.08f)
    when (input) {
        HintInput.Image, HintInput.Images -> {
            drawRoundRect(shadow, rect.topLeft + Offset(rect.width * 0.03f, rect.height * 0.04f), rect.size, corner)
            drawRoundRect(brand.paper, rect.topLeft, rect.size, corner)
            val inner = rect.deflate(rect.width * 0.1f)
            drawRoundRect(brand.gradientEnd.copy(alpha = if (dimmed) 0.18f else 0.28f), inner.topLeft, inner.size, CornerRadius(inner.width * 0.06f))
            drawCircle(Sun.copy(alpha = if (dimmed) 0.6f else 1f), inner.width * 0.13f, Offset(inner.left + inner.width * 0.72f, inner.top + inner.height * 0.26f))
            val hills = Path().apply {
                moveTo(inner.left, inner.bottom)
                lineTo(inner.left + inner.width * 0.36f, inner.top + inner.height * 0.5f)
                lineTo(inner.left + inner.width * 0.58f, inner.top + inner.height * 0.74f)
                lineTo(inner.left + inner.width * 0.76f, inner.top + inner.height * 0.6f)
                lineTo(inner.right, inner.bottom)
                close()
            }
            drawPath(hills, brand.gradientStart.copy(alpha = if (dimmed) 0.45f else 0.75f))
        }
        HintInput.Office -> {
            drawSheet(rect, brand.paper, brand.ink, shadow, lines = 3)
            drawRoundRect(brand.gradientStart, rect.topLeft, Size(rect.width, rect.height * 0.12f), CornerRadius(rect.width * 0.08f))
        }
        HintInput.Pdf, HintInput.Pdfs -> drawSheet(rect, brand.paper, brand.ink, shadow, lines = 4)
    }
}
