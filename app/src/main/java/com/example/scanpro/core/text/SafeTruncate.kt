package com.example.scanpro.core.text

/**
 * The first [max] UTF-16 units of the string, but never cutting a character in two: a character outside the basic plane
 * (an emoji, some CJK and Indic extension characters) is two units, and `take` at the wrong place leaves half of it,
 * which shows as a replacement mark. When the cut would land between the two halves, the whole character is dropped.
 */
fun String.takeWhole(max: Int): String {
    if (max <= 0) return ""
    if (length <= max) return this
    val end = if (Character.isHighSurrogate(this[max - 1]) && Character.isLowSurrogate(this[max])) max - 1 else max
    return substring(0, end)
}
