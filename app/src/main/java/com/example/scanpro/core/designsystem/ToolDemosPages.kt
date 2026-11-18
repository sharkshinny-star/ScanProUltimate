package com.example.scanpro.core.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt

/*
 * Tool demonstrations (F195) for: Merge, Split, Organise (rotate, extract), Insert pages, Remove blank pages, N-up, Page numbers, Compress PDF.
 * Built with the shared pieces in ToolDemos.kt. Keep the map lazy (or at the bottom of the file): top-level values
 * are set in file order, so a map above the scenes it names would hold nulls.
 *
 * Every scene tells one story on the tilted desk, about five seconds long: the pages as they are, the tool at work
 * (pages lifting off the desk, moving, turning, shrinking), and the result with a tick, then a short fade back to the
 * start. With animations off, the frame at 0.62 of the loop shows the tool mid-way or done.
 */

/** Scenes by tool id for this group; [toolDemoScene] looks here after ToolDemos.kt's own. */
internal val PAGE_TOOL_SCENES: Map<String, DemoScene> by lazy {
    mapOf(
        "merge" to MergeScene,
        "split" to SplitScene,
        "organize" to OrganiseScene,
        "rotate" to RotateScene,
        "extract" to ExtractScene,
        "insert_pages" to InsertPagesScene,
        "blank_pages" to BlankPagesScene,
        "nup" to NupScene,
        "page_numbers" to PageNumbersScene,
        "compress" to CompressScene,
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Pieces for page scenes
// ---------------------------------------------------------------------------------------------------------------

/** A small page of a document: paper, a heading bar in [tint] (which document it belongs to) and a few text lines. */
private fun DrawScope.docPage(page: Rect, c: DemoColours, tint: Color, lift: Float = 0f, alpha: Float = 1f, blank: Boolean = false) {
    if (alpha <= 0f) return
    drawPaper(page, c, lift = lift, alpha = alpha)
    if (blank) return
    val y = page.top + page.height * 0.14f
    drawLine(tint.copy(alpha = 0.85f * alpha), Offset(page.left + page.width * 0.14f, y), Offset(page.left + page.width * 0.58f, y), page.height * 0.035f, StrokeCap.Round)
    drawTextLines(page, c, alpha = alpha, count = 5, heading = false)
}

/** The page's number in a small dark pill near its foot. */
private fun DrawScope.pageNumber(page: Rect, number: String, f: DemoFrame, alpha: Float = 1f) {
    drawPill(number, Offset(page.center.x, page.bottom - page.height * 0.12f), f.c, f, alpha, background = f.c.ink.copy(alpha = 0.78f))
}

/** A point between [a] and [b]. */
private fun mix(a: Offset, b: Offset, t: Float) = Offset(mixf(a.x, b.x, t), mixf(a.y, b.y, t))

/** The whole scene fades to the background at the end of the loop, so it starts again cleanly. */
private fun DrawScope.loopFade(t: Float, c: DemoColours, from: Float = 0.93f) {
    val fade = seg(t, from, 0.99f)
    if (fade > 0f) drawRect(c.surface.copy(alpha = fade), Offset.Zero, size)
}

/** A curved arrow round [centre]: "turn". */
private fun DrawScope.turnArrow(centre: Offset, radius: Float, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    val stroke = radius * 0.22f
    drawArc(c.accent.copy(alpha = alpha), -200f, 250f, false, Offset(centre.x - radius, centre.y - radius), Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
    // The arrow head at the end of the arc (50 degrees, clockwise from the right).
    val end = Math.toRadians(50.0)
    val tip = Offset(centre.x + radius * Math.cos(end).toFloat(), centre.y + radius * Math.sin(end).toFloat())
    val head = Path().apply {
        moveTo(tip.x + stroke * 1.6f, tip.y - stroke * 0.6f)
        lineTo(tip.x - stroke * 0.6f, tip.y + stroke * 1.4f)
        lineTo(tip.x - stroke * 1.2f, tip.y - stroke * 1.2f)
        close()
    }
    drawPath(head, c.accent.copy(alpha = alpha))
}

// ---------------------------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------------------------

/**
 * Merge: two documents, three blue pages and two green ones, lift off the desk and slide together into one pile, then
 * the pile fans out into a single row, numbered 1 to 5 in order: the first file's pages, then the second's.
 */
val MergeScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val together = seg(t, 0.12f, 0.42f)
    val fan = seg(t, 0.5f, 0.72f)
    val lift = window(t, 0.1f, 0.5f, 0.06f)
    val bigH = size.height * 0.52f
    val smallH = size.height * 0.36f
    val pileCentre = Offset(size.width / 2f, size.height * 0.5f)
    // Draw the last page first, so page 1 ends on top of the pile.
    for (i in 4 downTo 0) {
        val fromA = i < 3
        val k = if (fromA) i else i - 3
        val stack = if (fromA) Offset(size.width * 0.27f, size.height * 0.5f) else Offset(size.width * 0.73f, size.height * 0.5f)
        val inStack = stack + Offset(k * size.width * 0.025f, -k * size.height * 0.035f)
        val inPile = pileCentre + Offset(i * size.width * 0.012f, -i * size.height * 0.018f)
        val inRow = Offset(size.width * (0.14f + i * 0.18f), size.height * 0.5f)
        val at = mix(mix(inStack, inPile, together), inRow, fan)
        val page = pageAt(at, mixf(bigH, smallH, fan))
        docPage(page, c, if (fromA) c.accent else c.mint, lift = lift * (1f - fan))
        val numbered = seg(t, 0.68f + i * 0.03f, 0.72f + i * 0.03f)
        if (numbered > 0f) pageNumber(page, "${i + 1}", f, numbered)
    }
    // Labels of the two files while they are apart.
    val labels = 1f - seg(t, 0.1f, 0.18f)
    drawPill("A.pdf", Offset(size.width * 0.27f, size.height * 0.12f), c, f, labels, background = c.accent)
    drawPill("B.pdf", Offset(size.width * 0.73f, size.height * 0.12f), c, f, labels, background = c.mint, foreground = Color(0xFF04281E))
    drawDoneBadge(Offset(size.width * 0.5f, size.height * 0.14f), size.minDimension * 0.07f, c, seg(t, 0.8f, 0.85f))
    loopFade(t, c)
}

/**
 * Split: a four-page document; a dashed cut appears between pages 2 and 3, then the two halves lift apart into two
 * files of their own, each fanning out to show its pages.
 */
val SplitScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val apart = seg(t, 0.3f, 0.58f)
    val spread = seg(t, 0.6f, 0.76f)
    val lift = window(t, 0.28f, 0.62f, 0.06f)
    val h = size.height * mixf(0.56f, 0.44f, apart)
    for (i in 3 downTo 0) {
        val second = i >= 2
        val k = if (second) i - 2 else i
        val inPile = Offset(size.width * 0.5f + i * size.width * 0.02f, size.height * 0.52f - i * size.height * 0.03f)
        val fileCentre = Offset(size.width * (if (second) 0.74f else 0.26f), size.height * 0.54f)
        val inFile = fileCentre + Offset(k * size.width * 0.02f, -k * size.height * 0.03f)
        val fanned = fileCentre + Offset((k - 0.5f) * size.width * 0.16f, 0f)
        val at = mix(mix(inPile, inFile, apart), fanned, spread)
        val page = pageAt(at, h)
        docPage(page, c, if (second) c.mint else c.accent, lift = lift)
        val numbered = 1f - seg(t, 0.92f, 0.96f)
        pageNumber(page, "${i + 1}", f, numbered * seg(t, 0.04f, 0.1f))
    }
    // The cut: a dashed line that draws itself down between the halves, then a pair of scissors' worth of flash.
    val cut = seg(t, 0.12f, 0.28f) * (1f - seg(t, 0.32f, 0.38f))
    if (cut > 0f) {
        val x = size.width * 0.5f
        drawLine(
            c.accent.copy(alpha = cut), Offset(x, size.height * 0.08f), Offset(x, size.height * mixf(0.08f, 0.92f, seg(t, 0.12f, 0.28f))),
            size.minDimension * 0.012f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
        )
    }
    val files = seg(t, 0.66f, 0.72f) * (1f - seg(t, 0.92f, 0.96f))
    drawPill("1–2.pdf", Offset(size.width * 0.26f, size.height * 0.12f), c, f, files)
    drawPill("3–4.pdf", Offset(size.width * 0.74f, size.height * 0.12f), c, f, files, background = c.mint, foreground = Color(0xFF04281E))
    loopFade(t, c)
}

/**
 * Organise: four pages in a 2 by 2 grid. A finger presses page 3, it lifts off the desk and is carried to the first
 * place while the others slide along; then a tap turns page 4 a quarter.
 */
val OrganiseScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val h = size.height * 0.4f
    val slots = listOf(
        Offset(size.width * 0.38f, size.height * 0.28f), Offset(size.width * 0.62f, size.height * 0.28f),
        Offset(size.width * 0.38f, size.height * 0.74f), Offset(size.width * 0.62f, size.height * 0.74f)
    )
    // Page 3 moves to the front: 1 2 3 4 becomes 3 1 2 4.
    val after = mapOf(1 to 1, 2 to 2, 3 to 0, 4 to 3)
    val press = seg(t, 0.14f, 0.2f)
    val move = seg(t, 0.2f, 0.46f)
    val drop = seg(t, 0.46f, 0.52f)
    val turn = seg(t, 0.64f, 0.78f)
    val held = press * (1f - drop)
    val tints = listOf(c.accent, c.mint, c.highlight, c.accent)
    // The carried page is drawn last, above the others.
    for (n in listOf(1, 2, 4, 3)) {
        val from = slots[n - 1]
        val to = slots[after.getValue(n)]
        var at = mix(from, to, move)
        if (n == 3) at += Offset(0f, -h * 0.06f * held)
        val page = pageAt(at, h * (1f + 0.06f * if (n == 3) held else 0f))
        val degrees = if (n == 4) 90f * turn else 0f
        rotate(degrees, page.center) {
            docPage(page, c, tints[n - 1], lift = if (n == 3) held else 0f)
            pageNumber(page, "$n", f)
        }
    }
    turnArrow(slots[3] + Offset(h * 0.36f, -h * 0.36f), h * 0.12f, c, window(t, 0.6f, 0.8f))
    // The finger: down on page 3, carried with it, then a tap on page 4.
    val carry = mix(slots[2], slots[0], move)
    val tapFour = window(t, 0.56f, 0.7f, 0.04f)
    when {
        t < 0.52f -> drawFinger(carry + Offset(h * 0.1f, h * 0.1f), c, window(t, 0.06f, 0.54f, 0.06f), pressed = held > 0.5f)
        else -> drawFinger(slots[3] + Offset(h * 0.1f, h * 0.1f), c, tapFour, pressed = t in 0.6f..0.66f)
    }
    drawDoneBadge(Offset(size.width * 0.86f, size.height * 0.5f), size.minDimension * 0.07f, c, seg(t, 0.82f, 0.86f))
    loopFade(t, c)
}

