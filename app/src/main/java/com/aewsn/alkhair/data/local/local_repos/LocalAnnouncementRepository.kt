package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.AnnouncementDao
import com.aewsn.alkhair.data.models.Announcement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAnnouncementRepository @Inject constructor(
    private val announcementDao: AnnouncementDao
) {
    fun getAllAnnouncements(): Flow<List<Announcement>> =
        announcementDao.getAllAnnouncements()

    // ✅ Renamed/Aliased function as requested
    fun getFiveLatestAnnouncementsFlow(): Flow<List<Announcement>> =
        announcementDao.getFiveLatestAnnouncements()

    fun getAnnouncementById(announcementId: String): Flow<Announcement?> =
        announcementDao.getAnnouncementById(announcementId)

    suspend fun insertAnnouncement(announcement: Announcement) =
        announcementDao.insertAnnouncement(announcement)

    suspend fun insertAnnouncements(announcements: List<Announcement>) =
        announcementDao.insertAnnouncements(announcements)

    suspend fun updateAnnouncement(announcement: Announcement) =
        announcementDao.updateAnnouncement(announcement)

    suspend fun deleteAnnouncement(announcementId: String) =
        announcementDao.deleteAnnouncementById(announcementId)

    suspend fun clearAll() = announcementDao.clearAllAnnouncements()

    suspend fun getUnsyncedAnnouncements(): List<Announcement> = announcementDao.getUnsyncedAnnouncements()

    suspend fun markAnnouncementsAsSynced(ids: List<String>) = announcementDao.markAnnouncementsAsSynced(ids)
}