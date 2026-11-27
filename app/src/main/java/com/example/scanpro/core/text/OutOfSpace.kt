package com.example.scanpro.core.text

import com.example.scanpro.R

/**
 * True when [error], or anything it was caused by, is the file system reporting that the device is full. The system's
 * text is the only portable signal: Android words it "write failed: ENOSPC (No space left on device)". A message the app
 * wrote itself ([UserFacingException]) is never the file system's, and reading it needs the app's strings.
 */
fun isOutOfSpace(error: Throwable): Boolean = generateSequence(error) { it.cause }.any {
    it !is UserFacingException && it.message.orEmpty().let { text ->
        text.contains("ENOSPC") || text.contains("No space left on device", ignoreCase = true)
    }
}

/**
 * The plain "not enough free space" message for [error] when the phone is full, else null. Tools and background jobs
 * show an error's own text, which for a full phone is the system's English line, not something a person can act on.
 */
fun outOfSpaceMessageRes(error: Throwable): Int? = if (isOutOfSpace(error)) R.string.err_no_space else null
