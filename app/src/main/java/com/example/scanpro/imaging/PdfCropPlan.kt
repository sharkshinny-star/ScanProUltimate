package com.example.scanpro.imaging

/**
 * A crop as fractions of the page as it is shown (top-left origin): (0, 0, 1, 1) keeps the whole page. Fractions, not
 * points, so one crop fits every page of a file whose pages differ in size. Pure Kotlin, unit tested.
 */
data class CropFraction(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isWhole: Boolean get() = left <= EPS && top <= EPS && right >= 1f - EPS && bottom >= 1f - EPS

    companion object {
        val WHOLE = CropFraction()
        private const val EPS = 0.002f
    }
}

/**
 * What Crop PDF pages keeps of each page when the crop is chosen by hand (F241): [all] for every page, except the
 * [pages] given a crop of their own. An own crop equal to [CropFraction.WHOLE] keeps that page whole while the others
 * are cropped. Pages are 0-based.
 */
data class CropPlan(val all: CropFraction = CropFraction.WHOLE, val pages: Map<Int, CropFraction> = emptyMap()) {
    fun forPage(index: Int): CropFraction = pages[index] ?: all
    fun hasOwn(index: Int): Boolean = index in pages
    /** How many of [pageCount] pages this plan changes. */
    fun croppedCount(pageCount: Int): Int = (0 until pageCount).count { !forPage(it).isWhole }
    val isEmpty: Boolean get() = all.isWhole && pages.values.all { it.isWhole }
    fun withAll(crop: CropFraction): CropPlan = copy(all = crop)
    fun withPage(index: Int, crop: CropFraction): CropPlan = copy(pages = pages + (index to crop))
    fun withoutPage(index: Int): CropPlan = copy(pages = pages - index)
    /** [all] on every page: the pages' own crops are dropped. */
    fun sameEverywhere(): CropPlan = CropPlan(all)
}
