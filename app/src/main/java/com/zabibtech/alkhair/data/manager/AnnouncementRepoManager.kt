package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAnnouncementRepository
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAnnouncementRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
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
                // Remote operation was successful, so we don't propagate the exception.
                // The cache will be updated on the next fetch.
            }
        }
        return result
    }

    suspend fun getAllAnnouncements(): Result<List<Announcement>> {
        val localData = localAnnouncementRepo.getAllAnnouncements().first()

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAnnouncementRepo.getAllAnnouncements()
        return remoteResult.fold(
            onSuccess = { remoteAnnouncements ->
                try {
                    val updated = remoteAnnouncements.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localAnnouncementRepo.insertAnnouncements(updated)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepoManager", "Failed to cache all announcements", e)
                }
                Result.success(remoteAnnouncements)
            },
            onFailure = { exception ->
                // On remote failure, return local data if available, otherwise return the error
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getFiveLatestAnnouncements(): Result<List<Announcement>> {
        val localData = localAnnouncementRepo.getFiveLatestAnnouncements().first()

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAnnouncementRepo.getFiveLatestAnnouncements()
        return remoteResult.fold(
            onSuccess = { remoteAnnouncements ->
                try {
                    val updated = remoteAnnouncements.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localAnnouncementRepo.insertAnnouncements(updated)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepoManager", "Failed to cache latest announcements", e)
                }
                Result.success(remoteAnnouncements)
            },
            onFailure = { exception ->
                 // On remote failure, return local data if available, otherwise return the error
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        val firebaseUpdateMap = mapOf(
            "title" to announcement.title,
            "content" to announcement.content,
            "timeStamp" to announcement.timeStamp,
            "updatedAt" to System.currentTimeMillis()
        )

        val result = firebaseAnnouncementRepo.updateAnnouncement(announcement.id, firebaseUpdateMap)
        result.onSuccess {
            try {
                localAnnouncementRepo.updateAnnouncement(announcement.copy(updatedAt = System.currentTimeMillis()))
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
            } catch (e: Exception) {
                Log.e("AnnouncementRepoManager", "Failed to delete announcement from local cache", e)
            }
        }
        return result
    }
}