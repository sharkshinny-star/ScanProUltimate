package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAlignmentTest {
    @Test fun horizontalPageIsAligned() {
        val quad = CropQuad(P(.10f, .14f), P(.80f, .14f), P(.80f, .85f), P(.10f, .85f))
        assertEquals(0.0, ScanAlignment.skewDegrees(quad, 800, 1000)!!, 0.01)
        assertFalse(ScanAlignment.isSkewed(quad, 800, 1000))
    }

    @Test fun tiltedPageIsFlagged() {
        // Both horizontal edges climb about 15 degrees in an 800x1000 image.
        val quad = CropQuad(P(.15f, .18f), P(.75f, .31f), P(.64f, .84f), P(.04f, .71f))
        assertTrue(ScanAlignment.isSkewed(quad, 800, 1000))
        assertTrue(ScanAlignment.skewDegrees(quad, 800, 1000)!! > 12.0)
    }

    @Test fun perspectiveTrapezoidIsNotMistakenForCameraRoll() {
        val quad = CropQuad(P(.15f, .18f), P(.79f, .29f), P(.83f, .78f), P(.12f, .87f))
        assertNull(ScanAlignment.skewDegrees(quad, 800, 1000))
        assertFalse(ScanAlignment.isSkewed(quad, 800, 1000))
    }

    @Test fun invalidDimensionsDoNotReportRotation() {
        assertNull(ScanAlignment.skewDegrees(CropQuad.DEFAULT, 0, 1000))
        assertFalse(ScanAlignment.isSkewed(CropQuad.DEFAULT, 800, 0))
    }

    @Test fun noticeIsAdvisoryNotCaptureBlock() {
        val quad = CropQuad(P(.15f, .18f), P(.75f, .31f), P(.64f, .84f), P(.04f, .71f))
        val detection = DocumentDetection(quad, .9f, 180.0, 130.0, 800, 1000, Guidance.READY)
        assertTrue(detection.isSkewed)
        assertTrue(detection.isReady)
        // Live hint only: the crop straightens a tilted page, so the captured photo is not flagged.
        assertTrue(PageQuality.issues(detection).isEmpty())
    }
}
