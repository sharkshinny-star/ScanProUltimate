package com.example.scanpro

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.scanpro.core.crash.CrashReporter
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.imaging.OpenCvRuntime
import com.example.scanpro.work.CacheCleaner
import com.example.scanpro.work.CleanupWorker
import com.google.android.play.core.splitcompat.SplitCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ScanProApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: AppNotifications

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Makes code from on-demand modules (translation) usable as soon as Play installs them, without a restart.
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        PDFBoxResourceLoader.init(this)
        // Maven OpenCV packages the native libraries in the app. Load them off the main thread so they
        // don't hold up the first frame.
        OpenCvRuntime.preloadInBackground()
        CrashReporter.install(this)
        // Off the main thread: WorkManager's first use reads its database, and the cache sweep clears temporary
        // copies of documents that a crash may have left behind. Neither should delay the first frame.
        Thread {
            runCatching { scheduleMaintenance() }
            runCatching { CacheCleaner.purge(this) }
            // Channels exist from the start, so Android's notification settings list them before the first notice.
            runCatching { notifications.ensureChannels() }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /**
     * Debug builds log disk and network work on the main thread, and leaked files, cursors and activities,
     * to logcat under the "StrictMode" tag. Nothing is thrown, so a violation never stops a debug session.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    private fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
