package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin

/*
 * Tool demonstrations (F195): a short looping scene for each tool that shows what it does to a real-looking page,
 * on a slightly tilted 3D "desk" so layers can lift off the page and settle back. Shown in the tool's introduction
 * and in the "How it works" card at the top of its page. Every scene is drawn with the shared pieces below, in the
 * brand colours, reads the frame clock only in the draw phase (it redraws without recomposing), and holds a
 * meaningful still frame when animations are turned off.
 *
 * To add a scene: write a `DemoScene` with a period and a draw block, and add the tool id to `DEMO_SCENES`.
 */

/** Colours for the scenes, resolved once from the theme. */
class DemoColours(
    val paper: Color,
    val ink: Color,
    val inkSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val mint: Color,
    val redact: Color,
    val highlight: Color,
    val shadow: Color,
    val surface: Color,
    val onSurface: Color,
)

/** What a scene draws with: where it is in its loop ([t], 0 to 1), the colours and a text measurer. */
class DemoFrame(val t: Float, val c: DemoColours, val text: TextMeasurer, val still: Boolean)

/** One tool's scene. [tilt] puts it on the 3D desk; flat scenes (a dialog-like picture) leave it off. */
class DemoScene(val periodMillis: Long, val tilt: Boolean = true, val draw: DrawScope.(DemoFrame) -> Unit)

/** The scene for a tool id, or null when the tool has none yet (its introduction then shows the general picture). */
fun toolDemoScene(toolId: String): DemoScene? = DEMO_SCENES[toolId]

/**
 * Scenes by tool id. Lazy: top-level values are set in file order, and the scenes are declared further down (and in
 * other files), so a plain map here would hold nulls.
 */
private val DEMO_SCENES: Map<String, DemoScene> by lazy {
    mapOf(
        "redact" to RedactTextScene,
        "redact_box" to RedactAreasScene,
        "flatten" to FlattenScene,
        "header_footer" to HeaderFooterScene,
    ) + PAGE_TOOL_SCENES + EDIT_TOOL_SCENES + CONVERT_TOOL_SCENES
}

/**
 * Plays [scene] in a loop. The desk tilts back a little and sways slowly, so the page reads as a sheet lying on a
 * table rather than a flat icon. With animations off it shows the frame at [stillAt].
 */
@Composable
fun ToolDemo(scene: DemoScene, modifier: Modifier = Modifier, stillAt: Float = 0.62f) {
    val motion = rememberMotionEnabled()
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val clock = remember { mutableLongStateOf(-1L) }
    // The stage flips up into place when it first appears, like a sheet laid down on a desk.
    val entrance = remember(scene) { androidx.compose.animation.core.Animatable(if (motion && !inspection) 0f else 1f) }
    if (motion && !inspection) {
        LaunchedEffect(scene) {
            entrance.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 170f))
        }
        LaunchedEffect(scene) {
            var start = -1L
            while (true) withInfiniteAnimationFrameMillis { now ->
                if (start < 0) start = now
                clock.longValue = now - start
            }
        }
    }
    val colours = rememberDemoColours()
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier.graphicsLayer {
            val e = entrance.value
            alpha = e.coerceIn(0f, 1f)
            if (scene.tilt) {
                val elapsed = clock.longValue
                val sway = if (elapsed < 0) 0f else sin(elapsed / 2600.0 * 2 * PI).toFloat()
                // A slow camera: a little orbit around the desk and a gentle push in and out.
                val dolly = if (elapsed < 0) 0f else sin(elapsed / 4100.0 * 2 * PI).toFloat()
                rotationX = 16f + 46f * (1f - e)
                rotationY = -8f + 5f * sway
                scaleX = (0.9f + 0.1f * e) * (1f + 0.015f * dolly)
                scaleY = scaleX
                cameraDistance = 14f * density
            } else {
                scaleX = 0.94f + 0.06f * e
                scaleY = scaleX
            }
        }
    ) {
        val elapsed = clock.longValue
        val t = if (elapsed < 0) stillAt else (elapsed % scene.periodMillis).toFloat() / scene.periodMillis
        if (scene.tilt) drawStage(colours)
        scene.draw(this, DemoFrame(t, colours, measurer, still = elapsed < 0))
    }
}

