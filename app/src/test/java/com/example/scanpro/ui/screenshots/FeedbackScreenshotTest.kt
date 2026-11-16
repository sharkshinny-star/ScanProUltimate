package com.example.scanpro.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.feature.feedback.FeedbackAttachment
import com.example.scanpro.feature.feedback.FeedbackFormContent
import com.example.scanpro.feature.feedback.FeedbackFormKind
import com.example.scanpro.feature.feedback.FeedbackReason
import com.example.scanpro.feature.feedback.IssueCategory
import com.example.scanpro.feature.feedback.RateExperienceContent
import com.example.scanpro.feature.feedback.RateThanksContent
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
 * Renders "Rate your experience" and the feedback form (sheet contents, on the sheet's colour) to PNG files.
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*FeedbackScreenshotTest*' -Pscanpro.screenshots=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class FeedbackScreenshotTest(private val dark: Boolean) {
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

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                // ModalBottomSheet's default container colour.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.padding(top = 24.dp)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "feedback_$name-${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rateNothingChosen() = shoot("rate_empty") {
        RateExperienceContent(0, emptySet(), "", onRate = {}, onToggleReason = {}, onTextChange = {}, onSubmit = {}, onNotNow = {})
    }

    @Test
    fun ratePoorWithOther() = shoot("rate_poor") {
        RateExperienceContent(
            2, setOf(FeedbackReason.SLOW, FeedbackReason.OTHER), "Saving a 20-page scan takes a long time",
            onRate = {}, onToggleReason = {}, onTextChange = {}, onSubmit = {}, onNotNow = {}
        )
    }

    @Test
    fun rateLovedIt() = shoot("rate_loved") {
        RateExperienceContent(5, setOf(FeedbackReason.QUALITY), "", onRate = {}, onToggleReason = {}, onTextChange = {}, onSubmit = {}, onNotNow = {})
    }

    @Test
    fun thanksHappy() = shoot("thanks_play") {
        RateThanksContent(5, "Your email app opened with the details filled in", onRateOnPlay = {}, onDone = {})
    }

    @Test
    fun issueForm() = shoot("form_issue") {
        val thumb = Bitmap.createBitmap(60, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8AA4C8.toInt()) }.asImageBitmap()
        FeedbackFormContent(
            FeedbackFormKind.ISSUE, setOf(IssueCategory.QUALITY), "The edges of receipts are cut off",
            attachments = listOf(FeedbackAttachment(Uri.EMPTY, thumb), FeedbackAttachment(Uri.EMPTY, null)),
            onToggleCategory = {}, onTextChange = {}, onAttach = {}, onRemoveAttachment = {}, onClose = {}, onSubmit = {}
        )
    }

    @Test
    fun featureFormEmpty() = shoot("form_feature") {
        FeedbackFormContent(
            FeedbackFormKind.FEATURE, emptySet(), "", attachments = emptyList(),
            onToggleCategory = {}, onTextChange = {}, onAttach = {}, onRemoveAttachment = {}, onClose = {}, onSubmit = {}
        )
    }
}
