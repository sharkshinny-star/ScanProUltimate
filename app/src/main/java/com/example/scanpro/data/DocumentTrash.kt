package com.example.scanpro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The recycle bin: moving documents in and out, deleting for good and the retention clean-up (R9). */
internal class DocumentTrash(private val store: DocumentStore) {
    private val db get() = store.db
    private val settings get() = store.settings
    private suspend fun mutateDocument(id: String, block: suspend (ScanDocument) -> ScanDocument) = store.mutateDocument(id, block)
    private fun deleteDocumentFilesAndRecord(doc: ScanDocument) = store.deleteDocumentFilesAndRecord(doc)

    /** How many recycle-bin documents are deleted for good within the next [withinMs], under the retention setting. */
    suspend fun trashExpiringWithin(withinMs: Long): Int = withContext(Dispatchers.IO) {
        val retentionMs = settings.trashRetentionDays.first().coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val now = now()
        db.loadTrashSummaries().count { it.trashedAt > 0 && it.trashedAt + retentionMs - now in 1..withinMs }
    }

    suspend fun loadTrashSummaries(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        purgeExpiredTrash()
        db.loadTrashSummaries()
    }

    suspend fun moveToTrash(id: String) = mutateDocument(id) { doc ->
        doc.copy(isTrashed = true, trashedAt = now(), updatedAt = now())
    }

    /** Serialises restoring with purging (purge also runs from the library refresh and a worker). */
    private val trashLock = Any()

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        // Under the trash lock, so a purge that is running can't delete the document just after it was restored.
        synchronized(trashLock) {
            val doc = store.requireDocument(id)
            db.saveDocumentMetadata(doc.copy(isTrashed = false, trashedAt = 0L, updatedAt = now()))
        }
    }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        val doc = db.loadDocument(id) ?: return@withContext
        deleteDocumentFilesAndRecord(doc)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        db.loadTrash().forEach { deleteDocumentFilesAndRecord(it) }
    }

    /** Deletes recycle-bin documents older than the user's retention setting. */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val maxAgeDays = settings.trashRetentionDays.first()
        val cutoff = now() - maxAgeDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        // The trash is read inside the lock, so a document restored a moment ago is no longer in it.
        synchronized(trashLock) {
            val expired = db.loadTrash().filter { it.trashedAt in 1 until cutoff }
            expired.forEach { deleteDocumentFilesAndRecord(it) }
            expired.size
        }
    }
}
