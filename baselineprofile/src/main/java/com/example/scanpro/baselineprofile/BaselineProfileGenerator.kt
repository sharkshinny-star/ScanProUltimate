package com.example.scanpro.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records ScanPro's startup and main journeys: launch, the home screen, opening the camera, and each tab.
 * Run on a device or emulator with: ./gradlew :app:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        // The camera opens without a permission prompt the recording can't answer.
        device.executeShellCommand("pm grant $packageName android.permission.CAMERA")
        startActivityAndWait()
        tapIfShown("Skip")          // first-run welcome
        tapIfShown("Start scanning")
        device.wait(Until.hasObject(By.text("QR code")), TIMEOUT)
        tapIfShown("Got it")        // first-time camera tips
        device.pressBack()
        listOf("Tools", "Files", "Settings", "Home").forEach(::tapIfShown)
    }
}

internal const val PACKAGE = "com.scanpro.app"
private const val TIMEOUT = 5_000L

internal fun MacrobenchmarkScope.tapIfShown(text: String) {
    if (device.wait(Until.hasObject(By.text(text)), TIMEOUT)) {
        device.findObject(By.text(text))?.click()
        device.waitForIdle()
    }
}
