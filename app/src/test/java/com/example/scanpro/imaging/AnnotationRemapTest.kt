package com.example.scanpro.imaging

import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationRemapTest {
    private fun box(x: Float, y: Float, w: Float, h: Float) = PageAnnotation(type = AnnotationType.REDACT, x = x, y = y, width = w, height = h)
    private fun rect(l: Float, t: Float, r: Float, b: Float) =
        CropQuad(NormalizedPoint(l, t), NormalizedPoint(r, t), NormalizedPoint(r, b), NormalizedPoint(l, b))

    @Test
    fun boxFollowsItsContentWhenTheCropShrinks() {
        // Full page -> left half: a box at x 0.2..0.3 of the photo is at 0.4..0.6 of the new page.
        val moved = AnnotationRemap.remap(listOf(box(0.2f, 0.4f, 0.1f, 0.1f)), CropQuad.DEFAULT, rect(0f, 0f, 0.5f, 1f), 0, 0.75f)
        assertEquals(1, moved.size)
        assertEquals(0.4f, moved[0].x, 1e-3f)
        assertEquals(0.2f, moved[0].width, 1e-3f)
        assertEquals(0.4f, moved[0].y, 1e-3f)
        assertEquals(0.1f, moved[0].height, 1e-3f)
    }

    @Test
    fun boxWhoseContentIsCroppedAwayIsDropped() {
        val moved = AnnotationRemap.remap(listOf(box(0.7f, 0.4f, 0.1f, 0.1f)), CropQuad.DEFAULT, rect(0f, 0f, 0.5f, 1f), 0, 0.75f)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun rotationIsUndoneAndRedone() {
        // Page turned a quarter turn: a box near the photo's top-left corner sits near the page's top-right.
        val photoBox = box(0.1f, 0.1f, 0.1f, 0.1f)
        val turned = PageAnnotationTurns.clockwise(photoBox)
        val moved = AnnotationRemap.remap(listOf(turned), CropQuad.DEFAULT, rect(0f, 0f, 0.5f, 0.5f), 90, 1f)
        // In the new crop the photo box is at 0.2..0.4 both ways; turned clockwise that is x 0.6..0.8, y 0.2..0.4.
        assertEquals(0.6f, moved[0].x, 1e-3f)
        assertEquals(0.2f, moved[0].y, 1e-3f)
        assertEquals(0.2f, moved[0].width, 1e-3f)
        assertEquals(0.2f, moved[0].height, 1e-3f)
    }

    @Test
    fun perspectiveMappingRoundTrips() {
        val crop = CropQuad(NormalizedPoint(0.1f, 0.05f), NormalizedPoint(0.9f, 0.12f), NormalizedPoint(0.85f, 0.95f), NormalizedPoint(0.05f, 0.9f))
        val mapping = CropMapping(crop, 0.75f)
        val (x, y) = mapping.toSource(0.3f, 0.7f)!!
        val (u, v) = mapping.fromSource(x, y)!!
        assertEquals(0.3f, u, 1e-4f)
        assertEquals(0.7f, v, 1e-4f)
        assertEquals(0.1f, mapping.toSource(0f, 0f)!!.first, 1e-5f)
        assertEquals(0.95f, mapping.toSource(1f, 1f)!!.second, 1e-5f)
    }

    @Test
    fun curvedMappingRoundTrips() {
        val crop = rect(0.1f, 0.1f, 0.9f, 0.9f).copy(topBow = NormalizedPoint(0f, -0.06f), bottomBow = NormalizedPoint(0f, 0.04f))
        val mapping = CropMapping(crop, 0.75f)
        // The top edge's middle bulges up to y = 0.04.
        assertEquals(0.04f, mapping.toSource(0.5f, 0f)!!.second, 2e-3f)
        for ((u, v) in listOf(0.2f to 0.3f, 0.5f to 0.5f, 0.85f to 0.9f)) {
            val (x, y) = mapping.toSource(u, v)!!
            val back = mapping.fromSource(x, y)!!
            assertEquals(u, back.first, 1e-3f)
            assertEquals(v, back.second, 1e-3f)
        }
    }

    @Test
    fun unchangedCropKeepsAnnotationsAsTheyAre() {
        val items = listOf(box(0.1f, 0.2f, 0.3f, 0.1f))
        assertEquals(items, AnnotationRemap.remap(items, CropQuad.DEFAULT, CropQuad.DEFAULT, 0, 1f))
    }
}

/** The same quarter turn the app applies to mark-up when a page is rotated. */
private object PageAnnotationTurns {
    fun clockwise(a: PageAnnotation) = a.copy(x = 1f - (a.y + a.height), y = a.x, width = a.height, height = a.width)
}
