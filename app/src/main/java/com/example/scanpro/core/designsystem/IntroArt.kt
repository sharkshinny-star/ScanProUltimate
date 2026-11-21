package com.example.scanpro.core.designsystem

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * The large, realistic pictures on the welcome pages and the camera explainers (2026-11-16, owner: the old ones were
 * "not clear"). Each is a small scene drawn in Compose (a desk, real-looking paper, a phone, a card, an open book) on
 * its own background, so it reads the same in light and dark themes and stays crisp at any size. Only a scan line or
 * the brackets move; with animations turned off every picture holds a still frame. Text drawn inside a picture ("ID
 * CARD", the MRZ, receipt numbers) is decoration on a specimen, not interface text, so it is not translated.
 */

private val WoodLight = Color(0xFFA9774F)
private val WoodDark = Color(0xFF6E4429)
private val WoodGrain = Color(0xFF4E2D18)
private val Paper = Color(0xFFFCFCF9)
private val PaperEdge = Color(0xFFE3E1DA)
private val Ink = Color(0xFF262A33)
private val InkSoft = Color(0xFF8E94A0)
private val InkFaint = Color(0xFFC9CDD4)
private val ScanBlue = Color(0xFF2F7BFF)
private val IdGreen = Color(0xFF1D8A57)
private val Skin = Color(0xFFE9B892)
private val SkinShade = Color(0xFFC68B66)
private val PdfRed = Color(0xFFD9472B)

/** Book mode's letters for the (left, right) halves of a spread: A marks the page saved first. */
fun bookHalfLetters(leftFirst: Boolean): Pair<String, String> = if (leftFirst) "A" to "B" else "B" to "A"

@Composable
private fun loop(label: String, millis: Int = 2_400): Float {
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = label)
    val value = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = label
    )
    return if (motion) value.value else 0.55f
}

// ---------------------------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------------------------

/** Welcome page 1: a phone held over a bill on a desk, the bill on its screen with blue brackets and a scan line. */
@Composable
fun PhoneScanArt(modifier: Modifier = Modifier) {
    val beam = loop("phoneScan")
    Canvas(modifier.fillMaxSize()) {
        drawDesk()
        // The real bill on the desk, larger than the phone and partly under it.
        val billW = min(size.width * 0.62f, size.height * 0.62f)
        val bill = Rect(Offset(maxOf(size.width * 0.5f - billW * 0.95f, size.width * 0.06f), size.height * 0.2f), Size(billW, billW * 1.3f))
        rotate(-8f, bill.center) { drawPaperShadow(bill, billW * 0.03f); drawBill(bill) }
        // The phone.
        val phoneH = size.height * 0.84f
        val phoneW = phoneH * 0.5f
        val phone = Rect(Offset(size.width * 0.56f - phoneW / 2f, size.height * 0.08f), Size(phoneW, phoneH))
        drawPhone(phone) { screen ->
            drawRect(Color(0xFF3A3F47), screen.topLeft, screen.size)
            val page = Rect(Offset(screen.left + screen.width * 0.1f, screen.top + screen.height * 0.14f), Size(screen.width * 0.8f, screen.width * 0.8f * 1.3f))
            drawBill(page)
            drawScanBeam(page, beam)
            drawBrackets(page.inflate(screen.width * 0.04f), screen.width * 0.12f, screen.width * 0.025f, ScanBlue)
            // Shutter.
            val shutter = Offset(screen.center.x, screen.bottom - screen.width * 0.16f)
            drawCircle(Color.White, screen.width * 0.1f, shutter)
            drawCircle(Color(0xFF3A3F47), screen.width * 0.08f, shutter, style = Stroke(screen.width * 0.012f))
        }
    }
}

/** Welcome page 2: four scanned receipts laid out in a grid on the desk, numbered 01 to 04. */
@Composable
fun ReceiptGridArt(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        drawDesk()
        val cellH = size.height * 0.42f
        val cellW = min(cellH * 0.72f, size.width * 0.34f)
        val gap = cellW * 0.16f
        val gridW = cellW * 2 + gap
        val gridH = cellH * 2 + gap * 0.6f
        val origin = Offset((size.width - gridW) / 2f, (size.height - gridH) / 2f)
        val tilts = floatArrayOf(-3f, 2.5f, 2f, -2.5f)
        for (i in 0 until 4) {
            val col = i % 2
            val row = i / 2
            val rect = Rect(Offset(origin.x + col * (cellW + gap), origin.y + row * (cellH + gap * 0.6f)), Size(cellW, cellH))
            rotate(tilts[i], rect.center) {
                drawPaperShadow(rect, cellW * 0.04f)
                drawReceipt(rect, seed = i)
                // Number badge.
                val badge = cellW * 0.16f
                val centre = Offset(rect.left + badge * 0.95f, rect.top + badge * 0.95f)
                drawCircle(ScanBlue, badge * 0.78f, centre)
                drawCentredText(measurer, "0${i + 1}", centre, badge * 0.72f, Color.White, FontWeight.Bold)
            }
        }
    }
}

