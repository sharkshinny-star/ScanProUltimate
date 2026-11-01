package com.example.scanpro.feature.document

import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.JsonCodec
import com.example.scanpro.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class InkAnnotationsTest {
    private val blue = 0xFF1A49C9.toInt()
    private val red = 0xFFD32F2F.toInt()

    @Test
    fun `one box per colour and width, padded by half the pen width`() {
        val strokes = listOf(
            InkStroke(listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.4f, 0.5f)), blue, 0.01f),
            InkStroke(listOf(NormalizedPoint(0.6f, 0.6f)), blue, 0.01f),
            InkStroke(listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.2f)), red, 0.01f)
        )
        val ink = inkAnnotations(strokes, pageAspect = 0.5f)
        assertEquals(2, ink.size)
        val b = ink.first { it.color == blue }
        assertEquals(AnnotationType.INK, b.type)
        assertEquals(0.2f - 0.005f, b.x, 1e-5f)
        assertEquals(0.3f - 0.0025f, b.y, 1e-5f)
        assertEquals(0.6f + 0.005f - b.x, b.width, 1e-5f)
        assertEquals(2, b.strokes.size)
        // Points are stored relative to the box, so the first point lands just inside its top-left corner.
        assertEquals(0.005f / b.width, b.strokes[0][0].x, 1e-4f)
    }

    @Test
    fun `ink survives the journal and database encoding`() {
        val ink = inkAnnotations(listOf(InkStroke(listOf(NormalizedPoint(0.3f, 0.3f), NormalizedPoint(0.5f, 0.4f)), blue, 0.008f)), 0.7f)
        val back = JsonCodec.decodeAnnotations(JsonCodec.encodeAnnotations(ink)).single()
        assertEquals(AnnotationType.INK, back.type)
        assertEquals(blue, back.color)
        assertEquals(0.008f, back.brushSize, 1e-6f)
        assertEquals(ink.single().strokes[0][1].x, back.strokes[0][1].x, 1e-5f)
    }
}
