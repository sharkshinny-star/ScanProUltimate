package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint

internal fun quadCorners(crop: CropQuad) = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)

/** Maps a crop outline onto the image after it is turned a quarter turn; corners keep their reading order. */
internal fun rotateQuad(crop: CropQuad, clockwise: Boolean): CropQuad {
    fun cw(p: NormalizedPoint) = NormalizedPoint(1f - p.y, p.x)
    fun ccw(p: NormalizedPoint) = NormalizedPoint(p.y, 1f - p.x)
    return if (clockwise) {
        CropQuad(cw(crop.bottomLeft), cw(crop.topLeft), cw(crop.topRight), cw(crop.bottomRight))
    } else {
        CropQuad(ccw(crop.topRight), ccw(crop.bottomRight), ccw(crop.bottomLeft), ccw(crop.topLeft))
    }
}

internal fun encodeQuads(map: Map<String, CropQuad>): List<String> = map.map { (key, q) ->
    key + "|" + quadCorners(q).joinToString(",") { "${it.x},${it.y}" }
}

internal fun decodeQuad(text: String): Pair<String, CropQuad>? = runCatching {
    val (key, values) = text.split("|", limit = 2)
    val n = values.split(",").map { it.toFloat() }
    key to CropQuad(
        NormalizedPoint(n[0], n[1]), NormalizedPoint(n[2], n[3]),
        NormalizedPoint(n[4], n[5]), NormalizedPoint(n[6], n[7])
    )
}.getOrNull()
