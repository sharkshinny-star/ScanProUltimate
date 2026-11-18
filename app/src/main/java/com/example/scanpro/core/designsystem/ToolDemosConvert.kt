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
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Tool demonstrations (F211) for: PDF to images, Images to PDF, Office to PDF, PDF to text, Word, Excel and PowerPoint, OCR, Extract images, PDF info, Repair, Fill forms, the image tools.
 * Built with the shared pieces in ToolDemos.kt. Keep the map lazy (or at the bottom of the file): top-level values
 * are set in file order, so a map above the scenes it names would hold nulls.
 *
 * Every scene shows the tool's result happening to a real-looking page or picture (F211): pages peel off and become
 * photo cards, lines lift off a page and rebuild as a document, a scan line turns a picture of text into selectable
 * text. The text drawn in the scenes is language-neutral (formats, numbers, units). Each holds a meaningful frame at
 * the still point (0.62) for people who turn animations off.
 */

/** Scenes by tool id for this group; [toolDemoScene] looks here after ToolDemos.kt's own. */
internal val CONVERT_TOOL_SCENES: Map<String, DemoScene> by lazy {
    mapOf(
        "pdf2img" to PdfToImagesScene,
        "img2pdf" to ImagesToPdfScene,
        "office" to OfficeToPdfScene,
        "pdf2text" to PdfToWordScene,
        "pdf2excel" to PdfToExcelScene,
        "pdf2ppt" to PdfToSlidesScene,
        "ocr" to OcrScene,
        "extract_images" to ExtractImagesScene,
        "pdf_info" to PdfInfoScene,
        "repair" to RepairScene,
        "fill" to FillFormScene,
        "crop_img" to CropImageScene,
        "compress_img" to CompressImageScene,
        "convert_img" to ConvertImageScene,
        "resize_img" to ResizeImageScene,
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Pieces for this group
// ---------------------------------------------------------------------------------------------------------------

/** Word blue, Excel green, PowerPoint orange and PDF red: the colours people know these files by. */
private val WORD_BLUE = Color(0xFF2B579A)
private val EXCEL_GREEN = Color(0xFF217346)
private val SLIDES_ORANGE = Color(0xFFD24726)
private val PDF_RED = Color(0xFFE5252A)

/** [a] moved towards [b] by [f]. */
private fun lerpRect(a: Rect, b: Rect, f: Float): Rect =
    Rect(mixf(a.left, b.left, f), mixf(a.top, b.top, f), mixf(a.right, b.right, f), mixf(a.bottom, b.bottom, f))

/** 0 → 1 → 0 over [f]: the height of a throw. */
private fun arc(f: Float): Float = sin(f * PI).toFloat()

/**
 * Text lines between [top] and [bottom] (fractions of the page height), with an optional heading bar: the same look as
 * [drawTextLines], placed where a scene needs it.
 */
private fun DrawScope.drawLines(page: Rect, c: DemoColours, alpha: Float = 1f, count: Int = DEMO_LINES.size, heading: Boolean = true, top: Float = 0.24f, bottom: Float = 0.82f) {
    if (alpha <= 0f) return
    val stroke = page.height * 0.022f
    if (heading) {
        val y = page.top + page.height * (top - 0.11f).coerceAtLeast(0.08f)
        drawLine(c.accent.copy(alpha = 0.75f * alpha), Offset(page.left + page.width * 0.14f, y), Offset(page.left + page.width * 0.52f, y), stroke * 1.35f, StrokeCap.Round)
    }
    for (i in 0 until count) {
        val y = lineY(page, i, count, top, bottom)
        val (l, r) = lineSpan(page, i)
        drawLine(c.inkSoft.copy(alpha = c.inkSoft.alpha * alpha), Offset(l, y), Offset(r, y), stroke, StrokeCap.Round)
    }
}

/** A landscape photo: sky, sun and hills, clipped to [area]. */
private fun DrawScope.drawPhoto(area: Rect, c: DemoColours, alpha: Float = 1f, tint: Color = c.accent) {
    if (alpha <= 0f) return
    clipRect(area.left, area.top, area.right, area.bottom) {
        drawRect(c.accentSoft.copy(alpha = 0.6f * alpha), area.topLeft, area.size)
        drawRect(tint.copy(alpha = 0.10f * alpha), area.topLeft, area.size)
        drawCircle(c.highlight.copy(alpha = 0.9f * alpha), area.height * 0.14f, Offset(area.left + area.width * 0.72f, area.top + area.height * 0.3f))
        val back = Path().apply {
            moveTo(area.left, area.bottom)
            lineTo(area.left + area.width * 0.35f, area.top + area.height * 0.42f)
            lineTo(area.left + area.width * 0.7f, area.bottom)
            close()
        }
        drawPath(back, tint.copy(alpha = 0.45f * alpha))
        val front = Path().apply {
            moveTo(area.left + area.width * 0.3f, area.bottom)
            lineTo(area.left + area.width * 0.68f, area.top + area.height * 0.55f)
            lineTo(area.right, area.bottom)
            close()
        }
        drawPath(front, tint.copy(alpha = 0.7f * alpha))
    }
}

/** A printed photo: a white border round [drawPhoto], with a shadow that grows as it [lift]s. */
private fun DrawScope.drawPhotoCard(card: Rect, c: DemoColours, lift: Float = 0f, alpha: Float = 1f, tint: Color = c.accent) {
    if (alpha <= 0f) return
    val drop = card.height * (0.04f + 0.08f * lift)
    val corner = CornerRadius(card.width * 0.04f)
    drawRoundRect(c.shadow.copy(alpha = c.shadow.alpha * alpha * (1.2f - 0.4f * lift)), card.topLeft + Offset(drop * 0.4f, drop), card.size, corner)
    drawRoundRect(Color.White.copy(alpha = alpha), card.topLeft, card.size, corner)
    val border = card.width * 0.07f
    drawPhoto(Rect(card.left + border, card.top + border, card.right - border, card.bottom - border * 2.2f), c, alpha, tint)
}

/** A file icon with a folded corner and a coloured band carrying [label] ("DOCX"). */
private fun DrawScope.drawFileIcon(area: Rect, c: DemoColours, band: Color, label: String, frame: DemoFrame, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawPaper(area, c, alpha = alpha)
    drawLines(area, c, alpha = alpha * 0.8f, count = 4, heading = false, top = 0.2f, bottom = 0.5f)
    val bandRect = Rect(area.left - area.width * 0.08f, area.top + area.height * 0.6f, area.left + area.width * 0.78f, area.top + area.height * 0.84f)
    drawRoundRect(band.copy(alpha = alpha), bandRect.topLeft, bandRect.size, CornerRadius(bandRect.height * 0.18f))
    drawSmallText(label, Offset(bandRect.left + bandRect.width * 0.12f, bandRect.top + bandRect.height * 0.12f), Color.White.copy(alpha = alpha), frame, scale = bandRect.height * 0.62f / size.minDimension / 1.25f, bold = true, maxWidth = bandRect.width)
}

/** A small "PDF" tag in PDF red at the top-left corner of [page]. */
private fun DrawScope.drawPdfTag(page: Rect, frame: DemoFrame, alpha: Float) {
    if (alpha <= 0f) return
    val tag = Rect(page.left - page.width * 0.06f, page.top + page.height * 0.06f, page.left + page.width * 0.34f, page.top + page.height * 0.17f)
    drawRoundRect(PDF_RED.copy(alpha = alpha), tag.topLeft, tag.size, CornerRadius(tag.height * 0.2f))
    drawSmallText("PDF", Offset(tag.left + tag.width * 0.16f, tag.top + tag.height * 0.08f), Color.White.copy(alpha = alpha), frame, scale = tag.height * 0.62f / size.minDimension / 1.25f, bold = true, maxWidth = tag.width)
}

/** A grid of [rows] × [cols] cells over [area]; cells before [filled] (in reading order) carry a short bar. */
private fun DrawScope.drawSheet(area: Rect, c: DemoColours, rows: Int, cols: Int, filled: Float, alpha: Float = 1f) {
    drawRect(Color.White.copy(alpha = alpha), area.topLeft, area.size)
    val cw = area.width / cols
    val rh = area.height / rows
    // The header row in Excel green.
    drawRect(EXCEL_GREEN.copy(alpha = 0.18f * alpha), area.topLeft, Size(area.width, rh))
    for (r in 0..rows) drawLine(c.ink.copy(alpha = 0.18f * alpha), Offset(area.left, area.top + r * rh), Offset(area.right, area.top + r * rh), 1f)
    for (k in 0..cols) drawLine(c.ink.copy(alpha = 0.18f * alpha), Offset(area.left + k * cw, area.top), Offset(area.left + k * cw, area.bottom), 1f)
    val total = rows * cols
    for (i in 0 until total) {
        val shown = (filled * total - i).coerceIn(0f, 1f)
        if (shown <= 0f) continue
        val r = i / cols
        val k = i % cols
        val y = area.top + rh * (r + 0.5f)
        val x0 = area.left + cw * k + cw * 0.15f
        val w = cw * (if (k == 0) 0.7f else 0.45f) * shown
        drawLine((if (r == 0) EXCEL_GREEN else c.ink).copy(alpha = 0.7f * alpha), Offset(x0, y), Offset(x0 + w, y), rh * 0.28f, StrokeCap.Round)
    }
}

/** A magnifying glass whose lens is centred on [at]. */
private fun DrawScope.drawMagnifier(at: Offset, radius: Float, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(Color.White.copy(alpha = 0.25f * alpha), radius, at)
    drawCircle(c.ink.copy(alpha = 0.85f * alpha), radius, at, style = Stroke(radius * 0.16f))
    val handleStart = Offset(at.x + radius * 0.72f, at.y + radius * 0.72f)
    drawLine(c.ink.copy(alpha = 0.85f * alpha), handleStart, handleStart + Offset(radius * 0.8f, radius * 0.8f), radius * 0.26f, StrokeCap.Round)
}

/** The corner handles of a selection or crop frame over [area]. */
private fun DrawScope.drawHandles(area: Rect, colour: Color, length: Float, width: Float) {
    listOf(area.topLeft to Offset(1f, 1f), area.topRight to Offset(-1f, 1f), area.bottomLeft to Offset(1f, -1f), area.bottomRight to Offset(-1f, -1f)).forEach { (corner, dir) ->
        drawLine(colour, corner, corner + Offset(length * dir.x, 0f), width, StrokeCap.Round)
        drawLine(colour, corner, corner + Offset(0f, length * dir.y), width, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------------------------

/**
 * PDF to images: three pages lie stacked; one after another the top page peels off, turns and lands to the right as
 * a printed photo of itself, until the pages are a fanned row of pictures with the resolution on a pill.
 */
val PdfToImagesScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val stack = mainPage(scale = 0.62f, dx = -size.width * 0.24f, dy = size.height * 0.04f)
    val cardH = size.height * 0.36f
    val targets = (0..2).map { i ->
        pageAt(Offset(size.width * (0.6f + 0.13f * i), size.height * (0.52f - 0.04f * i)), cardH)
    }
    val reset = 1f - seg(t, 0.92f, 0.99f)
    // The pages still in the stack, bottom first: page i leaves at its own time.
    for (i in 2 downTo 0) {
        val start = 0.1f + i * 0.2f
        val p = seg(t, start, start + 0.2f) * reset
        if (p >= 0.999f) continue
        val offset = Offset(i * size.width * 0.012f, -i * size.height * 0.012f)
        val page = stack.translate(offset)
        if (p <= 0f) {
            drawPaper(page, c)
            drawLines(page, c, count = 4, top = 0.22f, bottom = 0.46f)
            drawPhoto(Rect(page.left + page.width * 0.14f, page.top + page.height * 0.56f, page.right - page.width * 0.14f, page.top + page.height * 0.84f), c)
        }
    }
    // The peeling pages: from the stack to their place in the row, turning a little and becoming photo cards.
    for (i in 0..2) {
        val start = 0.1f + i * 0.2f
        val p = seg(t, start, start + 0.2f) * reset
        if (p <= 0f) continue
        val from = stack.translate(Offset(i * size.width * 0.012f, -i * size.height * 0.012f))
        val rect = lerpRect(from, targets[i], p).translate(Offset(0f, -arc(p) * size.height * 0.12f))
        rotate(degrees = mixf(0f, -6f + 6f * i, p) + arc(p) * 10f, pivot = rect.center) {
            if (p < 0.55f) {
                drawPaper(rect, c, lift = arc(p))
                drawLines(rect, c, alpha = 1f - p, count = 4, top = 0.22f, bottom = 0.46f)
                drawPhoto(Rect(rect.left + rect.width * 0.14f, rect.top + rect.height * 0.56f, rect.right - rect.width * 0.14f, rect.top + rect.height * 0.84f), c, alpha = 1f - p)
            }
            drawPhotoCard(rect, c, lift = arc(p), alpha = seg(p, 0.35f, 0.7f))
        }
    }
    drawPill("JPG · 150 dpi", Offset(size.width * 0.73f, size.height * 0.12f), c, f, window(t, 0.62f, 0.95f))
    drawDoneBadge(Offset(targets[2].right, targets[2].top), size.minDimension * 0.06f, c, seg(t, 0.72f, 0.78f) * (1f - seg(t, 0.92f, 0.96f)))
}

/**
 * Images to PDF: photos drop one by one onto a page stack, each settling as a page with a white margin round it; the
 * stack then squares up, a binding runs down its edge and the PDF tag appears.
 */
val ImagesToPdfScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val stackCentre = Offset(size.width * 0.5f, size.height * 0.56f)
    val pageH = size.height * 0.66f
    val reset = 1f - seg(t, 0.92f, 0.99f)
    val tints = listOf(c.accent, c.mint, SLIDES_ORANGE)
    for (i in 0..2) {
        val start = 0.06f + i * 0.17f
        val drop = seg(t, start, start + 0.16f) * reset
        if (drop <= 0f) continue
        // Settled pages sit a little offset, like a loose stack, until they square up.
        val square = seg(t, 0.62f, 0.7f)
        val settle = Offset((i - 1) * size.width * 0.02f * (1f - square), -i * size.height * 0.012f)
        val page = pageAt(stackCentre + settle, pageH)
        val photo = Rect(page.left + page.width * 0.1f, page.top + page.height * 0.12f, page.right - page.width * 0.1f, page.top + page.height * 0.6f)
        val falling = Rect(photo.left, photo.top - size.height * 0.8f * (1f - drop), photo.right, photo.bottom - size.height * 0.8f * (1f - drop))
        val turn = (1f - drop) * (if (i % 2 == 0) -14f else 12f)
        // The page appears under the photo as it lands.
        drawPaper(page, c, alpha = seg(drop, 0.7f, 1f))
        drawLines(page, c, alpha = seg(drop, 0.8f, 1f) * 0.6f, count = 3, heading = false, top = 0.7f, bottom = 0.86f)
        rotate(turn, falling.center) {
            if (drop < 1f) drawPhotoCard(falling.inflate(page.width * 0.04f), c, lift = 1f - drop, alpha = 1f - seg(drop, 0.85f, 1f), tint = tints[i])
            drawPhoto(falling, c, alpha = seg(drop, 0.8f, 1f), tint = tints[i])
        }
    }
    // The binding: a stitched line down the left edge once the stack is square.
    val bind = seg(t, 0.7f, 0.8f) * reset
    if (bind > 0f) {
        val page = pageAt(stackCentre + Offset(0f, -2 * size.height * 0.012f), pageH)
        val x = page.left + page.width * 0.05f
        drawLine(c.ink.copy(alpha = 0.55f), Offset(x, page.top + page.height * 0.06f), Offset(x, mixf(page.top + page.height * 0.06f, page.bottom - page.height * 0.06f, bind)), page.width * 0.025f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        drawPdfTag(page, f, seg(t, 0.76f, 0.82f) * reset)
        drawDoneBadge(Offset(page.right, page.bottom), page.width * 0.11f, c, seg(t, 0.8f, 0.86f) * (1f - seg(t, 0.92f, 0.96f)))
    }
}

/**
 * Office to PDF: a Word file slides in, a mint beam passes over it and it unfolds into a PDF page laid out on A4,
 * text and picture in place; then the same happens to a spreadsheet's file icon in the corner, to show it takes both.
 */
val OfficeToPdfScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val icon = pageAt(Offset(size.width * 0.3f, size.height * 0.52f), size.height * 0.5f)
    val page = mainPage(scale = 0.74f, dx = size.width * 0.16f, dy = size.height * 0.03f)
    val arrive = seg(t, 0.02f, 0.14f)
    val morph = seg(t, 0.24f, 0.52f)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    // The file icon slides in and then flows into the page's place.
    val iconRect = lerpRect(icon.translate(Offset(-size.width * 0.3f * (1f - arrive), 0f)), page, morph)
    scale(1f + arc(morph) * 0.06f, 1f + arc(morph) * 0.06f, iconRect.center) {
        drawFileIcon(iconRect, c, WORD_BLUE, "DOCX", f, alpha = arrive * (1f - seg(morph, 0.4f, 0.8f)) * reset)
        val shown = seg(morph, 0.45f, 1f) * reset
        if (shown > 0f) {
            drawPaper(iconRect, c, alpha = shown)
            drawLines(iconRect, c, alpha = shown, count = 4, top = 0.24f, bottom = 0.48f)
            drawPhoto(Rect(iconRect.left + iconRect.width * 0.14f, iconRect.top + iconRect.height * 0.58f, iconRect.right - iconRect.width * 0.14f, iconRect.top + iconRect.height * 0.84f), c, alpha = shown)
            drawPdfTag(iconRect, f, shown)
        }
    }
    drawScanPass(iconRect, mixf(iconRect.top, iconRect.bottom, seg(t, 0.2f, 0.5f)), c, window(t, 0.2f, 0.5f, 0.03f))
    // The A4 pill, and the other kinds of file it takes, waiting their turn in the corner.
    drawPill("A4", Offset(page.center.x, page.top - page.height * 0.06f), c, f, window(t, 0.55f, 0.92f))
    val small = pageAt(Offset(size.width * 0.14f, size.height * 0.3f), size.height * 0.22f)
    drawFileIcon(small, c, EXCEL_GREEN, "XLSX", f, alpha = window(t, 0.58f, 0.95f) * 0.9f)
    drawFileIcon(small.translate(Offset(0f, size.height * 0.36f)), c, SLIDES_ORANGE, "PPTX", f, alpha = window(t, 0.64f, 0.95f) * 0.9f)
    drawDoneBadge(Offset(page.right, page.bottom), page.width * 0.1f, c, seg(t, 0.56f, 0.62f) * (1f - seg(t, 0.9f, 0.95f)))
}

/** Where the lines of the PDF go: a document, a sheet's cells or slides. */
private enum class TextTarget { WORD, SHEET, SLIDES }

/**
 * The PDF to text family: the lines of the PDF on the left lift off one after another, arc across and settle into the
 * new file on the right: lines of a Word document, cells of a sheet or text on slides.
 */
private fun textScene(target: TextTarget) = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val source = pageAt(Offset(size.width * 0.27f, size.height * 0.53f), size.height * 0.7f)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    drawPaper(source, c)
    drawPdfTag(source, f, 1f)
    val count = 5
    // The destination.
    val doc = pageAt(Offset(size.width * 0.73f, size.height * 0.53f), size.height * 0.7f)
    val sheet = Rect(size.width * 0.55f, size.height * 0.24f, size.width * 0.95f, size.height * 0.82f)
    val slides = (0..2).map { i -> Rect(size.width * 0.54f, size.height * (0.14f + 0.27f * i), size.width * 0.92f, size.height * (0.14f + 0.27f * i) + size.height * 0.22f) }
    val appear = seg(t, 0.02f, 0.12f) * reset
    when (target) {
        TextTarget.WORD -> {
            drawPaper(doc, c, alpha = appear)
            if (appear > 0f) drawFileTagBand(doc, WORD_BLUE, "W", f, appear)
        }
        TextTarget.SHEET -> drawSheet(sheet, c, rows = 5, cols = 3, filled = seg(t, 0.2f, 0.7f) * reset, alpha = appear)
        TextTarget.SLIDES -> slides.forEach { s ->
            drawRoundRect(c.shadow.copy(alpha = 0.16f * appear), s.topLeft + Offset(2f, 5f), s.size, CornerRadius(s.height * 0.06f))
            drawRoundRect(Color.White.copy(alpha = appear), s.topLeft, s.size, CornerRadius(s.height * 0.06f))
            drawRect(SLIDES_ORANGE.copy(alpha = 0.85f * appear), s.topLeft, Size(s.width * 0.04f, s.height))
        }
    }
    val stroke = source.height * 0.022f
    for (i in 0 until count) {
        val start = 0.16f + i * 0.1f
        val p = seg(t, start, start + 0.14f) * reset
        val y0 = lineY(source, i, count, 0.24f, 0.8f)
        val (l0, r0) = lineSpan(source, i)
        // Where this line ends up.
        val (endL, endR, endY) = when (target) {
            TextTarget.WORD -> {
                val (l, r) = lineSpan(doc, i)
                Triple(l, r, lineY(doc, i, count, 0.24f, 0.8f))
            }
            TextTarget.SHEET -> {
                val rh = sheet.height / 5
                Triple(sheet.left + sheet.width * 0.05f, sheet.left + sheet.width * 0.3f, sheet.top + rh * (i + 0.5f))
            }
            TextTarget.SLIDES -> {
                val s = slides[i % 3]
                val y = s.top + s.height * (if (i < 3) 0.3f else 0.65f)
                Triple(s.left + s.width * 0.14f, s.left + s.width * (if (i < 3) 0.7f else 0.55f), y)
            }
        }
        if (p <= 0f) {
            drawLine(c.inkSoft, Offset(l0, y0), Offset(r0, y0), stroke, StrokeCap.Round)
            continue
        }
        // A faint ghost stays where the line was, as the text is copied, not taken away.
        drawLine(c.inkSoft.copy(alpha = 0.18f), Offset(l0, y0), Offset(r0, y0), stroke, StrokeCap.Round)
        val lift = arc(p) * size.height * 0.16f
        val y = mixf(y0, endY, p) - lift
        val l = mixf(l0, endL, p)
        val r = mixf(r0, endR, p)
        if (p in 0.01f..0.99f) drawLine(c.shadow.copy(alpha = 0.25f), Offset(l + 4f, y + lift * 0.4f + 6f), Offset(r + 4f, y + lift * 0.4f + 6f), stroke, StrokeCap.Round)
        val colour = when (target) {
            TextTarget.WORD -> c.ink
            TextTarget.SHEET -> EXCEL_GREEN
            TextTarget.SLIDES -> if (i < 3) SLIDES_ORANGE else c.ink
        }
        drawLine(colour.copy(alpha = mixf(0.45f, 0.85f, p)), Offset(l, y), Offset(r, y), stroke * mixf(1f, if (target == TextTarget.SLIDES && i < 3) 1.4f else 1f, p), StrokeCap.Round)
    }
    val badgeAt = when (target) {
        TextTarget.WORD -> Offset(doc.right, doc.bottom)
        TextTarget.SHEET -> Offset(sheet.right, sheet.bottom)
        TextTarget.SLIDES -> Offset(slides[2].right, slides[2].bottom)
    }
    drawDoneBadge(badgeAt, size.minDimension * 0.06f, c, seg(t, 0.76f, 0.82f) * (1f - seg(t, 0.92f, 0.96f)))
}

/** A coloured band with a letter at the top of a document: "W" for Word. */
private fun DrawScope.drawFileTagBand(page: Rect, band: Color, letter: String, frame: DemoFrame, alpha: Float) {
    val tag = Rect(page.left - page.width * 0.06f, page.top + page.height * 0.06f, page.left + page.width * 0.2f, page.top + page.height * 0.18f)
    drawRoundRect(band.copy(alpha = alpha), tag.topLeft, tag.size, CornerRadius(tag.height * 0.2f))
    drawSmallText(letter, Offset(tag.left + tag.width * 0.3f, tag.top + tag.height * 0.06f), Color.White.copy(alpha = alpha), frame, scale = tag.height * 0.7f / size.minDimension / 1.25f, bold = true, maxWidth = tag.width)
}

val PdfToWordScene = textScene(TextTarget.WORD)
val PdfToExcelScene = textScene(TextTarget.SHEET)
val PdfToSlidesScene = textScene(TextTarget.SLIDES)

/**
 * OCR: a scanned page whose words are only a picture (grainy blocks). The mint scan line passes; behind it the blocks
 * turn into crisp text, and a finger presses a line, which highlights with selection handles: it can now be selected
 * and searched.
 */
val OcrScene = DemoScene(5_800) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(dy = size.height * 0.03f)
    drawPaper(page, c, paper = Color(0xFFF4F1EA))
    // The page turns back into a picture of text under a veil at the end, so the loop has no visible jump.
    val reset = 1f - seg(t, 0.93f, 0.95f)
    val scanY = mixf(page.top, page.bottom, seg(t, 0.12f, 0.5f))
    val read = seg(t, 0.12f, 0.5f) * reset
    val count = DEMO_LINES.size
    val stroke = page.height * 0.024f
    for (i in 0 until count) {
        val y = lineY(page, i)
        val (l, r) = lineSpan(page, i)
        val crisp = read > 0f && y < scanY || read >= 1f
        if (crisp) {
            drawLine(c.ink.copy(alpha = 0.75f), Offset(l, y), Offset(r, y), stroke * 0.8f, StrokeCap.Round)
        } else {
            // Picture text: grainy blocks, not letters.
            var x = l
            var k = 0
            while (x < r) {
                val w = page.width * (0.035f + 0.02f * ((i + k) % 3))
                drawRect(c.inkSoft.copy(alpha = 0.55f + 0.15f * ((i * 3 + k) % 2)), Offset(x, y - stroke * 0.6f), Size(minOf(w, r - x), stroke * 1.2f))
                x += w + page.width * 0.012f
                k++
            }
        }
    }
    drawScanPass(page, scanY, c, window(t, 0.1f, 0.52f, 0.03f) * reset)
    // Selecting a line: press, highlight, handles, and the "Copy" pill.
    val press = seg(t, 0.56f, 0.62f) * reset
    val line = 2
    val y = lineY(page, line)
    val (l, r) = lineSpan(page, line)
    if (press > 0f) {
        val selRight = mixf(l, r, seg(t, 0.6f, 0.7f))
        drawRect(c.accent.copy(alpha = 0.28f * press), Offset(l - stroke * 0.3f, y - stroke), Size(selRight - l + stroke * 0.6f, stroke * 2f))
        drawCircle(c.accent.copy(alpha = press), stroke * 0.7f, Offset(l - stroke * 0.3f, y + stroke * 1.4f))
        drawCircle(c.accent.copy(alpha = press), stroke * 0.7f, Offset(selRight + stroke * 0.3f, y + stroke * 1.4f))
        drawCopyChip(Offset((l + r) / 2f, y - page.height * 0.09f), page.width * 0.09f, c, seg(t, 0.68f, 0.72f) * (1f - seg(t, 0.88f, 0.92f)))
    }
    drawFinger(Offset(mixf(page.right, (l + r) / 2f, seg(t, 0.5f, 0.56f)), y + stroke * 2.5f), c, window(t, 0.5f, 0.74f), pressed = press > 0.5f && t < 0.7f)
    val veil = seg(t, 0.88f, 0.93f) * (1f - seg(t, 0.95f, 1f))
    if (veil > 0f) drawRect(c.surface.copy(alpha = veil), page.inflate(page.width * 0.12f).topLeft, page.inflate(page.width * 0.12f).size)
}

/** The "copy" chip of a text selection, drawn as its icon (two overlapping sheets) so it needs no words. */
private fun DrawScope.drawCopyChip(at: Offset, width: Float, c: DemoColours, alpha: Float) {
    if (alpha <= 0f) return
    val chip = Rect(at.x - width, at.y - width * 0.55f, at.x + width, at.y + width * 0.55f)
    drawRoundRect(c.ink.copy(alpha = alpha), chip.topLeft, chip.size, CornerRadius(chip.height / 2f))
    val s = chip.height * 0.36f
    drawRoundRect(c.paper.copy(alpha = alpha), Offset(at.x - s * 0.7f, at.y - s * 0.7f), Size(s, s), CornerRadius(s * 0.15f), style = Stroke(2f))
    drawRoundRect(c.paper.copy(alpha = alpha), Offset(at.x - s * 0.3f, at.y - s * 0.3f), Size(s, s), CornerRadius(s * 0.15f))
}

/** Extract images: a page with two pictures; the pictures lift straight up out of it and land beside it as files. */
val ExtractImagesScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.72f, dx = -size.width * 0.18f, dy = size.height * 0.04f)
    drawPaper(page, c)
    drawLines(page, c, count = 3, top = 0.2f, bottom = 0.34f)
    drawLines(page, c, count = 2, heading = false, top = 0.86f, bottom = 0.92f)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    val spots = listOf(
        Rect(page.left + page.width * 0.12f, page.top + page.height * 0.42f, page.left + page.width * 0.52f, page.top + page.height * 0.78f),
        Rect(page.left + page.width * 0.58f, page.top + page.height * 0.48f, page.right - page.width * 0.1f, page.top + page.height * 0.74f),
    )
    val tints = listOf(c.accent, c.mint)
    val targets = listOf(
        Rect(size.width * 0.6f, size.height * 0.14f, size.width * 0.86f, size.height * 0.44f),
        Rect(size.width * 0.64f, size.height * 0.54f, size.width * 0.92f, size.height * 0.84f),
    )
    spots.forEachIndexed { i, spot ->
        val start = 0.14f + i * 0.24f
        val lift = seg(t, start, start + 0.12f) * reset
        val fly = seg(t, start + 0.1f, start + 0.26f) * reset
        // The hole the picture leaves, outlined, so it reads as lifted out rather than erased.
        if (lift > 0f) {
            drawRect(c.paper, spot.topLeft, spot.size)
            drawRect(c.accent.copy(alpha = 0.4f * lift), spot.topLeft, spot.size, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
        }
        val raised = spot.translate(Offset(0f, -lift * size.height * 0.05f)).inflate(lift * spot.width * 0.04f)
        val now = lerpRect(raised, targets[i], fly).translate(Offset(0f, -arc(fly) * size.height * 0.1f))
        if (fly > 0.3f) drawPhotoCard(now, c, lift = arc(fly), alpha = seg(fly, 0.3f, 0.6f), tint = tints[i])
        else {
            if (lift > 0f) drawRect(c.shadow.copy(alpha = 0.3f * lift), now.topLeft + Offset(4f, 8f * lift), now.size)
            drawPhoto(now, c, tint = tints[i])
        }
        drawPill(if (i == 0) "JPG" else "PNG", Offset(targets[i].center.x, targets[i].bottom + size.height * 0.04f), c, f, seg(t, start + 0.26f, start + 0.3f) * (1f - seg(t, 0.92f, 0.96f)))
    }
}

