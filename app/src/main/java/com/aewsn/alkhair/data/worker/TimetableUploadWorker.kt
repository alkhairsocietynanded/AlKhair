package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.TimetableDao
import com.aewsn.alkhair.data.remote.supabase.SupabaseTimetableRepo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class TimetableUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localDao: TimetableDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val remoteRepo: SupabaseTimetableRepo
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Items
            val unsyncedItems = localDao.getUnsyncedTimetables()
            if (unsyncedItems.isNotEmpty()) {
                unsyncedItems.forEach { item ->
                    val result = remoteRepo.upsertTimetable(item)
                    if (result.isFailure) {
                        return@withContext Result.retry()
                    }
                }
                localDao.markTimetablesAsSynced(unsyncedItems.map { it.id })
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("TIMETABLE")
            if (pendingDeletions.isNotEmpty()) {
                 pendingDeletions.forEach { pending ->
                    val result = remoteRepo.deleteTimetable(pending.id)
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
