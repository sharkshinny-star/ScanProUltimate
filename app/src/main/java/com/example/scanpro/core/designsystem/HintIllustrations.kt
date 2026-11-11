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

/** A page found and straightened: the scan beam sweeps the sheet while the brackets lock on, the way the camera does. */
@Composable
fun DocumentScanIllustration(modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val shadow = illustrationShadow()
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "documentScan")
    val beam = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beam"
    )
    val lock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3_200
                0f at 0
                0f at 400 using FastOutSlowInEasing
                1f at 1_000
                1f at 2_800 using FastOutSlowInEasing
                0f at 3_200
            }
        ),
        label = "lock"
    )
    Canvas(modifier.fillMaxSize()) {
        val height = size.height * 0.7f
        val width = height * 0.74f
        val sheet = Rect(Offset((size.width - width) / 2f, (size.height - height) / 2f), Size(width, height))
        drawSheet(sheet, brand.paper, brand.ink, shadow)
        drawBeam(sheet, if (motion) beam.value else 0.55f, brand.scanLine)
        val locked = if (motion) lock.value else 1f
        drawBrackets(
            sheet.inflate(10.dp.toPx() + (1f - locked) * 10.dp.toPx()),
            arm = 16.dp.toPx(),
            stroke = 3.5.dp.toPx(),
            color = lerp(idle, brand.scanLine, locked)
        )
    }
}

/** Both sides of a card: it turns over between the brackets, front then back. */
@Composable
fun IdCardIllustration(modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val shadow = illustrationShadow()
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "idCard")
    val turn = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4_400
                0f at 0
                0f at 1_500 using FastOutSlowInEasing
                180f at 2_100
                180f at 3_700 using FastOutSlowInEasing
                360f at 4_300
                360f at 4_400
            }
        ),
        label = "turn"
    )
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cardWidth = minOf(maxWidth * 0.5f, maxHeight * 0.56f * CARD_RATIO)
        val cardHeight = cardWidth / CARD_RATIO
        // The brackets and the shadow stay put while the card turns between them.
        Canvas(Modifier.size(cardWidth + 36.dp, cardHeight + 36.dp)) {
            val card = Rect(Offset(18.dp.toPx(), 18.dp.toPx()), Size(cardWidth.toPx(), cardHeight.toPx()))
            drawOval(shadow, Offset(card.left + card.width * 0.1f, card.bottom + 4.dp.toPx()), Size(card.width * 0.8f, 8.dp.toPx()))
            drawBrackets(card.inflate(10.dp.toPx()), arm = 14.dp.toPx(), stroke = 3.5.dp.toPx(), color = brand.scanLine)
        }
        Canvas(
            Modifier
                .size(cardWidth, cardHeight)
                .graphicsLayer {
                    rotationY = if (motion) turn.value else 0f
                    cameraDistance = 16f * density
                }
        ) {
            val angle = if (motion) turn.value % 360f else 0f
            if (angle < 90f || angle > 270f) {
                drawCardFront(brand)
            } else {
                // Past the half turn the layer shows its mirror image; flip the drawing back so the back reads right.
                withTransform({ scale(-1f, 1f) }) { drawCardBack(brand) }
            }
        }
    }
}

