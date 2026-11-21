package com.example.scanpro.feature.qr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanHistoryCsv
import com.example.scanpro.data.ScanHistoryEntry
import com.example.scanpro.data.ScanHistoryStore
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.scanner.ScannedCode
import com.example.scanpro.scanner.parseScannedCode
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Records live scans in the history, when "Save scan history" is on. */
@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val store: ScanHistoryStore,
    private val settings: SettingsStore
) : ViewModel() {
    fun record(code: ScannedCode) {
        viewModelScope.launch {
            if (settings.scanHistoryEnabled.first()) store.add(code.raw, code.format, code.valueType)
        }
    }
}

@HiltViewModel
class QrHistoryViewModel @Inject constructor(
    private val store: ScanHistoryStore,
    private val settings: SettingsStore
) : ViewModel() {
    val entries: StateFlow<List<ScanHistoryEntry>> = store.entries
    val storageAvailable: StateFlow<Boolean> = store.storageAvailable
    val saveHistory: StateFlow<Boolean> = settings.scanHistoryEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSaveHistory(value: Boolean) {
        viewModelScope.launch { settings.setScanHistoryEnabled(value) }
    }

    fun setFavourite(raw: String, favourite: Boolean) = store.setFavourite(raw, favourite)
    fun remove(raw: String) = store.remove(raw)
    fun restore(entry: ScanHistoryEntry, index: Int) = store.restore(entry, index)
    fun clear() = store.clear()

    /**
     * Writes [shown] as CSV in the cache folder ScanPro shares from (one fixed name, replaced each time); null when it
     * can't be written. Only called from the user's own Export tap; the file goes out through the share sheet.
     */
    suspend fun writeCsv(context: Context, shown: List<ScanHistoryEntry>): File? = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            File(folder, "scan-history.csv").apply { writeText(ScanHistoryCsv.export(shown)) }
        }.getOrNull()
    }
}

@HiltViewModel
class QrGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner,
    private val history: ScanHistoryStore
) : ViewModel() {
    /** [content] drawn as [format] in [style], off the main thread; null when it can't be encoded. */
    suspend fun render(content: String, format: CodeFormat, style: CodeStyle): Bitmap? = withContext(Dispatchers.Default) {
        QrEncoder.encode(content, format, style)?.toBitmap()
    }

    /**
     * Keeps a code the person shared, saved or copied in the (encrypted) history, marked "Created", when "Save scan
     * history" is on.
     */
    fun recordCreated(content: String, format: CodeFormat) {
        viewModelScope.launch {
            if (!settings.scanHistoryEnabled.first()) return@launch
            val hint = if (format == CodeFormat.EAN_13) Barcode.TYPE_PRODUCT else Barcode.TYPE_UNKNOWN
            val type = parseScannedCode(content, format.mlKitFormat, hint).valueType
            history.add(content, format.mlKitFormat, type, created = true)
        }
    }

    /**
     * Android 10+: adds [bitmap] to the gallery as Pictures/ScanPro/<name>.png through MediaStore, which needs no
     * storage permission. False when it fails (or on older Android, where the screen offers Share instead).
     */
    suspend fun saveToGallery(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ScanPro-code-" + System.currentTimeMillis() + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScanPro")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return@withContext false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } == true
        }.getOrDefault(false)
        if (!written) {
            // Never leave a half-written, invisible entry behind.
            runCatching { resolver.delete(uri, null, null) }
            return@withContext false
        }
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }.isSuccess
    }

    /** Writes [bitmap] as a PNG in the cache folder that ScanPro shares from; null when the file can't be written. */
    suspend fun writePng(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            // A fixed name: sharing it again replaces the last one, so these files don't pile up.
            val file = File(folder, "qr-code.png")
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
            file
        }.getOrNull()
    }

    /** Saves the PNG at [file] as a one-page document in the library, with the usual progress and "saved" messages. */
    fun saveAsDocument(file: File) {
        operations.enqueue(context.getString(R.string.msg_import_images_progress), context.getString(R.string.msg_document_saved)) {
            repository.importScan(null, listOf(Uri.fromFile(file)), settings.pdfQuality.first(), settings.autoTitle.first())
        }
    }
}
