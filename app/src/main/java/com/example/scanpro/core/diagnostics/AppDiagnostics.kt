package com.example.scanpro.core.diagnostics

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import com.example.scanpro.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Diagnostic logger and system info collector for support issue reports.
 * Captures device state, display metrics, memory, storage, recent crash traces,
 * in-memory events, and system logcat to facilitate swift troubleshooting.
 */
object AppDiagnostics {

    private const val MAX_IN_MEMORY_LOGS = 100
    private val inMemoryLogs = ArrayDeque<String>(MAX_IN_MEMORY_LOGS)

    /**
     * Takes what is private out of text that is about to leave the phone in a report: the app's folder paths, other
     * apps' content links, and the names of exported files (they come from document titles). Pure, so it can be
     * tested; [redact] fills in this app's paths.
     */
    internal fun redactPaths(text: String, appPaths: List<String>, packageName: String): String {
        var out = text
        appPaths.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }.forEach { out = out.replace(it, "<app>") }
        out = out.replace(Regex("/data/(?:user/\\d+|data)/" + Regex.escape(packageName)), "<app>")
        out = out.replace(Regex("(exports/)[^\\s\"')\\]]+"), "$1<file>")
        out = out.replace(Regex("content://[^\\s\"')\\]]+"), "<content-uri>")
        return out
    }

    fun redact(context: Context, text: String): String = redactPaths(
        text,
        listOfNotNull(
            context.filesDir?.absolutePath, context.cacheDir?.absolutePath, context.dataDir?.absolutePath,
            context.getExternalFilesDir(null)?.absolutePath, context.externalCacheDir?.absolutePath
        ),
        context.packageName
    )

    @Synchronized
    fun recordEvent(tag: String, message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "$stamp [$tag] $message"
        if (inMemoryLogs.size >= MAX_IN_MEMORY_LOGS) {
            inMemoryLogs.removeFirst()
        }
        inMemoryLogs.addLast(entry)
        android.util.Log.d(tag, message)
    }

    @Synchronized
    fun getInMemoryLogs(): List<String> = inMemoryLogs.toList()

    /**
     * Collects the last [maxLines] lines of logcat for this app process.
     */
    fun collectLogcat(maxLines: Int = 150): String = runCatching {
        fun dump(vararg args: String): List<String> {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString()) + args)
            // "-d" dumps and exits, but never leave the process behind if reading fails or it stalls.
            return try {
                process.inputStream.bufferedReader().use { it.readLines() }
            } finally {
                process.destroy()
            }
        }
        val lines = dump("--pid=${Process.myPid()}").ifEmpty { dump() }
        lines.takeLast(maxLines).joinToString("\n")
    }.getOrDefault("")

    /**
     * Reads the most recent crash report recorded by CrashReporter, if any.
     */
    fun getRecentCrashReport(context: Context): String? = runCatching {
        val dir = File(context.filesDir, "crash-reports")
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
            ?.let { redact(context, it) }
    }.getOrNull()

    /**
     * Compiles a comprehensive diagnostic report of the system, hardware, memory, storage,
     * display, and environment.
     */
    // The free space is shown as information only; allocatable bytes (which count clearable caches) are not what is meant.
    @SuppressLint("UsableSpace")
    fun buildSystemInfo(context: Context): String = buildString {
        val res = context.resources
        val dm = res.displayMetrics
        val config = res.configuration
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val filesDir = context.filesDir
        val freeSpaceMb = filesDir.usableSpace / (1024 * 1024)
        val totalSpaceMb = filesDir.totalSpace / (1024 * 1024)
        val cacheSizeMb = runCatching {
            context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024)
        }.getOrDefault(0L)

        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val installDate = packageInfo?.firstInstallTime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "unknown"
        val updateDate = packageInfo?.lastUpdateTime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "unknown"

        appendLine("[APPLICATION]")
        appendLine("App: ScanPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.BUILD_TYPE}]")
        appendLine("Package: ${context.packageName}")
        appendLine("Installed: $installDate | Updated: $updateDate")
        appendLine()

        appendLine("[DEVICE & PLATFORM]")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
        appendLine("Brand: ${Build.BRAND} | Hardware: ${Build.HARDWARE} | Board: ${Build.BOARD}")
        appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine()

        appendLine("[DISPLAY & ENVIRONMENT]")
        val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
        val darkTheme = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        appendLine("Screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi (density scale: ${dm.density}, font scale: ${config.fontScale})")
        appendLine("Orientation: $orientation | Dark Theme: $darkTheme")
        appendLine("Locale: ${Locale.getDefault().toLanguageTag()} (${Locale.getDefault().displayName})")
        appendLine("Timezone: ${TimeZone.getDefault().id} (${TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)})")
        appendLine()

        appendLine("[MEMORY & STORAGE]")
        appendLine("Heap Memory: ${usedMemMb}MB used / ${maxMemMb}MB max")
        appendLine("System Low RAM: ${memInfo.lowMemory} (Avail: ${memInfo.availMem / (1024 * 1024)}MB / Threshold: ${memInfo.threshold / (1024 * 1024)}MB)")
        appendLine("Internal Storage: ${freeSpaceMb}MB free of ${totalSpaceMb}MB")
        appendLine("App Cache: ~${cacheSizeMb}MB")
    }

    /**
     * Builds the full diagnostic report including system info, recent crash, in-memory events,
     * and logcat dump.
     */
    fun buildFullReport(context: Context): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        appendLine("================================================================")
        appendLine("SCANPRO SYSTEM & DIAGNOSTIC REPORT")
        appendLine("Generated: $stamp")
        appendLine("================================================================")
        appendLine()

        appendLine(buildSystemInfo(context))

        appendLine("================================================================")
        appendLine("RECENT CRASH / UNCAUGHT EXCEPTION")
        appendLine("================================================================")
        val crash = getRecentCrashReport(context)
        if (!crash.isNullOrBlank()) {
            appendLine(crash.trim())
        } else {
            appendLine("No recent crashes recorded.")
        }
        appendLine()

        val inMemory = getInMemoryLogs()
        if (inMemory.isNotEmpty()) {
            appendLine("================================================================")
            appendLine("IN-APP EVENT TRAIL (${inMemory.size} events)")
            appendLine("================================================================")
            inMemory.forEach { appendLine(redact(context, it)) }
            appendLine()
        }

        appendLine("================================================================")
        appendLine("SYSTEM LOGCAT (RECENT)")
        appendLine("================================================================")
        val logcat = redact(context, collectLogcat(150))
        if (logcat.isNotBlank()) {
            appendLine(logcat.trim())
        } else {
            appendLine("Logcat output unavailable or empty.")
        }
        appendLine()
        appendLine("================================================================")
        appendLine("END OF REPORT")
        appendLine("================================================================")
    }

    /**
     * Writes the diagnostic report to a temporary cache file under exports/,
     * which is exposed via FileProvider for sharing/email attachments.
     */
    fun generateReportFile(context: Context): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val reportFile = File(exportsDir, "scanpro_issue_report.txt")
        val content = buildFullReport(context)
        reportFile.writeText(content)
        return reportFile
    }
}
