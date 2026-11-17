package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PageInspectorTest {
    private val found = CropQuad.DEFAULT to setOf(PageIssue.BLURRY)

    @Test
    fun `crop and quality asked together cost one detection`() = runTest {
        val runs = AtomicInteger()
        val inspector = PageInspector(backgroundScope, StandardTestDispatcher(testScheduler)) { runs.incrementAndGet(); found }
        val page = File("/session/raw_1.jpg")

        val answers = listOf(async { inspector.of(page) }, async { inspector.of(page) }, async { inspector.of(page) }).awaitAll()

        assertEquals(listOf(found, found, found), answers)
        assertEquals(1, runs.get())
        // Asking again later (Done, then Save) reuses the answer.
        assertEquals(found, inspector.of(page))
        assertEquals(1, runs.get())
    }

    @Test
    fun `each page is inspected on its own`() = runTest {
        val seen = mutableListOf<String>()
        val inspector = PageInspector(backgroundScope, StandardTestDispatcher(testScheduler)) { seen += it.name; found }

        inspector.of(File("/session/raw_1.jpg"))
        inspector.of(File("/session/raw_2.jpg"))

        assertEquals(listOf("raw_1.jpg", "raw_2.jpg"), seen)
    }

    @Test
    fun `an unreadable photo gives null once and is not retried`() = runTest {
        val runs = AtomicInteger()
        val inspector = PageInspector(backgroundScope, StandardTestDispatcher(testScheduler)) { runs.incrementAndGet(); error("corrupt") }
        val page = File("/session/raw_1.jpg")

        assertNull(inspector.of(page))
        assertNull(inspector.of(page))
        assertEquals(1, runs.get())
    }

    @Test
    fun `a page that left the session is forgotten`() = runTest {
        val runs = AtomicInteger()
        val inspector = PageInspector(backgroundScope, StandardTestDispatcher(testScheduler)) { runs.incrementAndGet(); found }
        val kept = File("/session/raw_1.jpg")
        val gone = File("/session/raw_2.jpg")
        inspector.of(kept)
        inspector.of(gone)

        inspector.retain(setOf(kept.absolutePath))
        inspector.of(kept)
        inspector.of(gone)

        assertEquals(3, runs.get())
    }
}
