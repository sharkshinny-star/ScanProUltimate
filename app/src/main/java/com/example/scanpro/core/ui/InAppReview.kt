package com.example.scanpro.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

/** The activity behind a Compose context, or null outside one. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Asks Google Play for the in-app review flow and shows it when Play returns one. Play decides whether
 * the card appears and when it is quota-limited, so the result says nothing about whether the user rated;
 * the app only ever asks once. Failures (no Play, sideloaded build) are swallowed.
 */
suspend fun requestInAppReview(activity: Activity) {
    runCatching {
        val manager = ReviewManagerFactory.create(activity)
        val info = manager.requestReviewFlow().await()
        manager.launchReviewFlow(activity, info).await()
    }
}
