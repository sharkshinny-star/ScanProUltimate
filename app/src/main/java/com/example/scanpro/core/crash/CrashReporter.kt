package com.example.scanpro.core.crash

import android.content.Context
import android.os.Build
import com.example.scanpro.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last few crash reports in the app's private files, so the next launch can offer to share
 * one. Nothing leaves the device unless the user picks a share target: the app has no analytics
 * endpoint and does not need one for this.
 */
object CrashReporter {
    private const val DIR = "crash-reports"
    private const val KEEP = 5
    private const val PREFS = "crash_reports"
    private const val KEY_OFFERED = "last_offered"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The newest report that has not been offered to the user yet, if any. */
    fun pendingReport(context: Context): File? {
        val newest = reports(context).firstOrNull() ?: return null
        val offered = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OFFERED, null)
        return newest.takeIf { it.name != offered }
    }

    /** Marks [report] as offered, so the prompt is shown once per crash whether or not it was shared. */
    fun markOffered(context: Context, report: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OFFERED, report.name).apply()
    }

    private fun reports(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        File(dir, "crash-$stamp.txt").writeText(
            """
            ScanPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}
            Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}
            Thread: ${thread.name}
            Time: $stamp

            """.trimIndent() + trace
        )
        reports(context).drop(KEEP).forEach { it.delete() }
    }
}
