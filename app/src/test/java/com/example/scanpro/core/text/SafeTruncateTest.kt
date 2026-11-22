package com.example.scanpro.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cutting a title to its length limit must not leave half of a two-unit character. */
class SafeTruncateTest {
    private val smile = "😀" // one character, two UTF-16 units

    @Test
    fun aShortStringIsReturnedAsItIs() {
        assertEquals("Invoice", "Invoice".takeWhole(120))
        assertEquals("", "Invoice".takeWhole(0))
    }

    @Test
    fun aPlainCutIsLikeTake() {
        assertEquals("Invoi", "Invoice".takeWhole(5))
    }

    @Test
    fun aCutBetweenTheTwoHalvesDropsTheWholeCharacter() {
        val text = "ab" + smile + "cd"
        // "take(3)" would end on the first half of the smile.
        assertEquals("ab", text.takeWhole(3))
        // A cut right after the smile keeps it.
        assertEquals("ab" + smile, text.takeWhole(4))
        assertEquals("abc", "abcd".takeWhole(3))
    }
}
