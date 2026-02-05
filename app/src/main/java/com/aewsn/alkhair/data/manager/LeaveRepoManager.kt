package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalLeaveRepository
import com.aewsn.alkhair.data.local.local_repos.LocalUserRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.Leave
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.remote.supabase.SupabaseLeaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaveRepoManager @Inject constructor(
    private val localLeaveRepository: LocalLeaveRepository,
    private val supabaseLeaveRepository: SupabaseLeaveRepository,
    private val workManager: androidx.work.WorkManager,
    private val pendingDeletionDao: PendingDeletionDao,
    private val userRepo: LocalUserRepository,
    private val attendanceRepoManager: AttendanceRepoManager
) : BaseRepoManager<Leave>() {

    /* ============================================================
       📦 READ OPERATIONS (SSOT - Local Flow)
       ============================================================ */

    override fun observeLocal(): Flow<List<Leave>> {
        return flowOf(emptyList())
    }

    fun getLeavesForStudent(studentId: String): Flow<List<Leave>> {
        return localLeaveRepository.getLeavesByStudent(studentId)
    }

    // Teacher: Get leaves and Sync
    fun getLeavesForClass(classId: String): Flow<List<com.aewsn.alkhair.data.models.LeaveWithStudent>> =
        localLeaveRepository.getLeavesByClass(classId)

    // Admin: Get all and Sync
    fun getAllLeaves(): Flow<List<com.aewsn.alkhair.data.models.LeaveWithStudent>> =
        localLeaveRepository.getAllLeaves()

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<Leave> {
        return try {
            val leaves = supabaseLeaveRepository.fetchAllLeaves(after)
            if (leaves.isNotEmpty()) {
                Log.d("LeaveRepoManager", "Global Sync: Fetched ${leaves.size} leaves")
            }
            leaves
        } catch (e: Exception) {
            Log.e("LeaveRepoManager", "Global Sync failed", e)
            emptyList()
        }
    }

    suspend fun syncLeavesForStudent(studentId: String, lastSync: Long): Result<Unit> {
        return try {
            Log.d("LeaveRepoManager", "Syncing leaves for student: $studentId")
            val remoteLeaves = supabaseLeaveRepository.fetchLeavesForStudent(studentId, lastSync)
            if (remoteLeaves.isNotEmpty()) {
                Log.d(
                    "LeaveRepoManager",
                    "Synced ${remoteLeaves.size} leaves for student: $studentId"
                )
                insertLocal(remoteLeaves)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LeaveRepoManager", "Sync failed for student: $studentId", e)
            Result.failure(e)
        }
    }

    suspend fun syncLeavesForClass(classId: String, lastSync: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get List of Students in Class
                val students = userRepo.getUsersByClass(classId)
                val studentIds = students.map { it.uid }

                if (studentIds.isEmpty()) return@withContext Result.success(Unit)

                // 2. Fetch leaves for these students
                val leaves = supabaseLeaveRepository.fetchLeavesForStudents(studentIds, lastSync)

                // 3. Save to Local
                if (leaves.isNotEmpty()) {
                    val syncedLeaves = leaves.map { it.copy(isSynced = true) }
                    localLeaveRepository.insertLeaves(syncedLeaves)
                    Log.d("LeaveRepoManager", "Synced ${leaves.size} leaves for class: $classId")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("LeaveRepoManager", "Sync failed for class: $classId", e)
                Result.failure(e)
            }
        }
    }

    /* ============================================================
       ✍️ WRITE OPERATIONS (Offline First)
       ============================================================ */

    suspend fun applyLeave(leave: Leave): Result<Boolean> {
        Log.d("LeaveRepoManager", "Applying leave: $leave")

        // 1. Save to Local DB first (Optimistic UI - Pending/Not Synced)
        val localLeave = leave.copy(isSynced = false)
        insertLocal(localLeave)

        // 2. Try pushing to server immediately
        val result = supabaseLeaveRepository.applyLeave(leave)

        return if (result.isSuccess) {
            val submittedLeave = result.getOrNull()
            if (submittedLeave != null) {
                val syncedLeave = submittedLeave.copy(isSynced = true)
                localLeaveRepository.insertLeave(syncedLeave)
            }
            Result.success(true)
        } else {
            Log.e(
                "LeaveRepoManager",
                "Remote apply failed, keeping local copy.",
                result.exceptionOrNull()
            )
            scheduleUploadWorker()
            Result.failure(result.exceptionOrNull() ?: Exception("Network error, saved locally"))
        }
    }

    suspend fun updateLeave(leave: Leave): Result<Unit> {
        val currentTime = System.currentTimeMillis()

        // 1. Prepare Local Update
        val updatedLocal = leave.copy(
            updatedAt = currentTime,
            isSynced = false
        )

        // 2. Insert Local Immediately
        insertLocal(updatedLocal)

        // 3. Try pushing to server (Same as apply since we use upsert)
        val result = supabaseLeaveRepository.applyLeave(updatedLocal)

        if (result.isFailure) {
            scheduleUploadWorker()
        } else {
            // If success, update synced status
            val synced = result.getOrNull()?.copy(isSynced = true)
            if (synced != null) insertLocal(synced)
        }

        return Result.success(Unit)
    }

    suspend fun updateLeaveStatus(leave: Leave, status: String): Result<Unit> {
        val result = updateLeave(leave.copy(status = status))

        if (result.isSuccess && status.equals("Approved", ignoreCase = true)) {
            // Apply attendance for the date range
            try {
                // Fetch student to get Class ID (Leave table doesn't have it)
                val student = userRepo.getUserByIdOneShot(leave.studentId)

                if (student != null && !student.classId.isNullOrEmpty()) {
                    val classId = student.classId!! // Safe because of isNullOrEmpty check
                    val startDate = leave.startDate
                    val endDate = leave.endDate
                    // Simple date parsing assuming yyyy-MM-dd
                    val dates =
                        com.aewsn.alkhair.utils.DateUtils.getDatesBetween(startDate, endDate)

                    val attendanceList = dates.map { date ->
                        com.aewsn.alkhair.data.models.Attendance(
                            studentId = leave.studentId,
                            classId = classId,
                            date = date,
                            status = "Leave",
                            isSynced = false
                        )
                    }
                    // Use existing saveAttendance. Date/Shift params are unused for batch processing so passing defaults.
                    attendanceRepoManager.saveAttendance(classId, startDate, "", attendanceList)
                } else {
                    Log.e(
                        "LeaveRepoManager",
                        "Student or ClassId not found for leave: ${leave.id}, cannot update attendance"
                    )
                }

            } catch (e: Exception) {
                Log.e("LeaveRepoManager", "Failed to update attendance for approved leave", e)
                // Non-fatal, return success for leave update
            }
        }
        return result
    }

    suspend fun deleteLeave(leaveId: String): Result<Unit> {
        // 1. Delete Locally
        deleteLocally(leaveId)

        // 2. Mark for deletion
        val pendingDeletion = PendingDeletion(
            id = leaveId,
            type = "LEAVE",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)

        // 3. Schedule Upload Worker
        scheduleUploadWorker()

        return Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest =
            androidx.work.OneTimeWorkRequestBuilder<com.aewsn.alkhair.data.worker.LeaveUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

        workManager.enqueueUniqueWork(
            "LeaveUploadWork",
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    /* ============================================================
       🔧 LOCAL HELPER OVERRIDES
       ============================================================ */

    override suspend fun insertLocal(items: List<Leave>) {
        localLeaveRepository.insertLeaves(items)
    }

    override suspend fun insertLocal(item: Leave) {
        localLeaveRepository.insertLeave(item)
    }

    override suspend fun deleteLocally(id: String) {
        localLeaveRepository.deleteLeave(id)
    }

    override suspend fun clearLocal() {
        localLeaveRepository.clearLeaves()
    }
}
