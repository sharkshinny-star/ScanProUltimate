package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSafeNameTest {
    @Test
    fun `keeps titles in any script`() {
        assertEquals("बिल मार्च", fileSafeName("बिल मार्च"))
        assertEquals("発票 2024", fileSafeName("発票 2024"))
    }

    @Test
    fun `replaces only file-system-unsafe characters`() {
        assertEquals("Invoice_ 12_03_2024", fileSafeName("Invoice: 12/03/2024"))
        assertEquals("Document", fileSafeName("   "))
    }
}