/** PDF info: a magnifier passes over the page and the facts it finds tick in on a card beside it, one by one. */
val PdfInfoScene = DemoScene(5_600) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(scale = 0.7f, dx = -size.width * 0.2f, dy = size.height * 0.03f)
    drawPaper(page, c)
    drawTextLines(page, c)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    // The magnifier travels along a gentle S across the page.
    val travel = seg(t, 0.06f, 0.62f)
    val at = Offset(mixf(page.left + page.width * 0.25f, page.right - page.width * 0.2f, travel), mixf(page.top + page.height * 0.25f, page.bottom - page.height * 0.25f, travel) + sin(travel * 2 * PI).toFloat() * page.height * 0.08f)
    drawMagnifier(at, page.width * 0.2f, c, window(t, 0.04f, 0.68f) * reset)
    // The facts card.
    val card = Rect(size.width * 0.56f, size.height * 0.16f, size.width * 0.96f, size.height * 0.86f)
    val cardIn = seg(t, 0.04f, 0.12f) * reset
    drawRoundRect(c.shadow.copy(alpha = 0.18f * cardIn), card.topLeft + Offset(3f, 6f), card.size, CornerRadius(card.width * 0.06f))
    drawRoundRect(c.surface.copy(alpha = cardIn), card.topLeft, card.size, CornerRadius(card.width * 0.06f))
    // Language-neutral facts: pages 1 to 12, the paper, the size and the version.
    val facts = listOf("1–12", "A4 · 210×297", "1.2 MB", "PDF 1.7")
    facts.forEachIndexed { i, fact ->
        val shown = seg(t, 0.14f + i * 0.12f, 0.2f + i * 0.12f) * reset
        val rowY = card.top + card.height * (0.12f + 0.22f * i)
        drawDoneBadge(Offset(card.left + card.width * 0.13f, rowY + card.height * 0.045f), card.width * 0.06f, c, shown)
        drawSmallText(fact, Offset(card.left + card.width * 0.25f + (1f - shown) * card.width * 0.1f, rowY), c.onSurface.copy(alpha = shown), f, scale = 0.05f, bold = i == 0, maxWidth = card.width * 0.72f)
    }
}

