package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalUserRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAuthRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseUserRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepoManager @Inject constructor(
    private val localUserRepository: LocalUserRepository,
    private val firebaseUserRepository: FirebaseUserRepository,
    private val firebaseAuthRepository: FirebaseAuthRepository
) : BaseRepoManager<User>() {

    /* ============================================================
       📦 READ — SSOT
       ============================================================ */
    override fun observeLocal(): Flow<List<User>> = localUserRepository.getAllUsers()

    fun observeUsersByRole(role: String): Flow<List<User>> = localUserRepository.getUsersByRole(role)

    suspend fun getUserById(uid: String): User? = localUserRepository.getUserById(uid).first()

    suspend fun getCurrentUser(): User? {
        val uid = firebaseAuthRepository.currentUserUid() ?: return null
        return getUserById(uid)
    }

    suspend fun getUsersByRole(role: String): Result<List<User>> {
        return try {
            val users = localUserRepository.getUsersByRole(role).first()
            Result.success(users)
        } catch (e: Exception) {
            Log.e("UserRepoManager", "Failed to get users by role: $role", e)
            Result.failure(e)
        }
    }

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    // 1. Global Sync (Admin)
    override suspend fun fetchRemoteUpdated(after: Long): List<User> =
        firebaseUserRepository.getUsersUpdatedAfter(after).getOrElse { emptyList() }

    // 2. Class Sync (Teacher) - ✅ NEW
    suspend fun syncClassStudents(classId: String, lastSync: Long): Result<Unit> {
        return firebaseUserRepository.getStudentsForClassUpdatedAfter(classId, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) insertLocal(list)
            }
            .map { }
    }

    // 3. Profile Sync (Student) - ✅ NEW
    suspend fun syncUserProfile(uid: String): Result<Unit> {
        return firebaseUserRepository.getUserById(uid)
            .onSuccess { user ->
                if (user != null) insertLocal(user)
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE OPERATIONS
       ============================================================ */

    suspend fun createUser(user: User): Result<User> {
        // UID handled by AuthRepo or passed in object
        return firebaseUserRepository.createUser(user)
            .onSuccess { newUser -> insertLocal(newUser) }
    }

    suspend fun updateUser(user: User): Result<User> {
        return firebaseUserRepository.updateUser(user)
            .onSuccess { updatedUser -> insertLocal(updatedUser) }
    }

    suspend fun saveUserLocally(user: User) { insertLocal(user) }

    suspend fun deleteUser(uid: String): Result<Unit> {
        return firebaseUserRepository.deleteUser(uid).onSuccess {
            deleteLocally(uid)
            try {
                val deletedRecord = DeletedRecord(
                    id = uid, type = "user", timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(uid).setValue(deletedRecord).await()
            } catch (e: Exception) {
                Log.e("UserRepoManager", "Failed to set tombstone", e)
            }
        }
    }

    // Base Impl
    override suspend fun insertLocal(items: List<User>) = localUserRepository.insertUsers(items)
    override suspend fun insertLocal(item: User) = localUserRepository.insertUser(item)
    override suspend fun deleteLocally(id: String) = localUserRepository.deleteUser(id)
}