/** Welcome page 3: a PDF with tool chips around it and a shield: everything happens on the phone. */
@Composable
fun PrivateToolsArt(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val tools = listOf(
        rememberVectorPainter(Icons.AutoMirrored.Rounded.CallMerge) to Color(0xFF4457E6),
        rememberVectorPainter(Icons.Rounded.Draw) to Color(0xFF7D4CDB),
        rememberVectorPainter(Icons.Rounded.Compress) to Color(0xFF0E9C8A),
        rememberVectorPainter(Icons.Rounded.Lock) to Color(0xFFDB7B12)
    )
    val offline = rememberVectorPainter(Icons.Rounded.WifiOff)
    val pulse = loop("tools", 2_000)
    Canvas(modifier.fillMaxSize()) {
        drawDesk()
        val docH = min(size.height * 0.7f, size.width * 0.46f / 0.76f)
        val doc = Rect(Offset(size.width / 2f - docH * 0.38f, size.height * 0.13f), Size(docH * 0.76f, docH))
        drawPaperShadow(doc, docH * 0.03f)
        drawBill(doc)
        // PDF tag.
        val tag = Rect(Offset(doc.left - doc.width * 0.1f, doc.bottom - doc.height * 0.26f), Size(doc.width * 0.5f, doc.height * 0.15f))
        drawRoundRect(PdfRed, tag.topLeft, tag.size, CornerRadius(tag.height * 0.18f))
        drawCentredText(measurer, "PDF", tag.center, tag.height * 0.6f, Color.White, FontWeight.Black)
        // Tool chips at the four corners.
        val chip = min(size.height * 0.15f, size.width * 0.13f)
        val spots = listOf(
            Offset(doc.left - chip * 0.9f, doc.top + chip * 0.6f),
            Offset(doc.right + chip * 0.9f, doc.top + chip * 0.6f),
            Offset(doc.left - chip * 0.9f, doc.center.y + chip * 0.2f),
            Offset(doc.right + chip * 0.9f, doc.center.y + chip * 0.2f)
        )
        tools.forEachIndexed { i, (painter, colour) ->
            val c = spots[i]
            val r = chip / 2f * (1f + 0.04f * pulse * (if (i % 2 == 0) 1f else -1f))
            drawCircle(Color.Black.copy(alpha = 0.25f), r, c + Offset(0f, r * 0.12f))
            drawCircle(Color.White, r, c)
            val icon = r * 1.05f
            translate(c.x - icon / 2f, c.y - icon / 2f) { with(painter) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(colour)) } }
        }
        // The shield: nothing leaves the phone.
        val shieldW = size.height * 0.26f
        val sc = Offset(doc.right - shieldW * 0.1f, doc.bottom - shieldW * 0.35f)
        val shield = Path().apply {
            moveTo(sc.x, sc.y - shieldW * 0.55f)
            lineTo(sc.x + shieldW * 0.45f, sc.y - shieldW * 0.38f)
            cubicTo(sc.x + shieldW * 0.45f, sc.y + shieldW * 0.15f, sc.x + shieldW * 0.25f, sc.y + shieldW * 0.42f, sc.x, sc.y + shieldW * 0.58f)
            cubicTo(sc.x - shieldW * 0.25f, sc.y + shieldW * 0.42f, sc.x - shieldW * 0.45f, sc.y + shieldW * 0.15f, sc.x - shieldW * 0.45f, sc.y - shieldW * 0.38f)
            close()
        }
        translate(0f, shieldW * 0.06f) { drawPath(shield, Color.Black.copy(alpha = 0.3f)) }
        drawPath(shield, Brush.verticalGradient(listOf(Color(0xFF22C38E), Color(0xFF0E8F68)), sc.y - shieldW * 0.55f, sc.y + shieldW * 0.58f))
        drawPath(shield, Color.White.copy(alpha = 0.9f), style = Stroke(shieldW * 0.04f))
        val icon = shieldW * 0.5f
        translate(sc.x - icon / 2f, sc.y - icon / 2f) { with(offline) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color.White)) } }
    }
}

