package com.example.scanpro.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.example.scanpro.data.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.data.SettingsStore
import kotlinx.coroutines.flow.first

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository,
    private val settings: SettingsStore,
    private val notifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): WorkResult = try {
        repository.purgeExpiredTrash()
        CacheCleaner.purge(applicationContext)
        runCatching { remind() }
        WorkResult.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        WorkResult.retry()
    }

    /**
     * The optional reminders. This work runs about once a day at a time Android chooses, so a reminder is
     * "within the next day", never at an exact hour.
     */
    private suspend fun remind(now: Long = System.currentTimeMillis()) {
        // Each daily run covers the next day, so every document is mentioned about once before it goes.
        notifications.trashExpiring(repository.trashExpiringWithin(DAY_MS))
        if (settings.notifyBackup.first()) {
            val since = maxOf(settings.lastBackupAt.first(), settings.lastBackupReminderAt.first())
            if (now - since >= BACKUP_REMINDER_AFTER_MS && repository.activeDocumentCount() > 0) {
                notifications.backupDue()
                settings.recordBackupReminder(now)
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "scanpro-library-maintenance"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val BACKUP_REMINDER_AFTER_MS = 30 * DAY_MS
    }
}
