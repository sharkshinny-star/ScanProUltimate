package com.example.scanpro.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.scanpro.MainActivity
import com.example.scanpro.R
import com.example.scanpro.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local notifications: a finished or failed job while the app is out of sight, and the optional reminders.
 * Nothing is sent from a server; every notice is posted on the device by the app itself.
 *
 * Privacy: a notice never names a document. On the lock screen only a neutral line shows, and while app lock is on
 * the notice itself stays neutral too, since the lock is meant to keep what is in the library private.
 * Each kind has one fixed id, so a newer notice replaces the older one instead of piling up.
 */
@Singleton
class AppNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    /** True while one of the app's screens is showing; finished and failed jobs then speak through the snackbar. */
    @Volatile var appVisible: Boolean = false

    /** Whether the app may post at all: the Android 13+ permission and the app-wide switch in system settings. */
    fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** A job the user started finished while they were elsewhere. [documentId] makes the tap open that document. */
    suspend fun taskFinished(detail: String?, documentId: String? = null) {
        if (appVisible || !settings.notifyFinished.first()) return
        post(
            id = ID_FINISHED,
            channel = CHANNEL_RESULTS,
            title = context.getString(R.string.notify_finished_title),
            detail = detail,
            tap = documentId?.let { openDocumentIntent(it) } ?: openAppIntent()
        )
    }

    /**
     * A job the user started could not finish while they were elsewhere; opening the app shows the reason again.
     * [action] is one of the `MainActivity.ACTION_OPEN_*` screens to open on tap, or null for the app itself.
     */
    suspend fun taskFailed(detail: String?, action: String? = null) {
        if (appVisible || !settings.notifyFailures.first()) return
        post(
            id = ID_FAILED,
            channel = CHANNEL_FAILURES,
            title = context.getString(R.string.notify_failed_title),
            detail = detail,
            tap = openAppIntent(action)
        )
    }

    /** [count] documents leave the recycle bin for good within a day. */
    suspend fun trashExpiring(count: Int) {
        if (count <= 0 || !settings.notifyTrash.first()) return
        post(
            id = ID_TRASH,
            channel = CHANNEL_REMINDERS,
            title = context.getString(R.string.notify_trash_title),
            detail = context.resources.getQuantityString(R.plurals.notify_trash_body, count, count),
            tap = openAppIntent(MainActivity.ACTION_OPEN_TRASH),
            neutralWhenLocked = false
        )
    }

    /** No backup was made for a long time. */
    suspend fun backupDue() {
        if (!settings.notifyBackup.first()) return
        post(
            id = ID_BACKUP,
            channel = CHANNEL_REMINDERS,
            title = context.getString(R.string.notify_backup_title),
            detail = context.getString(R.string.notify_backup_body),
            tap = openAppIntent(MainActivity.ACTION_OPEN_SETTINGS),
            neutralWhenLocked = false
        )
    }

    /** Clears finished and failed notices once the user is back in the app, where the snackbar already said it. */
    fun clearTaskNotices() {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ID_FINISHED)
        manager.cancel(ID_FAILED)
    }

    /** Android's own notification settings for this app, where channels can be tuned or switched off. */
    fun systemSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RESULTS, context.getString(R.string.notify_channel_results), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.notify_channel_results_description)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_FAILURES, context.getString(R.string.notify_channel_failures), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.notify_channel_failures_description)
                },
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.notify_channel_reminders), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.notify_channel_reminders_description)
                    setShowBadge(false)
                }
            )
        )
    }

    private suspend fun post(
        id: Int,
        channel: String,
        title: String,
        detail: String?,
        tap: PendingIntent,
        neutralWhenLocked: Boolean = true
    ) {
        if (!canPost()) return
        ensureChannels()
        // With app lock on, the notice itself must not say what the library holds or what was done to it.
        val appLocked = neutralWhenLocked && settings.appLock.first()
        val body = if (appLocked) context.getString(R.string.notify_open_to_see) else detail
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notify_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .apply { if (!body.isNullOrBlank()) setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)) }
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(if (channel == CHANNEL_REMINDERS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (denied: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show.
        }
    }

    private fun openAppIntent(action: String? = null): PendingIntent =
        pending(Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN), requestCode = action.hashCode())

    // The id travels to the app's own activity only; the lock gate still applies before anything opens.
    private fun openDocumentIntent(documentId: String): PendingIntent =
        pending(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DOCUMENT)
                .putExtra(MainActivity.EXTRA_DOCUMENT_ID, documentId),
            requestCode = ID_FINISHED
        )

    private fun pending(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private companion object {
        const val CHANNEL_RESULTS = "results"
        const val CHANNEL_FAILURES = "failures"
        const val CHANNEL_REMINDERS = "reminders"
        const val ID_FINISHED = 0x5CB0
        const val ID_FAILED = 0x5CB1
        const val ID_TRASH = 0x5CB2
        const val ID_BACKUP = 0x5CB3
    }
}
