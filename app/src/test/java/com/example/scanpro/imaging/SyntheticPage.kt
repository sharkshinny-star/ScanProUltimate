package com.example.scanpro.imaging

import org.opencv.core.Mat

/**
 * A made-up photographed page, the same every time: off-white paper darkening evenly from left (1.0) to right
 * (0.6) like an uneven shadow, ten lines of dark text, a red and a blue block like logos, and a skin-coloured
 * finger on the left edge. Small enough that a golden image is a few kilobytes.
 */
internal object SyntheticPage {
    const val WIDTH = 240
    const val HEIGHT = 320

    /** A spot on the finger, a few pixels in from the left edge. */
    val FINGER_PROBE = 4 to 150

    /** Patches of bare paper at the left and right edges, between two text lines. */
    val PAPER_LEFT = IntRange(6, 14) to IntRange(40, 46)
    val PAPER_RIGHT = IntRange(222, 230) to IntRange(40, 46)

    private val PAPER = intArrayOf(235, 232, 225)
    private val INK = intArrayOf(30, 30, 36)
    private val SKIN = intArrayOf(224, 172, 105)

    fun create(): Mat = bytesToMat(createBytes(), WIDTH, HEIGHT)

    fun createBytes(): ByteArray {
        val pixels = ByteArray(WIDTH * HEIGHT * 3)
        var seed = 12345L
        fun next(): Int {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return (seed shr 8).toInt()
        }
        fun set(x: Int, y: Int, rgb: IntArray) {
            val i = (y * WIDTH + x) * 3
            for (c in 0..2) pixels[i + c] = rgb[c].coerceIn(0, 255).toByte()
        }

        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val shade = 1.0 - 0.4 * x / (WIDTH - 1)
            val noise = next() % 5 - 2
            set(x, y, IntArray(3) { c -> (PAPER[c] * shade).toInt() + noise })
        }
        // Ten lines of text: runs of ink with gaps, from x = 20 to 220.
        for (row in 0 until 10) {
            val top = 30 + row * 20
            var x = 20
            while (true) {
                val width = 8 + next() % 30
                if (x + width > WIDTH - 20) break
                for (yy in top until top + 8) for (xx in x until x + width) set(xx, yy, INK)
                x += width + 5 + next() % 6
            }
        }
        // Two coloured blocks below the text.
        for (y in 240 until 290) for (x in 170 until 215) set(x, y, intArrayOf(200, 45, 45))
        for (y in 240 until 290) for (x in 30 until 100) set(x, y, intArrayOf(30, 60, 170))
        // A finger entering from the left edge, over the first text lines.
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val dx = (x - 6) / 52.0
            val dy = (y - 150) / 48.0
            if (dx * dx + dy * dy <= 1.0) set(x, y, IntArray(3) { c -> SKIN[c] + next() % 3 - 1 })
        }
        return pixels
    }

    fun luminance(bytes: ByteArray, x: Int, y: Int): Double {
        val i = (y * WIDTH + x) * 3
        return 0.299 * bytes[i].u() + 0.587 * bytes[i + 1].u() + 0.114 * bytes[i + 2].u()
    }

    fun meanLuminance(bytes: ByteArray, patch: Pair<IntRange, IntRange>): Double {
        var sum = 0.0
        var n = 0
        for (y in patch.second) for (x in patch.first) { sum += luminance(bytes, x, y); n++ }
        return sum / n
    }
}
