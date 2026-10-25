package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint

internal fun quadCorners(crop: CropQuad) = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)

/**
 * Maps a crop outline onto the image after it is turned a quarter turn; corners keep their reading order and
 * each bent edge keeps its bend, moving to the side it now faces.
 */
internal fun rotateQuad(crop: CropQuad, clockwise: Boolean): CropQuad {
    fun cw(p: NormalizedPoint) = NormalizedPoint(1f - p.y, p.x)
    fun ccw(p: NormalizedPoint) = NormalizedPoint(p.y, 1f - p.x)
    // Bends are offsets, so they turn without the shift that positions need.
    fun cwBow(b: NormalizedPoint?) = b?.let { NormalizedPoint(-it.y, it.x) }
    fun ccwBow(b: NormalizedPoint?) = b?.let { NormalizedPoint(it.y, -it.x) }
    return if (clockwise) {
        CropQuad(
            cw(crop.bottomLeft), cw(crop.topLeft), cw(crop.topRight), cw(crop.bottomRight),
            topBow = cwBow(crop.leftBow), bottomBow = cwBow(crop.rightBow),
            leftBow = cwBow(crop.bottomBow), rightBow = cwBow(crop.topBow)
        )
    } else {
        CropQuad(
            ccw(crop.topRight), ccw(crop.bottomRight), ccw(crop.bottomLeft), ccw(crop.topLeft),
            topBow = ccwBow(crop.rightBow), bottomBow = ccwBow(crop.leftBow),
            leftBow = ccwBow(crop.topBow), rightBow = ccwBow(crop.bottomBow)
        )
    }
}

private fun bows(q: CropQuad) = listOf(q.topBow, q.bottomBow, q.leftBow, q.rightBow)

/** "key|8 corner values" for a straight outline, plus 8 bend values when any edge is bent. */
internal fun encodeQuads(map: Map<String, CropQuad>): List<String> = map.map { (key, q) ->
    val corners = quadCorners(q).joinToString(",") { "${it.x},${it.y}" }
    if (!q.isCurved) "$key|$corners"
    else "$key|$corners," + bows(q).joinToString(",") { "${it?.x ?: 0f},${it?.y ?: 0f}" }
}

internal fun decodeQuad(text: String): Pair<String, CropQuad>? = runCatching {
    val (key, values) = text.split("|", limit = 2)
    val n = values.split(",").map { it.toFloat() }
    fun bow(i: Int) = if (n.size >= 16 && (n[i] != 0f || n[i + 1] != 0f)) NormalizedPoint(n[i], n[i + 1]) else null
    key to CropQuad(
        NormalizedPoint(n[0], n[1]), NormalizedPoint(n[2], n[3]),
        NormalizedPoint(n[4], n[5]), NormalizedPoint(n[6], n[7]),
        topBow = bow(8), bottomBow = bow(10), leftBow = bow(12), rightBow = bow(14)
    )
}.getOrNull()
