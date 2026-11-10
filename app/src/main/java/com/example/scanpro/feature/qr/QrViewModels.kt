package com.example.scanpro.feature.qr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.ScanHistoryEntry
import com.example.scanpro.data.ScanHistoryStore
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.scanner.ScannedCode
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
    val saveHistory: StateFlow<Boolean> = settings.scanHistoryEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSaveHistory(value: Boolean) {
        viewModelScope.launch { settings.setScanHistoryEnabled(value) }
    }

    fun setFavourite(raw: String, favourite: Boolean) = store.setFavourite(raw, favourite)
    fun remove(raw: String) = store.remove(raw)
    fun clear() = store.clear()
}

@HiltViewModel
class QrGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val operations: OperationRunner
) : ViewModel() {
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
