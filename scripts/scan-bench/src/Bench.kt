/*
 * Desktop port of the device benchmark (app/src/androidTest/.../ScanDetectionBenchmarkTest.kt), drawn with Java2D and
 * OpenCV instead of android.graphics, so detection can be measured without a phone. Pixel output differs slightly from
 * the device scenes; on 2026-11-17 the two agreed to within 0.3 percentage points of corner error on every scene.
 *
 * Arguments: "extra" adds shadow cases (x_) and scenes with no page (n_); "only=<text>" filters scenes by name;
 * "dump=<dir>" writes each scene with the true outline (green) and the detected one (red); "time" prints detection time.
 */
import com.example.scanpro.imaging.OpenCvRuntime
import com.example.scanpro.scanner.DocumentEdgeDetector
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.*
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.Random
import kotlin.math.*

const val W = 1280
const val H = 960

class P(val x: Double, val y: Double)
class Scene(val name: String, val gray: Mat, val color: Mat, val truth: List<P>)
enum class Desk { WOOD, DARK, GREY, WHITE, MAT }
/** A shadow band: start point offset from TL, direction vector, alpha, whether clipped to the page. */
class Shadow(val dx0: Double, val dy0: Double, val vx: Double, val vy: Double, val alpha: Int = 150, val pageOnly: Boolean = true, val soft: Float = 0.15f)

fun quad(vararg v: Double) = listOf(P(v[0], v[1]), P(v[2], v[3]), P(v[4], v[5]), P(v[6], v[7]))
fun rotated(cx: Double, cy: Double, w: Double, h: Double, deg: Double): List<P> {
    val r = Math.toRadians(deg); val c = cos(r); val s = sin(r)
    return listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2).map { (x, y) -> P(cx + x * c - y * s, cy + x * s + y * c) }
}

fun bufToMat(img: BufferedImage): Mat {
    val px = (img.raster.dataBuffer as DataBufferInt).data
    val bytes = ByteArray(px.size * 3)
    for (i in px.indices) { val p = px[i]; bytes[i * 3] = (p and 255).toByte(); bytes[i * 3 + 1] = (p shr 8 and 255).toByte(); bytes[i * 3 + 2] = (p shr 16 and 255).toByte() }
    return Mat(img.height, img.width, CvType.CV_8UC3).also { it.put(0, 0, bytes) } // BGR
}
fun matToBuf(m: Mat): BufferedImage {
    val img = BufferedImage(m.cols(), m.rows(), BufferedImage.TYPE_INT_RGB)
    val bytes = ByteArray(m.cols() * m.rows() * 3); m.get(0, 0, bytes)
    val px = (img.raster.dataBuffer as DataBufferInt).data
    for (i in px.indices) px[i] = ((bytes[i * 3 + 2].toInt() and 255) shl 16) or ((bytes[i * 3 + 1].toInt() and 255) shl 8) or (bytes[i * 3].toInt() and 255)
    return img
}
fun path(t: List<P>) = Path2D.Double().apply { moveTo(t[0].x, t[0].y); for (i in 1..3) lineTo(t[i].x, t[i].y); closePath() }

