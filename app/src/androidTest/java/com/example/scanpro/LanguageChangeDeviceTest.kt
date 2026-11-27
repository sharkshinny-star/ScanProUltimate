package com.example.scanpro

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.scanpro.core.text.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Choosing a language on a real phone (F66): on Android 13+ the picker's Continue applies the language in place,
 * so the same activity carries on (no restart, no black frame) and the system records the choice.
 * The test clears the "language chosen" flag so the picker shows, and puts the previous choice back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class LanguageChangeDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
    private var hadChosen = false
    private var previousTag: String? = null
    private var previousLocales: LocaleList? = null

    @Before
    fun showThePicker() {
        assumeTrue("in-place language change is Android 13+", Build.VERSION.SDK_INT >= 33)
        hadChosen = prefs.getBoolean("chosen", false)
        previousTag = prefs.getString("tag", null)
        previousLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        prefs.edit().remove("chosen").apply()
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
    }

    @After
    fun putTheChoiceBack() {
        if (Build.VERSION.SDK_INT < 33) return
        prefs.edit().putBoolean("chosen", hadChosen).apply()
        previousTag?.let { prefs.edit().putString("tag", it).apply() } ?: prefs.edit().remove("tag").apply()
        previousLocales?.let { context.getSystemService(LocaleManager::class.java).applicationLocales = it }
    }

    @Test
    fun continueAppliesTheLanguageWithoutRestartingTheActivity() {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithTextAny("Choose your language").fetchSemanticsNodes().isNotEmpty() }
            var before: Activity? = null
            scenario.onActivity { before = it }

            compose.onNodeWithText("हिन्दी").performClick()
            compose.onNodeWithText("Continue").performClick()

            compose.waitUntil(10_000) { compose.onAllNodesWithTextAny("Choose your language").fetchSemanticsNodes().isEmpty() }
            // Give a restart, if one were coming, time to happen; then check it did not.
            Thread.sleep(1_500)
            var after: Activity? = null
            scenario.onActivity { after = it }
            assertSame("the same activity carries on", before, after)
            val original = before!!
            assertFalse(original.isFinishing || original.isDestroyed)
            assertTrue(AppLocale.isChosen(context))
            assertEquals("hi", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        }
    }
}
