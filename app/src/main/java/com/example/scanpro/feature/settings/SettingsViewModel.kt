package com.example.scanpro.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.operations.UiMessage
import com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS
import com.example.scanpro.data.DocumentLibrary
import com.example.scanpro.data.DocumentRepository
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.SettingsStore
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val notifications: AppNotifications,
    library: DocumentLibrary
) : ViewModel() {
    val busy: StateFlow<Boolean> = operations.busy

    /** Writes every document as an encrypted package into the folder the user picked; see DocumentRepository.backupAll. */
    fun backupAll(folder: Uri, password: String) {
        operations.enqueue(context.getString(R.string.settings_backup_running), null) {
            val count = repository.backupAll(folder, password)
            settings.recordBackup()
            operations.showMessage(context.resources.getQuantityString(R.plurals.settings_backup_done, count, count))
        }
    }

    /** Restores one encrypted package as a new document, with a snackbar action that opens it. */
    fun restoreBackup(file: Uri, password: String) {
        operations.enqueue(context.getString(R.string.settings_restore_running), null) {
            val restored = repository.importEncryptedBackup(file, password)
            operations.showMessage(
                UiMessage(
                    context.getString(R.string.settings_restore_done, restored.title),
                    actionLabel = context.getString(R.string.lib_card_open),
                    // Named: a trailing lambda would bind to onDismiss, the last parameter.
                    onAction = { operations.requestOpenDocument(restored.id) }
                )
            )
        }
    }

    val pdfQuality = settings.pdfQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PdfQuality.BALANCED)
    val autoTitle = settings.autoTitle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val appLock = settings.appLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
    val dynamicColor = settings.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val documentCount: StateFlow<Int> = library.overview.map { it.documentCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.overview.value.documentCount)
    val trashCount: StateFlow<Int> = library.trash.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.trash.value.size)
    val storageBytes: StateFlow<Long> = library.storageBytes

    fun setPdfQuality(value: PdfQuality) { viewModelScope.launch { settings.setPdfQuality(value) } }
    fun setAutoTitle(value: Boolean) { viewModelScope.launch { settings.setAutoTitle(value) } }
    val namePattern = settings.namePattern.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setNamePattern(value: String) { viewModelScope.launch { settings.setNamePattern(value) } }
    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { settings.setThemeMode(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { settings.setDynamicColor(value) } }
    val accentPalette = settings.accentPalette.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentPalette.INDIGO)
    fun setAccentPalette(value: AccentPalette) { viewModelScope.launch { settings.setAccentPalette(value) } }
    val homeTips = settings.homeTips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setHomeTips(value: Boolean) { viewModelScope.launch { settings.setHomeTips(value) } }
    val trashRetentionDays = settings.trashRetentionDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_TRASH_RETENTION_DAYS)
    val defaultOcrScript = settings.defaultOcrScript.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OcrScript.LATIN)
    fun setTrashRetentionDays(value: Int) { viewModelScope.launch { settings.setTrashRetentionDays(value) } }
    fun setDefaultOcrScript(value: OcrScript) { viewModelScope.launch { settings.setDefaultOcrScript(value) } }

    val defaultScanMode = settings.defaultScanMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.scanpro.data.ScanMode.DOCUMENT.name)
    fun setDefaultScanMode(value: String) { viewModelScope.launch { settings.setDefaultScanMode(value) } }
    val defaultFilter = settings.defaultFilter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setDefaultFilter(value: com.example.scanpro.data.PageFilter?) { viewModelScope.launch { settings.setDefaultFilter(value) } }
    val reviewMode = settings.reviewMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.scanpro.data.ReviewMode.WHEN_NEEDED)
    fun setReviewMode(value: com.example.scanpro.data.ReviewMode) { viewModelScope.launch { settings.setReviewMode(value) } }

    val notifyFinished = settings.notifyFinished.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifyFailures = settings.notifyFailures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifyTrash = settings.notifyTrash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notifyBackup = settings.notifyBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setNotifyFinished(value: Boolean) { viewModelScope.launch { settings.setNotifyFinished(value) } }
    fun setNotifyFailures(value: Boolean) { viewModelScope.launch { settings.setNotifyFailures(value) } }
    fun setNotifyTrash(value: Boolean) { viewModelScope.launch { settings.setNotifyTrash(value) } }
    fun setNotifyBackup(value: Boolean) { viewModelScope.launch { settings.setNotifyBackup(value) } }
    /** Whether Android lets the app post notifications at all (permission and the app-wide switch). */
    fun canPostNotifications(): Boolean = notifications.canPost()
    fun notificationSettingsIntent() = notifications.systemSettingsIntent()
}
