package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.FirebaseRefs.announcementsRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Deprecated("Use AnnouncementRepoManager instead")
@Singleton
class AnnouncementRepository @Inject constructor() {


    suspend fun addAnnouncement(announcement: Announcement): Result<Unit> {
        return try {
            // Ek naya unique key generate karein aur uss par data set karein.
            // Agar announcement ID pehle se set nahi hai, toh push() ek naya ID banayega.
            val announcementId = announcement.id.ifEmpty { announcementsRef.push().key!! }
            announcementsRef.child(announcementId).setValue(announcement.copy(id = announcementId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllAnnouncements(): Result<List<Announcement>> {
        return try {
            val snapshot = announcementsRef.get().await()
            val announcements = snapshot.children.mapNotNull {
                it.getValue(Announcement::class.java)
            }
            // Hamesha latest announcement sabse upar dikhayein.
            Result.success(announcements.sortedByDescending { it.timeStamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFiveLatestAnnouncements(): Result<List<Announcement>> {
        return try {
            // ✅ Query ko 'timeStamp' ke hisab se order karein aur aakhri 5 results lein.
            val query = announcementsRef.orderByChild("timeStamp").limitToLast(5)
            val snapshot = query.get().await()
            val announcements = snapshot.children.mapNotNull {
                it.getValue(Announcement::class.java)
            }
            // ✅ Hamesha latest announcement sabse upar dikhayein (client-side sorting).
            Result.success(announcements.sortedByDescending { it.timeStamp })
        } catch (e: Exception) {
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
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            announcementsRef.child(announcementId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}