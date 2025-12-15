package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.FirebaseRefs.announcementsRef
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnnouncementRepository @Inject constructor() {

    suspend fun createAnnouncement(announcement: Announcement): Result<Announcement> {
        return try {
            val announcementId = announcement.id.ifEmpty { announcementsRef.push().key!! }
            val newAnnouncement = announcement.copy(id = announcementId)
            announcementsRef.child(announcementId).setValue(newAnnouncement).await()
            Result.success(newAnnouncement)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error creating announcement", e)
            Result.failure(e)
        }
    }

    suspend fun getAllAnnouncements(): Result<List<Announcement>> {
        return try {
            val snapshot = announcementsRef.get().await()
            val announcements = snapshot.children.mapNotNull {
                it.getValue(Announcement::class.java)
            }
            Result.success(announcements.sortedByDescending { it.timeStamp })
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error getting all announcements", e)
            Result.failure(e)
        }
    }

    suspend fun getFiveLatestAnnouncements(): Result<List<Announcement>> {
        return try {
            val query = announcementsRef.orderByChild("timeStamp").limitToLast(5)
            val snapshot = query.get().await()
            val announcements = snapshot.children.mapNotNull {
                it.getValue(Announcement::class.java)
            }
            Result.success(announcements.sortedByDescending { it.timeStamp })
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error getting latest announcements", e)
            Result.failure(e)
        }
    }

    suspend fun updateAnnouncement(
        announcementId: String,
        updatedData: Map<String, Any>
    ): Result<Unit> {
        return try {
            announcementsRef.child(announcementId).updateChildren(updatedData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error updating announcement", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            announcementsRef.child(announcementId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error deleting announcement", e)
            Result.failure(e)
        }
    }
    
    suspend fun getAnnouncementsUpdatedAfter(timestamp: Long): Result<List<Announcement>> {
        return try {
            val snapshot = announcementsRef.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val announcements = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
            Result.success(announcements)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error getting updated announcements", e)
            Result.failure(e)
        }
    }
}