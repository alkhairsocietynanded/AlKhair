package com.zabibtech.alkhair.data.worker

import androidx.work.ListenableWorker.Result
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseDivisionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DivisionUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localDivisionRepository: LocalDivisionRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseDivisionRepository: SupabaseDivisionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Divisions
            val unsyncedDivisions = localDivisionRepository.getUnsyncedDivisions()
            android.util.Log.d("DivisionUploadWorker", "Found ${unsyncedDivisions.size} unsynced divisions")

            if (unsyncedDivisions.isNotEmpty()) {
                val result = supabaseDivisionRepository.saveDivisionBatch(unsyncedDivisions)
                if (result.isSuccess) {
                    android.util.Log.d("DivisionUploadWorker", "Successfully uploaded divisions")
                    localDivisionRepository.markDivisionsAsSynced(unsyncedDivisions.map { it.id })
                } else {
                    android.util.Log.e("DivisionUploadWorker", "Failed to upload divisions: ${result.exceptionOrNull()?.message}")
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("DIVISION")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseDivisionRepository.deleteDivisionBatch(pendingDeletions.map { it.id })
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
