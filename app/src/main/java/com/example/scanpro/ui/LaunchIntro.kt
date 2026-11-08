package com.example.scanpro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ManropeFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * The launch animation, drawn from the Figma design "Launch v2" (file wlMBDKuD8B4ojE2RjQZWYi: frame 2:2 holds the
 * keyframes, 6:2 the written spec). The Figma timeline ran 1.6 s and read as a blink on a phone (owner, 2026-11-08), so
 * every keyframe below is that timeline stretched about 1.4x, with the wordmark held long enough to be read; times are
 * seconds. It starts from exactly what the system has just shown (res/drawable/ic_launch_icon.xml: the app-icon tile
 * with a blank page, centred on the window colour):
 *   0.00-0.72  the icon dips and springs; the mint scan line sweeps down the page, the text lines write themselves in
 *   0.55-0.86  the brackets breathe out and snap tight; a white halo ripples out
 *   0.66-1.24  an indigo-azure-teal flood grows out of the icon until it fills the screen; soft lights drift in
 *   0.92-1.52  "ScanPro" and the tagline rise into place and hold
 *   1.52-1.78  the icon zooms through and is gone before the app shows
 *   1.66-2.25  the colour crossfades into the app, which settles from 104 % (MainActivity, started by onReveal)
 * A tap skips to the zoom. One clock drives everything and is read only while drawing, so nothing recomposes per frame.
 */

/** Exit start: where a tap skips to. */
private const val EXIT_AT = 1.52f
/** The app starts to show and settle. */
private const val REVEAL_AT = 1.72f
private const val END_AT = 2.25f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
/** Material 3 emphasized decelerate / accelerate. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Hold = Easing { if (it >= 1f) 1f else 0f }

/** A keyframe: [value] at [time] seconds, reached from the previous keyframe with [easing]. */
private class K(val time: Float, val value: Float, val easing: Easing = LinearEasing)

/** Value of a keyframe track at [t]; holds the first value before it starts and the last after it ends. */
private fun Array<K>.at(t: Float): Float {
    if (t <= this[0].time) return this[0].value
    for (i in 1 until size) {
        val k = this[i]
        if (t <= k.time) {
            val p = this[i - 1]
            return p.value + (k.value - p.value) * k.easing.transform((t - p.time) / (k.time - p.time))
        }
    }
    return last().value
}

