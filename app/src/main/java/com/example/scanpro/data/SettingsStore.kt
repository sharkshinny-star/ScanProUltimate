package com.example.scanpro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// A corrupt preferences file is replaced with defaults instead of failing every read (and crashing
// the app at launch).
private val Context.scanProSettings by preferencesDataStore(
    name = "scanpro_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val keyQuality = stringPreferencesKey("pdf_quality")
    private val keyAutoTitle = booleanPreferencesKey("auto_title")
    private val keyAppLock = booleanPreferencesKey("app_lock")

    /** Preferences that fall back to defaults when the file can't be read, instead of throwing. */
    private val preferences: Flow<Preferences> = context.scanProSettings.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val pdfQuality: Flow<PdfQuality> = preferences.map { preferences ->
        runCatching {
            PdfQuality.valueOf(preferences[keyQuality] ?: PdfQuality.BALANCED.name)
        }.getOrDefault(PdfQuality.BALANCED)
    }

    private val keyDefaultScanMode = stringPreferencesKey("default_scan_mode")
    private val keyDefaultFilter = stringPreferencesKey("default_filter")

    /** Mode the camera opens in when nothing asked for a particular one: a ScanMode name, or "QR". */
    val defaultScanMode: Flow<String> = preferences.map { it[keyDefaultScanMode] ?: ScanMode.DOCUMENT.name }

    /** Filter new scans start with; null keeps each mode's own (ScanMode.defaultFilter). */
    val defaultFilter: Flow<PageFilter?> = preferences.map { p ->
        p[keyDefaultFilter]?.let { name -> PageFilter.entries.firstOrNull { it.name == name } }
    }

    suspend fun setDefaultScanMode(value: String) {
        context.scanProSettings.edit { it[keyDefaultScanMode] = value }
    }

    suspend fun setDefaultFilter(value: PageFilter?) {
        context.scanProSettings.edit { if (value == null) it.remove(keyDefaultFilter) else it[keyDefaultFilter] = value.name }
    }

    val autoTitle: Flow<Boolean> = preferences.map { preferences ->
        preferences[keyAutoTitle] ?: true
    }

    // On a read error, stay locked rather than silently opening the library.
    val appLock: Flow<Boolean> = context.scanProSettings.data
        .map { preferences -> preferences[keyAppLock] ?: false }
        .catch { error -> if (error is IOException) emit(true) else throw error }

    suspend fun setPdfQuality(value: PdfQuality) {
        context.scanProSettings.edit { it[keyQuality] = value.name }
    }

    suspend fun setAutoTitle(value: Boolean) {
        context.scanProSettings.edit { it[keyAutoTitle] = value }
    }

    private val keyScanHistory = booleanPreferencesKey("scan_history")

    /** Whether codes scanned in QR mode are kept in the on-device scan history; on unless the person switches it off. */
    val scanHistoryEnabled: Flow<Boolean> = preferences.map { it[keyScanHistory] ?: true }.distinctUntilChanged()

    suspend fun setScanHistoryEnabled(value: Boolean) {
        context.scanProSettings.edit { it[keyScanHistory] = value }
    }

    private val keyNamePattern = stringPreferencesKey("name_pattern")

    /** Name pattern for new scans (R34, `imaging/ScanTitle`); blank for the automatic name. */
    val namePattern: Flow<String> = preferences.map { it[keyNamePattern].orEmpty() }.distinctUntilChanged()
    suspend fun setNamePattern(value: String) {
        context.scanProSettings.edit { if (value.isBlank()) it.remove(keyNamePattern) else it[keyNamePattern] = value.trim() }
    }

    private val keyWelcomeSeen = booleanPreferencesKey("welcome_seen")

    /** False until the first-run welcome has been shown and closed. */
    val welcomeSeen: Flow<Boolean> = preferences.map { it[keyWelcomeSeen] ?: false }

    suspend fun setWelcomeSeen() {
        context.scanProSettings.edit { it[keyWelcomeSeen] = true }
    }

    suspend fun setAppLock(value: Boolean) {
        context.scanProSettings.edit { it[keyAppLock] = value }
    }

    private val keyReviewMode = stringPreferencesKey("review_mode")

    /** Check pages before saving (R26). Default: only pages that need it, the fast save the owner asked for. */
    val reviewMode: Flow<ReviewMode> = preferences.map { p ->
        runCatching { ReviewMode.valueOf(p[keyReviewMode] ?: ReviewMode.WHEN_NEEDED.name) }.getOrDefault(ReviewMode.WHEN_NEEDED)
    }.distinctUntilChanged()

    suspend fun setReviewMode(value: ReviewMode) {
        context.scanProSettings.edit { it[keyReviewMode] = value.name }
    }

    private val keyThemeMode = stringPreferencesKey("theme_mode")

    // The theme a tap just picked, applied before the write reaches disk so the switch feels instant.
    private val pendingThemeMode = MutableStateFlow<ThemeMode?>(null)

    val themeMode: Flow<ThemeMode> = combine(
        preferences.map { preferences ->
            runCatching {
                ThemeMode.valueOf(preferences[keyThemeMode] ?: ThemeMode.LIGHT.name)
            }.getOrDefault(ThemeMode.LIGHT)
        },
        pendingThemeMode
    ) { stored, pending -> pending ?: stored }.distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) {
        pendingThemeMode.value = value
        context.scanProSettings.edit { it[keyThemeMode] = value.name }
    }

    private val keyAccentPalette = stringPreferencesKey("accent_palette")

    // Like the theme mode: a tapped swatch recolours the app before the write reaches disk.
    private val pendingAccentPalette = MutableStateFlow<AccentPalette?>(null)

    /** The colour theme; [AccentPalette.INDIGO] (the original look) until one is picked. */
    val accentPalette: Flow<AccentPalette> = combine(
        preferences.map { preferences ->
            runCatching { AccentPalette.valueOf(preferences[keyAccentPalette] ?: AccentPalette.INDIGO.name) }
                .getOrDefault(AccentPalette.INDIGO)
        },
        pendingAccentPalette
    ) { stored, pending -> pending ?: stored }.distinctUntilChanged()

    /** Picking a colour theme also turns wallpaper colours off, since those would hide it. */
    suspend fun setAccentPalette(value: AccentPalette) {
        pendingAccentPalette.value = value
        context.scanProSettings.edit {
            it[keyAccentPalette] = value.name
            it[keyDynamicColor] = false
        }
    }

    private val keyHomeTips = booleanPreferencesKey("home_tips")

    /** The "Did you know?" card on Home. On until closed there or switched off in Settings. */
    val homeTips: Flow<Boolean> = preferences.map { it[keyHomeTips] ?: true }

    suspend fun setHomeTips(value: Boolean) {
        context.scanProSettings.edit { it[keyHomeTips] = value }
    }

    private val keyWhatsNewSeen = intPreferencesKey("whats_new_seen")

    /** The newest "What's new" release notes already shown (0 = none). See [WHATS_NEW_VERSION]. */
    val whatsNewSeen: Flow<Int> = preferences.map { it[keyWhatsNewSeen] ?: 0 }

    suspend fun setWhatsNewSeen(version: Int) {
        context.scanProSettings.edit { it[keyWhatsNewSeen] = maxOf(version, it[keyWhatsNewSeen] ?: 0) }
    }

    private val keyDynamicColor = booleanPreferencesKey("dynamic_color")

    /** Android 12+ wallpaper colours instead of the brand palette. Off by default so the brand shows first. */
    val dynamicColor: Flow<Boolean> = preferences.map { it[keyDynamicColor] ?: false }

    suspend fun setDynamicColor(value: Boolean) {
        context.scanProSettings.edit { it[keyDynamicColor] = value }
    }

    private val keyLibrarySort = stringPreferencesKey("library_sort")
    private val keyLibraryGrid = booleanPreferencesKey("library_grid")
    private val keyTrashRetentionDays = intPreferencesKey("trash_retention_days")
    private val keyDefaultOcrScript = stringPreferencesKey("default_ocr_script")

    /** How the Files tab is sorted; a [LibrarySort] name. */
    val librarySort: Flow<String> = preferences.map { it[keyLibrarySort] ?: "UPDATED" }
    val libraryGrid: Flow<Boolean> = preferences.map { it[keyLibraryGrid] ?: true }
    /** Days a deleted document waits in the recycle bin before it is removed for good. */
    val trashRetentionDays: Flow<Int> = preferences.map { it[keyTrashRetentionDays] ?: DEFAULT_TRASH_RETENTION_DAYS }
    /** The OCR script new documents are read with. */
    val defaultOcrScript: Flow<OcrScript> = preferences.map { runCatching { OcrScript.valueOf(it[keyDefaultOcrScript].orEmpty()) }.getOrDefault(OcrScript.LATIN) }

    suspend fun setLibrarySort(value: String) { context.scanProSettings.edit { it[keyLibrarySort] = value } }

    private val keyLibrarySortAscending = stringPreferencesKey("library_sort_ascending")
    private val keyRecentSearches = stringPreferencesKey("recent_searches")

    /** Files sort direction (R35): true ascending, false descending, null the key's own default. */
    val librarySortAscending: Flow<Boolean?> = preferences.map { p ->
        when (p[keyLibrarySortAscending]) { "ASC" -> true; "DESC" -> false; else -> null }
    }
    suspend fun setLibrarySortAscending(value: Boolean?) {
        context.scanProSettings.edit { if (value == null) it.remove(keyLibrarySortAscending) else it[keyLibrarySortAscending] = if (value) "ASC" else "DESC" }
    }

    /** Recent search terms, newest first (R35). */
    val recentSearches: Flow<List<String>> = preferences.map { JsonCodec.decodeStringList(it[keyRecentSearches]) }.distinctUntilChanged()
    suspend fun recordSearch(term: String) {
        context.scanProSettings.edit { it[keyRecentSearches] = JsonCodec.encodeStringList(RecentSearches.add(JsonCodec.decodeStringList(it[keyRecentSearches]), term)) }
    }
    suspend fun clearRecentSearches() { context.scanProSettings.edit { it.remove(keyRecentSearches) } }
    suspend fun setLibraryGrid(value: Boolean) { context.scanProSettings.edit { it[keyLibraryGrid] = value } }
    suspend fun setTrashRetentionDays(value: Int) { context.scanProSettings.edit { it[keyTrashRetentionDays] = value.coerceIn(1, 365) } }
    suspend fun setDefaultOcrScript(value: OcrScript) { context.scanProSettings.edit { it[keyDefaultOcrScript] = value.name } }

    private val keyLockedFolders = stringSetPreferencesKey("locked_folders")
    /** Folder names (lower-cased) whose documents need the phone's unlock; see FolderLock. */
    val lockedFolders: Flow<Set<String>> = preferences.map { it[keyLockedFolders] ?: emptySet() }

    suspend fun setFolderLocked(folderKey: String, locked: Boolean) {
        context.scanProSettings.edit {
            val now = it[keyLockedFolders] ?: emptySet()
            it[keyLockedFolders] = if (locked) now + folderKey else now - folderKey
        }
    }

    private val keyCompletedScans = intPreferencesKey("completed_scans")
    private val keyFeedbackPromptShown = booleanPreferencesKey("feedback_prompt_shown")

    /** Camera scans saved as documents, for deciding when to ask about the experience. */
    val completedScans: Flow<Int> = preferences.map { it[keyCompletedScans] ?: 0 }
    /** True once the rating sheet has been shown; it is shown only once. */
    val feedbackPromptShown: Flow<Boolean> = preferences.map { it[keyFeedbackPromptShown] ?: false }

    suspend fun recordCompletedScan() {
        context.scanProSettings.edit { it[keyCompletedScans] = (it[keyCompletedScans] ?: 0) + 1 }
    }

    suspend fun setFeedbackPromptShown() {
        context.scanProSettings.edit { it[keyFeedbackPromptShown] = true }
    }

    private val keyNotifyFinished = booleanPreferencesKey("notify_finished")
    private val keyNotifyFailures = booleanPreferencesKey("notify_failures")
    private val keyNotifyTrash = booleanPreferencesKey("notify_trash")
    private val keyNotifyBackup = booleanPreferencesKey("notify_backup")
    private val keyLastBackupAt = longPreferencesKey("last_backup_at")
    private val keyLastBackupReminderAt = longPreferencesKey("last_backup_reminder_at")

    /** A job that finished while the app was out of sight. On by default: it answers the user's own request. */
    val notifyFinished: Flow<Boolean> = preferences.map { it[keyNotifyFinished] ?: true }
    /** A job that failed while the app was out of sight. On by default, for the same reason. */
    val notifyFailures: Flow<Boolean> = preferences.map { it[keyNotifyFailures] ?: true }
    /** A day before documents leave the recycle bin for good. Off until the user asks for it. */
    val notifyTrash: Flow<Boolean> = preferences.map { it[keyNotifyTrash] ?: false }
    /** A reminder when no backup was made for a month. Off until the user asks for it. */
    val notifyBackup: Flow<Boolean> = preferences.map { it[keyNotifyBackup] ?: false }
    /** When the last backup was written (0 when never), and when the last reminder about it was shown. */
    val lastBackupAt: Flow<Long> = preferences.map { it[keyLastBackupAt] ?: 0L }
    val lastBackupReminderAt: Flow<Long> = preferences.map { it[keyLastBackupReminderAt] ?: 0L }

    suspend fun setNotifyFinished(value: Boolean) { context.scanProSettings.edit { it[keyNotifyFinished] = value } }
    suspend fun setNotifyFailures(value: Boolean) { context.scanProSettings.edit { it[keyNotifyFailures] = value } }
    suspend fun setNotifyTrash(value: Boolean) { context.scanProSettings.edit { it[keyNotifyTrash] = value } }
    suspend fun setNotifyBackup(value: Boolean, now: Long = System.currentTimeMillis()) {
        context.scanProSettings.edit {
            it[keyNotifyBackup] = value
            // The month is counted from when the reminder was switched on, not from a backup made long ago.
            if (value) it[keyLastBackupReminderAt] = now
        }
    }
    suspend fun recordBackup(now: Long = System.currentTimeMillis()) { context.scanProSettings.edit { it[keyLastBackupAt] = now } }
    suspend fun recordBackupReminder(now: Long = System.currentTimeMillis()) { context.scanProSettings.edit { it[keyLastBackupReminderAt] = now } }
}

const val DEFAULT_TRASH_RETENTION_DAYS = 7
/** The recycle-bin retention choices offered in Settings. */
val TRASH_RETENTION_CHOICES = listOf(7, 14, 30)

/**
 * The release notes the "What's new" sheet describes. Raise it (and update the sheet) when a release has
 * something worth telling existing users about; everyone who has not seen that version yet sees it once.
 * New installs skip it: the welcome already covers everything.
 */
const val WHATS_NEW_VERSION = 2