/** The camera's "How to scan" picture: the built-in sample page on a desk, with the brackets locking on to it. */
@Composable
fun SampleDocumentArt(modifier: Modifier = Modifier) {
    val beam = loop("sampleDoc")
    Canvas(modifier.fillMaxSize()) {
        val page = drawSampleScene()
        drawScanBeam(page, beam)
        drawBrackets(page.inflate(size.minDimension * 0.035f), size.minDimension * 0.09f, 4.dp.toPx(), ScanBlue)
    }
}

/** ID card mode: the front (green "ID CARD" header, photo, chip) and the back (barcode, MRZ) side by side. */
@Composable
fun IdCardArt(frontLabel: String, backLabel: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val beam = loop("idCard", 2_600)
    Canvas(modifier.fillMaxSize()) {
        drawDesk(dark = true)
        val gap = size.width * 0.05f
        val cardW = min((size.width - gap * 3f) / 2f, size.height * 0.62f / 0.63f)
        val cardH = cardW * 0.63f
        val top = (size.height - cardH) / 2f + size.height * 0.06f
        val left = (size.width - cardW * 2 - gap) / 2f
        val front = Rect(Offset(left, top), Size(cardW, cardH))
        val back = Rect(Offset(left + cardW + gap, top), Size(cardW, cardH))
        listOf(front to frontLabel, back to backLabel).forEach { (rect, label) ->
            drawPaperShadow(rect, cardW * 0.03f)
            val labelCentre = Offset(rect.center.x, rect.top - cardH * 0.2f)
            val laid = measurer.measure(label, TextStyle(fontSize = (cardH * 0.13f).toSp(), fontWeight = FontWeight.Bold, color = Color.White))
            val pill = Size(laid.size.width + cardH * 0.22f, laid.size.height + cardH * 0.06f)
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(labelCentre.x - pill.width / 2f, labelCentre.y - pill.height / 2f), pill, CornerRadius(pill.height / 2f))
            drawText(laid, topLeft = Offset(labelCentre.x - laid.size.width / 2f, labelCentre.y - laid.size.height / 2f))
        }
        drawIdFront(front, measurer)
        drawIdBack(back, measurer)
        // The scan line passes over the front, then the back.
        val target = if (beam < 0.5f) front else back
        drawScanBeam(target, if (beam < 0.5f) beam * 2f else (beam - 0.5f) * 2f)
    }
}

/**
 * Book mode: an open book held flat by two hands on the desk, the spine marked with a dashed line and the halves
 * lettered A and B (A is saved first), as the viewfinder shows them.
 */
@Composable
fun OpenBookArt(modifier: Modifier = Modifier, leftFirst: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val beam = loop("book", 2_800)
    Canvas(modifier.fillMaxSize()) {
        drawDesk()
        val bookW = min(size.width * 0.74f, size.height * 1.2f)
        val bookH = bookW * 0.62f
        val book = Rect(Offset((size.width - bookW) / 2f, (size.height - bookH) / 2f - size.height * 0.02f), Size(bookW, bookH))
        drawOpenBook(book)
        // Spine split line and letters.
        val cx = book.center.x
        drawLine(
            ScanBlue, Offset(cx, book.top - bookH * 0.06f), Offset(cx, book.bottom + bookH * 0.04f), 3.dp.toPx(),
            StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
        val (leftLetter, rightLetter) = bookHalfLetters(leftFirst)
        val letterSize = bookH * 0.42f
        drawCentredText(measurer, leftLetter, Offset(book.left + bookW * 0.27f, book.center.y), letterSize, ScanBlue.copy(alpha = 0.5f), FontWeight.Black)
        drawCentredText(measurer, rightLetter, Offset(book.right - bookW * 0.27f, book.center.y), letterSize, ScanBlue.copy(alpha = 0.5f), FontWeight.Black)
        drawBrackets(book.inflate(bookW * 0.03f), bookW * 0.08f, 3.5.dp.toPx(), lerpColour(Color.White.copy(alpha = 0.6f), ScanBlue, beam))
    }
}

/**
 * Draws the built-in sample page (the "How to scan" sample) to a bitmap: a page lying slightly turned on a wooden desk,
 * so the normal edge detection, crop and filters have something real to work on. [width] x [height] pixels.
 */
fun renderSampleScanBitmap(width: Int = 1500, height: Int = 2000): Bitmap {
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawSampleScene()
    }
    return image.asAndroidBitmap()
}

// ---------------------------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------------------------

