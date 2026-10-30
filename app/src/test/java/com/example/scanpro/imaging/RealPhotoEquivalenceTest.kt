package com.example.scanpro.imaging

import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.opencv.core.Mat

/**
 * Runs the filters on real photographs and compares them with the algorithms they replaced. It needs photos, which
 * are not stored in the repository, so it is skipped unless a folder of JPEG or PNG files is given:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*RealPhotoEquivalenceTest*' -Pscanpro.photoDir=/path/to/photos
 *
 * (OpenCV's own samples/data folder works, and so do your own scans.) A per-photo table is printed to the test output.
 */
class RealPhotoEquivalenceTest {
    @Before fun requireOpenCvAndPhotos() {
        NativeOpenCv.require()
        Assume.assumeTrue("no -Pscanpro.photoDir given", photoDir() != null)
    }

    private fun photoDir(): File? = System.getProperty("scanpro.photoDir").orEmpty().takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }

    private class Photo(val name: String, val width: Int, val height: Int, val rgb: ByteArray)

    private fun photos(): List<Photo> = photoDir()!!.listFiles { f -> f.name.matches(Regex("(?i).+\\.(jpg|jpeg|png)")) }!!
        .sortedBy { it.name }
        .mapNotNull { file ->
            val image = ImageIO.read(file) ?: return@mapNotNull null
            if (image.width < 100 || image.height < 100) return@mapNotNull null
            val rgb = ByteArray(image.width * image.height * 3)
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val p = image.getRGB(x, y)
                val i = (y * image.width + x) * 3
                rgb[i] = (p shr 16).toByte(); rgb[i + 1] = (p shr 8).toByte(); rgb[i + 2] = p.toByte()
            }
            Photo(file.name, image.width, image.height, rgb)
        }

    private fun Photo.mat() = bytesToMat(rgb, width, height)

    private fun maxDifference(a: ByteArray, b: ByteArray): Int {
        var worst = 0
        for (i in a.indices) worst = maxOf(worst, abs(a[i].u() - b[i].u()))
        return worst
    }

    @Test
    fun `colour matrix, whiteboard and black and white match the old filters on real photos`() {
        val list = photos()
        assertTrue("no usable photos in ${photoDir()}", list.isNotEmpty())
        val adjustments = PageAdjustments(brightness = 0.25f, contrast = 1.2f, saturation = 1.4f).normalized()
        println("%-26s %9s %7s %7s %8s %8s".format("photo", "size", "colour", "board", "bw-in%", "bw-all%"))
        var worstBwAll = 0.0

        for (photo in list) {
            val colour = MatFilters.applyUserAdjustments(photo.mat(), adjustments).let { out ->
                try {
                    maxDifference(ReferenceFilters.colorMatrix(photo.rgb, adjustments.saturation, adjustments.contrast, adjustments.brightness * 92f), out.toBytes())
                } finally { out.release() }
            }
            val board = MatFilters.whiteboard(photo.mat()).let { out ->
                try { maxDifference(ReferenceFilters.whiteboard(photo.rgb), out.toBytes()) } finally { out.release() }
            }
            val margins = IntArray(photo.width * photo.height)
            val expected = ReferenceFilters.adaptiveBlackWhite(photo.rgb, photo.width, photo.height, margins)
            val bw = MatFilters.adaptiveBlackWhite(photo.mat()).let { out -> try { out.toBytes() } finally { out.release() } }
            val radius = maxOf(10, minOf(photo.width, photo.height) / 55)
            var inner = 0L; var innerWrong = 0L; var wrong = 0L
            for (y in 0 until photo.height) for (x in 0 until photo.width) {
                val p = y * photo.width + x
                val mismatch = (bw[p * 3].u() == 255) != expected[p]
                if (mismatch) wrong++
                val inside = x >= radius && x < photo.width - radius && y >= radius && y < photo.height - radius
                if (inside) {
                    inner++
                    if (mismatch) {
                        innerWrong++
                        // Inside the page a difference is only ever a pixel sitting on the threshold.
                        assertTrue("${photo.name}: pixel $x,$y differs from the old filter by ${margins[p]} grey levels", abs(margins[p]) <= 1)
                    }
                }
            }
            val bwAll = 100.0 * wrong / (photo.width * photo.height)
            println("%-26s %4dx%-4d %7d %7d %8.2f %8.2f".format(photo.name, photo.width, photo.height, colour, board, 100.0 * innerWrong / maxOf(1, inner), bwAll))
            assertTrue("${photo.name}: colour matrix differs by $colour", colour <= 1)
            assertTrue("${photo.name}: whiteboard differs by $board", board <= 1)
            worstBwAll = maxOf(worstBwAll, bwAll)
        }
        assertTrue("black and white differs from the old filter on up to $worstBwAll% of a photo", worstBwAll <= 2.0)
    }

    @Test
    fun `every look runs on real photos and keeps the size`() {
        for (photo in photos()) {
            for (filter in PageFilter.entries) {
                val out: Mat = MatFilters.look(photo.mat(), filter, PageAdjustments(0.1f, 1.1f, 1.1f, removeShadows = true, removeFingers = true))
                try {
                    assertEquals("${photo.name} $filter width", photo.width, out.cols())
                    assertEquals("${photo.name} $filter height", photo.height, out.rows())
                } finally { out.release() }
            }
        }
    }
}
