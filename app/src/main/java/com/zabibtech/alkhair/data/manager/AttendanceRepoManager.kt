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
        return firebaseAttendanceRepo.getAttendanceUpdatedAfter(after)
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
        return firebaseAttendanceRepo.getAttendanceForClassAndShiftUpdatedAfter(classId, shift, lastSync)
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
        return firebaseAttendanceRepo.getAttendanceForStudentUpdatedAfter(studentId, lastSync)
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
       ✍️ WRITE — (Remote First -> Then Local)
       ============================================================ */

    suspend fun saveAttendance(
        classId: String,
        date: String,
        shift: String,
        attendanceList: List<Attendance>
    ): Result<Unit> {
        // UI List bhejta hai, lekin Firebase Repo (legacy reasons se) Map le raha tha.
        // Hamne Firebase Repo update kar diya hai lekin method signature wahi hai.
        val map = attendanceList.associate { it.studentId to it.status }

        return firebaseAttendanceRepo.saveAttendanceForClass(classId, date, shift,map)
            .onSuccess {
                // Save to Local immediately with fresh timestamps
                // Note: Firebase Repo save karte waqt timestamp generate karta hai,
                // lekin local consistency ke liye hum yahan bhi update kar dete hain.
                val updatedList = attendanceList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                insertLocal(updatedList)
            }
    }
}