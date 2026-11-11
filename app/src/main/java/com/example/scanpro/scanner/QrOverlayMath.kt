package com.example.scanpro.scanner

/** A box in view pixels. Kept free of Android types so the mapping can be unit tested on the JVM. */
internal data class ViewBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Where [box], a code's box in an upright [imageWidth] x [imageHeight] camera image (ML Kit's coordinates after it has
 * rotated the frame), lands in a [viewWidth] x [viewHeight] preview that shows the whole image centred with its aspect
 * ratio kept (PreviewView's FIT_CENTER). Null when any size is zero or negative.
 */
internal fun mapToFitCenter(box: IntBox, imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): ViewBox? {
    if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) return null
    val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
    val offsetX = (viewWidth - imageWidth * scale) / 2f
    val offsetY = (viewHeight - imageHeight * scale) / 2f
    return ViewBox(
        left = offsetX + box.left * scale,
        top = offsetY + box.top * scale,
        right = offsetX + box.right * scale,
        bottom = offsetY + box.bottom * scale
    )
}

/**
 * [box] grown around its centre to at least [minSize] in each direction (a touch target), then moved back inside the
 * [viewWidth] x [viewHeight] view if it sticks out; a view smaller than [minSize] just gets the whole view.
 */
internal fun withMinimumTouchSize(box: ViewBox, minSize: Float, viewWidth: Float, viewHeight: Float): ViewBox {
    val width = maxOf(box.width, minSize).coerceAtMost(viewWidth)
    val height = maxOf(box.height, minSize).coerceAtMost(viewHeight)
    val centreX = (box.left + box.right) / 2f
    val centreY = (box.top + box.bottom) / 2f
    val left = (centreX - width / 2f).coerceIn(0f, viewWidth - width)
    val top = (centreY - height / 2f).coerceIn(0f, viewHeight - height)
    return ViewBox(left, top, left + width, top + height)
}
