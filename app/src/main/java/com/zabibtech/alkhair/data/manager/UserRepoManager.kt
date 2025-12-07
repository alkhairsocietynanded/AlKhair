package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalUserRepository
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAuthRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseUserRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
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
        val result = firebaseUserRepository.updateUser(user)
        result.onSuccess { updatedUser ->
            try {
                localUserRepository.insertUser(updatedUser.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to cache updated user locally", e)
            }
        }
        return result
    }

    suspend fun getUserById(uid: String): Result<User?> {
        val localUser = try {
            localUserRepository.getUserById(uid).first()
        } catch (e: Exception) {
            Log.w("UserRepoManager", "Could not get local user by id", e)
            null
        }

        if (localUser != null && !StaleDetector.isStale(localUser.updatedAt)) {
            return Result.success(localUser)
        }

        val remoteResult = firebaseUserRepository.getUserById(uid)
        return remoteResult.fold(
            onSuccess = { remoteUser ->
                remoteUser?.let {
                    try {
                        localUserRepository.insertUser(it.copy(updatedAt = System.currentTimeMillis()))
                    } catch (e: Exception) {
                        Log.e("UserRepoManager", "Failed to cache user by id", e)
                    }
                }
                Result.success(remoteUser)
            },
            onFailure = { exception ->
                if (localUser != null) Result.success(localUser) else Result.failure(exception)
            }
        )
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
            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to delete user from local cache", e)
            }
        }
        return result
    }

    suspend fun getUsersByRole(role: String): Result<List<User>> {
        val localData = try {
            localUserRepository.getUsersByRole(role).first()
        } catch (e: Exception) {
            Log.w("UserRepoManager", "Could not get local users by role", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseUserRepository.getUsersByRole(role)
        return remoteResult.fold(
            onSuccess = { remoteUsers ->
                try {
                    val updated = remoteUsers.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localUserRepository.insertUsers(updated)
                } catch (e: Exception) {
                    Log.e("UserRepoManager", "Failed to cache users by role", e)
                }
                Result.success(remoteUsers)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getAllUsers(): Result<List<User>> {
        val localData = try {
            localUserRepository.getAllUsers().first()
        } catch (e: Exception) {
            Log.w("UserRepoManager", "Could not get all local users", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseUserRepository.getAllUsers()
        return remoteResult.fold(
            onSuccess = { remoteUsers ->
                try {
                    val updated = remoteUsers.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localUserRepository.insertUsers(updated)
                } catch (e: Exception) {
                    Log.e("UserRepoManager", "Failed to cache all users", e)
                }
                Result.success(remoteUsers)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }
}
