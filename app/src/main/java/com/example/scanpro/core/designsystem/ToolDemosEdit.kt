package com.example.scanpro.core.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/*
 * Tool demonstrations (F212) for: Redact, Redact areas, Flatten, Crop PDF, Header and footer, Watermark, Greyscale, Invert, Protect, Unlock, Sign.
 * Built with the shared pieces in ToolDemos.kt. Keep the map lazy (or at the bottom of the file): top-level values
 * are set in file order, so a map above the scenes it names would hold nulls.
 *
 * Each scene shows what the tool really does to the file: Crop moves the visible edges in (the content stays where it
 * is), Watermark stamps every page of the stack, Greyscale and Invert redraw the page with a sweep, Protect locks the
 * file behind a password (its contents scramble), Unlock lifts the lock and the text reads again, and Sign draws a
 * signature that is then laid onto the page's signature line.
 */

/** Scenes by tool id for this group; [toolDemoScene] looks here after ToolDemos.kt's own. */
internal val EDIT_TOOL_SCENES: Map<String, DemoScene> by lazy {
    mapOf(
        "crop_pdf" to CropPdfScene,
        "watermark" to WatermarkScene,
        "greyscale" to GreyscaleScene,
        "invert_pdf" to InvertScene,
        "protect" to ProtectScene,
        "unlock" to UnlockScene,
        "sign" to SignScene,
    )
}

private fun lerpRect(a: Rect, b: Rect, f: Float): Rect =
    Rect(mixf(a.left, b.left, f), mixf(a.top, b.top, f), mixf(a.right, b.right, f), mixf(a.bottom, b.bottom, f))

private fun greyOf(colour: Color): Color {
    val l = 0.299f * colour.red + 0.587f * colour.green + 0.114f * colour.blue
    return Color(l, l, l, colour.alpha)
}

private fun invertOf(colour: Color): Color = Color(1f - colour.red, 1f - colour.green, 1f - colour.blue, colour.alpha)

