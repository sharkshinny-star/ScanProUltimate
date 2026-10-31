package com.example.scanpro.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredMessageTest {
    @Test
    fun `plain text from older versions is shown as it is`() {
        assertEquals("Disk full", AppStrings.fromStorable("Disk full"))
    }

    @Test
    fun `a plain error is stored as its message`() {
        assertEquals("boom", AppStrings.storable(IllegalStateException("boom")))
    }

    @Test
    fun `a stored resource without resources available falls back to the text saved with it`() {
        val stored = """{"messageRes":"export_err_no_pages","kind":"string","fallback":"No pages","args":[]}"""
        assertEquals("No pages", AppStrings.fromStorable(stored))
    }
}
