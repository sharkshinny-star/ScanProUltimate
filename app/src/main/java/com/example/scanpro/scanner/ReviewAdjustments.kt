package com.example.scanpro.scanner

import com.example.scanpro.data.PageAdjustments
import kotlin.math.roundToInt

/** Each review slider runs from [REVIEW_SLIDER_MIN] to [REVIEW_SLIDER_MAX] with 0 as "unchanged". */
const val REVIEW_SLIDER_MIN = -50
const val REVIEW_SLIDER_MAX = 50

/**
 * Maps the review screen's −50..50 sliders onto [PageAdjustments]: brightness −1..1, contrast 0.6..1.4 (the
 * same step either side of neutral, within the 0.6..1.6 range) and saturation 0..2, so 0 on every slider is the
 * neutral page. Values outside the slider range are clamped.
 */
fun reviewAdjustments(bri: Int, con: Int, sat: Int): PageAdjustments {
    val b = bri.coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX)
    val c = con.coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX)
    val s = sat.coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX)
    return PageAdjustments(
        brightness = b / 50f,
        contrast = 1f + c / 125f,
        saturation = 1f + s / 50f
    ).normalized()
}

/** The slider positions that produce [adjustments]: the inverse of [reviewAdjustments], rounded to whole steps. */
fun reviewSliders(adjustments: PageAdjustments): Triple<Int, Int, Int> = Triple(
    (adjustments.brightness * 50f).roundToInt().coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX),
    ((adjustments.contrast - 1f) * 125f).roundToInt().coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX),
    ((adjustments.saturation - 1f) * 50f).roundToInt().coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX)
)
