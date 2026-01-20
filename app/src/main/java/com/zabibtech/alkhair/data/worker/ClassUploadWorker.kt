package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseClassRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ClassUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localClassRepository: LocalClassRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val firebaseClassRepository: FirebaseClassRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Classes
            val unsyncedClasses = localClassRepository.getUnsyncedClasses()
            if (unsyncedClasses.isNotEmpty()) {
                val result = firebaseClassRepository.saveClassBatch(unsyncedClasses)
                if (result.isSuccess) {
                    localClassRepository.markClassesAsSynced(unsyncedClasses.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("CLASS")
            if (pendingDeletions.isNotEmpty()) {
                val result = firebaseClassRepository.deleteClassBatch(pendingDeletions.map { it.id })
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
