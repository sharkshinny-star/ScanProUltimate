package com.example.scanpro

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.core.text.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Starts the real app (Application, Hilt, MainActivity, the first Compose screen) on every Android version it
 * supports (7.0 to 17), on the JVM with Robolectric's copy of each version's framework. It catches crashes that depend on the
 * Android version, like the start-up crash on 7–12 in ca8bfa8 (see F33). It is not a device test: camera, GPU and
 * Play services are not exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37])
class LaunchSmokeTest {

    @Test
    fun `the app starts and reaches its first screen`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }

    @Test
    fun `the app survives being recreated, as on a rotation or language change`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }

    @Test
    fun `a saved language is applied while the application is still being attached`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().putString("tag", "hi").commit()
        // During Application.attachBaseContext the base context has no application context yet: the F33 crash.
        val attaching = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context? = null
        }
        val wrapped = AppLocale.wrap(attaching)
        if (Build.VERSION.SDK_INT < 33) assertEquals("hi", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `reopening from Recents does not replay the intent the task started with`() {
        val fromHistory = Intent(Intent.ACTION_MAIN).setAction("com.example.scanpro.action.REPORT_ISSUE")
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        launch(fromHistory).use { scenario ->
            settle()
            scenario.onActivity { activity -> assertNull(shadowOf(activity).nextStartedActivity) }
        }
    }

    private fun launch(intent: Intent): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(intent.setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
            .also { it.moveToState(Lifecycle.State.RESUMED) }

    /** Lets the settings load on their own thread and the main thread catch up. */
    private fun settle() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    @Test
    fun `mlkit context initializes without missing dependency exception`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(app)
    }
}
