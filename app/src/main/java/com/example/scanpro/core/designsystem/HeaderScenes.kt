package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The little scenes that play beside the ScanPro name on Home. [millis] is how long one pass takes. */
enum class HeaderScene(val millis: Int) {
    /** A monkey comes down on a vine, swings while holding a scanned page, and goes back up. */
    MONKEY(6500),
    /** A monkey sits on the road and tosses a scanned page up and catches it. */
    MONKEY_PLAY(6200),
    /** Three birds fly across, flapping. */
    BIRDS(5200),
    /** A red racing car at speed, leaving tyre marks and smoke. */
    RACE_CAR(2600),
    BIKE(3800),
    /** An auto-rickshaw: green body, yellow hood. */
    AUTO(4800),
    /** A painted lorry of the kind seen on Punjab's highways, crown over the cab and tassels swinging. */
    LORRY(6000),
    /** A white and blue semi-high-speed train with a long sloped nose. */
    TRAIN(3600),
    /** A zombie shuffling slowly across, arms out. */
    ZOMBIE(11000),
    /** A small propeller plane towing a banner with the app's name. */
    PLANE(6500),
    /** A rocket that sits smoking on its pad, then lifts off out of the top. */
    ROCKET(5200),
    /** An elephant in a red and gold cloth, walking across. */
    ELEPHANT(9500),
    /** A paper plane folded from a scanned page, gliding up and down. */
    PAPER_PLANE(4800)
}

/** The empty pause between two scenes. */
private const val REST_MILLIS = 1600

/**
 * A small decoration for Home's top bar (owner's request, 2026-11-19): one scene at random, a pause, then another, a
 * different one each time; a tap moves on to the next. Behind them a little sky for the time of day ([hour], 0 to 23):
 * a sun and clouds, or a moon and stars. Drawn with shapes only, nothing downloaded. Time is read in the draw phase, so
 * a frame only redraws this canvas. It is not read out. With animations turned off in the phone's settings only the
 * still sky is drawn.
 *
 * [still] draws one scene at a fixed point of its pass (0 to 1), for screenshots.
 */
@Composable
fun HeaderSceneStrip(
    modifier: Modifier = Modifier,
    still: Pair<HeaderScene, Float>? = null,
    hour: Int = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
) {
    val motion = rememberMotionEnabled()
    val inspection = LocalInspectionMode.current
    val animated = still == null && motion && !inspection
    val scene = remember { mutableStateOf(still?.first ?: HeaderScene.entries.random()) }
    // Below zero: resting, only the sky is drawn.
    val progress = remember { mutableFloatStateOf(still?.second ?: -1f) }
    val mirrored = remember { mutableStateOf(still == null && Random.nextBoolean()) }
    // Milliseconds since the strip appeared, for the sky, which never rests.
    val clock = remember { mutableLongStateOf(0L) }
    val skip = remember { mutableStateOf(false) }
    // A new number each pass, so a scene varies a little each time it comes round (how many birds, their heights).
    val seed = remember { mutableIntStateOf(Random.nextInt()) }
    // Every scene once, in a shuffled order, before any comes round again.
    val bag = remember { ArrayDeque<HeaderScene>() }
    if (animated) {
        LaunchedEffect(Unit) {
            var first = -1L
            var start = -1L
            while (true) withInfiniteAnimationFrameMillis { now ->
                if (first < 0) first = now
                if (start < 0) start = now
                clock.longValue = now - first
                val elapsed = now - start
                val length = scene.value.millis
                when {
                    skip.value || elapsed > length + REST_MILLIS -> {
                        skip.value = false
                        if (bag.isEmpty()) bag.addAll((HeaderScene.entries - scene.value).shuffled())
                        scene.value = bag.removeFirst()
                        mirrored.value = Random.nextBoolean()
                        seed.intValue = Random.nextInt()
                        start = now
                        progress.floatValue = 0f
                    }
                    elapsed <= length -> progress.floatValue = elapsed / length.toFloat()
                    else -> progress.floatValue = -1f
                }
            }
        }
    }
    val palette = ScenePalette(
        ink = MaterialTheme.colorScheme.onSurface,
        soft = MaterialTheme.colorScheme.onSurfaceVariant,
        brand = MaterialTheme.colorScheme.primary,
        paper = MaterialTheme.colorScheme.surfaceContainerLowest,
        night = hour !in 6..17
    )
    // The banner the plane tows carries the app's name.
    val measurer = rememberTextMeasurer()
    val name = stringResource(R.string.core_recommend_app_subject)
    val banner = remember(measurer, name) {
        measurer.measure(name, TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .clipToBounds()
            // A tap moves on to another scene. No ripple and nothing announced: it is a toy, not a control.
            .clickable(interactionSource = interaction, indication = null, enabled = animated) { skip.value = true }
            .clearAndSetSemantics { }
            // Drawn into a layer of its own, so the edges can be faded out below.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        if (size.width < size.height) return@Canvas
        sky(hour, clock.longValue, palette)
        val p = progress.floatValue
        if (p >= 0f) scene(scene.value, p, mirrored.value, seed.intValue, palette, banner)
        // Soft left and right edges: things slide in and out of view instead of being cut off beside the name.
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.93f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }
}

/** One frame of [current], [p] of the way through its pass. */
private fun DrawScope.scene(current: HeaderScene, p: Float, mirrored: Boolean, seed: Int, palette: ScenePalette, banner: TextLayoutResult) {
        val u = size.height
        when (current) {
            HeaderScene.MONKEY -> monkey(p, palette, left = mirrored)
            HeaderScene.MONKEY_PLAY -> monkeyPlay(p, palette, left = mirrored)
            HeaderScene.BIRDS -> birds(p, palette, seed)
            HeaderScene.PLANE -> plane(p, palette, banner)
            HeaderScene.ROCKET -> rocket(p, palette, left = mirrored)
            HeaderScene.PAPER_PLANE -> scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f) { paperPlane(p, palette) }
            else -> {
                // A faint road (or rail), so the wheels have something to run on.
                drawLine(palette.soft.copy(alpha = 0.18f), Offset(0f, GROUND * u), Offset(size.width, GROUND * u), strokeWidth = 0.015f * u)
                val length = when (current) {
                    HeaderScene.TRAIN -> 4.2f * u
                    HeaderScene.LORRY -> 1.9f * u
                    HeaderScene.ZOMBIE -> 0.6f * u
                    else -> 1.4f * u
                }
                // The racing car pulls away, faster and faster; everything else keeps a steady pace.
                val travel = if (current == HeaderScene.RACE_CAR) p * (0.45f + 0.55f * p) else p
                val x = -length + (size.width + 2 * length) * travel
                // Drawn facing right; half the time it comes from the other side.
                scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f) {
                    when (current) {
                        HeaderScene.RACE_CAR -> raceCar(x, p, palette)
                        HeaderScene.BIKE -> bike(x, p, palette)
                        HeaderScene.LORRY -> lorry(x, p, palette)
                        HeaderScene.TRAIN -> train(x, palette)
                        HeaderScene.ZOMBIE -> zombie(x, p, palette)
                        HeaderScene.ELEPHANT -> elephant(x, p)
                        else -> auto(x, p, palette)
                    }
                }
            }
        }
}

/** The theme's colours, and whether it is night: headlamps throw their beams only after dark. */
private class ScenePalette(val ink: Color, val soft: Color, val brand: Color, val paper: Color, val night: Boolean)

/** Where the road is, as a part of the strip's height. */
private const val GROUND = 0.95f

private val Tyre = Color(0xFF1B1C20)
private val Steel = Color(0xFF9AA3AD)
private val Chrome = Color(0xFFD9DEE3)
private val DarkMetal = Color(0xFF3A3F47)
private val Glass = Color(0xFFBFE0FF)
private val GlassDeep = Color(0xFF6FA6DA)
private val Lamp = Color(0xFFFFE08A)
private val TailRed = Color(0xFFD9342B)
private val Fur = Color(0xFF8A5A3C)
private val FurDark = Color(0xFF5E3B26)
private val FurLight = Color(0xFFB07A55)
private val Face = Color(0xFFF1D0B0)
private val Vine = Color(0xFF3F8F4A)
private val VineDark = Color(0xFF2C6B36)
private val ScanGreen = Color(0xFF2ECC8F)
private val AutoYellow = Color(0xFFF7C514)
private val AutoGreen = Color(0xFF237A3B)
private val BikeRed = Color(0xFFD63C2F)
private val Skin = Color(0xFFC68A5E)
private val Denim = Color(0xFF2F4A6B)

private fun Color.lighter(by: Float) = lerp(this, Color.White, by)
private fun Color.darker(by: Float) = lerp(this, Color.Black, by)

/** The soft shadow a vehicle throws on the road. */
private fun DrawScope.shadow(left: Float, width: Float) {
    val u = size.height
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(left, (GROUND - 0.025f) * u), Size(width, 0.06f * u))
}

/** The light a headlamp throws ahead of a vehicle facing right. */
private fun DrawScope.beam(from: Offset, reach: Float, spread: Float) {
    drawPath(
        Path().apply {
            moveTo(from.x, from.y)
            lineTo(from.x + reach, from.y - spread * 0.35f)
            lineTo(from.x + reach, from.y + spread)
            close()
        },
        Brush.horizontalGradient(listOf(Lamp.copy(alpha = 0.45f), Lamp.copy(alpha = 0f)), startX = from.x, endX = from.x + reach)
    )
}

/** A tyre on an alloy rim; the spokes turn as far as the vehicle has travelled. */
private fun DrawScope.wheel(centre: Offset, radius: Float, travelled: Float, spokes: Int = 5) {
    drawCircle(Tyre, radius, centre)
    drawCircle(Color.White.copy(alpha = 0.10f), radius * 0.9f, centre, style = Stroke(width = radius * 0.06f))
    val rim = radius * 0.66f
    drawCircle(Brush.radialGradient(listOf(Chrome, Steel), center = centre - Offset(rim * 0.3f, rim * 0.3f), radius = rim * 1.6f), rim, centre)
    val turn = travelled / radius
    for (i in 0 until spokes) {
        val angle = turn + i * (2f * PI.toFloat() / spokes)
        drawLine(DarkMetal, centre, centre + Offset(cos(angle), sin(angle)) * (rim * 0.92f), strokeWidth = radius * 0.13f, cap = StrokeCap.Round)
    }
    drawCircle(DarkMetal, radius * 0.2f, centre)
    drawCircle(Chrome, radius * 0.09f, centre)
}

/** Grey smoke rolling back from [from]: puffs that grow, rise and fade as they age. */
private fun DrawScope.smoke(from: Offset, p: Float, reach: Float, puffs: Int = 6, strength: Float = 0.4f) {
    val u = size.height
    for (i in 0 until puffs) {
        val age = (p * 14f + i / puffs.toFloat()) % 1f
        drawCircle(
            Color(0xFF9AA0A8).copy(alpha = (1f - age) * strength),
            (0.035f + age * 0.1f) * u,
            from + Offset(-age * reach, -age * 0.2f * u - sin(i * 2.1f) * 0.03f * u)
        )
    }
}

