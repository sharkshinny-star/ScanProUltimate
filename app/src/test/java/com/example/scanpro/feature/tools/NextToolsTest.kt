package com.example.scanpro.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Continue with this file" on a tool result: which tools are offered for which kind of output. */
class NextToolsTest {

    @Test
    fun `a PDF result offers PDF tools but not the one just used`() {
        val offered = nextToolsFor("pdf", justUsed = "compress").map { it.id }
        assertFalse("compress" in offered)
        assertEquals(listOf("protect", "sign", "watermark", "page_numbers", "organize", "split", "pdf2img"), offered)
    }

    @Test
    fun `every offered tool is a real tile`() {
        // A renamed tile id would otherwise drop out of the row without anyone noticing.
        assertEquals(8, nextToolsFor("PDF", justUsed = null).size)
        assertEquals(4, nextToolsFor("jpg", justUsed = null).size)
    }

    @Test
    fun `a picture result offers the image tools`() {
        val offered = nextToolsFor("png", justUsed = "resize_img").map { it.id }
        assertEquals(listOf("compress_img", "crop_img", "convert_img"), offered)
    }

    @Test
    fun `a zip or an office file offers nothing`() {
        assertTrue(nextToolsFor("zip", justUsed = "split").isEmpty())
        assertTrue(nextToolsFor("docx", justUsed = "pdf2text").isEmpty())
    }
}