fun scene(name: String, desk: Desk, truth: List<P>, pageColor: Color = Color(248, 247, 242), receipt: Boolean = false,
          shadow: Shadow? = null, noisy: Boolean = false, spread: Boolean = false, dim: Boolean = false, noPage: Boolean = false): Scene {
    val random = Random(name.hashCode().toLong())
    var img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    drawDesk(g, desk, random); g.dispose()
    var bgr = bufToMat(img)
    if (noPage) { val gray = Mat(); Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY); return Scene(name, gray, bgr, truth) }
    // Soft drop shadow under the page.
    run {
        val mask = Mat.zeros(H, W, CvType.CV_32F)
        Imgproc.fillPoly(mask, listOf(MatOfPoint(*truth.map { Point(it.x + 7, it.y + 9) }.toTypedArray())), Scalar(110.0 / 255.0), Imgproc.LINE_AA)
        Imgproc.GaussianBlur(mask, mask, Size(0.0, 0.0), 8.5)
        val f = Mat(); bgr.convertTo(f, CvType.CV_32FC3)
        val inv = Mat(); Core.subtract(Mat.ones(H, W, CvType.CV_32F), mask, inv)
        val inv3 = Mat(); Core.merge(listOf(inv, inv, inv), inv3)
        Core.multiply(f, inv3, f); f.convertTo(bgr, CvType.CV_8UC3)
    }
    // Page, flat then warped.
    val fw = if (receipt) 360 else if (spread) 1400 else 800
    val fh = if (receipt) 1500 else if (spread) 1000 else 1131
    val flat = BufferedImage(fw, fh, BufferedImage.TYPE_INT_RGB)
    val pg = flat.createGraphics(); pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    drawPage(pg, fw, fh, pageColor, receipt, spread, random); pg.dispose()
    val src = MatOfPoint2f(Point(0.0, 0.0), Point(fw.toDouble(), 0.0), Point(fw.toDouble(), fh.toDouble()), Point(0.0, fh.toDouble()))
    val dst = MatOfPoint2f(*truth.map { Point(it.x, it.y) }.toTypedArray())
    val hm = Imgproc.getPerspectiveTransform(src, dst)
    val warped = Mat(); Imgproc.warpPerspective(bufToMat(flat), warped, hm, Size(W.toDouble(), H.toDouble()), Imgproc.INTER_LINEAR)
    val pmask = Mat.zeros(H, W, CvType.CV_8U); Imgproc.fillPoly(pmask, listOf(MatOfPoint(*truth.map { Point(it.x, it.y) }.toTypedArray())), Scalar(255.0), Imgproc.LINE_AA)
    run {
        val a = Mat(); pmask.convertTo(a, CvType.CV_32F, 1 / 255.0); val a3 = Mat(); Core.merge(listOf(a, a, a), a3)
        val inv3 = Mat(); Core.subtract(Mat(H, W, CvType.CV_32FC3, Scalar(1.0, 1.0, 1.0)), a3, inv3)
        val fb = Mat(); bgr.convertTo(fb, CvType.CV_32FC3); val fp = Mat(); warped.convertTo(fp, CvType.CV_32FC3)
        Core.multiply(fb, inv3, fb); Core.multiply(fp, a3, fp); Core.add(fb, fp, fb); fb.convertTo(bgr, CvType.CV_8UC3)
    }
    if (shadow != null) {
        img = matToBuf(bgr)
        val g2 = img.createGraphics()
        if (shadow.pageOnly) g2.clip = path(truth)
        val x0 = truth[0].x + shadow.dx0; val y0 = truth[0].y + shadow.dy0
        val c0 = Color(0, 0, 0, 0); val c1 = Color(20, 20, 25, shadow.alpha)
        g2.paint = LinearGradientPaint(x0.toFloat(), y0.toFloat(), (x0 + shadow.vx).toFloat(), (y0 + shadow.vy).toFloat(),
            floatArrayOf(0f, shadow.soft, 1f - shadow.soft, 1f), arrayOf(c0, c1, c1, c0))
        g2.fillRect(0, 0, W, H); g2.dispose()
        bgr = bufToMat(img)
    }
    if (noisy) {
        val small = Mat(); Imgproc.resize(bgr, small, Size(W / 3.0, H / 3.0), 0.0, 0.0, Imgproc.INTER_LINEAR)
        Imgproc.resize(small, bgr, Size(W.toDouble(), H.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        addNoise(bgr, random, 16.0, 1.0)
    }
    if (dim) addNoise(bgr, random, 5.0, 0.32)
    val gray = Mat(); Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
    return Scene(name, gray, bgr, truth)
}

fun addNoise(bgr: Mat, random: Random, sd: Double, gain: Double) {
    val bytes = ByteArray(W * H * 3); bgr.get(0, 0, bytes)
    for (i in 0 until W * H) { val n = random.nextGaussian() * sd; for (c in 0..2) { val v = (bytes[i * 3 + c].toInt() and 255) * gain + n; bytes[i * 3 + c] = v.toInt().coerceIn(0, 255).toByte() } }
    bgr.put(0, 0, bytes)
}

fun drawDesk(g: Graphics2D, desk: Desk, random: Random) {
    g.color = when (desk) { Desk.WOOD -> Color(142, 98, 62); Desk.DARK -> Color(34, 34, 38); Desk.GREY -> Color(205, 205, 200); Desk.WHITE -> Color(238, 238, 236); Desk.MAT -> Color(205, 150, 60) }
    g.fillRect(0, 0, W, H)
    when (desk) {
        Desk.WOOD -> repeat(70) {
            val y = random.nextInt(H)
            g.color = Color(if (random.nextBoolean()) 70 else 190, 50, 30, 40 + random.nextInt(40))
            g.stroke = BasicStroke(1f + random.nextInt(4)); g.drawLine(0, y, W, y + random.nextInt(30) - 15)
        }
        Desk.MAT -> {
            g.color = Color(20, 40, 150); g.stroke = BasicStroke(26f)
            var x = -200; while (x < W + 200) { g.drawLine(x, 0, x + 260, H); x += 130 }
            var y = -200; while (y < H + 200) { g.drawLine(0, y, W, y + 120); y += 120 }
        }
        else -> Unit
    }
}

fun drawPage(g: Graphics2D, w: Int, h: Int, pageColor: Color, receipt: Boolean, spread: Boolean, random: Random) {
    g.color = pageColor; g.fillRect(0, 0, w, h)
    val margin = if (receipt) 24f else 70f
    var y = margin + 20f
    val words = listOf("Invoice", "Total", "ScanPro", "Quantity", "Amount", "Payment", "Reference", "Tax", "Subtotal", "Date", "Customer", "Balance")
    fun line(size: Float, bold: Boolean, x: Float, maxX: Float) {
        g.font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, size.toInt()); g.color = Color(30, 30, 34)
        val text = StringBuilder()
        while (g.fontMetrics.stringWidth(text.toString()) < (maxX - x) * (0.55f + random.nextFloat() * 0.45f)) text.append(words[random.nextInt(words.size)]).append(' ')
        g.drawString(text.toString().trim(), x, y); y += size * 1.55f
    }
    val columns = if (spread) listOf(margin to w / 2f - 40f, w / 2f + 40f to w - margin) else listOf(margin to w - margin)
    columns.forEach { (x0, x1) ->
        y = margin + 20f
        line(if (receipt) 34f else 48f, true, x0, x1); y += 12f
        while (y < h - margin - 40f) {
            val small = random.nextInt(4) == 0
            line(if (receipt) 24f else if (small) 17f else 26f, false, x0, x1)
            if (random.nextInt(9) == 0) { g.stroke = BasicStroke(2f); g.drawLine(x0.toInt(), (y - 18).toInt(), x1.toInt(), (y - 18).toInt()) }
        }
    }
    if (spread) {
        g.paint = LinearGradientPaint(w / 2f - 70f, 0f, w / 2f + 70f, 0f, floatArrayOf(0f, .5f, 1f), arrayOf(Color(0, 0, 0, 0), Color(0, 0, 0, 120), Color(0, 0, 0, 0)))
        g.fillRect((w / 2f - 70f).toInt(), 0, 140, h)
    }
}