/**
 * Repair: the page lies torn in two along a jagged line, the halves apart and askew; they slide together, a mint
 * stitch runs along the tear, a flash and the page is whole.
 */
val RepairScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(dy = size.height * 0.03f)
    // The page comes apart again at the very end, so the loop starts where it ends.
    val reset = 1f - seg(t, 0.86f, 0.9f)
    val join = seg(t, 0.14f, 0.42f) * (1f - seg(t, 0.9f, 1f))
    val apart = 1f - join
    // The tear: a zigzag down the page, a little left of centre.
    val teeth = 7
    fun tearX(k: Int): Float = page.left + page.width * (0.48f + if (k % 2 == 0) -0.05f else 0.05f)
    fun tearY(k: Int): Float = page.top + page.height * k / teeth
    val left = Path().apply {
        moveTo(page.left, page.top)
        for (k in 0..teeth) lineTo(tearX(k), tearY(k))
        lineTo(page.left, page.bottom)
        close()
    }
    val right = Path().apply {
        moveTo(page.right, page.top)
        for (k in 0..teeth) lineTo(tearX(k), tearY(k))
        lineTo(page.right, page.bottom)
        close()
    }
    val whole = seg(t, 0.56f, 0.62f) * reset
    if (whole < 1f) {
        listOf(left to -1f, right to 1f).forEach { (half, side) ->
            val dx = side * apart * page.width * 0.12f
            val turn = side * apart * 7f
            rotate(turn, Offset(page.center.x + dx, page.bottom)) {
                translateBy(dx) {
                    drawPath(half, c.shadow.copy(alpha = 0.14f + 0.1f * apart))
                    clipPathRect(half) {
                        drawPaper(page, c, lift = apart)
                        drawTextLines(page, c)
                    }
                }
            }
        }
    }
    // The stitch along the tear while it closes.
    val stitch = seg(t, 0.4f, 0.56f) * reset
    if (stitch > 0f && whole < 1f) {
        val upTo = (teeth * stitch).roundToInt().coerceAtLeast(1)
        for (k in 0 until upTo) {
            drawLine(c.mint, Offset(tearX(k), tearY(k)), Offset(tearX(k + 1), tearY(k + 1)), page.width * 0.022f, StrokeCap.Round)
        }
    }
    if (whole > 0f) {
        drawPaper(page, c, alpha = whole)
        drawTextLines(page, c, alpha = whole)
    }
    val flash = window(t, 0.54f, 0.64f, 0.03f) * reset
    if (flash > 0f) drawRoundRect(c.mint.copy(alpha = 0.35f * flash), page.topLeft, page.size, CornerRadius(page.width * 0.04f))
    drawDoneBadge(Offset(page.right, page.bottom), page.width * 0.11f, c, seg(t, 0.62f, 0.68f) * reset)
}

