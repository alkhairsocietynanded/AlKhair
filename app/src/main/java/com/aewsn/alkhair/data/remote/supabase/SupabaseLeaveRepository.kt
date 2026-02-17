package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.Leave
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseLeaveRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun applyLeave(leave: Leave): Result<Leave> {
        return try {
            val result = supabase.from("leaves").upsert(leave) {
                select()
            }.decodeSingle<Leave>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseLeaveRepo", "Error applying leave: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteLeave(id: String): Result<Unit> {
        return try {
            supabase.from("leaves").delete {
                filter {
                    eq("id", id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseLeaveRepo", "Error deleting leave: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLeavesForStudent(studentId: String, after: Long): List<Leave> {
        return try {
            supabase.from("leaves").select {
                filter {
                    eq("user_id", studentId)
                    gt("updated_at_ms", after)
                }
            }.decodeList()
        } catch (e: Exception) {
            Log.e("SupabaseLeaveRepo", "Error fetching leaves: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchLeavesForStudents(studentIds: List<String>, after: Long): List<Leave> {
        if (studentIds.isEmpty()) return emptyList()
        return try {
            supabase.from("leaves").select {
                filter {
                    isIn("user_id", studentIds)
                    gt("updated_at_ms", after)
                }
            }.decodeList()
        } catch (e: Exception) {
            Log.e("SupabaseLeaveRepo", "Error fetching batch leaves: ${e.message}", e)
            emptyList()
        }
    }
    
    // For Admin: Fetch all
    suspend fun fetchAllLeaves(after: Long): List<Leave> {
        return try {
            supabase.from("leaves").select {
                filter {
                    gt("updated_at_ms", after)
                }
            }.decodeList()
        } catch (e: Exception) {
             Log.e("SupabaseLeaveRepo", "Error fetching all leaves: ${e.message}", e)
             emptyList()
        }
    }
}
