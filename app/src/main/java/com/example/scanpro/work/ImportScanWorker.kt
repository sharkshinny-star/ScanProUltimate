package com.example.scanpro.work

import com.example.scanpro.core.text.AppStrings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.scanpro.MainActivity
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.JsonCodec
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.scanner.SESSION_IMPORT_FAILED
import com.example.scanpro.scanner.SESSION_IMPORT_REQUEST
import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import com.example.scanpro.scanner.SESSION_ROOT
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.core.net.toUri
import com.example.scanpro.core.notifications.AppNotifications

/**
 * Turns a finished camera session into a document. It runs as WorkManager work rather than in the app's
 * own scope, so a scan whose import was under way when the process died is finished the next time the
 * app runs instead of being lost in the cache.
 */
@HiltWorker
class ImportScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner,
    private val notifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Large batches keep their page lists in the scan's folder (see enqueue); read them back from there.
        val inputData = expand(inputData)
        val uris = inputData.getNullableStringArray(KEY_URIS)?.filterNotNull()?.map(Uri::parse).orEmpty()
        if (uris.isEmpty()) return Result.success()
        val mode = runCatching { ScanMode.valueOf(inputData.getString(KEY_MODE).orEmpty()) }.getOrDefault(ScanMode.DOCUMENT)
        val crops = inputData.getNullableStringArray(KEY_CROPS)?.filterNotNull()?.map(JsonCodec::decodeCrop).orEmpty()
        val filters = inputData.getNullableStringArray(KEY_FILTERS)?.filterNotNull()?.map { runCatching { PageFilter.valueOf(it) }.getOrDefault(PageFilter.ORIGINAL) }.orEmpty()
        // One entry per photo; an empty entry means the review sliders were left alone for that page.
        val adjustments: List<PageAdjustments?> = inputData.getNullableStringArray(KEY_ADJUSTMENTS)
            ?.map { raw -> raw?.takeIf { it.isNotBlank() }?.let(JsonCodec::decodeAdjustments) }.orEmpty()
        // Photos the scanner already discarded (for instance after a long time in the background) can't be imported.
        if (uris.any { it.scheme == "file" && !File(it.path.orEmpty()).isFile }) {
            operations.showMessage(applicationContext.getString(R.string.msg_scan_photos_unavailable))
            return Result.success()
        }

        val document = try {
            operations.perform(applicationContext.getString(R.string.msg_finishing_scan_progress, applicationContext.getString(mode.labelRes).lowercase())) {
                repository.importImages(
                    uris, settings.pdfQuality.first(), settings.autoTitle.first(), mode, crops, filters, adjustments,
                    title = inputData.getString(KEY_TITLE),
                    folder = inputData.getString(KEY_FOLDER)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Keep the photos: they exist nowhere else. A passing failure (low memory, a busy disk) gets
            // two more automatic tries; after that the message offers Retry, which runs the same work again.
            if (runAttemptCount < MAX_AUTOMATIC_ATTEMPTS - 1) return Result.retry()
            val context = applicationContext
            val input = inputData
            // The scan now shows under "Unsaved scans", with this reason, until it is saved or discarded.
            sessionDirOf(input)?.let { session ->
                runCatching { File(session, SESSION_IMPORT_FAILED).writeText(AppStrings.storable(error)) }
            }
            notifications.taskFailed(context.getString(R.string.notify_scan_failed), MainActivity.ACTION_OPEN_KEPT_SCANS)
            operations.showMessage(
                UiMessage(
                    context.getString(R.string.msg_could_not_save_scan, error.message ?: context.getString(R.string.msg_unknown_error)),
                    actionLabel = context.getString(R.string.common_retry),
                    onAction = { enqueue(context, input) }
                )
            )
            return Result.success()
        }

        settings.recordCompletedScan()
        // Text scans copy their text instead; every other scan offers share/save straight away.
        if (mode == ScanMode.TEXT) copyRecognizedText(document) else operations.offerScanSaved(document.id)
        // Opens the document only if the user is still on a top-level screen; see ScanProApp.
        operations.requestOpenWhenIdle(document.id)
        notifications.taskFinished(applicationContext.getString(R.string.notify_scan_saved), document.id)
        // The document now holds its own copies; drop the scanner session's temporary captures.
        uris.asSequence()
            .filter { it.scheme == "file" }
            .mapNotNull { uri -> uri.path?.let { File(it).parentFile } }
            .filter { it.parentFile?.name == SESSION_ROOT }
            .distinct()
            .forEach { it.deleteRecursively() }
        return Result.success()
    }

    /** "Extract text" scans put the recognised text on the clipboard so it can be pasted straight away. */
    private fun copyRecognizedText(document: ScanDocument) {
        val text = document.pages.sortedBy { it.position }.joinToString("\n\n") { it.ocrText.trim() }.trim()
        if (text.isBlank()) {
            operations.showMessage(applicationContext.getString(R.string.msg_no_text_found_in_scan))
            return
        }
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(applicationContext.getString(R.string.msg_clip_extracted_text), text).apply {
            // Scans often hold IDs and account numbers; keep them out of the clipboard preview on Android 13+.
            description.extras = android.os.PersistableBundle().apply {
                putBoolean(
                    if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                    true
                )
            }
        }
        clipboard.setPrimaryClip(clip)
        operations.showMessage(applicationContext.getString(R.string.msg_text_copied_to_clipboard))
    }

    companion object {
        internal const val KEY_URIS = "uris"
        internal const val KEY_MODE = "mode"
        internal const val KEY_CROPS = "crops"
        internal const val KEY_FILTERS = "filters"
        /** Parallel to [KEY_URIS]: each page's review-slider adjustments as JSON, or an empty string for none. */
        internal const val KEY_ADJUSTMENTS = "adjustments"
        /** The document name typed on the review screen; absent when it is left to the automatic title. */
        internal const val KEY_TITLE = "title"
        /** The folder chosen on the review screen; absent for none. */
        internal const val KEY_FOLDER = "folder"
        private const val MAX_AUTOMATIC_ATTEMPTS = 3
        /** Every import carries this tag, plus [SESSION_TAG_PREFIX] and its session folder's name. */
        const val TAG = "import-scan"
        const val SESSION_TAG_PREFIX = "import-scan-session:"

        private fun sessionDirOf(input: ImportRequest): File? =
            input.getString(KEY_REQUEST_FILE)?.let { File(it).parentFile }
                ?: input.getNullableStringArray(KEY_URIS)?.firstOrNull()?.let { File(it.toUri().path.orEmpty()).parentFile }

        /** Set instead of the page lists when they were too large for WorkManager: the file that holds them. */
        internal const val KEY_REQUEST_FILE = "request_file"

        /** The full request: the work's input itself, or the one saved at its [KEY_REQUEST_FILE]. */
        internal fun expand(input: Data): ImportRequest {
            val path = input.getString(KEY_REQUEST_FILE) ?: return ImportRequest.fromData(input)
            return runCatching { ImportRequest.readFile(File(path)) }.getOrNull() ?: ImportRequest.fromData(input)
        }

        fun inputFor(
            uris: List<Uri>,
            mode: ScanMode,
            crops: List<CropQuad>,
            filters: List<PageFilter>,
            adjustments: List<PageAdjustments?> = emptyList(),
            /** The name typed on the review screen; null or blank names the document automatically. */
            title: String? = null,
            /** The folder chosen on the review screen; null or blank saves it outside any folder. */
            folder: String? = null
        ): ImportRequest = ImportRequest.Builder()
            .putStringArray(KEY_URIS, uris.map { it.toString() }.toTypedArray())
            .putString(KEY_MODE, mode.name)
            .putStringArray(KEY_CROPS, crops.map { JsonCodec.encodeCrop(it) }.toTypedArray())
            .putStringArray(KEY_FILTERS, filters.map { it.name }.toTypedArray())
            .putStringArray(KEY_ADJUSTMENTS, adjustments.map { it?.let(JsonCodec::encodeAdjustments) ?: "" }.toTypedArray())
            .apply { title?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(KEY_TITLE, it) } }
            .apply { folder?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(KEY_FOLDER, it) } }
            .build()

        /**
         * Keeps a camera session whose photos could not be added to an existing document (Add page, Retake) under
         * "Unsaved scans", so they can still be saved as a scan, shared or discarded instead of being lost.
         * [error] is the reason shown there; null when the user stopped the job.
         */
        fun keepUnsaved(input: ImportRequest, error: Throwable?) {
            val sessionDir = sessionDirOf(input)?.takeIf { it.parentFile?.name == SESSION_ROOT && it.isDirectory } ?: return
            runCatching {
                File(sessionDir, SESSION_IMPORT_REQUEST).writeText(input.toJson())
                if (error != null) File(sessionDir, SESSION_IMPORT_FAILED).writeText(AppStrings.storable(error))
                // Last, so "Unsaved scans" never lists a session without its request.
                File(sessionDir, SESSION_PENDING_IMPORT).createNewFile()
            }
        }

        /** One piece of work per session folder, so a repeated request for the same scan never imports it twice. */
        fun enqueue(context: Context, input: ImportRequest) {
            val sessionDir = sessionDirOf(input)
            val sessionKey = sessionDir?.name ?: "scan"
            val inSession = sessionDir?.parentFile?.name == SESSION_ROOT
            if (inSession) runCatching {
                // Cleanup leaves a session with this marker alone until its import is done, and the request
                // itself is kept beside the photos so the scan can be saved again from "Unsaved scans".
                File(sessionDir, SESSION_PENDING_IMPORT).createNewFile()
                File(sessionDir, SESSION_IMPORT_REQUEST).writeText(input.toJson())
                // A new attempt: the scan leaves "Unsaved scans" until this one fails too.
                File(sessionDir, SESSION_IMPORT_FAILED).delete()
            }
            // WorkManager refuses input over 10 KB, which a batch of about 60 pages passes (a path, crop, filter and
            // sliders per page). Then the work gets only the path of the request saved above.
            val workInput = input.toWorkData()
                ?: if (inSession) workDataOf(KEY_REQUEST_FILE to File(sessionDir, SESSION_IMPORT_REQUEST).absolutePath)
                else error("Scan import request too large and not in a scanner session")
            val request = OneTimeWorkRequestBuilder<ImportScanWorker>()
                .setInputData(workInput)
                .addTag(TAG)
                .addTag(SESSION_TAG_PREFIX + sessionKey)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("import-scan-$sessionKey", ExistingWorkPolicy.KEEP, request)
        }
    }
}