/** A low red racing car at full speed, facing right: tyre marks on the road behind it and smoke off the rear wheel. */
private fun DrawScope.raceCar(x: Float, p: Float, c: ScenePalette) {
    val u = size.height
    val length = 1.4f * u
    val ground = GROUND * u
    val bounce = sin(p * 60f) * 0.004f * u
    fun at(along: Float, up: Float) = Offset(x + along * length, ground - up * u + bounce)
    val r = 0.1f * u
    val rearAxle = Offset(x + 0.2f * length, ground - r)
    val frontAxle = Offset(x + 0.8f * length, ground - r)
    // Two dark tyre marks, fading the further back they were laid.
    val marks = Brush.horizontalGradient(listOf(Color.Transparent, Tyre.copy(alpha = 0.55f)), startX = rearAxle.x - 1.6f * u, endX = rearAxle.x)
    drawLine(marks, Offset(rearAxle.x - 1.6f * u, ground - 0.012f * u), Offset(rearAxle.x, ground - 0.012f * u), strokeWidth = 0.022f * u)
    drawLine(marks, Offset(rearAxle.x - 1.45f * u, ground + 0.018f * u), Offset(rearAxle.x, ground + 0.018f * u), strokeWidth = 0.018f * u)
    smoke(Offset(rearAxle.x - r, ground - 0.05f * u), p, reach = 0.75f * u)
    // Streaks of air over the roof.
    for (i in 0..2) {
        val y = ground - (0.3f + i * 0.09f) * u
        val start = x - (0.25f + ((p * 9f + i * 0.37f) % 1f) * 0.5f) * u
        drawLine(c.soft.copy(alpha = 0.35f), Offset(start, y), Offset(start + 0.22f * u, y), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
    }
    shadow(x - 0.02f * length, 1.04f * length)

    val red = Color(0xFFD9161B)
    // Rear wing on two struts.
    drawLine(DarkMetal, at(0.04f, 0.26f), at(0.03f, 0.38f), strokeWidth = 0.02f * u)
    drawLine(DarkMetal, at(0.1f, 0.27f), at(0.09f, 0.38f), strokeWidth = 0.02f * u)
    drawRoundRect(Tyre, at(-0.02f, 0.41f), Size(0.17f * length, 0.035f * u), CornerRadius(0.015f * u))
    val shell = Path().apply {
        fun to(o: Offset) = lineTo(o.x, o.y)
        moveTo(at(0.01f, 0.1f).x, at(0.01f, 0.1f).y)
        to(at(0f, 0.22f))
        quadraticTo(at(0f, 0.28f).x, at(0f, 0.28f).y, at(0.08f, 0.28f).x, at(0.08f, 0.28f).y)
        cubicTo(at(0.24f, 0.3f).x, at(0.24f, 0.3f).y, at(0.3f, 0.41f).x, at(0.3f, 0.41f).y, at(0.42f, 0.41f).x, at(0.42f, 0.41f).y)
        cubicTo(at(0.54f, 0.41f).x, at(0.54f, 0.41f).y, at(0.58f, 0.33f).x, at(0.58f, 0.33f).y, at(0.66f, 0.29f).x, at(0.66f, 0.29f).y)
        cubicTo(at(0.82f, 0.25f).x, at(0.82f, 0.25f).y, at(0.95f, 0.2f).x, at(0.95f, 0.2f).y, at(1f, 0.14f).x, at(1f, 0.14f).y)
        to(at(1f, 0.1f))
        close()
    }
    drawPath(shell, Brush.verticalGradient(listOf(red.lighter(0.35f), red, red.darker(0.4f)), startY = at(0f, 0.41f).y, endY = at(0f, 0.1f).y))
    // Dark tinted cockpit glass with a streak of sky on it.
    val canopy = Path().apply {
        moveTo(at(0.3f, 0.3f).x, at(0.3f, 0.3f).y)
        cubicTo(at(0.33f, 0.37f).x, at(0.33f, 0.37f).y, at(0.37f, 0.385f).x, at(0.37f, 0.385f).y, at(0.43f, 0.385f).x, at(0.43f, 0.385f).y)
        cubicTo(at(0.52f, 0.385f).x, at(0.52f, 0.385f).y, at(0.56f, 0.33f).x, at(0.56f, 0.33f).y, at(0.62f, 0.295f).x, at(0.62f, 0.295f).y)
        close()
    }
    drawPath(canopy, Brush.verticalGradient(listOf(Color(0xFF5B6B80), Color(0xFF151A22)), startY = at(0f, 0.385f).y, endY = at(0f, 0.29f).y))
    drawLine(Color.White.copy(alpha = 0.6f), at(0.36f, 0.36f), at(0.44f, 0.37f), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
    drawLine(red.darker(0.3f), at(0.455f, 0.385f), at(0.47f, 0.3f), strokeWidth = 0.012f * u)
    // The light along the shoulder, a side intake ahead of the rear wheel, a door line and a white sill stripe.
    drawLine(Color.White.copy(alpha = 0.45f), at(0.1f, 0.265f), at(0.9f, 0.2f), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(at(0.29f, 0.24f).x, at(0.29f, 0.24f).y); lineTo(at(0.4f, 0.22f).x, at(0.4f, 0.22f).y)
            lineTo(at(0.34f, 0.15f).x, at(0.34f, 0.15f).y); lineTo(at(0.29f, 0.15f).x, at(0.29f, 0.15f).y); close()
        },
        Tyre.copy(alpha = 0.85f)
    )
    drawLine(red.darker(0.45f), at(0.47f, 0.28f), at(0.5f, 0.13f), strokeWidth = 0.008f * u)
    drawLine(Color.White.copy(alpha = 0.9f), at(0.3f, 0.125f), at(0.7f, 0.125f), strokeWidth = 0.014f * u)
    drawRect(Tyre, at(0.01f, 0.115f), Size(0.985f * length, 0.02f * u))
    // Lamps: a thin headlamp swept back along the wing, round tail lamps.
    drawPath(
        Path().apply {
            moveTo(at(0.9f, 0.2f).x, at(0.9f, 0.2f).y); lineTo(at(0.975f, 0.165f).x, at(0.975f, 0.165f).y)
            lineTo(at(0.93f, 0.17f).x, at(0.93f, 0.17f).y); close()
        },
        Lamp
    )
    drawCircle(TailRed.lighter(0.2f), 0.022f * u, at(0.015f, 0.22f))
    drawCircle(Lamp, 0.014f * u, at(0.74f, 0.22f))
    drawCircle(Tyre.copy(alpha = 0.9f), r * 1.2f, rearAxle + Offset(0f, bounce))
    drawCircle(Tyre.copy(alpha = 0.9f), r * 1.2f, frontAxle + Offset(0f, bounce))
    wheel(rearAxle, r, x * 3f)
    wheel(frontAxle, r, x * 3f)
}

/** A motorcycle and its rider, facing right. */
private fun DrawScope.bike(x: Float, p: Float, c: ScenePalette) {
    val u = size.height
    val ground = GROUND * u
    val r = 0.125f * u
    val bounce = sin(p * 52f) * 0.007f * u
    fun at(along: Float, up: Float) = Offset(x + (along + 0.18f) * u, ground - up * u + bounce)
    val rear = Offset(at(0.14f, 0f).x, ground - r)
    val front = Offset(at(0.8f, 0f).x, ground - r)
    shadow(rear.x - r, front.x - rear.x + 2 * r)
    if (c.night) beam(at(0.78f, 0.47f), 0.45f * u, 0.22f * u)

    // Exhaust, swing arm and frame behind everything else.
    drawLine(Brush.horizontalGradient(listOf(Steel, Chrome)), at(0.02f, 0.2f), at(0.42f, 0.14f), strokeWidth = 0.05f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, Offset(rear.x, rear.y + bounce), at(0.42f, 0.16f), strokeWidth = 0.035f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, at(0.36f, 0.36f), at(0.42f, 0.14f), strokeWidth = 0.03f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, at(0.62f, 0.4f), at(0.52f, 0.14f), strokeWidth = 0.03f * u, cap = StrokeCap.Round)
    // Engine with its cooling fins.
    drawRoundRect(Brush.verticalGradient(listOf(Steel, DarkMetal)), at(0.38f, 0.3f), Size(0.17f * u, 0.2f * u), CornerRadius(0.03f * u))
    for (i in 0..3) drawLine(Tyre.copy(alpha = 0.5f), at(0.39f, 0.27f - i * 0.035f), at(0.54f, 0.27f - i * 0.035f), strokeWidth = 0.008f * u)
    // Fork and handlebar; the front wheel sits ahead of the headstock.
    drawLine(Chrome, Offset(front.x, front.y + bounce), at(0.69f, 0.5f), strokeWidth = 0.035f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, at(0.66f, 0.54f), at(0.72f, 0.55f), strokeWidth = 0.03f * u, cap = StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(Color.White, Lamp), center = at(0.76f, 0.47f), radius = 0.05f * u), 0.04f * u, at(0.75f, 0.46f))
    // Mudguards.
    drawArc(BikeRed, 200f, 110f, false, Offset(front.x - r * 1.18f, front.y - r * 1.18f + bounce), Size(r * 2.36f, r * 2.36f), style = Stroke(0.03f * u, cap = StrokeCap.Round))
    drawArc(BikeRed, 215f, 100f, false, Offset(rear.x - r * 1.18f, rear.y - r * 1.18f + bounce), Size(r * 2.36f, r * 2.36f), style = Stroke(0.03f * u, cap = StrokeCap.Round))
    // Tank, seat and tail.
    val tank = Path().apply {
        moveTo(at(0.36f, 0.36f).x, at(0.36f, 0.36f).y)
        cubicTo(at(0.38f, 0.5f).x, at(0.38f, 0.5f).y, at(0.6f, 0.52f).x, at(0.6f, 0.52f).y, at(0.66f, 0.42f).x, at(0.66f, 0.42f).y)
        quadraticTo(at(0.6f, 0.34f).x, at(0.6f, 0.34f).y, at(0.36f, 0.36f).x, at(0.36f, 0.36f).y)
        close()
    }
    drawPath(tank, Brush.verticalGradient(listOf(BikeRed.lighter(0.3f), BikeRed, BikeRed.darker(0.3f)), startY = at(0f, 0.52f).y, endY = at(0f, 0.34f).y))
    drawLine(Color.White.copy(alpha = 0.5f), at(0.43f, 0.46f), at(0.56f, 0.47f), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
    drawRoundRect(Tyre, at(0.1f, 0.42f), Size(0.28f * u, 0.065f * u), CornerRadius(0.03f * u))
    drawRoundRect(TailRed, at(0.07f, 0.4f), Size(0.035f * u, 0.035f * u), CornerRadius(0.01f * u))
    wheel(rear, r, x, spokes = 7)
    wheel(front, r, x, spokes = 7)

    // Rider: leg bent to the footrest, jacket leaning to the bars, gloved hand, helmet with a visor.
    val hip = at(0.26f, 0.46f)
    val knee = at(0.46f, 0.36f)
    val foot = at(0.4f, 0.12f)
    drawLine(Denim, hip, knee, strokeWidth = 0.085f * u, cap = StrokeCap.Round)
    drawLine(Denim, knee, foot, strokeWidth = 0.07f * u, cap = StrokeCap.Round)
    drawRoundRect(Tyre, foot - Offset(0.03f * u, 0.02f * u), Size(0.11f * u, 0.05f * u), CornerRadius(0.02f * u))
    val shoulder = at(0.42f, 0.68f)
    val jacket = c.brand
    drawLine(Brush.linearGradient(listOf(jacket.darker(0.25f), jacket.lighter(0.15f)), start = hip, end = shoulder), hip + Offset(0.01f * u, -0.03f * u), shoulder, strokeWidth = 0.13f * u, cap = StrokeCap.Round)
    val elbow = at(0.56f, 0.57f)
    val hand = at(0.67f, 0.55f)
    drawLine(jacket.darker(0.12f), shoulder + Offset(0.02f * u, 0.03f * u), elbow, strokeWidth = 0.06f * u, cap = StrokeCap.Round)
    drawLine(jacket.darker(0.12f), elbow, hand, strokeWidth = 0.055f * u, cap = StrokeCap.Round)
    drawCircle(Tyre, 0.03f * u, hand)
    val helmet = at(0.47f, 0.8f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5A6070), Tyre), center = helmet - Offset(0.03f * u, 0.03f * u), radius = 0.12f * u), 0.088f * u, helmet)
    drawArc(Brush.linearGradient(listOf(Glass, GlassDeep)), -50f, 95f, true, helmet - Offset(0.075f * u, 0.07f * u), Size(0.15f * u, 0.14f * u))
    drawLine(Color.White.copy(alpha = 0.6f), helmet + Offset(-0.05f * u, -0.05f * u), helmet + Offset(-0.01f * u, -0.07f * u), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
}

