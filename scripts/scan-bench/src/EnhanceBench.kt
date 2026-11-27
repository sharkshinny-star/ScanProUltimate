import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.imaging.MatFilters
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Random
import kotlin.math.max

/*
 * Enhancement and readability benchmark for the app's page filters (MatFilters.kt, compiled unchanged), the
 * measuring stick the roadmap asks for ("score each filter ... then tune, instead of judging by eye").
 *
 * A known flat page (ink text, coloured patches, paper of a known colour) is degraded the ways a photographed,
 * already-cropped page really is: a warm colour cast, a brightness ramp across the page, a corner shadow, dim noisy
 * light, a sheen of glare. Each of the app's filters then runs on each degraded page, and the output is scored
 * against the undegraded original using masks taken from the original:
 *
 *   contrast  mean paper luminance minus mean ink luminance (higher reads better); the original is about 211
 *   bgStd     the paper's luminance spread (lower is a cleaner, more even background)
 *   blown%    share of paper pixels at luminance 252 and up (whites clipped flat by over-enhancement)
 *   crushed%  share of ink pixels at luminance 3 and down (black-and-white output is expected to crush ink)
 *   halo      mean brightness overshoot above the paper level in a ring around the ink strokes (unsharp halos)
 *   dE        mean CIE Lab distance of the five colour patches from their true colours (colour fidelity; for GRAY
 *             and BW it measures desaturation and is reported only for completeness)
 *   faint%    how much of the faint content survives: a pencil-grey line and a highlighter bar are drawn on the
 *             original, and their Lab distinctness from the surrounding paper is measured again on the output, as a
 *             share of the original distinctness. 100 means fully kept, 0 means wiped out; this is the real harm
 *             behind a high blown% (a clean white background is fine until it eats the pencil notes)
 *
 * What a good filter does: raise contrast, cut bgStd, keep blown%, halo and dE low. The "input" row is the degraded
 * page before any filter, so each filter's row can be read as repair against that baseline. OCR word accuracy on a
 * phone (ML Kit) is the follow-up the roadmap names; nothing here substitutes for it.
 */

private const val PW = 1000
private const val PH = 1400

private class Patch(val rect: Rect, val color: Scalar)

/** Content much fainter than ink: pencil marks and highlighter, the first casualties of over-enhancement. */
private class FaintElement(val name: String, val rect: Rect)

private class PageTruth(val rgb: Mat, val inkMask: Mat, val paperMask: Mat, val haloRing: Mat, val patches: List<Patch>, val paper: Scalar, val faint: List<FaintElement>)

private fun renderTruth(): PageTruth {
    val img = BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val paper = Color(248, 247, 242)
    g.color = paper
    g.fillRect(0, 0, PW, PH)
    g.color = Color(30, 30, 34)
    val random = Random(7)
    val words = listOf("Invoice", "Total", "ScanPro", "Quantity", "Amount", "Payment", "Reference", "Tax", "Subtotal", "Date")
    var y = 120f
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 44)
    g.drawString("Subtotal Balance Payment", 80f, y)
    y += 70f
    while (y < PH - 260f) {
        val small = random.nextInt(4) == 0
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, if (small) 17 else 26)
        val text = StringBuilder()
        while (g.fontMetrics.stringWidth(text.toString()) < (PW - 160) * (0.55f + random.nextFloat() * 0.45f)) text.append(words[random.nextInt(words.size)]).append(' ')
        g.drawString(text.toString().trim(), 80f, y)
        if (random.nextInt(9) == 0) { g.stroke = BasicStroke(2f); g.drawLine(80, (y - 18).toInt(), PW - 80, (y - 18).toInt()) }
        y += if (small) 28f else 42f
    }
    // Faint content: a pencil-grey line and a pale highlighter bar. Over-enhancement wipes these out first.
    val faint = ArrayList<FaintElement>()
    run {
        val pencil = Rect(80, PH - 320, 600, 36)
        g.color = Color(176, 176, 176)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 26)
        g.drawString("pencil note in light grey pencil note", pencil.x.toFloat(), (pencil.y + 28).toFloat())
        faint += FaintElement("pencil", pencil)
        val mark = Rect(700, PH - 320, 220, 36)
        g.color = Color(250, 238, 150)
        g.fillRect(mark.x, mark.y, mark.width, mark.height)
        faint += FaintElement("highlight", mark)
    }

    // Five colour patches of known colour along the bottom, like a letterhead or a chart.
    val colors = listOf(Color(200, 40, 40), Color(40, 140, 60), Color(40, 70, 180), Color(220, 170, 30), Color(120, 60, 150))
    val patches = ArrayList<Patch>()
    colors.forEachIndexed { i, c ->
        val r = Rect(80 + i * 176, PH - 200, 140, 110)
        g.color = c
        g.fillRect(r.x, r.y, r.width, r.height)
        patches += Patch(r, Scalar(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble()))
    }
    g.dispose()
    val rgb = bufToMat(img)
    Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_BGR2RGB)

    // Masks from the clean original: ink is dark, paper is bright and clear of ink and patches.
    val gray = Mat()
    Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
    val ink = Mat()
    Imgproc.threshold(gray, ink, 100.0, 255.0, Imgproc.THRESH_BINARY_INV)
    patches.forEach { Imgproc.rectangle(ink, it.rect, Scalar(0.0), -1) }
    val inkWide = Mat()
    val inkNear = Mat()
    val k7 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
    val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
    Imgproc.dilate(ink, inkWide, k7)
    Imgproc.dilate(ink, inkNear, k3)
    val haloRing = Mat()
    Core.subtract(inkWide, inkNear, haloRing)
    val paperMask = Mat()
    Imgproc.threshold(gray, paperMask, 200.0, 255.0, Imgproc.THRESH_BINARY)
    Core.subtract(paperMask, inkWide, paperMask)
    patches.forEach { p -> Imgproc.rectangle(paperMask, Rect(p.rect.x - 6, p.rect.y - 6, p.rect.width + 12, p.rect.height + 12), Scalar(0.0), -1) }
    faint.forEach { p -> Imgproc.rectangle(paperMask, Rect(p.rect.x - 6, p.rect.y - 6, p.rect.width + 12, p.rect.height + 12), Scalar(0.0), -1) }
    listOf(gray, ink, inkWide, inkNear, k7, k3).forEach(Mat::release)
    return PageTruth(rgb, Mat(), paperMask, haloRing, patches, Scalar(248.0, 247.0, 242.0), faint).also { truth ->
        // The ink mask proper (eroded-free original) is rebuilt here so the metrics sample real stroke pixels.
        val g2 = Mat(); Imgproc.cvtColor(rgb, g2, Imgproc.COLOR_RGB2GRAY)
        Imgproc.threshold(g2, truth.inkMask, 90.0, 255.0, Imgproc.THRESH_BINARY_INV)
        truth.patches.forEach { Imgproc.rectangle(truth.inkMask, it.rect, Scalar(0.0), -1) }
        g2.release()
    }
}

