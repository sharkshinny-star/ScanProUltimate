package com.example.scanpro.scanner

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase

/**
 * Keeps the camera's target rotation equal to the screen's rotation.
 *
 * The activity handles rotation itself (it is not restarted), so the use cases bound once keep the rotation the
 * screen had at that moment. Without this, a photo taken after turning the phone carries the old orientation and
 * the page is saved sideways. Call the returned function to stop following.
 */
internal fun followDisplayRotation(context: Context, view: View, useCases: List<UseCase>): () -> Unit {
    val displays = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return {}
    fun apply() {
        val rotation = view.display?.rotation ?: return
        useCases.forEach { useCase ->
            when (useCase) {
                is Preview -> useCase.targetRotation = rotation
                is ImageCapture -> useCase.targetRotation = rotation
                is ImageAnalysis -> useCase.targetRotation = rotation
                else -> Unit
            }
        }
    }
    val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == view.display?.displayId) apply()
        }
    }
    displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    apply()
    return { displays.unregisterDisplayListener(listener) }
}
