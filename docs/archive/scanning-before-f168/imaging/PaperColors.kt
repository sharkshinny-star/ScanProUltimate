package com.example.scanpro.imaging

/**
 * Guesses the paper and ink colours of a patch of a page, so replaced words can be painted over in the paper's
 * own shade and rewritten in a matching ink. Works on ARGB pixels; pure arithmetic, no Android types.
 */
object PaperColors {
    data class Estimate(val paper: Int, val ink: Int)

    fun estimate(pixels: IntArray): Estimate {
        if (pixels.isEmpty()) return Estimate(WHITE, DEFAULT_INK)
        val byLuma = pixels.sortedBy { luma(it) }
        // Paper: the brighter half. Ink: the darkest few percent, which on a line of text is the letters.
        val paper = median(byLuma.subList(byLuma.size / 2, byLuma.size))
        val inkCount = (byLuma.size * 0.06f).toInt().coerceAtLeast(1)
        val ink = median(byLuma.subList(0, inkCount))
        // Too little contrast means there were no letters to sample; fall back to near-black.
        return Estimate(paper, if (luma(paper) - luma(ink) < 60) DEFAULT_INK else ink)
    }

    private fun median(pixels: List<Int>): Int {
        fun channel(shift: Int) = pixels.map { (it shr shift) and 0xFF }.sorted()[pixels.size / 2]
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luma(argb: Int): Int =
        (299 * ((argb shr 16) and 0xFF) + 587 * ((argb shr 8) and 0xFF) + 114 * (argb and 0xFF)) / 1000

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DEFAULT_INK = 0xFF141414.toInt()
}