/** An auto-rickshaw as on Indian roads: green body, yellow hood, one front wheel, the driver at the handlebar. */
private fun DrawScope.auto(x: Float, p: Float, c: ScenePalette) {
    val u = size.height
    val length = 1.02f * u
    val ground = GROUND * u
    val bounce = sin(p * 40f) * 0.008f * u
    fun at(along: Float, up: Float) = Offset(x + 0.14f * u + along * length, ground - up * u + bounce)
    val r = 0.09f * u
    val rear = Offset(at(0.2f, 0f).x, ground - r)
    val front = Offset(at(0.9f, 0f).x, ground - r)
    shadow(at(-0.03f, 0f).x, 1.06f * length)
    if (c.night) beam(at(0.97f, 0.36f), 0.42f * u, 0.2f * u)

    // Inside, seen through the open side: the passenger bench and the driver.
    drawRoundRect(Color(0xFF7A2E2E), at(0.07f, 0.56f), Size(0.2f * length, 0.16f * u), CornerRadius(0.03f * u))
    val seat = at(0.56f, 0.44f)
    val neck = at(0.6f, 0.62f)
    drawLine(Color(0xFFB9A07A), seat, neck, strokeWidth = 0.11f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFFB9A07A), neck + Offset(0f, 0.03f * u), at(0.74f, 0.5f), strokeWidth = 0.045f * u, cap = StrokeCap.Round)
    drawCircle(Skin, 0.055f * u, at(0.62f, 0.7f))
    drawArc(Tyre, 180f, 200f, true, at(0.62f, 0.7f) - Offset(0.058f * u, 0.062f * u), Size(0.116f * u, 0.1f * u))
    drawLine(DarkMetal, at(0.74f, 0.5f), at(0.8f, 0.4f), strokeWidth = 0.025f * u, cap = StrokeCap.Round)

    // Body: the tub, rising at the front into the nose over the single wheel.
    val tub = Path().apply {
        moveTo(at(0f, 0.13f).x, at(0f, 0.13f).y)
        lineTo(at(0f, 0.4f).x, at(0f, 0.4f).y)
        quadraticTo(at(0f, 0.44f).x, at(0f, 0.44f).y, at(0.04f, 0.44f).x, at(0.04f, 0.44f).y)
        lineTo(at(0.5f, 0.44f).x, at(0.5f, 0.44f).y)
        lineTo(at(0.52f, 0.3f).x, at(0.52f, 0.3f).y)
        lineTo(at(0.76f, 0.3f).x, at(0.76f, 0.3f).y)
        lineTo(at(0.8f, 0.44f).x, at(0.8f, 0.44f).y)
        cubicTo(at(0.93f, 0.44f).x, at(0.93f, 0.44f).y, at(1f, 0.36f).x, at(1f, 0.36f).y, at(1f, 0.24f).x, at(1f, 0.24f).y)
        lineTo(at(0.98f, 0.13f).x, at(0.98f, 0.13f).y)
        close()
    }
    drawPath(tub, Brush.verticalGradient(listOf(AutoGreen.lighter(0.22f), AutoGreen, AutoGreen.darker(0.35f)), startY = at(0f, 0.44f).y, endY = at(0f, 0.13f).y))
    drawLine(AutoYellow, at(0.01f, 0.3f), at(0.5f, 0.3f), strokeWidth = 0.022f * u)
    drawLine(Color.White.copy(alpha = 0.3f), at(0.05f, 0.415f), at(0.48f, 0.415f), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
    drawRoundRect(Color.White, at(0.02f, 0.25f), Size(0.09f * length, 0.05f * u), CornerRadius(0.008f * u))
    // Hood: over the back, across the top, down to the windscreen.
    val hood = Path().apply {
        moveTo(at(-0.01f, 0.44f).x, at(-0.01f, 0.44f).y)
        cubicTo(at(-0.03f, 0.74f).x, at(-0.03f, 0.74f).y, at(0.04f, 0.86f).x, at(0.04f, 0.86f).y, at(0.2f, 0.87f).x, at(0.2f, 0.87f).y)
        lineTo(at(0.62f, 0.87f).x, at(0.62f, 0.87f).y)
        quadraticTo(at(0.76f, 0.87f).x, at(0.76f, 0.87f).y, at(0.8f, 0.78f).x, at(0.8f, 0.78f).y)
        lineTo(at(0.74f, 0.78f).x, at(0.74f, 0.78f).y)
        quadraticTo(at(0.72f, 0.81f).x, at(0.72f, 0.81f).y, at(0.62f, 0.81f).x, at(0.62f, 0.81f).y)
        lineTo(at(0.3f, 0.81f).x, at(0.3f, 0.81f).y)
        cubicTo(at(0.3f, 0.6f).x, at(0.3f, 0.6f).y, at(0.3f, 0.5f).x, at(0.3f, 0.5f).y, at(0.3f, 0.44f).x, at(0.3f, 0.44f).y)
        close()
    }
    drawPath(hood, Brush.verticalGradient(listOf(AutoYellow.lighter(0.3f), AutoYellow, AutoYellow.darker(0.22f)), startY = at(0f, 0.87f).y, endY = at(0f, 0.44f).y))
    // The little rear window in the hood, and the creases of the canvas.
    drawRoundRect(Tyre.copy(alpha = 0.55f), at(0.06f, 0.72f), Size(0.14f * length, 0.1f * u), CornerRadius(0.02f * u))
    drawLine(AutoYellow.darker(0.3f), at(0.24f, 0.84f), at(0.24f, 0.46f), strokeWidth = 0.008f * u)
    // Windscreen: glass in a dark frame, leaning back.
    val screen = Path().apply {
        moveTo(at(0.75f, 0.78f).x, at(0.75f, 0.78f).y)
        lineTo(at(0.8f, 0.78f).x, at(0.8f, 0.78f).y)
        lineTo(at(0.9f, 0.44f).x, at(0.9f, 0.44f).y)
        lineTo(at(0.84f, 0.44f).x, at(0.84f, 0.44f).y)
        close()
    }
    drawPath(screen, Brush.verticalGradient(listOf(Glass, GlassDeep), startY = at(0f, 0.78f).y, endY = at(0f, 0.44f).y), alpha = 0.9f)
    drawPath(screen, DarkMetal, style = Stroke(width = 0.012f * u))
    // Headlamp, front mudguard and wheels.
    drawCircle(Brush.radialGradient(listOf(Color.White, Lamp), center = at(0.97f, 0.36f), radius = 0.05f * u), 0.035f * u, at(0.965f, 0.35f))
    drawArc(AutoYellow, 190f, 140f, false, Offset(front.x - r * 1.2f, front.y - r * 1.2f + bounce), Size(r * 2.4f, r * 2.4f), style = Stroke(0.03f * u, cap = StrokeCap.Round))
    drawCircle(Tyre.copy(alpha = 0.9f), r * 1.2f, rear + Offset(0f, bounce))
    wheel(rear, r, x, spokes = 4)
    wheel(front, r, x, spokes = 4)
}

/** Three birds crossing from right to left: body, head, beak, tail and a wing that beats. */
private fun DrawScope.birds(p: Float, c: ScenePalette, seed: Int = 0) {
    val u = size.height
    // Two to five birds, at heights and sizes that change each time the scene comes round.
    val random = Random(seed)
    val count = 2 + random.nextInt(4)
    val heights = FloatArray(count) { 0.24f + random.nextFloat() * 0.4f }
    val sizes = FloatArray(count) { 0.75f + random.nextFloat() * 0.3f }
    val feather = lerp(c.ink, c.soft, 0.35f)
    for (i in 0 until count) {
        val k = sizes[i] * u
        // Each a little behind the one before, rising and falling as it goes.
        val x = size.width + 0.3f * u - (size.width + 1.7f * u) * p + i * 0.42f * u
        val y = heights[i] * u + sin(p * 4f * PI.toFloat() + i * 1.7f) * 0.06f * u
        val beat = sin(p * 2f * PI.toFloat() * 10f + i * 1.3f)
        // Tail, then the far wing behind the body, the body and head, then the near wing.
        drawPath(
            Path().apply { moveTo(x + 0.08f * k, y); lineTo(x + 0.2f * k, y - 0.035f * k); lineTo(x + 0.19f * k, y + 0.03f * k); close() },
            feather.darker(0.2f)
        )
        fun wing(depth: Float, colour: Color) = drawPath(
            Path().apply {
                moveTo(x - 0.05f * k, y - 0.01f * k)
                quadraticTo(x + 0.02f * k, y - beat * 0.16f * k * depth, x + 0.1f * k, y - beat * 0.24f * k * depth)
                quadraticTo(x + 0.09f * k, y - beat * 0.08f * k * depth, x + 0.07f * k, y + 0.005f * k)
                close()
            },
            colour
        )
        wing(0.85f, feather.darker(0.3f))
        drawOval(Brush.verticalGradient(listOf(feather, feather.lighter(0.45f)), startY = y - 0.05f * k, endY = y + 0.05f * k), Offset(x - 0.11f * k, y - 0.045f * k), Size(0.22f * k, 0.09f * k))
        drawCircle(feather, 0.042f * k, Offset(x - 0.11f * k, y - 0.02f * k))
        drawPath(
            Path().apply { moveTo(x - 0.145f * k, y - 0.03f * k); lineTo(x - 0.2f * k, y - 0.012f * k); lineTo(x - 0.145f * k, y - 0.002f * k); close() },
            Color(0xFFF2A33A)
        )
        drawCircle(Color.White, 0.011f * k, Offset(x - 0.122f * k, y - 0.028f * k))
        drawCircle(Color.Black, 0.006f * k, Offset(x - 0.124f * k, y - 0.028f * k))
        wing(1f, feather.darker(0.1f))
    }
}

