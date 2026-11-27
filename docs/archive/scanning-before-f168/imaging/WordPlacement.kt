package com.example.scanpro.imaging

import com.example.scanpro.data.OcrWord

/**
 * Where to draw one recognised word so its invisible text lies exactly under the printed word.
 * The page image sits in a rectangle on the PDF page, and the word box is a fraction of that image.
 */
internal data class WordPlacement(val x: Float, val baseline: Float, val textSize: Float, val width: Float)

internal object WordPlacementMath {
    /** Fraction of the box height that the glyphs' ascent-to-baseline span occupies; keeps the baseline near the bottom of the word. */
    private const val BASELINE_FROM_BOTTOM = 0.2f
    private const val MIN_TEXT_SIZE = 2f

    fun place(word: OcrWord, imageLeft: Float, imageTop: Float, imageWidth: Float, imageHeight: Float): WordPlacement? {
        val width = (word.right - word.left) * imageWidth
        val height = (word.bottom - word.top) * imageHeight
        if (width <= 0.5f || height <= 0.5f) return null
        val bottom = imageTop + word.bottom * imageHeight
        return WordPlacement(
            x = imageLeft + word.left * imageWidth,
            baseline = bottom - height * BASELINE_FROM_BOTTOM,
            textSize = maxOf(MIN_TEXT_SIZE, height * 0.8f),
            width = width
        )
    }
}
