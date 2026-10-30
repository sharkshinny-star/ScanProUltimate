package com.example.scanpro.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.scanpro.BuildConfig

/**
 * Where feedback goes. Fill these in before release: the address receives "Give feedback", "Report an
 * issue" and "Suggest a feature" emails; the two links open from Settings and are hidden while blank.
 */
object SupportLinks {
    const val EMAIL = "support@scanpro.app"
    const val PRIVACY_POLICY_URL = ""
    const val TERMS_OF_USE_URL = ""
}

/** What the user is writing to support about; each kind gets its own subject and body template. */
enum class SupportTopic(val subject: String, val prompt: String) {
    FEEDBACK("ScanPro feedback", "What works well, and what doesn't?"),
    ISSUE("ScanPro issue report", "What happened, and what did you expect instead?\nSteps to reproduce:\n1.\n2.\n3."),
    FEATURE("ScanPro feature suggestion", "What would you like ScanPro to do?")
}

/** App and device details appended to support emails, so a report can be reproduced without a follow-up. */
fun supportDetails(): String =
    "ScanPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

/**
 * Opens the user's email app with the subject, prompt and details filled in; with no email app, the
 * share sheet takes the same text; with neither, the details are copied so they can be pasted elsewhere.
 */
enum class SupportDelivery(val confirmation: String) {
    EMAIL("Your email app opened with the details filled in"),
    SHARE("No email app found; pick an app to send the message with"),
    CLIPBOARD("The message was copied; paste it into any email to ${SupportLinks.EMAIL}")
}

fun contactSupport(context: Context, topic: SupportTopic): SupportDelivery {
    val body = "${topic.prompt}\n\n\n—\n${supportDetails()}"
    val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportLinks.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, topic.subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(email)
        return SupportDelivery.EMAIL
    } catch (_: ActivityNotFoundException) {
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, topic.subject)
        putExtra(Intent.EXTRA_TEXT, "To: ${SupportLinks.EMAIL}\n\n$body")
    }
    return try {
        context.startActivity(Intent.createChooser(share, topic.subject))
        SupportDelivery.SHARE
    } catch (_: ActivityNotFoundException) {
        copyText(context, "To: ${SupportLinks.EMAIL}\nSubject: ${topic.subject}\n\n$body")
        SupportDelivery.CLIPBOARD
    }
}

/** Opens a web link in the browser, or says so when none can. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open this link", Toast.LENGTH_SHORT).show()
    }
}
