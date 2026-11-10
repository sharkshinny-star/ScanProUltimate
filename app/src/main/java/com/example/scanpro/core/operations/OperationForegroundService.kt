package com.example.scanpro.core.operations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.scanpro.R

/**
 * Runs while [OperationRunner] has work in flight. Without it, Android may kill the process a few
 * seconds after the user switches apps during a long OCR or export, and the work silently vanishes.
 * The service does nothing itself; its foreground notification is what keeps the process alive.
 *
 * Every start goes through [onStartCommand], which always calls startForeground before anything else,
 * including for the stop request: stopping with stopService() right after startForegroundService()
 * would otherwise crash on Android 8+ ("did not then call startForeground").
 */
class OperationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL)
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(label),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // With the start id, a newer "show" request that is already queued keeps the service alive.
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 limits data-sync services to a few hours a day; the work itself continues on its scope.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(label: String?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scanpro)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(label ?: getString(R.string.operation_notification_working))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.operation_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.operation_channel_description)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "operations"
        private const val NOTIFICATION_ID = 0x5CA7
        private const val EXTRA_LABEL = "label"
        private const val ACTION_STOP = "com.example.scanpro.action.STOP_OPERATION_SERVICE"

        /** Shows (or updates) the "working" notification. A refusal, e.g. from the background on Android 12+, is harmless. */
        fun show(context: Context, label: String?) {
            runCatching {
                ContextCompat.startForegroundService(context, intent(context).putExtra(EXTRA_LABEL, label))
            }
        }

        fun stop(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, intent(context).setAction(ACTION_STOP)) }
        }

        private fun intent(context: Context) = Intent(context, OperationForegroundService::class.java)
    }
}