/** Translates the drawing by [dx] horizontally. */
private inline fun DrawScope.translateBy(dx: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, 0f)
    block()
    drawContext.transform.translate(-dx, 0f)
}

/** Draws [block] clipped to [path]. */
private inline fun DrawScope.clipPathRect(path: Path, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.transform.clipPath(path)
    block()
    drawContext.canvas.restore()
}

/** Fill forms: a finger taps each field in turn; text types into two of them and a box gets its tick. */
val FillFormScene = DemoScene(5_800) { f ->
    val c = f.c
    val t = f.t
    val page = mainPage(dy = size.height * 0.03f)
    drawPaper(page, c)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    drawLine(c.accent.copy(alpha = 0.75f), Offset(page.left + page.width * 0.14f, page.top + page.height * 0.12f), Offset(page.left + page.width * 0.56f, page.top + page.height * 0.12f), page.height * 0.03f, StrokeCap.Round)
    val fields = listOf(
        Rect(page.left + page.width * 0.12f, page.top + page.height * 0.24f, page.right - page.width * 0.12f, page.top + page.height * 0.33f),
        Rect(page.left + page.width * 0.12f, page.top + page.height * 0.44f, page.right - page.width * 0.3f, page.top + page.height * 0.53f),
    )
    val box = Rect(page.left + page.width * 0.12f, page.top + page.height * 0.66f, page.left + page.width * 0.24f, page.top + page.height * 0.66f + page.width * 0.12f)
    // Field labels.
    listOf(fields[0].top, fields[1].top, box.top).forEach { y ->
        drawLine(c.inkSoft, Offset(page.left + page.width * 0.12f, y - page.height * 0.035f), Offset(page.left + page.width * 0.4f, y - page.height * 0.035f), page.height * 0.018f, StrokeCap.Round)
    }
    drawLine(c.inkSoft, Offset(box.right + page.width * 0.06f, box.center.y), Offset(page.right - page.width * 0.2f, box.center.y), page.height * 0.018f, StrokeCap.Round)
    val taps = listOf(0.1f, 0.36f, 0.62f)
    val texts = listOf("Asha Rao", "12/04/1990")
    fields.forEachIndexed { i, field ->
        val active = window(t, taps[i] + 0.04f, taps[i + 1] + 0.02f) * reset
        drawRoundRect(c.paper, field.topLeft, field.size, CornerRadius(page.width * 0.015f))
        drawRoundRect((if (active > 0f) c.accent else c.inkSoft).copy(alpha = 0.4f + 0.5f * active), field.topLeft, field.size, CornerRadius(page.width * 0.015f), style = Stroke(1.4f + 1.2f * active))
        val typed = (texts[i].length * seg(t, taps[i] + 0.06f, taps[i] + 0.22f) * reset).toInt()
        if (typed > 0) {
            drawSmallText(texts[i].take(typed), Offset(field.left + field.width * 0.05f, field.top + field.height * 0.12f), c.ink, f, scale = field.height * 0.62f / size.minDimension / 1.25f, maxWidth = field.width * 0.9f)
        }
    }
    val ticked = seg(t, taps[2] + 0.06f, taps[2] + 0.12f) * reset
    drawRoundRect(c.paper, box.topLeft, box.size, CornerRadius(box.width * 0.15f))
    drawRoundRect(c.accent.copy(alpha = 0.6f), box.topLeft, box.size, CornerRadius(box.width * 0.15f), style = Stroke(1.6f))
    if (ticked > 0f) {
        val tick = Path().apply {
            moveTo(box.left + box.width * 0.22f, box.center.y)
            lineTo(box.left + box.width * 0.44f, box.bottom - box.height * 0.24f)
            lineTo(box.left + box.width * mixf(0.44f, 0.82f, ticked), box.bottom - box.height * mixf(0.24f, 0.74f, ticked))
        }
        drawPath(tick, c.accent, style = Stroke(box.width * 0.14f, cap = StrokeCap.Round))
    }
    // The finger moves to each field and taps.
    val targetsAt = listOf(fields[0].center, fields[1].center, box.center)
    var finger: Offset? = null
    var pressed = false
    taps.forEachIndexed { i, tap ->
        if (t in (tap - 0.08f)..(tap + 0.12f)) {
            val from = if (i == 0) Offset(page.right + page.width * 0.2f, page.bottom) else targetsAt[i - 1]
            finger = Offset(mixf(from.x, targetsAt[i].x, seg(t, tap - 0.08f, tap)), mixf(from.y, targetsAt[i].y, seg(t, tap - 0.08f, tap)) + page.height * 0.04f)
            pressed = t in tap..(tap + 0.04f)
        }
    }
    finger?.let { drawFinger(it, c, reset, pressed) }
    drawDoneBadge(Offset(page.right, page.bottom), page.width * 0.11f, c, seg(t, 0.8f, 0.86f) * (1f - seg(t, 0.92f, 0.96f)))
}

