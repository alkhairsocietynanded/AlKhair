package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.SubjectDao
import com.aewsn.alkhair.data.remote.supabase.SupabaseSubjectRepo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SubjectUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localDao: SubjectDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val remoteRepo: SupabaseSubjectRepo
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Items
            val unsyncedItems = localDao.getUnsyncedSubjects()
            if (unsyncedItems.isNotEmpty()) {
                // Upsert one by one or batch if repo supports it. 
                // Currently repo has upsertSubject(single). Loop for now.
                // Ideally repo should support batch upsert.
                unsyncedItems.forEach { item ->
                    val result = remoteRepo.upsertSubject(item)
                    if (result.isFailure) {
                        return@withContext Result.retry() 
                    }
                }
                localDao.markSubjectsAsSynced(unsyncedItems.map { it.id })
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("SUBJECT")
            if (pendingDeletions.isNotEmpty()) {
                pendingDeletions.forEach { pending ->
                    val result = remoteRepo.deleteSubject(pending.id) // Corrected to deleteSubject
                     if (result.isFailure) {
                        return@withContext Result.retry()
                    }
                }
                pendingDeletionDao.removePendingDeletions(pendingDeletions.map { it.id })
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