/** Rotate: a page scanned on its side lies turned on the desk; a tap on the turn button and it swings upright. */
val RotateScene = DemoScene(4_800) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.62f)
    val turn = seg(t, 0.3f, 0.58f)
    val lift = window(t, 0.26f, 0.62f, 0.06f)
    rotate(-90f + 90f * turn, page.center) {
        docPage(page, c, c.accent, lift = lift)
    }
    val button = Offset(size.width * 0.82f, size.height * 0.78f)
    drawCircle(c.surface, size.minDimension * 0.09f, button)
    drawCircle(c.accent.copy(alpha = 0.35f), size.minDimension * 0.09f, button, style = Stroke(2f))
    turnArrow(button, size.minDimension * 0.045f, c, 1f)
    drawFinger(button + Offset(size.minDimension * 0.02f, size.minDimension * 0.02f), c, window(t, 0.12f, 0.34f, 0.05f), pressed = t in 0.22f..0.3f)
    drawDoneBadge(Offset(page.right, page.top + page.height * 0.1f), page.width * 0.12f, c, seg(t, 0.62f, 0.68f))
    loopFade(t, c)
}

/** Extract: a row of four pages; taps tick pages 2 and 4, which lift out of the row and settle as a new PDF of their own. */
val ExtractScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val h = size.height * 0.36f
    val out = seg(t, 0.5f, 0.74f)
    val others = 1f - 0.65f * seg(t, 0.5f, 0.6f)
    for (i in 0 until 4) {
        val chosen = i == 1 || i == 3
        val inRow = Offset(size.width * (0.17f + i * 0.22f), size.height * 0.32f)
        val k = if (i == 1) 0 else 1
        val inNew = Offset(size.width * (0.42f + k * 0.16f), size.height * 0.74f)
        val at = if (chosen) mix(inRow, inNew, out) else inRow
        val page = pageAt(at, h)
        val lift = if (chosen) window(t, 0.48f, 0.78f, 0.06f) else 0f
        docPage(page, c, c.accent, lift = lift, alpha = if (chosen) 1f else others)
        pageNumber(page, "${i + 1}", f, if (chosen) 1f else others)
        if (chosen) {
            val ticked = seg(t, if (i == 1) 0.16f else 0.32f, if (i == 1) 0.2f else 0.36f)
            drawDoneBadge(Offset(page.right - page.width * 0.12f, page.top + page.width * 0.14f), page.width * 0.13f, c, ticked)
        }
    }
    val tapAt = if (t < 0.26f) Offset(size.width * 0.39f, size.height * 0.34f) else Offset(size.width * 0.83f, size.height * 0.34f)
    drawFinger(tapAt, c, window(t, 0.08f, 0.42f, 0.05f), pressed = t in 0.15f..0.2f || t in 0.31f..0.36f)
    drawDoneBadge(Offset(size.width * 0.78f, size.height * 0.74f), size.minDimension * 0.06f, c, seg(t, 0.76f, 0.82f))
    loopFade(t, c)
}

