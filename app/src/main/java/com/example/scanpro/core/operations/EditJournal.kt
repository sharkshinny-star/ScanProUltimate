package com.example.scanpro.core.operations

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * A write-ahead record of document edits waiting in [OperationRunner]'s queue, so an edit the user asked for is not
 * lost when Android stops the app before its turn comes.
 *
 * One file per edit, named by a sequence number so they replay in the order they were asked for:
 * - `<seq>.json` is queued and has not started;
 * - `<seq>.started` has started. If the app stops now the edit may be half applied, so it is never replayed, only
 *   reported, and the user checks the document.
 *
 * The file is removed when the edit finishes, fails or is stopped. Only edit parameters are written: never passwords,
 * images or files from other apps (see [com.example.scanpro.feature.document.DocumentEdit]). Callers serialise every
 * call (one thread), which keeps the files in step with the queue.
 */
class EditJournal(private val dir: File) {
    private val sequence = AtomicLong(System.currentTimeMillis() * 1_000)

    /** Records a queued edit and returns its sequence number. */
    fun add(payload: JSONObject): Long {
        val seq = sequence.incrementAndGet()
        dir.mkdirs()
        val tmp = File(dir, "$seq.tmp")
        // Synced before the rename, so a power cut can't leave a record whose name exists but whose content doesn't.
        java.io.FileOutputStream(tmp).use { out ->
            out.write(payload.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(File(dir, "$seq$QUEUED"))) {
            tmp.delete()
            throw java.io.IOException("Could not record edit $seq")
        }
        return seq
    }

    /** The edit is about to run: from now on it is reported, not replayed, if the app stops. */
    fun markStarted(seq: Long) {
        File(dir, "$seq$QUEUED").renameTo(File(dir, "$seq$STARTED"))
    }

    /** The edit finished, failed or was stopped. */
    fun remove(seq: Long) {
        File(dir, "$seq$QUEUED").delete()
        File(dir, "$seq$STARTED").delete()
    }

    /**
     * What the previous run left behind, once per launch, before anything new is added: queued edits in order (still
     * in the journal under their own numbers, to be run again) and how many had started without finishing (removed).
     */
    fun recover(): Recovered {
        val files = dir.listFiles().orEmpty()
        val interrupted = files.filter { it.name.endsWith(STARTED) }
        interrupted.forEach { it.delete() }
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        val queued = files.filter { it.name.endsWith(QUEUED) }
            .mapNotNull { file ->
                val seq = file.name.removeSuffix(QUEUED).toLongOrNull() ?: return@mapNotNull null
                val payload = runCatching { JSONObject(file.readText()) }.getOrNull()
                if (payload == null) {
                    file.delete()
                    null
                } else {
                    seq to payload
                }
            }
            .sortedBy { it.first }
        // New edits must sort after the recovered ones even if the clock went back.
        queued.lastOrNull()?.first?.let { last -> sequence.updateAndGet { maxOf(it, last) } }
        return Recovered(queued, interrupted.size)
    }

    data class Recovered(val queued: List<Pair<Long, JSONObject>>, val interrupted: Int)

    private companion object {
        const val QUEUED = ".json"
        const val STARTED = ".started"
    }
}
