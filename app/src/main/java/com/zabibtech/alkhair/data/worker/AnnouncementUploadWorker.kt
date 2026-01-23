package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalAnnouncementRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseAnnouncementRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class AnnouncementUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localAnnouncementRepository: LocalAnnouncementRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseAnnouncementRepository: SupabaseAnnouncementRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Announcements
            val unsyncedAnnouncements = localAnnouncementRepository.getUnsyncedAnnouncements()
            if (unsyncedAnnouncements.isNotEmpty()) {
                val result = supabaseAnnouncementRepository.saveAnnouncementBatch(unsyncedAnnouncements)
                if (result.isSuccess) {
                    localAnnouncementRepository.markAnnouncementsAsSynced(unsyncedAnnouncements.map { it.id })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("ANNOUNCEMENT")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseAnnouncementRepository.deleteAnnouncementBatch(pendingDeletions.map { it.id })
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
