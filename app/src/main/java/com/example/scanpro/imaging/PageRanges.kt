package com.example.scanpro.imaging

import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.core.text.UserFacingPluralException

/**
 * Parses "1-3, 5, 8-10" into page ranges. Every entry must be a whole page number inside the document;
 * nothing is clamped or dropped quietly, so a typo surfaces as an error the user can read.
 */
object PageRanges {
    fun parse(text: String, maxPages: Int): Result<List<IntRange>> {
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.failure(UserFacingException(R.string.err_page_range_empty))
        val ranges = mutableListOf<IntRange>()
        for (part in parts) {
            val bounds = part.split('-', '–').map { it.trim() }
            val range = when (bounds.size) {
                1 -> bounds[0].toIntOrNull()?.let { it..it }
                2 -> {
                    val start = bounds[0].toIntOrNull()
                    val end = bounds[1].toIntOrNull()
                    if (start == null || end == null) null
                    else if (start > end) return Result.failure(UserFacingException(R.string.err_page_range_backwards, part, end, start))
                    else start..end
                }
                else -> null
            } ?: return Result.failure(UserFacingException(R.string.err_page_range_invalid, part))
            if (range.first < 1) return Result.failure(UserFacingException(R.string.err_page_range_starts_at_one))
            if (range.last > maxPages) {
                return Result.failure(UserFacingPluralException(R.plurals.err_page_range_past_end, maxPages, maxPages, part))
            }
            ranges += range
        }
        return Result.success(ranges)
    }
}
