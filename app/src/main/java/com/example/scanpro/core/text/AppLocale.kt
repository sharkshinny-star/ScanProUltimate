package com.example.scanpro.core.text

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's own language, chosen in the app (first launch, or Settings > App language), on every Android version.
 * Android 13+ keeps the choice itself (LocaleManager, also shown in the system's per-app language page); older
 * versions keep it here and apply it by wrapping the application and activity contexts ([wrap]).
 */
object AppLocale {
    /** Languages the app is translated into, with their names written in that language. Keep in step with locales_config.xml. */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം"
    )

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"
    private const val KEY_CHOSEN = "chosen"

    // The application context is null while the Application itself is being attached ([wrap] runs then, on
    // Android 12 and older), so fall back to the context given; shared preferences are per app either way.
    private fun prefs(context: Context) = (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the user has picked a language (first-launch screen shown). */
    fun isChosen(context: Context): Boolean = prefs(context).getBoolean(KEY_CHOSEN, false)

    /** The chosen language tag, or null to follow the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            prefs(context).getString(KEY_TAG, null)
        }

    /** The supported language closest to the phone's, for preselecting on first launch. */
    fun suggested(): String {
        val phone = Locale.getDefault().language
        return SUPPORTED.firstOrNull { it.first == phone }?.first ?: "en"
    }

    /**
     * Switches the app to [tag] and remembers that a choice was made. On Android 13+ the system recreates the
     * activity; on older versions [activity] is recreated here so the new language takes effect at once.
     */
    fun apply(activity: Activity, tag: String) {
        val wasSame = current(activity) == tag
        prefs(activity).edit { putBoolean(KEY_CHOSEN, true).putString(KEY_TAG, tag) }
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag)
        } else if (!wasSame) {
            // The application's resources were set up in the old language at process start; update them too, so
            // notifications, background messages and AppStrings switch now rather than after a restart.
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val resources = activity.application.resources
            val config = Configuration(resources.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            activity.recreate()
        }
    }

    /** Android 12 and older: [base] with the chosen language applied, or [base] itself when none was chosen. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = prefs(base).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
