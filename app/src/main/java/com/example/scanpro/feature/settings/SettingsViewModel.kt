package com.example.scanpro.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.data.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val repository: DocumentRepository,
    private val operations: OperationRunner,
    library: DocumentLibrary
) : ViewModel() {
    val busy: StateFlow<Boolean> = operations.busy

    /** Writes every document as an encrypted package into the folder the user picked; see DocumentRepository.backupAll. */
    fun backupAll(folder: Uri, password: String) {
        operations.enqueue(context.getString(R.string.settings_backup_running), null) {
            val count = repository.backupAll(folder, password)
            operations.showMessage(context.resources.getQuantityString(R.plurals.settings_backup_done, count, count))
        }
    }

    /** Restores one encrypted package as a new document. */
    fun restoreBackup(file: Uri, password: String) {
        operations.enqueue(context.getString(R.string.settings_restore_running), null) {
            val restored = repository.importEncryptedBackup(file, password)
            operations.showMessage(context.getString(R.string.settings_restore_done, restored.title))
        }
    }
    val pdfQuality = settings.pdfQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PdfQuality.BALANCED)
    val autoTitle = settings.autoTitle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val appLock = settings.appLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
    val documentCount: StateFlow<Int> = library.documents.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.documents.value.size)
    val trashCount: StateFlow<Int> = library.trash.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)
    val storageBytes: StateFlow<Long> = library.storageBytes

    fun setPdfQuality(value: PdfQuality) { viewModelScope.launch { settings.setPdfQuality(value) } }
    fun setAutoTitle(value: Boolean) { viewModelScope.launch { settings.setAutoTitle(value) } }
    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { settings.setThemeMode(value) } }
}
