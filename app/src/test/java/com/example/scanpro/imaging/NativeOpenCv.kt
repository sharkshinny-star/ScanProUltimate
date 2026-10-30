package com.example.scanpro.imaging

import java.io.File
import org.junit.Assume
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Loads the desktop build of OpenCV that is vendored with the tests (app/src/test/resources/native), so the
 * imaging code runs on the JVM against the real library. It exists for Linux x86_64 only; elsewhere the
 * imaging tests are skipped. On Linux x86_64 a failure to load is a failure, never a silent skip, so a
 * broken library can't make the tests quietly stop checking anything.
 */
internal object NativeOpenCv {
    private val supported: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux") &&
            System.getProperty("os.arch").orEmpty().lowercase().let { it == "amd64" || it == "x86_64" }

    private val loaded: Result<Unit> by lazy {
        runCatching {
            val resource = NativeOpenCv::class.java.getResource("/native/linux-x86_64/opencv_java4.bin")
                ?: error("The vendored desktop OpenCV is missing from the test resources")
            // Stored as ".bin" because the Android Gradle plugin drops ".so" files from test resources;
            // System.load takes the file by path, whatever it is called.
            System.load(File(resource.toURI()).absolutePath)
        }
    }

    /** Call from @Before: skips the test off Linux x86_64, otherwise makes sure the library is loaded. */
    fun require() {
        Assume.assumeTrue("The desktop OpenCV is vendored for Linux x86_64 only", supported)
        loaded.getOrThrow()
    }
}

/** The bytes of an 8-bit 3-channel image, row by row (R, G, B per pixel for the images used here). */
internal fun Mat.toBytes(): ByteArray {
    require(type() == CvType.CV_8UC3) { "expected an 8-bit 3-channel image" }
    return ByteArray(rows() * cols() * 3).also { get(0, 0, it) }
}

internal fun bytesToMat(bytes: ByteArray, width: Int, height: Int): Mat =
    Mat(height, width, CvType.CV_8UC3).also { it.put(0, 0, bytes) }

internal fun Byte.u(): Int = toInt() and 0xFF
