package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.utils.FirebaseRefs
import com.zabibtech.alkhair.utils.Roles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AppDataSyncManager @Inject constructor(
    private val appDataStore: AppDataStore,
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val feesRepoManager: FeesRepoManager,
    private val salaryRepoManager: SalaryRepoManager,
    private val homeworkRepoManager: HomeworkRepoManager,
    private val announcementRepoManager: AnnouncementRepoManager
) {

    companion object {
        private const val TAG = "AppDataSyncManager"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val SYNC_THRESHOLD_MS = 1 * 60 * 1000L // 1 min
    }

    private val syncMutex = Mutex()
    private var isSyncRunning = false

    /**
     * 🔁 Public entry point
     */
    suspend fun syncAllData(forceRefresh: Boolean = false): Result<Unit> {
        Log.d(TAG, "syncAllData called with forceRefresh: $forceRefresh")
        // Fast check without locking
        if (isSyncRunning) {
            Log.d(TAG, "⚠️ Sync skipped: Already running.")
            return Result.success(Unit)
        }

        return syncMutex.withLock {
            if (isSyncRunning) return Result.success(Unit)
            isSyncRunning = true
            Log.d(TAG, "Sync lock acquired.")
            try {
                performSync(forceRefresh)
            } finally {
                isSyncRunning = false
                Log.d(TAG, "Sync lock released.")
            }
        }
    }

    private suspend fun performSync(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Check Login & Role
                val currentUid = authRepoManager.getCurrentUserUid()
                if (currentUid == null) {
                    Log.d(TAG, "Sync skipped: No user logged in.")
                    return@withContext Result.success(Unit)
                }

                // Fetch User Role from Local DB
                val currentUser = userRepoManager.getUserById(currentUid)
                val role = currentUser?.role?.trim() ?: Roles.STUDENT
                Log.d(TAG, "Current user role: $role")

                // 2. Check Timestamps
                val lastSync = appDataStore.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L
                val deviceTime = System.currentTimeMillis()

                if (!forceRefresh && lastSync != 0L && (deviceTime - lastSync) < SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "Sync skipped → Data is fresh. Last sync: $lastSync")
                    return@withContext Result.success(Unit)
                }

                val isFirstSync = lastSync == 0L
                val queryTime = if (isFirstSync) 0L else lastSync + 1

                Log.d(TAG, "Starting sync ($role). Last: $lastSync, Query: $queryTime, isFirstSync: $isFirstSync")

                supervisorScope {

                    // List to hold all sync jobs.
                    // Type is Result<Any> to accommodate both Result<Long> (timestamps) and Result<Unit> (void).
                    val syncJobs = mutableListOf<Deferred<Result<Any>>>()

                    // ====================================================
                    // 👑 ADMIN STRATEGY (Global Sync)
                    // ====================================================
                    if (role.equals(Roles.ADMIN, true)) {
                        Log.d(TAG, "Syncing for ADMIN")
                        syncJobs.add(async { userRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { feesRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { salaryRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { homeworkRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { announcementRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { attendanceRepoManager.sync(queryTime).map { it as Any } })
                    }

                    // ====================================================
                    // 👨‍🏫 TEACHER STRATEGY (Class-Specific + Personal Sync)
                    // ====================================================
                    else if (role.equals(Roles.TEACHER, true)) {
                        Log.d(TAG, "Syncing for TEACHER")

                        // 1. My Salary
                        syncJobs.add(async { salaryRepoManager.syncStaffSalary(currentUid, queryTime).map { it as Any } })

                        // 2. Global Announcements
                        syncJobs.add(async { announcementRepoManager.sync(queryTime).map { it as Any } })

                        // 3. Class Data (Students, Homework, Attendance)
                        val teacherClassId = currentUser?.classId
                        if (!teacherClassId.isNullOrBlank()) {
                            Log.d(TAG, "Teacher class: $teacherClassId")
                            syncJobs.add(async { userRepoManager.syncClassStudents(teacherClassId, queryTime).map { it as Any } })
                            syncJobs.add(async { homeworkRepoManager.syncClassHomework(teacherClassId, queryTime).map { it as Any } })
                            syncJobs.add(async { attendanceRepoManager.syncClassAttendance(teacherClassId, queryTime).map { it as Any } })
                            syncJobs.add(async { feesRepoManager.syncClassFees(teacherClassId, queryTime).map { it as Any }  })
                        } else {
                            Log.d(TAG, "Teacher has no class, syncing profile only.")
                            // Fallback: Sync self profile only
                            syncJobs.add(async { userRepoManager.syncUserProfile(currentUid).map { it as Any } })
                        }
                    }

                    // ====================================================
                    // 🎓 STUDENT STRATEGY (Targeted Sync)
                    // ====================================================
                    else {
                        Log.d(TAG, "Syncing for STUDENT")
                        // 1. Targeted Announcements (Global + Class)
                        syncJobs.add(async { announcementRepoManager.syncTargetAnnouncements("ALL", queryTime).map { it as Any } })

                        val userClassId = currentUser?.classId
                        if (!userClassId.isNullOrBlank()) {
                            Log.d(TAG, "Student class: $userClassId")
                            syncJobs.add(async { announcementRepoManager.syncTargetAnnouncements(userClassId, queryTime).map { it as Any } })
                            // 2. My Class Homework
                            syncJobs.add(async { homeworkRepoManager.syncClassHomework(userClassId, queryTime).map { it as Any } })
                        }

                        // 3. My Fees
                        syncJobs.add(async { feesRepoManager.syncStudentFees(currentUid, queryTime).map { it as Any } })

                        // 4. My Attendance
                        syncJobs.add(async { attendanceRepoManager.syncStudentAttendance(currentUid, queryTime).map { it as Any } })

                        // 5. My Profile
                        syncJobs.add(async { userRepoManager.syncUserProfile(currentUid).map { it as Any } })
                    }

                    // ====================================================
                    // 🌍 COMMON JOBS (Metadata & Deletions) - For All Roles
                    // ====================================================
                    Log.d(TAG, "Syncing common jobs")
                    syncJobs.add(async { classDivisionRepoManager.syncClasses(queryTime).map { it as Any } })
                    syncJobs.add(async { classDivisionRepoManager.syncDivisions(queryTime).map { it as Any } })

                    if (!isFirstSync) {
                        Log.d(TAG, "Syncing deletions")
                        syncJobs.add(async { syncDeletions(queryTime).map { it as Any } })
                    }

                    // --- EXECUTION & TIMESTAMP CALCULATION ---
                    Log.d(TAG, "Awaiting all sync jobs.")
                    val results = syncJobs.awaitAll()
                    Log.d(TAG, "All sync jobs finished.")

                    // Calculate Max Timestamp to update local sync time correctly
                    var maxDataTimestamp = lastSync

                    results.forEachIndexed { index, result ->
                        result.onSuccess { data ->
                            // Only update timestamp if the repo returned a Long (meaning it fetched new data)
                            if (data is Long && data > maxDataTimestamp) {
                                maxDataTimestamp = data
                                Log.d(TAG, "Job $index success with new max timestamp: $maxDataTimestamp")
                            }
                        }
                        result.onFailure { e ->
                            Log.e(TAG, "A sync job ($index) failed", e)
                        }
                    }

                    // Save Timestamp (Greater of Device Time vs Max Data Time)
                    val newSyncTime = max(deviceTime, maxDataTimestamp)
                    appDataStore.saveString(KEY_LAST_SYNC, newSyncTime.toString())
                    Log.d(TAG, "✅ Sync completed ($role). New Timestamp: $newSyncTime")
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync crashed", e)
                Result.failure(e)
            }
        }

    /**
     * 🗑️ Tombstone delete sync
     */
    private suspend fun syncDeletions(after: Long): Result<Unit> {
        Log.d(TAG, "Starting deletion sync after: $after")
        return try {
            val snapshot = FirebaseRefs.deletedRecordsRef
                .orderByChild("timestamp")
                .startAt(after.toDouble())
                .get()
                .await()

            val records = snapshot.children.mapNotNull { it.getValue(DeletedRecord::class.java) }
            Log.d(TAG, "Found ${records.size} records to delete.")

            if (records.isNotEmpty()) {
                records.forEach { record ->
                    try {
                        Log.d(TAG, "Deleting ${record.type} with id: ${record.id}")
                        when (record.type) {
                            "user" -> userRepoManager.deleteLocally(record.id)
                            "class" -> classDivisionRepoManager.deleteClassLocally(record.id)
                            "division" -> classDivisionRepoManager.deleteDivisionLocally(record.id)
                            "fees" -> feesRepoManager.deleteLocally(record.id)
                            "salary" -> salaryRepoManager.deleteLocally(record.id)
                            "homework" -> homeworkRepoManager.deleteLocally(record.id)
                            "announcement" -> announcementRepoManager.deleteLocally(record.id)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting ${record.type} : ${record.id}", e)
                    }
                }
            }
            Log.d(TAG, "Deletion sync finished.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Deletion sync failed", e)
            Result.failure(e)
        }
    }
}
