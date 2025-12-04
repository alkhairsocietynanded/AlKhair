package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.AnnouncementDao
import com.zabibtech.alkhair.data.models.Announcement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAnnouncementRepository @Inject constructor(
    private val announcementDao: AnnouncementDao
) {
    fun getAllAnnouncements(): Flow<List<Announcement>> = announcementDao.getAllAnnouncements()

    suspend fun insertAnnouncement(announcement: Announcement) = 
        announcementDao.insertAnnouncement(announcement)

    suspend fun insertAnnouncements(announcements: List<Announcement>) = 
        announcementDao.insertAnnouncements(announcements)

    suspend fun clearAll() = announcementDao.clearAllAnnouncements()

    // New methods added for local-first architecture support

    fun getAnnouncementById(announcementId: String): Flow<Announcement?> =
        announcementDao.getAnnouncementById(announcementId)

    fun getFiveLatestAnnouncements(): Flow<List<Announcement>> =
        announcementDao.getFiveLatestAnnouncements()

    suspend fun updateAnnouncement(announcement: Announcement) =
        announcementDao.updateAnnouncement(announcement)

    suspend fun deleteAnnouncement(announcementId: String) =
        announcementDao.deleteAnnouncementById(announcementId)
}