/** A monkey that comes down a vine, swings there holding a scanned page, and climbs back out of sight. */
private fun DrawScope.monkey(p: Float, c: ScenePalette, left: Boolean) {
    val u = size.height
    // Comes down in the first eighth, goes back up in the last tenth, and swings in between.
    val out = (minOf(p / 0.125f, (1f - p) / 0.1f, 1f)).coerceIn(0f, 1f)
    val eased = out * out * (3f - 2f * out)
    val anchor = Offset(size.width * (if (left) 0.3f else 0.62f), 0f)
    // Drawn hanging straight down, lifted out of sight by the part of the drop still to come, then swung as one.
    val lift = (1f - eased) * 0.98f * u
    val phase = p * 2f * PI.toFloat() * 3.5f
    rotate(sin(phase) * 18f * eased, pivot = anchor) {
        val grip = Offset(anchor.x, 0.2f * u - lift)
        // Vine with a few leaves.
        drawLine(Brush.horizontalGradient(listOf(VineDark, Vine), startX = grip.x - 0.02f * u, endX = grip.x + 0.02f * u), Offset(anchor.x, -0.1f * u), grip + Offset(0f, 0.08f * u), strokeWidth = 0.034f * u, cap = StrokeCap.Round)
        fun leaf(at: Offset, turn: Float) = rotate(turn, pivot = at) {
            drawOval(Vine, at - Offset(0f, 0.022f * u), Size(0.095f * u, 0.044f * u))
            drawLine(VineDark, at, at + Offset(0.085f * u, 0f), strokeWidth = 0.006f * u)
        }
        leaf(grip + Offset(0.01f * u, -0.1f * u), -25f)
        leaf(grip + Offset(-0.01f * u, -0.02f * u), 205f)

        val chest = Offset(grip.x + 0.035f * u, grip.y + 0.36f * u)
        val fur = Brush.radialGradient(listOf(FurLight, Fur, FurDark), center = chest - Offset(0.05f * u, 0.08f * u), radius = 0.3f * u)
        // Tail: long, thinning, curled at the end.
        val tail = Path().apply {
            moveTo(chest.x - 0.06f * u, chest.y + 0.1f * u)
            cubicTo(chest.x - 0.3f * u, chest.y + 0.24f * u, chest.x - 0.36f * u, chest.y - 0.06f * u, chest.x - 0.24f * u, chest.y - 0.1f * u)
            cubicTo(chest.x - 0.17f * u, chest.y - 0.12f * u, chest.x - 0.17f * u, chest.y - 0.02f * u, chest.x - 0.23f * u, chest.y - 0.03f * u)
        }
        drawPath(tail, FurDark, style = Stroke(width = 0.042f * u, cap = StrokeCap.Round))
        drawPath(tail, Fur, style = Stroke(width = 0.026f * u, cap = StrokeCap.Round))
        // Legs: thigh, shin and foot, trailing a little behind the swing.
        val kick = sin(phase - 0.9f) * 0.035f * u
        fun leg(side: Float) {
            val hip = chest + Offset(side * 0.05f * u, 0.1f * u)
            val knee = hip + Offset(side * 0.07f * u + kick * 0.5f, 0.09f * u)
            val ankle = knee + Offset(-side * 0.03f * u + kick, 0.1f * u)
            drawLine(FurDark, hip, knee, strokeWidth = 0.062f * u, cap = StrokeCap.Round)
            drawLine(Fur, knee, ankle, strokeWidth = 0.05f * u, cap = StrokeCap.Round)
            drawOval(Face.darker(0.12f), ankle - Offset(0.035f * u, 0.012f * u), Size(0.075f * u, 0.04f * u))
        }
        leg(-1f)
        leg(1f)
        // The arm that holds on: shoulder, elbow, and the hand wrapped round the vine.
        val shoulder = chest + Offset(0.03f * u, -0.1f * u)
        val elbow = Offset(grip.x + 0.06f * u, grip.y + 0.14f * u)
        drawLine(FurDark, shoulder, elbow, strokeWidth = 0.058f * u, cap = StrokeCap.Round)
        drawLine(Fur, elbow, grip, strokeWidth = 0.05f * u, cap = StrokeCap.Round)
        // Body with a paler belly.
        drawOval(fur, chest - Offset(0.105f * u, 0.13f * u), Size(0.21f * u, 0.27f * u))
        drawOval(Brush.verticalGradient(listOf(Face, Face.darker(0.12f)), startY = chest.y - 0.07f * u, endY = chest.y + 0.1f * u), chest - Offset(0.06f * u, 0.06f * u), Size(0.12f * u, 0.17f * u))
        drawOval(Face.darker(0.1f), grip - Offset(0.035f * u, 0.02f * u), Size(0.07f * u, 0.045f * u))
        monkeyHead(Offset(chest.x - 0.085f * u, chest.y - 0.2f * u), blink = blinking(p, 6.5f))
        // The other arm holds out a scanned page: its shadow, the sheet, lines of text and the green scan line.
        val page = chest + Offset(0.16f * u, -0.12f * u)
        val hand = page + Offset(0.01f * u, 0.14f * u)
        val bend = chest + Offset(0.12f * u, 0.06f * u)
        drawLine(FurDark, chest + Offset(0.06f * u, -0.05f * u), bend, strokeWidth = 0.055f * u, cap = StrokeCap.Round)
        drawLine(Fur, bend, hand, strokeWidth = 0.048f * u, cap = StrokeCap.Round)
        rotate(8f, pivot = page) { scanPage(page, c, sin(phase * 1.3f)) }
        drawOval(Face.darker(0.1f), hand - Offset(0.028f * u, 0.02f * u), Size(0.056f * u, 0.04f * u))
    }
}

/** The monkey's head: ears, fur, the heart-shaped face, muzzle, eyes with a glint, brows and a smile. */
/** [blink] draws the eyes shut. */
private fun DrawScope.monkeyHead(head: Offset, blink: Boolean = false) {
    val u = size.height
    for (side in listOf(-1f, 1f)) {
        drawCircle(FurDark, 0.045f * u, head + Offset(side * 0.105f * u, -0.005f * u))
        drawCircle(Color(0xFFE0A58C), 0.025f * u, head + Offset(side * 0.105f * u, -0.005f * u))
    }
    drawCircle(Brush.radialGradient(listOf(FurLight, Fur, FurDark), center = head - Offset(0.04f * u, 0.05f * u), radius = 0.17f * u), 0.11f * u, head)
    drawCircle(Face, 0.05f * u, head + Offset(-0.035f * u, -0.012f * u))
    drawCircle(Face, 0.05f * u, head + Offset(0.035f * u, -0.012f * u))
    drawOval(Face, head + Offset(-0.065f * u, 0f), Size(0.13f * u, 0.095f * u))
    for (side in listOf(-1f, 1f)) {
        val eye = head + Offset(side * 0.034f * u, -0.018f * u)
        if (blink) {
            drawLine(Color(0xFF2A1A12), eye - Offset(0.016f * u, 0f), eye + Offset(0.016f * u, 0f), strokeWidth = 0.008f * u, cap = StrokeCap.Round)
        } else {
            drawCircle(Color.White, 0.02f * u, eye)
            drawCircle(Color(0xFF2A1A12), 0.013f * u, eye + Offset(0.003f * u, 0.002f * u))
            drawCircle(Color.White, 0.005f * u, eye + Offset(0.007f * u, -0.004f * u))
        }
        drawLine(FurDark, eye + Offset(-0.02f * u, -0.03f * u), eye + Offset(0.02f * u, -0.033f * u), strokeWidth = 0.009f * u, cap = StrokeCap.Round)
    }
    drawCircle(FurDark, 0.006f * u, head + Offset(-0.01f * u, 0.03f * u))
    drawCircle(FurDark, 0.006f * u, head + Offset(0.01f * u, 0.03f * u))
    drawArc(
        Color(0xFF5A3A26), startAngle = 15f, sweepAngle = 150f, useCenter = false,
        topLeft = head + Offset(-0.04f * u, 0.022f * u), size = Size(0.08f * u, 0.05f * u),
        style = Stroke(width = 0.012f * u, cap = StrokeCap.Round)
    )
}

/** A scanned page as ScanPro draws one: its shadow, the sheet, lines of text, and the green scan line at [scan] (-1 to 1). */
private fun DrawScope.scanPage(page: Offset, c: ScenePalette, scan: Float) {
    val u = size.height
    val sheet = Size(0.16f * u, 0.2f * u)
    drawRoundRect(Color.Black.copy(alpha = 0.18f), page + Offset(0.012f * u, 0.014f * u), sheet, CornerRadius(0.02f * u))
    drawRoundRect(Color.White, page, sheet, CornerRadius(0.02f * u))
    drawRoundRect(c.brand, page, sheet, CornerRadius(0.02f * u), style = Stroke(width = 0.014f * u))
    for (row in 0..3) {
        val y = 0.04f * u + row * 0.038f * u
        drawLine(c.brand.copy(alpha = 0.45f), page + Offset(0.028f * u, y), page + Offset((if (row == 3) 0.09f else 0.13f) * u, y), strokeWidth = 0.011f * u, cap = StrokeCap.Round)
    }
    val line = 0.03f * u + (0.5f + 0.5f * scan) * 0.14f * u
    drawLine(ScanGreen, page + Offset(-0.015f * u, line), page + Offset(0.175f * u, line), strokeWidth = 0.02f * u, cap = StrokeCap.Round)
}

/** A monkey sitting on the road, tossing a scanned page into the air and catching it, its tail flicking. */
private fun DrawScope.monkeyPlay(p: Float, c: ScenePalette, left: Boolean) {
    val u = size.height
    val ground = GROUND * u
    // Pops up from below at the start and ducks back down at the end.
    val out = (minOf(p / 0.1f, (1f - p) / 0.1f, 1f)).coerceIn(0f, 1f)
    val sink = (1f - out * out * (3f - 2f * out)) * 0.7f * u
    val seat = Offset(size.width * (if (left) 0.32f else 0.6f), ground + sink)
    // One toss: up and down along an arc, the page turning once on the way.
    val toss = (p * 5f) % 1f
    val height = 4f * toss * (1f - toss)
    val chest = seat + Offset(0f, -0.2f * u)
    drawOval(Color.Black.copy(alpha = 0.14f), seat + Offset(-0.2f * u, -0.03f * u), Size(0.42f * u, 0.06f * u))
    // Tail lying on the ground behind, its tip flicking.
    val flick = sin(p * 2f * PI.toFloat() * 6f) * 0.05f * u
    val tail = Path().apply {
        moveTo(chest.x - 0.08f * u, seat.y - 0.06f * u)
        cubicTo(chest.x - 0.26f * u, seat.y - 0.02f * u, chest.x - 0.34f * u, seat.y - 0.06f * u, chest.x - 0.33f * u, seat.y - 0.2f * u + flick)
    }
    drawPath(tail, FurDark, style = Stroke(width = 0.042f * u, cap = StrokeCap.Round))
    drawPath(tail, Fur, style = Stroke(width = 0.026f * u, cap = StrokeCap.Round))
    // Legs folded in front, feet resting on the road.
    for (side in listOf(-1f, 1f)) {
        val knee = chest + Offset(side * 0.13f * u, 0.07f * u)
        val foot = Offset(chest.x + side * 0.1f * u + 0.03f * u, seat.y - 0.03f * u)
        drawLine(FurDark, chest + Offset(side * 0.05f * u, 0.1f * u), knee, strokeWidth = 0.062f * u, cap = StrokeCap.Round)
        drawLine(Fur, knee, foot, strokeWidth = 0.05f * u, cap = StrokeCap.Round)
        drawOval(Face.darker(0.12f), foot - Offset(0.03f * u, 0.02f * u), Size(0.085f * u, 0.042f * u))
    }
    drawOval(Brush.radialGradient(listOf(FurLight, Fur, FurDark), center = chest - Offset(0.05f * u, 0.08f * u), radius = 0.3f * u), chest - Offset(0.11f * u, 0.12f * u), Size(0.22f * u, 0.28f * u))
    drawOval(Brush.verticalGradient(listOf(Face, Face.darker(0.12f)), startY = chest.y - 0.06f * u, endY = chest.y + 0.11f * u), chest - Offset(0.062f * u, 0.05f * u), Size(0.124f * u, 0.17f * u))
    // The page above the hands; the arms follow it up as it leaves and come back to catch it.
    val reach = (0.16f + 0.1f * minOf(height * 3f, 1f)) * u
    val page = Offset(chest.x + 0.02f * u, chest.y - 0.3f * u - height * 0.26f * u)
    for (side in listOf(-1f, 1f)) {
        val shoulder = chest + Offset(side * 0.08f * u, -0.07f * u)
        val elbow = shoulder + Offset(side * 0.09f * u, -0.03f * u)
        val hand = Offset(chest.x + side * 0.09f * u, chest.y - 0.07f * u - reach)
        drawLine(FurDark, shoulder, elbow, strokeWidth = 0.055f * u, cap = StrokeCap.Round)
        drawLine(Fur, elbow, hand, strokeWidth = 0.048f * u, cap = StrokeCap.Round)
        drawOval(Face.darker(0.1f), hand - Offset(0.028f * u, 0.02f * u), Size(0.056f * u, 0.04f * u))
    }
    // Head tipped back a little while the page is high.
    val head = chest + Offset(-0.005f * u, -0.21f * u)
    rotate(-10f * height, pivot = head) { monkeyHead(head, blink = blinking(p, 6.2f)) }
    rotate(360f * toss, pivot = page) { scanPage(page - Offset(0.08f * u, 0.1f * u), c, sin(p * 40f)) }
}

