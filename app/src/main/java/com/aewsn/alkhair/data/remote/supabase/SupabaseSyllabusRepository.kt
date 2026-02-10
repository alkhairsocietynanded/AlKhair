package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.Syllabus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSyllabusRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createSyllabus(syllabus: Syllabus): Result<Syllabus> {
        return try {
            val result = supabase.from("syllabus").upsert(syllabus) {
                select()
            }.decodeSingle<Syllabus>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseSyllabusRepo", "Error creating syllabus", e)
            Result.failure(e)
        }
    }

    suspend fun saveSyllabusBatch(list: List<Syllabus>): Result<List<Syllabus>> {
        return try {
            val result = supabase.from("syllabus").upsert(list) {
                select()
            }.decodeList<Syllabus>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseSyllabusRepo", "Error saving batch syllabus", e)
            Result.failure(e)
        }
    }

    suspend fun getSyllabusForClassUpdatedAfter(
        classId: String,
        timestamp: Long
    ): Result<List<Syllabus>> {
        return try {
            val list = supabase.from("syllabus").select {
                filter {
                    Syllabus::classId eq classId
                    Syllabus::updatedAt gt timestamp
                }
            }.decodeList<Syllabus>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseSyllabusRepo", "Error getting syllabus for class $classId", e)
            Result.failure(e)
        }
    }

     // Global Sync (Admin/Teacher generic)
    suspend fun getSyllabusUpdatedAfter(timestamp: Long): Result<List<Syllabus>> {
        return try {
            val list = supabase.from("syllabus").select {
                filter {
                    Syllabus::updatedAt gt timestamp
                }
            }.decodeList<Syllabus>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseSyllabusRepo", "Error getting updated syllabus", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSyllabusBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("syllabus").delete {
                filter {
                    Syllabus::id isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Log.e("SupabaseSyllabusRepo", "Error deleting batch syllabus", e)
             Result.failure(e)
        }
    }
}