/**
 * Insert pages: three blue pages in a row; two green pages from another PDF float down from above, the row opens a
 * gap after page 1, and they drop into it: five pages, numbered in their new order.
 */
val InsertPagesScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val h = size.height * 0.34f
    val open = seg(t, 0.3f, 0.5f)
    val drop = seg(t, 0.46f, 0.68f)
    val numbered = seg(t, 0.72f, 0.78f)
    val step = size.width * 0.18f
    val left = size.width * 0.14f
    // Base pages 1, 2, 3 end in places 1, 4, 5.
    listOf(0 to 0, 1 to 3, 2 to 4).forEach { (i, end) ->
        val start = Offset(left + step * (i + 1), size.height * 0.62f)
        val target = Offset(left + step * end, size.height * 0.62f)
        val page = pageAt(mix(start, target, open), h)
        docPage(page, c, c.accent)
        pageNumber(page, if (numbered > 0.5f) "${end + 1}" else "${i + 1}", f)
    }
    // The new pages, from the other PDF, held above and then set in places 2 and 3.
    for (k in 0 until 2) {
        val above = Offset(size.width * (0.42f + k * 0.17f), size.height * 0.2f)
        val slot = Offset(left + step * (1 + k), size.height * 0.62f)
        val arrive = seg(t, 0.06f + k * 0.06f, 0.22f + k * 0.06f)
        val page = pageAt(mix(above, slot, drop), h)
        val lift = 1f - drop
        docPage(page, c, c.mint, lift = lift, alpha = arrive)
        if (numbered > 0f) pageNumber(page, "${k + 2}", f, numbered)
    }
    drawDoneBadge(Offset(size.width * 0.9f, size.height * 0.2f), size.minDimension * 0.07f, c, seg(t, 0.8f, 0.85f))
    loopFade(t, c)
}