private fun lerpColour(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** Wooden desk filling the whole area, with grain and a soft vignette. */
private fun DrawScope.drawDesk(dark: Boolean = false) {
    val top = if (dark) Color(0xFF2E3440) else WoodLight
    val bottom = if (dark) Color(0xFF1C2028) else WoodDark
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val grain = if (dark) Color.White.copy(alpha = 0.04f) else WoodGrain.copy(alpha = 0.18f)
    val rows = 16
    val stroke = Stroke(width = (size.height / 260f).coerceAtLeast(1f))
    for (i in 0 until rows) {
        val y = size.height * (i + 0.5f) / rows
        val wave = size.height / rows * 0.25f * (if (i % 2 == 0) 1f else -1f)
        val path = Path().apply {
            moveTo(0f, y)
            cubicTo(size.width * 0.3f, y + wave, size.width * 0.6f, y - wave, size.width, y + wave * 0.5f)
        }
        drawPath(path, grain, style = stroke)
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), center = center, radius = size.maxDimension * 0.75f))
}

/** A soft, slightly offset shadow under a sheet or card. */
private fun DrawScope.drawPaperShadow(rect: Rect, spread: Float) {
    for (i in 1..4) {
        val g = spread * i / 2f
        drawRoundRect(
            Color.Black.copy(alpha = 0.07f),
            Offset(rect.left - g * 0.5f, rect.top + spread * 0.8f - g * 0.3f),
            Size(rect.width + g, rect.height + g),
            CornerRadius(g + spread * 0.3f)
        )
    }
}

/** A bill or letter: logo and title, address block, a table of items with amounts and a highlighted total. */
private fun DrawScope.drawBill(rect: Rect) {
    drawRect(Paper, rect.topLeft, rect.size)
    drawRect(PaperEdge, rect.topLeft, rect.size, style = Stroke(rect.width * 0.004f))
    val w = rect.width
    val pad = w * 0.09f
    val line = w * 0.028f
    fun bar(x: Float, y: Float, width: Float, colour: Color, h: Float = line) =
        drawRoundRect(colour, Offset(rect.left + x, rect.top + y), Size(width, h), CornerRadius(h / 2f))
    // Header: logo square, title, and the date block on the right.
    drawRoundRect(ScanBlue, Offset(rect.left + pad, rect.top + pad), Size(w * 0.12f, w * 0.12f), CornerRadius(w * 0.02f))
    bar(pad + w * 0.16f, pad + w * 0.015f, w * 0.3f, Ink, line * 1.5f)
    bar(pad + w * 0.16f, pad + w * 0.075f, w * 0.2f, InkSoft)
    bar(w * 0.62f, pad + w * 0.01f, w * 0.29f, InkSoft)
    bar(w * 0.7f, pad + w * 0.06f, w * 0.21f, InkSoft)
    // Address.
    var y = pad + w * 0.22f
    floatArrayOf(0.34f, 0.28f, 0.3f).forEach { bar(pad, y, w * it, InkSoft); y += line * 1.9f }
    // Table header and rows.
    y += line * 1.2f
    drawRect(Color(0xFFE8EEF9), Offset(rect.left + pad * 0.7f, rect.top + y - line * 0.6f), Size(w - pad * 1.4f, line * 2.2f))
    bar(pad, y, w * 0.24f, Ink); bar(w * 0.72f, y, w * 0.19f, Ink)
    y += line * 2.7f
    val items = floatArrayOf(0.46f, 0.38f, 0.5f, 0.32f, 0.42f)
    items.forEachIndexed { i, fraction ->
        if (rect.top + y + line * 4f > rect.bottom) return@forEachIndexed
        bar(pad, y, w * fraction, InkSoft)
        bar(w * (0.78f - (i % 2) * 0.04f), y, w * (0.13f + (i % 2) * 0.04f), Ink.copy(alpha = 0.75f))
        y += line * 2.1f
        drawLine(InkFaint, Offset(rect.left + pad, rect.top + y - line * 0.6f), Offset(rect.right - pad, rect.top + y - line * 0.6f), w * 0.003f)
    }
    // Total.
    if (rect.top + y + line * 3f < rect.bottom) {
        y += line * 0.6f
        bar(w * 0.5f, y, w * 0.16f, Ink, line * 1.4f)
        bar(w * 0.72f, y, w * 0.19f, ScanBlue, line * 1.4f)
    }
    // Footer lines.
    val footer = rect.height - pad - line * 3.4f
    if (footer > y + line * 3f) {
        bar(pad, footer, w * 0.7f, InkFaint)
        bar(pad, footer + line * 1.8f, w * 0.5f, InkFaint)
    }
}

