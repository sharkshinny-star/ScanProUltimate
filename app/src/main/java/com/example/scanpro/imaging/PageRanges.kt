package com.example.scanpro.imaging

/**
 * Parses "1-3, 5, 8-10" into page ranges. Every entry must be a whole page number inside the document;
 * nothing is clamped or dropped quietly, so a typo surfaces as an error the user can read.
 */
object PageRanges {
    fun parse(text: String, maxPages: Int): Result<List<IntRange>> {
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.failure(IllegalArgumentException("Enter at least one page or range, such as 1-3"))
        val ranges = mutableListOf<IntRange>()
        for (part in parts) {
            val bounds = part.split('-', '–').map { it.trim() }
            val range = when (bounds.size) {
                1 -> bounds[0].toIntOrNull()?.let { it..it }
                2 -> {
                    val start = bounds[0].toIntOrNull()
                    val end = bounds[1].toIntOrNull()
                    if (start == null || end == null) null
                    else if (start > end) return Result.failure(IllegalArgumentException("\"$part\" runs backwards; write it as $end-$start"))
                    else start..end
                }
                else -> null
            } ?: return Result.failure(IllegalArgumentException("\"$part\" is not a page number or a range like 2-4"))
            if (range.first < 1) return Result.failure(IllegalArgumentException("Pages start at 1"))
            if (range.last > maxPages) {
                return Result.failure(IllegalArgumentException("\"$part\" goes past the last page; this PDF has ${if (maxPages == 1) "1 page" else "$maxPages pages"}"))
            }
            ranges += range
        }
        return Result.success(ranges)
    }
}
