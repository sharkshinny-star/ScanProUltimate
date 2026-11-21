package com.example.scanpro.scanner

import android.graphics.Bitmap
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
            val detection = DocumentEdgeDetector.detect(bitmap)
            PageQuality.issues(detection) + blankIssue(bitmap, detection.quad)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** [detectCrop] and [assessQuality] from one decode and one detection, for when both are still missing. */
    fun inspect(source: File): Pair<CropQuad, Set<PageIssue>> {
        val bitmap = ImageProcessor.decodeOrientedScaled(source.absolutePath, 1600) ?: return CropQuad.DEFAULT to emptySet()
        return try {
            val detection = DocumentEdgeDetector.detect(bitmap)
            val quad = detection.quad
            (if (quad != null && detection.confidence >= 0.34f) quad else CropQuad.DEFAULT) to
                (PageQuality.issues(detection) + blankIssue(bitmap, quad))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * [PageIssue.BLANK] when the page area of [bitmap] holds (almost) nothing. The area is the detected outline's
     * bounding box, or the middle of the photo when no page was found, pulled in by a margin so the page edge and
     * the table beside it are not mistaken for ink. Sampled at about 640 px, enough to keep thin pen strokes.
     */
    private fun blankIssue(bitmap: Bitmap, quad: CropQuad?): Set<PageIssue> {
        val xs = quad?.let { listOf(it.topLeft.x, it.topRight.x, it.bottomRight.x, it.bottomLeft.x) } ?: listOf(0f, 1f)
        val ys = quad?.let { listOf(it.topLeft.y, it.topRight.y, it.bottomRight.y, it.bottomLeft.y) } ?: listOf(0f, 1f)
        val insetX = (xs.max() - xs.min()) * 0.08f
        val insetY = (ys.max() - ys.min()) * 0.08f
        val left = ((xs.min() + insetX) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = ((ys.min() + insetY) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val width = (((xs.max() - insetX) * bitmap.width).toInt() - left).coerceIn(1, bitmap.width - left)
        val height = (((ys.max() - insetY) * bitmap.height).toInt() - top).coerceIn(1, bitmap.height - top)
        var area: Bitmap? = null
        var small: Bitmap? = null
        return try {
            area = Bitmap.createBitmap(bitmap, left, top, width, height)
            val scale = minOf(1f, SAMPLE_LONG_SIDE.toFloat() / maxOf(width, height))
            small = if (scale < 1f) {
                Bitmap.createScaledBitmap(area, maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), true)
            } else area
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            val luma = ByteArray(pixels.size) { i ->
                val p = pixels[i]
                ((((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000).toByte()
            }
            if (BlankPage.isBlank(luma)) setOf(PageIssue.BLANK) else emptySet()
        } catch (_: Exception) {
            emptySet()
        } finally {
            if (small != null && small !== area && !small.isRecycled) small.recycle()
            if (area != null && area !== bitmap && !area.isRecycled) area.recycle()
        }
    }

    private const val SAMPLE_LONG_SIDE = 640

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
