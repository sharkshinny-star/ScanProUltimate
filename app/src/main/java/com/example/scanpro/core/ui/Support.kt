package com.example.scanpro.core.ui

import com.example.scanpro.R
import androidx.annotation.StringRes
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.scanpro.BuildConfig
import androidx.core.net.toUri
import com.example.scanpro.core.diagnostics.AppDiagnostics

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
enum class SupportTopic(@param:StringRes val subjectRes: Int, @param:StringRes val promptRes: Int) {
    FEEDBACK(R.string.support_subject_feedback, R.string.support_prompt_feedback),
    ISSUE(R.string.support_subject_issue, R.string.support_prompt_issue),
    FEATURE(R.string.support_subject_feature, R.string.support_prompt_feature)
}

/** App and device details appended to support emails, so a report can be reproduced without a follow-up. */
fun supportDetails(): String =
    "ScanPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

/**
 * Opens the user's email app with the subject, prompt and details filled in; with no email app, the
 * share sheet takes the same text; with neither, the details are copied so they can be pasted elsewhere.
 */
enum class SupportDelivery(@param:StringRes private val confirmationRes: Int) {
    EMAIL(R.string.support_delivery_email),
    SHARE(R.string.support_delivery_share),
    CLIPBOARD(R.string.support_delivery_clipboard);

    fun confirmation(context: Context): String =
        if (this == CLIPBOARD) context.getString(confirmationRes, SupportLinks.EMAIL) else context.getString(confirmationRes)
}

fun contactSupport(context: Context, topic: SupportTopic): SupportDelivery {
    AppDiagnostics.recordEvent("Support", "Contact support initiated for topic: ${topic.name}")

    val isIssue = topic == SupportTopic.ISSUE
    val reportUri = if (isIssue) {
        runCatching {
            val reportFile = AppDiagnostics.generateReportFile(context)
            FileProvider.getUriForFile(context, "${context.packageName}.files", reportFile)
        }.getOrNull()
    } else null

    val subject = if (isIssue) {
        "${context.getString(topic.subjectRes)} [${BuildConfig.VERSION_NAME} - ${Build.MANUFACTURER} ${Build.MODEL}]"
    } else {
        context.getString(topic.subjectRes)
    }

    val body = if (isIssue) {
        val sysSummary = AppDiagnostics.buildSystemInfo(context)
        val recentCrash = AppDiagnostics.getRecentCrashReport(context)
        val crashSection = if (!recentCrash.isNullOrBlank()) {
            "[Recent Crash Detected]\n${recentCrash.take(1000)}\n(Full crash trace included in attached report)\n"
        } else {
            "[Recent Crash: None]"
        }

        """
        |${context.getString(topic.promptRes)}
        |
        |
        |—
        |[System Diagnostics]
        |$sysSummary
        |
        |$crashSection
        |
        |(Full diagnostic report and recent app logs are attached as scanpro_issue_report.txt)
        """.trimMargin()
    } else {
        "${context.getString(topic.promptRes)}\n\n\n—\n${supportDetails()}"
    }

    val email = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportLinks.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (reportUri != null) {
            putExtra(Intent.EXTRA_STREAM, reportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(email)
        return SupportDelivery.EMAIL
    } catch (_: ActivityNotFoundException) {
    }

    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportLinks.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.support_to_line, SupportLinks.EMAIL) + "\n\n$body")
        if (reportUri != null) {
            putExtra(Intent.EXTRA_STREAM, reportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val chooser = Intent.createChooser(share, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(chooser)
        SupportDelivery.SHARE
    } catch (_: ActivityNotFoundException) {
        val clipboardContent = if (isIssue) {
            context.getString(R.string.support_to_line, SupportLinks.EMAIL) + "\n" +
                context.getString(R.string.support_subject_line, subject) + "\n\n$body\n\n" +
                AppDiagnostics.buildFullReport(context)
        } else {
            context.getString(R.string.support_to_line, SupportLinks.EMAIL) + "\n" +
                context.getString(R.string.support_subject_line, subject) + "\n\n$body"
        }
        copyText(context, clipboardContent)
        SupportDelivery.CLIPBOARD
    }
}
