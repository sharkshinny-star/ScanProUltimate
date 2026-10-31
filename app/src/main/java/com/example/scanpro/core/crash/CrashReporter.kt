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
import androidx.core.content.edit

/**
 * Keeps the last few crash reports in the app's private files, so the next launch can offer to share
 * one. Nothing leaves the device unless the user picks a share target: the app has no analytics
 * endpoint and does not need one for this.
 */
object CrashReporter {
    private const val DIR = "crash-reports"
    private const val PREFS = "crash_reports"
    private const val KEY_OFFERED = "last_offered"

    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(
            chainedCrashHandler(Thread.getDefaultUncaughtExceptionHandler()) { thread, error -> write(app, thread, error) }
        )
    }

    /** The newest report that has not been offered to the user yet, if any. */
    fun pendingReport(context: Context): File? {
        val offered = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OFFERED, null)
        return files(context).pending(offered)
    }

    /** Marks [report] as offered, so the prompt is shown once per crash whether or not it was shared. */
    fun markOffered(context: Context, report: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_OFFERED, report.name) }
    }

    private fun files(context: Context) = CrashReportFiles(File(context.filesDir, DIR))

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val header = "ScanPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Thread: ${thread.name}\n" +
            "Time: $stamp\n\n"
        files(context).write(header, error, stamp)
    }
}

/** The crash report files in one folder: written newest-first by name, with only the last few kept. */
internal class CrashReportFiles(private val dir: File, private val keep: Int = 5) {
    fun write(header: String, error: Throwable, stamp: String): File {
        dir.mkdirs()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(header + trace)
        newestFirst().drop(keep).forEach { it.delete() }
        return file
    }

    fun newest(): File? = newestFirst().firstOrNull()

    /** The newest report, unless it is the one already offered to the user. */
    fun pending(offeredName: String?): File? = newest()?.takeIf { it.name != offeredName }

    private fun newestFirst(): List<File> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }?.sortedByDescending { it.name }.orEmpty()
}

/**
 * An uncaught-exception handler that records the crash and then hands it on to [previous], which is what ends the
 * process. Recording can fail (a full disk, say); that must never stop the crash from reaching [previous].
 */
internal fun chainedCrashHandler(
    previous: Thread.UncaughtExceptionHandler?,
    record: (Thread, Throwable) -> Unit
): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, error ->
    runCatching { record(thread, error) }
    previous?.uncaughtException(thread, error)
}
