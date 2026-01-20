package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update // Import for @Update annotation
import com.zabibtech.alkhair.data.models.Announcement
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: Announcement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(announcements: List<Announcement>)

    @Query("SELECT * FROM announcements ORDER BY timeStamp DESC")
    fun getAllAnnouncements(): Flow<List<Announcement>>

    @Query("DELETE FROM announcements")
    suspend fun clearAllAnnouncements()

    // New methods added to support LocalAnnouncementRepository

    @Query("SELECT * FROM announcements ORDER BY timeStamp DESC LIMIT 5")
    fun getFiveLatestAnnouncements(): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE id = :announcementId")
    fun getAnnouncementById(announcementId: String): Flow<Announcement?>

    @Update(onConflict = OnConflictStrategy.REPLACE) // Use @Update for updating existing entities
    suspend fun updateAnnouncement(announcement: Announcement)

    @Query("DELETE FROM announcements WHERE id = :announcementId")
    suspend fun deleteAnnouncementById(announcementId: String)

    @Query("SELECT * FROM announcements WHERE isSynced = 0")
    suspend fun getUnsyncedAnnouncements(): List<Announcement>

    @Query("UPDATE announcements SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAnnouncementsAsSynced(ids: List<String>)
}