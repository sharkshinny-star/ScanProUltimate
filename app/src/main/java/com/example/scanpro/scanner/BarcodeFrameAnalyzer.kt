package com.example.scanpro.scanner

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.scanpro.BuildConfig
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * Reads QR codes and barcodes from camera frames with ML Kit, on the device. After a hit it pauses until
 * [resume], so the result sheet isn't replaced while the user reads it. If the reader can't run (its model
 * hasn't been downloaded by Play services yet), [onUnavailable] is called once instead of failing silently.
 */
class BarcodeFrameAnalyzer(
    private val context: Context,
    private val onFound: (Barcode) -> Unit,
    private val onUnavailable: () -> Unit = {}
) : ImageAnalysis.Analyzer, Closeable {
    // Created on first use: document mode never needs it, and creating it costs time before the preview.
    private val scanner by lazy { BarcodeScanning.getClient() }
    @Volatile private var paused = false
    @Volatile private var closed = false
    @Volatile private var reportedUnavailable = false
    @Volatile private var requestedDownload = false

    fun resume() { paused = false }

    /**
     * Reads a code from a picture (a screenshot of a ticket or a Wi-Fi card, say), like Lens's gallery button.
     * Calls [onResult] with the first code found, or null when there is none or the picture can't be read.
     */
    fun scanImage(uri: android.net.Uri, onResult: (Barcode?) -> Unit) {
        paused = true
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        // Decoding a large photo takes a moment: off the main thread. ML Kit's listeners run on the main thread.
        kotlin.concurrent.thread(name = "qr-image") {
            val input = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (input == null) {
                main.post { onResult(null) }
                return@thread
            }
            scanner.process(input)
                .addOnSuccessListener { codes -> onResult(codes.firstOrNull { !it.rawValue.isNullOrEmpty() }) }
                .addOnFailureListener { onResult(null) }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (paused || closed || media == null) {
            image.close()
            return
        }
        if (!requestedDownload && !BuildConfig.BUNDLED_ML) {
            // Normally fetched at install time; asking again is harmless and covers installs where it wasn't.
            requestedDownload = true
            runCatching { ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build()) }
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
            .addOnFailureListener { error ->
                val unavailable = (error as? MlKitException)?.errorCode == MlKitException.UNAVAILABLE
                if (unavailable && !reportedUnavailable && !closed) {
                    reportedUnavailable = true
                    onUnavailable()
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
