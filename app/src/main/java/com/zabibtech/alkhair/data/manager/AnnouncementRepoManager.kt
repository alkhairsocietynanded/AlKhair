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
       📦 SSOT — FLOW FROM ROOM
       ============================================================ */

    override fun observeLocal(): Flow<List<Announcement>> =
        localAnnouncementRepo.getAllAnnouncements()

    // Specific flow for dashboard (Limit logic can be in DAO or VM)
    // For simplicity, we can observe all and take 5 in VM, or create a specific DAO method returning Flow
    fun observeLatestAnnouncements(): Flow<List<Announcement>> =
        localAnnouncementRepo.getFiveLatestAnnouncements() // Ensure DAO has this Flow method

    /* ============================================================
       🔁 SYNC
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<Announcement> =
        firebaseAnnouncementRepo.getAnnouncementsUpdatedAfter(after).getOrElse { emptyList() }

    override suspend fun insertLocal(items: List<Announcement>) =
        localAnnouncementRepo.insertAnnouncements(items)

    override suspend fun insertLocal(item: Announcement) =
        localAnnouncementRepo.insertAnnouncement(item)

    override suspend fun deleteLocally(id: String) =
        localAnnouncementRepo.deleteAnnouncement(id)

    /* ============================================================
       ✍️ WRITE — (Remote -> Local)
       ============================================================ */

    suspend fun createAnnouncement(announcement: Announcement): Result<Unit> {
        return firebaseAnnouncementRepo.createAnnouncement(announcement)
            .onSuccess { newAnnouncement ->
                // Save to local immediately to update UI
                insertLocal(newAnnouncement)
            }
            .map { }
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        val announcementToUpdate = announcement.copy(updatedAt = System.currentTimeMillis())
        val updateMap = mapOf(
            "title" to announcementToUpdate.title,
            "content" to announcementToUpdate.content,
            "timeStamp" to announcementToUpdate.timeStamp,
            "updatedAt" to announcementToUpdate.updatedAt
        )

        return firebaseAnnouncementRepo.updateAnnouncement(announcement.id, updateMap)
            .onSuccess {
                insertLocal(announcementToUpdate)
            }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return firebaseAnnouncementRepo.deleteAnnouncement(announcementId).onSuccess {
            deleteLocally(announcementId)
            try {
                val deletedRecord = DeletedRecord(
                    id = announcementId,
                    type = "announcement",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(announcementId).setValue(deletedRecord).await()
            } catch (e: Exception) {
                Log.e("AnnouncementRepo", "Tombstone creation failed", e)
            }
        }
    }
}