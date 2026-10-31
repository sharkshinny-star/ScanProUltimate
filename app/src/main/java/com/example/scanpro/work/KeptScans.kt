package com.example.scanpro.work

import android.content.Context
import androidx.work.Data
import androidx.work.WorkManager
import com.example.scanpro.data.ScanMode
import com.example.scanpro.scanner.SESSION_IMPORT_FAILED
import com.example.scanpro.scanner.SESSION_IMPORT_REQUEST
import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import com.example.scanpro.scanner.scannerSessionRoot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * A camera scan that was handed to [ImportScanWorker] but never became a document: the import failed on every
 * try, or the app was updated from a version that kept no record of the request. Its photos exist nowhere else.
 */
data class KeptScan(
    val id: String,
    val photos: List<File>,
    val mode: ScanMode,
    /** When the session last changed; [CacheCleaner] removes it [KEPT_SCAN_MAX_AGE_MS] after this. */
    val keptAt: Long,
    /** The last error, as the worker reported it, or null when none was recorded. */
    val failure: String?
) {
    val expiresAt: Long get() = keptAt + KEPT_SCAN_MAX_AGE_MS
}

/** How long a scan waiting for its import is kept. Shared with [CacheCleaner]. */
const val KEPT_SCAN_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Finds kept scans and lets the user save them again or discard them. Nothing here deletes photos on its own:
 * only [discard], which the screen calls after the user confirms, and [CacheCleaner] once [KEPT_SCAN_MAX_AGE_MS] has passed.
 */
@Singleton
class KeptScanStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val refresh = MutableStateFlow(0)

    /** Kept scans, newest first. Updates whenever an import starts, finishes or fails, and after [discard]. */
    val scans: Flow<List<KeptScan>> =
        combine(WorkManager.getInstance(context).getWorkInfosByTagFlow(ImportScanWorker.TAG), refresh) { infos, _ ->
            val active = infos.filter { !it.state.isFinished }.flatMap { it.tags }
                .mapNotNull { it.removePrefix(ImportScanWorker.SESSION_TAG_PREFIX).takeIf { id -> id != it } }
                .toSet()
            collect(scannerSessionRoot(context), active)
        }.flowOn(Dispatchers.IO)

    /** Hands the scan to [ImportScanWorker] again, with the edits the user confirmed in the scanner when they are known. */
    suspend fun retry(id: String) = withContext(Dispatchers.IO) {
        val session = File(scannerSessionRoot(context), id)
        val scan = collect(scannerSessionRoot(context), emptySet()).firstOrNull { it.id == id } ?: return@withContext
        // A fresh attempt restarts the 30 days: the user just showed they still want this scan.
        session.setLastModified(System.currentTimeMillis())
        ImportScanWorker.enqueue(context, retryInput(session, scan.photos))
        refresh.value++
    }

    /** Deletes the scan's photos. Only after the user confirmed: they exist nowhere else. */
    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        val root = scannerSessionRoot(context)
        val session = File(root, id)
        // Never follow an id outside the session folder.
        if (session.parentFile?.canonicalPath == root.canonicalPath) session.deleteRecursively()
        refresh.value++
    }

    companion object {
        /** The kept sessions under [root], skipping those whose import is queued or running ([activeSessions]). */
        internal fun collect(root: File, activeSessions: Set<String>): List<KeptScan> =
            root.listFiles().orEmpty()
                .filter { it.isDirectory && it.name !in activeSessions && File(it, SESSION_PENDING_IMPORT).exists() }
                .mapNotNull { session ->
                    val request = readRequest(session)
                    val photos = photosOf(session, request)
                    if (photos.isEmpty()) return@mapNotNull null
                    KeptScan(
                        id = session.name,
                        photos = photos,
                        mode = request?.getString(ImportScanWorker.KEY_MODE)
                            ?.let { runCatching { ScanMode.valueOf(it) }.getOrNull() } ?: ScanMode.DOCUMENT,
                        keptAt = session.lastModified(),
                        failure = File(session, SESSION_IMPORT_FAILED).takeIf { it.isFile }
                            ?.let { runCatching { it.readText() }.getOrNull() }?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
                .sortedByDescending { it.keptAt }

        /**
         * The request to send again: the recorded one, limited to the photos still on disk (with each photo's own
         * outline and filter), or, with no record, every photo in the session for the importer to outline itself.
         */
        internal fun retryInput(session: File, photos: List<File>): Data {
            val request = readRequest(session)
            val recorded = request?.getNullableStringArray(ImportScanWorker.KEY_URIS)?.toList()
            if (request == null || recorded == null) {
                return Data.Builder()
                    .putStringArray(ImportScanWorker.KEY_URIS, photos.map { "file://${it.absolutePath}" }.toTypedArray())
                    .putString(ImportScanWorker.KEY_MODE, ScanMode.DOCUMENT.name)
                    .build()
            }
            val crops = request.getNullableStringArray(ImportScanWorker.KEY_CROPS)?.toList().orEmpty()
            val filters = request.getNullableStringArray(ImportScanWorker.KEY_FILTERS)?.toList().orEmpty()
            val keep = recorded.indices.filter { index -> recorded[index]?.let(::fileOf)?.isFile == true }
            fun List<String?>.pick() = if (size == recorded.size) keep.map { this[it] }.toTypedArray() else emptyArray()
            return Data.Builder()
                .putAll(request)
                .putStringArray(ImportScanWorker.KEY_URIS, keep.map { recorded[it] }.toTypedArray())
                .putStringArray(ImportScanWorker.KEY_CROPS, crops.pick())
                .putStringArray(ImportScanWorker.KEY_FILTERS, filters.pick())
                .build()
        }

        private fun readRequest(session: File): Data? =
            File(session, SESSION_IMPORT_REQUEST).takeIf { it.isFile }
                ?.let { runCatching { Data.fromByteArray(it.readBytes()) }.getOrNull() }

        private fun photosOf(session: File, request: Data?): List<File> {
            val recorded = request?.getNullableStringArray(ImportScanWorker.KEY_URIS)
                ?.mapNotNull { it?.let(::fileOf) }?.filter { it.isFile }
            if (!recorded.isNullOrEmpty()) return recorded
            return session.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
                .sortedBy { it.lastModified() }
        }

        // Plain parsing rather than android.net.Uri, so this works in JVM tests too. Session paths are never escaped.
        private fun fileOf(uri: String): File? =
            uri.takeIf { it.startsWith("file:") }?.removePrefix("file:")?.trimStart('/')?.let { File("/$it") }
    }
}