/** A multiplicative light field (per-channel gains that vary across the page) plus noise and blur. */
private fun degrade(truth: Mat, name: String): Mat {
    val f = Mat()
    truth.convertTo(f, CvType.CV_32FC3, 1 / 255.0)
    val gain = Mat(PH, PW, CvType.CV_32FC3)
    val row = FloatArray(PW * 3)
    for (y in 0 until PH) {
        for (x in 0 until PW) {
            val fx = x.toDouble() / PW
            val fy = y.toDouble() / PH
            var r = 1.0; var g = 1.0; var b = 1.0
            when (name) {
                "warm_cast" -> { r = 1.02; g = 0.97; b = 0.84 }
                "side_ramp" -> { val d = 1.0 - 0.45 * fx; r = d * 1.03; g = d * 0.99; b = d * 0.90 }
                "corner_shadow" -> {
                    val dist = Math.hypot(fx - 1.0, fy - 1.0)
                    val d = (0.52 + 0.48 * (dist / 0.9).coerceAtMost(1.0))
                    r = d; g = d; b = d * 0.96
                }
                "dim_noisy" -> { r = 0.42; g = 0.42; b = 0.40 }
                "glare_sheen" -> {
                    val dist = Math.hypot((fx - 0.65) * 1.4, (fy - 0.3) * 2.0)
                    val boost = 1.0 + 0.5 * Math.exp(-dist * dist * 14)
                    r = boost; g = boost; b = boost
                }
            }
            row[x * 3] = r.toFloat(); row[x * 3 + 1] = g.toFloat(); row[x * 3 + 2] = b.toFloat()
        }
        gain.put(y, 0, row)
    }
    Core.multiply(f, gain, f)
    Core.min(f, Scalar(1.0, 1.0, 1.0), f)
    val out = Mat()
    f.convertTo(out, CvType.CV_8UC3, 255.0)
    f.release(); gain.release()
    if (name == "dim_noisy") {
        val noise = Mat(PH, PW, CvType.CV_8UC3)
        Core.randn(noise, 0.0, 8.0)
        Core.add(out, noise, out)
        noise.release()
        Imgproc.GaussianBlur(out, out, Size(3.0, 3.0), 0.0)
    }
    return out
}

private class Scores(val contrast: Double, val bgStd: Double, val blown: Double, val crushed: Double, val halo: Double, val dE: Double, val faint: Double)

/** Mean Lab distance between the faint elements and the paper around them, on any image (1- or 3-channel). */
private fun faintDistinctness(image: Mat, truth: PageTruth): Double {
    val rgb = Mat()
    if (image.channels() == 3) image.copyTo(rgb) else Imgproc.cvtColor(image, rgb, Imgproc.COLOR_GRAY2RGB)
    val lab = Mat()
    Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
    val paper = Core.mean(lab, truth.paperMask).`val`
    var sum = 0.0
    truth.faint.forEach { e ->
        val m = Core.mean(lab.submat(e.rect)).`val`
        sum += Math.sqrt((m[0] - paper[0]) * (m[0] - paper[0]) + (m[1] - paper[1]) * (m[1] - paper[1]) + (m[2] - paper[2]) * (m[2] - paper[2]))
    }
    rgb.release(); lab.release()
    return sum / truth.faint.size
}

