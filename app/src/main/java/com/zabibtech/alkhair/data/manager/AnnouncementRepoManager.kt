package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAnnouncementRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAnnouncementRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepoManager @Inject constructor(
    private val localAnnouncementRepo: LocalAnnouncementRepository,
    private val firebaseAnnouncementRepo: FirebaseAnnouncementRepository
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
        return firebaseAnnouncementRepo.getAnnouncementsUpdatedAfter(after)
            .getOrElse {
                Log.e("AnnouncementRepo", "Global sync failed", it)
                emptyList()
            }
    }

    // 2. Targeted Sync (Used by AppDataSyncManager for STUDENT/TEACHER)
    suspend fun syncTargetAnnouncements(target: String, lastSync: Long): Result<Unit> {
        return firebaseAnnouncementRepo.getAnnouncementsForTargetUpdatedAfter(target, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    insertLocal(list) // Save to Room
                    Log.d("AnnouncementRepo", "Synced ${list.size} records for target: $target")
                }
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE OPERATIONS (Remote -> Local)
       ============================================================ */

    suspend fun createAnnouncement(announcement: Announcement): Result<Unit> {
        return firebaseAnnouncementRepo.createAnnouncement(announcement)
            .onSuccess { newAnnouncement ->
                // Save to Local immediately
                insertLocal(newAnnouncement.copy(updatedAt = System.currentTimeMillis()))
            }
            .map { }
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        return firebaseAnnouncementRepo.updateAnnouncement(announcement)
            .onSuccess {
                // Update Local immediately
                val updatedLocal = announcement.copy(updatedAt = System.currentTimeMillis())
                localAnnouncementRepo.updateAnnouncement(updatedLocal)
            }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return firebaseAnnouncementRepo.deleteAnnouncement(announcementId).onSuccess {
            // 1. Delete Locally
            deleteLocally(announcementId)

            // 2. Create Tombstone for Sync
            try {
                val record = DeletedRecord(
                    id = announcementId,
                    type = "announcement",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(announcementId).setValue(record).await()
            } catch (e: Exception) {
                Log.e("AnnouncementRepo", "Tombstone failed", e)
            }
        }
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
}