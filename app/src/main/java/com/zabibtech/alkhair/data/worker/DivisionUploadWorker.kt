package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseDivisionRepository
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
    private val firebaseDivisionRepository: FirebaseDivisionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Divisions
            val unsyncedDivisions = localDivisionRepository.getUnsyncedDivisions()
            if (unsyncedDivisions.isNotEmpty()) {
                val result = firebaseDivisionRepository.saveDivisionBatch(unsyncedDivisions)
                if (result.isSuccess) {
                    localDivisionRepository.markDivisionsAsSynced(unsyncedDivisions.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("DIVISION")
            if (pendingDeletions.isNotEmpty()) {
                val result = firebaseDivisionRepository.deleteDivisionBatch(pendingDeletions.map { it.id })
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
