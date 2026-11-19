package com.example.scanpro.imaging

import android.graphics.Rect
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Coordinates between the upright page (where the crop is drawn) and the photo as stored (turned by its EXIF tag), for
 * reading only the page's area of a large photo. EXIF [rotation] is the clockwise turn that makes the stored photo
 * upright: 0, 90, 180 or 270.
 */
internal object PageArea {
    /** Margin around the page, as a share of the photo, so the perspective warp has pixels at its edges. */
    private const val MARGIN = 0.01f

    fun toRaw(u: Float, v: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> v to 1f - u
        180 -> 1f - u to 1f - v
        270 -> 1f - v to u
        else -> u to v
    }

    fun toUpright(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> 1f - y to x
        180 -> 1f - x to 1f - y
        270 -> y to 1f - x
        else -> x to y
    }

    private fun CropQuad.points() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** The page's bounding box in the stored photo, in pixels, with a small margin. */
    fun rawBox(crop: CropQuad, rotation: Int, width: Int, height: Int): Rect {
        val raw = crop.points().map { toRaw(it.x, it.y, rotation) }
        val x0 = (raw.minOf { it.first } - MARGIN).coerceIn(0f, 1f)
        val x1 = (raw.maxOf { it.first } + MARGIN).coerceIn(0f, 1f)
        val y0 = (raw.minOf { it.second } - MARGIN).coerceIn(0f, 1f)
        val y1 = (raw.maxOf { it.second } + MARGIN).coerceIn(0f, 1f)
        return Rect(floor(x0 * width).toInt(), floor(y0 * height).toInt(), ceil(x1 * width).toInt(), ceil(y1 * height).toInt())
    }

    /** [crop] expressed within the upright picture of [box] (the stored-photo pixels [rawBox] chose). */
    fun cropWithin(crop: CropQuad, box: Rect, rotation: Int, width: Int, height: Int): CropQuad {
        val corners = listOf(
            box.left.toFloat() / width to box.top.toFloat() / height,
            box.right.toFloat() / width to box.bottom.toFloat() / height
        ).map { (x, y) -> toUpright(x, y, rotation) }
        val u0 = minOf(corners[0].first, corners[1].first)
        val u1 = maxOf(corners[0].first, corners[1].first)
        val v0 = minOf(corners[0].second, corners[1].second)
        val v1 = maxOf(corners[0].second, corners[1].second)
        fun map(p: NormalizedPoint) = NormalizedPoint(
            ((p.x - u0) / (u1 - u0)).coerceIn(0f, 1f),
            ((p.y - v0) / (v1 - v0)).coerceIn(0f, 1f)
        )
        // A bow is an offset, a share of the picture's width and height, so it only changes scale.
        fun bow(p: NormalizedPoint?) = p?.let { NormalizedPoint(it.x / (u1 - u0), it.y / (v1 - v0)) }
        return CropQuad(
            map(crop.topLeft), map(crop.topRight), map(crop.bottomRight), map(crop.bottomLeft),
            topBow = bow(crop.topBow), bottomBow = bow(crop.bottomBow), leftBow = bow(crop.leftBow), rightBow = bow(crop.rightBow)
        )
    }
}