private fun score(rgb: Mat, truth: PageTruth, faintTruth: Double = 1.0): Scores {
    val gray = Mat()
    val source = if (rgb.channels() == 1) rgb else { Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY); gray }
    fun meanOn(mask: Mat) = Core.mean(source, mask).`val`[0]
    val paperMean = meanOn(truth.paperMask)
    val inkMean = meanOn(truth.inkMask)
    val std = MatOfDouble(); val mean = MatOfDouble()
    Core.meanStdDev(source, mean, std, truth.paperMask)
    val bgStd = std.toArray()[0]
    fun clippedShare(mask: Mat, threshold: Double, above: Boolean): Double {
        val clipped = Mat()
        Imgproc.threshold(source, clipped, threshold, 255.0, if (above) Imgproc.THRESH_BINARY else Imgproc.THRESH_BINARY_INV)
        val onMask = Mat()
        Core.bitwise_and(clipped, mask, onMask)
        val share = Core.countNonZero(onMask).toDouble() / max(1, Core.countNonZero(mask))
        clipped.release(); onMask.release()
        return share
    }
    val blown = clippedShare(truth.paperMask, 251.0, true)
    val crushed = clippedShare(truth.inkMask, 4.0, false)
    val halo = (Core.mean(source, truth.haloRing).`val`[0] - paperMean).coerceAtLeast(0.0)
    // Colour fidelity of the patches in Lab; a grayscale result keeps L only, so its dE mostly measures desaturation.
    var dE = 0.0
    if (rgb.channels() == 3) {
        val lab = Mat(); Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        val truthLab = Mat(); Imgproc.cvtColor(truth.rgb, truthLab, Imgproc.COLOR_RGB2Lab)
        truth.patches.forEach { p ->
            val a = Core.mean(lab.submat(p.rect)).`val`
            val b = Core.mean(truthLab.submat(p.rect)).`val`
            dE += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))
        }
        dE /= truth.patches.size
        lab.release(); truthLab.release()
    } else dE = Double.NaN
    std.release(); mean.release()
    if (source === gray) gray.release()
    val faint = 100.0 * faintDistinctness(rgb, truth) / faintTruth
    return Scores(paperMean - inkMean, bgStd, blown, crushed, halo, dE, faint)
}

fun enhanceBench(dump: String?) {
    val truth = renderTruth()
    val degradations = listOf("warm_cast", "side_ramp", "corner_shadow", "dim_noisy", "glare_sheen")
    val filters = listOf(PageFilter.ORIGINAL, PageFilter.AUTO, PageFilter.COLOR, PageFilter.GRAY, PageFilter.BW, PageFilter.WHITEBOARD)
    val faintTruth = faintDistinctness(truth.rgb, truth)
    val clean = score(truth.rgb, truth, faintTruth)
    println("clean original: contrast %.0f bgStd %.1f blown %.1f%% halo %.1f faint %.0f%%".format(clean.contrast, clean.bgStd, clean.blown * 100, clean.halo, clean.faint))
    degradations.forEach { name ->
        val input = degrade(truth.rgb, name)
        println("\n== $name")
        println("%-10s | %8s | %5s | %6s | %8s | %4s | %5s | %6s".format("filter", "contrast", "bgStd", "blown%", "crushed%", "halo", "dE", "faint%"))
        val base = score(input, truth, faintTruth)
        println("%-10s | %8.0f | %5.1f | %6.2f | %8.2f | %4.1f | %5.1f | %6.0f".format("input", base.contrast, base.bgStd, base.blown * 100, base.crushed * 100, base.halo, base.dE, base.faint))
        filters.forEach { filter ->
            val out = MatFilters.look(input.clone(), filter, PageAdjustments.DEFAULT)
            val s = score(out, truth, faintTruth)
            println("%-10s | %8.0f | %5.1f | %6.2f | %8.2f | %4.1f | %5.1f | %6.0f".format(filter.name, s.contrast, s.bgStd, s.blown * 100, s.crushed * 100, s.halo, s.dE, s.faint))
            if (dump != null) {
                val bgr = Mat()
                if (out.channels() == 3) Imgproc.cvtColor(out, bgr, Imgproc.COLOR_RGB2BGR) else out.copyTo(bgr)
                Imgcodecs.imwrite("$dump/enh_${name}_${filter.name}.png", bgr)
                bgr.release()
            }
            out.release()
        }
        if (dump != null) { val bgr = Mat(); Imgproc.cvtColor(input, bgr, Imgproc.COLOR_RGB2BGR); Imgcodecs.imwrite("$dump/enh_${name}_input.png", bgr); bgr.release() }
        input.release()
    }
}
