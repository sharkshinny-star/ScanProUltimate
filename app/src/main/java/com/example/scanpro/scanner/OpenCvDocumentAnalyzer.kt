package com.example.scanpro.scanner

import com.example.scanpro.imaging.OpenCvRuntime
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.scanpro.data.CropQuad
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.hypot

class OpenCvDocumentAnalyzer(
    /** Each analysed frame: the detection, whether the page is steady, and how far along the steadiness count is (0..1). */
    private val onDetection: (DocumentDetection, Boolean, Float) -> Unit,
    private val onAutoCapture: () -> Unit,
    /** Called once if the native detector cannot be loaded on this device; frames are then dropped. */
    private val onUnavailable: () -> Unit = {},
) : ImageAnalysis.Analyzer {
    private var reportedUnavailable = false
    private var lastFrameSize = 0L
    private var failedFrames = 0
    private val main = Handler(Looper.getMainLooper())
    private var previousQuad: CropQuad? = null
    private val steadiness = Steadiness()
    private var lastAutoCaptureAt = 0L
    // Re-armed only when the page has really gone (lost for several frames) or a clearly different page
    // is in view, so a held page is captured once even if one frame is blurry.
    private var armed = true
    private var lostFrames = 0
    private var capturedQuad: CropQuad? = null
    // R32: what the page just captured looked like, and the sharpness of the recent steady frames.
    private var capturedSignature: FloatArray? = null
    private val recentBlur = ArrayDeque<Double>()

    @Volatile
    var autoCaptureEnabled: Boolean = true

    /** False while nothing is shown for the result (No-detect mode, review step): frames are dropped unread. */
    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var closed = false

    /** Stops callbacks after the screen is gone; a frame finishing late must not touch disposed state. */
    fun close() { closed = true }

    // Reused across frames: one allocation per frame of the full image was most of the analyser's garbage.
    private var frameBytes = ByteArray(0)
    private var rowBytes = ByteArray(0)

    internal fun frameBuffer(size: Int): ByteArray {
        if (frameBytes.size != size) frameBytes = ByteArray(size)
        return frameBytes
    }

    internal fun rowBuffer(size: Int): ByteArray {
        if (rowBytes.size < size) rowBytes = ByteArray(size)
        return rowBytes
    }

    private var uBytes = ByteArray(0)
    private var vBytes = ByteArray(0)
    private var chromaBytes = ByteArray(0)

    private var planeBytes = ByteArray(0)

    /**
     * The frame's colour-strength map ([LiveChroma]) and its two colour-difference planes (one 2-channel Mat), each
     * turned upright and cut to the viewport like the grey image; either is null when the frame has no usable colour
     * planes. Reads the luma that [grayMatOf] left in the frame buffer, so it must be called after it, for the same frame.
     */
    private fun colourMatsOf(image: ImageProxy): Pair<Mat?, Mat?> {
        val planes = image.planes
        if (planes.size < 3 || frameBytes.size != image.width * image.height) return null to null
        fun read(buffer: java.nio.ByteBuffer, into: ByteArray): ByteArray {
            buffer.rewind()
            val bytes = if (into.size == buffer.remaining()) into else ByteArray(buffer.remaining())
            buffer.get(bytes, 0, bytes.size)
            return bytes
        }
        uBytes = read(planes[1].buffer, uBytes)
        vBytes = read(planes[2].buffer, vBytes)
        val width = LiveChroma.width(image.width)
        val height = LiveChroma.height(image.height)
        if (width <= 0 || height <= 0) return null to null
        val rowStride = planes[1].rowStride
        val pixelStride = planes[1].pixelStride
        if (chromaBytes.size != width * height) chromaBytes = ByteArray(width * height)
        val chroma = if (!LiveChroma.compute(frameBytes, image.width, image.height, uBytes, vBytes, rowStride, pixelStride, chromaBytes)) null
        else runCatching { uprightMat(image, height, width, CvType.CV_8UC1, chromaBytes) }.getOrNull()
        if (planeBytes.size != 2 * width * height) planeBytes = ByteArray(2 * width * height)
        val colourPlanes = if (!LiveChroma.planes(image.width, image.height, uBytes, vBytes, rowStride, pixelStride, planeBytes)) null
        else runCatching { uprightMat(image, height, width, CvType.CV_8UC2, planeBytes) }.getOrNull()
        return chroma to colourPlanes
    }

    /** [bytes] as a [rows] x [cols] Mat of [type] (a quarter-size map of the frame), turned upright and cut to the viewport. */
    private fun uprightMat(image: ImageProxy, rows: Int, cols: Int, type: Int, bytes: ByteArray): Mat {
        val source = Mat(rows, cols, type)
        try {
            source.put(0, 0, bytes)
            val rotateCode = when (image.imageInfo.rotationDegrees) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> -1
            }
            if (rotateCode < 0) return source.croppedToViewport(image, LiveChroma.STEP)
            val rotated = Mat()
            try {
                Core.rotate(source, rotated, rotateCode)
            } catch (error: Throwable) {
                rotated.release()
                throw error
            }
            source.release()
            return rotated.croppedToViewport(image, LiveChroma.STEP)
        } catch (error: Throwable) {
            source.release()
            throw error
        }
    }

    override fun analyze(image: ImageProxy) {
        if (!enabled || closed) {
            image.close()
            return
        }
        // Analysis runs on the camera executor, so waiting here for a still-loading OpenCV never blocks the UI.
        if (!OpenCvRuntime.ensureLoaded()) {
            if (!reportedUnavailable) {
                reportedUnavailable = true
                main.post(onUnavailable)
            }
            image.close()
            return
        }
        try {
            // Log the delivered analysis size once per change, to confirm on a device that CameraX honoured the bounded selector.
            val frameSize = (image.width.toLong() shl 32) or image.height.toLong()
            if (frameSize != lastFrameSize) {
                lastFrameSize = frameSize
                Log.d("ScanProAnalyzer", "CameraX analysis frame: ${image.width}x${image.height}")
            }
            // Only the part of the frame the preview and the saved photo show is analysed, so the corners that
            // are found describe exactly the pixels the user sees and the capture keeps.
            val gray = toRotatedGrayMat(image).croppedToViewport(image)
            // The colour-strength map from the same frame (R57); without it the detector works on brightness alone, as before.
            // Its two colour-difference planes add colour edges (2026-11-19): a card's coloured band or a coloured page
            // against a surface of the same brightness has no brightness edge at all.
            val (chroma, colourPlanes) = runCatching { colourMatsOf(image) }.getOrDefault(null to null)
            try {
                val raw = DocumentEdgeDetector.detect(gray, chroma, colourPlanes)
                var result = raw.copy(guidance = steadyGuidance(raw.guidance))
                // Monotonic clock: unaffected by the user or the network changing the wall-clock time.
                val now = android.os.SystemClock.elapsedRealtime()
                val stable = updateStability(result, now)
                val progress = steadiness.progress(now)
                // Sharpness of the steady frames so far, so the shutter can wait for a sharp one.
                if (stable) { recentBlur.addLast(result.blurScore); while (recentBlur.size > SharpnessGate.WINDOW) recentBlur.removeFirst() } else recentBlur.clear()

                // Only frames with no page at all count as "the page left"; a blurry or dark frame of the same page doesn't.
                lostFrames = if (result.quad != null) 0 else lostFrames + 1
                if (!armed) {
                    val captured = capturedQuad
                    val current = result.quad
                    val differentPage = captured != null && current != null && result.isReady &&
                        (averageCornerMovement(captured, current) > 0.12f)
                    if (lostFrames >= 8 || differentPage) armed = true
                }
                val candidate = autoCaptureEnabled && armed && stable && result.isReady && now - lastAutoCaptureAt > 2200L
                var duplicate = false
                var signature: FloatArray? = null
                if (candidate) {
                    // The same page back in view (put down and picked up, or never moved) is not captured twice.
                    signature = result.quad?.let { runCatching { PageSignature.of(gray, it) }.getOrNull() }
                    val last = capturedSignature
                    duplicate = signature != null && last != null && PageSignature.isSamePage(signature, last)
                    if (duplicate) result = result.copy(guidance = Guidance.SAME_PAGE)
                }
                main.post { if (!closed) onDetection(result, stable, progress) }

                if (candidate && !duplicate && SharpnessGate.shouldFire(recentBlur, result.blurScore)) {
                    armed = false
                    capturedQuad = result.quad
                    capturedSignature = signature
                    lastAutoCaptureAt = now
                    steadiness.reset()
                    recentBlur.clear()
                    main.post { if (!closed) onAutoCapture() }
                }
            } finally {
                gray.release()
                chroma?.release()
                colourPlanes?.release()
            }
        } catch (error: Throwable) {
            // Analyser failures should never kill the camera pipeline. One bad frame is dropped quietly, but a
            // detector that keeps failing is logged (the first time, then every 100th) so it shows up in debug.
            failedFrames++
            if (failedFrames == 1 || failedFrames % 100 == 0) {
                Log.w("ScanProAnalyzer", "Document detector failed on $failedFrames frame(s)", error)
            }
        } finally {
            image.close()
        }
    }

    private var shownGuidance: Guidance? = null
    private var candidateGuidance: Guidance? = null
    private var candidateFrames = 0

    /**
     * The hint shown to the user changes only once the detector has said the same thing for a few frames,
     * so one blurry or dark frame doesn't flash "Hold steady" over a page that is about to be captured.
     */
    private fun steadyGuidance(latest: Guidance): Guidance {
        val shown = shownGuidance
        if (latest == shown) {
            candidateFrames = 0
            return shown
        }
        if (latest == candidateGuidance) candidateFrames++ else { candidateGuidance = latest; candidateFrames = 1 }
        if (candidateFrames >= 3 || shown == null) {
            shownGuidance = latest
            candidateFrames = 0
            return latest
        }
        return shown
    }

    private fun updateStability(result: DocumentDetection, now: Long): Boolean {
        val quad = result.quad
        val previous = previousQuad
        previousQuad = quad
        if (quad == null || previous == null || !result.isReady) {
            steadiness.reset()
            return false
        }
        return steadiness.onFrame(still = averageCornerMovement(previous, quad) < 0.014f, now = now)
    }

    private fun averageCornerMovement(a: CropQuad, b: CropQuad): Float {
        val pairs = listOf(
            a.topLeft to b.topLeft,
            a.topRight to b.topRight,
            a.bottomRight to b.bottomRight,
            a.bottomLeft to b.bottomLeft,
        )
        return (pairs.sumOf { (p1, p2) -> hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()) } / pairs.size)
            .toFloat()
    }
}