// ---------------------------------------------------------------------------------------------------------------
// Image tools
// ---------------------------------------------------------------------------------------------------------------

/** Crop image: a frame with corner handles tightens round the subject, the outside dims, and the picture fills out. */
val CropImageScene = DemoScene(5_200, tilt = true) { f ->
    val c = f.c
    val t = f.t
    val full = Rect(size.width * 0.18f, size.height * 0.14f, size.width * 0.82f, size.height * 0.86f)
    val tight = Rect(size.width * 0.36f, size.height * 0.26f, size.width * 0.76f, size.height * 0.74f)
    val reset = 1f - seg(t, 0.92f, 0.99f)
    val squeeze = seg(t, 0.14f, 0.46f) * reset
    val apply = seg(t, 0.56f, 0.7f) * reset
    val frame = lerpRect(full, tight, squeeze)
    drawRoundRect(c.shadow, full.topLeft + Offset(4f, 8f), full.size, CornerRadius(8f))
    if (apply < 1f) {
        drawPhoto(full, c, alpha = 1f - apply)
        // The dimmed outside.
        val dim = c.ink.copy(alpha = 0.45f * squeeze * (1f - apply))
        drawRect(dim, full.topLeft, Size(full.width, frame.top - full.top))
        drawRect(dim, Offset(full.left, frame.bottom), Size(full.width, full.bottom - frame.bottom))
        drawRect(dim, Offset(full.left, frame.top), Size(frame.left - full.left, frame.height))
        drawRect(dim, Offset(frame.right, frame.top), Size(full.right - frame.right, frame.height))
        drawRect(Color.White.copy(alpha = 1f - apply), frame.topLeft, frame.size, style = Stroke(2f))
        drawHandles(frame, Color.White.copy(alpha = 1f - apply), frame.minDimension * 0.14f, 5f)
    }
    if (apply > 0f) {
        // The crop, scaled up from where it was to fill the card.
        val grown = lerpRect(frame, full, apply)
        clipRect(grown.left, grown.top, grown.right, grown.bottom) {
            val k = grown.width / frame.width
            val origin = Offset(grown.left - (frame.left - full.left) * k, grown.top - (frame.top - full.top) * k)
            drawPhoto(Rect(origin, Size(full.width * k, full.height * k)), c, alpha = apply)
        }
    }
    val corner = frame.topLeft
    drawFinger(corner + Offset(6f, 6f), c, window(t, 0.1f, 0.5f), pressed = squeeze in 0.02f..0.98f)
    drawDoneBadge(Offset(full.right, full.bottom), size.minDimension * 0.06f, c, seg(t, 0.7f, 0.76f) * (1f - seg(t, 0.92f, 0.96f)))
}