/** A padlock centred on [centre], [h] tall; [open] lifts and swings the shackle free (0 shut, 1 open). */
private fun DrawScope.drawPadlock(centre: Offset, h: Float, c: DemoColours, alpha: Float, open: Float = 0f, body: Color = c.accent) {
    if (alpha <= 0f) return
    val w = h * 0.78f
    val bodyTop = centre.y - h * 0.08f
    val bodyRect = Rect(centre.x - w / 2f, bodyTop, centre.x + w / 2f, centre.y + h / 2f)
    val shackleW = w * 0.62f
    val lift = h * 0.22f * open
    val legTop = bodyTop - h * 0.26f - lift
    val stroke = h * 0.11f
    val shackle = Path().apply {
        moveTo(centre.x - shackleW / 2f, bodyTop + stroke * 0.3f - lift)
        lineTo(centre.x - shackleW / 2f, legTop)
        cubicTo(centre.x - shackleW / 2f, legTop - shackleW * 0.62f, centre.x + shackleW / 2f, legTop - shackleW * 0.62f, centre.x + shackleW / 2f, legTop)
        lineTo(centre.x + shackleW / 2f, bodyTop + stroke * 0.3f)
    }
    // Open: the shackle turns about its right leg, as a real one swings out.
    rotate(-28f * open, pivot = Offset(centre.x + shackleW / 2f, bodyTop)) {
        drawPath(shackle, c.ink.copy(alpha = 0.75f * alpha), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawRoundRect(c.shadow.copy(alpha = c.shadow.alpha * alpha), bodyRect.topLeft + Offset(0f, h * 0.05f), bodyRect.size, CornerRadius(h * 0.1f))
    drawRoundRect(body.copy(alpha = alpha), bodyRect.topLeft, bodyRect.size, CornerRadius(h * 0.1f))
    // Keyhole.
    drawCircle(Color.White.copy(alpha = 0.9f * alpha), h * 0.07f, Offset(centre.x, bodyRect.center.y - h * 0.03f))
    drawLine(Color.White.copy(alpha = 0.9f * alpha), Offset(centre.x, bodyRect.center.y), Offset(centre.x, bodyRect.center.y + h * 0.1f), h * 0.05f, StrokeCap.Round)
}

/** Scrambled bytes: short dashes of varying length where the text lines were, for an encrypted page. */
private fun DrawScope.drawScrambled(page: Rect, c: DemoColours, alpha: Float, seed: Int = 0) {
    if (alpha <= 0f) return
    val rows = 7
    for (i in 0 until rows) {
        val y = lineY(page, i, rows)
        var x = page.left + page.width * 0.14f
        var k = i * 7 + seed
        while (x < page.right - page.width * 0.16f) {
            val w = page.width * (0.04f + 0.05f * ((k * 37 % 11) / 10f))
            drawLine(c.inkSoft.copy(alpha = c.inkSoft.alpha * alpha), Offset(x, y), Offset((x + w).coerceAtMost(page.right - page.width * 0.14f), y), page.height * 0.02f, StrokeCap.Round)
            x += w + page.width * 0.035f
            k++
        }
    }
}

/** A password field: [count] dots appearing in a pill centred on [at]. */
private fun DrawScope.drawPasswordPill(at: Offset, count: Int, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    val h = size.minDimension * 0.1f
    val w = h * 4.2f
    drawRoundRect(c.surface.copy(alpha = alpha), Offset(at.x - w / 2f, at.y - h / 2f), Size(w, h), CornerRadius(h / 2f))
    drawRoundRect(c.accent.copy(alpha = 0.7f * alpha), Offset(at.x - w / 2f, at.y - h / 2f), Size(w, h), CornerRadius(h / 2f), style = Stroke(1.5f))
    for (i in 0 until count) {
        drawCircle(c.onSurface.copy(alpha = alpha), h * 0.11f, Offset(at.x - w / 2f + h * 0.55f + i * h * 0.42f, at.y))
    }
}

/**
 * Crop PDF: guides slide in from the four edges to the content, the white margins fall away like trimmed paper, and
 * the smaller page settles with its text exactly where it was.
 */
val CropPdfScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val full = mainPage(scale = 0.8f)
    val content = Rect(full.left + full.width * 0.1f, full.top + full.height * 0.085f, full.right - full.width * 0.1f, full.bottom - full.height * 0.15f)
    val guides = seg(t, 0.06f, 0.3f) * (1f - seg(t, 0.5f, 0.56f))
    val reset = seg(t, 0.93f, 1f)
    val trim = seg(t, 0.36f, 0.54f) * (1f - reset)
    val settle = seg(t, 0.56f, 0.7f) * (1f - reset)
    val page = lerpRect(full, content, trim)
    scale(1f + 0.16f * settle, pivot = content.center) {
        // The margins as paper strips that drop away as they are cut off.
        if (trim in 0.001f..0.999f) {
            val drop = full.height * 0.18f * trim
            val fade = 1f - trim
            drawRect(c.paper.copy(alpha = fade), Offset(full.left, full.top - drop), Size(full.width, content.top - full.top))
            drawRect(c.paper.copy(alpha = fade), Offset(full.left, content.bottom + drop), Size(full.width, full.bottom - content.bottom))
            drawRect(c.paper.copy(alpha = fade), Offset(full.left - drop * 0.6f, content.top), Size(content.left - full.left, content.height))
            drawRect(c.paper.copy(alpha = fade), Offset(content.right + drop * 0.6f, content.top), Size(full.right - content.right, content.height))
        }
        drawPaper(page, c, lift = settle * 0.6f)
        drawTextLines(full, c, count = 6, top = 0.2f, bottom = 0.78f)
        if (guides > 0f) {
            // The parts that go, shaded, inside the guides as they close in.
            val frame = lerpRect(full, content, guides)
            val shade = c.ink.copy(alpha = 0.12f * guides)
            drawRect(shade, full.topLeft, Size(full.width, frame.top - full.top))
            drawRect(shade, Offset(full.left, frame.bottom), Size(full.width, full.bottom - frame.bottom))
            drawRect(shade, Offset(full.left, frame.top), Size(frame.left - full.left, frame.height))
            drawRect(shade, Offset(frame.right, frame.top), Size(full.right - frame.right, frame.height))
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            val guide = c.accent.copy(alpha = guides)
            val reach = full.width * 0.06f
            drawLine(guide, Offset(full.left - reach, frame.top), Offset(full.right + reach, frame.top), 2f, pathEffect = dash)
            drawLine(guide, Offset(full.left - reach, frame.bottom), Offset(full.right + reach, frame.bottom), 2f, pathEffect = dash)
            drawLine(guide, Offset(frame.left, full.top - reach), Offset(frame.left, full.bottom + reach), 2f, pathEffect = dash)
            drawLine(guide, Offset(frame.right, full.top - reach), Offset(frame.right, full.bottom + reach), 2f, pathEffect = dash)
        }
    }
    drawDoneBadge(Offset(content.right, content.bottom), full.width * 0.1f, c, seg(t, 0.7f, 0.76f) * (1f - seg(t, 0.9f, 0.94f)))
}

/**
 * Watermark: the word comes down onto the top page of a stack as a translucent diagonal stamp, then the stack fans
 * out and every page carries it.
 */
val WatermarkScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val fan = seg(t, 0.46f, 0.64f) * (1f - seg(t, 0.92f, 1f))
    val baseHeight = size.height * mixf(0.76f, 0.58f, fan)
    val word = "DRAFT"
    // The middle page (1) is drawn last: it is the front of the stack, where the stamp lands first.
    for (i in listOf(2, 0, 1)) {
        // Behind each other at first (a slight offset reads as a stack), side by side once fanned out.
        val stackShift = Offset((i - 1) * size.width * 0.025f, (1 - i) * size.height * 0.02f)
        val fanShift = Offset((i - 1) * size.width * 0.28f, 0f)
        val centre = Offset(size.width / 2f, size.height / 2f) + Offset(mixf(stackShift.x, fanShift.x, fan), mixf(stackShift.y, fanShift.y, fan))
        val page = pageAt(centre, baseHeight)
        drawPaper(page, c)
        drawTextLines(page, c, count = 6, top = 0.22f, bottom = 0.8f)
        // The stamp: dropped onto the front page first, then appearing on the others as they come out.
        val stamp = if (i == 1) seg(t, 0.1f, 0.32f) else seg(t, 0.56f + i * 0.04f, 0.66f + i * 0.04f)
        val on = stamp * (1f - seg(t, 0.9f, 0.97f))
        if (on > 0f) {
            val drop = if (i == 1) 1f + 0.5f * (1f - stamp) else 1f
            val style = TextStyle(
                fontSize = (page.width * 0.2f * drop / density).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F).copy(alpha = 0.38f * on),
                letterSpacing = (page.width * 0.012f / density).sp
            )
            val layout = f.text.measure(word, style)
            rotate(-45f, pivot = page.center) {
                drawText(layout, topLeft = page.center - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
    }
    drawDoneBadge(Offset(size.width * 0.86f, size.height * 0.84f), size.minDimension * 0.07f, c, seg(t, 0.74f, 0.8f) * (1f - seg(t, 0.9f, 0.95f)))
}

/** The colourful page the Greyscale and Invert scenes recolour: [tint] maps every colour (identity, grey, inverted). */
private fun DrawScope.drawColourPage(page: Rect, c: DemoColours, tint: (Color) -> Color) {
    drawPaper(page, c, paper = tint(c.paper))
    val headingY = page.top + page.height * 0.12f
    drawLine(tint(Color(0xFF3949AB)), Offset(page.left + page.width * 0.14f, headingY), Offset(page.left + page.width * 0.6f, headingY), page.height * 0.032f, StrokeCap.Round)
    // A photo: sky, sun and hill.
    val photo = Rect(page.left + page.width * 0.14f, page.top + page.height * 0.2f, page.right - page.width * 0.14f, page.top + page.height * 0.48f)
    drawRect(tint(Color(0xFF64B5F6)), photo.topLeft, photo.size)
    drawCircle(tint(Color(0xFFFFCA28)), photo.height * 0.16f, Offset(photo.left + photo.width * 0.75f, photo.top + photo.height * 0.32f))
    val hill = Path().apply {
        moveTo(photo.left, photo.bottom)
        cubicTo(photo.left + photo.width * 0.3f, photo.top + photo.height * 0.3f, photo.left + photo.width * 0.6f, photo.top + photo.height * 0.9f, photo.right, photo.top + photo.height * 0.55f)
        lineTo(photo.right, photo.bottom)
        close()
    }
    drawPath(hill, tint(Color(0xFF43A047)))
    // Text and a small bar chart.
    for (i in 0..2) {
        val y = page.top + page.height * (0.56f + i * 0.06f)
        drawLine(tint(c.inkSoft), Offset(page.left + page.width * 0.14f, y), Offset(page.right - page.width * (0.14f + 0.12f * i), y), page.height * 0.02f, StrokeCap.Round)
    }
    val bars = listOf(Color(0xFFE53935) to 0.5f, Color(0xFFFB8C00) to 0.8f, Color(0xFF8E24AA) to 0.35f, Color(0xFF00897B) to 0.65f)
    val base = page.top + page.height * 0.9f
    bars.forEachIndexed { i, (colour, h) ->
        val x = page.left + page.width * (0.18f + i * 0.17f)
        drawRect(tint(colour), Offset(x, base - page.height * 0.13f * h), Size(page.width * 0.1f, page.height * 0.13f * h))
    }
}

/** Greyscale: a scan line sweeps down the page and the colour drains out behind it, then the grey page settles. */
val GreyscaleScene = DemoScene(5_000) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage()
    val sweep = seg(t, 0.14f, 0.62f)
    val back = seg(t, 0.92f, 1f)
    val y = mixf(page.top, page.bottom, sweep)
    drawColourPage(page, c) { it }
    if (sweep > 0f && back < 1f) {
        clipRect(page.left - 4f, page.top - 4f, page.right + 4f, y) {
            drawColourPage(page, c) { lerpColour(greyOf(it), it, back) }
        }
    }
    drawScanPass(page, y, c, window(t, 0.12f, 0.64f))
    drawDoneBadge(Offset(page.right - page.width * 0.04f, page.bottom - page.width * 0.04f), page.width * 0.1f, c, seg(t, 0.66f, 0.72f) * (1f - seg(t, 0.88f, 0.92f)))
}

/** Invert colours: a wipe crosses the page and leaves it in dark mode (light text on black), with a moon. */
val InvertScene = DemoScene(5_000) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage()
    val wipe = seg(t, 0.14f, 0.58f)
    val back = seg(t, 0.9f, 1f)
    val x = mixf(page.left, page.right, wipe)
    drawColourPage(page, c) { it }
    if (wipe > 0f && back < 1f) {
        clipRect(page.left - 4f, page.top - 4f, x, page.bottom + 8f) {
            drawColourPage(page, c) { lerpColour(invertOf(it), it, back) }
        }
        // The wipe's leading edge, a soft glowing line.
        if (wipe < 1f) drawLine(c.mint.copy(alpha = 0.9f), Offset(x, page.top - page.height * 0.03f), Offset(x, page.bottom + page.height * 0.03f), page.width * 0.02f, StrokeCap.Round)
    }
    // A crescent moon: night reading.
    val moon = seg(t, 0.6f, 0.68f) * (1f - seg(t, 0.86f, 0.9f))
    if (moon > 0f) {
        val centre = Offset(page.right, page.top + page.height * 0.06f)
        val r = page.width * 0.12f
        drawCircle(c.highlight.copy(alpha = moon), r, centre)
        drawCircle(c.surface.copy(alpha = moon), r * 0.82f, centre + Offset(r * 0.42f, -r * 0.3f))
    }
}