/** The desk the scene sits on: a faint spotlight from above and a soft shadow on the floor, so the pages stand on something. */
private fun DrawScope.drawStage(c: DemoColours) {
    val centre = Offset(size.width / 2f, size.height * 0.52f)
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.accent.copy(alpha = 0.07f), Color.Transparent), center = centre, radius = size.minDimension * 0.75f),
        radius = size.minDimension * 0.75f,
        center = centre
    )
    val floor = Offset(size.width / 2f, size.height * 0.93f)
    scale(1f, 0.18f, pivot = floor) {
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), center = floor, radius = size.width * 0.42f),
            radius = size.width * 0.42f,
            center = floor
        )
    }
}

@Composable
fun rememberDemoColours(): DemoColours {
    val brand = ScanProTheme.colors
    val scheme = MaterialTheme.colorScheme
    return remember(brand, scheme) {
        DemoColours(
            paper = brand.paper,
            ink = brand.ink,
            inkSoft = brand.ink.copy(alpha = 0.45f),
            accent = brand.gradientStart,
            accentSoft = brand.gradientStart.copy(alpha = 0.18f),
            mint = brand.scanLine,
            redact = Color(0xFF121317),
            highlight = Color(0xFFFFD54F),
            shadow = Color.Black.copy(alpha = 0.16f),
            surface = scheme.surface,
            onSurface = scheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------------------

/** 0 before [from], 1 after [to], eased in between: the building block of every timeline. */
fun seg(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** 1 while [t] is inside [from]..[to], fading over [fade] at each end. */
fun window(t: Float, from: Float, to: Float, fade: Float = 0.04f): Float =
    seg(t, from, from + fade) * (1f - seg(t, to - fade, to))

fun mixf(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** An A4-shaped page of [height] centred on [centre]. */
fun pageAt(centre: Offset, height: Float): Rect {
    val width = height * 0.72f
    return Rect(centre.x - width / 2f, centre.y - height / 2f, centre.x + width / 2f, centre.y + height / 2f)
}

/** The page most scenes use: centred, as tall as the stage allows. */
fun DrawScope.mainPage(scale: Float = 0.78f, dx: Float = 0f, dy: Float = 0f): Rect =
    pageAt(Offset(size.width / 2f + dx, size.height / 2f + dy), size.height * scale)

/**
 * A sheet of paper lying on the desk: a soft shadow built from layers (wider and fainter the higher [lift] raises the
 * sheet), the paper's edge, light falling from the top left, and a folded corner.
 */
fun DrawScope.drawPaper(page: Rect, c: DemoColours, lift: Float = 0f, alpha: Float = 1f, paper: Color = c.paper) {
    val corner = CornerRadius(page.width * 0.04f)
    val drop = page.height * (0.02f + 0.06f * lift)
    val spread = page.width * (0.012f + 0.03f * lift)
    // Soft shadow: four layers, each a little wider and fainter, approximate a blur without a render effect.
    for (k in 0 until 4) {
        val grow = spread * k
        val layerAlpha = c.shadow.alpha * alpha * (0.42f - k * 0.09f) * (1f - 0.3f * lift)
        drawRoundRect(
            c.shadow.copy(alpha = layerAlpha.coerceAtLeast(0f)),
            Offset(page.left - grow + drop * 0.35f, page.top - grow + drop),
            Size(page.width + grow * 2f, page.height + grow * 2f),
            CornerRadius(corner.x + grow)
        )
    }
    // The paper's thickness: a sliver of darker edge along the bottom and right.
    val edge = page.height * 0.008f
    drawRoundRect(lerp(paper, Color.Black, 0.18f).copy(alpha = paper.alpha * alpha), page.topLeft + Offset(edge * 0.6f, edge), page.size, corner)
    drawRoundRect(paper.copy(alpha = paper.alpha * alpha), page.topLeft, page.size, corner)
    // Light from the top left: a touch brighter there, a touch darker at the far corner.
    drawRoundRect(
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.16f * alpha), Color.Transparent, Color.Black.copy(alpha = 0.05f * alpha)),
            start = page.topLeft,
            end = page.bottomRight
        ),
        page.topLeft, page.size, corner
    )
    drawRoundRect(c.ink.copy(alpha = 0.10f * alpha), page.topLeft, page.size, corner, style = Stroke(1f))
    val fold = page.width * 0.16f
    val path = Path().apply {
        moveTo(page.right - fold, page.top)
        lineTo(page.right, page.top + fold)
        lineTo(page.right - fold, page.top + fold)
        close()
    }
    drawPath(path, c.ink.copy(alpha = 0.10f * alpha))
    // The fold's own small shadow on the page.
    drawLine(c.ink.copy(alpha = 0.08f * alpha), Offset(page.right - fold, page.top + fold), Offset(page.right, page.top + fold), 1.5f)
}

/** Relative widths of the text lines on a page. */
val DEMO_LINES = floatArrayOf(1f, 0.86f, 0.94f, 0.7f, 0.9f, 0.62f, 0.82f)

/** The y of text line [index] on [page], between a top and bottom margin. */
fun lineY(page: Rect, index: Int, count: Int = DEMO_LINES.size, top: Float = 0.24f, bottom: Float = 0.82f): Float =
    page.top + page.height * (top + (bottom - top) * index / (count - 1).coerceAtLeast(1))

/** The left and right x of text line [index]. */
fun lineSpan(page: Rect, index: Int): Pair<Float, Float> {
    val left = page.left + page.width * 0.14f
    val full = page.width * 0.72f
    return left to left + full * DEMO_LINES[index % DEMO_LINES.size]
}

/** Text lines and a heading bar on [page]; [skip] leaves out lines a scene draws itself. */
fun DrawScope.drawTextLines(page: Rect, c: DemoColours, alpha: Float = 1f, count: Int = DEMO_LINES.size, heading: Boolean = true, skip: Set<Int> = emptySet()) {
    val stroke = page.height * 0.022f
    if (heading) {
        val y = page.top + page.height * 0.13f
        drawLine(c.accent.copy(alpha = 0.75f * alpha), Offset(page.left + page.width * 0.14f, y), Offset(page.left + page.width * 0.52f, y), stroke * 1.35f, StrokeCap.Round)
    }
    for (i in 0 until count) {
        if (i in skip) continue
        val y = lineY(page, i, count)
        val (l, r) = lineSpan(page, i)
        drawLine(c.inkSoft.copy(alpha = c.inkSoft.alpha * alpha), Offset(l, y), Offset(r, y), stroke, StrokeCap.Round)
    }
}

/** A fingertip touch point, as screen recordings show touches; smaller while [pressed]. */
fun DrawScope.drawFinger(at: Offset, c: DemoColours, alpha: Float, pressed: Boolean = false) {
    if (alpha <= 0f) return
    val r = size.minDimension * 0.045f * (if (pressed) 0.85f else 1f)
    drawCircle(c.onSurface.copy(alpha = 0.40f * alpha), r, at)
    drawCircle(c.surface.copy(alpha = 0.9f * alpha), r, at, style = Stroke(r * 0.18f))
}

/** A short label in a pill, centred on [at]. */
fun DrawScope.drawPill(text: String, at: Offset, c: DemoColours, frame: DemoFrame, alpha: Float = 1f, background: Color = c.accent, foreground: Color = Color.White) {
    if (alpha <= 0f) return
    val style = TextStyle(fontSize = (size.minDimension * 0.055f / density).sp, fontWeight = FontWeight.SemiBold, color = foreground.copy(alpha = alpha))
    val layout = frame.text.measure(text, style)
    val padX = layout.size.height * 0.45f
    val box = Size(layout.size.width + padX * 2, layout.size.height * 1.25f)
    val topLeft = Offset(at.x - box.width / 2f, at.y - box.height / 2f)
    drawRoundRect(background.copy(alpha = background.alpha * alpha), topLeft, box, CornerRadius(box.height / 2f))
    drawText(layout, topLeft = Offset(topLeft.x + padX, topLeft.y + (box.height - layout.size.height) / 2f))
}

/** Plain text at [topLeft], sized as a fraction of the stage. */
fun DrawScope.drawSmallText(text: String, topLeft: Offset, colour: Color, frame: DemoFrame, scale: Float = 0.045f, bold: Boolean = false, maxWidth: Float? = null) {
    val style = TextStyle(fontSize = (size.minDimension * scale / density).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = colour)
    val layout = frame.text.measure(text, style, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (maxWidth ?: size.width).toInt().coerceAtLeast(1)))
    drawText(layout, topLeft = topLeft)
}

/** A tick in a mint circle: "done". */
fun DrawScope.drawDoneBadge(centre: Offset, radius: Float, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(c.mint.copy(alpha = alpha), radius * (0.8f + 0.2f * alpha), centre)
    val tick = Path().apply {
        moveTo(centre.x - radius * 0.42f, centre.y + radius * 0.02f)
        lineTo(centre.x - radius * 0.1f, centre.y + radius * 0.34f)
        lineTo(centre.x + radius * 0.45f, centre.y - radius * 0.3f)
    }
    drawPath(tick, Color(0xFF04281E).copy(alpha = alpha), style = Stroke(radius * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The mint scan line passing over [page] at [y], with its glow. */
fun DrawScope.drawScanPass(page: Rect, y: Float, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    clipRect(page.left, page.top, page.right, page.bottom) {
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.mint.copy(alpha = 0.3f * alpha)), startY = y - page.height * 0.12f, endY = y),
            topLeft = Offset(page.left, y - page.height * 0.12f),
            size = Size(page.width, page.height * 0.12f)
        )
    }
    drawLine(c.mint.copy(alpha = alpha), Offset(page.left - page.width * 0.04f, y), Offset(page.right + page.width * 0.04f, y), page.height * 0.014f, StrokeCap.Round)
}

// ---------------------------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------------------------

/**
 * Redact text: a search for a word, every match on the page lights up, black bars sweep over them, then the bars
 * lift for a moment to show the words are gone from underneath (not just covered).
 */
val RedactTextScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(dy = size.height * 0.04f)
    drawPaper(page, c)
    // Words that match, as (line, start fraction, end fraction).
    val matches = listOf(Triple(1, 0.30f, 0.62f), Triple(3, 0.05f, 0.42f), Triple(5, 0.48f, 0.95f))
    drawTextLines(page, c)
    val stroke = page.height * 0.05f
    matches.forEachIndexed { i, (line, a, b) ->
        val y = lineY(page, line)
        val (l, r) = lineSpan(page, line)
        val x0 = mixf(l, r, a)
        val x1 = mixf(l, r, b)
        // Found: a highlight that pops in one match after another.
        val found = seg(t, 0.18f + i * 0.06f, 0.24f + i * 0.06f) * (1f - seg(t, 0.52f, 0.56f))
        if (found > 0f) drawRoundRect(c.highlight.copy(alpha = 0.75f * found), Offset(x0 - stroke * 0.2f, y - stroke / 2f), Size((x1 - x0) + stroke * 0.4f, stroke), CornerRadius(stroke * 0.3f))
        // The bar sweeps across.
        val sweep = seg(t, 0.40f + i * 0.05f, 0.52f + i * 0.05f)
        // At the end it lifts and shows the gap beneath: the words were removed, not hidden.
        val lift = window(t, 0.74f, 0.9f, 0.05f)
        if (sweep > 0f) {
            val barTop = y - stroke / 2f - lift * page.height * 0.07f
            if (lift > 0f) {
                // Paper where the words were.
                drawRect(c.paper, Offset(x0 - stroke * 0.2f, y - stroke * 0.6f), Size((x1 - x0) + stroke * 0.4f, stroke * 1.2f))
                drawRoundRect(c.shadow.copy(alpha = 0.2f * lift), Offset(x0, barTop + stroke * 1.2f), Size(x1 - x0, stroke * 0.5f), CornerRadius(stroke * 0.25f))
            }
            drawRoundRect(c.redact, Offset(x0 - stroke * 0.2f, barTop), Size(((x1 - x0) + stroke * 0.4f) * sweep, stroke), CornerRadius(stroke * 0.15f))
        }
    }
    // The search box above the page.
    val search = window(t, 0.04f, 0.6f)
    drawPill("Name", Offset(size.width / 2f, page.top - page.height * 0.02f), c, f, search, background = c.surface, foreground = c.onSurface)
    drawDoneBadge(Offset(page.right - page.width * 0.08f, page.bottom - page.width * 0.08f), page.width * 0.1f, c, seg(t, 0.62f, 0.68f) * (1f - seg(t, 0.94f, 0.99f)))
}

