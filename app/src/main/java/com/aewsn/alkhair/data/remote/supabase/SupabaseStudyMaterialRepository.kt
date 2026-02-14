package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.StudyMaterial
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseStudyMaterialRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createStudyMaterial(item: StudyMaterial): Result<StudyMaterial> {
        return try {
            val result = supabase.from("study_materials").upsert(item) {
                select()
            }.decodeSingle<StudyMaterial>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseStudyMaterialRepo", "Error creating study material", e)
            Result.failure(e)
        }
    }

    suspend fun saveBatch(list: List<StudyMaterial>): Result<List<StudyMaterial>> {
        return try {
            val result = supabase.from("study_materials").upsert(list) {
                select()
            }.decodeList<StudyMaterial>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseStudyMaterialRepo", "Error saving batch study materials", e)
            Result.failure(e)
        }
    }

    suspend fun getForClassUpdatedAfter(
        classId: String,
        timestamp: Long
    ): Result<List<StudyMaterial>> {
        return try {
            val list = supabase.from("study_materials").select {
                filter {
                    StudyMaterial::classId eq classId
                    StudyMaterial::updatedAt gt timestamp
                }
            }.decodeList<StudyMaterial>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseStudyMaterialRepo", "Error getting study materials for class $classId", e)
            Result.failure(e)
        }
    }

    // Global Sync (Admin/Teacher generic)
    suspend fun getUpdatedAfter(timestamp: Long): Result<List<StudyMaterial>> {
        return try {
            val list = supabase.from("study_materials").select {
                filter {
                    StudyMaterial::updatedAt gt timestamp
                }
            }.decodeList<StudyMaterial>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseStudyMaterialRepo", "Error getting updated study materials", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("study_materials").delete {
                filter {
                    StudyMaterial::id isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseStudyMaterialRepo", "Error deleting batch study materials", e)
            Result.failure(e)
        }
    }
}
