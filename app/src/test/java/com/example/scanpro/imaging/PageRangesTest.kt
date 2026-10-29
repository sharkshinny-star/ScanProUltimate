package com.example.scanpro.imaging

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
        assertTrue(result.exceptionOrNull()!!.message!!.contains("5 pages"))
    }

    @Test
    fun `rejects reversed ranges and junk`() {
        assertTrue(PageRanges.parse("5-2", 10).isFailure)
        assertTrue(PageRanges.parse("abc", 10).isFailure)
        assertTrue(PageRanges.parse("", 10).isFailure)
        assertTrue(PageRanges.parse("0", 10).isFailure)
    }
}
