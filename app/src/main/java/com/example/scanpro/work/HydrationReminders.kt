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
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.core.notifications.HydrationSchedule
import com.example.scanpro.data.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    /** The switch in Settings. */
    suspend fun setEnabled(enabled: Boolean) {
        settings.setHydrationReminders(enabled)
        if (enabled) schedule(ExistingWorkPolicy.REPLACE) else cancel()
    }

    /** The timings in Settings: the queued reminder moves to the new times at once. */
    suspend fun setTiming(timing: HydrationSchedule.Timing) {
        settings.setHydrationTiming(timing)
        if (settings.hydrationReminders.first()) schedule(ExistingWorkPolicy.REPLACE)
    }

    internal suspend fun schedule(policy: ExistingWorkPolicy, nowMs: Long = System.currentTimeMillis()) {
        val next = HydrationSchedule.next(nowMs, settings.hydrationTiming.first())
        val request = OneTimeWorkRequestBuilder<HydrationWorker>()
            .setInitialDelay((next.atMs - nowMs).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(HydrationWorker.KEY_DUE_AT to next.atMs))
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
        val dueAt = inputData.getLong(KEY_DUE_AT, 0L)
        val timing = settings.hydrationTiming.first()
        // Not shown when late, nor when queued for a time that is no longer one of the day's reminders.
        if (HydrationSchedule.isDue(dueAt, now) && HydrationSchedule.isSlot(dueAt, timing)) {
            runCatching { notifications.drinkWater(HydrationSchedule.wording(HydrationSchedule.minuteOfDay(dueAt), timing)) }
        }
        // Appended, not replaced: replacing work of the same name would cancel this very run.
        reminders.schedule(ExistingWorkPolicy.APPEND_OR_REPLACE, now)
        return Result.success()
    }

    companion object {
        const val KEY_DUE_AT = "dueAt"
    }
}

/**
 * The two buttons on a drink-water notice, "I drank" and "Skip". Both only put the notice away, without opening the
 * app; neither answer is kept anywhere, so the app holds no record of what the user drinks.
 */
@AndroidEntryPoint
class HydrationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: AppNotifications

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DRANK || intent.action == ACTION_SKIP) notifications.clearDrinkWater()
    }

    companion object {
        const val ACTION_DRANK = "com.example.scanpro.action.DRINK_WATER_DRANK"
        const val ACTION_SKIP = "com.example.scanpro.action.DRINK_WATER_SKIP"
    }
}
