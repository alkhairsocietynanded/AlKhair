package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseUserRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    // ✅ CREATE / UPDATE using Upsert
    suspend fun createUser(user: User): Result<User> {
        return saveUser(user)
    }

    suspend fun updateUser(user: User): Result<User> {
        return saveUser(user)
    }

    private suspend fun saveUser(user: User): Result<User> {
        return try {
            val currentTime = System.currentTimeMillis()
            val finalUser = user.copy(updatedAt = currentTime)
            
            // Supabase "users" table. upsert matches on Primary Key (uid).
            supabase.from("users").upsert(finalUser) {
                select() // Return the inserted row to confirm
            }.decodeSingle<User>()
            
            Result.success(finalUser)
        } catch (e: Exception) {
            Log.e("SupabaseUserRepo", "Error saving user", e)
            Result.failure(e)
        }
    }
    
    // ✅ Batch Save
    suspend fun saveUsersBatch(users: List<User>): Result<Unit> {
        return try {
            if (users.isEmpty()) return Result.success(Unit)
            val currentTime = System.currentTimeMillis()
            val finalList = users.map { it.copy(updatedAt = currentTime) }
            
            supabase.from("users").upsert(finalList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseUserRepo", "Error saving batch users", e)
            Result.failure(e)
        }
    }

    // ✅ SYNC: Get Users Updated After Timestamp
    suspend fun getUsersUpdatedAfter(timestamp: Long): Result<List<User>> {
        return try {
            val list = supabase.from("users").select {
                filter {
                    User::updatedAt gt timestamp
                }
            }.decodeList<User>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseUserRepo", "Error fetching updated users", e)
            Result.failure(e)
        }
    }

    // ✅ CLASS SYNC: Get Students for Class Updated After
    suspend fun getStudentsForClassUpdatedAfter(
        classId: String,
        shift: String, // Kept for interface compatibility but can be filtered if needed
        timestamp: Long
    ): Result<List<User>> {
        return try {
            val list = supabase.from("users").select {
                filter {
                     User::classId eq classId
                     User::updatedAt gt timestamp
                     // If we want to filter by shift too:
                     if (shift.isNotBlank() && shift != "General" && shift != "All") {
                         User::shift eq shift
                     }
                }
            }.decodeList<User>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseUserRepo", "Error fetching class students", e)
            Result.failure(e)
        }
    }

    // ✅ GET SINGLE USER
    suspend fun getUserById(uid: String): Result<User?> {
        return try {
            val user = supabase.from("users").select {
                filter {
                    User::uid eq uid
                }
            }.decodeSingleOrNull<User>()
            Result.success(user)
        } catch (e: Exception) {
            Log.e("SupabaseUserRepo", "Error fetching user by id", e)
            Result.failure(e)
        }
    }
    
    // ✅ DELETE
    suspend fun deleteUser(uid: String): Result<Unit> {
        return try {
            supabase.from("users").delete {
                filter {
                    User::uid eq uid
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Batch Delete? - Usually we soft delete or handle via sync, but basic impl:
    suspend fun deleteUsersBatch(ids: List<String>): Result<Unit> {
        return try {
             supabase.from("users").delete {
                filter {
                    User::uid isIn ids
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }
}
