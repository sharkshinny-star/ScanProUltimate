package com.example.scanpro.imaging

import org.opencv.android.OpenCVLoader

/**
 * Loads OpenCV's native library once. Loading takes a noticeable moment, so the app starts it on a
 * background thread at launch instead of blocking the first frame; anything that needs OpenCV calls
 * [ensureLoaded] first and, if the load is still running, waits for it (always off the main thread).
 */
object OpenCvRuntime {
    private val loaded: Boolean by lazy { OpenCVLoader.initLocal() }

    fun ensureLoaded(): Boolean = loaded

    fun preloadInBackground() {
        Thread({ ensureLoaded() }, "opencv-init").start()
    }
}
