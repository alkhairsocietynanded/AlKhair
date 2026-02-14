package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalStudyMaterialRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseStudyMaterialRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class StudyMaterialUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localStudyMaterialRepository: LocalStudyMaterialRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseStudyMaterialRepository: SupabaseStudyMaterialRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Study Materials
            val unsyncedMaterials = localStudyMaterialRepository.getUnsyncedStudyMaterials()
            if (unsyncedMaterials.isNotEmpty()) {
                val result = supabaseStudyMaterialRepository.saveBatch(unsyncedMaterials)
                if (result.isSuccess) {
                    localStudyMaterialRepository.markStudyMaterialsAsSynced(unsyncedMaterials.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("STUDY_MATERIAL")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseStudyMaterialRepository.deleteBatch(pendingDeletions.map { it.id })
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