/** A till receipt: narrow, zig-zag torn bottom, centred heading, dashed separators and a bold total. */
private fun DrawScope.drawReceipt(rect: Rect, seed: Int) {
    val w = rect.width
    val teeth = 9
    val tooth = w / teeth
    val body = Path().apply {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom - tooth * 0.5f)
        for (i in teeth downTo 1) {
            lineTo(rect.left + (i - 0.5f) * tooth, rect.bottom)
            lineTo(rect.left + (i - 1) * tooth, rect.bottom - tooth * 0.5f)
        }
        close()
    }
    drawPath(body, Paper)
    val line = w * 0.045f
    val pad = w * 0.12f
    fun bar(x: Float, y: Float, width: Float, colour: Color, h: Float = line) =
        drawRoundRect(colour, Offset(rect.left + x, rect.top + y), Size(width, h), CornerRadius(h / 2f))
    bar((w - w * 0.44f) / 2f + w * 0.08f, w * 0.12f, w * 0.44f, Ink, line * 1.4f)
    bar((w - w * 0.5f) / 2f, w * 0.24f, w * 0.5f, InkSoft)
    fun dashed(y: Float) = drawLine(
        InkSoft, Offset(rect.left + pad, rect.top + y), Offset(rect.right - pad, rect.top + y), w * 0.012f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.04f, w * 0.03f))
    )
    dashed(w * 0.36f)
    var y = w * 0.44f
    val rows = 4 + seed % 3
    repeat(rows) { i ->
        bar(pad, y, w * (0.32f + ((i + seed) % 3) * 0.08f), InkSoft)
        bar(w * 0.66f, y, w * 0.22f, Ink.copy(alpha = 0.7f))
        y += line * 2f
    }
    dashed(y + line * 0.2f)
    y += line * 1.3f
    bar(pad, y, w * 0.28f, Ink, line * 1.4f)
    bar(w * 0.6f, y, w * 0.28f, Ink, line * 1.4f)
    // Barcode at the foot.
    y += line * 3f
    if (rect.top + y + w * 0.16f < rect.bottom - tooth) {
        var x = pad
        var k = seed
        while (x < w - pad) {
            val bw = w * (0.008f + (k % 3) * 0.008f)
            drawRect(Ink, Offset(rect.left + x, rect.top + y), Size(bw, w * 0.14f))
            x += bw + w * (0.01f + (k % 2) * 0.008f)
            k = (k * 7 + 3) % 11
        }
    }
}

/** A modern phone, front on, with [screen] drawn inside its display. */
private fun DrawScope.drawPhone(rect: Rect, screen: DrawScope.(Rect) -> Unit) {
    val corner = rect.width * 0.14f
    drawPaperShadow(rect, rect.width * 0.05f)
    drawRoundRect(Color(0xFF0E1014), rect.topLeft, rect.size, CornerRadius(corner))
    drawRoundRect(Color(0xFF4A4F58), rect.topLeft, rect.size, CornerRadius(corner), style = Stroke(rect.width * 0.012f))
    // Side buttons.
    drawRoundRect(Color(0xFF2B2F36), Offset(rect.right - rect.width * 0.005f, rect.top + rect.height * 0.2f), Size(rect.width * 0.02f, rect.height * 0.1f), CornerRadius(4f))
    val inset = rect.width * 0.04f
    val display = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(display, CornerRadius(corner - inset))) }
    clipPath(clip) { screen(display) }
    // Camera hole.
    drawCircle(Color(0xFF0E1014), rect.width * 0.03f, Offset(rect.center.x, display.top + rect.width * 0.06f))
}

/** The blue scan line at [position] (0 top, 1 bottom) of [rect], with a glow trailing above it. */
private fun DrawScope.drawScanBeam(rect: Rect, position: Float) {
    val y = rect.top + rect.height * (0.04f + 0.92f * position)
    val wake = rect.height * 0.14f
    val wakeTop = maxOf(rect.top, y - wake)
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, ScanBlue.copy(alpha = 0.3f)), startY = y - wake, endY = y),
        Offset(rect.left, wakeTop), Size(rect.width, y - wakeTop)
    )
    val over = rect.width * 0.04f
    drawLine(ScanBlue.copy(alpha = 0.35f), Offset(rect.left - over, y), Offset(rect.right + over, y), rect.width * 0.03f, StrokeCap.Round)
    drawLine(ScanBlue, Offset(rect.left - over, y), Offset(rect.right + over, y), rect.width * 0.012f, StrokeCap.Round)
}

