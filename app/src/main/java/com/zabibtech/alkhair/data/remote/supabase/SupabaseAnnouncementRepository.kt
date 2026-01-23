package com.zabibtech.alkhair.data.remote.supabase

import android.util.Log
import com.zabibtech.alkhair.data.models.Announcement
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAnnouncementRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createAnnouncement(announcement: Announcement): Result<Announcement> {
        return try {
            val result = supabase.from("announcements").upsert(announcement) {
                select()
            }.decodeSingle<Announcement>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error creating announcement", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        return try {
            supabase.from("announcements").upsert(announcement)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error updating announcement", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            supabase.from("announcements").delete {
                filter {
                    Announcement::id eq announcementId
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error deleting announcement", e)
            Result.failure(e)
        }
    }

    suspend fun getAnnouncementsUpdatedAfter(timestamp: Long): Result<List<Announcement>> {
        return try {
            val list = supabase.from("announcements").select {
                filter {
                    Announcement::updatedAt gt timestamp
                }
            }.decodeList<Announcement>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error fetching global announcements", e)
            Result.failure(e)
        }
    }

    suspend fun getAnnouncementsForTargetUpdatedAfter(target: String, timestamp: Long): Result<List<Announcement>> {
        return try {
            val list = supabase.from("announcements").select {
                filter {
                    Announcement::target eq target
                    Announcement::updatedAt gt timestamp
                }
            }.decodeList<Announcement>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error fetching targeted announcements", e)
            Result.failure(e)
        }
    }

    suspend fun saveAnnouncementBatch(announcementList: List<Announcement>): Result<Unit> {
        return try {
            if (announcementList.isEmpty()) return Result.success(Unit)
            supabase.from("announcements").upsert(announcementList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAnnouncementRepo", "Error saving batch announcement", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncementBatch(ids: List<String>): Result<Unit> {
        return try {
             supabase.from("announcements").delete {
                filter {
                    Announcement::id isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Log.e("SupabaseAnnouncementRepo", "Error deleting batch announcement", e)
             Result.failure(e)
        }
    }
}
