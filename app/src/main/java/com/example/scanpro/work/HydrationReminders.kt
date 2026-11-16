package com.example.scanpro.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.core.notifications.HydrationSchedule
import com.example.scanpro.data.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the drink-water reminders ([HydrationSchedule]). One piece of delayed work waits for the next reminder;
 * when it runs it shows the notice and queues the one after. WorkManager keeps the queue across restarts, so no
 * boot permission or exact-alarm permission is needed; a reminder may arrive a few minutes late, never early.
 */
@Singleton
class HydrationReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val notifications: AppNotifications
) {
    /** At app start: keeps the queue alive while the reminders are on, and empties it while they are off. */
    suspend fun sync() {
        if (settings.hydrationReminders.first()) schedule(ExistingWorkPolicy.KEEP) else cancel()
    }

    /** The switch in Settings or the notice's "Turn off" button. */
    suspend fun setEnabled(enabled: Boolean) {
        settings.setHydrationReminders(enabled)
        if (enabled) schedule(ExistingWorkPolicy.REPLACE) else cancel()
    }

    internal fun schedule(policy: ExistingWorkPolicy, nowMs: Long = System.currentTimeMillis()) {
        val next = HydrationSchedule.next(nowMs)
        val request = OneTimeWorkRequestBuilder<HydrationWorker>()
            .setInitialDelay((next.atMs - nowMs).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(HydrationWorker.KEY_DUE_AT to next.atMs, HydrationWorker.KEY_SLOT to next.slot))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        notifications.clearDrinkWater()
    }

    private companion object {
        const val UNIQUE_NAME = "scanpro-drink-water"
    }
}

@HiltWorker
class HydrationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
    private val notifications: AppNotifications,
    private val reminders: HydrationReminders
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Switched off since this was queued: nothing is shown and nothing more is queued.
        if (!settings.hydrationReminders.first()) return Result.success()
        val now = System.currentTimeMillis()
        if (HydrationSchedule.isDue(inputData.getLong(KEY_DUE_AT, 0L), now)) {
            runCatching { notifications.drinkWater(inputData.getInt(KEY_SLOT, 0)) }
        }
        // Appended, not replaced: replacing work of the same name would cancel this very run.
        reminders.schedule(ExistingWorkPolicy.APPEND_OR_REPLACE, now)
        return Result.success()
    }

    companion object {
        const val KEY_DUE_AT = "dueAt"
        const val KEY_SLOT = "slot"
    }
}

/** The "Turn off" button on a drink-water notice: stops the reminders without opening the app. */
@AndroidEntryPoint
class HydrationOffReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: HydrationReminders
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TURN_OFF) return
        val pending = goAsync()
        scope.launch {
            try {
                reminders.setEnabled(false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TURN_OFF = "com.example.scanpro.action.DRINK_WATER_OFF"
    }
}
