package com.example.scanpro.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prompts waiting to be shown one at a time, oldest first; [head] is the one on screen. Several background jobs can
 * each ask (for instance three protected PDFs shared at once), and none is lost: a single slot kept only the last.
 * Offering an item that [sameAs] one already queued replaces it at the back.
 */
internal class PromptQueue<T>(private val sameAs: (T, T) -> Boolean) {
    private var queue: List<T> = emptyList()
    private val _head = MutableStateFlow<T?>(null)
    val head: StateFlow<T?> = _head.asStateFlow()

    fun offer(item: T) = change { q -> q.filterNot { sameAs(it, item) } + item }

    fun remove(match: (T) -> Boolean) = change { q -> q.filterNot(match) }

    /** The prompt on screen was dismissed: show the next one. */
    fun dropHead() = change { it.drop(1) }

    // Jobs finish on background threads, so every change and the head it leaves are one step.
    @Synchronized
    private fun change(block: (List<T>) -> List<T>) {
        queue = block(queue)
        _head.value = queue.firstOrNull()
    }
}
