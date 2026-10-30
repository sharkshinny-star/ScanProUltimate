package com.example.scanpro.core.crash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun files(keep: Int = 5) = CrashReportFiles(File(tmp.root, "crash-reports"), keep)

    @Test
    fun `a report holds the device header and the stack trace`() {
        val file = files().write("ScanPro 1.0\nAndroid 14\n\n", IllegalStateException("boom"), "20261030-120000")
        val text = file.readText()
        assertTrue(text.startsWith("ScanPro 1.0\nAndroid 14\n\n"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("CrashReporterTest"))
        assertEquals("crash-20261030-120000.txt", file.name)
    }

    @Test
    fun `only the newest reports are kept`() {
        val store = files(keep = 5)
        (1..7).forEach { store.write("", RuntimeException("crash $it"), "2026103$it-000000") }
        val remaining = File(tmp.root, "crash-reports").list()!!.sorted()
        assertEquals(5, remaining.size)
        assertEquals("crash-20261033-000000.txt", remaining.first())
        assertEquals("crash-20261037-000000.txt", store.newest()!!.name)
    }

    @Test
    fun `the newest report is pending until it has been offered`() {
        val store = files()
        assertNull("no reports, nothing to offer", store.pending(null))

        val first = store.write("", RuntimeException("first"), "20261030-100000")
        assertEquals(first.name, store.pending(null)!!.name)
        assertNull("offered once, not again", store.pending(first.name))

        val second = store.write("", RuntimeException("second"), "20261030-110000")
        assertEquals("a newer crash is offered even after the older one was", second.name, store.pending(first.name)!!.name)
    }

    @Test
    fun `the handler records the crash and then hands it on`() {
        val events = mutableListOf<String>()
        val previous = Thread.UncaughtExceptionHandler { _, error -> events += "previous:${error.message}" }
        chainedCrashHandler(previous) { _, error -> events += "recorded:${error.message}" }
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        assertEquals(listOf("recorded:boom", "previous:boom"), events)
    }

    @Test
    fun `a failing recorder never stops the crash reaching the previous handler`() {
        var handedOn = false
        val previous = Thread.UncaughtExceptionHandler { _, _ -> handedOn = true }
        chainedCrashHandler(previous) { _, _ -> throw java.io.IOException("disk full") }
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        assertTrue(handedOn)
    }

    @Test
    fun `with no previous handler the crash is just recorded`() {
        var recorded = false
        chainedCrashHandler(null) { _, _ -> recorded = true }.uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        assertTrue(recorded)
    }
}
