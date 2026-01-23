package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAttendanceRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.remote.supabase.SupabaseAttendanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.zabibtech.alkhair.data.worker.AttendanceUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit  
import androidx.work.OneTimeWorkRequest

@Singleton
class AttendanceRepoManager @Inject constructor(
    private val localAttendanceRepo: LocalAttendanceRepository,
    private val remoteRepo: SupabaseAttendanceRepository,
    private val workManager: WorkManager
) : BaseRepoManager<Attendance>() {

    /* ============================================================
       📦 SSOT — LOCAL OBSERVATION
       ============================================================ */

    override fun observeLocal(): Flow<List<Attendance>> =
        localAttendanceRepo.getAllAttendance()

    // UI Helpers
    fun observeAttendanceByDate(date: String): Flow<List<Attendance>> =
        localAttendanceRepo.getAttendanceByDate(date)

    fun observeAttendanceByStudent(studentId: String): Flow<List<Attendance>> =
        localAttendanceRepo.getAttendanceByStudent(studentId)

    /* ============================================================
       🔁 SYNC LOGIC (Timestamp Based)
       ============================================================ */

    /**
     * 👑 ADMIN  SYNC (GLOBAL)
     * यह फंक्शन `AppDataSyncManager` द्वारा बुलाया जाता है जब `User Role` Admin हो।
     * यह पूरे स्कूल का अपडेटेड डेटा लाता है।
     */
    override suspend fun fetchRemoteUpdated(after: Long): List<Attendance> {
        return remoteRepo.getAttendanceUpdatedAfter(after)
            .getOrElse {
                Log.e("AttendanceRepoManager", "Global Sync failed", it)
                emptyList()
            }
    }

    /**
     * 👨‍🏫 TEACHER SYNC (TARGETED)
     * टीचर के लिए सिर्फ उनकी क्लास का डेटा लाएं।
     */
    suspend fun syncClassAttendance(classId: String, shift: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getAttendanceForClassAndShiftUpdatedAfter(classId, shift, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    insertLocal(list)
                    Log.d("AttendanceRepo", "Synced ${list.size} records for class $classId")
                }
            }
            .map { }
    }

    /**
     * 🎓 STUDENT SYNC (TARGETED) - ✅ NEW Added
     * यह फंक्शन `AppDataSyncManager` द्वारा तब बुलाया जाएगा जब `User Role` Student हो।
     * यह सिर्फ उस स्टूडेंट का डेटा लाता है, जिससे बैंडविड्थ बचती है।
     */
    suspend fun syncStudentAttendance(studentId: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getAttendanceForStudentUpdatedAfter(studentId, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    // Local DB me insert karein (updatedAt pehle se set hai Firebase se)
                    insertLocal(list)
                    Log.d("AttendanceRepo", "Synced ${list.size} records for student $studentId")
                }
            }
            .map { } // Result<List> -> Result<Unit> conversion
    }

    // BaseRepoManager implementation
    override suspend fun insertLocal(items: List<Attendance>) =
        localAttendanceRepo.insertAttendanceList(items)

    override suspend fun insertLocal(item: Attendance) =
        localAttendanceRepo.insertAttendance(item)

    override suspend fun deleteLocally(id: String) {
        // Composite keys deletion logic if needed in future
    }
    override suspend fun clearLocal() = localAttendanceRepo.clearAll()

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun saveAttendance(
        classId: String,
        date: String,
        shift: String,
        attendanceList: List<Attendance>
    ): Result<Unit> {
        // 1. Prepare Local Data (Mark as Unsynced)
        val currentTime = System.currentTimeMillis()
        val localList = attendanceList.map {
            it.copy(
                updatedAt = currentTime,
                isSynced = false
            )
        }

        // 2. Insert Local Immediately
        insertLocal(localList)

        // 3. Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<AttendanceUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "AttendanceUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }
}