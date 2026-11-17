package com.example.scanpro.core.concurrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BackgroundWorkTest {
    @Test
    fun workRunsOnLowPriorityDaemonThreads() = runBlocking {
        val (priority, daemon, name) = withContext(BackgroundWork.tools) {
            Triple(Thread.currentThread().priority, Thread.currentThread().isDaemon, Thread.currentThread().name)
        }
        assertTrue("below the UI thread's normal priority", priority < Thread.NORM_PRIORITY)
        assertTrue(daemon)
        assertTrue(name, name.startsWith("scanpro-tools"))
    }

    @Test
    fun thumbnailsUseNoMoreThanTwoThreadsHoweverManyAreAsked() = runBlocking {
        val threads = ConcurrentHashMap.newKeySet<Long>()
        List(40) {
            async(BackgroundWork.thumbnails) {
                threads += Thread.currentThread().id
                Thread.sleep(2)
            }
        }.awaitAll()
        assertTrue("threads used: $threads", threads.size <= 2)
    }

    @Test
    fun aQueuedJobWhoseScreenHasGoneIsDroppedBeforeItStarts() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val started = AtomicInteger()
        // Two long jobs occupy both thumbnail threads.
        val busy = List(2) {
            async(BackgroundWork.thumbnails) { started.incrementAndGet(); while (!release.isCompleted) Thread.sleep(1) }
        }
        while (started.get() < 2) Thread.sleep(1)
        val ran = AtomicInteger()
        val waiting = async(BackgroundWork.thumbnails, start = CoroutineStart.DEFAULT) { ran.incrementAndGet() }
        // Cancelled while both threads are busy; only once they are free can the cancelled job be taken off the queue.
        waiting.cancel()
        release.complete(Unit)
        busy.awaitAll()
        waiting.join()
        assertTrue(waiting.isCancelled)
        assertEquals(0, ran.get())
    }
}
