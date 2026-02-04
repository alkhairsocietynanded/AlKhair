package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalSalaryRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseSalaryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SalaryUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localSalaryRepository: LocalSalaryRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseSalaryRepository: SupabaseSalaryRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Salary
            val unsyncedSalaries = localSalaryRepository.getUnsyncedSalaries()
            if (unsyncedSalaries.isNotEmpty()) {
                val result = supabaseSalaryRepository.saveSalaryBatch(unsyncedSalaries)
                if (result.isSuccess) {
                    localSalaryRepository.markSalariesAsSynced(unsyncedSalaries.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("SALARY")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseSalaryRepository.deleteSalaryBatch(pendingDeletions.map { it.id })
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