/** A painted lorry, facing right: panelled load body, cab with a decorated crown over it, tassels and a charm swinging. */
private fun DrawScope.lorry(x: Float, p: Float, c: ScenePalette) {
    val u = size.height
    val length = 1.9f * u
    val ground = GROUND * u
    val bounce = sin(p * 34f) * 0.007f * u
    fun at(along: Float, up: Float) = Offset(x + along * length, ground - up * u + bounce)
    val sway = sin(p * 30f)
    val r = 0.105f * u
    shadow(x - 0.02f * length, 1.04f * length)
    if (c.night) beam(at(0.995f, 0.3f), 0.4f * u, 0.2f * u)
    smoke(at(0.02f, 0.2f), p, reach = 0.4f * u, puffs = 4, strength = 0.28f)

    val blue = Color(0xFF1F4E9C)
    val saffron = Color(0xFFF08A1C)
    val panels = listOf(Color(0xFFD62B2B), Color(0xFFF2B705), Color(0xFF1E9E5A), Color(0xFFE8641B), Color(0xFF8E3FB5))
    // Chassis, fuel tank and mud flap.
    drawRect(DarkMetal, at(0f, 0.2f), Size(length, 0.06f * u))
    drawRoundRect(Steel, at(0.4f, 0.2f), Size(0.14f * length, 0.08f * u), CornerRadius(0.03f * u))
    drawRect(Tyre, at(0.035f, 0.2f), Size(0.012f * length, 0.13f * u))
    // Load body: blue boards, a row of painted panels each with its motif, yellow rails, a red skirt with white dots.
    drawRect(Brush.verticalGradient(listOf(blue.lighter(0.2f), blue.darker(0.25f)), startY = at(0f, 0.74f).y, endY = at(0f, 0.2f).y), at(0f, 0.74f), Size(0.66f * length, 0.54f * u))
    val panelWidth = 0.66f * length / 5f
    for (i in 0..4) {
        val corner = at(0f, 0.66f) + Offset(i * panelWidth + 0.012f * u, 0f)
        val size = Size(panelWidth - 0.024f * u, 0.3f * u)
        drawRoundRect(panels[i], corner, size, CornerRadius(0.02f * u))
        val middle = corner + Offset(size.width / 2f, size.height / 2f)
        val motif = panels[(i + 2) % 5].lighter(0.35f)
        // A diamond on one panel, a flower on the next.
        if (i % 2 == 0) {
            drawPath(
                Path().apply {
                    moveTo(middle.x, middle.y - 0.1f * u); lineTo(middle.x + 0.07f * u, middle.y)
                    lineTo(middle.x, middle.y + 0.1f * u); lineTo(middle.x - 0.07f * u, middle.y); close()
                },
                motif
            )
            drawCircle(Color.White, 0.022f * u, middle)
        } else {
            for (petal in 0..5) {
                val angle = petal * PI.toFloat() / 3f
                drawCircle(motif, 0.035f * u, middle + Offset(cos(angle), sin(angle)) * (0.055f * u))
            }
            drawCircle(Color(0xFFFFF3C4), 0.03f * u, middle)
        }
    }
    drawRect(Color(0xFFF2B705), at(0f, 0.76f), Size(0.66f * length, 0.035f * u))
    drawRect(Color(0xFFF2B705), at(0f, 0.345f), Size(0.66f * length, 0.02f * u))
    drawRect(Color(0xFFC62828), at(0f, 0.31f), Size(0.66f * length, 0.06f * u))
    for (i in 0..13) drawCircle(Color.White, 0.011f * u, at(0.02f + i * 0.048f, 0.28f))
    // Tassels along the skirt, and a black charm on a string at the back, all swinging with the road.
    for (i in 0..9) {
        val top = at(0.03f + i * 0.066f, 0.25f)
        val end = top + Offset(-sway * 0.02f * u - 0.012f * u, 0.07f * u)
        drawLine(panels[i % 5], top, end, strokeWidth = 0.012f * u, cap = StrokeCap.Round)
        drawCircle(panels[(i + 1) % 5], 0.014f * u, end)
    }
    val charm = at(0.005f, 0.3f) + Offset(-sway * 0.035f * u - 0.03f * u, 0.14f * u)
    drawLine(Tyre, at(0.005f, 0.3f), charm, strokeWidth = 0.008f * u)
    drawCircle(Tyre, 0.03f * u, charm)

    // Cab: saffron, with the door, its window, the windscreen, a grille and a bumper.
    val cab = Path().apply {
        moveTo(at(0.68f, 0.2f).x, at(0.68f, 0.2f).y)
        lineTo(at(0.68f, 0.66f).x, at(0.68f, 0.66f).y)
        lineTo(at(0.93f, 0.66f).x, at(0.93f, 0.66f).y)
        cubicTo(at(0.97f, 0.64f).x, at(0.97f, 0.64f).y, at(0.995f, 0.5f).x, at(0.995f, 0.5f).y, at(1f, 0.4f).x, at(1f, 0.4f).y)
        lineTo(at(1f, 0.2f).x, at(1f, 0.2f).y)
        close()
    }
    drawPath(cab, Brush.verticalGradient(listOf(saffron.lighter(0.3f), saffron, saffron.darker(0.3f)), startY = at(0f, 0.66f).y, endY = at(0f, 0.2f).y))
    val glass = Brush.verticalGradient(listOf(Glass, GlassDeep), startY = at(0f, 0.62f).y, endY = at(0f, 0.44f).y)
    drawRoundRect(glass, at(0.7f, 0.62f), Size(0.09f * length, 0.17f * u), CornerRadius(0.02f * u))
    drawPath(
        Path().apply {
            moveTo(at(0.82f, 0.62f).x, at(0.82f, 0.62f).y); lineTo(at(0.935f, 0.62f).x, at(0.935f, 0.62f).y)
            lineTo(at(0.98f, 0.45f).x, at(0.98f, 0.45f).y); lineTo(at(0.82f, 0.45f).x, at(0.82f, 0.45f).y); close()
        },
        glass
    )
    drawLine(Color.White.copy(alpha = 0.55f), at(0.85f, 0.6f), at(0.88f, 0.48f), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
    drawLine(saffron.darker(0.45f), at(0.805f, 0.64f), at(0.805f, 0.22f), strokeWidth = 0.008f * u)
    drawLine(Color(0xFF1E9E5A), at(0.69f, 0.41f), at(0.99f, 0.41f), strokeWidth = 0.022f * u)
    drawLine(Color(0xFFF2B705), at(0.69f, 0.385f), at(0.995f, 0.385f), strokeWidth = 0.012f * u)
    for (i in 0..2) drawLine(DarkMetal, at(0.955f, 0.36f - i * 0.03f), at(0.998f, 0.36f - i * 0.03f), strokeWidth = 0.01f * u)
    drawCircle(Brush.radialGradient(listOf(Color.White, Lamp), center = at(0.985f, 0.3f), radius = 0.05f * u), 0.032f * u, at(0.982f, 0.29f))
    drawRoundRect(Tyre, at(0.9f, 0.22f), Size(0.11f * length, 0.055f * u), CornerRadius(0.02f * u))
    for (i in 0..3) drawLine(Color(0xFFF2B705), at(0.915f + i * 0.024f, 0.215f), at(0.927f + i * 0.024f, 0.172f), strokeWidth = 0.012f * u)
    // The crown over the cab: a painted arch reaching forward, with a scalloped edge and a row of bright bosses.
    val crown = Path().apply {
        moveTo(at(0.66f, 0.66f).x, at(0.66f, 0.66f).y)
        lineTo(at(0.66f, 0.86f).x, at(0.66f, 0.86f).y)
        cubicTo(at(0.8f, 0.93f).x, at(0.8f, 0.93f).y, at(0.94f, 0.9f).x, at(0.94f, 0.9f).y, at(1.03f, 0.76f).x, at(1.03f, 0.76f).y)
        lineTo(at(1.02f, 0.7f).x, at(1.02f, 0.7f).y)
        lineTo(at(0.94f, 0.66f).x, at(0.94f, 0.66f).y)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFD84A), Color(0xFFE8A200)), startY = at(0f, 0.92f).y, endY = at(0f, 0.66f).y))
    drawPath(crown, Color(0xFFC62828), style = Stroke(width = 0.016f * u))
    for (i in 0..5) {
        val boss = at(0.69f + i * 0.054f, 0.78f + sin(i * 0.6f) * 0.03f)
        drawCircle(panels[i % 5], 0.026f * u, boss)
        drawCircle(Color.White.copy(alpha = 0.8f), 0.009f * u, boss - Offset(0.006f * u, 0.006f * u))
    }
    for (i in 0..7) drawCircle(Color(0xFFC62828), 0.014f * u, at(0.68f + i * 0.038f, 0.675f))
    // Flag on the roof, streaming back.
    drawLine(DarkMetal, at(0.72f, 0.9f), at(0.72f, 1.02f), strokeWidth = 0.01f * u)
    drawPath(
        Path().apply {
            moveTo(at(0.72f, 1.02f).x, at(0.72f, 1.02f).y)
            lineTo(at(0.66f, 0.99f + sway * 0.01f).x, at(0.66f, 0.99f + sway * 0.01f).y)
            lineTo(at(0.72f, 0.95f).x, at(0.72f, 0.95f).y); close()
        },
        saffron
    )
    // Twin rear wheels and the front wheel.
    val wheels = listOf(0.12f, 0.24f, 0.86f)
    for (along in wheels) drawCircle(Tyre.copy(alpha = 0.9f), r * 1.18f, Offset(x + along * length, ground - r + bounce))
    for (along in wheels) wheel(Offset(x + along * length, ground - r), r, x, spokes = 6)
}

