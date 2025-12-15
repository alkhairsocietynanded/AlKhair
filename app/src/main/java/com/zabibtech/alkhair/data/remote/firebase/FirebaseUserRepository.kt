package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.FirebaseRefs.usersDb
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor() {
    suspend fun createUser(user: User): Result<User> {
        return try {
            usersDb.child(user.uid).setValue(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error creating user", e)
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User): Result<User> {
        return try {
            usersDb.child(user.uid).setValue(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error updating user", e)
            Result.failure(e)
        }
    }

    suspend fun getUserById(uid: String): Result<User?> {
        return try {
            val user = usersDb.child(uid).get().await().getValue(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting user by id", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> {
        return try {
            usersDb.child(uid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error deleting user", e)
            Result.failure(e)
        }
    }

    suspend fun getUsersByRole(role: String): Result<List<User>> {
        return try {
            val snapshot = usersDb.orderByChild("role").equalTo(role).get().await()
            val users = snapshot.children
                .mapNotNull { it.getValue(User::class.java) }
                .sortedBy { it.name.lowercase() }
            Result.success(users)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting users by role", e)
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = usersDb.get().await()
            val users = snapshot.children
                .mapNotNull { it.getValue(User::class.java) }
                .sortedBy { it.name.lowercase() }
            Result.success(users)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting all users", e)
            Result.failure(e)
        }
    }

    suspend fun getUsersUpdatedAfter(timestamp: Long): Result<List<User>> {
        return try {
            val snapshot =
                usersDb.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            Log.d("FirebaseUserRepository", "Fetched ${users.size} users updated after $timestamp")
            Result.success(users)

        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting updated users", e)
            Result.failure(e)
        }
    }
}