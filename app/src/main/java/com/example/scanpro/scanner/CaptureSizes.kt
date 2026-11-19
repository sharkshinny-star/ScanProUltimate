package com.example.scanpro.scanner

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Photo sizes for the two capture settings, and whether HD is worth offering on this camera (2026-11-17).
 *
 * Checked on the owner's phone (OnePlus CPH2569): its main camera gives apps photos of at most 4096x3072 (12.6 MP).
 * Standard capture asked for "about 4000x3000 or the next size down", which skipped 4096x3072 and took 3456x2592
 * (9 MP); HD then took 4096x3072. So on that phone standard photos were a third smaller than the camera allows, and HD
 * only got back what standard should have had. Standard now asks for 4096x3072, and HD is offered only where the camera
 * really has larger photos (a 48 or 50 MP sensor that exposes its full size).
 */
internal object CaptureSizes {
    /** Standard capture: about 12 MP, the full binned size of most phone cameras. */
    const val STANDARD_WIDTH = 4096
    const val STANDARD_HEIGHT = 3072

    /** HD capture: up to about 48 MP. */
    const val HD_WIDTH = 8000
    const val HD_HEIGHT = 6000

    private const val STANDARD_PIXELS = STANDARD_WIDTH.toLong() * STANDARD_HEIGHT

    /** HD is offered when the camera has a 4:3 photo at least 30% larger than the standard one. */
    fun offersHd(sizes: List<Pair<Int, Int>>): Boolean = sizes.any { (w, h) ->
        val long = maxOf(w, h).toDouble()
        val short = minOf(w, h).toDouble()
        short > 0 && kotlin.math.abs(long / short - 4.0 / 3.0) < 0.02 && w.toLong() * h > STANDARD_PIXELS * 13 / 10
    }

    /** Every JPEG size the camera [selector] picks offers apps, including the slow high-resolution ones. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun jpegSizes(provider: ProcessCameraProvider, selector: CameraSelector): List<Pair<Int, Int>> = runCatching {
        val info = selector.filter(provider.availableCameraInfos).firstOrNull() ?: return emptyList()
        val map = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return emptyList()
        val normal = map.getOutputSizes(ImageFormat.JPEG).orEmpty().toList()
        val high = if (Build.VERSION.SDK_INT >= 23) map.getHighResolutionOutputSizes(ImageFormat.JPEG).orEmpty().toList() else emptyList()
        (normal + high).map { it.width to it.height }
    }.getOrDefault(emptyList())
}