private val IconScale = arrayOf(K(0f, 1f), K(0.1f, 0.92f, EaseOut), K(0.5f, 1.06f, EmphasizedDecelerate), K(0.72f, 1f, EaseInOut), K(1.52f, 1f), K(1.78f, 5f, EmphasizedAccelerate))
private val IconAlpha = arrayOf(K(1.56f, 1f), K(1.74f, 0f, EaseIn))
private val ShadowAlpha = arrayOf(K(0f, 0f), K(0.18f, 1f, EaseOut))
/** Scan line and wake offset, in px of the 144 px icon. */
private val Sweep = arrayOf(K(0f, -34f), K(0.08f, -34f), K(0.6f, 32.7f, EaseInOut), K(0.9f, 0f, EaseOutBack))
private val ScanAlpha = arrayOf(K(0f, 0f), K(0.1f, 1f, EaseOut))
private val WakeAlpha = arrayOf(K(0f, 0f), K(0.12f, 1f, EaseOut), K(0.6f, 1f), K(0.8f, 0f, EaseOut))
private val Line1Alpha = arrayOf(K(0f, 0f), K(0.28f, 1f, Hold))
private val Line1Width = arrayOf(K(0.28f, 1f), K(0.48f, 25.33f, EaseOut))
private val Line2Alpha = arrayOf(K(0f, 0f), K(0.4f, 1f, Hold))
private val Line2Width = arrayOf(K(0.4f, 1f), K(0.62f, 32f, EaseOut))
private val BracketScale = arrayOf(K(0f, 1f), K(0.55f, 1.18f, EaseOut), K(0.86f, 1f, EaseOutBack))
private val HaloAlpha = arrayOf(K(0f, 0f), K(0.78f, 0f), K(0.84f, 0.65f, EaseOut), K(1.12f, 0f, EaseOut))
private val HaloScale = arrayOf(K(0.78f, 1f), K(1.12f, 1.9f, EaseOut))
/** Flood growth: 0 = hidden behind the icon, 1 = covering the screen. */
private val FloodGrowth = arrayOf(K(0f, 0f), K(0.66f, 0f), K(1.24f, 1f, EmphasizedDecelerate))
private val FloodAlpha = arrayOf(K(1.66f, 1f), K(2.02f, 0f, EaseInOut))
private val LightAlpha = arrayOf(K(0f, 0f), K(0.78f, 0f), K(1.3f, 1f, EaseOut), K(1.66f, 1f), K(2.02f, 0f, EaseInOut))
private val LightDrift = arrayOf(K(0.76f, 0f), K(2.05f, 1f, EaseOut))
private val WordAlpha = arrayOf(K(0f, 0f), K(0.92f, 0f), K(1.18f, 1f, EaseOut), K(1.52f, 1f), K(1.7f, 0f, EaseIn))
private val WordRise = arrayOf(K(0.92f, 18f), K(1.26f, 0f, EmphasizedDecelerate), K(1.52f, 0f), K(1.7f, -10f, EaseIn))
private val TagAlpha = arrayOf(K(0f, 0f), K(1.04f, 0f), K(1.28f, 1f, EaseOut), K(1.54f, 1f), K(1.7f, 0f, EaseIn))
private val TagRise = arrayOf(K(1.04f, 12f), K(1.32f, 0f, EmphasizedDecelerate), K(1.54f, 0f), K(1.7f, -8f, EaseIn))
private val BackgroundAlpha = arrayOf(K(1.72f, 1f), K(2.02f, 0f, EaseOut))

// The launcher glyph, in its 108-unit grid (res/drawable/ic_launcher_foreground.xml), drawn over the whole tile.
private fun path(d: String) = PathParser().parsePathString(d).toPath()
private val SheetShadow = path("M37,29h26l12,12v38.5a5,5 0 0,1 -5,5h-33a5,5 0 0,1 -5,-5v-45.5a5,5 0 0,1 5,-5z")
private val Sheet = path("M36,26h26l12,12v40a5,5 0 0,1 -5,5h-33a5,5 0 0,1 -5,-5v-47a5,5 0 0,1 5,-5z")
private val Fold = path("M62,26v8a4,4 0 0,0 4,4h8z")
private val Brackets = listOf("M32,20h-8a4,4 0 0,0 -4,4v8", "M76,20h8a4,4 0 0,1 4,4v8", "M32,88h-8a4,4 0 0,1 -4,-4v-8", "M76,88h8a4,4 0 0,0 4,-4v-8").map(::path)
private val BracketStroke = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val Indigo = Color(0xFF3346D3)
private val IconEnd = Color(0xFF6E5BE6)
private val Mint = Color(0xFF2FD6A6)
private val FoldTint = Color(0xFFC9CEFF)
private val Ink = Indigo.copy(alpha = 0.55f)
private val IconShadow = Color(0xFF1B2AA8)
/** The flood: deep indigo through the brand indigo and azure to teal, with the mint of the scan line in it. */
private val FloodStops = arrayOf(0f to Color(0xFF2B37B8), 0.4f to Color(0xFF4356E0), 0.72f to Color(0xFF3D7CF2), 1f to Color(0xFF2BC0B4))

/** A soft light: centre and radius in the 412 x 892 design frame, its colour, and where it drifts to. */
private class Light(val x: Float, val y: Float, val r: Float, val color: Color, val dx: Float, val dy: Float)
private val Lights = listOf(
    Light(40f, 170f, 210f, Mint.copy(alpha = 0.5f), 30f, 24f),
    Light(400f, 70f, 190f, Color(0xFF7DD3FC).copy(alpha = 0.45f), -26f, 20f),
    Light(310f, 840f, 230f, Color(0xFFC7D2FE).copy(alpha = 0.5f), 20f, -30f)
)

