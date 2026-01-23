package com.zabibtech.alkhair.data.remote.supabase

import android.util.Log
import com.zabibtech.alkhair.data.models.SalaryModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSalaryRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createSalary(salary: SalaryModel): Result<SalaryModel> {
        return try {
            val result = supabase.from("salary").upsert(salary) {
                select()
            }.decodeSingle<SalaryModel>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("SupabaseSalaryRepo", "Error creating salary", e)
            Result.failure(e)
        }
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
         return try {
            supabase.from("salary").upsert(salary)
            Result.success(Unit)
        } catch (e: Exception) {
             Log.e("SupabaseSalaryRepo", "Error updating salary", e)
            Result.failure(e)
        }
    }
    
    // ✅ TEACHER SYNC (Targeted Staff Sync)
    suspend fun getSalariesForStaffUpdatedAfter(staffId: String, timestamp: Long): Result<List<SalaryModel>> {
        return try {
            val list = supabase.from("salary").select {
                filter {
                    SalaryModel::staffId eq staffId
                    SalaryModel::updatedAt gt timestamp
                }
            }.decodeList<SalaryModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseSalaryRepo", "Error fetching staff salaries", e)
            Result.failure(e)
        }
    }

    // ✅ ADMIN SYNC (Global)
    suspend fun getSalariesUpdatedAfter(timestamp: Long): Result<List<SalaryModel>> {
        return try {
            val list = supabase.from("salary").select {
                filter {
                    SalaryModel::updatedAt gt timestamp
                }
            }.decodeList<SalaryModel>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseSalaryRepo", "Error getting updated salaries", e)
            Result.failure(e)
        }
    }

    suspend fun getSalaryById(salaryId: String): Result<SalaryModel> {
        return try {
            val salary = supabase.from("salary").select {
                filter {
                    SalaryModel::id eq salaryId
                }
            }.decodeSingleOrNull<SalaryModel>()
            
            if (salary != null) Result.success(salary)
            else Result.failure(NoSuchElementException("Salary not found"))
        } catch (e: Exception) {
             Result.failure(e)
        }
    }
    
    suspend fun deleteSalary(salaryId: String): Result<Unit> {
        return try {
            supabase.from("salary").delete {
                 filter {
                    SalaryModel::id eq salaryId
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }

    suspend fun saveSalaryBatch(salaryList: List<SalaryModel>): Result<Unit> {
        return try {
            if (salaryList.isEmpty()) return Result.success(Unit)
            supabase.from("salary").upsert(salaryList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseSalaryRepo", "Error saving batch salary", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSalaryBatch(ids: List<String>): Result<Unit> {
        return try {
            supabase.from("salary").delete {
                filter {
                    SalaryModel::id isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Log.e("SupabaseSalaryRepo", "Error deleting batch salary", e)
             Result.failure(e)
        }
    }
}
