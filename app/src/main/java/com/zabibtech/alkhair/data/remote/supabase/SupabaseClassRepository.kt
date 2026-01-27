package com.zabibtech.alkhair.data.remote.supabase

import android.util.Log
import com.zabibtech.alkhair.data.models.ClassModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClassRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        return try {
            val result = supabase.from("classes").upsert(classModel) {
                select()
            }.decodeSingle<ClassModel>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error adding class", e)
            Result.failure(e)
        }
    }

    suspend fun updateClass(classModel: ClassModel): Result<Unit> {
        return try {
            supabase.from("classes").upsert(classModel)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error updating class", e)
            Result.failure(e)
        }
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            supabase.from("classes").delete {
                filter {
                    ClassModel::id eq classId
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error deleting class", e)
            Result.failure(e)
        }
    }

    suspend fun getAllClasses(): Result<List<ClassModel>> {
        return try {
            val list = supabase.from("classes").select().decodeList<ClassModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error getting all classes", e)
            Result.failure(e)
        }
    }

    // Helper for Teacher Dashboard / Filters (if needed)
    suspend fun getClassesByDivision(divisionName: String): Result<List<ClassModel>> {
        return try {
            val list = supabase.from("classes").select {
                filter {
                    ClassModel::divisionName eq divisionName
                }
            }.decodeList<ClassModel>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClassesUpdatedAfter(timestamp: Long): Result<List<ClassModel>> {
        return try {
            val list = supabase.from("classes").select {
                filter {
                    ClassModel::updatedAt gt timestamp
                }
            }.decodeList<ClassModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error getting updated classes", e)
            Result.failure(e)
        }
    }

    suspend fun saveClassBatch(classList: List<ClassModel>): Result<Unit> {
         return try {
            if (classList.isEmpty()) return Result.success(Unit)
            supabase.from("classes").upsert(classList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseClassRepo", "Error saving batch class", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteClassBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("classes").delete {
                filter {
                    ClassModel::id isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }
}