/** Compress image: the photo card is squeezed; the size counter runs down from 4.2 MB to 380 kB and a badge says how much smaller. */
val CompressImageScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val reset = 1f - seg(t, 0.92f, 0.99f)
    val squeeze = seg(t, 0.16f, 0.6f) * reset
    val pulse = 1f - 0.08f * arc(seg(t, 0.16f, 0.6f)) * reset
    val card = Rect(size.width * 0.24f, size.height * 0.12f, size.width * 0.66f, size.height * 0.8f)
    scale(pulse, pulse, card.center) { drawPhotoCard(card, c) }
    // Squeeze arrows on both sides while it works.
    val arrows = window(t, 0.14f, 0.62f) * reset
    if (arrows > 0f) {
        val inset = card.width * 0.08f * arc(seg(t, 0.16f, 0.6f))
        listOf(card.left - card.width * 0.14f + inset to 1f, card.right + card.width * 0.14f - inset to -1f).forEach { (x, dir) ->
            val y = card.center.y
            drawLine(c.accent.copy(alpha = arrows), Offset(x - dir * card.width * 0.1f, y), Offset(x, y), 4f, StrokeCap.Round)
            drawLine(c.accent.copy(alpha = arrows), Offset(x, y), Offset(x - dir * card.width * 0.05f, y - card.width * 0.05f), 4f, StrokeCap.Round)
            drawLine(c.accent.copy(alpha = arrows), Offset(x, y), Offset(x - dir * card.width * 0.05f, y + card.width * 0.05f), 4f, StrokeCap.Round)
        }
    }
    // The size counter: 4200 kB down to 380 kB.
    val kb = mixf(4200f, 380f, squeeze).roundToInt()
    val label = if (kb >= 1000) "%.1f MB".format(java.util.Locale.ROOT, kb / 1000f) else "$kb kB"
    drawPill(label, Offset(size.width * 0.82f, size.height * 0.36f), c, f, background = if (squeeze >= 1f) c.mint else c.surface, foreground = if (squeeze >= 1f) Color(0xFF04281E) else c.onSurface)
    drawPill("−91%", Offset(size.width * 0.82f, size.height * 0.56f), c, f, seg(t, 0.6f, 0.66f) * (1f - seg(t, 0.92f, 0.96f)))
}