/** An open book: the dashed line down the middle is where the spread is cut, and the two pages part along it. */
@Composable
fun BookScanIllustration(modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val shadow = illustrationShadow()
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "book")
    val split = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3_000
                0f at 0
                0f at 900 using FastOutSlowInEasing
                1f at 1_500
                1f at 2_400 using FastOutSlowInEasing
                0f at 3_000
            }
        ),
        label = "split"
    )
    val march = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "march")
    Canvas(modifier.fillMaxSize()) {
        val bookHeight = size.height * 0.6f
        val pageWidth = minOf(bookHeight * 0.78f, size.width * 0.3f)
        val centre = size.width / 2f
        val top = (size.height - bookHeight) / 2f
        val dip = bookHeight * 0.06f
        val apart = (if (motion) split.value else 0.6f) * 10.dp.toPx()
        drawRoundRect(
            shadow,
            Offset(centre - pageWidth - apart, top + bookHeight * 0.07f),
            Size((pageWidth + apart) * 2f, bookHeight),
            CornerRadius(12.dp.toPx())
        )
        for (side in floatArrayOf(-1f, 1f)) {
            val inner = centre + side * apart
            val outer = inner + side * pageWidth
            val middle = (inner + outer) / 2f
            val page = Path().apply {
                moveTo(inner, top + dip)
                quadraticTo(middle, top - dip * 0.3f, outer, top)
                lineTo(outer, top + bookHeight)
                quadraticTo(middle, top + bookHeight + dip * 0.2f, inner, top + bookHeight + dip)
                close()
            }
            drawPath(page, brand.paper)
            // A little shade where the page curves into the spine.
            drawPath(
                page,
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent),
                    startX = inner,
                    endX = inner + side * pageWidth * 0.3f
                )
            )
            val ink = brand.ink.copy(alpha = brand.ink.alpha * 0.7f)
            val lineStroke = bookHeight * 0.034f
            // Text lines start at each page's left edge, as in a printed book.
            val pageLeft = minOf(inner, outer)
            BookLines.forEachIndexed { i, fraction ->
                val y = top + bookHeight * (0.2f + i * 0.13f)
                val from = pageLeft + pageWidth * 0.14f
                drawLine(ink, Offset(from, y), Offset(from + pageWidth * fraction * 0.9f, y), lineStroke, StrokeCap.Round)
            }
        }
        val dash = 6.dp.toPx()
        val gapLength = 5.dp.toPx()
        drawLine(
            brand.scanLine,
            Offset(centre, top - 8.dp.toPx()),
            Offset(centre, top + bookHeight + dip + 8.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gapLength), phase = -(if (motion) march.value else 0f) * (dash + gapLength))
        )
        drawBrackets(
            Rect(centre - pageWidth - apart - 14.dp.toPx(), top - 14.dp.toPx(), centre + pageWidth + apart + 14.dp.toPx(), top + bookHeight + dip + 12.dp.toPx()),
            arm = 16.dp.toPx(),
            stroke = 3.5.dp.toPx(),
            color = brand.scanLine
        )
    }
}

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
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "toolFlow")
    val flow = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "flow")
    Canvas(modifier.fillMaxSize()) {
        val progress = if (motion) flow.value else 0.35f
        val tile = minOf(size.height * 0.42f, 72.dp.toPx())
        val centre = Offset(size.width / 2f, size.height / 2f)
        val sheetWidth = tile * 0.66f
        val sheetHeight = tile * 0.86f
        val spread = minOf(size.width * 0.3f, tile * 2.1f)
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

        drawToolInput(input, inputCentre, sheetWidth, sheetHeight, brand, shadow)

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
        drawSheet(result, brand.paper, brand.ink, shadow, lines = 3)
        val badge = sheetWidth * 0.26f
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
private val BookLines = floatArrayOf(0.62f, 0.68f, 0.54f, 0.66f, 0.44f)
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

/** The mint scan line at [position] (0 top, 1 bottom) of [sheet], with a faint wake behind it. */
private fun DrawScope.drawBeam(sheet: Rect, position: Float, color: Color) {
    val y = sheet.top + sheet.height * (0.06f + 0.88f * position)
    val wake = sheet.height * 0.16f
    val wakeTop = max(sheet.top, y - wake)
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.28f)), startY = y - wake, endY = y),
        Offset(sheet.left, wakeTop),
        Size(sheet.width, y - wakeTop)
    )
    val overhang = 8.dp.toPx()
    drawLine(color, Offset(sheet.left - overhang, y), Offset(sheet.right + overhang, y), 3.dp.toPx(), StrokeCap.Round)
}

