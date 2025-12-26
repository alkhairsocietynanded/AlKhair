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
       🔁 SYNC (Standard Timestamp Logic)
       ============================================================ */

    // ✅ AB YEH KAAM KAREGA: Kyunki Firebase Repo ab "updatedAt" query support karta hai
    override suspend fun fetchRemoteUpdated(after: Long): List<Attendance> {
        return firebaseAttendanceRepo.getAttendanceUpdatedAfter(after)
            .getOrElse {
                Log.e("AttendanceRepoManager", "Sync failed", it)
                emptyList()
            }
    }

    override suspend fun insertLocal(items: List<Attendance>) =
        localAttendanceRepo.insertAttendanceList(items)

    override suspend fun insertLocal(item: Attendance) =
        localAttendanceRepo.insertAttendance(item)

    override suspend fun deleteLocally(id: String) {
        // Attendance usually composite keys use karta hai, standard delete complex hai.
        // Filhal ise empty chhod sakte hain ya custom logic laga sakte hain.
    }

    /* ============================================================
       ✍️ WRITE — (Remote First -> Then Local)
       ============================================================ */

    suspend fun saveAttendance(
        classId: String,
        date: String,
        attendanceList: List<Attendance>
    ): Result<Unit> {
        // Convert List back to Map for the specific Firebase Method signature
        // Or refactor Firebase repo to take List directly (Recommended)
        val map = attendanceList.associate { it.studentId to it.status }

        return firebaseAttendanceRepo.saveAttendanceForClass(classId, date, map)
            .onSuccess {
                // Save to Local immediately with fresh timestamps
                val updatedList = attendanceList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                insertLocal(updatedList)
            }
    }
}