private fun DrawScope.drawBrackets(rect: Rect, arm: Float, stroke: Float, colour: Color) {
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(colour, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
        drawLine(colour, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
    }
    corner(rect.left, rect.top, 1f, 1f)
    corner(rect.right, rect.top, -1f, 1f)
    corner(rect.left, rect.bottom, 1f, -1f)
    corner(rect.right, rect.bottom, -1f, -1f)
}

private fun DrawScope.drawCentredText(measurer: TextMeasurer, text: String, centre: Offset, heightPx: Float, colour: Color, weight: FontWeight, family: FontFamily? = null) {
    val laid = measurer.measure(text, TextStyle(fontSize = (heightPx / 1.2f).toSp(), fontWeight = weight, color = colour, fontFamily = family))
    drawText(laid, topLeft = Offset(centre.x - laid.size.width / 2f, centre.y - laid.size.height / 2f))
}

/** The sample scene: desk, a pen, and the sample page turned slightly. Returns the page's unrotated rectangle. */
private fun DrawScope.drawSampleScene(): Rect {
    drawDesk()
    val pageH = size.height * 0.74f
    val pageW = min(pageH * 0.72f, size.width * 0.62f)
    val page = Rect(Offset((size.width - pageW) / 2f, (size.height - pageW / 0.72f) / 2f), Size(pageW, pageW / 0.72f))
    rotate(-5f, page.center) {
        drawPaperShadow(page, pageW * 0.03f)
        drawBill(page)
    }
    // A pen lying beside the page.
    val penLen = pageW * 0.75f
    val penStart = Offset(page.right + pageW * 0.12f, page.top + pageW * 0.1f)
    rotate(18f, penStart) {
        drawRoundRect(Color.Black.copy(alpha = 0.25f), penStart + Offset(4f, 6f), Size(pageW * 0.05f, penLen), CornerRadius(pageW * 0.025f))
        drawRoundRect(Color(0xFF1F3B8F), penStart, Size(pageW * 0.05f, penLen), CornerRadius(pageW * 0.025f))
        drawRoundRect(Color(0xFFC9CED6), penStart, Size(pageW * 0.05f, penLen * 0.12f), CornerRadius(pageW * 0.025f))
    }
    return page
}

/** An open book: cover behind, page stacks at the edges, curved pages with text and a picture, and two thumbs. */
private fun DrawScope.drawOpenBook(book: Rect) {
    val w = book.width
    val h = book.height
    val cx = book.center.x
    val lift = h * 0.05f
    // Palms under the book.
    drawOval(SkinShade, Offset(book.left - w * 0.05f, book.bottom - h * 0.32f), Size(w * 0.2f, h * 0.42f))
    drawOval(SkinShade, Offset(book.right - w * 0.15f, book.bottom - h * 0.32f), Size(w * 0.2f, h * 0.42f))
    // Cover.
    drawPaperShadow(book, w * 0.02f)
    drawRoundRect(Color(0xFF8E2F2F), Offset(book.left - w * 0.015f, book.top + lift * 0.6f), Size(w * 1.03f, h * 1.02f), CornerRadius(w * 0.012f))
    // Page stacks.
    for (i in 3 downTo 1) {
        val o = w * 0.006f * i
        drawRect(PaperEdge, Offset(book.left + o * 0.5f, book.top + lift + o), Size(w - o, h - o * 0.2f))
        drawRect(InkFaint, Offset(book.left + o * 0.5f, book.top + lift + o), Size(w - o, h - o * 0.2f), style = Stroke(1f))
    }
    fun page(left: Boolean): Path = Path().apply {
        val outer = if (left) book.left + w * 0.012f else book.right - w * 0.012f
        val dir = if (left) 1f else -1f
        moveTo(outer, book.top + lift)
        cubicTo(outer + dir * w * 0.18f, book.top - lift * 0.6f, cx - dir * w * 0.12f, book.top - lift * 0.4f, cx, book.top + lift * 0.7f)
        lineTo(cx, book.bottom - lift * 0.1f)
        cubicTo(cx - dir * w * 0.12f, book.bottom - lift * 1.2f, outer + dir * w * 0.18f, book.bottom - lift * 1.3f, outer, book.bottom - lift * 0.4f)
        close()
    }
    listOf(true, false).forEach { left ->
        val p = page(left)
        drawPath(p, Paper)
        // Gutter shade towards the spine.
        val start = if (left) cx - w * 0.12f else cx + w * 0.12f
        drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startX = start, endX = cx))
        drawPath(p, PaperEdge, style = Stroke(w * 0.003f))
    }
    // Text on the pages: a heading, lines of text and, on the right, a picture.
    val line = h * 0.028f
    fun textBlock(left: Boolean, withPicture: Boolean) {
        val x0 = if (left) book.left + w * 0.07f else cx + w * 0.05f
        val width = w * 0.38f
        var y = book.top + lift + h * 0.1f
        drawRoundRect(Ink, Offset(x0, y), Size(width * 0.55f, line * 1.4f), CornerRadius(line))
        y += line * 3f
        if (withPicture) {
            val pic = Rect(Offset(x0, y), Size(width * 0.92f, h * 0.3f))
            drawRect(Color(0xFFBFD9F2), pic.topLeft, pic.size)
            drawCircle(Color(0xFFF6C14E), pic.height * 0.13f, Offset(pic.left + pic.width * 0.75f, pic.top + pic.height * 0.3f))
            val hills = Path().apply {
                moveTo(pic.left, pic.bottom); lineTo(pic.left + pic.width * 0.35f, pic.top + pic.height * 0.4f)
                lineTo(pic.left + pic.width * 0.6f, pic.top + pic.height * 0.75f); lineTo(pic.left + pic.width * 0.8f, pic.top + pic.height * 0.55f)
                lineTo(pic.right, pic.bottom); close()
            }
            drawPath(hills, Color(0xFF4F8A5B))
            y = pic.bottom + line * 1.6f
        }
        val widths = floatArrayOf(0.95f, 0.9f, 0.97f, 0.86f, 0.93f, 0.7f, 0.95f, 0.88f, 0.9f, 0.6f, 0.92f, 0.85f)
        var i = 0
        while (y < book.bottom - lift * 1.6f - line * 2f && i < widths.size) {
            drawRoundRect(InkSoft, Offset(x0, y), Size(width * widths[i], line * 0.8f), CornerRadius(line))
            y += line * 1.9f
            i++
        }
    }
    textBlock(left = true, withPicture = false)
    textBlock(left = false, withPicture = true)
    // Thumbs holding the pages flat at the lower outer corners.
    fun thumb(at: Offset, angle: Float) = rotate(angle, at) {
        val tw = w * 0.065f
        val th = h * 0.26f
        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(at.x - tw / 2f + tw * 0.12f, at.y - th * 0.15f + tw * 0.15f), Size(tw, th), CornerRadius(tw / 2f))
        drawRoundRect(Skin, Offset(at.x - tw / 2f, at.y - th * 0.15f), Size(tw, th), CornerRadius(tw / 2f))
        drawRoundRect(Color(0xFFF6DCCB), Offset(at.x - tw * 0.32f, at.y - th * 0.11f), Size(tw * 0.64f, th * 0.24f), CornerRadius(tw * 0.3f))
    }
    thumb(Offset(book.left + w * 0.07f, book.bottom - h * 0.2f), 28f)
    thumb(Offset(book.right - w * 0.07f, book.bottom - h * 0.2f), -28f)
}

