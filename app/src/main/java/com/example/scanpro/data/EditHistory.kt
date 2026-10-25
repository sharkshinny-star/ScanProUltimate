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
class EditHistory(private val limit: Int = 20) {
    private val undo = HashMap<String, ArrayDeque<List<ScanPage>>>()
    private val redo = HashMap<String, ArrayDeque<List<ScanPage>>>()
    private val _state = MutableStateFlow<Map<String, UndoState>>(emptyMap())
    val state: StateFlow<Map<String, UndoState>> = _state.asStateFlow()

    /** Remembers [before] as the step to return to; a new edit also ends any redo trail. */
    @Synchronized
    fun record(documentId: String, before: List<ScanPage>) {
        val stack = undo.getOrPut(documentId) { ArrayDeque() }
        if (stack.lastOrNull() == before) return
        stack.addLast(before)
        while (stack.size > limit) stack.removeFirst()
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

    private fun publish(documentId: String) {
        val value = UndoState(undo[documentId]?.size ?: 0, redo[documentId]?.size ?: 0)
        _state.value = if (value.undoSteps == 0 && value.redoSteps == 0) _state.value - documentId
        else _state.value + (documentId to value)
    }
}
