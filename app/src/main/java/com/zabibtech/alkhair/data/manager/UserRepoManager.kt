package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalUserRepository
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAuthRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseUserRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepoManager @Inject constructor(
    private val localUserRepository: LocalUserRepository,
    private val firebaseUserRepository: FirebaseUserRepository,
    private val firebaseAuthRepository: FirebaseAuthRepository
) {

    suspend fun createUser(user: User): Result<User> {
        val result = firebaseUserRepository.createUser(user)
        result.onSuccess { newUser ->
            try {
                localUserRepository.insertUser(newUser.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to cache created user locally", e)
            }
        }
        return result
    }

    suspend fun updateUser(user: User): Result<User> {
        // Ensure the updatedAt timestamp is fresh for the remote and local update
        val userToUpdate = user.copy(updatedAt = System.currentTimeMillis())
        val result = firebaseUserRepository.updateUser(userToUpdate)
        result.onSuccess { _ ->
            try {
                localUserRepository.insertUser(userToUpdate)
            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to cache updated user locally", e)
            }
        }
        return result
    }

    suspend fun getUserById(uid: String): Result<User?> {
        // Simple Getter: Always returns local data. Syncing is a separate concern.
        return try {
            val localUser = localUserRepository.getUserById(uid).first()
            Result.success(localUser)
        } catch (e: Exception) {
            Log.e("UserRepoManager", "Could not get local user by id: $uid", e)
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User?> {
        val uid = firebaseAuthRepository.currentUserUid()
        return if (uid != null) {
            getUserById(uid)
        } else {
            Result.success(null)
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> {
        val result = firebaseUserRepository.deleteUser(uid)
        result.onSuccess {
            try {
                localUserRepository.deleteUser(uid)

                val deletedRecord = DeletedRecord(id = uid, type = "user", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(uid).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to process user deletion for ID: $uid", e)
            }
        }
        return result
    }

    suspend fun deleteUserLocally(uid: String) {
        try {
            localUserRepository.deleteUser(uid)
        } catch (e: Exception) {
            Log.e("UserRepoManager", "Failed to delete local user: $uid", e)
        }
    }

    suspend fun syncUsers(lastSync: Long) {
        firebaseUserRepository.getUsersUpdatedAfter(lastSync).onSuccess { users ->
            Log.d("UserRepoManager", "Synced ${users.size} users from Firebase")
            if (users.isNotEmpty()) {
                try {
                    val updatedList = users.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localUserRepository.insertUsers(updatedList)
                } catch (e: Exception) {
                    Log.e("UserRepoManager", "Failed to cache synced users", e)
                }
            }
        }
    }

    suspend fun getUsersByRole(role: String): Result<List<User>> {
        // Simple Getter: Relies on local data, which is kept fresh by syncUsers
        return try {
            val localData = localUserRepository.getUsersByRole(role).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("UserRepoManager", "Could not get local users by role: $role", e)
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        val localData = try {
            localUserRepository.getAllUsers().first()
        } catch (e: Exception) {
            emptyList()
        }

        if (localData.isNotEmpty()) {
            return Result.success(localData)
        }

        // Fallback for first launch when local data is empty
        val remoteResult = firebaseUserRepository.getAllUsers()
        return remoteResult.fold(
            onSuccess = { remoteUsers ->
                try {
                    val updated = remoteUsers.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localUserRepository.insertUsers(updated)
                } catch (e: Exception) {
                    Log.e("UserRepoManager", "Failed to cache initial users", e)
                }
                Result.success(remoteUsers)
            },
            onFailure = { exception ->
                Result.failure(exception)
            }
        )
    }
}
