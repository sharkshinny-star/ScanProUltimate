package com.example.scanpro.imaging

import java.io.File

/**
 * Runs [block], which writes this file; if it fails or is cancelled part-way (disk full, an encode error, the user
 * pressing Cancel) the half-written file is removed so no truncated result is left looking like a finished one.
 */
internal inline fun <T> File.deleteIfFails(block: () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        delete()
        throw error
    }