private fun lerpColour(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f)

/** The page as a file icon: a small sheet with a folded corner and a "PDF" tag. */
private fun DrawScope.drawFileIcon(icon: Rect, c: DemoColours, f: DemoFrame, alpha: Float) {
    if (alpha <= 0f) return
    drawPaper(icon, c, alpha = alpha)
    val tag = Rect(icon.left + icon.width * 0.12f, icon.top + icon.height * 0.56f, icon.left + icon.width * 0.66f, icon.top + icon.height * 0.76f)
    drawRoundRect(Color(0xFFD32F2F).copy(alpha = alpha), tag.topLeft, tag.size, CornerRadius(tag.height * 0.2f))
    drawSmallText("PDF", Offset(tag.left + tag.width * 0.14f, tag.top + tag.height * 0.06f), Color.White.copy(alpha = alpha), f, scale = tag.height * 0.68f / size.minDimension, bold = true)
}

/**
 * Protect: a password is typed, a padlock drops onto the page and snaps shut, the contents scramble (only the right
 * password reads them back) and the page becomes a file with a padlock on it.
 */
val ProtectScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val shrink = seg(t, 0.6f, 0.76f) * (1f - seg(t, 0.94f, 1f))
    val full = mainPage(scale = 0.74f, dy = size.height * 0.06f)
    val icon = pageAt(Offset(size.width / 2f, size.height / 2f + size.height * 0.04f), size.height * 0.46f)
    val page = lerpRect(full, icon, shrink)
    val scramble = seg(t, 0.5f, 0.6f) * (1f - seg(t, 0.94f, 1f))
    if (shrink < 1f) {
        drawPaper(page, c)
        drawTextLines(page, c, alpha = 1f - scramble)
        drawScrambled(page, c, scramble)
    } else {
        drawFileIcon(page, c, f, 1f)
    }
    if (shrink > 0f && shrink < 1f) drawFileIcon(page, c, f, shrink)
    // Typing the password above the page.
    val typing = seg(t, 0.04f, 0.26f)
    drawPasswordPill(Offset(size.width / 2f, full.top - full.height * 0.04f), (8 * typing).toInt(), c, window(t, 0.02f, 0.4f))
    // The padlock: falls from above, lands with a little bounce, shackle snaps shut.
    val fall = seg(t, 0.28f, 0.44f)
    val shut = seg(t, 0.44f, 0.5f)
    val bounce = sin((seg(t, 0.44f, 0.56f) * PI).toFloat()) * 0.06f
    val lockHeight = mixf(full.height * 0.3f, icon.height * 0.42f, shrink)
    val rest = Offset(page.center.x + page.width * 0.32f * shrink, mixf(page.center.y, page.bottom - lockHeight * 0.2f, shrink))
    val lockAt = Offset(rest.x, mixf(-lockHeight, rest.y, fall) - lockHeight * bounce)
    val lockAlpha = seg(t, 0.28f, 0.32f) * (1f - seg(t, 0.94f, 1f))
    drawPadlock(lockAt, lockHeight, c, lockAlpha, open = 1f - shut)
    drawDoneBadge(Offset(icon.right + icon.width * 0.1f, icon.top), icon.width * 0.14f, c, seg(t, 0.78f, 0.84f) * (1f - seg(t, 0.92f, 0.95f)))
}