/**
 * Remove blank pages: a scan line runs along a row of four pages; the two empty ones are outlined, then tip off the
 * desk and fall away, and the pages left close up.
 */
val BlankPagesScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val h = size.height * 0.42f
    val y = size.height * 0.46f
    val fall = seg(t, 0.56f, 0.76f)
    val close = seg(t, 0.72f, 0.86f)
    val blank = setOf(1, 3)
    for (i in 0 until 4) {
        val isBlank = i in blank
        val keptIndex = if (i == 0) 0 else 1
        val x = mixf(size.width * (0.17f + i * 0.22f), size.width * (0.39f + keptIndex * 0.22f), if (isBlank) 0f else close)
        var page = pageAt(Offset(x, y), h)
        if (isBlank) page = page.translate(Offset(0f, size.height * 0.55f * fall * fall))
        val alpha = if (isBlank) 1f - fall else 1f
        rotate(if (isBlank) 18f * fall * (if (i == 1) -1f else 1f) else 0f, page.center) {
            docPage(page, c, c.accent, blank = isBlank, alpha = alpha)
            if (isBlank) {
                val found = seg(t, 0.36f + i * 0.04f, 0.42f + i * 0.04f) * alpha
                if (found > 0f) drawRoundRect(Color(0xFFE5484D).copy(alpha = found), page.topLeft, page.size, CornerRadius(page.width * 0.04f), style = Stroke(size.minDimension * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
            }
        }
    }
    // The scan line sweeps left to right across the row.
    val sweep = seg(t, 0.08f, 0.5f)
    val scan = window(t, 0.08f, 0.52f, 0.04f)
    if (scan > 0f) {
        val x = mixf(size.width * 0.06f, size.width * 0.94f, sweep)
        drawLine(c.mint.copy(alpha = scan), Offset(x, y - h * 0.62f), Offset(x, y + h * 0.62f), size.minDimension * 0.012f, StrokeCap.Round)
        drawRect(c.mint.copy(alpha = 0.18f * scan), Offset(x - size.width * 0.08f, y - h * 0.55f), Size(size.width * 0.08f, h * 1.1f))
    }
    drawDoneBadge(Offset(size.width * 0.86f, size.height * 0.16f), size.minDimension * 0.07f, c, seg(t, 0.84f, 0.88f))
    loopFade(t, c)
}

/** N-up: four pages lying apart shrink and glide onto one sheet, two by two, in reading order. */
val NupScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val gather = seg(t, 0.22f, 0.56f)
    val lift = window(t, 0.18f, 0.6f, 0.06f)
    val sheet = mainPage(scale = 0.8f)
    // The sheet slides in under the pages as they gather.
    val sheetIn = seg(t, 0.1f, 0.28f)
    drawPaper(sheet, c, alpha = sheetIn)
    val margin = sheet.width * 0.07f
    val gap = sheet.width * 0.04f
    val cellW = (sheet.width - 2 * margin - gap) / 2f
    val cellH = (sheet.height - 2 * margin - gap) / 2f
    val tints = listOf(c.accent, c.mint, c.highlight, c.accent)
    for (i in 0 until 4) {
        val column = i % 2
        val row = i / 2
        val apart = Offset(size.width * (0.14f + i * 0.24f), size.height * 0.5f)
        val cellCentre = Offset(sheet.left + margin + column * (cellW + gap) + cellW / 2f, sheet.top + margin + row * (cellH + gap) + cellH / 2f)
        val bigH = size.height * 0.46f
        val fitted = minOf(cellH, cellW / 0.72f)
        val page = pageAt(mix(apart, cellCentre, gather), mixf(bigH, fitted, gather))
        docPage(page, c, tints[i], lift = lift)
        pageNumber(page, "${i + 1}", f, 1f - 0.4f * gather)
    }
    drawPill("4 → 1", Offset(sheet.center.x, sheet.bottom + size.height * 0.02f), c, f, seg(t, 0.62f, 0.68f))
    drawDoneBadge(Offset(sheet.right, sheet.top + sheet.height * 0.05f), size.minDimension * 0.07f, c, seg(t, 0.66f, 0.72f))
    loopFade(t, c)
}

