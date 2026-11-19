package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The app's own loading and processing animations, drawn with Compose in the brand colours so they stay
 * crisp at any size and follow the light, dark and accent palettes:
 *
 *  - [FileLoadingAnimation]: a page floating inside scanner brackets while the mint scan line reads it
 *    and its lines appear; shown while a file or page is opened.
 *  - [ProcessingAnimation]: a ring that fills with the real progress (or circles while the length is
 *    unknown) around a page that the scan line keeps passing over, with a turning gear; shown while a
 *    tool or a document task works.
 *  - [LoadingShimmer], [ScanBeamProgressBar] and [ProcessingDots]: the small versions for thumbnails,
 *    the strip under an app bar and the inside of a busy button.
 *
 * Every loop reads the frame clock inside its draw block, so a running animation redraws but never
 * recomposes, and all loops stay still when the person has turned animations off.
 */

/**
 * Frame time in milliseconds while [running]; stays still otherwise. Shared timing keeps placeholders in
 * step. Previews hold a mid-animation frame, so a still picture shows a page being read, not a blank one.
 */
@Composable
private fun rememberLoopClock(running: Boolean): LongState {
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val clock = remember { mutableLongStateOf(if (inspection) 1_150L else 0L) }
    if (running && !inspection) {
        LaunchedEffect(Unit) {
            // The "infinite" frame request tells UI tests not to wait for this loop to settle.
            while (true) withInfiniteAnimationFrameMillis { clock.longValue = it }
        }
    }
    return clock
}

/** Position in a repeating cycle of [periodMillis], from 0 up to (not including) 1. */
private fun cycle(timeMillis: Long, periodMillis: Long): Float = (timeMillis % periodMillis).toFloat() / periodMillis

private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Colours for the illustrated page, resolved once per composition from the theme. */
private class PagePalette(
    val sheet: Color,
    val edge: Color,
    val fold: Color,
    val ink: Color,
    val inkStrong: Color,
    val beam: Color,
    val bracket: Color,
    val shadow: Color
)

@Composable
private fun rememberPagePalette(onDark: Boolean): PagePalette {
    val brand = ScanProTheme.colors
    val scheme = MaterialTheme.colorScheme
    val darkSurface = onDark || scheme.surface.luminance() < 0.4f
    return remember(brand, scheme, onDark) {
        PagePalette(
            sheet = brand.paper,
            // A faint outline keeps the paper visible on white too, such as a viewer page still rendering.
            edge = scheme.primary.copy(alpha = 0.28f),
            fold = brand.ink.copy(alpha = 0.55f),
            ink = brand.ink,
            inkStrong = brand.gradientStart,
            beam = brand.scanLine,
            bracket = if (onDark) Color.White.copy(alpha = 0.7f) else scheme.primary.copy(alpha = 0.6f),
            shadow = Color.Black.copy(alpha = if (darkSurface) 0.38f else 0.10f)
        )
    }
}

/** A sheet of paper with its top-right corner folded over, filling [page]. */
private fun sheetPath(page: Rect): Pair<Path, Path> {
    val r = page.width * 0.1f
    val fold = page.width * 0.26f
    val sheet = Path().apply {
        moveTo(page.left + r, page.top)
        lineTo(page.right - fold, page.top)
        lineTo(page.right, page.top + fold)
        lineTo(page.right, page.bottom - r)
        quadraticTo(page.right, page.bottom, page.right - r, page.bottom)
        lineTo(page.left + r, page.bottom)
        quadraticTo(page.left, page.bottom, page.left, page.bottom - r)
        lineTo(page.left, page.top + r)
        quadraticTo(page.left, page.top, page.left + r, page.top)
        close()
    }
    val corner = Path().apply {
        moveTo(page.right - fold, page.top)
        lineTo(page.right - fold, page.top + fold - r * 0.4f)
        quadraticTo(page.right - fold, page.top + fold, page.right - fold + r * 0.4f, page.top + fold)
        lineTo(page.right, page.top + fold)
        close()
    }
    return sheet to corner
}