val A4 = quad(370.0, 100.0, 910.0, 110.0, 900.0, 860.0, 380.0, 850.0)
val BENCH_SHADOW = Shadow(0.0, 200.0, 400.0, 130.0)

fun scenes(extra: Boolean): List<Scene> = listOf(
    scene("clean_a4_desk", Desk.WOOD, A4),
    scene("strong_perspective", Desk.WOOD, quad(420.0, 90.0, 860.0, 120.0, 1010.0, 880.0, 250.0, 840.0)),
    scene("rotated_20deg", Desk.WOOD, rotated(640.0, 480.0, 520.0, 720.0, 20.0)),
    scene("page_near_frame_edge", Desk.WOOD, quad(24.0, 18.0, 760.0, 22.0, 750.0, 930.0, 30.0, 925.0)),
    scene("receipt_dark_desk", Desk.DARK, quad(520.0, 40.0, 760.0, 44.0, 750.0, 920.0, 530.0, 915.0), pageColor = Color(246, 246, 240), receipt = true),
    scene("low_contrast_grey_desk", Desk.GREY, A4, pageColor = Color(226, 226, 222)),
    scene("white_page_white_table", Desk.WHITE, A4, pageColor = Color(250, 250, 248)),
    scene("busy_patterned_mat", Desk.MAT, A4),
    scene("hard_shadow_across_page", Desk.WOOD, A4, shadow = BENCH_SHADOW),
    scene("noisy_and_soft", Desk.WOOD, A4, noisy = true),
    scene("book_spread", Desk.WOOD, quad(190.0, 170.0, 1090.0, 150.0, 1110.0, 800.0, 170.0, 820.0), spread = true),
    scene("dim_light", Desk.WOOD, A4, dim = true)
) + if (!extra) emptyList() else listOf(
    // Extra shadow cases, not in the device benchmark, so the fix is not tuned to one picture.
    scene("x_shadow_band_lower_half", Desk.WOOD, A4, shadow = Shadow(0.0, 250.0, 60.0, 320.0)),
    scene("x_shadow_corner_wedge", Desk.WOOD, A4, shadow = Shadow(-100.0, -100.0, 420.0, 420.0, soft = 0.1f)),
    scene("x_shadow_on_desk_too", Desk.WOOD, A4, shadow = Shadow(0.0, 200.0, 400.0, 130.0, pageOnly = false)),
    scene("x_shadow_grey_desk", Desk.GREY, A4, shadow = BENCH_SHADOW),
    scene("x_shadow_white_desk", Desk.WHITE, A4, shadow = BENCH_SHADOW),
    scene("x_shadow_dark_desk", Desk.DARK, A4, shadow = BENCH_SHADOW),
    scene("x_shadow_mat", Desk.MAT, A4, shadow = BENCH_SHADOW),
    scene("x_shadow_rotated", Desk.WOOD, rotated(640.0, 480.0, 520.0, 720.0, 20.0), shadow = Shadow(-50.0, 250.0, 380.0, 160.0)),
    scene("x_shadow_light", Desk.WOOD, A4, shadow = Shadow(0.0, 200.0, 400.0, 130.0, alpha = 90)),
    scene("x_shadow_two_sides", Desk.WOOD, A4, shadow = Shadow(-150.0, 400.0, 700.0, 40.0, soft = 0.08f)),
    scene("x_shadow_on_tilted_page", Desk.WOOD, quad(420.0, 90.0, 860.0, 120.0, 1010.0, 880.0, 250.0, 840.0), shadow = Shadow(100.0, 150.0, 380.0, 200.0)),
    scene("x_shadow_noisy", Desk.WOOD, A4, shadow = BENCH_SHADOW, noisy = true),
    scene("x_shadow_sharp", Desk.WOOD, A4, shadow = Shadow(0.0, 200.0, 400.0, 130.0, soft = 0.01f)),
    scene("x_shadow_sharp_band", Desk.WOOD, A4, shadow = Shadow(0.0, 250.0, 60.0, 320.0, soft = 0.02f)),
    scene("x_shadow_sharp_desk_too", Desk.WOOD, A4, shadow = Shadow(0.0, 200.0, 400.0, 130.0, pageOnly = false, soft = 0.01f)),
    scene("x_shadow_sharp_grey", Desk.GREY, A4, shadow = Shadow(0.0, 200.0, 400.0, 130.0, soft = 0.01f)),
    scene("n_wood_only", Desk.WOOD, A4, noPage = true),
    scene("n_mat_only", Desk.MAT, A4, noPage = true),
    scene("n_white_only", Desk.WHITE, A4, noPage = true),
    scene("n_dark_only", Desk.DARK, A4, noPage = true)
)