/**
 * How long the page outline has held still. The page counts as steady after [HOLD_MS] of still frames, and at least
 * [MIN_FRAMES] of them. It used to be seven frames, however long those took: about a quarter of a second on a fast
 * phone and over a second on a slow one, so the shutter felt twitchy on one and stuck on the other. Counting time
 * gives every phone the same hold, and the frame floor keeps a slow phone from firing on a single lucky frame.
 */
internal class Steadiness {
    private var stillSince = -1L
    private var frames = 0

    /** Records one analysed frame and says whether the page has now held still long enough. */
    fun onFrame(still: Boolean, now: Long): Boolean {
        if (!still) {
            reset()
            return false
        }
        if (frames == 0) stillSince = now
        frames++
        return isSteady(now)
    }

    fun reset() {
        frames = 0
        stillSince = -1L
    }

    /** 0..1 for the shutter ring: how far along the hold is. */
    fun progress(now: Long): Float {
        if (frames == 0) return 0f
        val byTime = (now - stillSince).toFloat() / HOLD_MS
        val byFrames = frames.toFloat() / MIN_FRAMES
        return minOf(byTime, byFrames).coerceIn(0f, 1f)
    }

    private fun isSteady(now: Long) = frames >= MIN_FRAMES && now - stillSince >= HOLD_MS

