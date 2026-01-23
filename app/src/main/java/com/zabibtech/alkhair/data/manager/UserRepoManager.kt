package com.zabibtech.alkhair.data.manager

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalUserRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.PendingDeletion
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.supabase.SupabaseAuthRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseUserRepository
import com.zabibtech.alkhair.data.worker.UserUploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepoManager @Inject constructor(
    private val localUserRepository: LocalUserRepository,
    private val supabaseUserRepository: SupabaseUserRepository,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager
) : BaseRepoManager<User>() {

    /* ============================================================
       📦 READ — SSOT
       ============================================================ */
    override fun observeLocal(): Flow<List<User>> = localUserRepository.getAllUsers()

    fun observeUsersByRole(role: String): Flow<List<User>> = localUserRepository.getUsersByRole(role)

    suspend fun getUserById(uid: String): User? = localUserRepository.getUserById(uid).first()

    suspend fun getCurrentUser(): User? {
        val uid = supabaseAuthRepository.currentUserUid() ?: return null
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
        supabaseUserRepository.getUsersUpdatedAfter(after).getOrElse { emptyList() }

    // 2. Class Sync (Teacher) - ✅ NEW
    suspend fun syncClassStudents(classId: String, shift: String,  lastSync: Long): Result<Unit> {
        // Shift validation (taaki empty shift par crash na ho)
        val targetShift = shift.ifBlank { "General" }
        return supabaseUserRepository.getStudentsForClassUpdatedAfter(classId, targetShift,lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) insertLocal(list)
            }
            .map { }
    }

    // 3. Profile Sync (Student) - ✅ NEW
    suspend fun syncUserProfile(uid: String): Result<Unit> {
        return supabaseUserRepository.getUserById(uid)
            .onSuccess { user ->
                if (user != null) insertLocal(user)
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createUser(user: User): Result<User> {
        val newUser = user.copy(
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        insertLocal(newUser)
        scheduleUploadWorker()
        return Result.success(newUser)
    }

    suspend fun updateUser(user: User): Result<User> {
        val updatedUser = user.copy(
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        insertLocal(updatedUser)
        scheduleUploadWorker()
        return Result.success(updatedUser)
    }

    suspend fun saveUserLocally(user: User) { insertLocal(user) }

    suspend fun deleteUser(uid: String): Result<Unit> {
        deleteLocally(uid)
        val pendingDeletion = PendingDeletion(
            id = uid,
            type = "USER", // generic type should be consistent
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

     private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UserUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "UserUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    // Base Impl
    override suspend fun insertLocal(items: List<User>) = localUserRepository.insertUsers(items)
    override suspend fun insertLocal(item: User) = localUserRepository.insertUser(item)
    override suspend fun deleteLocally(id: String) = localUserRepository.deleteUser(id)
    override suspend fun clearLocal() = localUserRepository.clearAll()
}