package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.FeesModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFeesRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun saveFee(feesModel: FeesModel): Result<FeesModel> {
        return try {
            val result = supabase.from("fees").upsert(feesModel) {
                select()
            }.decodeSingle<FeesModel>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error saving fee", e)
            Result.failure(e)
        }
    }

    suspend fun updateFee(feesModel: FeesModel): Result<Unit> {
        return try {
            supabase.from("fees").upsert(feesModel)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error updating fee", e)
            Result.failure(e)
        }
    }

    // ✅ TEACHER SYNC (Class + Shift)
    suspend fun getFeesForClassAndShiftUpdatedAfter(
        classId: String,
        shift: String,
        timestamp: Long
    ): Result<List<FeesModel>> {
        return try {
            val safeShift = shift.ifBlank { "General" }
            val list = supabase.from("fees").select {
                filter {
                    eq("class_id", classId)
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<FeesModel>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ STUDENT SYNC
    suspend fun getFeesForStudentUpdatedAfter(
        studentId: String,
        timestamp: Long
    ): Result<List<FeesModel>> {
        return try {
            val list = supabase.from("fees").select {
                filter {
                    eq("user_id", studentId)
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<FeesModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error fetching student fees sync", e)
            Result.failure(e)
        }
    }

    // ✅ ADMIN SYNC (Global)
    suspend fun getFeesUpdatedAfter(timestamp: Long): Result<List<FeesModel>> {
        return try {
            val list = supabase.from("fees").select {
                filter {
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<FeesModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error getting updated fees global", e)
            Result.failure(e)
        }
    }

    // --- Standard Methods ---



    suspend fun getFee(feeId: String): Result<FeesModel> {
        return try {
            val fee = supabase.from("fees").select {
                filter {
                    eq("id", feeId)
                }
            }.decodeSingleOrNull<FeesModel>()

            if (fee != null) Result.success(fee)
            else Result.failure(NoSuchElementException("Fee not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFee(feeId: String): Result<Unit> {
        return try {
            supabase.from("fees").delete {
                filter {
                    eq("id", feeId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFeesBatch(feesList: List<FeesModel>): Result<Unit> {
        return try {
            if (feesList.isEmpty()) return Result.success(Unit)
            supabase.from("fees").upsert(feesList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error saving batch fees", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFeesBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("fees").delete {
                filter {
                    isIn("id", ids)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseFeesRepo", "Error deleting batch fees", e)
            Result.failure(e)
        }
    }
}
