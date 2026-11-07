package com.example.scanpro.scanner

import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.ImageProcessor
import java.io.File

object OpenCvScanProcessor {
    data class Result(
        val output: File,
        val detection: DocumentDetection?,
        val perspectiveCorrected: Boolean
    )

    /**
     * Detects the document again on a much larger image after capture. This avoids using
     * low-resolution preview coordinates for the final PDF.
     */
    fun correctCapturedPage(source: File, destination: File): Result {
        destination.parentFile?.mkdirs()
        // Detection runs on a ~900px working copy, so a full-resolution decode here only costs memory.
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, 1600)
        val detection = try {
            bitmap?.let { runCatching { DocumentEdgeDetector.detect(it) }.getOrNull() }
        } finally {
            if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        }
        val crop = detection?.quad

        val corrected = crop != null && detection.confidence >= 0.34f && runCatching {
            ImageProcessor.cropSourceToNewFile(source, destination, crop)
            true
        }.getOrDefault(false)

        if (!corrected) source.copyTo(destination, overwrite = true)
        return Result(destination, detection, corrected)
    }

    /** Outline of the page in a captured photo, or the whole frame when nothing convincing is found. */
    /**
     * What to point out about a captured page before it is saved: blurred, glare or too dark ([PageQuality]), judged
     * on the photo the way the live camera judges its frames. Empty when the photo can't be read.
     */
    fun assessQuality(source: File): Set<PageIssue> {
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, 1600) ?: return emptySet()
        return try {
            PageQuality.issues(DocumentEdgeDetector.detect(bitmap))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun detectCrop(source: File): CropQuad {
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, 1600) ?: return CropQuad.DEFAULT
        return try {
            val detection = DocumentEdgeDetector.detect(bitmap)
            val quad = detection.quad
            if (quad != null && detection.confidence >= 0.34f) quad else CropQuad.DEFAULT
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