private val IconSize = 144.dp
private val IconCorner = 38.dp

/**
 * Draws the launch animation over the app. [dark] is the app's own theme (the launch screen the system drew just
 * before is in the same mode from the second start on: MainActivity tells Android the app's choice). [onReveal] runs
 * once as the app starts to show (the caller settles the app into place then), [onFinished] once the animation is
 * over and can be removed.
 */
@Composable
fun LaunchIntro(dark: Boolean, onReveal: () -> Unit, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val background = colorResource(if (dark) R.color.scanpro_window_dark else R.color.scanpro_window_light)
    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reveal by rememberUpdatedState(onReveal)
    val finished by rememberUpdatedState(onFinished)
    val state = remember { object { var revealed = false; var job: Job? = null } }

    fun play(from: Float) {
        state.job?.cancel()
        state.job = scope.launch {
            clock.snapTo(from)
            suspend fun runTo(target: Float) {
                val ms = ((target - clock.value) * 1000f).roundToInt()
                if (ms > 0) clock.animateTo(target, tween(ms, easing = LinearEasing))
            }
            runTo(REVEAL_AT)
            if (!state.revealed) {
                state.revealed = true
                reveal()
            }
            runTo(END_AT)
            finished()
        }
    }
    LaunchedEffect(Unit) { play(0f) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // A tap skips to the zoom; until the animation is over it also keeps taps off the app underneath.
            .pointerInput(Unit) { detectTapGestures { if (clock.value < EXIT_AT) play(EXIT_AT) } }
            .clearAndSetSemantics { }
    ) {
        val textTop = maxHeight / 2 + IconSize / 2 + 26.dp
        Canvas(Modifier.fillMaxSize()) { drawIntro(clock.value, background) }
        Column(
            Modifier.fillMaxWidth().offset(y = textTop),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ScanPro",
                style = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp),
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    val t = clock.value
                    alpha = WordAlpha.at(t)
                    translationY = WordRise.at(t).dp.toPx()
                }
            )
            Text(
                stringResource(R.string.launch_tagline),
                style = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val t = clock.value
                    alpha = TagAlpha.at(t)
                    // The tagline sits 56dp below the wordmark's top in the design; the column already stacks them.
                    translationY = TagRise.at(t).dp.toPx()
                }
            )
        }
    }
}

private fun DrawScope.drawIntro(t: Float, background: Color) {
    val w = size.width
    val h = size.height
    val c = center
    val sx = w / 412.dp.toPx()
    val sy = h / 892.dp.toPx()
    drawRect(background.copy(alpha = BackgroundAlpha.at(t)))

    // Colour flood: from just behind the icon to past the screen corners.
    val growth = FloodGrowth.at(t)
    val floodAlpha = FloodAlpha.at(t)
    if (growth > 0f && floodAlpha > 0f) {
        val start = 65.dp.toPx()
        val r = start + (hypot(w / 2f, h / 2f) * 1.02f - start) * growth
        drawCircle(
            Brush.linearGradient(*FloodStops, start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)),
            radius = r,
            center = c,
            alpha = floodAlpha
        )
    }

    // Soft lights drifting over the colour.
    val lightAlpha = LightAlpha.at(t)
    if (lightAlpha > 0f) {
        val drift = LightDrift.at(t)
        Lights.forEach { l ->
            val centre = Offset((l.x + l.dx * drift).dp.toPx() * sx, (l.y + l.dy * drift).dp.toPx() * sy)
            val radius = l.r.dp.toPx() * sx
            drawCircle(Brush.radialGradient(listOf(l.color, Color.Transparent), centre, radius), radius, centre, alpha = lightAlpha)
        }
    }

    val icon = IconSize.toPx()
    // Lock-on halo.
    val haloAlpha = HaloAlpha.at(t)
    if (haloAlpha > 0f) {
        drawCircle(Color.White, radius = icon / 2f * HaloScale.at(t), center = c, alpha = haloAlpha, style = Stroke(2.dp.toPx()))
    }

    val iconAlpha = IconAlpha.at(t)
    if (iconAlpha <= 0f) return
    val scale = IconScale.at(t)
    val topLeft = Offset(c.x - icon / 2f, c.y - icon / 2f)
    val corner = CornerRadius(IconCorner.toPx())
    val tile = Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + icon, topLeft.y + icon, corner)) }
    withTransform({ scale(scale, scale, c) }) {
        // Shadow: soft rings fading outwards under the tile, faded in after the hand-over from the system's icon.
        val shadow = ShadowAlpha.at(t) * iconAlpha
        if (shadow > 0f) {
            val spread = 15.dp.toPx()
            val drop = 14.dp.toPx()
            for (i in 1..6) {
                val grow = spread * i / 6f
                drawRoundRect(
                    IconShadow,
                    topLeft = Offset(topLeft.x - grow, topLeft.y + drop - grow),
                    size = Size(icon + 2 * grow, icon + 2 * grow),
                    cornerRadius = CornerRadius(corner.x + grow),
                    alpha = 0.06f * shadow
                )
            }
        }
        drawPath(tile, Brush.linearGradient(listOf(Indigo, IconEnd), topLeft, Offset(topLeft.x + icon, topLeft.y + icon)), alpha = iconAlpha)
        clipPath(tile) {
            translate(topLeft.x, topLeft.y) { drawIconContents(t, icon, iconAlpha) }
        }
    }
}