/** Relative widths of the text lines drawn on the page; the first is a heading. */
private val PAGE_LINES = floatArrayOf(0.55f, 1f, 0.82f, 1f, 0.64f)

private fun lineY(page: Rect, index: Int): Float = page.top + page.height * (0.27f + index * 0.125f)

/** The mint scan line running a little past both edges of the page, with a glow on the side it came from. */
private fun DrawScope.drawScanBeam(page: Rect, sheet: Path, y: Float, colour: Color, alpha: Float, movingDown: Boolean, overhangFraction: Float = 0.12f) {
    if (alpha <= 0f) return
    val glow = page.height * 0.2f
    clipPath(sheet) {
        val top = if (movingDown) y - glow else y
        drawRect(
            brush = Brush.verticalGradient(
                if (movingDown) listOf(Color.Transparent, colour.copy(alpha = 0.34f * alpha))
                else listOf(colour.copy(alpha = 0.34f * alpha), Color.Transparent),
                startY = top,
                endY = top + glow
            ),
            topLeft = Offset(page.left, top),
            size = Size(page.width, glow)
        )
    }
    val overhang = page.width * overhangFraction
    drawLine(
        color = colour.copy(alpha = alpha),
        start = Offset(page.left - overhang, y),
        end = Offset(page.right + overhang, y),
        strokeWidth = page.width * 0.055f,
        cap = StrokeCap.Round
    )
}

