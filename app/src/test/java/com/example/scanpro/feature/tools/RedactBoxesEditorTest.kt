package com.example.scanpro.feature.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The drag-to-box mapping of the Redact areas editor (F174), pure maths with no Compose. */
class RedactBoxesEditorTest {
    private val size = Size(800f, 1000f)

    @Test
    fun aDragBecomesANormalisedBox() {
        val box = dragToBox(3, Offset(80f, 100f), Offset(400f, 600f), size)!!
        assertEquals(3, box.page)
        assertEquals(0.1f, box.left, 0.001f)
        assertEquals(0.1f, box.top, 0.001f)
        assertEquals(0.5f, box.right, 0.001f)
        assertEquals(0.6f, box.bottom, 0.001f)
    }

    @Test
    fun anUpwardDragAndAnOverhangStillWork() {
        val box = dragToBox(0, Offset(900f, 1200f), Offset(400f, 500f), size)!!
        assertEquals(0.5f, box.left, 0.001f)
        assertEquals(0.5f, box.top, 0.001f)
        assertEquals(1f, box.right, 0.001f)
        assertEquals(1f, box.bottom, 0.001f)
    }

    @Test
    fun aSlipOfTheFingerIsNoBox() {
        assertNull(dragToBox(0, Offset(100f, 100f), Offset(102f, 500f), size))
        assertNull(dragToBox(0, Offset(100f, 100f), Offset(500f, 103f), size))
        assertNull(dragToBox(0, Offset(100f, 100f), Offset(500f, 500f), Size(0f, 0f)))
    }
}
