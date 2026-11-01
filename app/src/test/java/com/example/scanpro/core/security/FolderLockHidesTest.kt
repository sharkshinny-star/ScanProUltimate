package com.example.scanpro.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLockHidesTest {
    @Test
    fun `names match ignoring case and spaces`() {
        assertTrue(FolderLock.hides(setOf(FolderLock.key("Bank ")), "bank"))
        assertFalse(FolderLock.hides(setOf("bank"), "Work"))
    }

    @Test
    fun `documents outside any folder are never hidden`() {
        assertFalse(FolderLock.hides(setOf("bank"), ""))
    }
}
