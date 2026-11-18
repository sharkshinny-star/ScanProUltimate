package com.example.scanpro.scanner

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.OpenCvRuntime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Edge-detection benchmark on the phone's own CPU, with known answers (F126).
 *
 * Every scene is drawn here, so the true page corners are exact. The detector sees only the picture. For each scene it
 * reports whether a page was found, the mean corner error as a percentage of the frame diagonal, the overlap (IoU)
 * between the found and the true page, and the time one detection takes. Results go to logcat under `SCANBENCH`.
 *
 * Synthetic scenes are not a replacement for real photographs (no lens blur, rolling shutter or real paper texture):
 * they measure the detector against geometry and lighting problems with a known answer, and let a change be judged by
 * numbers. Run it with `adb shell am instrument -w -e class com.example.scanpro.scanner.ScanDetectionBenchmarkTest
 * com.scanpro.app.debug.test/androidx.test.runner.AndroidJUnitRunner` (see BUILD_AND_TEST.md).
 */
@RunWith(AndroidJUnit4::class)
class ScanDetectionBenchmarkTest {

    private val width = 1280
    private val height = 960

    private class Scene(val name: String, val bitmap: Bitmap, val truth: List<PointF>)

    private class Result(val name: String, val found: Boolean, val cornerErrorPct: Double, val iou: Double, val millis: Double, val blur: Double = 0.0, val convincing: Boolean = false)

    @Test
    fun detectorOnKnownScenes() {
        assumeTrue("OpenCV could not load on this device", OpenCvRuntime.ensureLoaded())
        val results = scenes().map { scene -> measure(scene).also { scene.bitmap.recycle() } }

        val report = buildString {
            appendLine("scene | found | cornerErr% | IoU | ms | sharpness")
            results.forEach { appendLine("${it.name} | ${it.found} | ${"%.2f".format(it.cornerErrorPct)} | ${"%.3f".format(it.iou)} | ${"%.1f".format(it.millis)} | ${"%.0f".format(it.blur)}") }
            val found = results.count { it.found }
            appendLine("median ms over scenes ${"%.1f".format(results.map { it.millis }.sorted()[results.size / 2])}")
            appendLine("found $found of ${results.size}; mean IoU of found ${"%.3f".format(results.filter { it.found }.map { it.iou }.average())}")
        }
        report.lines().forEach { Log.i(TAG, it) }

        // A floor for the cases that work: plain pages, the faint-edge pages the faint-edge source (F126) fixed, and
        // the hard shadows the straight-edge search (F168) fixed.
        listOf(
            "clean_a4_desk", "strong_perspective", "receipt_dark_desk", "low_contrast_grey_desk", "white_page_white_table",
            "hard_shadow_across_page", "shadow_band_lower_half", "shadow_on_tilted_page", "shadow_corner_wedge",
            "shadow_sharp_edged", "shadow_most_of_left", "shadow_most_of_right_sharp", "yellow_paper_shadow_left",
            "warm_lamp_shadow_left", "dark_border_inset"
        ).forEach { name ->
            val r = results.first { it.name == name }
            assertTrue("$name: no page found", r.found)
            assertTrue("$name: corner error ${r.cornerErrorPct}% is above 4%", r.cornerErrorPct < 4.0)
        }
        // F177: a printed dark frame set in from the paper's edge is still a page, not a dark-rimmed object.
        assertTrue("dark_border_inset: not taken for a page", results.first { it.name == "dark_border_inset" }.convincing)
    }

