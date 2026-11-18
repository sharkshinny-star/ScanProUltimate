package com.example.scanpro.imaging

import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import java.io.File

/**
 * The Share PDF card (R28): a size estimate per quality before sharing, a quality picked for a size target
 * ("keep under 1 MB" for upload forms), and a PDF shrunk until it fits. The maths is here, testable; the renders go
 * through [PdfExporter] so an estimate is made the same way as the file that is shared.
 */
object PdfShare {
    /** Size targets offered on the card, in bytes: decimal megabytes, which is how Android shows file sizes ("5 MB"). */
    val SIZE_TARGETS: List<Long> = listOf(1_000_000L, 5_000_000L, 10_000_000L)

    /** How many pages an estimate renders; the rest is extrapolated. */
    const val SAMPLE_PAGES = 2

    /** Longest sides tried, after the smallest quality, when a file still exceeds its size target. */
    val SHRINK_DIMENSIONS: List<Int> = listOf(1000, 800, 600)

    /** A render step: the quality it is labelled with and the longest side its page images are read at. */
    data class Step(val quality: PdfQuality, val maxDimension: Int)

    /** What [build] produced. */
    data class Built(val file: File, val step: Step, val bytes: Long)

    /** The size of [totalPages] pages when [samplePages] of them took [sampleBytes]; 0 when nothing was sampled. */
    fun extrapolate(sampleBytes: Long, samplePages: Int, totalPages: Int): Long =
        if (samplePages <= 0) 0L else sampleBytes * totalPages / samplePages

    /**
     * The quality to share at: the best one whose [estimates] fit under [maxBytes], or the smallest when none does;
     * [preferred] when there is no target. Qualities without an estimate are skipped.
     */
    fun pickQuality(estimates: Map<PdfQuality, Long>, maxBytes: Long?, preferred: PdfQuality): PdfQuality {
        if (maxBytes == null) return preferred
        return PdfQuality.entries.firstOrNull { q -> estimates[q]?.let { it <= maxBytes } == true } ?: PdfQuality.entries.last()
    }

    /**
     * Render steps for [start]: just the one when there is no target to meet; otherwise each lower quality in turn,
     * then the smallest quality at ever shorter sides, so a file that is still too big keeps getting smaller.
     */
    fun shrinkPlan(start: PdfQuality, shrink: Boolean): List<Step> {
        val first = Step(start, start.maxImageDimension)
        if (!shrink) return listOf(first)
        val lower = PdfQuality.entries.filter { it.ordinal > start.ordinal }.map { Step(it, it.maxImageDimension) }
        val smallest = PdfQuality.entries.last()
        return listOf(first) + lower + SHRINK_DIMENSIONS.filter { it < smallest.maxImageDimension }.map { Step(smallest, it) }
    }

    /** Estimated size of [pages] at [quality]: up to [SAMPLE_PAGES] of them really rendered to [scratch], then scaled up. */
    fun estimate(pages: List<ScanPage>, quality: PdfQuality, pageSize: PdfPageSize, searchable: Boolean, pageNumbers: Boolean, scratch: File): Long {
        if (pages.isEmpty()) return 0L
        val sample = pages.take(SAMPLE_PAGES)
        try {
            PdfExporter.export(sample, scratch, quality, pageSize, searchable, pageNumbers)
            return extrapolate(scratch.length(), sample.size, pages.size)
        } finally {
            scratch.delete()
        }
    }

    /**
     * Writes [pages] to [destination] at [quality]; with [maxBytes] set, re-renders along [shrinkPlan] until the file
     * fits or the plan runs out (the last, smallest attempt is then what is shared).
     */
    fun build(
        pages: List<ScanPage>, destination: File, quality: PdfQuality, pageSize: PdfPageSize,
        searchable: Boolean, pageNumbers: Boolean, maxBytes: Long?
    ): Built {
        var last: Built? = null
        for (step in shrinkPlan(quality, maxBytes != null)) {
            PdfExporter.export(
                pages, destination, step.quality, pageSize, searchable, pageNumbers,
                decode = { path, _ -> ImageProcessor.decodeScaled(path, step.maxDimension) }
            )
            last = Built(destination, step, destination.length())
            if (maxBytes == null || last.bytes <= maxBytes) return last
        }
        return last!!
    }
}