/** A white train with a blue band and a long sloped nose, facing right, under its overhead wire. */
private fun DrawScope.train(x: Float, c: ScenePalette) {
    val u = size.height
    val length = 4.2f * u
    val ground = GROUND * u
    fun at(along: Float, up: Float) = Offset(x + along * length, ground - up * u)
    val band = Color(0xFF1B4FA8)
    // Overhead wire and the pantograph reaching up to it.
    drawLine(c.soft.copy(alpha = 0.45f), Offset(0f, 0.06f * u), Offset(size.width, 0.06f * u), strokeWidth = 0.008f * u)
    val foot = at(0.6f, 0.66f)
    val knee = at(0.585f, 0.78f)
    val shoe = Offset(at(0.6f, 0f).x, 0.07f * u)
    drawLine(DarkMetal, foot, knee, strokeWidth = 0.014f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, knee, shoe, strokeWidth = 0.014f * u, cap = StrokeCap.Round)
    drawLine(DarkMetal, shoe - Offset(0.07f * u, 0f), shoe + Offset(0.07f * u, 0f), strokeWidth = 0.018f * u, cap = StrokeCap.Round)
    shadow(x, length)
    // Bogies under the skirt.
    for (along in listOf(0.05f, 0.12f, 0.28f, 0.35f, 0.4f, 0.47f, 0.62f, 0.69f, 0.74f, 0.81f, 0.9f, 0.95f)) {
        val centre = Offset(x + along * length, ground - 0.05f * u)
        drawCircle(Tyre, 0.05f * u, centre)
        drawCircle(Steel, 0.022f * u, centre)
    }
    drawRect(DarkMetal, at(0f, 0.17f), Size(0.97f * length, 0.08f * u))
    // Body: three cars and the driving end, the roof line dropping in one curve to the coupler.
    val shell = Path().apply {
        moveTo(at(0f, 0.15f).x, at(0f, 0.15f).y)
        lineTo(at(0f, 0.64f).x, at(0f, 0.64f).y)
        quadraticTo(at(0f, 0.67f).x, at(0f, 0.67f).y, at(0.01f, 0.67f).x, at(0.01f, 0.67f).y)
        lineTo(at(0.86f, 0.67f).x, at(0.86f, 0.67f).y)
        cubicTo(at(0.93f, 0.66f).x, at(0.93f, 0.66f).y, at(0.985f, 0.42f).x, at(0.985f, 0.42f).y, at(1f, 0.24f).x, at(1f, 0.24f).y)
        quadraticTo(at(1f, 0.15f).x, at(1f, 0.15f).y, at(0.985f, 0.15f).x, at(0.985f, 0.15f).y)
        close()
    }
    drawPath(shell, Brush.verticalGradient(listOf(Color.White, Color(0xFFF2F4F7), Color(0xFFC9CED6)), startY = at(0f, 0.67f).y, endY = at(0f, 0.15f).y))
    // The blue band through the windows, sweeping down the nose to the lamps.
    val sweep = Path().apply {
        moveTo(at(0f, 0.54f).x, at(0f, 0.54f).y)
        lineTo(at(0.86f, 0.54f).x, at(0.86f, 0.54f).y)
        cubicTo(at(0.93f, 0.52f).x, at(0.93f, 0.52f).y, at(0.97f, 0.4f).x, at(0.97f, 0.4f).y, at(0.995f, 0.27f).x, at(0.995f, 0.27f).y)
        lineTo(at(0.975f, 0.22f).x, at(0.975f, 0.22f).y)
        cubicTo(at(0.95f, 0.32f).x, at(0.95f, 0.32f).y, at(0.92f, 0.36f).x, at(0.92f, 0.36f).y, at(0.86f, 0.36f).x, at(0.86f, 0.36f).y)
        lineTo(at(0f, 0.36f).x, at(0f, 0.36f).y)
        close()
    }
    drawPath(sweep, Brush.verticalGradient(listOf(band.lighter(0.18f), band.darker(0.15f)), startY = at(0f, 0.54f).y, endY = at(0f, 0.3f).y))
    // Windows in the band, doors at the car ends, and the gaps between the cars.
    for (car in 0..2) {
        val start = 0.02f + car * 0.29f
        for (i in 0..5) drawRoundRect(Color(0xFF16202E), at(start + 0.035f + i * 0.038f, 0.52f), Size(0.03f * length, 0.13f * u), CornerRadius(0.02f * u))
        drawRoundRect(Color(0xFFDDE2E8), at(start + 0.005f, 0.58f), Size(0.02f * length, 0.4f * u), CornerRadius(0.012f * u))
        drawRoundRect(Color(0xFF16202E), at(start + 0.009f, 0.52f), Size(0.012f * length, 0.13f * u), CornerRadius(0.01f * u))
        if (car > 0) drawLine(DarkMetal, at(start - 0.002f, 0.67f), at(start - 0.002f, 0.16f), strokeWidth = 0.014f * u)
    }
    drawLine(Color.White.copy(alpha = 0.9f), at(0.01f, 0.635f), at(0.86f, 0.635f), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
    // Driving cab: black windscreen wrapped round the nose, side window, headlamps.
    drawPath(
        Path().apply {
            moveTo(at(0.895f, 0.63f).x, at(0.895f, 0.63f).y)
            cubicTo(at(0.925f, 0.62f).x, at(0.925f, 0.62f).y, at(0.955f, 0.53f).x, at(0.955f, 0.53f).y, at(0.972f, 0.42f).x, at(0.972f, 0.42f).y)
            lineTo(at(0.93f, 0.42f).x, at(0.93f, 0.42f).y)
            cubicTo(at(0.92f, 0.5f).x, at(0.92f, 0.5f).y, at(0.91f, 0.55f).x, at(0.91f, 0.55f).y, at(0.895f, 0.57f).x, at(0.895f, 0.57f).y)
            close()
        },
        Brush.verticalGradient(listOf(Color(0xFF4A5A70), Color(0xFF0E131B)), startY = at(0f, 0.63f).y, endY = at(0f, 0.42f).y)
    )
    drawRoundRect(Color(0xFF16202E), at(0.868f, 0.52f), Size(0.02f * length, 0.13f * u), CornerRadius(0.02f * u))
    drawOval(Brush.radialGradient(listOf(Color.White, Lamp), center = at(0.985f, 0.24f), radius = 0.05f * u), at(0.972f, 0.27f), Size(0.02f * length, 0.05f * u))
    if (c.night) beam(at(0.995f, 0.24f), 0.45f * u, 0.18f * u)
    drawCircle(TailRed, 0.014f * u, at(0.99f, 0.19f))
}

/** A zombie shuffling to the right, slowly: arms out, one leg dragging, head lolling. */
private fun DrawScope.zombie(x: Float, p: Float, c: ScenePalette) {
    val u = size.height
    val ground = GROUND * u
    val skin = Color(0xFF8DBB6B)
    val shade = skin.darker(0.3f)
    val shirt = Color(0xFF6C7C90)
    val trousers = Color(0xFF3B3F52)
    val step = p * 2f * PI.toFloat() * 9f
    // Each step lurches the body forward and down a little.
    val lurch = sin(step)
    val middle = x + 0.3f * u
    val hip = Offset(middle, ground - 0.36f * u + abs(lurch) * 0.012f * u)
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(middle - 0.17f * u, ground - 0.03f * u), Size(0.36f * u, 0.06f * u))
    // Legs: the far one drags stiff behind, the near one takes the short steps.
    val dragFoot = Offset(middle - 0.11f * u - lurch * 0.02f * u, ground - 0.02f * u)
    drawLine(trousers.darker(0.2f), hip + Offset(-0.03f * u, 0f), dragFoot, strokeWidth = 0.075f * u, cap = StrokeCap.Round)
    drawRoundRect(Tyre, dragFoot - Offset(0.035f * u, 0.03f * u), Size(0.11f * u, 0.05f * u), CornerRadius(0.02f * u))
    val knee = hip + Offset(0.05f * u + lurch * 0.03f * u, 0.17f * u)
    val foot = Offset(middle + 0.07f * u + lurch * 0.06f * u, ground - 0.02f * u - maxOf(lurch, 0f) * 0.03f * u)
    drawLine(trousers, hip + Offset(0.03f * u, 0f), knee, strokeWidth = 0.08f * u, cap = StrokeCap.Round)
    drawLine(trousers, knee, foot, strokeWidth = 0.07f * u, cap = StrokeCap.Round)
    drawRoundRect(Tyre, foot - Offset(0.03f * u, 0.03f * u), Size(0.12f * u, 0.05f * u), CornerRadius(0.02f * u))
    // Body leaning forward, in a torn shirt.
    rotate(9f + lurch * 3f, pivot = hip) {
        val neck = hip + Offset(0f, -0.3f * u)
        val torso = Path().apply {
            moveTo(hip.x - 0.085f * u, neck.y + 0.02f * u)
            lineTo(hip.x + 0.085f * u, neck.y + 0.02f * u)
            lineTo(hip.x + 0.09f * u, hip.y + 0.03f * u)
            // Ragged hem.
            lineTo(hip.x + 0.05f * u, hip.y - 0.01f * u)
            lineTo(hip.x + 0.02f * u, hip.y + 0.045f * u)
            lineTo(hip.x - 0.02f * u, hip.y)
            lineTo(hip.x - 0.05f * u, hip.y + 0.04f * u)
            lineTo(hip.x - 0.09f * u, hip.y)
            close()
        }
        drawPath(torso, Brush.verticalGradient(listOf(shirt.lighter(0.15f), shirt.darker(0.3f)), startY = neck.y, endY = hip.y))
        drawLine(shirt.darker(0.45f), neck + Offset(-0.03f * u, 0.12f * u), neck + Offset(0.02f * u, 0.17f * u), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
        drawCircle(skin.darker(0.1f), 0.022f * u, neck + Offset(0.04f * u, 0.2f * u))
        // Arms straight out ahead, hands hanging, bobbing out of step with each other.
        for (side in listOf(0f, 1f)) {
            val bob = sin(step + side * 1.6f) * 0.02f * u
            val shoulder = neck + Offset(0.03f * u, 0.07f * u + side * 0.04f * u)
            val wrist = shoulder + Offset(0.27f * u, 0.01f * u + bob)
            val tone = if (side == 0f) shade else skin
            drawLine(if (side == 0f) shirt.darker(0.25f) else shirt, shoulder, shoulder + Offset(0.12f * u, bob * 0.4f), strokeWidth = 0.065f * u, cap = StrokeCap.Round)
            drawLine(tone, shoulder + Offset(0.12f * u, bob * 0.4f), wrist, strokeWidth = 0.045f * u, cap = StrokeCap.Round)
            for (finger in 0..2) {
                drawLine(tone, wrist, wrist + Offset(0.035f * u, (finger - 0.3f) * 0.025f * u + 0.02f * u), strokeWidth = 0.014f * u, cap = StrokeCap.Round)
            }
        }
        // Head: lolling to one side, thin hair, one wide eye and one drooping, a crooked stitched mouth.
        val head = neck + Offset(0.035f * u, -0.085f * u)
        rotate(lurch * 7f + 6f, pivot = neck) {
            drawOval(Brush.radialGradient(listOf(skin.lighter(0.2f), skin, shade), center = head - Offset(0.03f * u, 0.04f * u), radius = 0.17f * u), head - Offset(0.095f * u, 0.105f * u), Size(0.19f * u, 0.21f * u))
            drawArc(Color(0xFF2B2E36), 185f, 150f, true, head - Offset(0.098f * u, 0.115f * u), Size(0.19f * u, 0.14f * u))
            drawCircle(shade, 0.035f * u, head + Offset(0.04f * u, -0.01f * u))
            if (blinking(p, 11f)) {
                drawLine(Color(0xFF2A1A12), head + Offset(0.018f * u, -0.01f * u), head + Offset(0.062f * u, -0.01f * u), strokeWidth = 0.009f * u, cap = StrokeCap.Round)
            } else {
                drawCircle(Color(0xFFFFF7D6), 0.027f * u, head + Offset(0.04f * u, -0.01f * u))
                drawCircle(Color(0xFF2A1A12), 0.01f * u, head + Offset(0.05f * u, -0.008f * u))
            }
            drawCircle(shade, 0.022f * u, head + Offset(-0.02f * u, 0f))
            drawLine(Color(0xFF2A1A12), head + Offset(-0.035f * u, -0.002f * u), head + Offset(-0.005f * u, 0.004f * u), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
            val mouth = head + Offset(-0.01f * u, 0.055f * u)
            drawLine(Color(0xFF3A2A2A), mouth, mouth + Offset(0.075f * u, -0.008f * u), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
            for (stitch in 0..2) {
                val at = mouth + Offset(0.015f * u + stitch * 0.022f * u, -0.002f * u - stitch * 0.002f * u)
                drawLine(Color(0xFF3A2A2A), at - Offset(0f, 0.012f * u), at + Offset(0f, 0.012f * u), strokeWidth = 0.007f * u)
            }
            drawLine(shade, head + Offset(0.075f * u, -0.06f * u), head + Offset(0.055f * u, -0.035f * u), strokeWidth = 0.008f * u, cap = StrokeCap.Round)
        }
    }
}

/**
 * The sky behind the scenes: by day (6 to 17) a sun with slowly turning rays and two clouds drifting; otherwise a
 * crescent moon and a few stars that twinkle. Kept pale, so the scenes in front stay the subject.
 */
private fun DrawScope.sky(hour: Int, millis: Long, c: ScenePalette) {
    val u = size.height
    val seconds = millis / 1000f
    val orb = Offset(size.width - 0.42f * u, 0.27f * u)
    if (hour in 6..17) {
        val gold = Color(0xFFFFC53D)
        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = 0.35f), gold.copy(alpha = 0f)), center = orb, radius = 0.34f * u), 0.34f * u, orb)
        rotate(seconds * 8f, pivot = orb) {
            for (i in 0 until 8) {
                val angle = i * PI.toFloat() / 4f
                val direction = Offset(cos(angle), sin(angle))
                drawLine(gold.copy(alpha = 0.8f), orb + direction * (0.17f * u), orb + direction * (0.23f * u), strokeWidth = 0.022f * u, cap = StrokeCap.Round)
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE58A), gold), center = orb - Offset(0.03f * u, 0.03f * u), radius = 0.16f * u), 0.125f * u, orb)
    } else {
        val stars = listOf(0.08f to 0.2f, 0.22f to 0.42f, 0.37f to 0.14f, 0.52f to 0.34f, 0.66f to 0.18f, 0.93f to 0.5f)
        stars.forEachIndexed { i, (along, down) ->
            val twinkle = 0.45f + 0.4f * sin(seconds * 2.2f + i * 1.9f)
            val centre = Offset(along * size.width, down * u)
            val arm = (0.025f + 0.012f * (i % 2)) * u
            drawLine(c.soft.copy(alpha = twinkle), centre - Offset(arm, 0f), centre + Offset(arm, 0f), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
            drawLine(c.soft.copy(alpha = twinkle), centre - Offset(0f, arm), centre + Offset(0f, arm), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
        }
        val moon = Color(0xFFF4E6B0)
        drawCircle(Brush.radialGradient(listOf(moon.copy(alpha = 0.3f), moon.copy(alpha = 0f)), center = orb, radius = 0.3f * u), 0.3f * u, orb)
        // A crescent: the disc with a bite taken out of it.
        val crescent = Path.combine(
            PathOperation.Difference,
            Path().apply { addOval(Rect(orb, 0.125f * u)) },
            Path().apply { addOval(Rect(orb + Offset(0.07f * u, -0.04f * u), 0.11f * u)) }
        )
        drawPath(crescent, Brush.linearGradient(listOf(Color(0xFFFFF6CF), moon.darker(0.12f)), start = orb - Offset(0.12f * u, 0.12f * u), end = orb + Offset(0.1f * u, 0.12f * u)))
    }
    // Clouds drift across and come round again; fainter at night.
    val tint = (if (hour in 6..17) c.soft.copy(alpha = 0.2f) else c.soft.copy(alpha = 0.12f))
    val lap = size.width + 1.2f * u
    for (i in 0..1) {
        val x = ((seconds * (0.035f + i * 0.02f) * u * 4f + i * 0.55f * lap) % lap) - 0.6f * u
        val y = (0.2f + i * 0.2f) * u
        val k = (1f - i * 0.25f) * u
        drawRoundRect(tint, Offset(x, y), Size(0.5f * k, 0.11f * k), CornerRadius(0.055f * k))
        drawCircle(tint, 0.09f * k, Offset(x + 0.16f * k, y + 0.01f * k))
        drawCircle(tint, 0.12f * k, Offset(x + 0.3f * k, y - 0.01f * k))
    }
}

/** A small propeller plane flying left to right, towing a rippling banner with the app's name. */
private fun DrawScope.plane(p: Float, c: ScenePalette, banner: TextLayoutResult) {
    val u = size.height
    val bannerWidth = 0.95f * u
    val whole = 0.75f * u + 0.3f * u + bannerWidth
    // The nose's position: from fully off the left to fully off the right.
    val nose = -0.1f * u + (size.width + whole + 0.2f * u) * p
    val y = 0.42f * u + sin(p * 2f * PI.toFloat() * 2f) * 0.06f * u
    val tail = nose - 0.75f * u
    // Banner: a strip whose edges ripple, on two ropes from the tail.
    val start = tail - 0.3f * u - bannerWidth
    fun ripple(x: Float) = sin((x - start) / u * 7f - p * 60f) * 0.022f * u
    val cloth = Path().apply {
        val steps = 12
        moveTo(start, y - 0.11f * u + ripple(start))
        for (i in 1..steps) { val x = start + bannerWidth * i / steps; lineTo(x, y - 0.11f * u + ripple(x)) }
        for (i in steps downTo 0) { val x = start + bannerWidth * i / steps; lineTo(x, y + 0.11f * u + ripple(x)) }
        close()
    }
    drawLine(c.soft, Offset(tail, y), Offset(start + bannerWidth, y - 0.11f * u + ripple(start + bannerWidth)), strokeWidth = 0.008f * u)
    drawLine(c.soft, Offset(tail, y), Offset(start + bannerWidth, y + 0.11f * u + ripple(start + bannerWidth)), strokeWidth = 0.008f * u)
    drawPath(cloth, Brush.horizontalGradient(listOf(c.brand.darker(0.2f), c.brand, c.brand.lighter(0.15f)), startX = start, endX = start + bannerWidth))
    drawPath(cloth, Color.White.copy(alpha = 0.5f), style = Stroke(width = 0.01f * u))
    val fit = minOf(0.15f * u / banner.size.height, (bannerWidth - 0.1f * u) / banner.size.width)
    val textAt = Offset(start + bannerWidth / 2f, y + ripple(start + bannerWidth / 2f))
    scale(fit, fit, pivot = textAt) {
        drawText(banner, topLeft = textAt - Offset(banner.size.width / 2f, banner.size.height / 2f))
    }

    // Plane: tailplane and fin, fuselage, cockpit glass, a stripe, the wing, wheels and the propeller's blur.
    val white = Color(0xFFF7F8FA)
    drawPath(
        Path().apply { moveTo(tail + 0.02f * u, y - 0.02f * u); lineTo(tail - 0.03f * u, y - 0.2f * u); lineTo(tail + 0.1f * u, y - 0.2f * u); lineTo(tail + 0.2f * u, y - 0.04f * u); close() },
        c.brand
    )
    val fuselage = Path().apply {
        moveTo(tail, y - 0.01f * u)
        cubicTo(tail + 0.2f * u, y - 0.1f * u, nose - 0.25f * u, y - 0.13f * u, nose - 0.06f * u, y - 0.07f * u)
        quadraticTo(nose, y - 0.02f * u, nose - 0.04f * u, y + 0.06f * u)
        cubicTo(nose - 0.25f * u, y + 0.1f * u, tail + 0.25f * u, y + 0.08f * u, tail, y + 0.02f * u)
        close()
    }
    drawPath(fuselage, Brush.verticalGradient(listOf(Color.White, white, Color(0xFFBCC3CC)), startY = y - 0.13f * u, endY = y + 0.1f * u))
    drawLine(c.brand, Offset(tail + 0.08f * u, y + 0.005f * u), Offset(nose - 0.08f * u, y + 0.005f * u), strokeWidth = 0.022f * u, cap = StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(nose - 0.3f * u, y - 0.105f * u); quadraticTo(nose - 0.2f * u, y - 0.11f * u, nose - 0.13f * u, y - 0.05f * u)
            lineTo(nose - 0.3f * u, y - 0.05f * u); close()
        },
        Brush.verticalGradient(listOf(Glass, GlassDeep), startY = y - 0.11f * u, endY = y - 0.05f * u)
    )
    drawRoundRect(Brush.verticalGradient(listOf(white, Color(0xFFAEB6C0))), Offset(nose - 0.47f * u, y + 0.02f * u), Size(0.3f * u, 0.05f * u), CornerRadius(0.025f * u))
    drawRoundRect(white, Offset(tail - 0.02f * u, y - 0.03f * u), Size(0.16f * u, 0.03f * u), CornerRadius(0.015f * u))
    drawLine(DarkMetal, Offset(nose - 0.3f * u, y + 0.07f * u), Offset(nose - 0.32f * u, y + 0.14f * u), strokeWidth = 0.012f * u)
    drawCircle(Tyre, 0.035f * u, Offset(nose - 0.32f * u, y + 0.15f * u))
    drawCircle(DarkMetal, 0.03f * u, Offset(nose - 0.02f * u, y))
    drawOval(c.soft.copy(alpha = 0.3f), Offset(nose - 0.015f * u, y - 0.16f * u), Size(0.03f * u, 0.32f * u))
    val blade = sin(p * 400f) * 0.15f * u
    drawLine(DarkMetal.copy(alpha = 0.7f), Offset(nose, y - blade), Offset(nose, y + blade), strokeWidth = 0.014f * u, cap = StrokeCap.Round)
}