/** Scanner brackets around [frame], as in the ScanPro mark. */
private fun DrawScope.drawBrackets(frame: Rect, colour: Color, arm: Float, width: Float) {
    val corners = listOf(
        Triple(frame.topLeft, 1f, 1f),
        Triple(frame.topRight, -1f, 1f),
        Triple(frame.bottomLeft, 1f, -1f),
        Triple(frame.bottomRight, -1f, -1f)
    )
    for ((corner, dx, dy) in corners) {
        val path = Path().apply {
            moveTo(corner.x, corner.y + dy * arm)
            lineTo(corner.x, corner.y)
            lineTo(corner.x + dx * arm, corner.y)
        }
        drawPath(path, colour, style = Stroke(width = width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Shown while a file or a page is being opened: a page floats inside scanner brackets while the mint
 * scan line runs down it and its lines appear behind the line, then the page clears and it starts again.
 * Holds a finished page, still, when animations are off.
 *
 * @param onDark set on the dark editor and camera screens, where the brackets turn white.
 */
@Composable
fun FileLoadingAnimation(modifier: Modifier = Modifier, size: Dp = 72.dp, onDark: Boolean = false) {
    val motion = rememberMotionEnabled()
    val clock = rememberLoopClock(motion)
    val palette = rememberPagePalette(onDark)
    Canvas(modifier.size(size).progressSemantics()) {
        val t = clock.longValue
        val s = this.size.minDimension
        val origin = Offset((this.size.width - s) / 2f, (this.size.height - s) / 2f)
        val p = if (motion) cycle(t, 1_900L) else 0.62f
        // A slow float on its own period, so the loop never looks mechanical.
        val bob = if (motion) sin(2f * PI.toFloat() * cycle(t, 2_600L)) else 0f
        val lift = -bob * s * 0.025f

        translate(origin.x, origin.y) {
            // Shadow on the desk: smaller and fainter while the page is lifted.
            val shadowWidth = s * (0.46f - 0.04f * bob)
            drawOval(
                color = palette.shadow.copy(alpha = palette.shadow.alpha * (1f - 0.3f * bob)),
                topLeft = Offset((s - shadowWidth) / 2f, s * 0.885f),
                size = Size(shadowWidth, s * 0.055f)
            )

            val page = Rect(s * 0.29f, s * 0.15f + lift, s * 0.71f, s * 0.8f + lift)
            val (sheet, corner) = sheetPath(page)
            drawPath(sheet, palette.sheet)
            if (palette.edge.alpha > 0f) drawPath(sheet, palette.edge, style = Stroke(width = s * 0.018f))
            drawPath(corner, palette.fold)

            // The scan line runs down over the first 70% of the cycle, then fades; the lines appear just
            // behind it and the finished page clears before the next pass.
            val sweep = smoothStep(p / 0.7f)
            val beamTop = page.top + page.height * 0.12f
            val beamBottom = page.bottom - page.height * 0.08f
            val beamY = beamTop + (beamBottom - beamTop) * sweep
            val beamAlpha = when {
                !motion -> 1f
                p < 0.06f -> p / 0.06f
                p < 0.7f -> 1f
                else -> (1f - (p - 0.7f) / 0.12f).coerceIn(0f, 1f)
            }
            val pageFade = if (motion && p > 0.88f) 1f - (p - 0.88f) / 0.12f else 1f
            val left = page.left + page.width * 0.17f
            val maxWidth = page.width * 0.66f
            PAGE_LINES.forEachIndexed { index, width ->
                val y = lineY(page, index)
                val reveal = if (motion) smoothStep((beamY - y) / (page.height * 0.16f)) else 1f
                if (reveal > 0f) {
                    drawLine(
                        color = (if (index == 0) palette.inkStrong.copy(alpha = 0.55f) else palette.ink).let { it.copy(alpha = it.alpha * pageFade) },
                        start = Offset(left, y),
                        end = Offset(left + maxWidth * width * reveal, y),
                        strokeWidth = page.width * if (index == 0) 0.075f else 0.055f,
                        cap = StrokeCap.Round
                    )
                }
            }
            // A faint second line trails the beam, so the sweep reads as motion even in a glance.
            if (motion && beamAlpha > 0f) {
                val trailY = beamTop + (beamBottom - beamTop) * smoothStep(((p - 0.045f) / 0.7f).coerceIn(0f, 1f))
                if (trailY < beamY - s * 0.01f) {
                    drawLine(
                        color = palette.beam.copy(alpha = 0.25f * beamAlpha),
                        start = Offset(page.left, trailY),
                        end = Offset(page.right, trailY),
                        strokeWidth = page.width * 0.028f,
                        cap = StrokeCap.Round
                    )
                }
            }
            drawScanBeam(page, sheet, beamY, palette.beam, beamAlpha, movingDown = true)

            // Brackets breathe with the float and flash mint for a beat as each pass completes,
            // answering the scan the way the real capture brackets do.
            val done = if (motion) {
                val rise = smoothStep(((p - 0.66f) / 0.08f).coerceIn(0f, 1f))
                val fall = 1f - smoothStep(((p - 0.8f) / 0.12f).coerceIn(0f, 1f))
                rise * fall
            } else 0f
            val inset = s * (0.07f + 0.012f * bob - 0.008f * done)
            drawBrackets(
                Rect(inset, inset, s - inset, s - inset),
                lerp(palette.bracket, palette.beam, done),
                arm = s * (0.13f + 0.012f * done),
                width = s * 0.035f
            )
        }
    }
}

/**
 * [FileLoadingAnimation] with an optional line of text under it, centred: the full "opening a file" state
 * for a screen, a dialog or an editor canvas.
 */
@Composable
fun FileLoadingState(label: String?, modifier: Modifier = Modifier, size: Dp = 88.dp, onDark: Boolean = false) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FileLoadingAnimation(size = size, onDark = onDark)
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onDark) DarkChrome.mutedText else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A cog with [teeth] teeth around (0, 0), outer radius 1; scale it when drawing. */
private fun gearPath(teeth: Int = 8, inner: Float = 0.76f): Path = Path().apply {
    val step = 2f * PI.toFloat() / teeth
    val tip = step * 0.18f
    val root = step * 0.3f
    for (i in 0 until teeth) {
        val a = i * step
        val points = listOf(inner to a - root, 1f to a - tip, 1f to a + tip, inner to a + root)
        points.forEachIndexed { k, (radius, angle) ->
            val x = radius * cos(angle)
            val y = radius * sin(angle)
            if (i == 0 && k == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    close()
}

private val GEAR = gearPath()

/**
 * Shown while a tool or a document task works. A thick arc fills with the real [progress] (0 to 1) over a
 * dial of fine ticks, with a soft glow under it and a pulsing head trailing sparks; while [progress] is null
 * the arc circles instead, and it eases into the filling arc when the tool starts reporting, so the ring
 * never jumps. Inside, a page (with a second one tilted behind it) floats while the scan line passes over it,
 * lighting each line as it crosses, over a soft light that breathes behind them; a gear turns on a badge
 * centred under the page. At 100% the gear gives way to a tick that is drawn stroke by stroke while a ring of
 * light leaves the dial. One pass of the scan line (down and back) takes [PROCESSING_LOOP_MS], the shortest
 * time the tools keep this animation on screen, so even an instant task shows one whole pass. Everything is
 * drawn symmetric about the centre and inside the canvas, so a rounded or clipping parent never cuts the glow.
 * With animations off only the ring moves, and only when the real progress does.
 */
@Composable
fun ProcessingAnimation(progress: Float?, modifier: Modifier = Modifier, size: Dp = 124.dp) {
    val motion = rememberMotionEnabled()
    val clock = rememberLoopClock(motion)
    val palette = rememberPagePalette(onDark = false)
    val brand = ScanProTheme.colors
    val track = MaterialTheme.colorScheme.outlineVariant
    val halo = MaterialTheme.colorScheme.surfaceContainerHighest
    val badge = MaterialTheme.colorScheme.primary
    val onBadge = MaterialTheme.colorScheme.onPrimary
    // Eases between the real values only; 180 ms keeps the ring level with the "28 / 40" count beside it.
    val shown by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(if (motion) 180 else 0),
        label = "processingProgress"
    )
    // 0 while the length is unknown, 1 once the tool reports progress: blends the circling arc into the filling one.
    val known by animateFloatAsState(
        targetValue = if (progress != null) 1f else 0f,
        animationSpec = tween(if (motion) 450 else 0),
        label = "processingKnown"
    )
    val done by animateFloatAsState(
        targetValue = if (progress != null && progress >= 1f) 1f else 0f,
        animationSpec = tween(if (motion) PROCESSING_DONE_MS else 0),
        label = "processingDone"
    )
    val semantics = if (progress != null) Modifier.progressSemantics(progress.coerceIn(0f, 1f)) else Modifier.progressSemantics()
    Canvas(modifier.size(size).then(semantics)) {
        val t = clock.longValue
        val s = this.size.minDimension
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = s * 0.058f
        // Leaves room for the head's glow and sparks, so nothing is drawn outside the canvas.
        val radius = s / 2f - stroke * 1.85f
        val tau = 2f * PI.toFloat()

        // The dial: fine ticks around the ring instead of a flat track.
        val ticks = 48
        val segment = tau * radius / ticks
        drawCircle(
            track.copy(alpha = 0.55f),
            radius = radius,
            center = centre,
            style = Stroke(width = stroke * 0.42f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment * 0.42f, segment * 0.58f)))
        )

        // The arc: circling while the length is unknown, filling from the top once it is known.
        val spin = if (motion) cycle(t, 1_250L) else 0f
        val breathe = if (motion) 0.5f - 0.5f * cos(tau * cycle(t, 1_800L)) else 0.5f
        val start = blend(-90f + 360f * spin, -90f, known)
        val sweep = blend(50f + 170f * breathe, 360f * shown, known)
        if (sweep > 0.5f) {
            val arcTopLeft = Offset(centre.x - radius, centre.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            // A soft glow under the arc.
            drawArc(
                color = brand.gradientEnd.copy(alpha = 0.16f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke * 1.9f, cap = StrokeCap.Round)
            )
            rotate(start, centre) {
                val end = (sweep / 360f).coerceIn(0.001f, 1f)
                // Mint carries on a little past the end so the round cap at the head is mint, not indigo.
                val capEnd = (end + 0.03f).coerceAtMost(1f)
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to brand.gradientStart,
                        end * 0.6f to brand.gradientEnd,
                        end to brand.scanLine,
                        capEnd to brand.scanLine,
                        1f to brand.gradientStart,
                        center = centre
                    ),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            // The head: a pulsing glow and a few sparks drifting back along the arc behind it.
            val headDegrees = start + sweep
            val headAngle = Math.toRadians(headDegrees.toDouble())
            val head = Offset(centre.x + radius * cos(headAngle).toFloat(), centre.y + radius * sin(headAngle).toFloat())
            val pulse = if (motion) 0.5f + 0.5f * sin(tau * cycle(t, 900L)) else 0.5f
            val glowRadius = stroke * (1.25f + 0.45f * pulse)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(brand.scanLine.copy(alpha = 0.28f + 0.25f * pulse), Color.Transparent),
                    center = head,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = head
            )
            if (motion && sweep > 24f && done < 1f) {
                for (k in 0 until 4) {
                    val phase = cycle(t + k * 260L, 1_040L)
                    val angle = Math.toRadians((headDegrees - phase * 26f).toDouble())
                    val wobble = sin(phase * 7f + k * 1.9f) * stroke * 0.8f
                    val r = radius + wobble
                    drawCircle(
                        brand.scanLine.copy(alpha = (1f - phase) * 0.85f * (1f - done)),
                        radius = stroke * 0.2f * (1f - phase * 0.6f),
                        center = Offset(centre.x + r * cos(angle).toFloat(), centre.y + r * sin(angle).toFloat())
                    )
                }
            }
            drawCircle(brand.scanLine, radius = stroke * 0.42f, center = head)
        }

        // The finish: a ring of light leaves the dial and fades. It stops short of the canvas edge.
        if (done > 0f && done < 1f) {
            val room = s / 2f - radius - stroke * 0.3f
            drawCircle(
                brand.scanLine.copy(alpha = 0.6f * (1f - done)),
                radius = radius + room * smoothStep(done),
                center = centre,
                style = Stroke(width = stroke * 0.5f * (1f - done) + 1f)
            )
        }

        // A soft light behind the pages, breathing slowly, so they read as lifted off the card.
        val glow = if (motion) 0.5f - 0.5f * cos(tau * cycle(t, PROCESSING_LOOP_MS)) else 0.5f
        val glowReach = radius * 0.94f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(lerp(brand.gradientEnd, brand.scanLine, done).copy(alpha = 0.09f + 0.07f * glow + 0.1f * done), Color.Transparent),
                center = centre,
                radius = glowReach
            ),
            radius = glowReach,
            center = centre
        )

        // The pages: lifted a touch above the centre so the page and the badge under it sit centred together.
        val bob = if (motion) sin(tau * cycle(t, 2_600L)) else 0f
        val pageHeight = s * 0.4f
        val pageWidth = pageHeight * 0.76f
        val pageCentre = Offset(centre.x, centre.y - s * 0.03f + bob * s * 0.008f)
        val page = Rect(pageCentre.x - pageWidth / 2f, pageCentre.y - pageHeight / 2f, pageCentre.x + pageWidth / 2f, pageCentre.y + pageHeight / 2f)
        val (sheet, corner) = sheetPath(page)
        // A second sheet tilted behind, swaying gently: the file being worked on has more than one page.
        val sway = if (motion) sin(tau * cycle(t, 3_400L)) * 2.5f else 0f
        rotate(-9f + sway, pageCentre) {
            drawPath(sheet, palette.sheet.copy(alpha = 0.75f))
            drawPath(sheet, palette.edge, style = Stroke(width = s * 0.01f))
        }
        drawPath(sheet, palette.sheet)
        if (palette.edge.alpha > 0f) drawPath(sheet, palette.edge, style = Stroke(width = s * 0.012f))
        drawPath(corner, palette.fold)
        val p = if (motion) cycle(t, PROCESSING_LOOP_MS) else 0.25f
        val down = p < 0.5f
        val travel = smoothStep(if (down) p * 2f else (1f - p) * 2f)
        val beamY = page.top + page.height * (0.12f + 0.62f * travel)
        val left = page.left + page.width * 0.17f
        val maxWidth = page.width * 0.66f
        PAGE_LINES.forEachIndexed { index, width ->
            val y = lineY(page, index)
            // Each line lights up in the brand colour while the scan line crosses it.
            val lit = (1f - abs(beamY - y) / (page.height * 0.2f)).coerceIn(0f, 1f)
            drawLine(
                color = lerp(if (index == 0) palette.inkStrong.copy(alpha = 0.55f) else palette.ink, palette.inkStrong, lit),
                start = Offset(left, y),
                end = Offset(left + maxWidth * width, y),
                strokeWidth = page.width * if (index == 0) 0.075f else 0.055f,
                cap = StrokeCap.Round
            )
        }
        // Stops above the badge and barely overhangs the page, so it never shows as a stray bar beside it.
        drawScanBeam(page, sheet, beamY, palette.beam, 1f - done, movingDown = down, overhangFraction = 0.05f)

        // The badge, centred under the page: a turning gear that becomes a tick at 100%.
        val badgeRadius = s * 0.085f
        val badgeCentre = Offset(page.center.x, page.bottom + s * 0.025f)
        val pop = 1f + 0.12f * sin(PI.toFloat() * done)
        drawCircle(halo, radius = badgeRadius * 1.2f * pop, center = badgeCentre)
        drawCircle(lerp(badge, brand.scanLine, done), radius = badgeRadius * pop, center = badgeCentre)
        val turn = if (motion) 360f * cycle(t, 3_000L) else 0f
        translate(badgeCentre.x, badgeCentre.y) {
            if (done < 1f) {
                rotate(turn, Offset.Zero) {
                    val g = badgeRadius * 0.68f
                    scale(g, g, Offset.Zero) { drawPath(GEAR, onBadge.copy(alpha = 1f - done)) }
                }
                drawCircle(badge.copy(alpha = 1f - done), radius = badgeRadius * 0.24f, center = Offset.Zero)
            }
            if (done > 0f) {
                val r = badgeRadius
                // Drawn as a pen would: the short stroke down first, then the long one up.
                val from = Offset(-0.42f * r, 0.02f * r)
                val corner = Offset(-0.12f * r, 0.32f * r)
                val to = Offset(0.44f * r, -0.28f * r)
                val firstStroke = (done / TICK_FIRST_STROKE).coerceIn(0f, 1f)
                val secondStroke = ((done - TICK_FIRST_STROKE) / (1f - TICK_FIRST_STROKE)).coerceIn(0f, 1f)
                val tick = Path().apply {
                    moveTo(from.x, from.y)
                    lineTo(blend(from.x, corner.x, firstStroke), blend(from.y, corner.y, firstStroke))
                    if (secondStroke > 0f) lineTo(blend(corner.x, to.x, secondStroke), blend(corner.y, to.y, secondStroke))
                }
                drawPath(
                    tick,
                    MintInk.copy(alpha = (done * 3f).coerceAtMost(1f)),
                    style = Stroke(width = r * 0.22f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }
        }
    }
}

/** Dark green ink for marks drawn on the mint scan-line colour. */
private val MintInk = Color(0xFF04281E)

/** One pass of the scan line over the page, down and back; also the shortest time a tool shows [ProcessingAnimation]. */
const val PROCESSING_LOOP_MS = 2_000L

/** How long the finish (gear to tick, ring of light) takes once the progress reaches 100%. */
const val PROCESSING_DONE_MS = 450

/** The share of the finish spent on the tick's short first stroke. */
private const val TICK_FIRST_STROKE = 0.4f

private fun blend(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/**
 * A soft band of light sweeping across a placeholder while the picture behind it loads. Every shimmer on
 * screen runs on the same clock, so a grid of loading thumbnails sweeps together rather than flickering.
 */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    /** The colour of the band; by default a soft white that suits the theme's surfaces. */
    highlight: Color = Color.Unspecified
) {
    val motion = rememberMotionEnabled()
    val clock = rememberLoopClock(motion)
    val light = MaterialTheme.colorScheme.surface.luminance() >= 0.4f
    val band = when {
        highlight != Color.Unspecified -> highlight
        light -> Color.White.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.09f)
    }
    Canvas(modifier) {
        if (!motion) return@Canvas
        // Sweep for 1.2 s, rest for 0.3 s.
        val p = (cycle(clock.longValue, 1_500L) / 0.8f).coerceAtMost(1f)
        val width = size.width * 0.6f
        val x = -width + (size.width + width * 2f) * smoothStep(p)
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, band, Color.Transparent),
                start = Offset(x - width, 0f),
                end = Offset(x, size.height * 0.35f)
            )
        )
    }
}