/** Convert image: the photo's format badge flips over like a card, PNG to JPG to WebP. */
val ConvertImageScene = DemoScene(5_400) { f ->
    val c = f.c
    val t = f.t
    val card = Rect(size.width * 0.22f, size.height * 0.12f, size.width * 0.64f, size.height * 0.82f)
    drawPhotoCard(card, c)
    val formats = listOf("PNG", "JPG", "WebP")
    val colours = listOf(c.accent, PDF_RED.copy(red = 0.85f), c.mint)
    // Two flips: at 0.25 and at 0.55. Each turns the badge edge-on (scaleX → 0) and back with the next label.
    val flip1 = seg(t, 0.2f, 0.34f)
    val flip2 = seg(t, 0.5f, 0.64f)
    val reset = seg(t, 0.92f, 0.99f)
    val step = when {
        reset > 0.5f -> 0
        flip2 >= 0.5f -> 2
        flip1 >= 0.5f -> 1
        else -> 0
    }
    val turning = maxOf(arc(flip1), arc(flip2), arc(reset))
    val badge = Offset(card.right, card.top + card.height * 0.16f)
    scale(scaleX = (1f - turning).coerceAtLeast(0.02f), scaleY = 1f, pivot = badge) {
        drawPill(formats[step], badge, c, f, background = colours[step], foreground = if (step == 2) Color(0xFF04281E) else Color.White)
    }
    drawPill(listOf("2.4 MB", "640 kB", "410 kB")[step], Offset(card.center.x, card.bottom + size.height * 0.07f), c, f, background = c.surface, foreground = c.onSurface)
}

