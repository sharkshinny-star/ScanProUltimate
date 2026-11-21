package com.example.scanpro.feature.tools

import com.example.scanpro.imaging.CropFraction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What a finger on the crop frame takes hold of: a corner, a side, or the whole frame to move it. */
internal enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

/**
 * The handle a touch at ([x], [y]) grabs, all in fractions of the page; [reachX] and [reachY] are how far a fingertip
 * reaches, as fractions too. Corners win over sides, sides over moving; null outside the frame, so the touch can scroll
 * the screen instead. Pure Kotlin, unit tested.
 */
internal fun hitCropHandle(crop: CropFraction, x: Float, y: Float, reachX: Float, reachY: Float): CropHandle? {
    val nearLeft = abs(x - crop.left) <= reachX
    val nearRight = abs(x - crop.right) <= reachX
    val nearTop = abs(y - crop.top) <= reachY
    val nearBottom = abs(y - crop.bottom) <= reachY
    val alongX = x >= crop.left - reachX && x <= crop.right + reachX
    val alongY = y >= crop.top - reachY && y <= crop.bottom + reachY
    // On a frame narrower than two fingertips both sides are "near": the closer one is meant.
    val left = nearLeft && (!nearRight || abs(x - crop.left) <= abs(x - crop.right))
    val right = nearRight && !left
    val top = nearTop && (!nearBottom || abs(y - crop.top) <= abs(y - crop.bottom))
    val bottom = nearBottom && !top
    return when {
        top && left -> CropHandle.TOP_LEFT
        top && right -> CropHandle.TOP_RIGHT
        bottom && left -> CropHandle.BOTTOM_LEFT
        bottom && right -> CropHandle.BOTTOM_RIGHT
        top && alongX -> CropHandle.TOP
        bottom && alongX -> CropHandle.BOTTOM
        left && alongY -> CropHandle.LEFT
        right && alongY -> CropHandle.RIGHT
        x in crop.left..crop.right && y in crop.top..crop.bottom -> CropHandle.MOVE
        else -> null
    }
}

/**
 * [crop] after [handle] is dragged by ([dx], [dy]) (fractions of the page). It stays on the page, and never narrower
 * than [minWidth] or shorter than [minHeight]; moving keeps its size and stops at the page's edges.
 */
internal fun dragCrop(crop: CropFraction, handle: CropHandle, dx: Float, dy: Float, minWidth: Float, minHeight: Float): CropFraction {
    if (handle == CropHandle.MOVE) {
        val mx = dx.coerceIn(-crop.left, 1f - crop.right)
        val my = dy.coerceIn(-crop.top, 1f - crop.bottom)
        return CropFraction(crop.left + mx, crop.top + my, crop.right + mx, crop.bottom + my)
    }
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom
    if (handle == CropHandle.TOP_LEFT || handle == CropHandle.LEFT || handle == CropHandle.BOTTOM_LEFT) {
        l = (l + dx).coerceIn(0f, max(0f, r - minWidth))
    }
    if (handle == CropHandle.TOP_RIGHT || handle == CropHandle.RIGHT || handle == CropHandle.BOTTOM_RIGHT) {
        r = (r + dx).coerceIn(min(1f, l + minWidth), 1f)
    }
    if (handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP || handle == CropHandle.TOP_RIGHT) {
        t = (t + dy).coerceIn(0f, max(0f, b - minHeight))
    }
    if (handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM || handle == CropHandle.BOTTOM_RIGHT) {
        b = (b + dy).coerceIn(min(1f, t + minHeight), 1f)
    }
    return CropFraction(l, t, r, b)
}
