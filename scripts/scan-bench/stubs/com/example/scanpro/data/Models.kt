package com.example.scanpro.data
data class NormalizedPoint(val x: Float, val y: Float)
data class CropQuad(val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f), val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f), val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f)) { companion object { val DEFAULT = CropQuad() } }
