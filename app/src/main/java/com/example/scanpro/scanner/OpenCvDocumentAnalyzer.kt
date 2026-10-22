package com.example.scanpro.scanner

import com.example.scanpro.imaging.OpenCvRuntime
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.scanpro.data.CropQuad
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.hypot

class OpenCvDocumentAnalyzer(
    private val onDetection: (DocumentDetection, Boolean) -> Unit,
    private val onAutoCapture: () -> Unit
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())
    private var previousQuad: CropQuad? = null
    private var stableFrames = 0
    private var lastAutoCaptureAt = 0L
    // Re-armed only when the page has really gone (lost for several frames) or a clearly different page
    // is in view, so a held page is captured once even if one frame is blurry.
    private var armed = true
    private var lostFrames = 0
    private var capturedQuad: CropQuad? = null

    @Volatile
    var autoCaptureEnabled: Boolean = true

    override fun analyze(image: ImageProxy) {
        // Analysis runs on the camera executor, so waiting here for a still-loading OpenCV never blocks the UI.
        OpenCvRuntime.ensureLoaded()
        try {
            val gray = image.toRotatedGrayMat()
            try {
                val result = DocumentEdgeDetector.detect(gray)
                val stable = updateStability(result)
                main.post { onDetection(result, stable) }

                // Monotonic clock: unaffected by the user or the network changing the wall-clock time.
                val now = android.os.SystemClock.elapsedRealtime()
                lostFrames = if (result.isReady) 0 else lostFrames + 1
                if (!armed) {
                    val captured = capturedQuad
                    val current = result.quad
                    val differentPage = captured != null && current != null && result.isReady &&
                        averageCornerMovement(captured, current) > 0.12f
                    if (lostFrames >= 8 || differentPage) armed = true
                }
                if (autoCaptureEnabled && armed && stable && result.isReady && now - lastAutoCaptureAt > 2200L) {
                    armed = false
                    capturedQuad = result.quad
                    lastAutoCaptureAt = now
                    stableFrames = 0
                    main.post(onAutoCapture)
                }
            } finally {
                gray.release()
            }
        } catch (_: Throwable) {
            // Analyzer failures should never kill the camera pipeline.
        } finally {
            image.close()
        }
    }

    private fun updateStability(result: DocumentDetection): Boolean {
        val quad = result.quad
        val previous = previousQuad
        if (quad == null || previous == null || !result.isReady) {
            previousQuad = quad
            stableFrames = 0
            return false
        }

        val movement = averageCornerMovement(previous, quad)
        stableFrames = if (movement < 0.014f) stableFrames + 1 else 0
        previousQuad = quad
        return stableFrames >= 7
    }

    private fun averageCornerMovement(a: CropQuad, b: CropQuad): Float {
        val pairs = listOf(
            a.topLeft to b.topLeft,
            a.topRight to b.topRight,
            a.bottomRight to b.bottomRight,
            a.bottomLeft to b.bottomLeft
        )
        return pairs.sumOf { (p1, p2) -> hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()) }
            .div(pairs.size)
            .toFloat()
    }
}

private fun ImageProxy.toRotatedGrayMat(): Mat {
    val plane = planes[0]
    val buffer = plane.buffer
    val width = width
    val height = height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val bytes = ByteArray(width * height)

    if (pixelStride == 1 && rowStride == width) {
        buffer.rewind()
        buffer.get(bytes, 0, minOf(bytes.size, buffer.remaining()))
    } else {
        val row = ByteArray(rowStride)
        var offset = 0
        buffer.rewind()
        for (y in 0 until height) {
            val length = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, length)
            var xOffset = 0
            repeat(width) {
                bytes[offset++] = row[xOffset]
                xOffset += pixelStride
            }
        }
    }

    val source = Mat(height, width, CvType.CV_8UC1)
    val rotated = Mat()
    try {
        source.put(0, 0, bytes)
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) {
            rotated.release()
            return source
        }
        when (rotation) {
            90 -> Core.rotate(source, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(source, rotated, Core.ROTATE_180)
            270 -> Core.rotate(source, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> source.copyTo(rotated)
        }
        source.release()
        return rotated
    } catch (error: Throwable) {
        // Release native memory on failure; the caller's catch would otherwise leak it every frame.
        source.release()
        rotated.release()
        throw error
    }
}