/**
 * The strip under an app bar while a document task runs: a band in the brand colours with a mint head,
 * sweeping across like the scan line. Fills the space it is given (normally 4 dp high).
 */
@Composable
fun ScanBeamProgressBar(modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val clock = rememberLoopClock(motion)
    val brand = ScanProTheme.colors
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    Canvas(modifier.progressSemantics()) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        if (!motion) {
            drawRoundRect(brand.gradientStart.copy(alpha = 0.5f), cornerRadius = radius)
            return@Canvas
        }
        val band = size.width * 0.42f
        val head = -band * 0.2f + (size.width + band * 1.2f) * smoothStep(cycle(clock.longValue, 1_400L))
        clipPathRoundRect(radius) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to brand.gradientStart,
                    0.85f to brand.gradientEnd,
                    1f to brand.scanLine,
                    startX = head - band,
                    endX = head
                ),
                topLeft = Offset(head - band, 0f),
                size = Size(band, size.height)
            )
        }
    }
}

private inline fun DrawScope.clipPathRoundRect(radius: CornerRadius, block: DrawScope.() -> Unit) {
    val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), radius)) }
    clipPath(path) { block() }
}

/**
 * Three dots rising one after another: the busy state inside a button or a text field, where a page
 * would be too small to read. [dotSize] is the diameter of one dot.
 */
