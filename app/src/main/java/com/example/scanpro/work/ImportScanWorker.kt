package com.example.scanpro.work

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
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.JsonCodec
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.scanner.SESSION_ROOT
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

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
    private val operations: OperationRunner
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.map(Uri::parse).orEmpty()
        if (uris.isEmpty()) return Result.success()
        val mode = runCatching { ScanMode.valueOf(inputData.getString(KEY_MODE).orEmpty()) }.getOrDefault(ScanMode.DOCUMENT)
        val crops = inputData.getStringArray(KEY_CROPS)?.map(JsonCodec::decodeCrop).orEmpty()
        val filters = inputData.getStringArray(KEY_FILTERS)?.map { runCatching { PageFilter.valueOf(it) }.getOrDefault(PageFilter.ORIGINAL) }.orEmpty()
        // Photos the scanner already discarded (for instance after a long time in the background) can't be imported.
        if (uris.any { it.scheme == "file" && !File(it.path.orEmpty()).isFile }) {
            operations.showMessage("The photos from an unfinished scan are no longer available")
            return Result.success()
        }

        val document = try {
            operations.perform("Finishing ${mode.label.lowercase()} scan, enhancing pages, and running OCR…") {
                repository.importImages(uris, settings.pdfQuality.first(), settings.autoTitle.first(), mode, crops, filters)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Keep the photos: they exist nowhere else. Retry runs the same work again.
            val context = applicationContext
            val input = inputData
            operations.showMessage(
                UiMessage("Could not save the scan: ${error.message ?: "unknown error"}", actionLabel = "Retry") {
                    enqueue(context, input)
                }
            )
            return Result.success()
        }

        settings.recordCompletedScan()
        // Text scans copy their text instead; every other scan offers share/save straight away.
        if (mode == ScanMode.TEXT) copyRecognizedText(document) else operations.offerScanSaved(document.id)
        operations.requestOpenDocument(document.id)
        // The document now holds its own copies; drop the scanner session's temporary captures.
        uris.asSequence()
            .filter { it.scheme == "file" }
            .mapNotNull { uri -> uri.path?.let { File(it).parentFile } }
            .filter { it.parentFile?.name == SESSION_ROOT }
            .distinct()
            .forEach { it.deleteRecursively() }
        return Result.success()
    }

    /** "Extract text" scans put the recognized text on the clipboard so it can be pasted straight away. */
    private fun copyRecognizedText(document: ScanDocument) {
        val text = document.pages.sortedBy { it.position }.joinToString("\n\n") { it.ocrText.trim() }.trim()
        if (text.isBlank()) {
            operations.showMessage("No text found in this scan")
            return
        }
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Extracted text", text).apply {
            // Scans often hold IDs and account numbers; keep them out of the clipboard preview on Android 13+.
            description.extras = android.os.PersistableBundle().apply {
                putBoolean(
                    if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                    true
                )
            }
        }
        clipboard.setPrimaryClip(clip)
        operations.showMessage("Text copied to clipboard")
    }

    companion object {
        private const val KEY_URIS = "uris"
        private const val KEY_MODE = "mode"
        private const val KEY_CROPS = "crops"
        private const val KEY_FILTERS = "filters"

        fun inputFor(uris: List<Uri>, mode: ScanMode, crops: List<CropQuad>, filters: List<PageFilter>): Data = workDataOf(
            KEY_URIS to uris.map(Uri::toString).toTypedArray(),
            KEY_MODE to mode.name,
            KEY_CROPS to crops.map(JsonCodec::encodeCrop).toTypedArray(),
            KEY_FILTERS to filters.map { it.name }.toTypedArray()
        )

        /** One piece of work per session folder, so a repeated request for the same scan never imports it twice. */
        fun enqueue(context: Context, input: Data) {
            val sessionKey = input.getStringArray(KEY_URIS)?.firstOrNull()?.let { File(Uri.parse(it).path.orEmpty()).parentFile?.name } ?: "scan"
            val request = OneTimeWorkRequestBuilder<ImportScanWorker>().setInputData(input).build()
            WorkManager.getInstance(context).enqueueUniqueWork("import-scan-$sessionKey", ExistingWorkPolicy.KEEP, request)
        }
    }
}
