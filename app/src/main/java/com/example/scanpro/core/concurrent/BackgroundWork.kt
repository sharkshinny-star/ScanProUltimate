package com.example.scanpro.core.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Where the app's heavy, non-urgent work runs. `Dispatchers.IO` hands out up to 64 threads at the same priority as the
 * thread that draws the screen, so a burst of thumbnails or a long merge competes with the frames for the processor.
 * These pools are small and run at a lower priority (Linux nice 13): when a core is free they run at full speed, and
 * when the UI thread needs the core they step aside. Idle threads end after half a minute.
 */
object BackgroundWork {
    /** Page and file thumbnails: two threads. A queued request whose screen has gone is dropped before it starts. */
    val thumbnails: CoroutineDispatcher by lazy { pool("scanpro-thumbs", threads = 2) }

    /** PDF and image tool jobs: all but one core (two to four threads), so one tool never takes every core. */
    val tools: CoroutineDispatcher by lazy {
        pool("scanpro-tools", threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4))
    }

    /** Java priority 3 is nice 13 on Android; the normal priority of the UI thread is 5 (nice 0). */
    private const val LOW_PRIORITY = 3

    private fun pool(name: String, threads: Int): CoroutineDispatcher {
        var count = 0
        val executor = ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
            Thread(task, "$name-${++count}").apply {
                isDaemon = true
                priority = LOW_PRIORITY
            }
        }
        executor.allowCoreThreadTimeOut(true)
        return executor.asCoroutineDispatcher()
    }
}
