package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test
    fun `newest first, no repeats, capped`() {
        var list = emptyList<String>()
        list = RecentSearches.add(list, "invoice")
        list = RecentSearches.add(list, " Receipt ")
        list = RecentSearches.add(list, "INVOICE")
        assertEquals(listOf("INVOICE", "Receipt"), list)
        (1..10).forEach { list = RecentSearches.add(list, "term $it") }
        assertEquals(RecentSearches.LIMIT, list.size)
        assertEquals("term 10", list.first())
    }

    @Test
    fun `one letter and blanks are not kept`() {
        assertEquals(emptyList<String>(), RecentSearches.add(emptyList(), " a "))
        assertEquals(emptyList<String>(), RecentSearches.add(emptyList(), "   "))
    }
}