    /** F177 (R58): a laptop or a keyboard, which score as high as pages, must not be taken for one. */
    @Test
    fun clutterIsNotTakenForAPage() {
        assumeTrue("OpenCV could not load on this device", OpenCvRuntime.ensureLoaded())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun clutter(name: String, desk: Desk, draw: (Canvas) -> Unit) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawDesk(canvas, desk, Random(name.hashCode().toLong()))
            draw(canvas)
            val detection = DocumentEdgeDetector.detect(bmp)
            bmp.recycle()
            Log.i(TAG, "$name | confidence ${"%.2f".format(detection.confidence)} | framed ${detection.framedSides} | page ${detection.isConvincingPage}")
            assertFalse("$name: taken for a page", detection.isConvincingPage)
        }
        clutter("laptop_wood", Desk.WOOD) { c ->
            fill.color = Color.rgb(58, 58, 62); c.drawRoundRect(RectF(250f, 180f, 1010f, 700f), 12f, 12f, fill)
            fill.color = Color.rgb(30, 34, 44); c.drawRect(280f, 205f, 980f, 645f, fill)
            fill.color = Color.rgb(200, 210, 225); c.drawRect(320f, 240f, 940f, 600f, fill)
        }
        clutter("keyboard_grey", Desk.GREY) { c ->
            fill.color = Color.rgb(40, 40, 44); c.drawRoundRect(RectF(200f, 330f, 1080f, 630f), 10f, 10f, fill)
            fill.color = Color.rgb(225, 225, 228)
            for (r in 0 until 5) for (k in 0 until 15) {
                val x = 222f + k * 57f
                val y = 352f + r * 54f
                c.drawRoundRect(RectF(x, y, x + 48f, y + 44f), 3f, 3f, fill)
            }
        }
    }

    private fun measure(scene: Scene): Result {
        // One warm-up so class loading and native set-up are not counted.
        DocumentEdgeDetector.detect(scene.bitmap)
        var last: CropQuad? = null
        var blur = 0.0
        var convincing = false
        // The median of nine runs: one run interrupted by the system does not move it, as it moved the old average.
        val times = List(9) {
            val start = System.nanoTime()
            DocumentEdgeDetector.detect(scene.bitmap).let { last = it.quad; blur = it.blurScore; convincing = it.isConvincingPage }
            (System.nanoTime() - start) / 1e6
        }
        val millis = times.sorted()[times.size / 2]
        val quad = last ?: return Result(scene.name, false, Double.NaN, 0.0, millis, blur)
        val found = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).map { PointF(it.x * width, it.y * height) }
        val diagonal = hypot(width.toDouble(), height.toDouble())
        val error = scene.truth.indices.map { hypot((found[it].x - scene.truth[it].x).toDouble(), (found[it].y - scene.truth[it].y).toDouble()) }.average() / diagonal * 100.0
        if (error > 2.0) Log.i(TAG, "  ${scene.name} found ${found.joinToString { "(${it.x.toInt()},${it.y.toInt()})" }} truth ${scene.truth.joinToString { "(${it.x.toInt()},${it.y.toInt()})" }}")
        return Result(scene.name, true, error, overlap(scene.truth, found), millis, blur, convincing)
    }

    /** Overlap of two quadrilaterals by drawing both on a small mask: shared pixels over the pixels of either. */
    private fun overlap(a: List<PointF>, b: List<PointF>): Double {
        val w = 320
        val h = 240
        fun mask(points: List<PointF>): BooleanArray {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val path = Path().apply {
                moveTo(points[0].x * w / width, points[0].y * h / height)
                for (i in 1..3) lineTo(points[i].x * w / width, points[i].y * h / height)
                close()
            }
            Canvas(bmp).drawPath(path, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()
            return BooleanArray(w * h) { pixels[it] != 0 }
        }
        val x = mask(a)
        val y = mask(b)
        var both = 0
        var either = 0
        for (i in x.indices) {
            if (x[i] && y[i]) both++
            if (x[i] || y[i]) either++
        }
        return if (either == 0) 0.0 else both.toDouble() / either
    }

    // ---- scenes ----------------------------------------------------------------------------------------------

    private fun scenes(): List<Scene> = listOf(
        scene("clean_a4_desk", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f)),
        scene("strong_perspective", Desk.WOOD, quad(420f, 90f, 860f, 120f, 1010f, 880f, 250f, 840f)),
        scene("rotated_20deg", Desk.WOOD, rotated(640f, 480f, 520f, 720f, 20f)),
        scene("page_near_frame_edge", Desk.WOOD, quad(24f, 18f, 760f, 22f, 750f, 930f, 30f, 925f)),
        scene("receipt_dark_desk", Desk.DARK, quad(520f, 40f, 760f, 44f, 750f, 920f, 530f, 915f), pageColor = Color.rgb(246, 246, 240), receipt = true),
        scene("low_contrast_grey_desk", Desk.GREY, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), pageColor = Color.rgb(226, 226, 222)),
        scene("white_page_white_table", Desk.WHITE, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), pageColor = Color.rgb(250, 250, 248)),
        scene("busy_patterned_mat", Desk.MAT, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f)),
        scene("hard_shadow_across_page", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(0f, 200f, 400f, 130f)),
        // F168: a shadow across the lower half, and one across a page seen at a strong angle.
        scene("shadow_band_lower_half", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(0f, 250f, 60f, 320f)),
        scene("shadow_on_tilted_page", Desk.WOOD, quad(420f, 90f, 860f, 120f, 1010f, 880f, 250f, 840f), shadow = ShadowBand(100f, 150f, 380f, 200f)),
        // F169: a wedge of shadow over the top-left corner, and a shadow with a sharp edge (a phone held close).
        scene("shadow_corner_wedge", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(-100f, -100f, 420f, 420f, soft = 0.1f)),
        scene("shadow_sharp_edged", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(0f, 200f, 400f, 130f, soft = 0.01f)),
        // F175: a shadow over most of one side, so the page's edge there is as dark as the wood: only colour sees it.
        scene("shadow_most_of_left", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(-40f, 0f, 420f, 0f)),
        scene("shadow_most_of_right_sharp", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(160f, 0f, 420f, 0f, soft = 0.02f)),
        // F176: yellow paper and a warm lamp, each with a shadow over most of one side (the colour map must ignore both).
        scene("yellow_paper_shadow_left", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), pageColor = Color.rgb(250, 236, 140), shadow = ShadowBand(-40f, 0f, 420f, 0f)),
        scene("warm_lamp_shadow_left", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), shadow = ShadowBand(-40f, 0f, 420f, 0f), warm = true),
        // F177: a printed dark frame (a certificate, a form) set in from the paper's edge, as office printers leave.
        scene("dark_border_inset", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), border = 26, borderInset = 22),
        scene("noisy_and_soft", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), noisy = true),
        scene("book_spread", Desk.WOOD, quad(190f, 170f, 1090f, 150f, 1110f, 800f, 170f, 820f), spread = true),
        scene("dim_light", Desk.WOOD, quad(370f, 100f, 910f, 110f, 900f, 860f, 380f, 850f), dim = true)
    )

    private fun quad(vararg v: Float) = listOf(PointF(v[0], v[1]), PointF(v[2], v[3]), PointF(v[4], v[5]), PointF(v[6], v[7]))

    private fun rotated(cx: Float, cy: Float, w: Float, h: Float, degrees: Float): List<PointF> {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2).map { (x, y) -> PointF(cx + x * c - y * s, cy + x * s + y * c) }
    }

    private enum class Desk { WOOD, DARK, GREY, WHITE, MAT }

    /**
     * A hard shadow band across the page: it starts [dx], [dy] from the top-left corner and runs along ([vx], [vy]).
     * [soft] is how much of the band each edge's fade takes (0.15 is a hand's soft shadow, 0.01 a sharp one).
     */
    private class ShadowBand(val dx: Float, val dy: Float, val vx: Float, val vy: Float, val soft: Float = 0.15f)

    private fun scene(
        name: String,
        desk: Desk,
        truth: List<PointF>,
        pageColor: Int = Color.rgb(248, 247, 242),
        receipt: Boolean = false,
        shadow: ShadowBand? = null,
        noisy: Boolean = false,
        spread: Boolean = false,
        dim: Boolean = false,
        warm: Boolean = false,
        border: Int = 0,
        borderInset: Int = 0
    ): Scene {
        val random = Random(name.hashCode().toLong())
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawDesk(canvas, desk, random)

        // A soft shadow under the page, as a page lying on a desk has.
        val outline = Path().apply { moveTo(truth[0].x, truth[0].y); for (i in 1..3) lineTo(truth[i].x, truth[i].y); close() }
        canvas.save()
        canvas.translate(7f, 9f)
        canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0); maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL) })
        canvas.restore()

        // The page is drawn flat, then warped onto the four corners.
        val flatW = if (receipt) 360 else if (spread) 1400 else 800
        val flatH = if (receipt) 1500 else if (spread) 1000 else 1131
        val flat = Bitmap.createBitmap(flatW, flatH, Bitmap.Config.ARGB_8888)
        val flatCanvas = Canvas(flat)
        drawPage(flatCanvas, flatW, flatH, pageColor, receipt, spread, random)
        if (border > 0) {
            // A printed dark frame, [border] px thick, [borderInset] px in from the paper's edge.
            val ink = Paint().apply { color = Color.rgb(28, 28, 34) }
            val l = borderInset.toFloat(); val t = borderInset.toFloat()
            val r = (flatW - borderInset).toFloat(); val b = (flatH - borderInset).toFloat()
            flatCanvas.drawRect(l, t, r, t + border, ink)
            flatCanvas.drawRect(l, b - border, r, b, ink)
            flatCanvas.drawRect(l, t, l + border, b, ink)
            flatCanvas.drawRect(r - border, t, r, b, ink)
        }
        val matrix = Matrix()
        val src = floatArrayOf(0f, 0f, flatW.toFloat(), 0f, flatW.toFloat(), flatH.toFloat(), 0f, flatH.toFloat())
        val dst = floatArrayOf(truth[0].x, truth[0].y, truth[1].x, truth[1].y, truth[2].x, truth[2].y, truth[3].x, truth[3].y)
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        canvas.drawBitmap(flat, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        flat.recycle()

        if (shadow != null) {
            // A hard shadow, such as a hand or a phone casts, across the page only.
            canvas.save()
            canvas.clipPath(outline)
            val x0 = truth[0].x + shadow.dx
            val y0 = truth[0].y + shadow.dy
            val band = Paint().apply {
                shader = LinearGradient(x0, y0, x0 + shadow.vx, y0 + shadow.vy, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(150, 20, 20, 25), Color.argb(150, 20, 20, 25), Color.argb(0, 0, 0, 0)), floatArrayOf(0f, shadow.soft, 1f - shadow.soft, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), band)
            canvas.restore()
        }
        var result = bmp
        if (noisy) result = noisyAndSoft(bmp, random)
        if (dim) result = dimmed(result, random)
        if (warm) result = warmed(result)
        return Scene(name, result, truth)
    }

    private fun drawDesk(canvas: Canvas, desk: Desk, random: Random) {
        val base = when (desk) {
            Desk.WOOD -> Color.rgb(142, 98, 62)
            Desk.DARK -> Color.rgb(34, 34, 38)
            Desk.GREY -> Color.rgb(205, 205, 200)
            Desk.WHITE -> Color.rgb(238, 238, 236)
            Desk.MAT -> Color.rgb(205, 150, 60)
        }
        canvas.drawColor(base)
        val paint = Paint()
        when (desk) {
            Desk.WOOD -> repeat(70) {
                // Grain: long thin darker and lighter streaks.
                val y = random.nextInt(height).toFloat()
                paint.color = Color.argb(40 + random.nextInt(40), if (random.nextBoolean()) 70 else 190, 50, 30)
                paint.strokeWidth = 1f + random.nextInt(4)
                canvas.drawLine(0f, y, width.toFloat(), y + random.nextInt(30) - 15, paint)
            }
            Desk.MAT -> {
                // A woven mat with a bold blue grid, like the one in everyday photos.
                paint.color = Color.rgb(20, 40, 150)
                paint.strokeWidth = 26f
                var x = -200f
                while (x < width + 200) { canvas.drawLine(x, 0f, x + 260f, height.toFloat(), paint); x += 130f }
                var y = -200f
                while (y < height + 200) { canvas.drawLine(0f, y, width.toFloat(), y + 120f, paint); y += 120f }
            }
            else -> Unit
        }
    }

    private fun drawPage(canvas: Canvas, w: Int, h: Int, pageColor: Int, receipt: Boolean, spread: Boolean, random: Random) {
        canvas.drawColor(pageColor)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 34); typeface = Typeface.SANS_SERIF }
        val margin = if (receipt) 24f else 70f
        var y = margin + 20f
        val words = listOf("Invoice", "Total", "ScanPro", "Quantity", "Amount", "Payment", "Reference", "Tax", "Subtotal", "Date", "Customer", "Balance")
        fun line(size: Float, bold: Boolean, x: Float, maxX: Float) {
            ink.textSize = size
            ink.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.SANS_SERIF
            val text = StringBuilder()
            while (ink.measureText(text.toString()) < (maxX - x) * (0.55f + random.nextFloat() * 0.45f)) text.append(words[random.nextInt(words.size)]).append(' ')
            canvas.drawText(text.toString().trim(), x, y, ink)
            y += size * 1.55f
        }
        val columns = if (spread) listOf(margin to w / 2f - 40f, w / 2f + 40f to w - margin) else listOf(margin to w - margin)
        columns.forEach { (x0, x1) ->
            y = margin + 20f
            line(if (receipt) 34f else 48f, true, x0, x1)
            y += 12f
            while (y < h - margin - 40f) {
                val small = random.nextInt(4) == 0
                line(if (receipt) 24f else if (small) 17f else 26f, false, x0, x1)
                if (random.nextInt(9) == 0) {
                    ink.strokeWidth = 2f
                    canvas.drawLine(x0, y - 18f, x1, y - 18f, ink)
                }
            }
        }
        if (spread) {
            // The fold: a dark gutter down the middle that fades out to each side.
            val gutter = Paint().apply {
                shader = LinearGradient(w / 2f - 70f, 0f, w / 2f + 70f, 0f, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(w / 2f - 70f, 0f, w / 2f + 70f, h.toFloat(), gutter)
        }
    }

    private fun noisyAndSoft(source: Bitmap, random: Random): Bitmap {
        // Soft: shrunk and stretched back. Noisy: every pixel nudged, as a high ISO sensor does.
        val small = Bitmap.createScaledBitmap(source, width / 3, height / 3, true)
        val soft = Bitmap.createScaledBitmap(small, width, height, true)
        small.recycle()
        source.recycle()
        val pixels = IntArray(width * height)
        soft.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val n = (random.nextGaussian() * 16).toInt()
            val p = pixels[i]
            pixels[i] = Color.rgb((Color.red(p) + n).coerceIn(0, 255), (Color.green(p) + n).coerceIn(0, 255), (Color.blue(p) + n).coerceIn(0, 255))
        }
        soft.setPixels(pixels, 0, width, 0, 0, width, height)
        return soft
    }

    private fun warmed(source: Bitmap): Bitmap {
        // A warm lamp: blue down and red up a little, so white paper looks cream.
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = Color.rgb((Color.red(p) + 12).coerceAtMost(255), (Color.green(p) * 0.92f).toInt(), (Color.blue(p) * 0.72f).toInt())
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    private fun dimmed(source: Bitmap, random: Random): Bitmap {
        // Low light: everything darker, with a slight noise.
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val n = (random.nextGaussian() * 5).toInt()
            pixels[i] = Color.rgb((Color.red(p) * 0.32f + n).toInt().coerceIn(0, 255), (Color.green(p) * 0.32f + n).toInt().coerceIn(0, 255), (Color.blue(p) * 0.32f + n).toInt().coerceIn(0, 255))
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    private companion object {
        const val TAG = "SCANBENCH"
    }
}