/** Everything inside the tile, in tile coordinates ([icon] px square). */
private fun DrawScope.drawIconContents(t: Float, icon: Float, alpha: Float) {
    val u = icon / 108f
    val px = icon / 144f
    withTransform({ scale(u, u, Offset.Zero) }) {
        drawPath(SheetShadow, Color.Black, alpha = 0.18f * alpha)
        drawPath(Sheet, Color.White, alpha = alpha)
        drawPath(Fold, FoldTint, alpha = alpha)
    }
    // Text lines, written in from the left as the scan line passes.
    fun line(y: Float, width: Float, lineAlpha: Float) {
        if (lineAlpha <= 0f) return
        drawRoundRect(Ink, Offset(41f * u, y * u), Size(width * px, 4f * u), CornerRadius(2f * u), alpha = lineAlpha * alpha)
    }
    line(44f, Line1Width.at(t), Line1Alpha.at(t))
    line(63f, Line2Width.at(t), Line2Alpha.at(t))

    val dy = Sweep.at(t) * px
    val wake = WakeAlpha.at(t)
    if (wake > 0f) {
        val top = 39.5f * u + dy
        drawRect(
            Brush.verticalGradient(listOf(Mint.copy(alpha = 0f), Mint.copy(alpha = 0.45f)), startY = top, endY = top + 16f * u),
            topLeft = Offset(30f * u, top),
            size = Size(44f * u, 16f * u),
            alpha = wake * alpha
        )
    }
    val scan = ScanAlpha.at(t) * alpha
    if (scan > 0f) {
        val top = 53f * u + dy
        // A soft mint glow behind the line, then the line.
        for (i in 1..3) {
            val g = i * 1.6f * u
            drawRoundRect(Mint, Offset(21.5f * u - g, top - g), Size(65f * u + 2 * g, 5f * u + 2 * g), CornerRadius(2.5f * u + g), alpha = 0.12f * scan)
        }
        drawRoundRect(Mint, Offset(21.5f * u, top), Size(65f * u, 5f * u), CornerRadius(2.5f * u), alpha = scan)
    }
    // Brackets: breathe out, then snap tight around the page.
    val b = BracketScale.at(t)
    withTransform({
        scale(b, b, Offset(icon / 2f, icon / 2f))
        scale(u, u, Offset.Zero)
    }) {
        Brackets.forEach { drawPath(it, Color.White, alpha = alpha, style = BracketStroke) }
    }
}
