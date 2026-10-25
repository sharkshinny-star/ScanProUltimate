package com.example.scanpro.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * Reads QR codes and barcodes from camera frames with ML Kit, on the device. After a hit it pauses until
 * [resume], so the result sheet isn't replaced while the user reads it.
 */
class BarcodeFrameAnalyzer(private val onFound: (Barcode) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    private val scanner = BarcodeScanning.getClient()
    @Volatile private var paused = false
    @Volatile private var closed = false

    fun resume() { paused = false }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (paused || closed || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (code != null && !paused && !closed) {
                    paused = true
                    onFound(code)
                }
            }
            // The frame must stay open until ML Kit has finished reading it.
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        closed = true
        scanner.close()
    }
}

/** Sends each frame to the barcode reader in QR mode and to the page detector otherwise. */
class SwitchingAnalyzer(
    private val document: ImageAnalysis.Analyzer,
    private val barcode: ImageAnalysis.Analyzer
) : ImageAnalysis.Analyzer {
    @Volatile var barcodeMode = false

    override fun analyze(image: ImageProxy) {
        if (barcodeMode) barcode.analyze(image) else document.analyze(image)
    }
}
