package com.example.scanpro.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How many steps a document can go back and forward. */
data class UndoState(val undoSteps: Int = 0, val redoSteps: Int = 0) {
    val canUndo: Boolean get() = undoSteps > 0
    val canRedo: Boolean get() = redoSteps > 0
}

/**
 * Undo and redo for page edits, kept in memory for this session. Each step is the document's pages as they were
 * (edit settings, mark-up and text, not pixels): a page can always be drawn again from its original photo, so
 * going back only re-renders the pages that differ.
 */
class EditHistory(
    private val limit: Int = 20,
    /** Rough memory a document's steps may hold; the oldest steps go first, so a long scan can't pile up megabytes. */
    private val maxBytes: Long = 6L * 1024 * 1024
) {
    private val undo = HashMap<String, ArrayDeque<List<ScanPage>>>()
    private val redo = HashMap<String, ArrayDeque<List<ScanPage>>>()
    private val _state = MutableStateFlow<Map<String, UndoState>>(emptyMap())
    val state: StateFlow<Map<String, UndoState>> = _state.asStateFlow()

    /** Remembers [before] as the step to return to; a new edit also ends any redo trail. */
    @Synchronized
    fun record(documentId: String, before: List<ScanPage>) {
        val stack = undo.getOrPut(documentId) { ArrayDeque() }
        if (stack.lastOrNull() == before) return
        // A step is a fresh copy of the document's pages. Pages that did not change are shared with the newest
        // step, so ten edits to one page of a 100-page scan keep one copy of the other 99, not ten.
        val previous = stack.lastOrNull()?.associateBy { it.id }.orEmpty()
        stack.addLast(before.map { page -> previous[page.id]?.takeIf { it == page } ?: page })
        while (stack.size > limit) stack.removeFirst()
        // Always keep the newest step, however large, so the last edit can still be undone.
        while (stack.size > 1 && retainedBytes(documentId) > maxBytes) stack.removeFirst()
        redo.remove(documentId)
        publish(documentId)
    }

    /** The pages to go back to, given the [current] ones (which become the redo step); null if none. */
    @Synchronized
    fun undo(documentId: String, current: List<ScanPage>): List<ScanPage>? {
        val previous = undo[documentId]?.removeLastOrNull() ?: return null
        redo.getOrPut(documentId) { ArrayDeque() }.addLast(current)
        publish(documentId)
        return previous
    }

    @Synchronized
    fun redo(documentId: String, current: List<ScanPage>): List<ScanPage>? {
        val next = redo[documentId]?.removeLastOrNull() ?: return null
        undo.getOrPut(documentId) { ArrayDeque() }.addLast(current)
        publish(documentId)
        return next
    }

    /** Puts a step taken by [undo] or [redo] back when restoring it failed, so nothing is lost. */
    @Synchronized
    fun rollback(documentId: String, wasUndo: Boolean, taken: List<ScanPage>) {
        if (wasUndo) {
            redo[documentId]?.removeLastOrNull()
            undo.getOrPut(documentId) { ArrayDeque() }.addLast(taken)
        } else {
            undo[documentId]?.removeLastOrNull()
            redo.getOrPut(documentId) { ArrayDeque() }.addLast(taken)
        }
        publish(documentId)
    }

    /** Forgets a document's steps, for example after pages were added or deleted. */
    @Synchronized
    fun clear(documentId: String) {
        undo.remove(documentId)
        redo.remove(documentId)
        publish(documentId)
    }

    /** Every page state still reachable by undo or redo, so files they use are not deleted. */
    @Synchronized
    fun retained(documentId: String): List<ScanPage> =
        (undo[documentId].orEmpty() + redo[documentId].orEmpty()).flatten()

    /** Estimated size of every distinct page kept for [documentId]; a page shared by several steps counts once. */
    private fun retainedBytes(documentId: String): Long {
        val seen = java.util.IdentityHashMap<ScanPage, Boolean>()
        var total = 0L
        for (step in undo[documentId].orEmpty() + redo[documentId].orEmpty()) {
            for (page in step) if (seen.put(page, true) == null) total += estimateBytes(page)
        }
        return total
    }

    private fun estimateBytes(page: ScanPage): Long =
        400L + page.ocrText.length * 2L + page.ocrWords.sumOf { 80L + it.text.length * 2L } +
            page.annotations.sumOf { 200L + it.text.length * 2L + it.strokes.sumOf { stroke -> stroke.size * 16L } }

    private fun publish(documentId: String) {
        val value = UndoState(undo[documentId]?.size ?: 0, redo[documentId]?.size ?: 0)
        _state.value = if (value.undoSteps == 0 && value.redoSteps == 0) _state.value - documentId
        else _state.value + (documentId to value)
    }
}