fun iou(a: List<P>, b: List<P>): Double {
    fun mask(p: List<P>) = Mat.zeros(H / 4, W / 4, CvType.CV_8U).also { Imgproc.fillPoly(it, listOf(MatOfPoint(*p.map { q -> Point(q.x / 4, q.y / 4) }.toTypedArray())), Scalar(255.0)) }
    val x = mask(a); val y = mask(b); val i = Mat(); val u = Mat()
    Core.bitwise_and(x, y, i); Core.bitwise_or(x, y, u)
    return Core.countNonZero(i).toDouble() / max(1, Core.countNonZero(u))
}

fun main(args: Array<String>) {
    OpenCvRuntime.ensureLoaded()
    if ("time" in args) {
        val list = scenes(false)
        repeat(3) { list.forEach { s -> repeat(5) { DocumentEdgeDetector.detect(s.gray) } } }
        val med = list.map { s -> List(40) { val t = System.nanoTime(); DocumentEdgeDetector.detect(s.gray); (System.nanoTime() - t) / 1e6 }.sorted()[20] }

        println("time: median-of-scenes %.2f ms, mean %.2f ms, max %.2f".format(med.sorted()[med.size / 2], med.average(), med.max()))
        return
    }
    if ("enhance" in args) { enhanceBench(args.firstOrNull { it.startsWith("dump=") }?.removePrefix("dump=")); return }
    val extra = "extra" in args
    val dump = args.firstOrNull { it.startsWith("dump=") }?.removePrefix("dump=")
    val only = args.firstOrNull { it.startsWith("only=") }?.removePrefix("only=")
    println("scene | found | err% | IoU | ms | conf")
    val rows = mutableListOf<Triple<String, Double, Double>>()
    scenes(extra).filter { only == null || only in it.name }.forEach { s ->
        DocumentEdgeDetector.detect(s.gray)
        var det = DocumentEdgeDetector.detect(s.gray)
        val times = List(7) { val t = System.nanoTime(); det = DocumentEdgeDetector.detect(s.gray); (System.nanoTime() - t) / 1e6 }.sorted()
        val q = det.quad
        if (q == null) { println("${s.name} | false | - | 0 | ${"%.1f".format(times[3])} | 0"); rows += Triple(s.name, 100.0, 0.0); return@forEach }
        val found = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft).map { P(it.x * W.toDouble(), it.y * H.toDouble()) }
        val err = s.truth.indices.map { hypot(found[it].x - s.truth[it].x, found[it].y - s.truth[it].y) }.average() / hypot(W.toDouble(), H.toDouble()) * 100
        val o = iou(s.truth, found)
        rows += Triple(s.name, err, o)
        if (s.name.startsWith("n_")) { println("${s.name} | NEG conf ${"%.2f".format(det.confidence)} convincing ${det.isConvincingPage} area ${"%.2f".format(det.areaRatio)}"); return@forEach }
        println("${s.name} | true | ${"%.2f".format(err)} | ${"%.3f".format(o)} | ${"%.1f".format(times[3])} | ${"%.2f".format(det.confidence)}")
        if (dump != null) {
            val out = s.color.clone()
            Imgproc.polylines(out, listOf(MatOfPoint(*s.truth.map { Point(it.x, it.y) }.toTypedArray())), true, Scalar(0.0, 200.0, 0.0), 2)
            Imgproc.polylines(out, listOf(MatOfPoint(*found.map { Point(it.x, it.y) }.toTypedArray())), true, Scalar(0.0, 0.0, 255.0), 3)
            Imgcodecs.imwrite("$dump/${s.name}.jpg", out)
        }
    }
    val bench = rows.filter { !it.first.startsWith("x_") && !it.first.startsWith("n_") }
    if (bench.isEmpty()) return
    println("bench: mean IoU ${"%.3f".format(bench.map { it.third }.average())}, worst err ${"%.2f".format(bench.maxOf { it.second })}")
    val x = rows.filter { it.first.startsWith("x_") }
    if (x.isNotEmpty()) println("extra: mean IoU ${"%.3f".format(x.map { it.third }.average())}, under 2% err: ${x.count { it.second < 2.0 }}/${x.size}")
}