    companion object {
        const val HOLD_MS = 350L
        const val MIN_FRAMES = 4
    }
}

/** Cuts this upright image to the part of the frame the preview shows; [step] is how many frame pixels one of its pixels covers. */
private fun Mat.croppedToViewport(image: ImageProxy, step: Int = 1): Mat {
    val rect = image.cropRect
    val full = rotateBox(IntBox(rect.left, rect.top, rect.right, rect.bottom), image.width, image.height, image.imageInfo.rotationDegrees)
    val box = if (step == 1) full else IntBox(full.left / step, full.top / step, full.right / step, full.bottom / step)
    val width = box.right - box.left
    val height = box.bottom - box.top
    val coversFrame = box.left <= 0 && box.top <= 0 && width >= cols() && height >= rows()
    if (coversFrame || width <= 0 || height <= 0 || box.left + width > cols() || box.top + height > rows()) return this
    val view = Mat(this, org.opencv.core.Rect(box.left, box.top, width, height))
    val copy = view.clone()
    view.release()
    release()
    return copy
}

private fun OpenCvDocumentAnalyzer.toRotatedGrayMat(image: ImageProxy): Mat = grayMatOf(image)

/** The Y plane as an upright single-channel Mat, through the analyser's reusable buffers. */
private fun OpenCvDocumentAnalyzer.grayMatOf(image: ImageProxy): Mat = with(image) {
    val plane = planes[0]
    val buffer = plane.buffer
    val width = width
    val height = height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val bytes = frameBuffer(width * height)

    if (pixelStride == 1 && rowStride == width) {
        buffer.rewind()
        buffer.get(bytes, 0, minOf(bytes.size, buffer.remaining()))
    } else {
        val row = rowBuffer(rowStride)
        var offset = 0
        buffer.rewind()
        repeat(height) {
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