/** A rocket on its pad: smoke builds, it trembles, then climbs out of the top faster and faster. */
private fun DrawScope.rocket(p: Float, c: ScenePalette, left: Boolean) {
    val u = size.height
    val ground = GROUND * u
    val centre = size.width * (if (left) 0.3f else 0.58f)
    val lit = p > 0.12f
    val flight = ((p - 0.3f) / 0.5f).coerceIn(0f, 1f)
    val climb = flight * flight * 1.7f * u
    val tremble = if (lit && flight == 0f) sin(p * 900f) * 0.006f * u else 0f
    drawLine(c.soft.copy(alpha = 0.18f), Offset(0f, ground), Offset(size.width, ground), strokeWidth = 0.015f * u)
    drawRect(DarkMetal, Offset(centre - 0.16f * u, ground - 0.03f * u), Size(0.32f * u, 0.03f * u))
    // Gantry beside the pad.
    drawLine(Steel, Offset(centre - 0.2f * u, ground), Offset(centre - 0.2f * u, ground - 0.5f * u), strokeWidth = 0.018f * u)
    for (i in 1..4) drawLine(Steel, Offset(centre - 0.2f * u, ground - i * 0.11f * u), Offset(centre - 0.13f * u, ground - i * 0.11f * u + 0.04f * u), strokeWidth = 0.01f * u)
    // Smoke rolling out along the ground from the moment it lights, thinning once the rocket has gone.
    if (lit) {
        val grow = ((p - 0.12f) / 0.4f).coerceIn(0f, 1f)
        val fade = 1f - ((p - 0.75f) / 0.25f).coerceIn(0f, 1f)
        for (i in -4..4) {
            val spread = i / 4f
            val radius = (0.05f + 0.1f * grow * (1f - abs(spread) * 0.5f)) * u * (0.85f + 0.15f * sin(p * 30f + i))
            drawCircle(
                Color(0xFFB8BEC6).copy(alpha = 0.55f * fade),
                radius,
                Offset(centre + spread * grow * 0.55f * u, ground - radius * 0.7f)
            )
        }
    }
    val base = Offset(centre + tremble, ground - 0.03f * u - climb)
    // Flame under the engine, flickering, longer once it is climbing.
    if (lit) {
        val flicker = 0.8f + 0.2f * sin(p * 700f)
        val long = (0.12f + 0.2f * flight) * u * flicker
        val flame = Path().apply {
            moveTo(base.x - 0.045f * u, base.y)
            quadraticTo(base.x - 0.06f * u, base.y + long * 0.5f, base.x, base.y + long)
            quadraticTo(base.x + 0.06f * u, base.y + long * 0.5f, base.x + 0.045f * u, base.y)
            close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFF9B21), Color(0x00FF5A1F)), startY = base.y, endY = base.y + long))
    }
    // Fins, body, nose cone, a porthole and the brand's band.
    for (side in listOf(-1f, 1f)) {
        drawPath(
            Path().apply {
                moveTo(base.x + side * 0.05f * u, base.y - 0.16f * u)
                lineTo(base.x + side * 0.13f * u, base.y + 0.01f * u)
                lineTo(base.x + side * 0.05f * u, base.y - 0.02f * u)
                close()
            },
            Color(0xFFE2572B)
        )
    }
    val body = Path().apply {
        moveTo(base.x - 0.06f * u, base.y)
        lineTo(base.x - 0.06f * u, base.y - 0.36f * u)
        cubicTo(base.x - 0.06f * u, base.y - 0.48f * u, base.x - 0.02f * u, base.y - 0.54f * u, base.x, base.y - 0.57f * u)
        cubicTo(base.x + 0.02f * u, base.y - 0.54f * u, base.x + 0.06f * u, base.y - 0.48f * u, base.x + 0.06f * u, base.y - 0.36f * u)
        lineTo(base.x + 0.06f * u, base.y)
        close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(Color(0xFFC9CED6), Color.White, Color(0xFFDDE1E7), Color(0xFF9AA3AD)), startX = base.x - 0.06f * u, endX = base.x + 0.06f * u))
    drawPath(
        Path().apply {
            moveTo(base.x - 0.055f * u, base.y - 0.42f * u)
            cubicTo(base.x - 0.04f * u, base.y - 0.5f * u, base.x - 0.015f * u, base.y - 0.545f * u, base.x, base.y - 0.57f * u)
            cubicTo(base.x + 0.015f * u, base.y - 0.545f * u, base.x + 0.04f * u, base.y - 0.5f * u, base.x + 0.055f * u, base.y - 0.42f * u)
            close()
        },
        Color(0xFFE2572B)
    )
    drawRect(c.brand, Offset(base.x - 0.06f * u, base.y - 0.2f * u), Size(0.12f * u, 0.04f * u))
    drawCircle(DarkMetal, 0.034f * u, Offset(base.x, base.y - 0.31f * u))
    drawCircle(Brush.radialGradient(listOf(Glass, GlassDeep), center = Offset(base.x - 0.01f * u, base.y - 0.32f * u), radius = 0.04f * u), 0.025f * u, Offset(base.x, base.y - 0.31f * u))
    drawRect(DarkMetal, Offset(base.x - 0.045f * u, base.y - 0.01f * u), Size(0.09f * u, 0.025f * u))
}

