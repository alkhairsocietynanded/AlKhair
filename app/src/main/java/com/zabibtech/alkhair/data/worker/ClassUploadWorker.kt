package com.zabibtech.alkhair.data.worker

import androidx.work.ListenableWorker.Result
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseClassRepository
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
    private val supabaseClassRepository: SupabaseClassRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Classes
            val unsyncedClasses = localClassRepository.getUnsyncedClasses()
            android.util.Log.d("ClassUploadWorker", "Found ${unsyncedClasses.size} unsynced classes")
            
            if (unsyncedClasses.isNotEmpty()) {
                val result = supabaseClassRepository.saveClassBatch(unsyncedClasses)
                if (result.isSuccess) {
                    android.util.Log.d("ClassUploadWorker", "Successfully uploaded classes")
                    localClassRepository.markClassesAsSynced(unsyncedClasses.map { it.id })
                } else {
                    android.util.Log.e("ClassUploadWorker", "Failed to upload classes: ${result.exceptionOrNull()?.message}")
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("CLASS")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseClassRepository.deleteClassBatch(pendingDeletions.map { it.id })
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
