package com.example.scanpro

import android.app.Activity
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launch on a real phone (F66): a plain launcher start plays the intro and reaches the app in a few seconds, and
 * on Android 12+ the app's light/dark choice (told to the system for the launch screen) is applied without a restart.
 * Run from Android Studio with a phone or emulator connected (the welcome must already have been passed).
 */
@RunWith(AndroidJUnit4::class)
class LaunchDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @org.junit.Before
    fun ensureReady() {
        ensureWelcomePassed(context)
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(context.packageName, MainActivity::class.java.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test
    fun launcherStartPlaysTheIntroAndReachesTheApp() {
        ActivityScenario.launch<MainActivity>(launcherIntent()).use {
            // Intro 2.25 s + launch-screen hold up to 1.5 s, then the app; the tab bar is on every screen after the welcome.
            // 15 s leaves room for slow physical hardware; the wait itself is the timing check.
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.hasAnyNodeWithText("Home", "Settings")
            }
            compose.onNodeWithText("Choose your language").assertDoesNotExist()
        }
    }

    @Test
    fun appsLightDarkChoiceIsAppliedWithoutARestart() {
        assumeTrue("per-app night mode exists from Android 12", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ActivityScenario.launch<MainActivity>(launcherIntent()).use { scenario ->
            compose.waitUntil(15_000) { compose.hasAnyNodeWithText("Home", "Settings") }
            var first: Activity? = null
            scenario.onActivity { first = it }
            // The system's answer to setApplicationNightMode is a configuration change; it must land in place.
            Thread.sleep(3_000)
            var later: Activity? = null
            var activityDark = false
            scenario.onActivity {
                later = it
                activityDark = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            assertSame("no restart from the night-mode change", first, later)
            val appDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            assertEquals("activity and application agree on light/dark", appDark, activityDark)
            val phoneDark = context.getSystemService(UiModeManager::class.java).nightMode == UiModeManager.MODE_NIGHT_YES
            // There is no getter for the per-app mode; this line in the test output says whether it is overriding the phone.
            println("ScanPro light/dark: app dark=$appDark, phone dark=$phoneDark" + if (appDark != phoneDark) " (per-app mode in effect)" else "")
        }
    }
}
