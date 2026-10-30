package com.example.scanpro.imaging

import kotlin.math.max
import kotlin.math.min

/**
 * The page filters as they were written before the pipeline moved onto OpenCV images: per-pixel Kotlin on
 * 8-bit RGB bytes, and Android's ColorMatrix arithmetic spelled out. The tests compare the OpenCV versions
 * against these, so a behaviour change in the rewrite shows up as a difference here.
 */
internal object ReferenceFilters {
    /** Android's ColorMatrix.setSaturation followed by a contrast gain and a shift, on 8-bit RGB. */
    fun colorMatrix(rgb: ByteArray, saturation: Float, contrast: Float, brightness: Float): ByteArray {
        val inv = 1f - saturation
        val r = 0.213f * inv; val g = 0.715f * inv; val b = 0.072f * inv
        val translation = 128f * (1f - contrast) + brightness
        val out = ByteArray(rgb.size)
        var i = 0
        while (i < rgb.size) {
            val pr = rgb[i].u().toFloat(); val pg = rgb[i + 1].u().toFloat(); val pb = rgb[i + 2].u().toFloat()
            val sr = (r + saturation) * pr + g * pg + b * pb
            val sg = r * pr + (g + saturation) * pg + b * pb
            val sb = r * pr + g * pg + (b + saturation) * pb
            out[i] = (contrast * sr + translation + 0.5f).toInt().coerceIn(0, 255).toByte()
            out[i + 1] = (contrast * sg + translation + 0.5f).toInt().coerceIn(0, 255).toByte()
            out[i + 2] = (contrast * sb + translation + 0.5f).toInt().coerceIn(0, 255).toByte()
            i += 3
        }
        return out
    }

    /** Lifts the paper towards white: +16 everywhere, +42 where the darkest channel is above 150; then a colour matrix. */
    fun whiteboard(rgb: ByteArray): ByteArray {
        val boosted = ByteArray(rgb.size)
        var i = 0
        while (i < rgb.size) {
            val r = rgb[i].u(); val g = rgb[i + 1].u(); val b = rgb[i + 2].u()
            val boost = if (min(r, min(g, b)) > 150) 42 else 16
            boosted[i] = (r + boost).coerceAtMost(255).toByte()
            boosted[i + 1] = (g + boost).coerceAtMost(255).toByte()
            boosted[i + 2] = (b + boost).coerceAtMost(255).toByte()
            i += 3
        }
        return colorMatrix(boosted, saturation = 1.08f, contrast = 1.28f, brightness = 6f)
    }

    /** Integral-image adaptive threshold: true where the pixel stays white (not darker than its surroundings by the bias). */
    fun adaptiveBlackWhite(rgb: ByteArray, width: Int, height: Int, margins: IntArray? = null): BooleanArray {
        val gray = IntArray(width * height) { p ->
            (0.299f * rgb[p * 3].u() + 0.587f * rgb[p * 3 + 1].u() + 0.114f * rgb[p * 3 + 2].u()).toInt()
        }
        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            for (x in 1..width) {
                rowSum += gray[(y - 1) * width + x - 1]
                integral[y * stride + x] = integral[(y - 1) * stride + x] + rowSum
            }
        }
        val radius = max(10, min(width, height) / 55)
        val bias = 13
        val white = BooleanArray(width * height)
        for (y in 0 until height) {
            val y1 = max(0, y - radius); val y2 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x1 = max(0, x - radius); val x2 = min(width - 1, x + radius)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] - integral[y1 * stride + (x2 + 1)] -
                    integral[(y2 + 1) * stride + x1] + integral[y1 * stride + x1]
                val localMean = (sum / area).toInt()
                white[y * width + x] = !(gray[y * width + x] < localMean - bias)
                // How far the pixel is above its threshold; zero or one means it was nearly a coin toss.
                margins?.set(y * width + x, gray[y * width + x] - (localMean - bias))
            }
        }
        return white
    }
}
