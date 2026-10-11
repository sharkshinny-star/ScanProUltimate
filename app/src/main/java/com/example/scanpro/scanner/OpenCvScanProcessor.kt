package com.example.scanpro.scanner

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
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, 4600)
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
}