/**
 * Redact areas: a finger drags a box over a block of text, the box fills black, then a second box over the photo.
 */
val RedactAreasScene = DemoScene(5_000) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage()
    drawPaper(page, c)
    drawTextLines(page, c, count = 4, top = 0.22f, bottom = 0.44f)
    // A photo block in the lower half.
    val photo = Rect(page.left + page.width * 0.14f, page.top + page.height * 0.56f, page.left + page.width * 0.56f, page.top + page.height * 0.82f)
    drawRoundRect(c.accentSoft, photo.topLeft, photo.size, CornerRadius(page.width * 0.02f))
    drawCircle(c.accent.copy(alpha = 0.45f), photo.height * 0.18f, Offset(photo.left + photo.width * 0.3f, photo.top + photo.height * 0.35f))
    val hill = Path().apply {
        moveTo(photo.left, photo.bottom)
        lineTo(photo.left + photo.width * 0.45f, photo.top + photo.height * 0.45f)
        lineTo(photo.right, photo.bottom)
        close()
    }
    drawPath(hill, c.accent.copy(alpha = 0.35f))
    for (i in 0..2) {
        val y = photo.top + photo.height * (0.2f + i * 0.3f)
        drawLine(c.inkSoft, Offset(photo.right + page.width * 0.05f, y), Offset(page.right - page.width * 0.14f, y), page.height * 0.02f, StrokeCap.Round)
    }

    data class Box(val area: Rect, val start: Float)
    val boxes = listOf(
        Box(Rect(page.left + page.width * 0.1f, lineY(page, 1, 4, 0.22f, 0.44f) - page.height * 0.03f, page.left + page.width * 0.8f, lineY(page, 2, 4, 0.22f, 0.44f) + page.height * 0.03f), 0.08f),
        Box(photo.inflate(page.width * 0.02f), 0.48f)
    )
    var finger: Offset? = null
    var fingerAlpha = 0f
    var pressed = false
    boxes.forEach { (area, start) ->
        val arrive = seg(t, start, start + 0.06f)
        val drag = seg(t, start + 0.08f, start + 0.24f)
        val fill = seg(t, start + 0.26f, start + 0.31f)
        val leave = seg(t, start + 0.30f, start + 0.36f)
        if (drag > 0f) {
            val current = Rect(area.left, area.top, mixf(area.left, area.right, drag), mixf(area.top, area.bottom, drag))
            if (fill < 1f) drawRect(c.accent.copy(alpha = 0.9f * (1f - fill)), current.topLeft, current.size, style = Stroke(page.width * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
            drawRect(c.redact.copy(alpha = fill), current.topLeft, current.size)
        }
        if (t in start..(start + 0.36f)) {
            val at = Offset(mixf(area.left, area.right, drag), mixf(area.top, area.bottom, drag))
            finger = Offset(mixf(at.x + page.width * 0.3f, at.x, arrive), mixf(at.y + page.height * 0.2f, at.y, arrive))
            fingerAlpha = arrive * (1f - leave)
            pressed = drag in 0.01f..0.99f
        }
    }
    // Fades out together before the loop starts again, so the page "resets".
    val reset = 1f - seg(t, 0.9f, 0.97f)
    if (reset < 1f) drawRect(c.surface.copy(alpha = 1f - reset), page.inflate(page.width * 0.1f).topLeft, page.inflate(page.width * 0.1f).size)
    finger?.let { drawFinger(it, c, fingerAlpha, pressed) }
}

/**
 * Flatten: the page in an exploded view, with its form fields, a sticky note and a signature floating above it as
 * separate layers; they settle onto the page and become part of it, and the "editable" cursor disappears.
 */
val FlattenScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.62f, dy = size.height * 0.14f)
    // How far apart the layers float: open at the start, closed by 0.6, open again at the very end of the loop.
    val open = 1f - seg(t, 0.32f, 0.6f) + seg(t, 0.93f, 1f)
    val gap = page.height * 0.2f * open
    drawPaper(page, c)
    drawTextLines(page, c, count = 5, top = 0.2f, bottom = 0.5f)

    // Layer 1: form fields, two boxes with typed text and a caret.
    val formLift = gap
    val fieldA = Rect(page.left + page.width * 0.14f, page.top + page.height * 0.6f, page.left + page.width * 0.86f, page.top + page.height * 0.68f).translate(Offset(0f, -formLift))
    val fieldB = Rect(page.left + page.width * 0.14f, page.top + page.height * 0.72f, page.left + page.width * 0.6f, page.top + page.height * 0.8f).translate(Offset(0f, -formLift))
    if (open > 0.02f) {
        // The layer's glass sheet, so the floating set reads as a layer.
        drawRoundRect(c.accent.copy(alpha = 0.08f * open), page.topLeft - Offset(0f, formLift), page.size, CornerRadius(page.width * 0.04f))
        drawRoundRect(c.accent.copy(alpha = 0.35f * open), page.topLeft - Offset(0f, formLift), page.size, CornerRadius(page.width * 0.04f), style = Stroke(1.2f))
    }
    listOf(fieldA, fieldB).forEach { field ->
        drawRoundRect(c.paper, field.topLeft, field.size, CornerRadius(page.width * 0.015f))
        drawRoundRect(c.accent.copy(alpha = 0.3f + 0.5f * open), field.topLeft, field.size, CornerRadius(page.width * 0.015f), style = Stroke(1.4f))
        drawLine(c.ink, Offset(field.left + field.width * 0.08f, field.center.y), Offset(field.left + field.width * 0.55f, field.center.y), field.height * 0.28f, StrokeCap.Round)
    }
    // The blinking caret: only while the fields are still editable.
    val blink = if (f.still) 1f else if ((t * 12f).toInt() % 2 == 0) 1f else 0f
    val editable = 1f - seg(t, 0.55f, 0.62f) + seg(t, 0.95f, 1f)
    drawLine(c.accent.copy(alpha = blink * editable), Offset(fieldA.left + fieldA.width * 0.6f, fieldA.top + fieldA.height * 0.2f), Offset(fieldA.left + fieldA.width * 0.6f, fieldA.bottom - fieldA.height * 0.2f), 2f)

    // Layer 2: a yellow note and a signature, higher still.
    val noteLift = gap * 2f
    val note = Rect(page.right - page.width * 0.36f, page.top + page.height * 0.06f, page.right - page.width * 0.06f, page.top + page.height * 0.3f).translate(Offset(0f, -noteLift))
    drawRoundRect(c.shadow.copy(alpha = 0.25f * open), note.topLeft + Offset(4f, 8f * open + 2f), note.size, CornerRadius(4f))
    drawRoundRect(c.highlight, note.topLeft, note.size, CornerRadius(4f))
    for (i in 0..2) drawLine(c.ink.copy(alpha = 0.5f), Offset(note.left + note.width * 0.15f, note.top + note.height * (0.3f + i * 0.2f)), Offset(note.right - note.width * 0.15f, note.top + note.height * (0.3f + i * 0.2f)), 2f, StrokeCap.Round)
    val sign = Path().apply {
        val y = page.top + page.height * 0.9f - noteLift
        val x = page.left + page.width * 0.56f
        val w = page.width * 0.32f
        moveTo(x, y)
        cubicTo(x + w * 0.2f, y - w * 0.35f, x + w * 0.3f, y + w * 0.25f, x + w * 0.5f, y - w * 0.1f)
        cubicTo(x + w * 0.65f, y - w * 0.3f, x + w * 0.8f, y + w * 0.15f, x + w, y - w * 0.05f)
    }
    drawPath(sign, c.accent, style = Stroke(page.width * 0.018f, cap = StrokeCap.Round))

    // The moment they merge: a flash across the page, then the "flattened" badge.
    val flash = window(t, 0.58f, 0.68f, 0.04f)
    if (flash > 0f) drawRoundRect(c.mint.copy(alpha = 0.35f * flash), page.topLeft, page.size, CornerRadius(page.width * 0.04f))
    drawDoneBadge(Offset(page.right, page.bottom), page.width * 0.11f, c, seg(t, 0.64f, 0.7f) * (1f - seg(t, 0.92f, 0.96f)))
}

