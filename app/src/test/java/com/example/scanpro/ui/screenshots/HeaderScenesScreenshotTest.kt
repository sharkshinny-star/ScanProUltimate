package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.HeaderScene
import com.example.scanpro.core.designsystem.HeaderSceneStrip
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's header scenes, each frozen part-way through and drawn larger than it is in the top bar, so the
 * drawings can be looked at. Run with:
 * ./gradlew :app:testDebugUnitTest --tests '*HeaderScenesScreenshotTest*' -Pscanpro.screenshots=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class HeaderScenesScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")

    @Before
    fun setUp() {
        assumeTrue("Pass -Pscanpro.screenshots=true to render screenshots", System.getProperty("scanpro.screenshots") == "true")
        outDir.mkdirs()
    }

    /** Every scene, in two pictures (the list is too long for one screen): a day sky on the first, night on the second. */
    @Test fun scenesFirstThird() = shoot("header-scenes-1", HeaderScene.entries.take(6), hour = 10)

    @Test fun scenesSecondThird() = shoot("header-scenes-2", HeaderScene.entries.drop(6).take(6), hour = 22)

    @Test fun scenesLastThird() = shoot("header-scenes-3", HeaderScene.entries.drop(12), hour = 10)

    /** At its real size, beside the name as Home's top bar lays it out, with the menu button's space at the end. */
    @Test fun inTopBar() {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(top = 8.dp)) {
                        listOf(HeaderScene.RACE_CAR to 0.5f, HeaderScene.MONKEY to 0.45f, HeaderScene.AUTO to 0.5f, HeaderScene.ELEPHANT to 0.5f, HeaderScene.BIRDS to 0.4f)
                            .forEachIndexed { i, (scene, at) ->
                                Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BrandLockup(subtitle = "Good night")
                                    HeaderSceneStrip(Modifier.weight(1f).padding(start = 8.dp).height(52.dp), still = scene to at, hour = if (i % 2 == 0) 22 else 10)
                                }
                            }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "header-scenes-topbar-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String, scenes: List<HeaderScene>, hour: Int) {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(8.dp)) {
                        scenes.forEach { scene ->
                            val at = when (scene) {
                                HeaderScene.TRAIN -> 0.2f
                                HeaderScene.ROCKET -> 0.5f
                                else -> 0.45f
                            }
                            HeaderSceneStrip(Modifier.fillMaxWidth().height(104.dp), still = scene to at, hour = hour)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
