package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.local.local_repos.LocalAnnouncementRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.Announcement
import com.aewsn.alkhair.data.remote.supabase.SupabaseAnnouncementRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.worker.AnnouncementUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

@Singleton
class AnnouncementRepoManager @Inject constructor(
    private val localAnnouncementRepo: LocalAnnouncementRepository,
    private val remoteRepo: SupabaseAnnouncementRepository,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao
) : BaseRepoManager<Announcement>() {

    /* ============================================================
       📦 READ OPERATIONS (SSOT - Local Flow)
       ============================================================ */

    override fun observeLocal(): Flow<List<Announcement>> =
        localAnnouncementRepo.getAllAnnouncements()

    // Optimized flow for Dashboard (Limit 5)
    fun observeLatestAnnouncements(): Flow<List<Announcement>> =
        localAnnouncementRepo.getFiveLatestAnnouncementsFlow()

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    // 1. Global Sync (Used by AppDataSyncManager for ADMIN)
    override suspend fun fetchRemoteUpdated(after: Long): List<Announcement> {
        return remoteRepo.getAnnouncementsUpdatedAfter(after)
            .getOrElse {
                Log.e("AnnouncementRepo", "Global sync failed", it)
                emptyList()
            }
    }

    // 2. Targeted Sync (Used by AppDataSyncManager for STUDENT/TEACHER)
    suspend fun syncTargetAnnouncements(target: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getAnnouncementsForTargetUpdatedAfter(target, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    insertLocal(list) // Save to Room
                    Log.d("AnnouncementRepo", "Synced ${list.size} records for target: $target")
                }
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE OPERATIONS (Local First -> Background Sync)
       ============================================================ */

    suspend fun createAnnouncement(announcement: Announcement): Result<Unit> {
        // 1. Prepare Local Data
        val newId = announcement.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()
        
        val newAnnouncement = announcement.copy(
            id = newId,
            timestamp = if (announcement.timestamp == 0L) currentTime else announcement.timestamp,
            updatedAt = currentTime,
            isSynced = false
        )

        // 2. Insert Local Immediately
        insertLocal(newAnnouncement)
        
        // 3. Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        val currentTime = System.currentTimeMillis()

        // 1. Prepare Local Update
        val updatedLocal = announcement.copy(
            updatedAt = currentTime,
            isSynced = false
        )
        
        // 2. Update Local Immediately
        localAnnouncementRepo.updateAnnouncement(updatedLocal)
        
        // 3. Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        // 1. Delete Locally
        deleteLocally(announcementId)
        
        // 2. Mark for deletion
        val pendingDeletion = PendingDeletion(
            id = announcementId,
            type = "ANNOUNCEMENT",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        
        // 3. Schedule Upload Worker
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<AnnouncementUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "AnnouncementUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    /* ============================================================
       🔧 LOCAL HELPER OVERRIDES
       ============================================================ */

    override suspend fun insertLocal(items: List<Announcement>) =
        localAnnouncementRepo.insertAnnouncements(items)

    override suspend fun insertLocal(item: Announcement) =
        localAnnouncementRepo.insertAnnouncement(item)

    override suspend fun deleteLocally(id: String) =
        localAnnouncementRepo.deleteAnnouncement(id)
    override suspend fun clearLocal() = localAnnouncementRepo.clearAll()
}