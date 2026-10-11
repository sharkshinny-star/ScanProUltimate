package com.example.scanpro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scanProSettings by preferencesDataStore(name = "scanpro_settings")

/** Which colour scheme the app uses. [SYSTEM] follows the device's light or dark setting. */
enum class AppearanceMode { SYSTEM, DAY, NIGHT }

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val keyQuality = stringPreferencesKey("pdf_quality")
    private val keyAutoTitle = booleanPreferencesKey("auto_title")
    private val keyAppLock = booleanPreferencesKey("app_lock")
    private val keyAppearance = stringPreferencesKey("appearance")

    val pdfQuality: Flow<PdfQuality> = context.scanProSettings.data.map { preferences ->
        runCatching {
            PdfQuality.valueOf(preferences[keyQuality] ?: PdfQuality.BALANCED.name)
        }.getOrDefault(PdfQuality.BALANCED)
    }

    val autoTitle: Flow<Boolean> = context.scanProSettings.data.map { preferences ->
        preferences[keyAutoTitle] ?: true
    }

    val appLock: Flow<Boolean> = context.scanProSettings.data.map { preferences ->
        preferences[keyAppLock] ?: false
    }

    val appearance: Flow<AppearanceMode> = context.scanProSettings.data.map { preferences ->
        runCatching {
            AppearanceMode.valueOf(preferences[keyAppearance] ?: AppearanceMode.SYSTEM.name)
        }.getOrDefault(AppearanceMode.SYSTEM)
    }

    suspend fun setPdfQuality(value: PdfQuality) {
        context.scanProSettings.edit { it[keyQuality] = value.name }
    }

    suspend fun setAutoTitle(value: Boolean) {
        context.scanProSettings.edit { it[keyAutoTitle] = value }
    }

    suspend fun setAppLock(value: Boolean) {
        context.scanProSettings.edit { it[keyAppLock] = value }
    }

    suspend fun setAppearance(value: AppearanceMode) {
        context.scanProSettings.edit { it[keyAppearance] = value.name }
    }
}
