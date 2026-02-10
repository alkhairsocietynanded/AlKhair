package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalSyllabusRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseSyllabusRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyllabusUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localSyllabusRepository: LocalSyllabusRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseSyllabusRepository: SupabaseSyllabusRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Syllabus
            val unsyncedSyllabus = localSyllabusRepository.getUnsyncedSyllabus()
            if (unsyncedSyllabus.isNotEmpty()) {
                val result = supabaseSyllabusRepository.saveSyllabusBatch(unsyncedSyllabus)
                if (result.isSuccess) {
                    localSyllabusRepository.markSyllabusAsSynced(unsyncedSyllabus.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("SYLLABUS")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseSyllabusRepository.deleteSyllabusBatch(pendingDeletions.map { it.id })
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