/** Page numbers: three pages fanned out; a stamp comes down on the foot of each in turn and leaves its number. */
val PageNumbersScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val h = size.height * 0.62f
    val pages = (0 until 3).map { i -> pageAt(Offset(size.width * (0.25f + i * 0.25f), size.height * 0.5f), h) }
    pages.forEachIndexed { i, page ->
        docPage(page, c, c.accent)
        val start = 0.14f + i * 0.2f
        val stamped = seg(t, start + 0.07f, start + 0.09f)
        if (stamped > 0f) {
            val spot = Offset(page.center.x, page.bottom - page.height * 0.08f)
            drawSmallText("${i + 1}", spot - Offset(size.minDimension * 0.016f, size.minDimension * 0.03f), c.ink.copy(alpha = stamped), f, scale = 0.055f, bold = true)
            // A ring of ink spreading for a moment as the stamp lifts.
            val ring = window(t, start + 0.08f, start + 0.16f, 0.03f)
            if (ring > 0f) drawCircle(c.accent.copy(alpha = 0.4f * ring), size.minDimension * (0.03f + 0.05f * seg(t, start + 0.08f, start + 0.16f)), spot, style = Stroke(2f))
        }
    }
    // The stamp: a round head on a handle, hovering above the next page and pressing down onto it.
    val active = ((t - 0.14f) / 0.2f).toInt().coerceIn(0, 2)
    val start = 0.14f + active * 0.2f
    val press = seg(t, start + 0.02f, start + 0.08f) * (1f - seg(t, start + 0.09f, start + 0.16f))
    val visible = window(t, 0.08f, 0.76f, 0.05f)
    if (visible > 0f) {
        val page = pages[active]
        val glide = seg(t, start - 0.06f, start + 0.02f)
        val fromX = if (active == 0) page.center.x - size.width * 0.15f else pages[active - 1].center.x
        val x = mixf(fromX, page.center.x, glide)
        val foot = page.bottom - page.height * 0.08f
        val y = foot - size.height * 0.2f * (1f - press)
        val r = size.minDimension * 0.06f
        // Shadow on the paper grows sharper as the stamp comes down.
        drawOval(c.shadow.copy(alpha = c.shadow.alpha * visible * (0.5f + press)), Offset(x - r, foot - r * 0.3f), Size(r * 2, r * 0.6f))
        drawLine(c.ink.copy(alpha = 0.7f * visible), Offset(x, y - r * 2.6f), Offset(x, y - r), r * 0.5f, StrokeCap.Round)
        drawCircle(c.ink.copy(alpha = 0.85f * visible), r * 0.75f, Offset(x, y - r * 2.7f))
        drawRoundRect(c.accent.copy(alpha = visible), Offset(x - r, y - r), Size(r * 2, r * 1.1f), CornerRadius(r * 0.3f))
    }
    drawDoneBadge(Offset(size.width * 0.88f, size.height * 0.14f), size.minDimension * 0.07f, c, seg(t, 0.78f, 0.84f))
    loopFade(t, c)
}