@Composable
fun ProcessingDots(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 6.dp) {
    val motion = rememberMotionEnabled()
    val clock = rememberLoopClock(motion)
    Canvas(modifier.size(width = dotSize * 4.4f, height = dotSize * 2.2f).progressSemantics()) {
        val d = dotSize.toPx()
        val gap = d * 0.7f
        val baseline = size.height - d / 2f - d * 0.1f
        for (i in 0 until 3) {
            val phase = if (motion) cycle(clock.longValue + 1_000L - i * 160L, 1_000L) else 0f
            // A short rise and fall in the first half of the cycle, then a pause.
            val rise = if (phase < 0.5f) sin(phase * 2f * PI.toFloat()) else 0f
            drawCircle(
                color = color.copy(alpha = color.alpha * (0.45f + 0.55f * rise.coerceAtLeast(if (motion) 0f else 1f))),
                radius = d / 2f,
                center = Offset(d / 2f + i * (d + gap), baseline - rise * d * 0.9f)
            )
        }
    }
}

@Preview(name = "Loading and processing animations", widthDp = 360, heightDp = 300)
@Composable
private fun LoadingAnimationsPreview() = LoadingAnimationsSample(com.example.scanpro.data.ThemeMode.LIGHT)

@Preview(name = "Loading and processing animations, dark", widthDp = 360, heightDp = 300)
@Composable
private fun LoadingAnimationsDarkPreview() = LoadingAnimationsSample(com.example.scanpro.data.ThemeMode.DARK)

@Composable
private fun LoadingAnimationsSample(mode: com.example.scanpro.data.ThemeMode) {
    ScanProTheme(themeMode = mode) {
        androidx.compose.material3.Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileLoadingState(label = "Opening PDF…")
                    ProcessingAnimation(progress = 0.34f)
                    ProcessingAnimation(progress = null, size = 72.dp)
                }
                ScanBeamProgressBar(Modifier.size(width = 300.dp, height = 4.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProcessingDots(MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) { LoadingShimmer(Modifier.matchParentSize()) }
                }
            }
        }
    }
}
