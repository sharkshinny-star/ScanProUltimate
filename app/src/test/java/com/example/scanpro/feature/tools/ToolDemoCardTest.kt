package com.example.scanpro.feature.tools

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.FirstRunHints
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "How it works" on a tool's page: open on a new user's first visit to that tool, folded on every later visit and for
 * someone who updated from an older version, until it is tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ToolDemoCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = app.getString(id)

    /** A counter that throws the card away and composes a fresh one, as leaving and reopening the tool does. */
    private var visit by mutableIntStateOf(0)

    @Before
    fun freshInstall() {
        app.getSharedPreferences("first_run_hints", 0).edit().clear().commit()
    }

    private fun show(toolId: String = "merge") {
        compose.setContent { ScanProTheme { key(visit) { ToolDemoCard(toolId) } } }
    }

    private fun assertShown(shown: Boolean) {
        compose.waitForIdle()
        compose.onAllNodes(hasStateDescription(text(R.string.tool_demo_shown))).assertCountEquals(if (shown) 1 else 0)
        compose.onAllNodes(hasStateDescription(text(R.string.tool_demo_hidden))).assertCountEquals(if (shown) 0 else 1)
    }

    @Test
    fun `open on a new user's first visit, folded on the next`() {
        FirstRunHints.arm(app)
        show()
        assertShown(true)

        visit++
        assertShown(false)
    }

    @Test
    fun `each tool has its own first visit`() {
        FirstRunHints.arm(app)
        FirstRunHints.markSeen(app, FirstRunHints.demoKey("split"))
        show("merge")
        assertShown(true)
    }

    @Test
    fun `folded for someone who updated from an older version`() {
        // Not armed: the welcome was finished before the introductions existed.
        show()
        assertShown(false)
    }

    @Test
    fun `a tap opens and closes it`() {
        show()
        assertShown(false)
        compose.onNodeWithText(text(R.string.tool_demo_how_it_works)).performClick()
        assertShown(true)
        compose.onNodeWithText(text(R.string.tool_demo_how_it_works)).performClick()
        assertShown(false)
    }

    @Test
    fun `Show introductions again opens it once more`() {
        FirstRunHints.arm(app)
        show()
        assertShown(true)
        FirstRunHints.reset(app)
        visit++
        assertShown(true)
    }
}
