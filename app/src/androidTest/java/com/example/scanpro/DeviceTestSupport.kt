package com.example.scanpro

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.example.scanpro.data.SettingsStore
import kotlinx.coroutines.runBlocking

/** Nodes carrying any of [texts] (a screen's tab labels change with the language, so tests look for a few). */
fun ComposeTestRule.onAllNodesWithTextAny(vararg texts: String): SemanticsNodeInteractionCollection =
    onAllNodes(texts.map { hasText(it) }.reduce { a, b -> a.or(b) })

/** Safely checks if any node matches without throwing during early Activity launch. */
fun ComposeTestRule.hasAnyNodeWithText(vararg texts: String): Boolean =
    runCatching {
        onAllNodes(texts.map { hasText(it) }.reduce { a, b -> a.or(b) }).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

/** Ensures the first-launch language picker and welcome screen are marked as completed in English. */
fun ensureWelcomePassed(context: Context) {
    context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit()
        .putBoolean("chosen", true)
        .putString("tag", "en")
        .commit()
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        runCatching {
            context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                android.os.LocaleList.forLanguageTags("en")
        }
    }
    runBlocking {
        SettingsStore(context).setWelcomeSeen()
    }
}
