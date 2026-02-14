package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.datastore.AppDataStore
import com.aewsn.alkhair.data.remote.supabase.SupabaseDeletionRepository
import com.aewsn.alkhair.utils.Roles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import androidx.core.content.edit

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
    private val announcementRepoManager: AnnouncementRepoManager,
    private val leaveRepoManager: LeaveRepoManager,
    private val syllabusRepoManager: SyllabusRepoManager,
    private val subjectRepoManager: SubjectRepoManager,
    private val timetableRepoManager: TimetableRepoManager,
    private val resultRepoManager: ResultRepoManager,
    private val studyMaterialRepoManager: StudyMaterialRepoManager,
    private val appConfigRepoManager: AppConfigRepoManager,
    private val deletionRepository: SupabaseDeletionRepository,
    private val sharedPreferences: android.content.SharedPreferences
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
                var lastSync = appDataStore.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L
                val deviceTime = System.currentTimeMillis()

                // ⚠️ Detect post-destructive-migration: DB wiped but DataStore still has old timestamp
                if (lastSync != 0L && currentUser == null) {
                    Log.w(TAG, "⚠️ DB appears wiped (no local user) but lastSync=$lastSync. Resetting to 0 for full re-sync.")
                    appDataStore.saveString(KEY_LAST_SYNC, "0")
                    lastSync = 0L
                }

                if (!forceRefresh && lastSync != 0L && (deviceTime - lastSync) < SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "Sync skipped → Data is fresh. Last sync: $lastSync")
                    return@withContext Result.success(Unit)
                }

                val isFirstSync = lastSync == 0L
                val queryTime = if (isFirstSync) 0L else lastSync + 1

                Log.d(TAG, "Starting sync ($role). Last: $lastSync, Query: $queryTime, isFirstSync: $isFirstSync")

                supervisorScope {

                    // ====================================================
                    // 🌍 1. SYNC CLASSES & DIVISIONS FIRST (Sequential)
                    // ====================================================
                    // Important for hydration (User names, Homework division names etc).
                    // We sync these sequentially and await result before proceeding.
                    Log.d(TAG, "Step 1: Syncing Metadata (Classes/Divisions)")

                    // List to hold remaining sync jobs.
                    val syncJobs = mutableListOf<Deferred<Result<Any>>>()
                    
                    val classJob = async { classDivisionRepoManager.syncClasses(queryTime).map { it as Any } }
                    val divJob = async { classDivisionRepoManager.syncDivisions(queryTime).map { it as Any } }
                    
                    // Await Metadata
                    val metadataResults = listOf(classJob, divJob).awaitAll()
                    Log.d(TAG, "Step 1 Finished: Metadata Synced.")

                    // Sync Subjects (Metadata) - Globally for everyone
                    val subjectJob = async { subjectRepoManager.sync(queryTime).map { it as Any } }
                    val timetableJob = async { timetableRepoManager.sync(queryTime).map { it as Any } }
                    val examJob = async { resultRepoManager.syncExams(queryTime).map { it as Any } } // ✅ Sync Exams
                    val configJob = async { appConfigRepoManager.sync(queryTime).map { it as Any } } // ✅ Sync App Config

                    // We can await these or let them run in parallel with others if they don't block dependent data.
                    // Subjects are needed for Timetable display.
                    // Let's add them to syncJobs or await them if needed immediately.
                    // Since Timetable depends on Subjects (for names), we should arguably sync Subjects first or just let Flow update UI.
                    // Since we use Room, UI will update when data arrives.
                    syncJobs.add(subjectJob)
                    syncJobs.add(timetableJob)
                    syncJobs.add(examJob)
                    syncJobs.add(configJob)


                    // ====================================================
                    // 🚀 2. SYNC DEPENDENT DATA (Parallel)
                    // ====================================================
                    Log.d(TAG, "Step 2: Syncing Dependent Entities")

                    // ====================================================
                    // 👑 ADMIN STRATEGY (Global Sync)
                    // ====================================================
                    if (role.equals(Roles.ADMIN, true)) {
                        Log.d(TAG, "Syncing for ADMIN")
                        // 1. Core Dependencies (Users is CRITICAL for Results/Attendance)
                        val userJob = async { userRepoManager.sync(queryTime).map { it as Any } }
                        
                        // 2. Parallel jobs that don't depend on Users immediately or handle missing users gracefully
                        syncJobs.add(async { feesRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { salaryRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { homeworkRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { announcementRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { attendanceRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { leaveRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { syllabusRepoManager.sync(queryTime).map { it as Any } })
                        syncJobs.add(async { studyMaterialRepoManager.sync(queryTime).map { it as Any } })
                        
                        // 3. Await Users BEFORE Results
                        // Results depend on Users, Exams and Subjects. 
                        // Subjects & Exams are already started in Step 1.
                        // We must ensure they are done.
                        syncJobs.add(userJob)
                        
                        // Add a separate job for Results that starts AFTER dependencies
                        // But since we are in async block, we can't easily chain without blocking everything.
                        // Alternative: Add Result sync to the END of the process or use a separate coroutine scope that waits.
                        // Simplest fix: Launch Result sync independently but `await` it at the end? 
                        // No, if we fire it now, it runs now.
                        // BETTER: Add it to a lazy job or just run it last?
                        // We will wait for `syncJobs` (including Users) to finish, THEN sync Results.
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
                        val teacherShift = currentUser?.shift
                        if (!teacherClassId.isNullOrBlank() && !teacherShift.isNullOrBlank()) {
                            Log.d(TAG, "Teacher class: $teacherClassId")
                            // 1. Sync students FIRST (Leave sync depends on students being in local DB)
                            val studentSyncJob = async { userRepoManager.syncClassStudents(teacherClassId, teacherShift,queryTime).map { it as Any } }
                            syncJobs.add(studentSyncJob)

                            // 2. Independent jobs (run in parallel, don't depend on students)
                            syncJobs.add(async { homeworkRepoManager.syncClassHomework(teacherClassId, teacherShift, queryTime).map { it as Any } })
                            syncJobs.add(async { attendanceRepoManager.syncClassAttendance(teacherClassId, teacherShift, queryTime).map { it as Any } })
                            syncJobs.add(async { feesRepoManager.syncClassFees(teacherClassId, teacherShift,queryTime).map { it as Any }  })
                            syncJobs.add(async { syllabusRepoManager.syncClassSyllabus(teacherClassId, queryTime).map { it as Any } })
                            syncJobs.add(async { studyMaterialRepoManager.syncClassMaterials(teacherClassId, queryTime).map { it as Any } })

                            // 3. Teacher ki apni leave (independent, no dependency)
                            syncJobs.add(async { leaveRepoManager.syncLeavesForStudent(currentUid, queryTime).map { it as Any } })

                            // 4. Await students BEFORE syncing class leaves (syncLeavesForClass reads student IDs from local DB)
                            studentSyncJob.await()
                            syncJobs.add(async { leaveRepoManager.syncLeavesForClass(teacherClassId, queryTime).map { it as Any } })
                        } else {
                            Log.d(TAG, "Teacher has no class, syncing profile only.")
                            // Fallback: Sync self profile only
                            syncJobs.add(async { userRepoManager.syncUserProfile(currentUid).map { it as Any } })
                        }
                        // 4. Sync Other Teachers (For Timetable name resolution)
                        syncJobs.add(async { userRepoManager.syncTeachers(queryTime).map { it as Any } })
                    }

                    // ====================================================
                    // 🎓 STUDENT STRATEGY (Targeted Sync)
                    // ====================================================
                    else {
                        Log.d(TAG, "Syncing for STUDENT")

                        // 1. Targeted Announcements (Global)
                        syncJobs.add(async { announcementRepoManager.syncTargetAnnouncements("All", queryTime).map { it as Any } })

                        val userClassId = currentUser?.classId
                        val userShift = currentUser?.shift ?: "" // ✅ Get Student Shift

                        if (!userClassId.isNullOrBlank()) {
                            Log.d(TAG, "Student class: $userClassId, Shift: $userShift")

                            // Class Specific Announcements
                            syncJobs.add(async { announcementRepoManager.syncTargetAnnouncements(userClassId, queryTime).map { it as Any } })

                            // 2. My Class Homework (✅ Pass Shift here)
                            syncJobs.add(async {
                                homeworkRepoManager.syncClassHomework(userClassId, userShift, queryTime).map { it as Any }
                            })
                        }

                        // 3. My Fees
                        syncJobs.add(async { feesRepoManager.syncStudentFees(currentUid, queryTime).map { it as Any } })

                        // 4. My Attendance
                        syncJobs.add(async { attendanceRepoManager.syncStudentAttendance(currentUid, queryTime).map { it as Any } })

                        // 5. My Profile
                        syncJobs.add(async { userRepoManager.syncUserProfile(currentUid).map { it as Any } })

                        // 6. My Leaves (New!)
                        syncJobs.add(async { leaveRepoManager.syncLeavesForStudent(currentUid, queryTime).map { it as Any } })

                        // 7. My Class Syllabus
                        // 7. My Class Syllabus & 8. My Class Study Materials
                        if (!userClassId.isNullOrBlank()) {
                            syncJobs.add(async { syllabusRepoManager.syncClassSyllabus(userClassId, queryTime).map { it as Any } })
                            syncJobs.add(async { studyMaterialRepoManager.syncClassMaterials(userClassId, queryTime).map { it as Any } })
                        }
                        
                        // 9. Sync Teachers (For Timetable name resolution)
                        syncJobs.add(async { userRepoManager.syncTeachers(queryTime).map { it as Any } })
                    }

                    // ====================================================
                    // 🌍 COMMON JOBS (Deletions) - For All Roles
                    // ====================================================
                    
                    if (!isFirstSync) {
                        Log.d(TAG, "Syncing deletions")
                        syncJobs.add(async { syncDeletions(queryTime).map { it as Any } })
                    }

                    // --- EXECUTION & TIMESTAMP CALCULATION ---
                    Log.d(TAG, "Awaiting dependent sync jobs.")
                    val results = syncJobs.awaitAll() + metadataResults // Merge results for timestamp calc
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

                    // ... (existing timestamp calculation)

                    // ====================================================
                    // 🏁 3. SYNC RESULTS (Must be LAST to ensure FK Integrity)
                    // ====================================================
                    // Only run if dependencies (Users, Exams, Subjects) are likely synced.
                    // We rely on the fact that previous jobs finished (even if failed, we try).
                    // In strict mode, we should check compliance, but for now, late sync is better than crash.
                    
                        Log.d(TAG, "Step 3: Syncing Results (Dependent on all above)")
                        try {
                            val resultSyncResult = if (role.equals(Roles.ADMIN, true)) {
                                 resultRepoManager.syncAllResults(queryTime)
                            } else if (role.equals(Roles.TEACHER, true)) {
                                 resultRepoManager.syncAllResults(queryTime) // Teachers see all for now
                            } else {
                                 // ✅ Use already verified currentUid instead of re-fetching
                                 resultRepoManager.syncStudentResults(currentUid, queryTime)
                            }
                        
                        // Update timestamp with Result sync
                        resultSyncResult.onSuccess { data ->
                             if (data > maxDataTimestamp) {
                                 maxDataTimestamp = data
                                 Log.d(TAG, "Results synced with new max timestamp: $maxDataTimestamp")
                             }
                        }
                        resultSyncResult.onFailure { e ->
                            Log.e(TAG, "Results sync failed (non-fatal)", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Results sync crashed (non-fatal, skipping)", e)
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
     * 🗑️ Tombstone delete sync - Supabase Implementation
     */
    private suspend fun syncDeletions(after: Long): Result<Unit> {
        Log.d(TAG, "Starting deletion sync after: $after")
        return try {
            val result = deletionRepository.getDeletedRecords(after)

            if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)

            val records = result.getOrElse { emptyList() }
            Log.d(TAG, "Found ${records.size} records to delete.")

            if (records.isNotEmpty()) {
                records.forEach { record ->
                    try {
                        Log.d(TAG, "Deleting ${record.type} with id: ${record.recordId}")
                        when (record.type) {
                            "users" -> userRepoManager.deleteLocally(record.recordId)
                            "classes" -> classDivisionRepoManager.deleteClassLocally(record.recordId)
                            "divisions" -> classDivisionRepoManager.deleteDivisionLocally(record.recordId)
                            "fees" -> feesRepoManager.deleteLocally(record.recordId)
                            "salary" -> salaryRepoManager.deleteLocally(record.recordId)
                            "homework" -> homeworkRepoManager.deleteLocally(record.recordId)
                            "announcements" -> announcementRepoManager.deleteLocally(record.recordId)
                            "leaves" -> leaveRepoManager.deleteLocally(record.recordId)
                            "attendance" -> attendanceRepoManager.deleteLocally(record.recordId)
                            "syllabus" -> syllabusRepoManager.deleteLocally(record.recordId)
                            "study_materials" -> studyMaterialRepoManager.deleteLocally(record.recordId)
                            "subjects" -> subjectRepoManager.deleteLocally(record.recordId)
                            "timetable" -> timetableRepoManager.deleteLocally(record.recordId)
                            "exams" -> resultRepoManager.deleteExamLocally(record.recordId)
                            "results" -> resultRepoManager.deleteResultLocally(record.recordId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting ${record.type} : ${record.recordId}", e)
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

    suspend fun clearAllLocalData() {
        Log.d("AppDataSyncManager", "Clearing all local data...")

        // 1. Clear Database Tables
        // (Ensure clearLocal() is implemented in BaseRepoManager/All Repos)
        userRepoManager.clearLocal()
        feesRepoManager.clearLocal()
        attendanceRepoManager.clearLocal()
        homeworkRepoManager.clearLocal()
        salaryRepoManager.clearLocal()
//        classDivisionRepoManager.clearLocal()
//        classDivisionRepoManager.clearLocal()
        announcementRepoManager.clearLocal()
        leaveRepoManager.clearLocal()
        syllabusRepoManager.clearLocal()
        studyMaterialRepoManager.clearLocal()
        subjectRepoManager.clearLocal()
        timetableRepoManager.clearLocal()
        resultRepoManager.clearLocal()
        appConfigRepoManager.clearLocal()

        // 2. Clear Sync Timestamp (Important!)
        // Taaki naya user login kare to full sync ho
        appDataStore.clearAll()
        
        // 3. Clear SharedPreferences (Old remnants or specific flags)
        sharedPreferences.edit { clear() }

        Log.d("AppDataSyncManager", "All local data cleared.")
    }
}
