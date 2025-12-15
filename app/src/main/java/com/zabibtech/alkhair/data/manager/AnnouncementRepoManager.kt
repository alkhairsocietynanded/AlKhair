package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAnnouncementRepository
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAnnouncementRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepoManager @Inject constructor(
    private val localAnnouncementRepo: LocalAnnouncementRepository,
    private val firebaseAnnouncementRepo: FirebaseAnnouncementRepository
) {

    suspend fun createAnnouncement(announcement: Announcement): Result<Announcement> {
        val result = firebaseAnnouncementRepo.createAnnouncement(announcement)
        result.onSuccess { newAnnouncement ->
            try {
                localAnnouncementRepo.insertAnnouncement(newAnnouncement.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("AnnouncementRepoManager", "Failed to cache created announcement locally", e)
            }
        }
        return result
    }

    suspend fun getAllAnnouncements(): Result<List<Announcement>> {
        val localData = try {
            localAnnouncementRepo.getAllAnnouncements().first()
        } catch (e: Exception) {
            emptyList()
        }

        if (localData.isNotEmpty()) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAnnouncementRepo.getAllAnnouncements()
        return remoteResult.fold(
            onSuccess = { remoteAnnouncements ->
                try {
                    val updated = remoteAnnouncements.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localAnnouncementRepo.insertAnnouncements(updated)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepoManager", "Failed to cache initial announcements", e)
                }
                Result.success(remoteAnnouncements)
            },
            onFailure = { exception ->
                Result.failure(exception)
            }
        )
    }

    suspend fun syncAnnouncements(lastSync: Long) {
        firebaseAnnouncementRepo.getAnnouncementsUpdatedAfter(lastSync).onSuccess { announcements ->
            if (announcements.isNotEmpty()) {
                try {
                    // CORRECTED: Mark synced data as fresh
                    val updatedList = announcements.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localAnnouncementRepo.insertAnnouncements(updatedList)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepoManager", "Failed to cache synced announcements", e)
                }
            }
        }
    }

    // CORRECTED: This is now a Simple Getter
    suspend fun getFiveLatestAnnouncements(): Result<List<Announcement>> {
        return try {
            val localData = localAnnouncementRepo.getFiveLatestAnnouncements().first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("AnnouncementRepoManager", "Could not get five latest announcements from local db", e)
            Result.failure(e)
        }
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        val announcementToUpdate = announcement.copy(updatedAt = System.currentTimeMillis())
        val firebaseUpdateMap = mapOf(
            "title" to announcementToUpdate.title,
            "content" to announcementToUpdate.content,
            "timeStamp" to announcementToUpdate.timeStamp,
            "updatedAt" to announcementToUpdate.updatedAt
        )

        val result = firebaseAnnouncementRepo.updateAnnouncement(announcement.id, firebaseUpdateMap)
        result.onSuccess {
            try {
                localAnnouncementRepo.updateAnnouncement(announcementToUpdate)
            } catch (e: Exception) {
                Log.e("AnnouncementRepoManager", "Failed to cache updated announcement locally", e)
            }
        }
        return result
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        val result = firebaseAnnouncementRepo.deleteAnnouncement(announcementId)
        result.onSuccess {
            try {
                localAnnouncementRepo.deleteAnnouncement(announcementId)

                val deletedRecord = DeletedRecord(id = announcementId, type = "announcement", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(announcementId).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("AnnouncementRepoManager", "Failed to process announcement deletion for ID: $announcementId", e)
            }
        }
        return result
    }

    suspend fun deleteAnnouncementLocally(id: String) {
        try {
            localAnnouncementRepo.deleteAnnouncement(id)
        } catch (e: Exception) {
            Log.e("AnnouncementRepoManager", "Failed to delete announcement locally: $id", e)
        }
    }
}
