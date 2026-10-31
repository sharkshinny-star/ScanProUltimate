package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.Mat

/**
 * Locks in what each look does to the synthetic page, two ways:
 *  - semantic checks that don't depend on any stored picture (the finger is gone, the shadow is even, black
 *    and white is pure, grey has no colour);
 *  - golden images under src/test/resources/golden that catch any other change in the output.
 *
 * To accept an intended change, run the tests with -Pscanpro.updateGoldens=true, look at the changed
 * pictures in git, and commit them.
 */
class MatFiltersGoldenTest {
    private val w = SyntheticPage.WIDTH
    private val h = SyntheticPage.HEIGHT
    private val all = PageAdjustments.DEFAULT

    @Before fun requireOpenCv() = NativeOpenCv.require()

    private fun render(block: (Mat) -> Mat): ByteArray {
        val result = block(SyntheticPage.create())
        return try { result.toBytes() } finally { result.release() }
    }

    private fun filtered(filter: PageFilter, adjustments: PageAdjustments = all) =
        render { MatFilters.look(it, filter, adjustments) }

    // ---- semantics -----------------------------------------------------------------------------------

    @Test
    fun `finger removal paints the finger over with paper`() {
        val (x, y) = SyntheticPage.FINGER_PROBE
        val before = SyntheticPage.createBytes()
        val after = render { MatFilters.cleanUp(it, PageAdjustments(removeFingers = true)) }
        val i = (y * w + x) * 3
        val skinRedOverBlue = before[i].u() - before[i + 2].u()
        val redOverBlue = after[i].u() - after[i + 2].u()
        assertTrue("the probe starts skin-coloured ($skinRedOverBlue)", skinRedOverBlue > 80)
        assertTrue("the probe is paper-coloured afterwards ($redOverBlue)", redOverBlue < 40)
    }

    @Test
    fun `shadow removal evens out the paper from left to right`() {
        val before = SyntheticPage.createBytes()
        val after = render { MatFilters.cleanUp(it, PageAdjustments(removeShadows = true)) }
        fun ratio(bytes: ByteArray) =
            SyntheticPage.meanLuminance(bytes, SyntheticPage.PAPER_LEFT) / SyntheticPage.meanLuminance(bytes, SyntheticPage.PAPER_RIGHT)
        assertTrue("the page starts unevenly lit (${ratio(before)})", ratio(before) > 1.4)
        assertTrue("and ends evenly lit (${ratio(after)})", ratio(after) in 0.9..1.12)
    }

    @Test
    fun `black and white is pure and keeps text black and paper white`() {
        val out = filtered(PageFilter.BW)
        for (i in out.indices) assertTrue(out[i].u() == 0 || out[i].u() == 255)
        // A pixel in the middle of a text run is black; bare paper is white.
        val (paperX, paperY) = SyntheticPage.PAPER_LEFT.first.first to SyntheticPage.PAPER_LEFT.second.first
        assertTrue(out[(paperY * w + paperX) * 3].u() == 255)
        val blackShare = out.indices.step(3).count { out[it].u() == 0 }.toDouble() / (w * h)
        assertTrue("some, but not most, of the page is black ($blackShare)", blackShare in 0.02..0.4)
    }

    @Test
    fun `grey has no colour`() {
        val out = filtered(PageFilter.GRAY)
        for (p in 0 until w * h) {
            assertTrue(out[p * 3] == out[p * 3 + 1] && out[p * 3 + 1] == out[p * 3 + 2])
        }
    }

    @Test
    fun `every named look changes the page`() {
        val original = SyntheticPage.createBytes()
        PageFilter.entries.filter { it != PageFilter.ORIGINAL }.forEach { filter ->
            assertTrue("$filter left the page as it was", meanAbsDiff(original, filtered(filter)) > 2.0)
        }
        assertTrue(meanAbsDiff(original, filtered(PageFilter.ORIGINAL)) == 0.0)
    }

    // ---- golden images ---------------------------------------------------------------------------------

    @Test fun `golden auto`() = golden("auto", filtered(PageFilter.AUTO))
    @Test fun `golden color`() = golden("color", filtered(PageFilter.COLOR))
    @Test fun `golden gray`() = golden("gray", filtered(PageFilter.GRAY))
    @Test fun `golden black and white`() = golden("bw", filtered(PageFilter.BW))
    @Test fun `golden whiteboard`() = golden("whiteboard", filtered(PageFilter.WHITEBOARD))
    @Test fun `golden shadows removed`() = golden("shadows", render { MatFilters.cleanUp(it, PageAdjustments(removeShadows = true)) })
    @Test fun `golden fingers removed`() = golden("fingers", render { MatFilters.cleanUp(it, PageAdjustments(removeFingers = true)) })
    @Test fun `golden tonal adjustments`() =
        golden("tonal", filtered(PageFilter.ORIGINAL, PageAdjustments(brightness = 0.3f, contrast = 1.2f, saturation = 1.4f)))
    @Test fun `golden full chain`() = golden(
        "chain",
        filtered(
            PageFilter.COLOR,
            PageAdjustments(brightness = 0.2f, contrast = 1.15f, saturation = 1.2f, removeShadows = true, removeFingers = true)
        )
    )

    private fun golden(name: String, actual: ByteArray) {
        val source = File("src/test/resources/golden/$name.png")
        if (System.getProperty("scanpro.updateGoldens") == "true") {
            source.parentFile?.mkdirs()
            writePng(source, actual)
            return
        }
        val stored = javaClass.getResourceAsStream("/golden/$name.png")
            ?: error("Missing golden image $name.png; run with -Pscanpro.updateGoldens=true to create it")
        val expected = stored.use { readPng(it) }
        val mean = meanAbsDiff(expected, actual)
        val far = actual.indices.count { abs(actual[it].u() - expected[it].u()) > 8 }.toDouble() / actual.size
        // Float code can differ by a level or two between CPUs; a real change moves far more than this.
        assertTrue("$name: mean difference $mean (limit 1.0)", mean <= 1.0)
        assertTrue("$name: ${"%.2f".format(far * 100)}% of values differ by more than 8 (limit 0.5%)", far <= 0.005)
    }

    private fun meanAbsDiff(a: ByteArray, b: ByteArray): Double {
        require(a.size == b.size) { "image sizes differ" }
        var sum = 0L
        for (i in a.indices) sum += abs(a[i].u() - b[i].u())
        return sum.toDouble() / a.size
    }

    private fun writePng(file: File, rgb: ByteArray) {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 3
            image.setRGB(x, y, (rgb[i].u() shl 16) or (rgb[i + 1].u() shl 8) or rgb[i + 2].u())
        }
        ImageIO.write(image, "png", file)
    }

    private fun readPng(stream: java.io.InputStream): ByteArray {
        val image = ImageIO.read(stream)
        require(image.width == w && image.height == h) { "golden image has the wrong size" }
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val argb = image.getRGB(x, y)
            val i = (y * w + x) * 3
            out[i] = (argb shr 16).toByte(); out[i + 1] = (argb shr 8).toByte(); out[i + 2] = argb.toByte()
        }
        return out
    }
}