/**
 * Compress PDF: a page with a big photo; two plates press it from above and below, the photo turns to coarser blocks
 * as it is re-encoded, and the size counter runs down from 4.8 MB to 1.2 MB while the text stays sharp.
 */
val CompressScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.66f, dx = -size.width * 0.12f)
    val squeeze = seg(t, 0.22f, 0.4f) * (1f - seg(t, 0.52f, 0.62f))
    val done = seg(t, 0.3f, 0.6f)
    drawPaper(page, c, lift = squeeze * 0.5f)
    // Three lines of text above the photo (they stay sharp: text is not re-encoded).
    for (i in 0 until 3) {
        val y = lineY(page, i, 3, 0.2f, 0.34f)
        val (l, r) = lineSpan(page, i)
        drawLine(c.inkSoft, Offset(l, y), Offset(r, y), page.height * 0.022f, StrokeCap.Round)
    }
    // The photo: a sky gradient and a hill, drawn in blocks that grow coarser as it is compressed.
    val photo = Rect(page.left + page.width * 0.12f, page.top + page.height * 0.44f, page.right - page.width * 0.12f, page.top + page.height * 0.86f)
    val blocks = (24 - 16 * done).roundToInt().coerceAtLeast(6)
    val bw = photo.width / blocks
    val bh = photo.height / (blocks * photo.height / photo.width).coerceAtLeast(1f)
    clipRect(photo.left, photo.top, photo.right, photo.bottom) {
        var y = photo.top
        while (y < photo.bottom) {
            var x = photo.left
            while (x < photo.right) {
                val u = ((x + bw / 2f) - photo.left) / photo.width
                val v = ((y + bh / 2f) - photo.top) / photo.height
                val hill = v > 0.62f - 0.28f * kotlin.math.sin(u * 3.1f)
                val sun = (u - 0.72f) * (u - 0.72f) + (v - 0.28f) * (v - 0.28f) < 0.014f
                val colour = when {
                    sun -> c.highlight
                    hill -> lerp(c.mint, c.accent, v * 0.6f)
                    else -> lerp(c.accentSoft.copy(alpha = 1f), c.paper, v * 0.5f)
                }
                drawRect(colour, Offset(x, y), Size(bw + 0.5f, bh + 0.5f))
                x += bw
            }
            y += bh
        }
    }
    // The press: two plates closing on the page and opening again.
    val plateH = size.height * 0.05f
    val gapTop = page.top - plateH - size.height * 0.08f * (1f - squeeze)
    val gapBottom = page.bottom + size.height * 0.08f * (1f - squeeze)
    val plates = window(t, 0.12f, 0.68f, 0.05f)
    if (plates > 0f) {
        drawRoundRect(c.ink.copy(alpha = 0.55f * plates), Offset(page.left - page.width * 0.08f, gapTop), Size(page.width * 1.16f, plateH), CornerRadius(plateH * 0.3f))
        drawRoundRect(c.ink.copy(alpha = 0.55f * plates), Offset(page.left - page.width * 0.08f, gapBottom), Size(page.width * 1.16f, plateH), CornerRadius(plateH * 0.3f))
    }
    // The size counter, beside the page.
    val mb = mixf(4.8f, 1.2f, done)
    val tenths = (mb * 10f).roundToInt()
    val counter = Offset(size.width * 0.8f, size.height * 0.42f)
    drawPill("${tenths / 10}.${tenths % 10} MB", counter, c, f, background = if (done > 0.98f) c.mint else c.surface, foreground = if (done > 0.98f) Color(0xFF04281E) else c.onSurface)
    // A bar under it that shortens with the size.
    val barW = size.width * 0.22f
    drawRoundRect(c.inkSoft.copy(alpha = 0.25f), Offset(counter.x - barW / 2f, counter.y + size.height * 0.08f), Size(barW, size.height * 0.03f), CornerRadius(size.height * 0.015f))
    drawRoundRect(c.accent, Offset(counter.x - barW / 2f, counter.y + size.height * 0.08f), Size(barW * mb / 4.8f, size.height * 0.03f), CornerRadius(size.height * 0.015f))
    drawDoneBadge(Offset(size.width * 0.8f, size.height * 0.72f), size.minDimension * 0.07f, c, seg(t, 0.64f, 0.7f))
    loopFade(t, c)
}
