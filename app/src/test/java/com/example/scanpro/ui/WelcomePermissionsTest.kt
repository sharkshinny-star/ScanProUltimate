package com.example.scanpro.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * First launch asks for the camera and, on Android 13+, notifications on one page after the welcome, so the first
 * scan opens straight into the camera; it skips the page when there is nothing left to ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class WelcomePermissionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var finished = 0

    private fun text(@StringRes id: Int) = compose.activity.getString(id)

    private fun showAndGetStarted() {
        compose.setContent {
            ScanProTheme(themeMode = ThemeMode.LIGHT) { WelcomeScreen(onGetStarted = { finished++ }) }
        }
        compose.onNodeWithText(text(R.string.ui_welcome_get_started)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun getStartedExplainsBothPermissionsBeforeAsking() {
        showAndGetStarted()

        compose.onNodeWithText(text(R.string.ui_permissions_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ui_permission_camera_title)).assertExists()
        compose.onNodeWithText(text(R.string.ui_permission_notifications_title)).assertExists()
        assertEquals(0, finished)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun notNowCarriesOnWithoutAsking() {
        showAndGetStarted()

        compose.onNodeWithText(text(R.string.ui_permissions_not_now)).performClick()
        compose.waitForIdle()

        assertEquals(1, finished)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun allowAsksForBothInOneGoAndCarriesOnWhateverTheAnswers() {
        showAndGetStarted()

        compose.onNodeWithText(text(R.string.ui_permissions_allow)).performClick()
        compose.waitForIdle()

        val request = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
        assertEquals(0, finished)

        // The camera allowed, notifications refused: the app opens all the same.
        @Suppress("DEPRECATION")
        compose.activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED)
        )
        compose.waitForIdle()
        assertEquals(1, finished)
    }

    @Test
    fun nothingLeftToAskGoesStraightIn() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)

        showAndGetStarted()

        assertEquals(1, finished)
        compose.onNodeWithText(text(R.string.ui_permissions_title)).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13OnlyTheCameraIsAskedFor() {
        showAndGetStarted()

        compose.onNodeWithText(text(R.string.ui_permission_camera_title)).assertExists()
        compose.onNodeWithText(text(R.string.ui_permission_notifications_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.ui_permissions_allow)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(Manifest.permission.CAMERA), shadowOf(compose.activity).lastRequestedPermission.requestedPermissions.toList())
    }
}
