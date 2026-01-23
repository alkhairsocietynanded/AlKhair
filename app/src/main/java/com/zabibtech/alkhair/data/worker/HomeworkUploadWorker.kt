package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalHomeworkRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseHomeworkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class HomeworkUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localHomeworkRepository: LocalHomeworkRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseHomeworkRepository: SupabaseHomeworkRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Homework
            val unsyncedHomework = localHomeworkRepository.getUnsyncedHomework()
            if (unsyncedHomework.isNotEmpty()) {
                val result = supabaseHomeworkRepository.saveHomeworkBatch(unsyncedHomework)
                if (result.isSuccess) {
                    localHomeworkRepository.markHomeworkAsSynced(unsyncedHomework.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("HOMEWORK")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseHomeworkRepository.deleteHomeworkBatch(pendingDeletions.map { it.id })
                if (result.isSuccess) {
                    pendingDeletionDao.removePendingDeletions(pendingDeletions.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