/** Scanner brackets on the four corners of [rect]. */
private fun DrawScope.drawBrackets(rect: Rect, arm: Float, stroke: Float, color: Color) {
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
    }
    corner(rect.left, rect.top, 1f, 1f)
    corner(rect.right, rect.top, -1f, 1f)
    corner(rect.left, rect.bottom, 1f, -1f)
    corner(rect.right, rect.bottom, -1f, -1f)
}

private fun DrawScope.drawCardFront(brand: ScanProColors) {
    drawRoundRect(
        Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd), Offset.Zero, Offset(size.width, size.height)),
        cornerRadius = CornerRadius(size.height * 0.1f)
    )
    val photo = size.height * 0.46f
    val photoLeft = size.width * 0.08f
    val photoTop = size.height * 0.24f
    drawRoundRect(brand.onGradient.copy(alpha = 0.9f), Offset(photoLeft, photoTop), Size(photo * 0.82f, photo), CornerRadius(photo * 0.12f))
    val person = brand.gradientStart.copy(alpha = 0.55f)
    drawCircle(person, photo * 0.17f, Offset(photoLeft + photo * 0.41f, photoTop + photo * 0.38f))
    drawRoundRect(person, Offset(photoLeft + photo * 0.14f, photoTop + photo * 0.62f), Size(photo * 0.54f, photo * 0.3f), CornerRadius(photo * 0.15f))
    val textLeft = photoLeft + photo * 0.82f + size.width * 0.07f
    val lineHeight = size.height * 0.07f
    floatArrayOf(0.42f, 0.34f, 0.38f).forEachIndexed { i, fraction ->
        drawRoundRect(
            brand.onGradientMuted.copy(alpha = if (i == 0) 0.95f else 0.6f),
            Offset(textLeft, size.height * (0.3f + i * 0.17f)),
            Size(size.width * fraction, lineHeight),
            CornerRadius(lineHeight)
        )
    }
    drawRoundRect(brand.scanLine.copy(alpha = 0.9f), Offset(size.width * 0.78f, size.height * 0.1f), Size(size.width * 0.13f, size.height * 0.16f), CornerRadius(size.height * 0.03f))
}

private fun DrawScope.drawCardBack(brand: ScanProColors) {
    drawRoundRect(
        Brush.linearGradient(listOf(brand.gradientEnd, brand.gradientStart), Offset.Zero, Offset(size.width, size.height)),
        cornerRadius = CornerRadius(size.height * 0.1f)
    )
    drawRect(Color.Black.copy(alpha = 0.38f), Offset(0f, size.height * 0.14f), Size(size.width, size.height * 0.18f))
    val lineHeight = size.height * 0.07f
    floatArrayOf(0.78f, 0.64f, 0.7f).forEachIndexed { i, fraction ->
        drawRoundRect(
            brand.onGradientMuted.copy(alpha = 0.6f),
            Offset(size.width * 0.08f, size.height * (0.46f + i * 0.15f)),
            Size(size.width * fraction, lineHeight),
            CornerRadius(lineHeight)
        )
    }
}

/** The file a tool starts from; two of them, fanned, when it takes several. */
private fun DrawScope.drawToolInput(input: HintInput, centre: Offset, width: Float, height: Float, brand: ScanProColors, shadow: Color) {
    val rect = Rect(Offset(centre.x - width / 2f, centre.y - height / 2f), Size(width, height))
    val several = input == HintInput.Pdfs || input == HintInput.Images
    if (several) drawInputGlyph(rect.translate(-width * 0.2f, -height * 0.12f), input, brand, shadow, dimmed = true)
    drawInputGlyph(if (several) rect.translate(width * 0.08f, height * 0.06f) else rect, input, brand, shadow, dimmed = false)
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
        HintInput.Pdf, HintInput.Pdfs -> {
            drawSheet(rect, brand.paper, brand.ink, shadow, lines = 3)
            if (!dimmed) {
                drawRoundRect(
                    PdfMark,
                    Offset(rect.left - rect.width * 0.08f, rect.bottom - rect.height * 0.3f),
                    Size(rect.width * 0.52f, rect.height * 0.18f),
                    CornerRadius(rect.height * 0.04f)
                )
            }
        }
    }
}
