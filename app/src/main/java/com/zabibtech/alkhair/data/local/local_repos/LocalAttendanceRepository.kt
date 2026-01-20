package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.AttendanceDao
import com.zabibtech.alkhair.data.models.Attendance
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao
) {
    // ✅ Required for BaseRepoManager (SSOT)
    fun getAllAttendance(): Flow<List<Attendance>> =
        attendanceDao.getAllAttendance()

    fun getAttendance(studentId: String, date: String): Flow<Attendance?> =
        attendanceDao.getAttendance(studentId, date)

    // Specific Queries
    fun getAttendanceByDate(date: String): Flow<List<Attendance>> =
        attendanceDao.getAttendanceByDate(date)

    fun getAttendanceByStudent(studentId: String): Flow<List<Attendance>> =
        attendanceDao.getAttendanceByStudent(studentId)

    suspend fun insertAttendance(attendance: Attendance) =
        attendanceDao.insertAttendance(attendance)

    suspend fun insertAttendanceList(attendanceList: List<Attendance>) =
        attendanceDao.insertAttendanceList(attendanceList)

    suspend fun deleteAttendance(studentId: String, classId: String, date: String) {
        // DAO me delete query honi chahiye agar individual delete support karna hai
    }
    suspend fun clearAll() = attendanceDao.clearAllAttendance()

    suspend fun getUnsyncedAttendance(): List<Attendance> = attendanceDao.getUnsyncedAttendance()

    suspend fun markAttendanceAsSynced(studentId: String, classId: String, date: String) =
        attendanceDao.markAttendanceAsSynced(studentId, classId, date)
}