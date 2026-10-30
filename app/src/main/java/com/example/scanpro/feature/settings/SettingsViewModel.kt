package com.example.scanpro.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    library: DocumentLibrary,
    private val repository: com.example.scanpro.data.DocumentRepository,
    private val operations: com.example.scanpro.core.operations.OperationRunner
) : ViewModel() {
    /** Brings a ScanPro encrypted backup package back as a document. */
    fun restoreBackup(uri: android.net.Uri, password: String) {
        operations.enqueue("Restoring encrypted backup…", null) {
            val document = repository.importEncryptedBackup(uri, password)
            operations.showMessage(com.example.scanpro.core.operations.UiMessage("Restored \"${document.title}\"", actionLabel = "Open") {
                operations.requestOpenDocument(document.id)
            })
        }
    }

    val pdfQuality = settings.pdfQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PdfQuality.BALANCED)
    val autoTitle = settings.autoTitle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val appLock = settings.appLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
    val dynamicColor = settings.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val documentCount: StateFlow<Int> = library.documents.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value.size)
    val trashCount: StateFlow<Int> = library.trash.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)
    val storageBytes: StateFlow<Long> = library.storageBytes

    fun setPdfQuality(value: PdfQuality) { viewModelScope.launch { settings.setPdfQuality(value) } }
    fun setAutoTitle(value: Boolean) { viewModelScope.launch { settings.setAutoTitle(value) } }
    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { settings.setThemeMode(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { settings.setDynamicColor(value) } }
    val trashRetentionDays = settings.trashRetentionDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS)
    val defaultOcrScript = settings.defaultOcrScript.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.scanpro.data.OcrScript.LATIN)
    fun setTrashRetentionDays(value: Int) { viewModelScope.launch { settings.setTrashRetentionDays(value) } }
    fun setDefaultOcrScript(value: com.example.scanpro.data.OcrScript) { viewModelScope.launch { settings.setDefaultOcrScript(value) } }
}
