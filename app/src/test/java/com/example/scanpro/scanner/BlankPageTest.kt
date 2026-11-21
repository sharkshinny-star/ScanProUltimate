package com.example.scanpro.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlankPageTest {
    private val width = 200
    private val height = 150

    /** A sheet at [paper] with mild sensor noise, plus a [shade] gradient across it, and the given dark [marks]. */
    private fun sheet(paper: Int, shade: Int = 0, marks: Int = 0): ByteArray {
        val random = Random(7)
        val luma = ByteArray(width * height) { i ->
            val gradient = shade * (i % width) / width
            (paper - gradient + random.nextInt(-4, 5)).coerceIn(0, 255).toByte()
        }
        repeat(marks) { luma[it * 3] = 40 }
        return luma
    }

    @Test fun anEvenSheetIsBlank() = assertTrue(BlankPage.isBlank(sheet(200)))

    @Test fun aShadedButEmptySheetIsStillBlank() = assertTrue(BlankPage.isBlank(sheet(200, shade = 28)))

    @Test fun aDimButEmptySheetIsStillBlank() = assertTrue(BlankPage.isBlank(sheet(120)))

    @Test fun textMarksMakeItNotBlank() = assertFalse(BlankPage.isBlank(sheet(200, marks = 900)))

    @Test fun aSmallStampIsContent() = assertFalse(BlankPage.isBlank(sheet(200, marks = 60)))

    @Test fun aSingleSpeckIsNotContent() = assertTrue(BlankPage.isBlank(sheet(200, marks = 4)))

    @Test fun aDarkFrameIsNeverCalledBlank() = assertFalse(BlankPage.isBlank(sheet(40)))

    @Test fun tooFewSamplesSayNothing() = assertFalse(BlankPage.isBlank(ByteArray(100) { 200.toByte() }))

    @Test fun anEmptyPageOfBrightWhiteIsBlank() = assertTrue(BlankPage.isBlank(ByteArray(width * height) { 255.toByte() }))
}
