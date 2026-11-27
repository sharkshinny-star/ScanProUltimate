package com.example.scanpro.scanner

/**
 * Whether a photographed page is probably blank (PRD 10.2: blank-page detection for batch scans). Judged on 8-bit
 * brightness values of the page area alone, so it needs neither Android nor OpenCV and runs in plain JVM tests.
 *
 * The page is blank when almost nothing on it is darker than the paper. "The paper" is the median level, so an
 * evenly shaded or dim sheet still reads as blank, while any text, drawing, stamp or signature leaves ink pixels.
 * It is deliberately cautious: a false "looks blank" only shows a hint the user can ignore (nothing is deleted),
 * but a page with content must never be called blank, so dark frames and sparse-but-real marks count as content.
 */
object BlankPage {
    /** Below this median the frame is a dark scene or a whiteboard, not paper: never called blank (the camera says "too dark"). */
    const val MIN_PAPER_LEVEL = 90

    /** A pixel this many levels under the paper level is ink. Paper grain and sensor noise stay well within it. */
    const val INK_DELTA = 32

    /** A page with at most this share of ink pixels is blank: under a tenth of a percent, less than one small stamp. */
    const val MAX_INK_SHARE = 0.0006

    /** Fewer samples than this say nothing about a page. */
    const val MIN_SAMPLES = 1024

    fun isBlank(luma: ByteArray): Boolean {
        if (luma.size < MIN_SAMPLES) return false
        val histogram = IntArray(256)
        for (value in luma) histogram[value.toInt() and 0xFF]++
        val half = luma.size / 2
        var paper = 0
        var seen = 0
        while (paper < 255 && seen + histogram[paper] <= half) {
            seen += histogram[paper]
            paper++
        }
        if (paper < MIN_PAPER_LEVEL) return false
        val inkBelow = paper - INK_DELTA
        var ink = 0
        for (level in 0 until inkBelow) ink += histogram[level]
        return ink <= luma.size * MAX_INK_SHARE
    }
}