private fun DrawScope.drawIdFront(rect: Rect, measurer: TextMeasurer) {
    val w = rect.width
    val h = rect.height
    val corner = CornerRadius(h * 0.07f)
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, corner)) }
    clipPath(clip) {
        drawRect(Brush.linearGradient(listOf(Color(0xFFF3FAF6), Color(0xFFDDEFE6)), rect.topLeft, rect.bottomRight), rect.topLeft, rect.size)
        // Guilloche rings.
        for (i in 1..6) drawCircle(IdGreen.copy(alpha = 0.07f), h * 0.12f * i, Offset(rect.right - w * 0.18f, rect.bottom - h * 0.1f), style = Stroke(h * 0.01f))
        // Green header with the title.
        drawRect(IdGreen, rect.topLeft, Size(w, h * 0.22f))
        drawCentredText(measurer, "ID CARD", Offset(rect.left + w * 0.5f, rect.top + h * 0.11f), h * 0.15f, Color.White, FontWeight.Black)
    }
    // Photo with a silhouette.
    val photo = Rect(Offset(rect.left + w * 0.06f, rect.top + h * 0.3f), Size(w * 0.26f, h * 0.58f))
    drawRoundRect(Color(0xFFD5DDE2), photo.topLeft, photo.size, CornerRadius(h * 0.03f))
    clipRectPath(photo) {
        drawCircle(Color(0xFF7D8B96), photo.width * 0.24f, Offset(photo.center.x, photo.top + photo.height * 0.38f))
        drawOval(Color(0xFF7D8B96), Offset(photo.left + photo.width * 0.1f, photo.top + photo.height * 0.66f), Size(photo.width * 0.8f, photo.height * 0.6f))
    }
    // Labels (small, grey) and values (dark).
    val x = rect.left + w * 0.37f
    val bar = h * 0.055f
    var y = rect.top + h * 0.32f
    floatArrayOf(0.4f, 0.32f, 0.24f).forEach { fraction ->
        drawRoundRect(InkSoft, Offset(x, y), Size(w * 0.14f, bar * 0.7f), CornerRadius(bar))
        drawRoundRect(Ink, Offset(x, y + bar * 1.2f), Size(w * fraction, bar), CornerRadius(bar))
        y += bar * 3.1f
    }
    // Chip.
    val chip = Rect(Offset(rect.right - w * 0.21f, rect.top + h * 0.33f), Size(w * 0.14f, h * 0.2f))
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFF2D27A), Color(0xFFC79A2E)), chip.topLeft, chip.bottomRight), chip.topLeft, chip.size, CornerRadius(h * 0.03f))
    val chipLine = Color(0xFF9A7420)
    drawLine(chipLine, Offset(chip.left, chip.center.y), Offset(chip.right, chip.center.y), h * 0.008f)
    drawLine(chipLine, Offset(chip.center.x, chip.top), Offset(chip.center.x, chip.bottom), h * 0.008f)
    drawRoundRect(chipLine, Offset(chip.left + chip.width * 0.3f, chip.top + chip.height * 0.28f), Size(chip.width * 0.4f, chip.height * 0.44f), CornerRadius(h * 0.01f), style = Stroke(h * 0.008f))
    // Signature.
    val sig = Path().apply {
        moveTo(x, rect.bottom - h * 0.1f)
        cubicTo(x + w * 0.05f, rect.bottom - h * 0.22f, x + w * 0.08f, rect.bottom - h * 0.02f, x + w * 0.13f, rect.bottom - h * 0.12f)
        cubicTo(x + w * 0.17f, rect.bottom - h * 0.2f, x + w * 0.2f, rect.bottom - h * 0.06f, x + w * 0.27f, rect.bottom - h * 0.13f)
    }
    drawPath(sig, Color(0xFF1F3B8F), style = Stroke(h * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRoundRect(PaperEdge, rect.topLeft, rect.size, corner, style = Stroke(h * 0.008f))
}

private fun DrawScope.drawIdBack(rect: Rect, measurer: TextMeasurer) {
    val w = rect.width
    val h = rect.height
    val corner = CornerRadius(h * 0.07f)
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFF3FAF6), Color(0xFFDDEFE6)), rect.topLeft, rect.bottomRight), rect.topLeft, rect.size, corner)
    // Barcode.
    var x = rect.left + w * 0.07f
    var k = 5
    val barTop = rect.top + h * 0.1f
    while (x < rect.left + w * 0.62f) {
        val bw = w * (0.006f + (k % 3) * 0.006f)
        drawRect(Ink, Offset(x, barTop), Size(bw, h * 0.24f))
        x += bw + w * (0.007f + (k % 2) * 0.006f)
        k = (k * 5 + 3) % 13
    }
    // Text lines on the right and below.
    val bar = h * 0.05f
    var y = rect.top + h * 0.12f
    floatArrayOf(0.22f, 0.18f, 0.2f).forEach { drawRoundRect(InkSoft, Offset(rect.left + w * 0.68f, y), Size(w * it, bar), CornerRadius(bar)); y += bar * 2f }
    y = rect.top + h * 0.42f
    floatArrayOf(0.7f, 0.55f).forEach { drawRoundRect(InkSoft, Offset(rect.left + w * 0.07f, y), Size(w * it, bar), CornerRadius(bar)); y += bar * 2f }
    // Machine-readable zone.
    val mrz = Rect(Offset(rect.left, rect.top + h * 0.62f), Size(w, h * 0.38f))
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, corner)) }
    clipPath(clip) { drawRect(Color.White.copy(alpha = 0.85f), mrz.topLeft, mrz.size) }
    val lines = listOf("IDGBR1234567<<<<<<<<<<<", "8001014M3012315<<<<<<<6", "SPECIMEN<<SAMPLE<<<<<<<")
    val fontPx = mrz.height / 3.6f
    val style = TextStyle(fontSize = (fontPx / 1.15f).toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ink)
    lines.forEachIndexed { i, text ->
        val laid = measurer.measure(text, style)
        val scale = (w * 0.9f / laid.size.width).coerceAtMost(1f)
        val top = mrz.top + mrz.height * 0.08f + i * fontPx * 1.02f
        withTransform({ scale(scale, 1f, Offset(rect.left + w * 0.05f, top)) }) {
            drawText(laid, topLeft = Offset(rect.left + w * 0.05f, top))
        }
    }
    drawRoundRect(PaperEdge, rect.topLeft, rect.size, corner, style = Stroke(h * 0.008f))
}

private fun DrawScope.clipRectPath(rect: Rect, block: DrawScope.() -> Unit) =
    clipRect(rect.left, rect.top, rect.right, rect.bottom) { block() }
