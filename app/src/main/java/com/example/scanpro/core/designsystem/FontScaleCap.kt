package com.example.scanpro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times

/**
 * This style with its text grown at most [maxScale] times its nominal size, however large the phone's font size is.
 *
 * Only for short labels in fixed-size chrome (the bottom bar, stat tiles, pill buttons), where a 150 % or 200 % font
 * size otherwise cuts words in half or off the edge. Body text and anything that can wrap freely keeps the full scale.
 */
@Composable
fun TextStyle.cappedFontScale(maxScale: Float = 1.3f): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= maxScale) return this
    val factor = maxScale / scale
    return copy(
        fontSize = if (fontSize.isSpecified) factor * fontSize else fontSize,
        lineHeight = if (lineHeight.isSpecified) factor * lineHeight else lineHeight
    )
}
