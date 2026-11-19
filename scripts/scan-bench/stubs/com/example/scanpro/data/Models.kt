package com.example.scanpro.data
data class NormalizedPoint(val x: Float, val y: Float)
data class CropQuad(val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f), val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f), val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f)) {
    fun straightened() = this
    fun isIdentity(epsilon: Float = 0.002f) = this == DEFAULT
    companion object { val DEFAULT = CropQuad() } }

enum class PageFilter { ORIGINAL, AUTO, COLOR, GRAY, BW, WHITEBOARD }

data class PageAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val removeShadows: Boolean = false,
    val removeFingers: Boolean = false
) {
    fun normalized() = copy(
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0.6f, 1.6f),
        saturation = saturation.coerceIn(0f, 2f)
    )
    fun isTonallyNeutral(epsilon: Float = 0.001f): Boolean =
        kotlin.math.abs(brightness) < epsilon &&
            kotlin.math.abs(contrast - 1f) < epsilon &&
            kotlin.math.abs(saturation - 1f) < epsilon
    fun isNeutral(epsilon: Float = 0.001f): Boolean = isTonallyNeutral(epsilon) && !removeShadows && !removeFingers
    companion object { val DEFAULT = PageAdjustments() }
}
