package com.example.scanpro.imaging

import android.graphics.Bitmap
import com.example.scanpro.data.CropQuad
import java.io.File
import kotlin.math.roundToInt

/**
 * Book mode's two-page capture (R29, as Adobe Scan, CamScanner and OKEN do): a photographed spread is cut out along
 * its outline, then split down the spine into two page images, left page first unless the book reads right to left.
 */
object BookSpread {
    /** The two halves of a [width] x [height] spread split at [fraction] of its width: (left, right) as x ranges. */
    data class Halves(val leftStart: Int, val leftWidth: Int, val rightStart: Int, val rightWidth: Int)

    fun halves(width: Int, fraction: Float = 0.5f): Halves {
        val split = (width * fraction.coerceIn(0.2f, 0.8f)).roundToInt().coerceIn(1, width - 1)
        return Halves(0, split, split, width - split)
    }

    /** The halves in reading order. */
    fun <T> ordered(left: T, right: T, leftFirst: Boolean): List<T> = if (leftFirst) listOf(left, right) else listOf(right, left)

    /**
     * Cuts [source] along [quad], splits it at [fraction] and writes the halves to [leftFile] and [rightFile] as
     * upright JPEGs; the files are the new pages. Throws when the photo cannot be read.
     */
    fun split(source: File, quad: CropQuad, leftFile: File, rightFile: File, fraction: Float = 0.5f) {
        val spread = ImageProcessor.cropSourceToBitmap(source, quad, 3800)
        try {
            val h = halves(spread.width, fraction)
            val left = Bitmap.createBitmap(spread, h.leftStart, 0, h.leftWidth, spread.height)
            try { ImageProcessor.saveBitmapJpeg(left, leftFile, 96) } finally { if (left !== spread) left.recycle() }
            val right = Bitmap.createBitmap(spread, h.rightStart, 0, h.rightWidth, spread.height)
            try { ImageProcessor.saveBitmapJpeg(right, rightFile, 96) } finally { if (right !== spread) right.recycle() }
        } finally {
            spread.recycle()
        }
    }
}