/**
 * Header and footer: the text types itself into the top margin, the footer slides into the bottom one, then the page
 * fans out into three, each with the same header and its own page number.
 */
val HeaderFooterScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val fan = seg(t, 0.6f, 0.78f) * (1f - seg(t, 0.94f, 1f))
    val baseHeight = size.height * mixf(0.8f, 0.6f, fan)
    for (i in 2 downTo 0) {
        val shift = (i - 1) * size.width * 0.27f * fan
        if (i != 1 && fan <= 0.01f) continue
        val page = pageAt(Offset(size.width / 2f + shift, size.height / 2f), baseHeight)
        drawPaper(page, c, alpha = if (i == 1) 1f else fan)
        drawTextLines(page, c, alpha = if (i == 1) 1f else fan, count = 6, top = 0.26f, bottom = 0.76f)
        val marginAlpha = if (i == 1) 1f else fan
        // The margins, shown as dashed guides while the text goes in.
        val guides = window(t, 0.04f, 0.56f) * marginAlpha
        if (guides > 0f) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            drawLine(c.accent.copy(alpha = 0.5f * guides), Offset(page.left, page.top + page.height * 0.1f), Offset(page.right, page.top + page.height * 0.1f), 1.2f, pathEffect = dash)
            drawLine(c.accent.copy(alpha = 0.5f * guides), Offset(page.left, page.bottom - page.height * 0.1f), Offset(page.right, page.bottom - page.height * 0.1f), 1.2f, pathEffect = dash)
        }
        // Header: typed letter by letter on the middle page, already there on the others.
        val header = "Annual report 2026"
        val typed = if (i == 1) (header.length * seg(t, 0.08f, 0.32f)).toInt() else header.length
        if (typed > 0) {
            drawSmallText(header.take(typed), Offset(page.left + page.width * 0.1f, page.top + page.height * 0.03f), c.ink.copy(alpha = marginAlpha), f, scale = 0.04f * baseHeight / size.height / 0.8f, maxWidth = page.width * 0.8f)
        }
        if (i == 1 && typed in 1 until header.length && !f.still) {
            val caretX = page.left + page.width * (0.1f + 0.042f * typed)
            drawLine(c.accent, Offset(caretX.coerceAtMost(page.right), page.top + page.height * 0.03f), Offset(caretX.coerceAtMost(page.right), page.top + page.height * 0.08f), 2f)
        }
        // Footer: slides up into place.
        val slide = if (i == 1) seg(t, 0.34f, 0.48f) else 1f
        if (slide > 0f) {
            val footer = "Page ${i + 1} of 3"
            val y = page.bottom - page.height * 0.075f + (1f - slide) * page.height * 0.06f
            drawSmallText(footer, Offset(page.center.x - page.width * 0.2f, y), c.inkSoft.copy(alpha = marginAlpha * slide), f, scale = 0.036f * baseHeight / size.height / 0.8f, maxWidth = page.width * 0.6f)
        }
    }
}