/** Resize image: a finger drags the corner handle in; the picture scales with it and the pixel size counts down. */
val ResizeImageScene = DemoScene(5_200) { f ->
    val c = f.c
    val t = f.t
    val reset = 1f - seg(t, 0.92f, 0.99f)
    val drag = seg(t, 0.14f, 0.52f) * reset
    val full = Rect(size.width * 0.14f, size.height * 0.12f, size.width * 0.74f, size.height * 0.84f)
    val k = mixf(1f, 0.6f, drag)
    val now = Rect(full.left, full.top, full.left + full.width * k, full.top + full.height * k)
    // The original's outline stays, dashed, to show how much smaller it got.
    if (drag > 0f) drawRect(c.inkSoft.copy(alpha = 0.6f * drag), full.topLeft, full.size, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
    drawRoundRect(c.shadow, now.topLeft + Offset(4f, 8f), now.size, CornerRadius(6f))
    drawPhoto(now, c)
    drawRect(c.accent, now.topLeft, now.size, style = Stroke(2f))
    drawHandles(now, c.accent, now.minDimension * 0.12f, 6f)
    drawFinger(now.bottomRight, c, window(t, 0.08f, 0.6f), pressed = drag in 0.02f..0.98f)
    val w = mixf(4000f, 1600f, drag).roundToInt()
    val h = mixf(3000f, 1200f, drag).roundToInt()
    drawPill("$w × $h", Offset(size.width * 0.82f, size.height * 0.2f), c, f, background = c.surface, foreground = c.onSurface)
    drawDoneBadge(Offset(now.right, now.top), size.minDimension * 0.05f, c, seg(t, 0.56f, 0.62f) * (1f - seg(t, 0.92f, 0.96f)))
}
