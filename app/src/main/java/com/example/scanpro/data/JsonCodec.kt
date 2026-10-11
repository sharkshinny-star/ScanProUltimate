package com.example.scanpro.data

import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
    fun encodeStringList(items: List<String>): String = JSONArray().apply {
        items.forEach { put(it) }
    }.toString()

    fun decodeStringList(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    fun encodeCrop(crop: CropQuad): String = JSONObject().apply {
        put("tl", point(crop.topLeft))
        put("tr", point(crop.topRight))
        put("br", point(crop.bottomRight))
        put("bl", point(crop.bottomLeft))
    }.toString()

    fun decodeCrop(raw: String?): CropQuad = runCatching {
        if (raw.isNullOrBlank()) return@runCatching CropQuad.DEFAULT
        val json = JSONObject(raw)
        CropQuad(
            topLeft = decodePoint(json.getJSONObject("tl")),
            topRight = decodePoint(json.getJSONObject("tr")),
            bottomRight = decodePoint(json.getJSONObject("br")),
            bottomLeft = decodePoint(json.getJSONObject("bl"))
        )
    }.getOrDefault(CropQuad.DEFAULT)

    fun encodeAdjustments(value: PageAdjustments): String = JSONObject().apply {
        put("brightness", value.brightness.toDouble())
        put("contrast", value.contrast.toDouble())
        put("saturation", value.saturation.toDouble())
    }.toString()

    fun decodeAdjustments(raw: String?): PageAdjustments = runCatching {
        if (raw.isNullOrBlank()) return@runCatching PageAdjustments.DEFAULT
        val json = JSONObject(raw)
        PageAdjustments(
            brightness = json.optDouble("brightness", 0.0).toFloat(),
            contrast = json.optDouble("contrast", 1.0).toFloat(),
            saturation = json.optDouble("saturation", 1.0).toFloat()
        ).normalized()
    }.getOrDefault(PageAdjustments.DEFAULT)

    fun encodeAnnotations(items: List<PageAnnotation>): String = JSONArray().apply {
        items.forEach { annotation ->
            put(JSONObject().apply {
                put("id", annotation.id)
                put("type", annotation.type.name)
                put("x", annotation.x.toDouble())
                put("y", annotation.y.toDouble())
                put("w", annotation.width.toDouble())
                put("h", annotation.height.toDouble())
                put("rotation", annotation.rotationDegrees.toDouble())
                put("text", annotation.text)
                put("asset", annotation.assetPath)
                put("opacity", annotation.opacity.toDouble())
            })
        }
    }.toString()

    fun decodeAnnotations(raw: String?): List<PageAnnotation> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PageAnnotation(
                        id = item.optString("id"),
                        type = runCatching { AnnotationType.valueOf(item.optString("type")) }
                            .getOrDefault(AnnotationType.TEXT),
                        x = item.optDouble("x", 0.1).toFloat(),
                        y = item.optDouble("y", 0.1).toFloat(),
                        width = item.optDouble("w", 0.3).toFloat(),
                        height = item.optDouble("h", 0.1).toFloat(),
                        rotationDegrees = item.optDouble("rotation", 0.0).toFloat(),
                        text = item.optString("text"),
                        assetPath = item.optString("asset"),
                        opacity = item.optDouble("opacity", 1.0).toFloat()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun point(point: NormalizedPoint) = JSONObject().apply {
        put("x", point.x.toDouble())
        put("y", point.y.toDouble())
    }

    private fun decodePoint(json: JSONObject) = NormalizedPoint(
        x = json.optDouble("x", 0.0).toFloat(),
        y = json.optDouble("y", 0.0).toFloat()
    ).clamped()
}
