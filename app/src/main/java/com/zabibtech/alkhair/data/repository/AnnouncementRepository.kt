package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.FirebaseRefs.announcementsRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnnouncementRepository Firebase Realtime Database mein announcements se mutalliq
 * tamam data operations (CRUD - Create, Read, Update, Delete) ko manage karta hai.
 *
 * @Inject constructor() - Hilt isko dependency ke taur par inject karne ke liye istemal karta hai.
 * @Singleton - Yeh sunishchit karta hai ki poori app mein is repository ka sirf ek hi instance bane.
 */
@Singleton
class AnnouncementRepository @Inject constructor() {

    /**
     * Ek naya announcement database mein add karta hai.
     *
     * @param announcement Woh Announcement object jisko save karna hai.
     * @return Result<Unit> - Success ya failure ko represent karta hai.
     */
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

    /**
     * Database se saare announcements fetch karta hai.
     *
     * @return Result<List<Announcement>> - Announcements ki list ya error.
     */
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

    /**
     * Database se 5 sabse naye (latest) announcements fetch karta hai.
     *
     * @return Result<List<Announcement>> - 5 latest announcements ki list ya error.
     */
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

    /**
     * Ek maujooda announcement ko update karta hai.
     *
     * @param announcementId Uss announcement ki ID jisko update karna hai.
     * @param updatedData Woh Map jismein updated fields hain.
     * @return Result<Unit> - Success ya failure.
     */
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

    /**
     * Ek announcement ko uski ID se delete karta hai.
     *
     * @param announcementId Uss announcement ki ID jisko delete karna hai.
     * @return Result<Unit> - Success ya failure.
     */
    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            announcementsRef.child(announcementId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}