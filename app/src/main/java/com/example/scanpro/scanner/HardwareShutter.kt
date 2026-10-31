package com.example.scanpro.scanner

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Volume-button shutter. The activity reports presses here while the scanner says it is showing; the
 * scanner takes a photo for each one. Elsewhere in the app the volume keys keep their usual meaning.
 */
object HardwareShutter {
    /** True while the camera screen is up and can take a photo. */
    @Volatile var active: Boolean = false
    /** Bumped on each press; the scanner reacts to changes. */
    val presses = MutableStateFlow(0)

    /** Returns true when the press was taken as a shutter. */
    fun onVolumeKey(): Boolean {
        if (!active) return false
        presses.value += 1
        return true
    }
}
