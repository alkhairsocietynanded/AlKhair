package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalFeesRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseFeesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class FeesUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localFeesRepository: LocalFeesRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseFeesRepository: SupabaseFeesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Fees
            val unsyncedFees = localFeesRepository.getUnsyncedFees()
            if (unsyncedFees.isNotEmpty()) {
                val result = supabaseFeesRepository.saveFeesBatch(unsyncedFees)
                if (result.isSuccess) {
                    localFeesRepository.markFeesAsSynced(unsyncedFees.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("FEES")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseFeesRepository.deleteFeesBatch(pendingDeletions.map { it.id })
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
