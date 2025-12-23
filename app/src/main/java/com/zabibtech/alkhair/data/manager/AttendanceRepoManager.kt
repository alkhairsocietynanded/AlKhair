package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAttendanceRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAttendanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepoManager @Inject constructor(
    private val localAttendanceRepo: LocalAttendanceRepository,
    private val firebaseAttendanceRepo: FirebaseAttendanceRepository
) : BaseRepoManager<Attendance>() {

    /* ============================================================
       📦 BASE REPO MANAGER IMPLEMENTATION (SSOT)
       ============================================================ */

    // Direct pipe from Room DB.
    // Note: Ensure your LocalAttendanceRepository has getAllAttendance()
    override fun observeLocal(): Flow<List<Attendance>> =
        localAttendanceRepo.getAllAttendance()

    /**
     * Attendance syncing is Range-Based (e.g., Sync current month).
     * Global timestamp sync is inefficient for the Attendance data structure in Firebase.
     * We return empty here and use [syncAttendanceRange] explicitly in AppDataSyncManager.
     */
    override suspend fun fetchRemoteUpdated(after: Long): List<Attendance> {
        return emptyList()
    }

    override suspend fun insertLocal(items: List<Attendance>) =
        localAttendanceRepo.insertAttendanceList(items)

    override suspend fun insertLocal(item: Attendance) =
        localAttendanceRepo.insertAttendance(item)

    override suspend fun deleteLocally(id: String) {
        // Attendance uses composite keys (studentId + date + classId).
        // Standard ID-based deletion is complex here.
        // If needed, you can parse the 'id' string if it follows a format like "uid_date_class"
        // For now, we leave this empty as deletion is rarely synced for historical attendance.
    }

    /* ============================================================
       🔭 SPECIFIC OBSERVABLES (Used by ViewModel)
       ============================================================ */

    fun observeAttendanceByDate(date: String): Flow<List<Attendance>> =
        localAttendanceRepo.getAttendanceByDate(date)

    fun observeAttendanceByStudent(studentId: String): Flow<List<Attendance>> =
        localAttendanceRepo.getAttendanceByStudent(studentId)

    /* ============================================================
       ✍️ WRITE OPERATIONS (Remote First -> Then Local)
       ============================================================ */

    suspend fun saveAttendance(
        classId: String,
        date: String,
        attendanceList: List<Attendance>
    ): Result<Unit> {
        // 1. Prepare data for Firebase (Map<StudentId, Status>)
        val attendanceMap = attendanceList.associate { it.studentId to it.status }

        // 2. Save to Remote (Firebase)
        return firebaseAttendanceRepo.saveAttendanceForClass(classId, date, attendanceMap)
            .onSuccess {
                // 3. Save to Local (Room) - This triggers the Flow to update UI automatically
                try {
                    val updatedList = attendanceList.map {
                        it.copy(updatedAt = System.currentTimeMillis())
                    }
                    insertLocal(updatedList)
                } catch (e: Exception) {
                    Log.e("AttendanceRepoManager", "Failed to cache local attendance", e)
                }
            }
    }

    /* ============================================================
       🔁 RANGE SYNC (Specific to Attendance)
       ============================================================ */

    suspend fun syncAttendanceRange(startDate: String, endDate: String): Result<Unit> {
        return firebaseAttendanceRepo.getAttendanceForDateRange(startDate, endDate)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    try {
                        // Mark synced data with current timestamp for local freshness
                        val updatedList = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                        insertLocal(updatedList)
                        Log.d("AttendanceRepoManager", "Synced ${list.size} records ($startDate to $endDate)")
                    } catch (e: Exception) {
                        Log.e("AttendanceRepoManager", "Failed to cache synced attendance range", e)
                    }
                }
            }
            .map { } // Convert Result<List> to Result<Unit>
    }
}