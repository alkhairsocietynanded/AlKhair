package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.FirebaseRefs.announcementsRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnnouncementRepository @Inject constructor() {

    // =========================================================================
    // ✍️ WRITE OPERATIONS (Create, Update, Delete)
    // =========================================================================

    suspend fun createAnnouncement(announcement: Announcement): Result<Announcement> {
        return try {
            // 1. Ensure ID exists
            val announcementId = announcement.id.ifEmpty { announcementsRef.push().key!! }
            val currentTime = System.currentTimeMillis()

            // 2. Prepare Final Object
            val finalAnnouncement = announcement.copy(
                id = announcementId,
                updatedAt = currentTime
            )

            // 3. Convert to Map for Composite Key ("target_sync_key")
            // Format: "TARGET_TIMESTAMP" (e.g., "ALL_1766...", "Class10_1766...")
            val firebaseMap = mapOf(
                "id" to finalAnnouncement.id,
                "title" to finalAnnouncement.title,
                "content" to finalAnnouncement.content,
                "timeStamp" to finalAnnouncement.timeStamp,
                "target" to finalAnnouncement.target,
                "updatedAt" to finalAnnouncement.updatedAt,

                // 🔥 Optimization Key for Role-Based Sync
                "target_sync_key" to "${finalAnnouncement.target}_$currentTime"
            )

            announcementsRef.child(announcementId).setValue(firebaseMap).await()
            Result.success(finalAnnouncement)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error creating announcement", e)
            Result.failure(e)
        }
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        return try {
            val currentTime = System.currentTimeMillis()

            // Update Map ensures we update the Sync Key if the Target changes
            val updateMap = mapOf(
                "title" to announcement.title,
                "content" to announcement.content,
                "timeStamp" to announcement.timeStamp,
                "target" to announcement.target,
                "updatedAt" to currentTime,

                // 🔥 Update Composite Key as well
                "target_sync_key" to "${announcement.target}_$currentTime"
            )

            announcementsRef.child(announcement.id).updateChildren(updateMap).await()
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

    // =========================================================================
    // 🔁 SYNC OPERATIONS (Read)
    // =========================================================================

    /**
     * ✅ GLOBAL SYNC (For Admin)
     * Fetches ALL updates regardless of target.
     */
    suspend fun getAnnouncementsUpdatedAfter(timestamp: Long): Result<List<Announcement>> {
        return try {
            val snapshot = announcementsRef
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble()) // +1 to avoid duplicates
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error fetching global announcements", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ TARGETED SYNC (For Student/Teacher)
     * Fetches ONLY announcements meant for a specific target (e.g., "ALL" or "Class_10").
     * Uses composite key for filtering.
     */
    suspend fun getAnnouncementsForTargetUpdatedAfter(target: String, timestamp: Long): Result<List<Announcement>> {
        return try {
            // Start Search: "Target_LastSyncTime"
            val startKey = "${target}_${timestamp + 1}"

            // End Search: "Target_FarFuture"
            val endKey = "${target}_9999999999999"

            val snapshot = announcementsRef
                .orderByChild("target_sync_key") // Requires Index in Firebase Rules
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAnnouncementRepo", "Error fetching targeted announcements", e)
            Result.failure(e)
        }
    }

    // Legacy method (if needed for initial full load without sync logic)
    suspend fun getAllAnnouncements(): Result<List<Announcement>> {
        return try {
            val snapshot = announcementsRef.get().await()
            val list = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}