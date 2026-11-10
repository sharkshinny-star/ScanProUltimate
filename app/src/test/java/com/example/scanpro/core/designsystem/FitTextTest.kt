package com.example.scanpro.core.designsystem

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R46: a label too long for its space shrinks to fit on one line; one that fits is left alone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class FitTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.last()
    }

    @Test
    fun aLongLabelShrinksToOneLineInsteadOfBeingCut() {
        val label = "A very long label that cannot fit"
        compose.setContent {
            MaterialTheme { Box(Modifier.width(90.dp)) { FitText(label, style = TextStyle(fontSize = 16.sp)) } }
        }
        compose.waitForIdle()
        val layout = layoutOf(label)
        assertEquals(1, layout.lineCount)
        assertTrue("shrunk below the given size", layout.layoutInput.style.fontSize < 16.sp)
    }

    @Test
    fun aLabelThatFitsKeepsItsSize() {
        compose.setContent {
            MaterialTheme { Box(Modifier.width(200.dp)) { FitText("Files", style = TextStyle(fontSize = 16.sp)) } }
        }
        compose.waitForIdle()
        val layout = layoutOf("Files")
        assertEquals(16.sp, layout.layoutInput.style.fontSize)
        assertFalse(layout.didOverflowWidth)
    }

    @Test
    fun theSizeIsWorkedOutAgainWhenTheTextChanges() {
        var text by mutableStateOf("A very long label that cannot fit")
        compose.setContent {
            MaterialTheme { Box(Modifier.width(90.dp)) { FitText(text, style = TextStyle(fontSize = 16.sp)) } }
        }
        compose.waitForIdle()
        text = "Hi"
        compose.waitForIdle()
        assertEquals("a short text after a long one is back at full size", 16.sp, layoutOf("Hi").layoutInput.style.fontSize)
    }
}
