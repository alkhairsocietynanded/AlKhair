package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.DivisionModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDivisionRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun addDivision(division: DivisionModel): Result<DivisionModel> {
        return try {
            val result = supabase.from("divisions").upsert(division) {
                select()
            }.decodeSingle<DivisionModel>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error adding division", e)
            Result.failure(e)
        }
    }

    suspend fun updateDivision(division: DivisionModel): Result<Unit> {
        return try {
            supabase.from("divisions").upsert(division)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error updating division", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDivision(divisionId: String): Result<Unit> {
        return try {
            supabase.from("divisions").delete {
                 filter {
                    eq("id", divisionId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error deleting division", e)
            Result.failure(e)
        }
    }

    suspend fun getAllDivisions(): Result<List<DivisionModel>> {
        return try {
            val list = supabase.from("divisions").select().decodeList<DivisionModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error getting all divisions", e)
            Result.failure(e)
        }
    }

    suspend fun doesDivisionExist(divisionName: String): Result<Boolean> {
        return try {
            val count = supabase.from("divisions").select {
                count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                filter {
                    eq("name", divisionName)
                }
            }.countOrNull() ?: 0
            Result.success(count > 0)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error checking if division exists: $divisionName", e)
            Result.failure(e)
        }
    }

    suspend fun getDivisionsUpdatedAfter(timestamp: Long): Result<List<DivisionModel>> {
        return try {
            val list = supabase.from("divisions").select {
                filter {
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<DivisionModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error getting updated divisions", e)
            Result.failure(e)
        }
    }
    
    suspend fun saveDivisionBatch(divisionList: List<DivisionModel>): Result<Unit> {
         return try {
            if (divisionList.isEmpty()) return Result.success(Unit)
            supabase.from("divisions").upsert(divisionList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseDivisionRepo", "Error saving batch division", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteDivisionBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("divisions").delete {
                filter {
                    isIn("id", ids)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }
}