/**
 * Unlock: the locked file opens to the right password, the padlock springs open and falls away, and the scrambled
 * contents read as text again.
 */
val UnlockScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.74f, dy = size.height * 0.06f)
    val clear = seg(t, 0.5f, 0.66f) * (1f - seg(t, 0.94f, 1f))
    drawPaper(page, c)
    drawScrambled(page, c, 1f - clear, seed = 3)
    drawTextLines(page, c, alpha = clear)
    val typing = seg(t, 0.04f, 0.24f)
    drawPasswordPill(Offset(size.width / 2f, page.top - page.height * 0.04f), (8 * typing).toInt(), c, window(t, 0.02f, 0.36f))
    // The padlock: springs open, then drops off the page turning as it goes.
    val open = seg(t, 0.28f, 0.36f) * (1f - seg(t, 0.95f, 1f))
    val fall = seg(t, 0.4f, 0.62f) * (1f - seg(t, 0.95f, 1f))
    val lockHeight = page.height * 0.3f
    val at = Offset(page.center.x + page.width * 0.25f * fall, page.center.y + size.height * 0.75f * fall * fall)
    val alpha = 1f - seg(t, 0.52f, 0.62f) + seg(t, 0.95f, 1f)
    rotate(40f * fall, pivot = at) {
        drawPadlock(at, lockHeight, c, alpha.coerceIn(0f, 1f), open = open, body = c.accent)
    }
    drawDoneBadge(Offset(page.right - page.width * 0.08f, page.bottom - page.width * 0.08f), page.width * 0.1f, c, seg(t, 0.68f, 0.74f) * (1f - seg(t, 0.9f, 0.94f)))
}

