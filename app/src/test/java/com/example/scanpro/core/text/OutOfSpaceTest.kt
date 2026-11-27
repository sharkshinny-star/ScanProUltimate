package com.example.scanpro.core.text

import com.example.scanpro.R
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A full phone is told plainly in tools and background jobs, not with the system's English line. */
class OutOfSpaceTest {
    private val full = IOException("write failed: ENOSPC (No space left on device)")

    @Test
    fun theSystemsFullDiskErrorIsRecognisedAnywhereInTheChain() {
        assertTrue(isOutOfSpace(full))
        assertTrue(isOutOfSpace(IOException("No space left on device")))
        assertTrue(isOutOfSpace(RuntimeException("could not save", IOException("copy failed", full))))
    }

    @Test
    fun otherErrorsAreNotMistakenForIt() {
        assertFalse(isOutOfSpace(IOException("Backup is damaged")))
        assertFalse(isOutOfSpace(IllegalStateException("Index 3 out of bounds for length 3")))
        assertFalse(isOutOfSpace(IllegalStateException()))
    }

    @Test
    fun theMessageIsThePlainOneOnlyForAFullPhone() {
        assertEquals(R.string.err_no_space, outOfSpaceMessageRes(full))
        assertNull(outOfSpaceMessageRes(IOException("Backup is damaged")))
    }
}
