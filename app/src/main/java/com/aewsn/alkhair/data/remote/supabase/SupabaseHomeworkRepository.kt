package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.Homework
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseHomeworkRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createHomework(homework: Homework): Result<Homework> {
        return try {
            val result = supabase.from("homework").upsert(homework) {
                select()
            }.decodeSingle<Homework>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseHomeworkRepo", "Error creating homework", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateHomework(homeworkId: String, updatedData: Map<String, Any>): Result<Unit> {
         return try {
             // Supabase allows standard updates. We can update by ID using the map.
             // Or better, just upsert if we have the object. But here we have a Map.
             // We'll update specific columns.
             supabase.from("homework").update(updatedData) {
                 filter {
                     eq("id", homeworkId)
                 }
             }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseHomeworkRepo", "Error updating homework with ID $homeworkId", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkForClassAndShiftUpdatedAfter(
        classId: String,
        shift: String,
        timestamp: Long
    ): Result<List<Homework>> {
        return try {
            val safeShift = shift.ifBlank { "General" }
            val list = supabase.from("homework").select {
                filter {
                    eq("class_id", classId)
                    gt("updated_at_ms", timestamp)
                    if (safeShift != "All") {
                        eq("shift", safeShift)
                    }
                }
            }.decodeList<Homework>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHomeworkUpdatedAfter(timestamp: Long): Result<List<Homework>> {
        return try {
            val list = supabase.from("homework").select {
                filter {
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<Homework>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseHomeworkRepo", "Error getting updated homework", e)
            Result.failure(e)
        }
    }

    suspend fun saveHomeworkBatch(homeworkList: List<Homework>): Result<Unit> {
        return try {
            if (homeworkList.isEmpty()) return Result.success(Unit)
            supabase.from("homework").upsert(homeworkList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseHomeworkRepo", "Error saving batch homework", e)
            Result.failure(e)
        }
    }

    suspend fun deleteHomeworkBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("homework").delete {
                filter {
                    isIn("id", ids)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Log.e("SupabaseHomeworkRepo", "Error deleting batch homework", e)
             Result.failure(e)
        }
    }
}
