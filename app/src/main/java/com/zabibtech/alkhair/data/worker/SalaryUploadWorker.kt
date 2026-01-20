package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalSalaryRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseSalaryRepository
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
    private val firebaseSalaryRepository: FirebaseSalaryRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Salary
            val unsyncedSalaries = localSalaryRepository.getUnsyncedSalaries()
            if (unsyncedSalaries.isNotEmpty()) {
                val result = firebaseSalaryRepository.saveSalaryBatch(unsyncedSalaries)
                if (result.isSuccess) {
                    localSalaryRepository.markSalariesAsSynced(unsyncedSalaries.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("SALARY")
            if (pendingDeletions.isNotEmpty()) {
                val result = firebaseSalaryRepository.deleteSalaryBatch(pendingDeletions.map { it.id })
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