/** An elephant walking to the right in a red and gold cloth: legs in step, trunk and tail swinging, an ear flapping. */
private fun DrawScope.elephant(x: Float, p: Float) {
    val u = size.height
    val ground = GROUND * u
    val hide = Color(0xFF8B929C)
    val dark = hide.darker(0.28f)
    val step = p * 2f * PI.toFloat() * 8f
    val sway = sin(step)
    val bob = abs(sway) * 0.012f * u
    val left = x + 0.1f * u
    fun at(along: Float, up: Float) = Offset(left + along * u, ground - up * u + bob)
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(left + 0.05f * u, ground - 0.03f * u), Size(1.05f * u, 0.07f * u))
    // Tail with its tuft.
    val tuft = at(-0.03f, 0.28f) + Offset(-sway * 0.04f * u, 0f)
    drawLine(dark, at(0.07f, 0.55f), tuft, strokeWidth = 0.022f * u, cap = StrokeCap.Round)
    drawCircle(Color(0xFF3F434B), 0.022f * u, tuft)
    // Four legs: the far pair darker, each pair stepping opposite the other.
    fun leg(along: Float, stride: Float, colour: Color) {
        val foot = Offset(left + along * u + stride * 0.07f * u, ground - maxOf(stride, 0f) * 0.02f * u)
        drawLine(colour, at(along, 0.36f), foot - Offset(0f, 0.05f * u), strokeWidth = 0.14f * u, cap = StrokeCap.Round)
        drawRoundRect(colour, foot - Offset(0.075f * u, 0.07f * u), Size(0.15f * u, 0.07f * u), CornerRadius(0.02f * u))
        for (toe in -1..1) drawArc(Color(0xFFE9E3D5), 180f, 180f, true, foot + Offset(toe * 0.045f * u - 0.018f * u, -0.03f * u), Size(0.036f * u, 0.04f * u))
    }
    leg(0.25f, -sway, dark)
    leg(0.66f, sway, dark)
    // Body.
    drawOval(Brush.verticalGradient(listOf(hide.lighter(0.18f), hide, dark), startY = at(0f, 0.74f).y, endY = at(0f, 0.28f).y), at(0.05f, 0.74f), Size(0.72f * u, 0.46f * u))
    leg(0.17f, sway, hide.darker(0.08f))
    leg(0.58f, -sway, hide.darker(0.08f))
    // The cloth over its back: red, a gold border, a row of gold discs and a fringe.
    val cloth = Path().apply {
        moveTo(at(0.22f, 0.71f).x, at(0.22f, 0.71f).y)
        quadraticTo(at(0.4f, 0.78f).x, at(0.4f, 0.78f).y, at(0.58f, 0.72f).x, at(0.58f, 0.72f).y)
        lineTo(at(0.6f, 0.44f).x, at(0.6f, 0.44f).y)
        lineTo(at(0.2f, 0.44f).x, at(0.2f, 0.44f).y)
        close()
    }
    drawPath(cloth, Brush.verticalGradient(listOf(Color(0xFFD93030), Color(0xFF9E1B1B)), startY = at(0f, 0.76f).y, endY = at(0f, 0.44f).y))
    drawPath(cloth, Color(0xFFF2B705), style = Stroke(width = 0.022f * u))
    for (i in 0..3) drawCircle(Color(0xFFF2B705), 0.022f * u, at(0.27f + i * 0.09f, 0.58f))
    for (i in 0..7) drawLine(Color(0xFFF2B705), at(0.21f + i * 0.055f, 0.44f), at(0.21f + i * 0.055f, 0.4f) + Offset(-sway * 0.008f * u, 0f), strokeWidth = 0.012f * u, cap = StrokeCap.Round)
    // Head, with the ear flapping over the shoulder.
    val head = at(0.86f, 0.6f)
    rotate(sway * 7f, pivot = head + Offset(-0.1f * u, -0.1f * u)) {
        drawOval(Brush.verticalGradient(listOf(dark.lighter(0.1f), dark.darker(0.15f))), head + Offset(-0.26f * u, -0.17f * u), Size(0.24f * u, 0.36f * u))
        drawOval(Color(0xFFC49A93).copy(alpha = 0.5f), head + Offset(-0.22f * u, -0.1f * u), Size(0.15f * u, 0.24f * u))
    }
    drawCircle(Brush.radialGradient(listOf(hide.lighter(0.22f), hide, dark), center = head - Offset(0.05f * u, 0.07f * u), radius = 0.3f * u), 0.185f * u, head)
    // Trunk: thick at the face, thinner to the tip, which swings and curls.
    val tip = head + Offset(0.2f * u + sway * 0.04f * u, 0.4f * u)
    val trunk = Path().apply {
        moveTo(head.x + 0.12f * u, head.y + 0.03f * u)
        cubicTo(head.x + 0.26f * u, head.y + 0.12f * u, head.x + 0.14f * u + sway * 0.03f * u, head.y + 0.34f * u, tip.x, tip.y)
        quadraticTo(tip.x + 0.06f * u, tip.y + 0.01f * u, tip.x + 0.07f * u, tip.y - 0.05f * u)
    }
    drawPath(trunk, dark, style = Stroke(width = 0.1f * u, cap = StrokeCap.Round))
    drawPath(trunk, hide, style = Stroke(width = 0.075f * u, cap = StrokeCap.Round))
    // Tusk, eye with a glint, and a gold ornament on the forehead.
    drawPath(
        Path().apply { moveTo(head.x + 0.07f * u, head.y + 0.1f * u); quadraticTo(head.x + 0.2f * u, head.y + 0.2f * u, head.x + 0.26f * u, head.y + 0.1f * u) },
        Color(0xFFFFF8E7), style = Stroke(width = 0.035f * u, cap = StrokeCap.Round)
    )
    if (blinking(p, 9.5f)) {
        drawLine(Color(0xFF22252B), head + Offset(0.04f * u, -0.03f * u), head + Offset(0.08f * u, -0.03f * u), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
    } else {
        drawCircle(Color(0xFF22252B), 0.02f * u, head + Offset(0.06f * u, -0.03f * u))
        drawCircle(Color.White, 0.007f * u, head + Offset(0.066f * u, -0.036f * u))
    }
    drawPath(
        Path().apply {
            moveTo(head.x - 0.02f * u, head.y - 0.17f * u); lineTo(head.x + 0.1f * u, head.y - 0.13f * u)
            lineTo(head.x + 0.05f * u, head.y - 0.06f * u); close()
        },
        Color(0xFFF2B705)
    )
    drawCircle(Color(0xFFD93030), 0.014f * u, head + Offset(0.045f * u, -0.12f * u))
}

/** A paper plane folded from a scanned page, gliding to the right on a rising and falling path, a dotted trail behind. */
private fun DrawScope.paperPlane(p: Float, c: ScenePalette) {
    val u = size.height
    val span = size.width + 0.8f * u
    fun where(t: Float) = Offset(-0.4f * u + span * t, 0.46f * u + sin(t * 2f * PI.toFloat() * 2f) * 0.2f * u)
    val here = where(p)
    val ahead = where(p + 0.01f)
    for (i in 1..7) {
        val past = where(p - i * 0.016f)
        drawCircle(c.soft.copy(alpha = 0.5f * (1f - i / 8f)), 0.012f * u, past)
    }
    val tilt = atan2(ahead.y - here.y, ahead.x - here.x) * 180f / PI.toFloat()
    rotate(tilt, pivot = here) {
        val nose = here + Offset(0.2f * u, 0f)
        val top = here + Offset(-0.16f * u, -0.11f * u)
        val fold = here + Offset(-0.08f * u, 0.01f * u)
        val keel = here + Offset(-0.1f * u, 0.1f * u)
        val bottom = here + Offset(-0.16f * u, 0.04f * u)
        fun facet(a: Offset, b: Offset, d: Offset, colour: Color) = drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() }, colour)
        facet(nose, fold, bottom, Color(0xFFD5DAE2))
        facet(nose, fold, keel, Color(0xFFAEB6C2))
        facet(nose, top, fold, Color.White)
        // What was printed on the page, and the scan line, running along the wing.
        drawLine(c.brand.copy(alpha = 0.45f), here + Offset(-0.1f * u, -0.055f * u), here + Offset(0.02f * u, -0.02f * u), strokeWidth = 0.01f * u, cap = StrokeCap.Round)
        drawLine(ScanGreen, here + Offset(-0.12f * u, -0.035f * u), here + Offset(0.08f * u, -0.008f * u), strokeWidth = 0.016f * u, cap = StrokeCap.Round)
        drawPath(
            Path().apply { moveTo(nose.x, nose.y); lineTo(top.x, top.y); lineTo(fold.x, fold.y); lineTo(keel.x, keel.y); lineTo(nose.x, nose.y); lineTo(bottom.x, bottom.y); lineTo(fold.x, fold.y) },
            c.brand, style = Stroke(width = 0.01f * u)
        )
    }
}

/** Whether a character's eyes are shut at [p] of a pass lasting [seconds]: a quick blink about every three seconds. */
private fun blinking(p: Float, seconds: Float): Boolean = (p * seconds / 3.1f) % 1f in 0.6f..0.645f
