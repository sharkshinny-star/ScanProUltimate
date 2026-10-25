package com.example.scanpro.data

import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
    fun encodeWords(words: List<OcrWord>): String = JSONArray().apply {
        words.forEach { w ->
            put(JSONArray().put(w.text).put(w.left.toDouble()).put(w.top.toDouble()).put(w.right.toDouble()).put(w.bottom.toDouble()))
        }
    }.toString()

    fun decodeWords(raw: String?): List<OcrWord> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONArray(index)
                add(OcrWord(item.getString(0), item.getDouble(1).toFloat(), item.getDouble(2).toFloat(), item.getDouble(3).toFloat(), item.getDouble(4).toFloat()))
            }
        }
    }.getOrDefault(emptyList())

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
        crop.topBow?.let { put("topBow", vector(it)) }
        crop.bottomBow?.let { put("bottomBow", vector(it)) }
        crop.leftBow?.let { put("leftBow", vector(it)) }
        crop.rightBow?.let { put("rightBow", vector(it)) }
    }.toString()

    fun decodeCrop(raw: String?): CropQuad = runCatching {
        if (raw.isNullOrBlank()) return@runCatching CropQuad.DEFAULT
        val json = JSONObject(raw)
        CropQuad(
            topLeft = decodePoint(json.getJSONObject("tl")),
            topRight = decodePoint(json.getJSONObject("tr")),
            bottomRight = decodePoint(json.getJSONObject("br")),
            bottomLeft = decodePoint(json.getJSONObject("bl")),
            topBow = json.optJSONObject("topBow")?.let(::decodeVector),
            bottomBow = json.optJSONObject("bottomBow")?.let(::decodeVector),
            leftBow = json.optJSONObject("leftBow")?.let(::decodeVector),
            rightBow = json.optJSONObject("rightBow")?.let(::decodeVector)
        )
    }.getOrDefault(CropQuad.DEFAULT)

    fun encodeAdjustments(value: PageAdjustments): String = JSONObject().apply {
        put("brightness", value.brightness.toDouble())
        put("contrast", value.contrast.toDouble())
        put("saturation", value.saturation.toDouble())
        put("shadows", value.removeShadows)
        put("fingers", value.removeFingers)
    }.toString()

    fun decodeAdjustments(raw: String?): PageAdjustments = runCatching {
        if (raw.isNullOrBlank()) return@runCatching PageAdjustments.DEFAULT
        val json = JSONObject(raw)
        PageAdjustments(
            brightness = json.optDouble("brightness", 0.0).toFloat(),
            contrast = json.optDouble("contrast", 1.0).toFloat(),
            saturation = json.optDouble("saturation", 1.0).toFloat(),
            removeShadows = json.optBoolean("shadows", false),
            removeFingers = json.optBoolean("fingers", false)
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
                put("color", annotation.color)
            })
        }
    }.toString()

    fun decodeAnnotations(raw: String?): List<PageAnnotation> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // Decode per item so one corrupt entry cannot drop (and later overwrite) every annotation.
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
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
                    opacity = item.optDouble("opacity", 1.0).toFloat(),
                    color = item.optInt("color", PageAnnotation.DEFAULT_TEXT_COLOR)
                )
            }.getOrNull()
        }
    }

    private fun point(point: NormalizedPoint) = JSONObject().apply {
        put("x", point.x.toDouble())
        put("y", point.y.toDouble())
    }

    // Bows are offsets, not positions, so unlike points they may be negative; they are only kept within reason.
    private fun vector(point: NormalizedPoint) = point(point)

    private fun decodeVector(json: JSONObject) = NormalizedPoint(
        x = json.optDouble("x", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
        y = json.optDouble("y", 0.0).toFloat().coerceIn(-0.5f, 0.5f)
    )

    private fun decodePoint(json: JSONObject) = NormalizedPoint(
        x = json.optDouble("x", 0.0).toFloat(),
        y = json.optDouble("y", 0.0).toFloat()
    ).clamped()
}
