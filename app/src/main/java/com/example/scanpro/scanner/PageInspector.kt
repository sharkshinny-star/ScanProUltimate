package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * One decode and one detection per captured photo. The outline (for the crop) and the quality notes (blurred, glare,
 * dark, blank) both come from the same detector pass, but the camera used to ask for them separately: each new page
 * was decoded and searched twice in the background, and a third time if Done was tapped before either landed. Every
 * caller now shares one run per file; a failed run is remembered as null so a broken photo is not retried in a loop.
 */
internal class PageInspector(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val inspect: (File) -> Pair<CropQuad, Set<PageIssue>> = OpenCvScanProcessor::inspect,
) {
    private val runs = ConcurrentHashMap<String, Deferred<Pair<CropQuad, Set<PageIssue>>?>>()

    /** The page outline and quality notes of [file], or null when the photo could not be read. */
    suspend fun of(file: File): Pair<CropQuad, Set<PageIssue>>? =
        runs.computeIfAbsent(file.absolutePath) {
            scope.async(dispatcher) { runCatching { inspect(file) }.getOrNull() }
        }.await()

    /** Forgets pages that left the session (deleted or retaken), so their results are not kept for its whole life. */
    fun retain(paths: Set<String>) {
        runs.keys.retainAll(paths)
    }
}
