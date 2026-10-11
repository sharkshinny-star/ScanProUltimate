package com.example.scanpro.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.example.scanpro.data.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): WorkResult = runCatching {
        repository.purgeExpiredTrash(maxAgeDays = 30)
        WorkResult.success()
    }.getOrElse { WorkResult.retry() }

    companion object {
        const val UNIQUE_NAME = "scanpro-library-maintenance"
    }
}
