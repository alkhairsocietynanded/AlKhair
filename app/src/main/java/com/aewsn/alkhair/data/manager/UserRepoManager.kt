package com.aewsn.alkhair.data.manager

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalClassRepository
import com.aewsn.alkhair.data.local.local_repos.LocalDivisionRepository
import com.aewsn.alkhair.data.local.local_repos.LocalUserRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.data.remote.supabase.SupabaseAuthRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseUserRepository
import com.aewsn.alkhair.data.worker.UserUploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepoManager @Inject constructor(
    private val localUserRepository: LocalUserRepository,
    private val localClassRepository: LocalClassRepository,
    private val localDivisionRepository: LocalDivisionRepository,
    private val supabaseUserRepository: SupabaseUserRepository,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager
) : BaseRepoManager<User>() {

    /* ============================================================
       📦 READ — SSOT
       ============================================================ */
    override fun observeLocal(): Flow<List<User>> = localUserRepository.getAllUsers()

    fun observeUsersByRole(role: String): Flow<List<User>> =
        localUserRepository.getUsersByRole(role)

    suspend fun getUserById(uid: String): User? = localUserRepository.getUserByIdOneShot(uid)

    suspend fun getCurrentUser(): User? {
        val uid = supabaseAuthRepository.currentUserUid() ?: return null
        val user = getUserById(uid) ?: return null

        // ✅ Re-hydrate if names are missing but IDs depend on them
        // This handles race condition where user synced before class/division
        if ((!user.classId.isNullOrBlank() && user.className.isEmpty()) ||
            (!user.divisionId.isNullOrBlank() && user.divisionName.isEmpty())
        ) {
            val hydratedUser = hydrateUsers(listOf(user)).first()
            // Optional: Save back to DB to fix it permanently
            localUserRepository.insertUser(hydratedUser)
            return hydratedUser
        }

        return user
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
    suspend fun syncClassStudents(classId: String, shift: String, lastSync: Long): Result<Unit> {
        // Shift validation (taaki empty shift par crash na ho)
        val targetShift = shift.ifBlank { "General" }
        return supabaseUserRepository.getStudentsForClassUpdatedAfter(
            classId,
            targetShift,
            lastSync
        )
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

    suspend fun saveUserLocally(user: User) {
        insertLocal(user)
    }

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

    // Base Impl with Hydration
    override suspend fun insertLocal(items: List<User>) {
        val hydrated = hydrateUsers(items)
        localUserRepository.insertUsers(hydrated)
    }

    override suspend fun insertLocal(item: User) {
        val hydrated = hydrateUsers(listOf(item)).first()
        localUserRepository.insertUser(hydrated)
    }

    override suspend fun deleteLocally(id: String) = localUserRepository.deleteUser(id)
    override suspend fun clearLocal() = localUserRepository.clearAll()

    // 💧 Hydration Logic (Populate Names)
    private suspend fun hydrateUsers(users: List<User>): List<User> {
        if (users.isEmpty()) return users

        // Bulk Fetch Maps (Efficient)
        val classMap = localClassRepository.getAllClassesOneShot().associateBy { it.id }
        val divisionMap = localDivisionRepository.getAllDivisionsOneShot().associateBy { it.id }

        return users.map { user ->
            // Hydrate Class Name
            var cName = user.className
            val cId = user.classId
            if (!cId.isNullOrBlank()) {
                 cName = classMap[cId]?.className ?: cName
            }

            // Hydrate Division Name
            var dName = user.divisionName
            val dId = user.divisionId
            if (!dId.isNullOrBlank()) {
                dName = divisionMap[dId]?.name ?: dName
            }

            user.copy(className = cName, divisionName = dName)
        }
    }
}