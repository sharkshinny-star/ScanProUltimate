package com.example.scanpro.imaging

import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingPluralException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {
    @Test
    fun `parses pages and ranges`() {
        assertEquals(listOf(1..3, 5..5, 8..10), PageRanges.parse("1-3, 5; 8-10", 10).getOrThrow())
    }

    @Test
    fun `rejects pages past the end instead of clamping`() {
        val result = PageRanges.parse("10-20", 5)
        assertTrue(result.isFailure)
        // The message is a translated resource; check which one, and that it names the page count and the entry.
        val error = result.exceptionOrNull() as UserFacingPluralException
        assertEquals(R.plurals.err_page_range_past_end, error.pluralsRes)
        assertEquals(5, error.quantity)
        assertEquals(listOf<Any>(5, "10-20"), error.args.toList())
    }

    @Test
    fun `rejects reversed ranges and junk`() {
        assertTrue(PageRanges.parse("5-2", 10).isFailure)
        assertTrue(PageRanges.parse("abc", 10).isFailure)
        assertTrue(PageRanges.parse("", 10).isFailure)
        assertTrue(PageRanges.parse("0", 10).isFailure)
    }
}
