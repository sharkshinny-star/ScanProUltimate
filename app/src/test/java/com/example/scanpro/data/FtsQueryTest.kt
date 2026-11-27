package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The library search turns a typed word into an FTS4 prefix query over the pages' text. */
class FtsQueryTest {
    @Test
    fun plainWordsBecomePrefixQueriesOnTheContentColumn() {
        assertEquals("content:invoice*", buildFtsQuery("Invoice"))
        assertEquals("content:2024*", buildFtsQuery("2024"))
    }

    @Test
    fun punctuationSplitsAndSingleCharactersAreDropped() {
        assertEquals("content:don*", buildFtsQuery("don't"))
        assertNull(buildFtsQuery("a"))
        assertNull(buildFtsQuery("!!"))
        assertNull(buildFtsQuery("   "))
    }

    @Test
    fun vowelSignsStayInsideTheWord() {
        // Devanagari "hindi": consonants plus vowel signs and an anusvara, which are marks, not letters. Cut at the
        // marks it was three one-character pieces, all dropped, so nothing could be found.
        assertEquals("content:हिंदी*", buildFtsQuery("हिंदी"))
        // Tamil "tamil", with a vowel sign and a virama, stays whole too.
        assertEquals("content:தமிழ்*", buildFtsQuery("தமிழ்"))
    }

    @Test
    fun atMostEightTermsAreKept() {
        val query = buildFtsQuery("aa bb cc dd ee ff gg hh ii jj")!!
        assertEquals(8, query.split(" ").size)
    }
}
