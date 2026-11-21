package com.example.scanpro.scanner

/** An integer box, in pixels. Kept free of Android types so the rotation maths can be unit tested. */
internal data class IntBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Where [box], given in an unrotated [bufferWidth] x [bufferHeight] image, ends up after the image is rotated
 * clockwise by [degrees] (0, 90, 180 or 270), the same turn as OpenCV's ROTATE_90_CLOCKWISE and friends.
 */
internal fun rotateBox(box: IntBox, bufferWidth: Int, bufferHeight: Int, degrees: Int): IntBox = when (((degrees % 360) + 360) % 360) {
    90 -> IntBox(bufferHeight - box.bottom, box.left, bufferHeight - box.top, box.right)
    180 -> IntBox(bufferWidth - box.right, bufferHeight - box.bottom, bufferWidth - box.left, bufferHeight - box.top)
    270 -> IntBox(box.top, bufferWidth - box.right, box.bottom, bufferWidth - box.left)
    else -> box
}
