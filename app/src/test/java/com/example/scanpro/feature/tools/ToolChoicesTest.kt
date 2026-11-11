package com.example.scanpro.feature.tools

import com.example.scanpro.data.OcrScript
import org.junit.Assert.assertEquals
import org.junit.Test

/** Make searchable and PDF to text start on the script of the app's language, not always on Latin. */
class ToolChoicesTest {

    @Test
    fun `Hindi and other Devanagari languages start on Devanagari`() {
        assertEquals(OcrScript.DEVANAGARI, defaultOcrScript("hi"))
        assertEquals(OcrScript.DEVANAGARI, defaultOcrScript("hi-IN"))
        assertEquals(OcrScript.DEVANAGARI, defaultOcrScript("mr"))
    }

    @Test
    fun `scripts ML Kit cannot read start on Latin`() {
        // There is no ML Kit model for Tamil, Telugu, Kannada or Malayalam script.
        listOf("ta", "te", "kn", "ml", "en", "en_GB", "").forEach { assertEquals(it, OcrScript.LATIN, defaultOcrScript(it)) }
    }

    @Test
    fun `East Asian languages start on their own script`() {
        assertEquals(OcrScript.CHINESE, defaultOcrScript("zh-Hans"))
        assertEquals(OcrScript.JAPANESE, defaultOcrScript("ja"))
        assertEquals(OcrScript.KOREAN, defaultOcrScript("KO"))
    }
}
