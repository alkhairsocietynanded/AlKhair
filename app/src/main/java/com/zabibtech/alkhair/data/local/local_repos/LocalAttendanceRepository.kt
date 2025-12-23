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
    fun getAttendance(studentId: String, date: String): Flow<Attendance?> =
        attendanceDao.getAttendance(studentId, date)

    fun getAttendanceByDate(date: String): Flow<List<Attendance>> =
        attendanceDao.getAttendanceByDate(date)

    fun getAttendanceByStudent(studentId: String): Flow<List<Attendance>> =
        attendanceDao.getAttendanceByStudent(studentId)

    // ✅ ADDED for BaseRepoManager
    fun getAllAttendance(): Flow<List<Attendance>> =
        attendanceDao.getAllAttendance()

    suspend fun insertAttendance(attendance: Attendance) =
        attendanceDao.insertAttendance(attendance)

    suspend fun insertAttendanceList(attendanceList: List<Attendance>) =
        attendanceDao.insertAttendanceList(attendanceList)

    suspend fun clearAll() = attendanceDao.clearAllAttendance()
}