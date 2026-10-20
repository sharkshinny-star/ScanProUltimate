package com.example.scanpro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    suspend fun setAppLock(value: Boolean) {
        context.scanProSettings.edit { it[keyAppLock] = value }
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
}