/** A handwritten-looking signature across [box]: two loops and a flourish, left to right. */
private fun signaturePath(box: Rect): Path = Path().apply {
    val x = box.left
    val y = box.center.y
    val w = box.width
    val h = box.height
    moveTo(x, y + h * 0.1f)
    cubicTo(x + w * 0.08f, y - h * 0.9f, x + w * 0.18f, y - h * 0.6f, x + w * 0.14f, y + h * 0.3f)
    cubicTo(x + w * 0.12f, y + h * 0.6f, x + w * 0.24f, y - h * 0.2f, x + w * 0.32f, y - h * 0.1f)
    cubicTo(x + w * 0.4f, y, x + w * 0.38f, y + h * 0.35f, x + w * 0.46f, y + h * 0.1f)
    cubicTo(x + w * 0.54f, y - h * 0.7f, x + w * 0.62f, y - h * 0.5f, x + w * 0.6f, y + h * 0.25f)
    cubicTo(x + w * 0.66f, y - h * 0.1f, x + w * 0.74f, y - h * 0.15f, x + w * 0.8f, y + h * 0.05f)
    cubicTo(x + w * 0.88f, y + h * 0.3f, x + w * 0.95f, y - h * 0.3f, x + w, y - h * 0.4f)
}

/**
 * Sign: a finger writes a signature on a pad that floats over the page, then the signature drops onto the page's
 * signature line, shrinking to fit, and stays there.
 */
val SignScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.72f, dy = size.height * 0.1f)
    drawPaper(page, c)
    drawTextLines(page, c, count = 5, top = 0.2f, bottom = 0.56f)
    // The signature line, bottom right.
    val lineY = page.top + page.height * 0.8f
    val lineL = page.left + page.width * 0.4f
    val lineR = page.right - page.width * 0.1f
    drawLine(c.ink.copy(alpha = 0.5f), Offset(lineL, lineY), Offset(lineR, lineY), 1.5f)
    drawSmallText("×", Offset(lineL - page.width * 0.06f, lineY - page.height * 0.06f), c.inkSoft, f, scale = 0.05f)

    val reset = seg(t, 0.93f, 1f)
    // The pad, lifted above the page while writing.
    val padIn = seg(t, 0.02f, 0.1f)
    val padOut = seg(t, 0.46f, 0.56f)
    val pad = Rect(size.width * 0.16f, size.height * 0.04f, size.width * 0.84f, size.height * 0.36f)
    val padAlpha = padIn * (1f - padOut)
    if (padAlpha > 0f) {
        drawRoundRect(c.shadow.copy(alpha = 0.3f * padAlpha), pad.topLeft + Offset(0f, pad.height * 0.1f), pad.size, CornerRadius(pad.height * 0.12f))
        drawRoundRect(c.surface.copy(alpha = padAlpha), pad.topLeft, pad.size, CornerRadius(pad.height * 0.12f))
        drawLine(c.inkSoft.copy(alpha = 0.6f * padAlpha), Offset(pad.left + pad.width * 0.08f, pad.bottom - pad.height * 0.22f), Offset(pad.right - pad.width * 0.08f, pad.bottom - pad.height * 0.22f), 1.2f)
    }
    val writeBox = Rect(pad.left + pad.width * 0.12f, pad.top + pad.height * 0.2f, pad.right - pad.width * 0.12f, pad.bottom - pad.height * 0.3f)
    val signBox = Rect(lineL + (lineR - lineL) * 0.05f, lineY - page.height * 0.09f, lineR - (lineR - lineL) * 0.05f, lineY - page.height * 0.01f)
    val drop = seg(t, 0.5f, 0.68f)
    val box = lerpRect(writeBox, signBox, drop)
    val written = seg(t, 0.1f, 0.42f)
    if (written > 0f && reset < 1f) {
        val path = signaturePath(box)
        val measure = PathMeasure()
        measure.setPath(path, false)
        val partial = Path()
        measure.getSegment(0f, measure.length * written, partial, true)
        val ink = Color(0xFF1A3A8F).copy(alpha = 1f - reset)
        drawPath(partial, ink, style = Stroke(mixf(size.minDimension * 0.012f, size.minDimension * 0.007f, drop), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The fingertip writing, while the pen is down.
        if (written < 1f) drawFinger(measure.getPosition(measure.length * written), c, window(t, 0.08f, 0.44f), pressed = true)
    }
    drawDoneBadge(Offset(page.right - page.width * 0.04f, lineY + page.height * 0.07f), page.width * 0.09f, c, seg(t, 0.7f, 0.76f) * (1f - seg(t, 0.9f, 0.94f)))
}