/**
 * Files on this phone (the start prompt for "All files access", F196): a folder on the desk opens and the documents
 * already on the phone (a PDF, a Word file, a spreadsheet and a photo) rise out of it and fan out, each with its
 * colour badge, then settle into a tidy row with a tick.
 */
val FilesAccessScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val unit = size.height
    val folderW = unit * 0.62f
    val folderH = unit * 0.42f
    val folder = Rect(size.width / 2f - folderW / 2f, unit * 0.5f, size.width / 2f + folderW / 2f, unit * 0.5f + folderH)
    val open = seg(t, 0.08f, 0.22f) * (1f - seg(t, 0.9f, 0.98f))
    // Back of the folder, with its tab.
    val back = Color(0xFFF2B33D)
    val front = Color(0xFFF7C653)
    drawRoundRect(c.shadow, folder.topLeft + Offset(4f, unit * 0.03f), folder.size, CornerRadius(unit * 0.03f))
    drawRoundRect(back, Offset(folder.left, folder.top - unit * 0.05f), Size(folderW * 0.4f, unit * 0.08f), CornerRadius(unit * 0.02f))
    drawRoundRect(back, folder.topLeft, folder.size, CornerRadius(unit * 0.03f))
    // The documents: label, badge colour, final x position.
    val docs = listOf(
        Triple("PDF", Color(0xFFE5483B), -1.5f),
        Triple("DOC", Color(0xFF2F6FE4), -0.5f),
        Triple("XLS", Color(0xFF1E9E5A), 0.5f),
        Triple("JPG", c.accent, 1.5f),
    )
    docs.forEachIndexed { i, (label, colour, slot) ->
        val rise = seg(t, 0.18f + i * 0.07f, 0.42f + i * 0.07f) * (1f - seg(t, 0.86f, 0.95f))
        val h = unit * 0.36f
        val startCentre = Offset(size.width / 2f + slot * unit * 0.06f, folder.top + h * 0.3f)
        val endCentre = Offset(size.width / 2f + slot * unit * 0.3f, unit * 0.3f)
        val centre = Offset(mixf(startCentre.x, endCentre.x, rise), mixf(startCentre.y, endCentre.y, rise))
        val page = pageAt(centre, h)
        drawPaper(page, c, lift = rise)
        drawTextLines(page, c, count = 4, heading = false, top = 0.4f, bottom = 0.82f)
        drawPill(label, Offset(page.center.x, page.top + page.height * 0.2f), c, f, background = colour)
    }
    // Front of the folder, tilting open.
    val tilt = folderH * 0.18f * open
    val frontPath = Path().apply {
        moveTo(folder.left + tilt, folder.top + folderH * 0.18f + tilt)
        lineTo(folder.right - tilt, folder.top + folderH * 0.18f + tilt)
        lineTo(folder.right, folder.bottom)
        lineTo(folder.left, folder.bottom)
        close()
    }
    drawPath(frontPath, front)
    drawDoneBadge(Offset(folder.right - unit * 0.04f, folder.bottom - unit * 0.04f), unit * 0.07f, c, seg(t, 0.62f, 0.68f) * (1f - seg(t, 0.88f, 0.94f)))
}
